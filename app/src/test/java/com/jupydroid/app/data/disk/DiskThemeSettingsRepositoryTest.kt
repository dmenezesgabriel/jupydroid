package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ThemeOverride
import com.jupydroid.app.domain.model.ThemeSettings
import com.jupydroid.app.domain.port.ThemeSettingsRepository
import com.jupydroid.app.domain.port.ThemeSettingsRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The disk store owes the port's contract, plus what only a real filesystem can be asked. */
class DiskThemeSettingsRepositoryTest : ThemeSettingsRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  private val stateDir: File get() = File(temp.root, "state")

  override fun repository(): ThemeSettingsRepository = openStore()

  private fun openStore() = DiskThemeSettingsRepository(stateDir)

  /** A theme a user chose is still chosen after the process dies. */
  @Test
  fun survivesTheNextLaunch() = runTest {
    openStore().update(ThemeSettings(override = ThemeOverride.DARK))

    val reopened = openStore().observeSettings().first()

    assertEquals(ThemeOverride.DARK, reopened.override)
  }

  /** A store written by something else must not take the app down on launch. */
  @Test
  fun anUnreadableStoreReadsAsTheDefault() = runTest {
    stateDir.mkdirs()
    File(stateDir, THEME_SETTINGS_FILE_NAME).writeText("{ not json")

    assertEquals(ThemeSettings(), openStore().observeSettings().first())
  }

  /**
   * Its own file, beside `editor-settings.json`, `terminal-settings.json` and
   * `explorer-settings.json` rather than folded into any of them: a theme choice is neither an
   * editor, a terminal nor an explorer preference.
   */
  @Test
  fun keepsItsOwnFile() = runTest {
    openStore().update(ThemeSettings(override = ThemeOverride.LIGHT))

    assertTrue(File(stateDir, THEME_SETTINGS_FILE_NAME).isFile)
  }

  /**
   * The file is the record and the flow repeats it - so a refused write announces nothing:
   * publishing before the write would leave every collector showing a value the next launch
   * never kept. A directory standing where the file belongs is a write the filesystem refuses.
   */
  @Test
  fun aRefusedWritePublishesNothing() = runTest {
    val store = openStore()
    File(stateDir, THEME_SETTINGS_FILE_NAME).mkdirs()

    val refused = runCatching { store.update(ThemeSettings(override = ThemeOverride.LIGHT)) }

    assertTrue(refused.exceptionOrNull() is java.io.IOException)
    assertEquals(ThemeSettings(), store.observeSettings().first())
  }

  /**
   * An update replaces the store whole rather than rewriting it where it sits, so a kill mid-write
   * leaves the previous settings rather than half of them (ADR-0089).
   */
  @Test
  fun update_replacesTheFileRatherThanRewritingIt() = runTest {
    openStore().update(ThemeSettings(override = ThemeOverride.LIGHT))
    val taken = File(stateDir, THEME_SETTINGS_FILE_NAME).linkTakenBeforeTheWrite(temp.root)
    val before = taken.readText()

    openStore().update(ThemeSettings(override = ThemeOverride.DARK))

    assertEquals(before, taken.readText())
  }
}
