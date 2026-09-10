package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ExplorerSettings
import com.jupydroid.app.domain.port.ExplorerSettingsRepository
import com.jupydroid.app.domain.port.ExplorerSettingsRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The disk store owes the port's contract, plus what only a real filesystem can be asked. */
class DiskExplorerSettingsRepositoryTest : ExplorerSettingsRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  private val stateDir: File get() = File(temp.root, "state")

  override fun repository(): ExplorerSettingsRepository = openStore()

  private fun openStore() = DiskExplorerSettingsRepository(stateDir)

  /**
   * A section a user shut is still shut after the process dies - which is the whole reason this is
   * a port and not a field on a ViewModel, and what the reference does too (`setCollapsed` writes
   * through to storage on every change, `viewContainerModel.ts:456-461`).
   */
  @Test
  fun survivesTheNextLaunch() = runTest {
    openStore().update(ExplorerSettings(recentsCollapsed = true))

    val reopened = openStore().observeSettings().first()

    assertEquals(true, reopened.recentsCollapsed)
  }

  /** A store written by something else must not take the app down on launch. */
  @Test
  fun anUnreadableStoreReadsAsTheDefault() = runTest {
    stateDir.mkdirs()
    File(stateDir, EXPLORER_SETTINGS_FILE_NAME).writeText("{ not json")

    assertEquals(ExplorerSettings(), openStore().observeSettings().first())
  }

  /**
   * Its own file, beside `editor-settings.json` and `terminal-settings.json` rather than folded
   * into either: a collapsed section is neither an editor preference nor a terminal one.
   */
  @Test
  fun keepsItsOwnFile() = runTest {
    openStore().update(ExplorerSettings(recentsCollapsed = true))

    assertTrue(File(stateDir, EXPLORER_SETTINGS_FILE_NAME).isFile)
  }
}
