package com.jupydroid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
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
 * window, which a `DeviceConfigurationOverride` around a composable does not change, so forcing
 * a width here would drive one branch twice while looking like it covered both - the same
 * reasoning `CellActionsTest` carries.
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
   * The row, not the app-bar title.
   *
   * Two panes put the open notebook's name on screen twice - once in the list and once above the
   * editor - so matching on text alone finds two nodes. Only the row is selectable.
   */
  private fun listRow(title: String) = compose.onNode(hasText(title) and isSelectable())

  private fun startAppWithTwoNotebooks(twoPane: Boolean) {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      // The id *is* the name since X1b - a notebook's file is what it is called - so these two
      // cannot differ. They used to, and Home then listed "alpha" while the test looked for
      // "Alpha notebook".
      repository.saveNotebook(notebook(ALPHA_TITLE, ALPHA_SOURCE))
      repository.saveNotebook(notebook(BETA_TITLE, BETA_SOURCE))
    }
    compose.setContent {
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
    const val ALPHA_TITLE = "Alpha notebook"
    const val BETA_TITLE = "Beta notebook"

    // Disjoint on purpose: neither string can be matched by the other notebook's cell.
    const val ALPHA_SOURCE = "print('first one only')"
    const val BETA_SOURCE = "print('second one only')"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
