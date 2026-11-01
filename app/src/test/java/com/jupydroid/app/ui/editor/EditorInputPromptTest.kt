@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * A cell blocked in `input()`: the notebook holds the prompt while the run waits, and the answer
 * goes back to the kernel and stays in the cell as JupyterLab's echo.
 */
class EditorInputPromptTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aCellAskingForInputHoldsThePromptWhileItRuns() = runTest {
    val viewModel = loadedEditor(gateway = AskingUntilAnsweredKernelGateway())

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals("Name?", viewModel.uiState.value.inputRequests["c1"]?.prompt)
    assertEquals("c1", viewModel.uiState.value.runningCellId)
  }

  /** The reply carries the request whole, header included - the kernel matches it by that header. */
  @Test
  fun answeringRepliesToTheKernelWithTheRequestItAnswers() = runTest {
    val gateway = AskingUntilAnsweredKernelGateway()
    val viewModel = loadedEditor(gateway = gateway)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.answerInput("c1", "Ada")
    advanceUntilIdle()

    assertEquals(listOf(AskingUntilAnsweredKernelGateway.ASKED to "Ada"), gateway.replies)
  }

  /** The echo lands before what the cell prints with the answer - the order the cell ran in. */
  @Test
  fun answeringEchoesTheAnswerAndTheCellCarriesOn() = runTest {
    val viewModel = loadedEditor(gateway = AskingUntilAnsweredKernelGateway())
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.answerInput("c1", "Ada")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(
      listOf(
        CellOutput.Stream(StreamName.STDIN, "Name? Ada\n"),
        CellOutput.Stream(StreamName.STDOUT, "hi Ada\n"),
      ),
      state.outputs["c1"],
    )
    assertNull(state.inputRequests["c1"])
    assertNull(state.runningCellId)
  }

  /** Stop leaves nothing to answer: ipykernel raises `KeyboardInterrupt` and the run ends. */
  @Test
  fun stoppingACellThatIsAskingClosesThePrompt() = runTest {
    val viewModel = loadedEditor(gateway = AskingUntilAnsweredKernelGateway())
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.stopRunningCell()
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.inputRequests["c1"])
    assertEquals(ExecutionStatus.CANCELLED, viewModel.uiState.value.statusOf("c1"))
  }
}
