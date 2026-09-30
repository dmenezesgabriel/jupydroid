package com.jupydroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.domain.model.AutosaveInterval
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.model.ThemeOverride
import com.jupydroid.app.domain.model.ThemeSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.ThemeSettingsRepository
import com.jupydroid.app.theme.editorPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [SettingsScreen]'s own root, so a test can find it without depending on its title text. */
const val SETTINGS_SCREEN_TAG = "settings_screen"

/**
 * The app-wide Settings screen - six rows, every persisted [EditorSettings] toggle that used to
 * sit in `NotebookActionsMenu`'s View menu, plus the theme row.
 *
 * **A `Switch` per row, not the View menu's flip-label text.** That convention existed because a
 * `DropdownMenuItem`'s toggled state carries no accessible label
 * (`NotebookActionsMenu.kt`'s own `SHOW_LINE_NUMBERS` doc comment) - a constraint of *that*
 * control, not of toggles in general. `Switch` states its own role and checked state to the
 * accessibility tree directly (`Role.Switch`, `assertIsOn`/`assertIsOff`), which is both this
 * platform's own idiom for a settings screen and the reference's: JupyterLab's Settings-menu rows
 * are exactly this shape - one label, one `isToggled` (`docmanager:toggle-autosave`,
 * `apputils:adaptive-theme`). The theme row is the one exception, since it is not binary - see
 * [ThemeOverrideRow]'s own doc comment.
 *
 * Reached by [openSettings][com.jupydroid.app.openSettings], stacked over whatever document is
 * open rather than replacing it - see that function's own doc comment.
 *
 * ```
 * SettingsScreen(editorSettingsRepository, themeSettingsRepository, applicationScope)
 * ```
 */
@Composable
fun SettingsScreen(
  editorSettingsRepository: EditorSettingsRepository,
  themeSettingsRepository: ThemeSettingsRepository,
  /** Writes must outlive this screen's own composition - a tap and a navigate-away can race. */
  applicationScope: CoroutineScope,
  modifier: Modifier = Modifier,
) {
  val settings by editorSettingsRepository.observeSettings().collectAsStateWithLifecycle(EditorSettings())
  fun update(changed: EditorSettings) {
    applicationScope.launch { editorSettingsRepository.update(changed) }
  }

  // The clamp `TerminalPaneViewModel.changeFontSize` does for the terminal's own field - there is
  // no ViewModel here, so this screen is where it has to live, the same division that function's
  // own doc comment draws between "the store" and "the screen that computes what to store."
  fun changeCodeFontSize(deltaSp: Int) {
    val next = (settings.codeFontSizeSp + deltaSp).coerceIn(CodeFontSize.MIN, CodeFontSize.MAX)
    if (next == settings.codeFontSizeSp) return
    update(settings.copy(codeFontSizeSp = next))
  }
  fun changeAutosaveInterval(deltaSeconds: Int) {
    val next = (settings.autosaveIntervalSeconds + deltaSeconds)
      .coerceIn(AutosaveInterval.MIN, AutosaveInterval.MAX)
    if (next == settings.autosaveIntervalSeconds) return
    update(settings.copy(autosaveIntervalSeconds = next))
  }
  val themeSettings by themeSettingsRepository.observeSettings().collectAsStateWithLifecycle(ThemeSettings())
  fun updateTheme(changed: ThemeOverride) {
    applicationScope.launch { themeSettingsRepository.update(ThemeSettings(override = changed)) }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag(SETTINGS_SCREEN_TAG),
    // The document's own background, not a Material role - every other document screen in this
    // tree already follows this rule.
    containerColor = editorPalette.editorBackground,
    // No top bar at all: the tab above names it (`title.label` is the strip's, not a panel
    // row's) and leaving it is the strip's own act - the ✕ at two panes, the arrow below. The
    // strip already consumed the status-bar insets, so the content needs no spacer either.
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
      ThemeOverrideRow(themeSettings.override, ::updateTheme)
      SettingsToggleRow(SHOW_LINE_NUMBERS, settings.lineNumbersShown) {
        update(settings.copy(lineNumbersShown = it))
      }
      SettingsToggleRow(SYMBOL_ROW, settings.symbolRowShown) {
        update(settings.copy(symbolRowShown = it))
      }
      SettingsStepperRow(
        label = CODE_FONT_SIZE,
        value = settings.codeFontSizeSp,
        unit = "sp",
        minimum = CodeFontSize.MIN,
        maximum = CodeFontSize.MAX,
        decreaseDescription = DECREASE_CODE_FONT_SIZE,
        increaseDescription = INCREASE_CODE_FONT_SIZE,
        onIncrease = { changeCodeFontSize(CodeFontSize.STEP) },
        onDecrease = { changeCodeFontSize(-CodeFontSize.STEP) },
      )
      SettingsToggleRow(WRAP_CODE_LINES, settings.codeWrapEnabled) {
        update(settings.copy(codeWrapEnabled = it))
      }
      SettingsToggleRow(AUTO_CLOSING_BRACKETS, settings.autoClosingBrackets) {
        update(settings.copy(autoClosingBrackets = it))
      }
      IndentUnitRow(settings.indentUnit) { update(settings.copy(indentUnit = it)) }
      SettingsToggleRow(CODE_COMPLETION, settings.completionEnabled) {
        update(settings.copy(completionEnabled = it))
      }
      SettingsToggleRow(KERNEL_HISTORY_ACCESS, settings.accessKernelHistory) {
        update(settings.copy(accessKernelHistory = it))
      }
      SettingsToggleRow(AUTOSAVE_DOCUMENTS, settings.autosaveEnabled) {
        update(settings.copy(autosaveEnabled = it))
      }
      // Stays visible with autosave off: the schema's own pair both show in its settings editor,
      // and an interval set ahead of the toggle is still the interval it starts with.
      SettingsStepperRow(
        label = AUTOSAVE_INTERVAL,
        value = settings.autosaveIntervalSeconds,
        unit = "s",
        minimum = AutosaveInterval.MIN,
        maximum = AutosaveInterval.MAX,
        decreaseDescription = DECREASE_AUTOSAVE_INTERVAL,
        increaseDescription = INCREASE_AUTOSAVE_INTERVAL,
        onIncrease = { changeAutosaveInterval(AutosaveInterval.STEP) },
        onDecrease = { changeAutosaveInterval(-AutosaveInterval.STEP) },
      )
      SettingsToggleRow(SHUTDOWN_KERNEL_ON_CLOSE, settings.shutdownKernelOnClose) {
        update(settings.copy(shutdownKernelOnClose = it))
      }
      SettingsToggleRow(HEADING_NUMBERS, settings.tocNumberHeaders) {
        update(settings.copy(tocNumberHeaders = it))
      }
      // Only while there are numbers for it to change - `NotebookActionsMenu`'s own rule for the
      // same row, moved here with it: with numbering off, both states of this row look identical.
      if (settings.tocNumberHeaders) {
        SettingsToggleRow(NUMBER_TOP_LEVEL_HEADINGS, settings.tocNumberingH1) {
          update(settings.copy(tocNumberingH1 = it))
        }
      }
      SettingsToggleRow(OUTPUT_HEADINGS, settings.tocIncludeOutput) {
        update(settings.copy(tocIncludeOutput = it))
      }
    }
  }
}

/**
 * One persisted preference: a label and a [Switch], the whole row toggling it - `Switch`'s own
 * documented pattern (`onCheckedChange = null` on the control itself, `Modifier.toggleable` on the
 * row), so a tap anywhere on the row reaches it, not only the small control.
 */
@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  ListItem(
    headlineContent = { Text(label) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
  )
}

/**
 * One persisted numeric preference: a label and two `IconButton`s either side of its current
 * value, each disabling itself at its own bound rather than disappearing -
 * [com.jupydroid.app.ui.terminal.TerminalActionsMenu]'s own convention for the terminal's font
 * size, the reference's own `isEnabled` gate read through a screen row instead of a menu one.
 */
@Composable
private fun SettingsStepperRow(
  label: String,
  value: Int,
  unit: String,
  minimum: Int,
  maximum: Int,
  decreaseDescription: String,
  increaseDescription: String,
  onIncrease: () -> Unit,
  onDecrease: () -> Unit,
) {
  ListItem(
    headlineContent = { Text(label) },
    trailingContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrease, enabled = value > minimum) {
          Icon(Icons.Filled.Remove, contentDescription = decreaseDescription)
        }
        Text("$value$unit")
        IconButton(onClick = onIncrease, enabled = value < maximum) {
          Icon(Icons.Filled.Add, contentDescription = increaseDescription)
        }
      }
    },
  )
}

/**
 * The theme row: a label and three always-visible, mutually exclusive choices -
 * `SingleChoiceSegmentedButtonRow`, Compose Material3's own widget for 2-5 such options, rather
 * than a dropdown that hides the two choices not currently picked.
 *
 * Collapses the reference's `adaptive-theme` boolean plus its two named-theme strings
 * (`apputils:adaptive-theme`, `packages/apputils-extension/schema/themes.json:123-153`) into one
 * tri-state choice - see `ThemeOverride`'s own doc comment for why that is a faithful adaptation
 * and not a simplification of behaviour.
 */
@Composable
private fun ThemeOverrideRow(selected: ThemeOverride, onSelect: (ThemeOverride) -> Unit) {
  ListItem(
    headlineContent = { Text(THEME) },
    supportingContent = {
      SingleChoiceSegmentedButtonRow {
        THEME_OPTIONS.forEachIndexed { index, (option, label) ->
          SegmentedButton(
            selected = selected == option,
            onClick = { onSelect(option) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = THEME_OPTIONS.size),
            label = { Text(label) },
          )
        }
      }
    },
  )
}

/**
 * The `indentUnit` row: five always-visible, mutually exclusive choices -
 * `SingleChoiceSegmentedButtonRow`, the same widget [ThemeOverrideRow] reaches for, and still
 * inside its documented 2-5 option range. The labels are the schema's own enum literals
 * (`'Tab'`, `'1'`, `'2'`, `'4'`, `'8'` - `packages/codemirror/src/extension.ts:707-720`), which
 * is why there is no `*_OPTION` constant block here: `IndentUnit.option` *is* the schema string,
 * not a translation of it.
 */
@Composable
private fun IndentUnitRow(selected: IndentUnit, onSelect: (IndentUnit) -> Unit) {
  ListItem(
    headlineContent = { Text(INDENT_UNIT) },
    supportingContent = {
      SingleChoiceSegmentedButtonRow {
        IndentUnit.entries.forEachIndexed { index, option ->
          SegmentedButton(
            selected = selected == option,
            onClick = { onSelect(option) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = IndentUnit.entries.size),
            label = { Text(option.option) },
          )
        }
      }
    },
  )
}

/** `indentUnit`'s own title (`extension.ts:707`) - see `EditorSettings.indentUnit`. */
internal const val INDENT_UNIT = "Indentation unit"

/** `apputils:adaptive-theme` - see `ThemeSettings.override`. */
internal const val THEME = "Theme"
internal const val THEME_SYSTEM = "System"
internal const val THEME_LIGHT = "Light"
internal const val THEME_DARK = "Dark"

private val THEME_OPTIONS = listOf(
  ThemeOverride.SYSTEM to THEME_SYSTEM,
  ThemeOverride.LIGHT to THEME_LIGHT,
  ThemeOverride.DARK to THEME_DARK,
)

/** `notebook:toggle-all-cell-line-numbers` - see `EditorSettings.lineNumbersShown`. */
internal const val SHOW_LINE_NUMBERS = "Show line numbers"

/** See `EditorSettings.symbolRowShown`. */
internal const val SYMBOL_ROW = "Symbol row"

/** See `EditorSettings.codeFontSizeSp`. */
internal const val CODE_FONT_SIZE = "Code font size"

/** The decrease button's accessible label - see `SettingsStepperRow`. */
internal const val DECREASE_CODE_FONT_SIZE = "Decrease code font size"

/** The increase button's accessible label - see `SettingsStepperRow`. */
internal const val INCREASE_CODE_FONT_SIZE = "Increase code font size"

/** See `EditorSettings.codeWrapEnabled`. */
internal const val WRAP_CODE_LINES = "Wrap code lines"

/** `fileeditor:toggle-autoclosing-brackets-universal` - see `EditorSettings.autoClosingBrackets`. */
internal const val AUTO_CLOSING_BRACKETS = "Auto-closing brackets"

/** See `EditorSettings.completionEnabled`. */
internal const val CODE_COMPLETION = "Code completion"

/** `accessKernelHistory` - see `EditorSettings.accessKernelHistory`. */
internal const val KERNEL_HISTORY_ACCESS = "Kernel history access"

/** `docmanager:toggle-autosave`'s own title - see `EditorSettings.autosaveEnabled`. */
internal const val AUTOSAVE_DOCUMENTS = "Autosave documents"

/** `autosaveInterval`'s title, `plugin.json:119-124` - see `EditorSettings.autosaveIntervalSeconds`. */
internal const val AUTOSAVE_INTERVAL = "Autosave interval"

/** The decrease button's accessible label - see `SettingsStepperRow`. */
internal const val DECREASE_AUTOSAVE_INTERVAL = "Decrease autosave interval"

/** The increase button's accessible label - see `SettingsStepperRow`. */
internal const val INCREASE_AUTOSAVE_INTERVAL = "Increase autosave interval"

/**
 * `kernelShutdown`'s row - the schema's "Shut down kernel" with its own description folded in
 * ("...when closing a notebook", `tracker.json:741-746`), since this screen's rows carry one
 * label and "Shut down kernel" alone reads as an action that fires now.
 */
internal const val SHUTDOWN_KERNEL_ON_CLOSE = "Shut down kernel on close"

/** `toc:display-numbering` - see `EditorSettings.tocNumberHeaders`. */
internal const val HEADING_NUMBERS = "Heading numbers"

/** `toc:display-h1-numbering` - see `EditorSettings.tocNumberingH1`. */
internal const val NUMBER_TOP_LEVEL_HEADINGS = "Number top-level headings"

/** `toc:display-outputs-numbering` - see `EditorSettings.tocIncludeOutput`. */
internal const val OUTPUT_HEADINGS = "Output headings"
