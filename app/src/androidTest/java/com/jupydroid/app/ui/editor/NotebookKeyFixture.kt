package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jupydroid.app.OpenDebugStack
import com.jupydroid.app.OpenDocumentOutline
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.OpenNotebookCells
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
import com.jupydroid.app.theme.JupydroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule

/**
 * The fixture the notebook's hardware-key test classes share: a real [EditorScreen] seeded from
 * [FakeNotebookRepository], its first field tapped into edit mode, and the three reads every key
 * assertion needs - the head's frame, the fields, and the frame's own focus flag.
 *
 * A base class rather than a function file because what is shared is stateful: the compose rule,
 * the seeded repository, and the [viewModel] each test asks for by name.
 */
@OptIn(ExperimentalTestApi::class) // `performKeyInput`'s own opt-in requirement
open class NotebookKeyFixture {
  @get:Rule val compose: ComposeContentTestRule = createComposeRule()

  protected val repository = FakeNotebookRepository()
  protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  protected lateinit var viewModel: EditorViewModel

  protected fun field(index: Int): SemanticsNodeInteraction = compose.onAllNodes(hasSetTextAction())[index]

  protected fun headFrame(): SemanticsNodeInteraction = compose.onNodeWithTag(HEAD_CELL_FRAME_TAG)

  protected fun cellFrame(cellId: String): SemanticsNodeInteraction =
    compose.onNode(SemanticsMatcher.expectValue(CellFrameIdKey, cellId))

  /**
   * The frame's own focus, waited for rather than asserted straight off: `requestFocus` settles
   * through the focus transaction's blur-then-grant events. It is read on [FrameOwnsFocusKey]
   * rather than `assertIsFocused`, because the framework's `focused` semantics only re-applies
   * when the node's semantics are invalidated - and a programmatic `requestFocus` invalidates
   * nothing, leaving the flag stale long after the target is Active.
   */
  protected fun assertFrameFocused() {
    compose.waitUntil(TIMEOUT_MILLIS) {
      runCatching {
        headFrame().fetchSemanticsNode().config[FrameOwnsFocusKey]
      }.getOrDefault(false)
    }
    headFrame().assert(SemanticsMatcher.expectValue(FrameOwnsFocusKey, true))
  }

  /** The notebook open on the given cells, the first field tapped, so it is being edited. */
  protected fun showEditing(
    vararg cells: Cell,
    settings: EditorSettings = EditorSettings(),
    onSaveAs: () -> Unit = {},
  ) {
    val settingsRepository = FakeEditorSettingsRepository(settings)
    viewModel = notebookViewModel(settingsRepository, *cells)
    compose.setContent { JupydroidTheme { editorScreen(viewModel, settingsRepository, onSaveAs) } }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.isNotEmpty() }
    field(0).performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.mode == CellMode.EDIT }
  }

  protected fun notebookViewModel(
    settingsRepository: FakeEditorSettingsRepository,
    vararg cells: Cell,
  ): EditorViewModel {
    runBlocking { repository.saveNotebook(Notebook(NOTEBOOK_ID, "Data", "python3", cells.toList())) }
    return EditorViewModel(
      notebookId = NOTEBOOK_ID,
      notebookRepository = repository,
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      notebookExporter = FakeNotebookExporter(),
      applicationScope = scope,
      activeCellRuns = ActiveCellRuns(),
      openDocuments = OpenDocuments(),
      editorSettings = settingsRepository,
    )
  }

  @Composable
  protected fun editorScreen(
    viewModel: EditorViewModel,
    settingsRepository: FakeEditorSettingsRepository,
    onSaveAs: () -> Unit,
  ) = EditorScreen(
    notebookId = NOTEBOOK_ID,
    notebookRepository = repository,
    workspaceRepository = FakeWorkspaceRepository(),
    kernelGateway = FakeKernelGateway(),
    kernelDebugger = FakeKernelDebugger(),
    notebookExporter = FakeNotebookExporter(),
    documentSharer = FakeDocumentSharer(),
    applicationScope = scope,
    activeCellRuns = ActiveCellRuns(),
    editorSettingsRepository = settingsRepository,
    outline = OpenDocumentOutline(),
    onShowTableOfContents = null,
    debugPanel = OpenDebugStack(),
    notebookCells = OpenNotebookCells(),
    onShowDebugger = null,
    onGoToFolder = {},
    onBack = {},
    onSaveAs = onSaveAs,
    viewModel = viewModel,
  )

  protected companion object {
    const val NOTEBOOK_ID = "nb-1"
    val CELL = Cell(id = "c1", kind = CellKind.CODE, source = "pass")
    val SECOND_CELL = Cell(id = "c2", kind = CellKind.CODE, source = "pass")
    val THIRD_CELL = Cell(id = "c3", kind = CellKind.CODE, source = "pass")
    const val TIMEOUT_MILLIS = 5_000L
  }
}
