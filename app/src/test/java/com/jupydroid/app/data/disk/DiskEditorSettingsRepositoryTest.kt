package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.EditorSettingsRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The disk store owes the port's contract, plus what only a real filesystem can be asked. */
class DiskEditorSettingsRepositoryTest : EditorSettingsRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  private val stateDir: File get() = File(temp.root, "state")

  override fun repository(): EditorSettingsRepository = openStore()

  private fun openStore() = DiskEditorSettingsRepository(stateDir)

  /**
   * The setting is app state, so it outlives the process that wrote it - the whole reason this
   * port exists rather than a field on [com.jupydroid.app.di.AppContainer]: a toggle that forgets
   * on every launch is a defect by this repo's own standard.
   */
  @Test
  fun survivesTheNextLaunch() = runTest {
    openStore().update(EditorSettings(autoClosingBrackets = false))

    val reopened = openStore().observeSettings().first()

    assertFalse(reopened.autoClosingBrackets)
  }

  /**
   * An `editor-settings.json` written before `indentUnit` existed reads as its default - the
   * field's own contract: `ignoreUnknownKeys` covers fields it does not know, and a field it
   * has never heard of falls to `EditorSettings`'s own default rather than refusing the file.
   */
  @Test
  fun aStoreFromBeforeIndentUnit_readsAsTheDefault() = runTest {
    stateDir.mkdirs()
    File(stateDir, EDITOR_SETTINGS_FILE_NAME).writeText("""{"autoClosingBrackets": false}""")

    val reopened = openStore().observeSettings().first()

    assertFalse(reopened.autoClosingBrackets)
    assertEquals(IndentUnit.SPACES_4, reopened.indentUnit)
  }

  /** A store written by something else must not take the app down on launch. */
  @Test
  fun anUnreadableStoreReadsAsTheDefault() = runTest {
    stateDir.mkdirs()
    File(stateDir, EDITOR_SETTINGS_FILE_NAME).writeText("{ not json")

    assertTrue(openStore().observeSettings().first().autoClosingBrackets)
  }

  /**
   * Its own file, apart from [RECENTS_FILE_NAME] beside it - two different facts about the app
   * kept in two different files rather than merged into one that a future third fact would have
   * to be squeezed into.
   */
  @Test
  fun keepsItsOwnFile() = runTest {
    openStore().update(EditorSettings(autoClosingBrackets = false))

    assertTrue(File(stateDir, EDITOR_SETTINGS_FILE_NAME).isFile)
  }

  /**
   * The file is the record and the flow repeats it - so a refused write announces nothing:
   * publishing before the write would leave every collector showing a value the next launch
   * never kept. A directory standing where the file belongs is a write the filesystem refuses.
   */
  @Test
  fun aRefusedWritePublishesNothing() = runTest {
    val store = openStore()
    File(stateDir, EDITOR_SETTINGS_FILE_NAME).mkdirs()

    val refused = runCatching { store.update(EditorSettings(autoClosingBrackets = false)) }

    assertTrue(refused.exceptionOrNull() is java.io.IOException)
    assertTrue(store.observeSettings().first().autoClosingBrackets)
  }

  /**
   * An update replaces the store whole rather than rewriting it where it sits, so a kill mid-write
   * leaves the previous settings rather than half of them (ADR-0089).
   */
  @Test
  fun update_replacesTheFileRatherThanRewritingIt() = runTest {
    openStore().update(EditorSettings(autoClosingBrackets = false))
    val taken = File(stateDir, EDITOR_SETTINGS_FILE_NAME).linkTakenBeforeTheWrite(temp.root)
    val before = taken.readText()

    openStore().update(EditorSettings(autoClosingBrackets = true))

    assertEquals(before, taken.readText())
  }
}
