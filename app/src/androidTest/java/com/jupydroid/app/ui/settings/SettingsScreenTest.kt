package com.jupydroid.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeThemeSettingsRepository
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.ThemeOverride
import com.jupydroid.app.domain.model.ThemeSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.ThemeSettingsRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The six toggles P6c-2 moved out of `NotebookActionsMenu`'s View menu - each reads and writes
 * [EditorSettingsRepository] directly, the same repository the notebook's own bars already share.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
  @get:Rule val compose = createComposeRule()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Test
  fun rowsShowTheRepositorysCurrentValues() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(autoClosingBrackets = false, tocNumberHeaders = true)))

    compose.onNodeWithText(SHOW_LINE_NUMBERS).assertIsOn()
    compose.onNodeWithText(AUTO_CLOSING_BRACKETS).assertIsOff()
    compose.onNodeWithText(KERNEL_HISTORY_ACCESS).assertIsOff()
    compose.onNodeWithText(HEADING_NUMBERS).assertIsOn()
    // Only offered while there is numbering for it to refine - `NotebookActionsMenu`'s own rule.
    compose.onNodeWithText(NUMBER_TOP_LEVEL_HEADINGS).assertIsOn()
    compose.onNodeWithText(OUTPUT_HEADINGS).assertIsOn()
  }

  @Test
  fun theTopLevelHeadingRowIsHiddenUntilHeadingNumbersIsOn() {
    val repository = FakeEditorSettingsRepository(EditorSettings(tocNumberHeaders = false))
    startScreen(repository)
    compose.onNodeWithText(NUMBER_TOP_LEVEL_HEADINGS).assertDoesNotExist()

    compose.onNodeWithText(HEADING_NUMBERS).performClick()

    compose.onNodeWithText(NUMBER_TOP_LEVEL_HEADINGS).assertIsOn()
  }

  @Test
  fun tappingLineNumbersWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(lineNumbersShown = true))
    startScreen(repository)

    compose.onNodeWithText(SHOW_LINE_NUMBERS).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().lineNumbersShown }
  }

  @Test
  fun codeFontSizeRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(codeFontSizeSp = 16)))

    compose.onNodeWithText("16sp").assertIsDisplayed()
  }

  @Test
  fun tappingIncreaseCodeFontSizeWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(codeFontSizeSp = 16))
    startScreen(repository)

    compose.onNodeWithContentDescription(INCREASE_CODE_FONT_SIZE).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { repository.currentSettings().codeFontSizeSp == 16 + CodeFontSize.STEP }
  }

  @Test
  fun tappingDecreaseCodeFontSizeWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(codeFontSizeSp = 16))
    startScreen(repository)

    compose.onNodeWithContentDescription(DECREASE_CODE_FONT_SIZE).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { repository.currentSettings().codeFontSizeSp == 16 - CodeFontSize.STEP }
  }

  /** `TerminalActionsMenu`'s own gate, read as disabled rather than hidden - see `SettingsStepperRow`. */
  @Test
  fun increaseCodeFontSizeIsDisabledAtTheMaximum() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(codeFontSizeSp = CodeFontSize.MAX)))

    compose.onNodeWithContentDescription(INCREASE_CODE_FONT_SIZE).assertIsNotEnabled()
  }

  @Test
  fun decreaseCodeFontSizeIsDisabledAtTheMinimum() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(codeFontSizeSp = CodeFontSize.MIN)))

    compose.onNodeWithContentDescription(DECREASE_CODE_FONT_SIZE).assertIsNotEnabled()
  }

  @Test
  fun tappingAutoClosingBracketsWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(autoClosingBrackets = true))
    startScreen(repository)

    compose.onNodeWithText(AUTO_CLOSING_BRACKETS).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().autoClosingBrackets }
  }

  @Test
  fun tappingKernelHistoryAccessWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(accessKernelHistory = false))
    startScreen(repository)

    compose.onNodeWithText(KERNEL_HISTORY_ACCESS).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { repository.currentSettings().accessKernelHistory }
  }

  @Test
  fun tappingOutputHeadingsWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(tocIncludeOutput = true))
    startScreen(repository)

    compose.onNodeWithText(OUTPUT_HEADINGS).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().tocIncludeOutput }
  }

  @Test
  fun themeRowShowsTheRepositorysCurrentValue() {
    startScreen(themeSettingsRepository = FakeThemeSettingsRepository(ThemeSettings(override = ThemeOverride.DARK)))

    compose.onNodeWithText(THEME_DARK).assertIsSelected()
    compose.onNodeWithText(THEME_LIGHT).assertIsNotSelected()
    compose.onNodeWithText(THEME_SYSTEM).assertIsNotSelected()
  }

  @Test
  fun tappingLightWritesTheThemeRepository() {
    val repository = FakeThemeSettingsRepository(ThemeSettings(override = ThemeOverride.SYSTEM))
    startScreen(themeSettingsRepository = repository)

    compose.onNodeWithText(THEME_LIGHT).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { repository.currentSettings().override == ThemeOverride.LIGHT }
  }

  @Test
  fun tappingSystemAfterDarkWritesTheThemeRepository() {
    val repository = FakeThemeSettingsRepository(ThemeSettings(override = ThemeOverride.DARK))
    startScreen(themeSettingsRepository = repository)

    compose.onNodeWithText(THEME_SYSTEM).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { repository.currentSettings().override == ThemeOverride.SYSTEM }
  }

  private fun startScreen(
    editorSettingsRepository: EditorSettingsRepository = FakeEditorSettingsRepository(),
    themeSettingsRepository: ThemeSettingsRepository = FakeThemeSettingsRepository(),
  ) {
    compose.setContent {
      JupyterDroidTheme {
        SettingsScreen(
          editorSettingsRepository = editorSettingsRepository,
          themeSettingsRepository = themeSettingsRepository,
          applicationScope = scope,
          onBack = {},
        )
      }
    }
  }

  private fun EditorSettingsRepository.currentSettings(): EditorSettings = runBlocking { observeSettings().first() }

  private fun ThemeSettingsRepository.currentSettings(): ThemeSettings = runBlocking { observeSettings().first() }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
