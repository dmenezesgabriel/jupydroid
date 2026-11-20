package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWheelIndex
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.PackageReleases
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What a finished run leaves behind when its cell printed pip's miss: the note as an output of its
 * own, written to the file - and nothing at all when PyPI does not confirm the platform is why.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PipExplanationRunEndTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val duckdbReleases = PackageReleases(
    latestVersion = "2.0.1",
    wheelNamesByVersion = mapOf(
      "2.0.1" to listOf("duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl"),
    ),
  )

  @Test
  fun aConfirmedMissEndsTheCellWithTheNoteAsItsOwnOutputAndWritesIt() = runTest {
    val repository = WrittenNotebooks()
    val viewModel = loadedEditor(
      gateway = PipMissKernelGateway(),
      repository = repository,
      wheelIndex = FakeWheelIndex(mapOf("duckdb" to duckdbReleases)),
    )

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    val note = viewModel.uiState.value.outputs[codeCell.id].orEmpty().last() as? CellOutput.DisplayData
    assertTrue(
      note?.mimeBundle?.toString().orEmpty(),
      note?.mimeBundle?.toString().orEmpty().contains("musllinux aarch64"),
    )
    val written = repository.written.last().cells.single().outputs.last()
    assertEquals(note, written)
  }

  /** pip's text for a typo is pip's text for a platform miss; only PyPI's answer keeps them apart. */
  @Test
  fun aNamePyPiDoesNotHaveEndsTheCellAsItWas() = runTest {
    val viewModel = loadedEditor(gateway = PipMissKernelGateway(), wheelIndex = FakeWheelIndex())

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    val outputs = viewModel.uiState.value.outputs[codeCell.id].orEmpty()
    assertTrue(outputs.toString(), outputs.all { it is CellOutput.Stream })
  }

  @Test
  fun aCellThatPrintedSomethingElseGetsNoNote() = runTest {
    val viewModel = loadedEditor(
      gateway = FakeKernelGateway(),
      wheelIndex = FakeWheelIndex(
        mapOf(
          "duckdb" to duckdbReleases,
        ),
      ),
    )

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    val outputs = viewModel.uiState.value.outputs[codeCell.id].orEmpty()
    assertTrue(outputs.toString(), outputs.all { it is CellOutput.Stream })
  }

  /** A kernel whose `%pip install` cell answers as the tablet measured it: pip's text, then idle. */
  private class PipMissKernelGateway : KernelGateway by FakeKernelGateway() {
    override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
      emit(KernelEvent.Output(cellId, CellOutput.Stream(StreamName.STDOUT, PIP_MISS)))
      emit(KernelEvent.Idle)
    }
  }

  /** Keeps every notebook written, so the test can say the note reached the file rather than the screen alone. */
  private class WrittenNotebooks(private val delegate: FakeNotebookRepository = FakeNotebookRepository()) :
    NotebookRepository by delegate {
    val written = mutableListOf<Notebook>()

    override suspend fun saveNotebook(notebook: Notebook, expected: FileStamp?): FileStamp =
      delegate.saveNotebook(notebook, expected).also { written += notebook }
  }

  private companion object {
    const val PIP_MISS = "\u001b[31mERROR: Could not find a version that satisfies the requirement duckdb " +
      "(from versions: none)\u001b[0m\n\u001b[31mERROR: No matching distribution found for duckdb\u001b[0m\n" +
      "Note: you may need to restart the kernel to use updated packages.\n"
  }
}
