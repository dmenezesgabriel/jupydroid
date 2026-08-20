package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.EditorSettingsRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
}
