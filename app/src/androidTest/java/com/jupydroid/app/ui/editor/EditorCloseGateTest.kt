package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDebugStack
import com.jupydroid.app.OpenDocumentOutline
import com.jupydroid.app.OpenDocuments
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
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `saveBeforeClose` gate on the notebook's three ways out: the dismiss arrow, system Back,
 * and `notebook:close-and-shutdown` - whose kernel question comes first and whose close is the
 * same gated close (`docmanager/src/widgetmanager.ts:342-388` sits at the end of
 * `close-and-shutdown` too, `notebook-extension/src/index.ts:3189-3213`).
 *
 * Autosave is seeded off, exactly as `FileEditorCloseGateTest` explains: with it on the debounce
 * clears `dirty` before the gate can be observed and the case becomes a race rather than a state.
 */
@RunWith(AndroidJUnit4::class)
class EditorCloseGateTest {
  @get:Rule val compose = createComposeRule()

  private val repository = FakeNotebookRepository()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private lateinit var viewModel: EditorViewModel
  private var left = 0

  @Test
  fun aCleanNotebookClosesWithoutAsking() {
    show()

    compose.onNodeWithContentDescription("Back to notebooks").performClick()

    assertEquals(1, left)
  }

  @Test
  fun dismissOnADirtyNotebookAsksInstead() {
    show()
    dirty()

    compose.onNodeWithContentDescription("Back to notebooks").performClick()

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    assertEquals(0, left)
  }

  @Test
  fun systemBackOnADirtyNotebookAsksToo() {
    show()
    dirty()

    Espresso.pressBack()

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    assertEquals(0, left)
  }

  @Test
  fun discardClosesAndKeepsTheEditOffDisk() {
    show()
    dirty()
    compose.onNodeWithContentDescription("Back to notebooks").performClick()

    compose.onNodeWithText("Discard").performClick()

    assertEquals(1, left)
    assertEquals("pass", runBlocking { repository.readNotebook(NOTEBOOK_ID).cells[0].source })
  }

  /** Save writes the notebook first; `onBack` only runs once nothing is owed. */
  @Test
  fun saveWritesThenCloses() {
    show()
    dirty()
    compose.onNodeWithContentDescription("Back to notebooks").performClick()

    compose.onNodeWithText("Save").performClick()
    compose.waitForIdle()

    assertEquals(1, left)
    assertEquals("x = 1", runBlocking { repository.readNotebook(NOTEBOOK_ID).cells[0].source })
  }

  /**
   * `notebook:close-and-shutdown` is a close too: its own question is about the kernel, and the
   * dirty-document question comes after it - the reference awaits the shutdown *then* runs the
   * close path, dialog included.
   */
  @Test
  fun closeAndShutdownAlsoAsksBeforeLeaving() {
    show()
    dirty()

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText("Close and Shut Down Notebook…").performClick()
    compose.onNodeWithText("Shut Down").performClick()

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    assertEquals(0, left)
  }

  /** An edit through the real path - what a keystroke in a code cell ends up calling. */
  private fun dirty() {
    viewModel.editCell(CELL.id, "x = 1")
    compose.waitForIdle()
  }

  private fun show() {
    runBlocking { repository.saveNotebook(Notebook(NOTEBOOK_ID, "Data", "python3", listOf(CELL))) }
    viewModel =
      EditorViewModel(
        notebookId = NOTEBOOK_ID,
        notebookRepository = repository,
        workspaceRepository = FakeWorkspaceRepository(),
        kernelGateway = FakeKernelGateway(),
        notebookExporter = FakeNotebookExporter(),
        kernelDebugger = FakeKernelDebugger(),
        applicationScope = scope,
        activeCellRuns = ActiveCellRuns(),
        editorSettings = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
      )
    compose.setContent { EditorScreenUnderTest(viewModel) }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) { viewModel.uiState.value.cells.isNotEmpty() }
  }

  /** [EditorScreen] at one-pane width, where the dismiss is the labelled arrow. */
  @Composable
  private fun EditorScreenUnderTest(viewModel: EditorViewModel) {
    JupyterDroidTheme {
      EditorScreen(
        notebookId = NOTEBOOK_ID,
        notebookRepository = repository,
        workspaceRepository = FakeWorkspaceRepository(),
        kernelGateway = FakeKernelGateway(),
        kernelDebugger = FakeKernelDebugger(),
        notebookExporter = FakeNotebookExporter(),
        documentSharer = FakeDocumentSharer(),
        applicationScope = scope,
        activeCellRuns = ActiveCellRuns(),
        editorSettingsRepository = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
        openDocuments = OpenDocuments(),
        outline = OpenDocumentOutline(),
        onShowTableOfContents = null,
        debugPanel = OpenDebugStack(),
        onShowDebugger = null,
        twoPane = false,
        onGoToFolder = {},
        onBack = { left += 1 },
        viewModel = viewModel,
      )
    }
  }

  private companion object {
    const val NOTEBOOK_ID = "notes"
    const val LOAD_TIMEOUT_MILLIS = 5_000L
    val CELL = Cell(id = "c1", kind = CellKind.CODE, source = "pass")
  }
}
