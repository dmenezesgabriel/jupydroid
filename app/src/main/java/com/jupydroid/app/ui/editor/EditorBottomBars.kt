package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.SteppingCommand

/**
 * Which bar the notebook has along its bottom edge - one of two, or none.
 *
 * The mirror of [EditorTopBars], split out of [EditorScreen] for the same reason and by the same
 * gate: detekt counted the branches, and the wiring of the contextual bars is a decision of its own
 * while the screen below it is about a document and its panes. [editorBottomBar] owns the *rule*;
 * this owns what each bar is given.
 *
 * It takes the [viewModel] rather than twenty callbacks, exactly as [EditorTopBars] does and for
 * the reason stated there: this is the screen's own wiring lifted out whole, and a callback per
 * control would be a second copy of [EditorScreen]'s parameter list to keep in step.
 */
@Composable
internal fun EditorBottomBars(
  state: EditorUiState,
  viewModel: EditorViewModel,
  editorSettings: EditorSettings,
  /** The focused cell's field, which the symbol row types into - see [FocusedCellInput]. */
  cellInput: FocusedCellInput,
  /** `notebook:close-and-shutdown`'s second half; the shutdown is the first. */
  onBack: () -> Unit,
  /** Whether a side panel already shows the call stack and variables - see [DebuggerControlBar]. */
  twoPane: Boolean,
) {
  // Held here rather than inside [DebuggerBottomBar] itself: a real step's own DAP shape sends
  // `Continued` before the next `Stopped` (`NotebookDebuggerTest.watchForStopClearsOnAContinuedEvent`),
  // so `state.stoppedAt` is genuinely null for the width of that round trip and [editorBottomBar]
  // answers something other than DEBUGGER for exactly one recomposition - tearing down
  // [DebuggerBottomBar] and, with it, a `remember` owned there. [EditorBottomBars] itself never
  // leaves composition, so a toggle held here survives the same step that closed it when it lived
  // one level down - caught live on the tablet (`evidence/p6a-19/`), not guessed.
  var variablesOpen by remember { mutableStateOf(false) }
  when (editorBottomBar(state.selecting, state.mode, state.search != null, state.stoppedAt != null)) {
    EditorBottomBar.NONE -> Unit
    EditorBottomBar.DEBUGGER -> DebuggerBottomBar(
      state = state,
      viewModel = viewModel,
      twoPane = twoPane,
      variablesOpen = variablesOpen,
      onToggleVariables = { variablesOpen = !variablesOpen },
    )
    EditorBottomBar.SYMBOLS -> SymbolRow(
      input = cellInput,
      accessKernelHistory = editorSettings.accessKernelHistory,
      // The placeholder is read off the *field* rather than the notebook, for
      // [CellEditorText]'s reason: an edit reaches the model on a 150ms debounce, so the model
      // can be behind what the user is looking at. `focusedText` is null between a cell
      // losing focus and the mode leaving EDIT, and a tap in that frame does nothing.
      onRecallPreviousEntry = {
        cellInput.focusedText?.let { typed ->
          viewModel.kernel.history.recallPrevious(typed, cellInput::replaceAll)
        }
      },
      onRecallNextEntry = {
        cellInput.focusedText?.let { typed ->
          viewModel.kernel.history.recallNext(typed, cellInput::replaceAll)
        }
      },
    )
    EditorBottomBar.KERNEL -> NotebookBar(
      busy = state.runningCellId != null,
      onRunAll = { viewModel.kernel.run(RunRange.ALL) },
      onInterrupt = viewModel.kernel::stopRunningCell,
      onRestart = { viewModel.kernel.restart() },
      // Each of the three is the same restart with a different continuation, which is exactly
      // how the reference composes them (`index.ts:3245-3345`). The other two are in the menu.
      onRestartAndRunAll = viewModel.kernel::restartAndRunAll,
      // Everything behind the `⋮`, wired where the kernel indicator's copy is wired too - see
      // [notebookKernelCommands], which is what stops the two anchors from drifting apart.
      commands = notebookKernelCommands(state, viewModel, onBack),
    )
  }
}

/**
 * [DebuggerControlBar] and, while [variablesOpen], [DebuggerVariablesSheet] docked directly above
 * it - the one-pane adaptation of
 * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel]. The toggle itself is null on
 * a two-pane window: the side panel already shows this, and a second way to see the same stop
 * would be redundant chrome, not a convenience.
 *
 * [variablesOpen] is hoisted from [EditorBottomBars] rather than owned here - see that function's
 * own comment for why a `remember` at this level does not survive a step.
 */
@Composable
private fun DebuggerBottomBar(
  state: EditorUiState,
  viewModel: EditorViewModel,
  twoPane: Boolean,
  variablesOpen: Boolean,
  onToggleVariables: () -> Unit,
) {
  Column {
    if (!twoPane && variablesOpen) {
      DebuggerVariablesSheet(
        stack = state.debugStack,
        onExpand = viewModel.debugger::expand,
        onSelectFrame = viewModel.debugger::selectFrame,
        onEvaluate = viewModel.debugger::evaluate,
      )
    }
    DebuggerControlBar(
      stepping = state.stepping,
      onContinue = { viewModel.kernel.step(SteppingCommand.CONTINUE) },
      onNext = { viewModel.kernel.step(SteppingCommand.NEXT) },
      onStepIn = { viewModel.kernel.step(SteppingCommand.STEP_IN) },
      onStepOut = { viewModel.kernel.step(SteppingCommand.STEP_OUT) },
      onTerminate = {
        viewModel.debugger.clearStop()
        viewModel.kernel.restart()
      },
      onToggleVariables = if (twoPane) null else onToggleVariables,
    )
  }
}
