package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.NotebookBreakpoints
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The debugger's own first visible slice (P6a-6): a breakpoint toggled in the gutter, kept for as
 * long as the app runs rather than for as long as the ViewModel that toggled it does. No kernel
 * behaviour here yet - that starts at P6a-7a.
 */
class NotebookDebuggerTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aCellStartsWithNoBreakpoints() = runTest {
    val viewModel = loadedEditor()

    assertEquals(emptyMap<String, List<Breakpoint>>(), viewModel.uiState.value.breakpointsByCellId)
  }

  @Test
  fun toggleArmsALineNotYetMarked() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))

    viewModel.debugger.toggle(codeCell.id, line = 2)

    assertEquals(
      listOf(Breakpoint(line = 2)),
      viewModel.uiState.value.breakpointsByCellId[codeCell.id],
    )
  }

  @Test
  fun toggleOnAnArmedLineDisarmsIt() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))
    viewModel.debugger.toggle(codeCell.id, line = 2)

    viewModel.debugger.toggle(codeCell.id, line = 2)

    assertEquals(emptyList<Breakpoint>(), viewModel.uiState.value.breakpointsByCellId[codeCell.id])
  }

  /**
   * The gate's own question: does a breakpoint survive the `EditorViewModel` that set it being
   * torn down and a new one built for the same notebook - which is what re-opening a notebook
   * after navigating away actually does (`NotebookBackStack` reveals rather than reopens).
   */
  @Test
  fun aBreakpointSurvivesTheViewModelBeingRebuiltForTheSameNotebook() = runTest {
    val shared = NotebookBreakpoints()
    val first = loadedEditor(cells = listOf(codeCell), notebookBreakpoints = shared)
    first.debugger.toggle(codeCell.id, line = 4)

    val second = loadedEditor(cells = listOf(codeCell), notebookBreakpoints = shared)

    assertEquals(
      listOf(Breakpoint(line = 4)),
      second.uiState.value.breakpointsByCellId[codeCell.id],
    )
  }

  @Test
  fun eachCellsBreakpointsAreKeptApart() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    viewModel.debugger.toggle(codeCell.id, line = 1)

    assertEquals(null, viewModel.uiState.value.breakpointsByCellId[secondCodeCell.id])
  }
}
