package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The picker at the level it is wired - `selectKernel` reached through a real [EditorScreen],
 * rather than the rows [KernelPickerDialogTest] asserts or the state [NotebookKernelPickerTest]
 * pins. Two doors are proven from here: `Context.initialize` -> `selectKernel`
 * (`docregistry/src/context.ts:599-603`) for a kernelspec the catalogue has never heard of, and
 * `kernelName`'s click for a notebook that is already bound (`toolbar/widget.tsx:155`).
 */
@RunWith(AndroidJUnit4::class)
class EditorScreenKernelPickerTest {
  @get:Rule val compose = createComposeRule()

  private val repository = FakeNotebookRepository()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /**
   * A saved `kernelspec.name` the Space has no spec for asks which kernel to run *on load* - the
   * reference's `initialize` does it, and the file is never POSTed a spec that does not exist.
   */
  @Test
  fun anUnresolvableSavedKernelOpensThePickerOnLoad() {
    showNotebook(kernelId = "a-kernel-that-was-uninstalled")

    compose.onNodeWithText("Select Kernel").assertIsDisplayed()
  }

  /**
   * `kernelName`'s click (`toolbar/widget.tsx:155`). Bound first - the fixture cell runs - so the
   * dialog opens over a live session and the bound row is the marked one.
   */
  @Test
  fun theIndicatorOpensThePickerForTheBoundKernel() {
    showNotebook(kernelId = "python3")
    compose.onNodeWithContentDescription("Run all cells").performClick()
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodesWithContentDescription("Kernel Python 3: Idle").fetchSemanticsNodes().isNotEmpty()
    }

    compose.onNodeWithContentDescription("Kernel Python 3: Idle").performClick()

    compose.onNodeWithText("Select Kernel").assertIsDisplayed()
    compose.onNodeWithText("Select kernel for: \"Data\"").assertIsDisplayed()
  }

  /** *Select* on the marked row re-binds the same spec - and the indicator still names it. */
  @Test
  fun pickingTheMarkedRowKeepsTheKernelItNamed() {
    showNotebook(kernelId = "python3")
    compose.onNodeWithContentDescription("Run all cells").performClick()
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodesWithContentDescription("Kernel Python 3: Idle").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription("Kernel Python 3: Idle").performClick()

    compose.onNodeWithText("Select").performClick()

    compose.onNodeWithContentDescription("Kernel Python 3: Idle").assertIsDisplayed()
  }

  private fun showNotebook(kernelId: String) {
    runBlocking {
      repository.saveNotebook(Notebook(NOTEBOOK_ID, "Data", kernelId, listOf(CODE_CELL)))
    }
    val viewModel = EditorViewModel(
      notebookId = NOTEBOOK_ID,
      notebookRepository = repository,
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      notebookExporter = FakeNotebookExporter(),
      applicationScope = scope,
      activeCellRuns = ActiveCellRuns(),
      openDocuments = OpenDocuments(),
      kernelSpecs = FakeKernelSpecs(),
    )
    compose.setContent {
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
          editorSettingsRepository = FakeEditorSettingsRepository(),
          kernelSpecs = FakeKernelSpecs(),
          outline = OpenDocumentOutline(),
          onShowTableOfContents = null,
          debugPanel = OpenDebugStack(),
          activeCell = OpenNotebookActiveCell(),
          onShowDebugger = null,
          onGoToFolder = {},
          onBack = {},
          viewModel = viewModel,
        )
      }
    }
  }

  private companion object {
    const val NOTEBOOK_ID = "nb-1"
    val CODE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
    const val LOAD_TIMEOUT_MILLIS = 5_000L
  }
}
