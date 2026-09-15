package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.DocumentDismissAction
import com.jupydroid.app.ui.editor.ExecutionStatus

/**
 * The Console's own screen - P6b-9's first sub-step: [ConsoleTranscript] above, [ConsoleInputRow]
 * docked below, under a bar carrying the notebook's own title and [ConsoleActionsMenu]'s `Clear`.
 *
 * No navigation yet - [viewModel] is handed in whole rather than built here, the same split
 * [TerminalPaneScreen][com.jupydroid.app.ui.terminal.TerminalPaneScreen] draws between the screen
 * and its own factory default. Which kernel session it shares, and where a tap actually opens one,
 * are this sub-step's own two followers.
 *
 * ```kotlin
 * ConsoleScreen(title = notebookTitle, viewModel = consoleViewModel, onBack = { backStack.removeLastOrNull() })
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
  title: String,
  viewModel: ConsoleViewModel,
  onBack: () -> Unit,
  /** Whether a browser is drawn beside this document - see [DocumentDismissAction]. */
  twoPane: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val entries by viewModel.entries.collectAsStateWithLifecycle()
  val running = entries.lastOrNull()?.status == ExecutionStatus.RUNNING

  Scaffold(
    modifier = modifier.fillMaxSize(),
    // The document's own background, not a Material role - `docs/DECISIONS.md`'s rule every other
    // document screen in this tree already follows.
    containerColor = editorPalette.editorBackground,
    topBar = {
      TopAppBar(
        title = { Text(title) },
        // Not the bare "Back" `DocumentDismissAction`'s own KDoc warns against: a live-kernel
        // Maestro flow (P6b-9d) measured that string reaching the system navigation bar's own
        // back button instead of this one, which left the Console on screen. A distinct label
        // rather than a guess at why the tap missed - `EditorTopBar`'s "Back to notebooks" is the
        // precedent, singular here since a Console names the one notebook it was opened from.
        navigationIcon = {
          DocumentDismissAction(twoPane = twoPane, backDescription = "Back to notebook", onDismiss = onBack)
        },
        actions = { ConsoleActionsMenu(onClear = viewModel::clear) },
      )
    },
    bottomBar = {
      ConsoleInputRow(
        state = viewModel.input,
        running = running,
        onSubmit = viewModel::submit,
        onSourceEdited = viewModel::inputEdited,
        onRecallPreviousEntry = viewModel::recallPreviousEntry,
        onRecallNextEntry = viewModel::recallNextEntry,
        // `TerminalExtraKeysRow`'s own rule: the bar docked below the content owns the keyboard's
        // inset, so `Scaffold`'s own `padding` already reflects it and the content area does not
        // pay for it twice.
        modifier = Modifier.imePadding().navigationBarsPadding(),
      )
    },
  ) { padding ->
    ConsoleTranscript(entries = entries, modifier = Modifier.padding(padding).fillMaxSize())
  }
}
