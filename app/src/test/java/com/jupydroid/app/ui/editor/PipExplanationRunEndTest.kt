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

  /** A package whose every aarch64 wheel is built for musl - the kernel's glibc cannot run it. */
  private val muslOnlyReleases = PackageReleases(
    latestVersion = "1.0.0",
    wheelNamesByVersion = mapOf(
      "1.0.0" to listOf("muslonly-1.0.0-cp312-cp312-musllinux_1_2_aarch64.whl"),
    ),
  )

  @Test
  fun aConfirmedMissEndsTheCellWithTheNoteAsItsOwnOutputAndWritesIt() = runTest {
    val repository = WrittenNotebooks()
    val viewModel = loadedEditor(
      gateway = PipMissKernelGateway(),
      repository = repository,
      wheelIndex = FakeWheelIndex(mapOf("muslonly" to muslOnlyReleases)),
    )

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    val note = viewModel.uiState.value.outputs[codeCell.id].orEmpty().last() as? CellOutput.DisplayData
    assertTrue(
      note?.mimeBundle?.toString().orEmpty(),
      note?.mimeBundle?.toString().orEmpty().contains("manylinux aarch64"),
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
          "muslonly" to muslOnlyReleases,
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
    const val PIP_MISS = "\u001b[31mERROR: Could not find a version that satisfies the requirement muslonly " +
      "(from versions: none)\u001b[0m\n\u001b[31mERROR: No matching distribution found for muslonly\u001b[0m\n" +
      "Note: you may need to restart the kernel to use updated packages.\n"
  }
}
