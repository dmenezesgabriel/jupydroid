package com.jupydroid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskRecentDocumentsFile
import com.jupydroid.app.data.disk.DiskRecentDocumentsRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

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

  // Its own directory, because that is where it belongs: the store is app state and must not land
  // in the workspace the explorer lists.
  @get:Rule val state = TemporaryFolder()

  private val recentDocuments by lazy {
    DiskRecentDocumentsRepository(DiskRecentDocumentsFile(state.root), BASE_SPACE.id, temp.root)
  }

  /** The workspace is the first thing on screen now - there is no door to walk through first. */
  @Test
  fun theAppOpensOnTheWorkspace() {
    startAppWithANotebookInAFolder()

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
    waitForText("reports")

    compose.onNodeWithText("reports").performClick()
    waitForText("Nested.ipynb")
    compose.onNodeWithText("Nested.ipynb").performClick()

    waitForText(NESTED_SOURCE)
    compose.onNodeWithText(NESTED_SOURCE).assertIsDisplayed()
  }

  /**
   * Back climbs the workspace rather than leaving, while there is a parent to climb to.
   *
   * `filebrowser:go-up`, which the reference binds to Backspace. It used to be that back from the
   * explorer returned to Recents; the explorer is the first screen now, so what back owes is the
   * directory above - and, at the root, nothing at all, which is what lets the app be left.
   */
  @Test
  fun backClimbsOutOfAFolderRatherThanLeaving() {
    startAppWithANotebookInAFolder()
    waitForText("reports")
    compose.onNodeWithText("reports").performClick()
    waitForText("Nested.ipynb")

    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
      .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)

    waitForText("reports")
    compose.onNodeWithText("reports").assertIsDisplayed()
  }

  /**
   * Opening a notebook from a folder puts it in Recents, which it once did not.
   *
   * Recents was `rootDir.listFiles()` (`DiskNotebookRepository:149`), so a notebook in `reports/`
   * was reachable through the explorer and absent from the list the app opened on. Asserted
   * through the **real** store rather than a fake, because what has to survive is a write to disk;
   * and the folder is asserted too, since the reference records the containing directory alongside
   * the document (`docmanager/src/widgetmanager.ts:418-422`).
   */
  @Test
  fun openingANotebookFromAFolderPutsItAndItsFolderInRecents() {
    startAppWithANotebookInAFolder()
    waitForText("reports")
    compose.onNodeWithText("reports").performClick()
    waitForText("Nested.ipynb")

    compose.onNodeWithText("Nested.ipynb").performClick()
    waitForText(NESTED_SOURCE)

    compose.waitUntil(TIMEOUT_MILLIS) { runBlocking { recordedPaths() }.size == 2 }
    assertEquals(setOf("reports/Nested.ipynb", "reports"), runBlocking { recordedPaths() })
  }

  private suspend fun recordedPaths(): Set<String> = recentDocuments.observeRecents().first().map { it.path }.toSet()

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
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            enteredSpacePorts = { _, _ ->
              EnteredSpacePorts(
                notebookRepository = repository,
                workspaceRepository = DiskWorkspaceRepository(temp.root),
                recentDocuments = recentDocuments,
              )
            },
          ),
          importTitle = null,
          onImportConsumed = {},
          // Single pane, or the explorer and the editor would share the window and "reached it"
          // would be true before anything was tapped.
          twoPane = false,
        )
      }
    }
    compose.enterBaseSpace()
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val NESTED_SOURCE = "print('inside the folder')"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
