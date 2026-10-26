package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDebugStack
import com.jupydroid.app.OpenDocumentOutline
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.OpenNotebookActiveCell
import com.jupydroid.app.data.fake.FakeDocumentSharer
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Does a real stop, reached through [EditorScreen]'s composed tree rather
 * than fed straight to a leaf composable, still surface [DebuggerControlBar] and its own call
 * stack and variables toggle at **one-pane width** - the phone/no-side-panel path every prior
 * earlier Maestro flow skipped, each having run with the tablet's side panel open.
 *
 * [EditorScreen.twoPane] and [EditorScreen.onShowDebugger] are passed directly rather than read
 * from the window, [PaneDirective.forcedToOnePane]'s own reason: `currentWindowAdaptiveInfo()`
 * measures the activity's window, which a test cannot force.
 */
@RunWith(AndroidJUnit4::class)
class EditorScreenDebuggerAtOnePaneWidthTest {
  @get:Rule val compose = createComposeRule()

  private val repository = FakeNotebookRepository()
  private val gateway = StallingKernelGateway()
  private val debugger = FakeKernelDebugger()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Test
  fun theControlBarAppearsOnceStoppedWithNoSidePanel() {
    showStoppedAtOnePaneWidth()

    compose.onNodeWithContentDescription("Continue").assertIsDisplayed()
  }

  @Test
  fun theVariablesToggleStaysReachableAndOpensTheRealStop() {
    showStoppedAtOnePaneWidth()

    compose.onNodeWithContentDescription(SHOW_DEBUGGER_PANEL).performClick()

    compose.onNodeWithText("<module> — line 1").assertIsDisplayed()
  }

  /** With a side panel available, the control bar offers no second way to see the same thing. */
  @Test
  fun theVariablesToggleIsAbsentWithASidePanelAvailable() {
    showStoppedWith(twoPane = true, onShowDebugger = {})

    compose.onNodeWithContentDescription("Continue").assertIsDisplayed()
    compose.onNodeWithContentDescription(SHOW_DEBUGGER_PANEL).assertDoesNotExist()
  }

  /**
   * A real step's own DAP shape - debugpy sends `Continued` before the next `Stopped`
   * (`NotebookDebuggerTest.watchForStopClearsOnAContinuedEvent`), so `state.stoppedAt` is
   * genuinely `null` for the width of that round trip. The sheet must not close because of it -
   * measured live on the tablet, not assumed: a Maestro run of the reverse of this opened the
   * sheet, stepped, and never saw the new frame, because the sheet's own `open`
   * state lived on a composable the transient `null` tore down and rebuilt as freshly closed.
   */
  @Test
  fun theVariablesSheetStaysOpenAcrossAStep() {
    showStoppedAtOnePaneWidth()
    compose.onNodeWithContentDescription(SHOW_DEBUGGER_PANEL).performClick()
    compose.onNodeWithText("<module> — line 1").assertIsDisplayed()

    compose.onNodeWithContentDescription("Step over").performClick()
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      runBlocking { debugger.emit(SESSION, DebugEvent.Continued) }
      stoppedViewModel.uiState.value.stoppedAt == null
    }
    // A real recomposition boundary crossed with `stoppedAt` genuinely null - the frame
    // `DebuggerBottomBar` was torn down in, not a same-tick conflation a fast fake could hide.
    compose.waitForIdle()
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      runBlocking { debugger.emit(SESSION, DebugEvent.Stopped(threadId = 7)) }
      stoppedViewModel.uiState.value.stoppedAt != null
    }

    compose.onNodeWithText("<module> — line 1").assertIsDisplayed()
  }

  private lateinit var stoppedViewModel: EditorViewModel

  /** Loads the notebook, runs its one breakpointed cell, and waits for a real stop to land. */
  private fun showStoppedAtOnePaneWidth() = showStoppedWith(twoPane = false, onShowDebugger = null)

  private fun showStoppedWith(twoPane: Boolean, onShowDebugger: (() -> Unit)?) {
    stoppedViewModel = editorViewModel()
    compose.setContent { EditorScreenUnderTest(stoppedViewModel, twoPane, onShowDebugger) }
    compose.onNodeWithContentDescription("Run all cells").performClick()
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      runBlocking { debugger.emit(SESSION, DebugEvent.Stopped(threadId = 7)) }
      stoppedViewModel.uiState.value.stoppedAt != null
    }
  }

  /** [EditorScreen] with the width/side-panel combination each case names. */
  @Composable
  private fun EditorScreenUnderTest(viewModel: EditorViewModel, twoPane: Boolean, onShowDebugger: (() -> Unit)?) {
    JupyterDroidTheme {
      EditorScreen(
        notebookId = NOTEBOOK_ID,
        notebookRepository = repository,
        workspaceRepository = FakeWorkspaceRepository(),
        kernelGateway = gateway,
        kernelDebugger = debugger,
        notebookExporter = FakeNotebookExporter(),
        documentSharer = FakeDocumentSharer(),
        applicationScope = scope,
        activeCellRuns = ActiveCellRuns(),
        editorSettingsRepository = FakeEditorSettingsRepository(),
        outline = OpenDocumentOutline(),
        onShowTableOfContents = null,
        debugPanel = OpenDebugStack(),
        activeCell = OpenNotebookActiveCell(),
        onShowDebugger = onShowDebugger,
        twoPane = twoPane,
        onGoToFolder = {},
        onBack = {},
        viewModel = viewModel,
      )
    }
  }

  /** A real [EditorViewModel], its one cell already carrying the breakpoint [BREAKABLE_CELL.id] needs to stop. */
  private fun editorViewModel(): EditorViewModel {
    runBlocking { repository.saveNotebook(Notebook(NOTEBOOK_ID, "Data", "python3", listOf(BREAKABLE_CELL))) }
    return EditorViewModel(
      notebookId = NOTEBOOK_ID,
      notebookRepository = repository,
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = gateway,
      notebookExporter = FakeNotebookExporter(),
      applicationScope = scope,
      activeCellRuns = ActiveCellRuns(),
      openDocuments = OpenDocuments(),
      notebookBreakpoints = NotebookBreakpoints(),
      kernelDebugger = debugger,
    ).also { it.debugger.toggle(BREAKABLE_CELL.id, line = 1) }
  }

  private companion object {
    const val NOTEBOOK_ID = "nb-1"
    val BREAKABLE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")

    /** [FakeKernelGateway.connect]'s own first answer for `NotebookKernel`'s own `BASE_SPACE`. */
    val SESSION = FakeKernelGateway.mints(sessionNumber = 1)

    const val LOAD_TIMEOUT_MILLIS = 5_000L
  }
}

/**
 * A cell that never reaches `Idle` on its own - [FakeKernelGateway]'s own `execute` completes
 * immediately, which would cancel [NotebookCellRuns.eventsFor]'s own debug-event watcher before a
 * scripted [DebugEvent.Stopped] ever mattered. A real kernel paused at a breakpoint is the same
 * shape: `execute_request` has not resolved either, which is why `RecordingKernelGateway`'s own
 * `runsUntilInterrupted` exists for the identical reason in the JVM suite (`NotebookDebuggerTest`).
 */
private class StallingKernelGateway(private val fake: FakeKernelGateway = FakeKernelGateway()) :
  KernelGateway by fake {
  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> =
    flow { awaitCancellation() }
}
