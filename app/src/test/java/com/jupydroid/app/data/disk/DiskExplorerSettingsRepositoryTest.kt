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
   * A section a user opened is still open after the process dies - which is the whole reason this is
   * a port and not a field on a ViewModel, and what the reference does too (`setCollapsed` writes
   * through to storage on every change, `viewContainerModel.ts:456-461`). Open, because shut is the
   * default and would read back with nothing written at all.
   */
  @Test
  fun survivesTheNextLaunch() = runTest {
    openStore().update(ExplorerSettings(recentsCollapsed = false))

    val reopened = openStore().observeSettings().first()

    assertEquals(false, reopened.recentsCollapsed)
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

  /**
   * The file is the record and the flow repeats it - so a refused write announces nothing:
   * publishing before the write would leave every collector showing a value the next launch
   * never kept. A directory standing where the file belongs is a write the filesystem refuses.
   */
  @Test
  fun aRefusedWritePublishesNothing() = runTest {
    val store = openStore()
    File(stateDir, EXPLORER_SETTINGS_FILE_NAME).mkdirs()

    val refused = runCatching { store.update(ExplorerSettings(recentsCollapsed = false)) }

    assertTrue(refused.exceptionOrNull() is java.io.IOException)
    assertEquals(ExplorerSettings(), store.observeSettings().first())
  }

  /**
   * An update replaces the store whole rather than rewriting it where it sits, so a kill mid-write
   * leaves the previous settings rather than half of them (ADR-0089).
   */
  @Test
  fun update_replacesTheFileRatherThanRewritingIt() = runTest {
    openStore().update(ExplorerSettings(recentsCollapsed = true))
    val taken = File(stateDir, EXPLORER_SETTINGS_FILE_NAME).linkTakenBeforeTheWrite(temp.root)
    val before = taken.readText()

    openStore().update(ExplorerSettings(recentsCollapsed = false))

    assertEquals(before, taken.readText())
  }
}
