package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.editor.ExecutionStatus

/**
 * The Console's own screen - P6b-9's first sub-step: [ConsoleTranscript] above, [ConsoleInputRow]
 * docked below, under a bar carrying only actions ([ConsoleActionsMenu]'s `Clear`) - the name
 * is the tab's now, the way the reference keeps no title panel under a tab strip.
 *
 * No navigation yet - [viewModel] is handed in whole rather than built here, the same split
 * [TerminalPaneScreen][com.jupydroid.app.ui.terminal.TerminalPaneScreen] draws between the screen
 * and its own factory default. Which kernel session it shares, and where a tap actually opens one,
 * are this sub-step's own two followers.
 *
 * ```kotlin
 * ConsoleScreen(viewModel = consoleViewModel)
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
  viewModel: ConsoleViewModel,
  /** `fileeditor:toggle-…-universal` reaches the Console too - its field's rules come from here. */
  editorSettingsRepository: EditorSettingsRepository,
  modifier: Modifier = Modifier,
) {
  val entries by viewModel.entries.collectAsStateWithLifecycle()
  val running = entries.lastOrNull()?.status == ExecutionStatus.RUNNING
  // The universal toggle's own scope: the notebook's cells, the file editor and this field all
  // read the same pair, where this row used to compose `CodeCellField`'s defaults instead.
  val editorSettings by editorSettingsRepository.observeSettings()
    .collectAsStateWithLifecycle(EditorSettings())

  Scaffold(
    modifier = modifier.fillMaxSize(),
    // The document's own background, not a Material role - the rule every other
    // document screen in this tree already follows.
    containerColor = editorPalette.editorBackground,
    topBar = {
      TopAppBar(
        // The tab above names this Console - both references keep it a distinct widget kind
        // rather than a title row inside the document (a separate tab kind in JupyterLab, a
        // separate document type in VS Code's Interactive Window), so nothing here repeats it.
        title = {},
        // No navigation icon - leaving is the strip's own act, and the arrow there names the
        // destination itself (`upDestinationLabel`), which is P6b-9d's fix carried up a level:
        // a bare "Back" label here kept reaching the system navigation bar's own button in
        // `uiautomator` dumps.
        actions = {
          ConsoleActionsMenu(onClear = viewModel::clear, onInterrupt = viewModel::interrupt, running = running)
        },
      )
    },
    bottomBar = {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Separates the live input from the scrolled-back transcript above it - the reference's
        // own `.jp-CodeConsole-input` box-shadow (`packages/console/style/base.css:79-89`, "matches
        // the box shadow on the notebook toolbar"), adapted to a border since this app already
        // prefers one over an inset shadow Compose cannot draw (`CellEditorBox.kt`'s own reasoning).
        HorizontalDivider()
        ConsoleInputRow(
          state = viewModel.input,
          running = running,
          onSubmit = viewModel::submit,
          onSourceEdited = viewModel::inputEdited,
          onRecallPreviousEntry = viewModel::recallPreviousEntry,
          onRecallNextEntry = viewModel::recallNextEntry,
          autoClosingBrackets = editorSettings.autoClosingBrackets,
          indentUnit = editorSettings.indentUnit,
          // `TerminalExtraKeysRow`'s own rule: the bar docked below the content owns the
          // keyboard's inset, so `Scaffold`'s own `padding` already reflects it and the content
          // area does not pay for it twice.
          modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
        )
      }
    },
  ) { padding ->
    ConsoleTranscript(entries = entries, modifier = Modifier.padding(padding).fillMaxSize())
  }
}
