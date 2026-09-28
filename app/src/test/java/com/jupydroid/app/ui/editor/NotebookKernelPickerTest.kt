package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelSpecModels
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * `SessionContextDialogs.selectKernel` (`packages/apputils/src/sessioncontext.tsx:1398-1460`) -
 * opening the picker lists the catalogue and what is running, and its answer lands on
 * `changeKernel` or `shutdown` rather than on a new session of its own.
 */
class NotebookKernelPickerTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /** The groups `kernelOptions` builds - catalogue specs and the server's running sessions. */
  @Test
  fun openingThePickerListsSpecsAndWhatIsRunning() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = FakeKernelGateway(registry)
    val viewModel = loadedEditor(
      gateway = gateway,
      kernelSessions = FakeKernelSessions(registry),
      kernelSpecs = FakeKernelSpecs(CATALOGUE),
      applicationScope = this,
    )
    gateway.connect(BASE_SPACE, "Other.ipynb")

    viewModel.kernel.picker.open()
    advanceUntilIdle()

    assertPickerRows(viewModel)
  }

  /**
   * **Passive**: a picker asked before the kernel exists draws its rows from the catalogue
   * alone - `specsManager.specs` answers without starting anything, and the Space's own list is
   * the same answer when the server is stopped. A `connect` issued here would boot a runtime
   * for the sake of a dialog.
   */
  @Test
  fun openingThePickerStartsNothing() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.picker.open()
    advanceUntilIdle()

    assertEquals(emptyList<KernelChoice>(), gateway.connectsAskedFor)
    assertEquals(false, viewModel.uiState.value.kernelConnected)
  }

  /** The pick lands on `SessionContext.changeKernel` - the session keeps its id, gets a kernel. */
  @Test
  fun pickingASpecChangesTheKernel() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    val before = viewModel.kernel.attachConsole().opened

    viewModel.kernel.picker.open()
    advanceUntilIdle()
    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    assertEquals(listOf(KernelChoice.Spec("p8g")), gateway.kernelChanges)
    assertEquals(before?.serverSessionId, viewModel.kernel.attachConsole().opened?.serverSessionId)
    assertNull("the picker closed when its answer was taken", viewModel.uiState.value.kernelPicker)
  }

  /** A pick on a notebook that never ran is the *start* - `changeKernel` with no session. */
  @Test
  fun pickingAKernelOnAnUnboundNotebookStartsIt() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    assertEquals(listOf(KernelChoice.Spec("p8g")), gateway.connectsAskedFor)
  }

  /**
   * `model === null && !hasNoKernel -> shutdown()` (`:1447-1450`): "No Kernel" is the pick that
   * ends the session, not one that starts a kernel.
   */
  @Test
  fun pickingNoKernelShutsTheSessionDown() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.picker.pick(null)
    advanceUntilIdle()

    assertEquals(1, gateway.shutDownSessions.size)
    assertEquals("No Kernel", viewModel.uiState.value.kernelDisplayName)
  }

  /** "No Kernel" while already unbound is a no-op - `!hasNoKernel` guards the shutdown. */
  @Test
  fun pickingNoKernelWithNoneBoundEndsNothing() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.picker.pick(null)
    advanceUntilIdle()

    assertEquals(0, gateway.shutDownSessions.size)
    assertEquals(emptyList<KernelChoice>(), gateway.connectsAskedFor)
  }

  /** Cancel is the reference's early `return` - the dialog closes and nothing is asked. */
  @Test
  fun dismissingThePickerChangesNothing() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.picker.open()
    advanceUntilIdle()
    viewModel.kernel.picker.dismiss()

    assertNull(viewModel.uiState.value.kernelPicker)
    assertEquals(0, gateway.kernelChanges.size)
  }

  /**
   * `_isChangingKernel` reads as `'starting'` on the status (`sessioncontext.tsx:623-625`) -
   * for the seconds a PATCH takes, the indicator says a kernel is coming rather than none.
   */
  @Test
  fun aKernelChangeReadsAsStartingWhileInFlight() = runTest {
    val gateway = SlowToChangeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()
    assertEquals(KernelStatus.STARTING, viewModel.uiState.value.kernelStatus)

    gateway.letItChange()
    advanceUntilIdle()
    assertEquals(KernelStatus.IDLE, viewModel.uiState.value.kernelStatus)
  }

  /** The choice the running row carries is `KernelChoice.Running` - attach, not restart. */
  @Test
  fun pickingARunningKernelAttachesTheSessionToIt() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = FakeKernelGateway(registry)
    val viewModel = loadedEditor(
      gateway = gateway,
      kernelSessions = FakeKernelSessions(registry),
      applicationScope = this,
    )
    val other = gateway.connect(BASE_SPACE, "Other.ipynb")
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    val before = viewModel.kernel.attachConsole().opened

    viewModel.kernel.picker.pick(KernelChoice.Running(other.kernelId))
    advanceUntilIdle()

    val bound = viewModel.kernel.attachConsole().opened
    assertEquals(other.kernelId, bound?.kernelId)
    assertEquals(before?.serverSessionId, bound?.serverSessionId)
  }

  /** The rows `kernelOptions` draws once opened - asserted down to each option's text. */
  private fun assertPickerRows(viewModel: EditorViewModel) {
    val groups = viewModel.uiState.value.kernelPicker!!
    // `python3`'s spec name resolves to language "python" (`languages[preference.name]`), so the
    // groups split preferred/other - the catalogue language shape, not the flat one.
    assertEquals(
      listOf("Start python Kernel", "Use No Kernel", "Start Kernel", "Connect to Existing python Kernel"),
      groups.map { it.label },
    )
    assertEquals(listOf("Python 3"), groups[0].options.map { it.text })
    assertEquals(listOf("No Kernel"), groups[1].options.map { it.text })
    assertEquals(listOf("P8g Julia"), groups[2].options.map { it.text })
    assertEquals(listOf("Other.ipynb (fake)"), groups[3].options.map { it.text })
  }

  private companion object {
    val CATALOGUE = KernelSpecModels(
      default = "python3",
      specs = listOf(
        KernelSpec(name = "python3", displayName = "Python 3", language = "python"),
        KernelSpec(name = "p8g", displayName = "P8g Julia", language = "julia"),
      ),
    )
  }
}
