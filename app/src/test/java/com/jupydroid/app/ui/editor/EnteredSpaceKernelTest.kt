package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A notebook opened inside an entered Space asks that Space for its kernel, its catalogue and its
 * picker rows - never base. [THESIS] declares a kernel base does not, so a base answer shows.
 */
class EnteredSpaceKernelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aCellRunsOnTheEnteredSpacesKernel() = runTest {
    val viewModel = loadedEditor(
      gateway = FakeKernelGateway(),
      notebook = SAVED_ON_THESIS,
      space = THESIS,
      applicationScope = this,
    )

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals("space-1", viewModel.kernel.attachConsole().opened?.spaceId)
  }

  @Test
  fun thePickerListsTheEnteredSpacesKernels() = runTest {
    val viewModel = loadedEditor(space = THESIS, applicationScope = this)

    viewModel.kernel.picker.open()
    advanceUntilIdle()

    val rows = viewModel.uiState.value.kernelPicker!!.flatMap { group -> group.options.map { it.text } }
    assertEquals(true, "Thesis Python" in rows)
  }

  /** The saved spec resolves against the entered Space's catalogue, so a Thesis notebook owes no pick there. */
  @Test
  fun aNotebookSavedOnTheEnteredSpacesKernelOwesNoPick() = runTest {
    val viewModel = loadedEditor(
      notebook = SAVED_ON_THESIS,
      space = THESIS,
      applicationScope = this,
    )

    assertEquals(0, viewModel.uiState.value.kernelPickRequired)
  }

  private companion object {
    val SAVED_ON_THESIS = Notebook("nb-1", "Data", "thesis-python", listOf(codeCell))

    val THESIS = Space(
      id = "space-1",
      name = "Thesis",
      kernels = listOf(KernelSpec(name = "thesis-python", displayName = "Thesis Python", language = "python")),
    )
  }
}
