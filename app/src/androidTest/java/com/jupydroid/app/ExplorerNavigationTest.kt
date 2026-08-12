package com.jupydroid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The explorer is reachable, and what it opens is the notebook it names.
 *
 * Recents lists notebooks wherever they are; the explorer lists the workspace as it is on disk,
 * which is the only view that shows a folder at all. Both are on the way to the file browser
 * being the root - JupyterLab has one file browser and no separate recents screen - so this
 * asserts the route rather than the arrangement, and survives Recents becoming a section of it.
 */
@RunWith(AndroidJUnit4::class)
class ExplorerNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  @Test
  fun browsingFilesReachesTheWorkspace() {
    startAppWithANotebookInAFolder()

    compose.onNodeWithContentDescription(BROWSE).performClick()

    waitForText("reports")
    compose.onNodeWithText("reports").assertIsDisplayed()
  }

  /**
   * The notebook opened is the one in the folder, asserted by its **cell**.
   *
   * A title alone would be matched by the row that was tapped. It also proves the explorer passes
   * a *path* - `reports/Nested.ipynb` - and not a bare name: the editor resolves its id against
   * the workspace root, so a name-only id would open nothing.
   */
  @Test
  fun openingANotebookFromAFolderOpensThatNotebook() {
    startAppWithANotebookInAFolder()
    compose.onNodeWithContentDescription(BROWSE).performClick()
    waitForText("reports")

    compose.onNodeWithText("reports").performClick()
    waitForText("Nested.ipynb")
    compose.onNodeWithText("Nested.ipynb").performClick()

    waitForText(NESTED_SOURCE)
    compose.onNodeWithText(NESTED_SOURCE).assertIsDisplayed()
  }

  /** Back leaves the explorer the way it was reached, rather than the app. */
  @Test
  fun theExplorerIsLeftByGoingBack() {
    startAppWithANotebookInAFolder()
    compose.onNodeWithContentDescription(BROWSE).performClick()
    waitForText("reports")

    compose.onNodeWithContentDescription(BACK).performClick()

    waitForText(RECENTS)
    compose.onNodeWithText(RECENTS).assertIsDisplayed()
  }

  private fun startAppWithANotebookInAFolder() {
    File(temp.root, "reports").mkdirs()
    val repository = DiskNotebookRepository(temp.root)
    runBlocking {
      repository.saveNotebook(
        Notebook(
          "reports/Nested.ipynb",
          "reports/Nested.ipynb",
          "python3",
          listOf(Cell("c1", CellKind.CODE, NESTED_SOURCE)),
        ),
      )
    }
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            notebookRepository = repository,
            workspaceRepository = DiskWorkspaceRepository(temp.root),
          ),
          importTitle = null,
          onImportConsumed = {},
          // Single pane, or the explorer and the editor would share the window and "reached it"
          // would be true before anything was tapped.
          twoPane = false,
        )
      }
    }
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val BROWSE = "Browse files"
    const val BACK = "Back to recents"
    const val RECENTS = "Recents"
    const val NESTED_SOURCE = "print('inside the folder')"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
