package com.jupydroid.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeErrorLog
import com.jupydroid.app.data.fake.FakeThemeSettingsRepository
import com.jupydroid.app.domain.model.AutosaveInterval
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.model.ThemeOverride
import com.jupydroid.app.domain.model.ThemeSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.ThemeSettingsRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.app.ui.PHONE_WIDTH
import com.jupydroid.app.ui.breaksInsideAWord
import com.jupydroid.app.ui.isCut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The six toggles moved out of `NotebookActionsMenu`'s View menu - each reads and writes
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

    compose.onNodeWithText(HEADING_NUMBERS).performScrollTo().performClick()

    compose.onNodeWithText(NUMBER_TOP_LEVEL_HEADINGS).performScrollTo().assertIsOn()
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

  /** See `EditorSettings.symbolRowShown`. */
  @Test
  fun symbolRowRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(symbolRowShown = false)))

    compose.onNodeWithText(SYMBOL_ROW).assertIsOff()
  }

  @Test
  fun tappingSymbolRowWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(symbolRowShown = true))
    startScreen(repository)

    compose.onNodeWithText(SYMBOL_ROW).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().symbolRowShown }
  }

  @Test
  fun wrapCodeLinesRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(codeWrapEnabled = false)))

    compose.onNodeWithText(WRAP_CODE_LINES).assertIsOff()
  }

  @Test
  fun tappingWrapCodeLinesWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(codeWrapEnabled = true))
    startScreen(repository)

    compose.onNodeWithText(WRAP_CODE_LINES).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().codeWrapEnabled }
  }

  @Test
  fun codeCompletionRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(completionEnabled = false)))

    compose.onNodeWithText(CODE_COMPLETION).assertIsOff()
  }

  @Test
  fun tappingCodeCompletionWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(completionEnabled = true))
    startScreen(repository)

    compose.onNodeWithText(CODE_COMPLETION).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().completionEnabled }
  }

  @Test
  fun tappingAutoClosingBracketsWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(autoClosingBrackets = true))
    startScreen(repository)

    compose.onNodeWithText(AUTO_CLOSING_BRACKETS).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().autoClosingBrackets }
  }

  /** `indentUnit` - the schema's own five options as segments - see `EditorSettings.indentUnit`. */
  @Test
  fun indentUnitRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(indentUnit = IndentUnit.SPACES_2)))

    compose.onNodeWithText(INDENT_UNIT).performScrollTo().assertIsDisplayed()
    compose.onNodeWithText(IndentUnit.SPACES_2.option).assertIsSelected()
    compose.onNodeWithText(IndentUnit.TAB.option).assertIsNotSelected()
  }

  @Test
  fun tappingAnIndentUnitWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(indentUnit = IndentUnit.SPACES_4))
    startScreen(repository)

    compose.onNodeWithText(IndentUnit.TAB.option).performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { repository.currentSettings().indentUnit == IndentUnit.TAB }
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

    compose.onNodeWithText(OUTPUT_HEADINGS).performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().tocIncludeOutput }
  }

  /** `docmanager-extension/schema/plugin.json`'s `autosave` - see `EditorSettings.autosaveEnabled`. */
  @Test
  fun autosaveDocumentsRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)))

    compose.onNodeWithText(AUTOSAVE_DOCUMENTS).assertIsOff()
  }

  @Test
  fun tappingAutosaveDocumentsWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = true))
    startScreen(repository)

    compose.onNodeWithText(AUTOSAVE_DOCUMENTS).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { !repository.currentSettings().autosaveEnabled }
  }

  /** `autosaveInterval` - "Length of save interval in seconds", shown the way `16sp` is. */
  @Test
  fun autosaveIntervalRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(autosaveIntervalSeconds = 60)))

    compose.onNodeWithText("60s").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun tappingIncreaseAutosaveIntervalWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(autosaveIntervalSeconds = 60))
    startScreen(repository)

    compose.onNodeWithContentDescription(INCREASE_AUTOSAVE_INTERVAL).performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) {
      repository.currentSettings().autosaveIntervalSeconds == 60 + AutosaveInterval.STEP
    }
  }

  @Test
  fun tappingDecreaseAutosaveIntervalWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(autosaveIntervalSeconds = 60))
    startScreen(repository)

    compose.onNodeWithContentDescription(DECREASE_AUTOSAVE_INTERVAL).performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) {
      repository.currentSettings().autosaveIntervalSeconds == 60 - AutosaveInterval.STEP
    }
  }

  /** `TerminalActionsMenu`'s own gate, read as disabled rather than hidden - see `SettingsStepperRow`. */
  @Test
  fun increaseAutosaveIntervalIsDisabledAtTheMaximum() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(autosaveIntervalSeconds = AutosaveInterval.MAX)))

    compose.onNodeWithContentDescription(INCREASE_AUTOSAVE_INTERVAL).assertIsNotEnabled()
  }

  @Test
  fun decreaseAutosaveIntervalIsDisabledAtTheMinimum() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(autosaveIntervalSeconds = AutosaveInterval.MIN)))

    compose.onNodeWithContentDescription(DECREASE_AUTOSAVE_INTERVAL).assertIsNotEnabled()
  }

  /** `kernelShutdown` - the schema's own "Shut down kernel" - see `EditorSettings.shutdownKernelOnClose`. */
  @Test
  fun shutdownKernelOnCloseRowShowsTheRepositorysCurrentValue() {
    startScreen(FakeEditorSettingsRepository(EditorSettings(shutdownKernelOnClose = true)))

    compose.onNodeWithText(SHUTDOWN_KERNEL_ON_CLOSE).performScrollTo().assertIsOn()
  }

  @Test
  fun tappingShutdownKernelOnCloseWritesTheRepository() {
    val repository = FakeEditorSettingsRepository(EditorSettings(shutdownKernelOnClose = false))
    startScreen(repository)

    compose.onNodeWithText(SHUTDOWN_KERNEL_ON_CLOSE).performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { repository.currentSettings().shutdownKernelOnClose }
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

  @Test
  fun everyThemeChoiceKeepsItsWholeWordOnAPhoneAtTwiceTheFontScale() {
    startPhoneScreenAt(fontScale = 2f)

    assertEveryChoiceIsWhole(THEME_CHOICES)
  }

  @Test
  fun everyThemeChoiceKeepsItsWholeWordOnAPhoneAtTheDefaultFontScale() {
    startPhoneScreenAt(fontScale = 1f)

    assertEveryChoiceIsWhole(THEME_CHOICES)
  }

  @Test
  fun everyIndentChoiceKeepsItsWholeWordOnAPhoneAtTwiceTheFontScale() {
    startPhoneScreenAt(fontScale = 2f)
    compose.onNodeWithText(INDENT_UNIT).performScrollTo()

    assertEveryChoiceIsWhole(INDENT_CHOICES)
  }

  @Test
  fun themeChoicesAreRadioButtonsForAScreenReader() {
    startScreen()

    THEME_CHOICES.forEach { label ->
      compose.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }
  }

  @Test
  fun tappingTheLicencesRowAsksToOpenThem() {
    var opened = 0
    startScreen(onOpenLicences = { opened++ })

    compose.onNodeWithText(LICENCES).performScrollTo().performClick()

    assertEquals(1, opened)
  }

  private fun startScreen(
    editorSettingsRepository: EditorSettingsRepository = FakeEditorSettingsRepository(),
    themeSettingsRepository: ThemeSettingsRepository = FakeThemeSettingsRepository(),
    onOpenLicences: () -> Unit = {},
    frame: @Composable (@Composable () -> Unit) -> Unit = { screen -> screen() },
  ) {
    compose.setContent {
      JupyterDroidTheme {
        frame {
          SettingsScreen(
            editorSettingsRepository = editorSettingsRepository,
            themeSettingsRepository = themeSettingsRepository,
            applicationScope = scope,
            onOpenLicences = onOpenLicences,
            diagnostics = DiagnosticsViewModel(FakeErrorLog()),
          )
        }
      }
    }
  }

  /** The screen at the phone emulator's width, because the tablet's pane has room the Theme row never runs out of. */
  private fun startPhoneScreenAt(fontScale: Float) =
    startScreen(frame = { screen -> Box(Modifier.requiredWidth(PHONE_WIDTH)) { AtFontScale(fontScale, screen) } })

  private fun assertEveryChoiceIsWhole(labels: List<String>) {
    labels.forEach { label ->
      assertFalse("$label is cut", choiceLabel(label).isCut())
      assertFalse("$label breaks inside a word", choiceLabel(label).breaksInsideAWord())
    }
  }

  /** The label's own node: the segment merges its descendants, so the text layout is only in the unmerged tree. */
  private fun choiceLabel(label: String): SemanticsNodeInteraction =
    compose.onNode(hasText(label), useUnmergedTree = true)

  private fun EditorSettingsRepository.currentSettings(): EditorSettings = runBlocking { observeSettings().first() }

  private fun ThemeSettingsRepository.currentSettings(): ThemeSettings = runBlocking { observeSettings().first() }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    val THEME_CHOICES = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
    val INDENT_CHOICES = IndentUnit.entries.map { it.option }
  }
}
