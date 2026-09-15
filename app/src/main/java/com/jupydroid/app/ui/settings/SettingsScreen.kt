package com.jupydroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.DocumentDismissAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [SettingsScreen]'s own root, so a test can find it without depending on its title text. */
const val SETTINGS_SCREEN_TAG = "settings_screen"

/**
 * The app-wide Settings screen - P6c-2's own six rows, every persisted [EditorSettings] toggle
 * that used to sit in `NotebookActionsMenu`'s View menu.
 *
 * **A `Switch` per row, not the View menu's flip-label text.** That convention existed because a
 * `DropdownMenuItem`'s toggled state carries no accessible label
 * (`NotebookActionsMenu.kt`'s own `SHOW_LINE_NUMBERS` doc comment) - a constraint of *that*
 * control, not of toggles in general. `Switch` states its own role and checked state to the
 * accessibility tree directly (`Role.Switch`, `assertIsOn`/`assertIsOff`), which is both this
 * platform's own idiom for a settings screen and the reference's: JupyterLab's Settings-menu rows
 * are exactly this shape - one label, one `isToggled` (`docmanager:toggle-autosave`,
 * `apputils:adaptive-theme`).
 *
 * Reached by [openSettings][com.jupydroid.app.openSettings], stacked over whatever document is
 * open rather than replacing it - see that function's own doc comment.
 *
 * ```
 * SettingsScreen(editorSettingsRepository, applicationScope, onBack = { backStack.removeLastOrNull() })
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  editorSettingsRepository: EditorSettingsRepository,
  /** Writes must outlive this screen's own composition - a tap and a navigate-away can race. */
  applicationScope: CoroutineScope,
  onBack: () -> Unit,
  twoPane: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val settings by editorSettingsRepository.observeSettings().collectAsStateWithLifecycle(EditorSettings())
  fun update(changed: EditorSettings) {
    applicationScope.launch { editorSettingsRepository.update(changed) }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag(SETTINGS_SCREEN_TAG),
    // The document's own background, not a Material role - every other document screen in this
    // tree already follows this rule (`docs/DECISIONS.md`).
    containerColor = editorPalette.editorBackground,
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          DocumentDismissAction(twoPane = twoPane, backDescription = "Back to files", onDismiss = onBack)
        },
      )
    },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
      SettingsToggleRow(SHOW_LINE_NUMBERS, settings.lineNumbersShown) {
        update(settings.copy(lineNumbersShown = it))
      }
      SettingsToggleRow(AUTO_CLOSING_BRACKETS, settings.autoClosingBrackets) {
        update(settings.copy(autoClosingBrackets = it))
      }
      SettingsToggleRow(KERNEL_HISTORY_ACCESS, settings.accessKernelHistory) {
        update(settings.copy(accessKernelHistory = it))
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

/** `notebook:toggle-all-cell-line-numbers` - see `EditorSettings.lineNumbersShown`. */
internal const val SHOW_LINE_NUMBERS = "Show line numbers"

/** `fileeditor:toggle-autoclosing-brackets-universal` - see `EditorSettings.autoClosingBrackets`. */
internal const val AUTO_CLOSING_BRACKETS = "Auto-closing brackets"

/** `accessKernelHistory` - see `EditorSettings.accessKernelHistory`. */
internal const val KERNEL_HISTORY_ACCESS = "Kernel history access"

/** `toc:display-numbering` - see `EditorSettings.tocNumberHeaders`. */
internal const val HEADING_NUMBERS = "Heading numbers"

/** `toc:display-h1-numbering` - see `EditorSettings.tocNumberingH1`. */
internal const val NUMBER_TOP_LEVEL_HEADINGS = "Number top-level headings"

/** `toc:display-outputs-numbering` - see `EditorSettings.tocIncludeOutput`. */
internal const val OUTPUT_HEADINGS = "Output headings"
