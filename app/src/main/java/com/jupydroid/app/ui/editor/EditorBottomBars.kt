package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import com.jupydroid.app.domain.model.EditorSettings

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
) {
  when (editorBottomBar(state.selecting, state.mode, state.search != null)) {
    EditorBottomBar.NONE -> Unit
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
      notebookTitle = state.title,
      onRunAll = { viewModel.kernel.run(RunRange.ALL) },
      onRun = viewModel.kernel::run,
      // Greyed rather than absent, so the menu's shape does not change under the thumb as the
      // head moves - and each says why by being disabled exactly where the reference disables it.
      canRunAllAbove = state.canRunAllAbove,
      canRunSelectedAndBelow = state.canRunSelectedAndBelow,
      // On the kernel bar although it asks the kernel nothing, because it is where the
      // reference files it too - the Kernel menu, beside the restart it composes with.
      onClearAllOutputs = viewModel.edits::clearAllOutputs,
      onInterrupt = viewModel.kernel::stopRunningCell,
      onRestart = { viewModel.kernel.restart() },
      // Each of the three is the same restart with a different continuation, which is exactly
      // how the reference composes them (`index.ts:3245-3345`).
      onRestartAndRunAll = viewModel.kernel::restartAndRunAll,
      // Composed here rather than on the kernel, and the asymmetry is the ownership: the two
      // that run cells need the runner `NotebookKernel` keeps private, while this one's
      // continuation is a document operation `NotebookCellEdits` owns.
      onRestartAndClear = { viewModel.kernel.restart(then = { viewModel.edits.clearAllOutputs() }) },
      onRestartAndRunTo = viewModel.kernel::restartAndRunTo,
      // Greyed with nothing selected: `restart-and-run-to-selected` is `isEnabledAndSingleSelected`
      // there, and with no head there is no "up to" to run.
      canRestartAndRunTo = state.selectedCellId != null,
      onShutdown = { viewModel.kernel.shutdown() },
      // The close is the shutdown's continuation rather than a second act, so it runs after
      // the session has actually ended - `notebook:close-and-shutdown` awaits it too.
      onCloseAndShutdown = { viewModel.kernel.shutdown(then = onBack) },
    )
  }
}
