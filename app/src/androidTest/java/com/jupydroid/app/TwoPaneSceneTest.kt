package com.jupydroid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * That two panes are two panes: both entries on screen at once, and each still its own entry.
 *
 * `TwoPaneSceneStrategyTest` argues which entries get paired; what it cannot answer is whether
 * both actually draw, and whether the editor beside the list is still the *right* notebook.
 * That second question is the one this file exists for - `NotebookNavigationTest` records what
 * it costs to get wrong (defect D1: `viewModel { }` keys on the class name, so an editor that
 * stops being its own navigation entry is handed the previous notebook's ViewModel, id and all).
 *
 * `twoPane` is passed in, not measured. `currentWindowAdaptiveInfo()` reads the *activity's*
 * window, which a `DeviceConfigurationOverride` around a composable does not change, so *choosing*
 * the branch by forcing a width would drive one branch twice while looking like it covered both -
 * the same reasoning `CellActionsTest` carries.
 *
 * The **size** is still forced, which is a different thing: `ForcedSize` does change the layout
 * constraints, and [TwoPaneScene] gives its list pane a fixed 360dp. On the 320dp emulator that
 * leaves the detail pane no width at all, so both wide cases failed there while passing on the
 * tablet - a suite that quietly meant "whatever device you happened to run it on". The window a
 * test needs is part of the test.
 */
@RunWith(AndroidJUnit4::class)
class TwoPaneSceneTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun aWideWindowShowsTheListAndTheOpenNotebookAtOnce() {
    startAppWithTwoNotebooks(twoPane = true)

    compose.onNodeWithText(ALPHA_TITLE).performClick()

    waitForText(ALPHA_SOURCE)
    compose.onNodeWithText(ALPHA_SOURCE).assertIsDisplayed()
    // The list pane is still there beside it: the notebook that was *not* opened is on screen,
    // which the editor alone could never show.
    compose.onNodeWithText(BETA_TITLE).assertIsDisplayed()
  }

  @Test
  fun aNarrowWindowShowsOnlyTheOpenNotebook() {
    startAppWithTwoNotebooks(twoPane = false)

    compose.onNodeWithText(ALPHA_TITLE).performClick()

    waitForText(ALPHA_SOURCE)
    assertGone(BETA_TITLE)
  }

  /**
   * The defect this design exists to avoid, driven through the surface that makes it reachable.
   *
   * Only two panes let a second notebook be tapped while the first is open. It must *replace*
   * the detail: a stacked back stack would pair the last two entries and put a notebook where
   * the list belongs, and a shared ViewModel store would hand the second notebook the first
   * one's state.
   */
  @Test
  fun tappingAnotherNotebookInTheListPaneSwapsTheDetail() {
    startAppWithTwoNotebooks(twoPane = true)

    compose.onNodeWithText(ALPHA_TITLE).performClick()
    waitForText(ALPHA_SOURCE)
    compose.onNodeWithText(BETA_TITLE).performClick()

    waitForText(BETA_SOURCE)
    compose.onNodeWithText(BETA_SOURCE).assertIsDisplayed()
    assertGone(ALPHA_SOURCE)
  }

  /**
   * The list has to say which row the pane beside it belongs to.
   *
   * A need two panes create: until this step nothing was ever shown next to the list, so there
   * was nothing to point at. `selected` rather than a colour alone, so TalkBack says it too.
   */
  @Test
  fun theListPaneMarksTheNotebookThatIsOpen() {
    startAppWithTwoNotebooks(twoPane = true)

    compose.onNodeWithText(ALPHA_TITLE).performClick()

    waitForText(ALPHA_SOURCE)
    listRow(ALPHA_TITLE).assertIsSelected()
    listRow(BETA_TITLE).assertIsNotSelected()
  }

  /** Nothing is open, so nothing is marked - including on a wide window. */
  @Test
  fun theListPaneMarksNothingWhenNoNotebookIsOpen() {
    startAppWithTwoNotebooks(twoPane = true)

    listRow(ALPHA_TITLE).assertIsNotSelected()
    listRow(BETA_TITLE).assertIsNotSelected()
  }

  /**
   * The defect the step opened with, driven at the width that shows it: a wide window used to
   * pair Recents with the file browser, because `calculateScene` put any two last entries beside
   * each other. The browser is not a document the notebook home opens, so it draws alone.
   */
  @Test
  fun aWideWindowBrowseFilesDrawsTheExplorerAlone() {
    startAppWithAFileToBrowse(twoPane = true)

    compose.onNodeWithContentDescription("Browse files").performClick()

    waitForText("Files")
    assertGone("Recents")
  }

  /**
   * A file opened from the explorer pairs it the same way a notebook does: Explorer|FileEditor,
   * with the explorer's back arrow gone - it is the list pane, and the drawer's own arrow would
   * point nowhere.
   */
  @Test
  fun aWideWindowPairsTheExplorerWithTheFileItOpened() {
    startAppWithAFileToBrowse(twoPane = true)

    compose.onNodeWithContentDescription("Browse files").performClick()
    waitForText("Files")
    compose.onNodeWithText(FILE_NAME).performClick()

    waitForText(FILE_TEXT)
    compose.onNodeWithText(FILE_TEXT).assertIsDisplayed()
    // Still the two panes: only the explorer draws the "Workspace" crumb, and only the editor
    // draws the field's text - together they mean the browser is beside the document. (The file
    // name itself appears twice, row and title, so it cannot name the panes.)
    waitForText("Workspace")
    compose.onNodeWithText("Workspace").assertIsDisplayed()
    val backArrows = compose.onAllNodesWithContentDescription("Back to recents").fetchSemanticsNodes()
    assert(backArrows.isEmpty()) { "the explorer list pane still draws a back arrow" }
    assertGone("Recents")
  }

  /**
   * The row, not the app-bar title.
   *
   * Two panes put the open notebook's name on screen twice - once in the list and once above the
   * editor - so matching on text alone finds two nodes. Only the row is selectable.
   */
  private fun listRow(title: String) = compose.onNode(hasText(title) and isSelectable())

  private fun startAppWithTwoNotebooks(twoPane: Boolean) {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      // The id *is* the file name - suffix included, `Contents.IModel.path` - so these two
      // cannot differ.
      repository.saveNotebook(notebook(ALPHA_TITLE, ALPHA_SOURCE))
      repository.saveNotebook(notebook(BETA_TITLE, BETA_SOURCE))
    }
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowFor(twoPane))) {
        JupyterDroidTheme {
          MainNavigation(
            appContainer = AppContainer(notebookRepository = repository),
            importTitle = null,
            onImportConsumed = {},
            twoPane = twoPane,
          )
        }
      }
    }
  }

  private fun startAppWithAFileToBrowse(twoPane: Boolean) {
    val workspace = FakeWorkspaceRepository(
      entriesByPath = mapOf("" to listOf(WorkspaceEntry(FILE_NAME, FILE_NAME, WorkspaceEntryKind.FILE))),
      textByPath = mapOf(FILE_NAME to FILE_TEXT),
    )
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowFor(twoPane))) {
        JupyterDroidTheme {
          MainNavigation(
            appContainer = AppContainer(
              notebookRepository = DiskNotebookRepository(tempFolder.root),
              workspaceRepository = workspace,
            ),
            importTitle = null,
            onImportConsumed = {},
            twoPane = twoPane,
          )
        }
      }
    }
  }

  /** Expanded enough for 360dp of list and a readable notebook, or a phone that fits neither. */
  private fun windowFor(twoPane: Boolean) =
    if (twoPane) DpSize(900.dp, 800.dp) else DpSize(360.dp, 720.dp)

  private fun notebook(name: String, source: String) =
    Notebook(name, name, "python3", listOf(Cell("$name-c1", CellKind.CODE, source)))

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun assertGone(text: String) {
    val found = compose.onAllNodesWithText(text).fetchSemanticsNodes()
    assert(found.isEmpty()) { "expected '$text' to be gone, found ${found.size} node(s)" }
  }

  private companion object {
    const val ALPHA_TITLE = "Alpha notebook.ipynb"
    const val BETA_TITLE = "Beta notebook.ipynb"

    // Disjoint on purpose: neither string can be matched by the other notebook's cell.
    const val ALPHA_SOURCE = "print('first one only')"
    const val BETA_SOURCE = "print('second one only')"
    const val FILE_NAME = "script.py"
    const val FILE_TEXT = "print('plain of this file')"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
