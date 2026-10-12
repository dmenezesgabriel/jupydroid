package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.port.LauncherSettingsRepository
import com.jupydroid.app.domain.port.LauncherSettingsRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The disk store owes the port's contract, plus what only a real filesystem can be asked. */
class DiskLauncherSettingsRepositoryTest : LauncherSettingsRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  private val stateDir: File get() = File(temp.root, "state")

  override fun repository(): LauncherSettingsRepository = openStore()

  private fun openStore() = DiskLauncherSettingsRepository(stateDir)

  /**
   * A first run survived is still a first run survived after the process dies - which is the whole
   * reason this is a port and not a field on the navigation: without it every cold start would
   * push the launcher again.
   */
  @Test
  fun survivesTheNextLaunch() = runTest {
    openStore().update(LauncherSettings(launcherSeen = true))

    val reopened = openStore().observeSettings().first()

    assertEquals(true, reopened.launcherSeen)
  }

  /** A store written by something else must not take the app down on launch. */
  @Test
  fun anUnreadableStoreReadsAsTheDefault() = runTest {
    stateDir.mkdirs()
    File(stateDir, LAUNCHER_SETTINGS_FILE_NAME).writeText("{ not json")

    assertEquals(LauncherSettings(), openStore().observeSettings().first())
  }

  /**
   * Its own file, beside `explorer-settings.json` and `theme-settings.json` rather than folded
   * into either: whether the launcher has been shown is neither an explorer preference nor a theme.
   */
  @Test
  fun keepsItsOwnFile() = runTest {
    openStore().update(LauncherSettings(launcherSeen = true))

    assertTrue(File(stateDir, LAUNCHER_SETTINGS_FILE_NAME).isFile)
  }

  /**
   * The file is the record and the flow repeats it - so a refused write announces nothing:
   * publishing before the write would leave every collector showing a value the next launch
   * never kept. A directory standing where the file belongs is a write the filesystem refuses.
   */
  @Test
  fun aRefusedWritePublishesNothing() = runTest {
    val store = openStore()
    File(stateDir, LAUNCHER_SETTINGS_FILE_NAME).mkdirs()

    val refused = runCatching { store.update(LauncherSettings(launcherSeen = true)) }

    assertTrue(refused.exceptionOrNull() is java.io.IOException)
    assertEquals(LauncherSettings(), store.observeSettings().first())
  }

  /**
   * An update replaces the store whole rather than rewriting it where it sits, so a kill mid-write
   * leaves the previous settings rather than half of them (ADR-0089).
   */
  @Test
  fun update_replacesTheFileRatherThanRewritingIt() = runTest {
    openStore().update(LauncherSettings(launcherSeen = true))
    val taken = File(stateDir, LAUNCHER_SETTINGS_FILE_NAME).linkTakenBeforeTheWrite(temp.root)
    val before = taken.readText()

    openStore().update(LauncherSettings(launcherSeen = false))

    assertEquals(before, taken.readText())
  }
}
