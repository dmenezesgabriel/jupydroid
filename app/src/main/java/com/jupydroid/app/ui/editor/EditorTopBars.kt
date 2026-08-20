package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import com.jupydroid.app.domain.model.EditorSettings

/**
 * Which bar the notebook has along its top edge - one of three, never two.
 *
 * The top half of the rule [editorBottomBar] states for the bottom, and split out of
 * [EditorScreen] when detekt counted the branches: three contextual bars and their wiring is a
 * decision of its own, and the screen below it is about a document and its panes.
 *
 * **Find beats marking beats the document**, and the order is not arbitrary - each replaces the one
 * under it rather than adding to it, which is Material's contextual-bar rule and the reference's
 * too: JupyterLab's selection and search commands are never on the notebook's own toolbar. In
 * practice the states are already exclusive, since searching is asked for from the document's bar
 * and marking a run drops out of edit mode; the order is what keeps that true if they ever met.
 *
 * It takes the [viewModel] rather than twenty callbacks, which is the one place in this package
 * that does. This is the screen's own wiring lifted out whole for a file-length and complexity
 * gate; giving it a callback per control would have been a second copy of [EditorScreen]'s
 * parameter list to keep in step. Everything below it - [EditorCell] and its neighbours - still
 * takes callbacks.
 */
@Composable
internal fun EditorTopBars(
  state: EditorUiState,
  viewModel: EditorViewModel,
  editorSettings: EditorSettings,
  /** One callback for all four View-menu toggles, so this needs no scope and no repository. */
  onSettingsChanged: (EditorSettings) -> Unit,
  tocHeadings: List<TocHeading>,
  tocPresentation: TocPresentation,
  tocPaneOpen: Boolean,
  onToggleTocPane: () -> Unit,
  onHeadingSelected: (String) -> Unit,
  onBack: () -> Unit,
) {
  val head = state.cells.firstOrNull { it.id == state.selectedCellId }
  val searching = state.search
  when {
    searching != null -> FindTopBar(
      search = searching,
      counter = state.searchCounter(),
      onQueryChanged = viewModel.search::queryChanged,
      onPrevious = viewModel.search::previous,
      onNext = viewModel.search::next,
      onClose = viewModel.search::end,
      onToggleReplaceShown = viewModel.replace::toggleShown,
      onReplaceTextChanged = viewModel.replace::textChanged,
      onTogglePreserveCase = viewModel.replace::togglePreserveCase,
      onReplace = viewModel.replace::current,
      onReplaceAll = viewModel.replace::all,
      // Zero unless a run is marked, which a cell's own menu can still start while this bar is up.
      markedCells = if (state.selecting) state.selectedCellIds.size else 0,
      onToggleSearchOutputs = viewModel.search::toggleOutputs,
      onToggleSearchInSelection = viewModel.search::toggleInSelection,
    )

    state.selecting && head != null -> SelectionTopBar(
      selectedCount = state.selectedCellIds.size,
      headCell = head,
      canPaste = state.clipboard.isNotEmpty(),
      onDeselectAll = viewModel.selection::deselectAll,
      onSelectAll = viewModel.selection::selectAll,
      onExtend = viewModel.selection::extend,
      onCellAction = { action -> viewModel.edits.applyTo(head.id, action) },
      // The run itself lets the selection go as it starts, which is `runSelected`'s own last two
      // lines - so this bar goes away and the notebook's comes back while the cells run.
      onRunMarked = { viewModel.kernel.run(RunRange.MARKED) },
    )

    // Dropped only on a window too short to afford it while a keyboard is up - see
    // [editorTopBarVisible]. On a phone in landscape the alternative is a notebook with zero
    // height, which is what this was.
    isEditorTopBarVisible(state.mode) -> DocumentTopBar(
      state = state,
      viewModel = viewModel,
      editorSettings = editorSettings,
      onSettingsChanged = onSettingsChanged,
      tocHeadings = tocHeadings,
      tocPresentation = tocPresentation,
      tocPaneOpen = tocPaneOpen,
      onToggleTocPane = onToggleTocPane,
      onHeadingSelected = onHeadingSelected,
      onBack = onBack,
    )
  }
}

/** [EditorTopBar] with this notebook's state on it - its own function only to keep the choice above readable. */
@Composable
private fun DocumentTopBar(
  state: EditorUiState,
  viewModel: EditorViewModel,
  editorSettings: EditorSettings,
  onSettingsChanged: (EditorSettings) -> Unit,
  tocHeadings: List<TocHeading>,
  tocPresentation: TocPresentation,
  tocPaneOpen: Boolean,
  onToggleTocPane: () -> Unit,
  onHeadingSelected: (String) -> Unit,
  onBack: () -> Unit,
) {
  EditorTopBar(
    title = state.title,
    canUndo = state.undoable.isNotEmpty(),
    canRedo = state.redoable.isNotEmpty(),
    onRename = viewModel::renameNotebook,
    onUndo = viewModel.edits::undo,
    onRedo = viewModel.edits::redo,
    onBack = onBack,
    onSetAllHeadingsCollapsed = viewModel.edits::setAllHeadingsCollapsed,
    lineNumbersShown = state.lineNumbersShown,
    onToggleLineNumbers = viewModel::toggleLineNumbers,
    autoClosingBrackets = editorSettings.autoClosingBrackets,
    onToggleAutoClosingBrackets = {
      onSettingsChanged(editorSettings.copy(autoClosingBrackets = !editorSettings.autoClosingBrackets))
    },
    tableOfContentsHeadings = tocHeadings,
    onTableOfContentsHeadingSelected = onHeadingSelected,
    tableOfContentsPresentation = tocPresentation,
    tableOfContentsPaneOpen = tocPaneOpen,
    onToggleTableOfContentsPane = onToggleTocPane,
    tocNumbering = editorSettings.tocNumbering(),
    onToggleTocNumbering = {
      onSettingsChanged(editorSettings.copy(tocNumberHeaders = !editorSettings.tocNumberHeaders))
    },
    onToggleTocNumberingH1 = {
      onSettingsChanged(editorSettings.copy(tocNumberingH1 = !editorSettings.tocNumberingH1))
    },
    tocIncludeOutput = editorSettings.tocIncludeOutput,
    onToggleTocIncludeOutput = {
      onSettingsChanged(editorSettings.copy(tocIncludeOutput = !editorSettings.tocIncludeOutput))
    },
    onFind = viewModel.search::start,
  )
}
