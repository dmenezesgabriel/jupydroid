package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.TerminalSettings
import com.jupydroid.app.domain.port.TerminalSettingsRepository
import com.jupydroid.app.domain.port.TerminalSettingsRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The disk store owes the port's contract, plus what only a real filesystem can be asked. */
class DiskTerminalSettingsRepositoryTest : TerminalSettingsRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  private val stateDir: File get() = File(temp.root, "state")

  override fun repository(): TerminalSettingsRepository = openStore()

  private fun openStore() = DiskTerminalSettingsRepository(stateDir)

  /**
   * The setting is app state, so it outlives the process that wrote it - the whole reason this
   * port exists rather than a field on [com.jupydroid.app.di.AppContainer].
   */
  @Test
  fun survivesTheNextLaunch() = runTest {
    openStore().update(TerminalSettings(fontSizeSp = 20))

    val reopened = openStore().observeSettings().first()

    assertEquals(20, reopened.fontSizeSp)
  }

  /** A store written by something else must not take the app down on launch. */
  @Test
  fun anUnreadableStoreReadsAsTheDefault() = runTest {
    stateDir.mkdirs()
    File(stateDir, TERMINAL_SETTINGS_FILE_NAME).writeText("{ not json")

    assertEquals(TerminalSettings(), openStore().observeSettings().first())
  }

  /** Its own file, apart from `editor-settings.json` beside it - a separate concern, not a shared one. */
  @Test
  fun keepsItsOwnFile() = runTest {
    openStore().update(TerminalSettings(fontSizeSp = 20))

    assertTrue(File(stateDir, TERMINAL_SETTINGS_FILE_NAME).isFile)
  }

  /**
   * The file is the record and the flow repeats it - so a refused write announces nothing:
   * publishing before `writeText` would leave every collector showing a value the next launch
   * never kept. A directory standing where the file belongs is a write the filesystem refuses.
   */
  @Test
  fun aRefusedWritePublishesNothing() = runTest {
    val store = openStore()
    File(stateDir, TERMINAL_SETTINGS_FILE_NAME).mkdirs()

    val refused = runCatching { store.update(TerminalSettings(fontSizeSp = 20)) }

    assertTrue(refused.exceptionOrNull() is java.io.IOException)
    assertEquals(TerminalSettings(), store.observeSettings().first())
  }
}
