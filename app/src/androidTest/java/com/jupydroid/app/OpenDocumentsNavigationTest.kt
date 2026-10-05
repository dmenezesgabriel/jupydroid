package com.jupydroid.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The open notebook reached **by its path**, from outside its own `NavEntry`, and edited there.
 *
 * `ITextModelService`'s side of the seam a workspace replace needs: `BulkTextEdits` resolves a model for
 * every resource it edits, open or not (`bulkTextEdits.ts:227`), so a replace edits what is on
 * screen instead of writing the file behind it - which this app's debounced save would then undo.
 *
 * Instrumented rather than JVM for [DocumentOutlineNavigationTest]'s reason: `OpenDocumentsTest`
 * already pins the holder and `EditorViewModelOpenDocumentTest` the document, and what is left is
 * the wiring - whether a real editor entry, composed and then disposed, registers and clears, and
 * whether the **cell's field** shows an edit nobody typed into it.
 */
@RunWith(AndroidJUnit4::class)
class OpenDocumentsNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  private val open = OpenDocuments()

  @Test
  fun openingANotebookMakesItReachableByItsPath() {
    startAppWithOneNotebook()

    compose.onNodeWithText(TITLE).performClick()
    waitForText(CELL_SOURCE)

    assertEquals(TITLE, open.at(TITLE)?.path)
  }

  /**
   * **And the cell draws it**, which is the assertion no ViewModel test can make: a cell's
   * `TextFieldState` cannot see a changed `Cell.source` on its own, so this passes only because
   * `applyEdits` bumps `sourceRevisions` and `adoptRewrittenSource` reads it.
   */
  @Test
  fun anEditPushedByPathShowsInTheCellThatIsOnScreen() {
    startAppWithOneNotebook()
    compose.onNodeWithText(TITLE).performClick()
    waitForText(CELL_SOURCE)

    compose.runOnUiThread { open.at(TITLE)?.applyEdits(cell = 0, edits = listOf(TextEdit(0..2, "dog"))) }
    waitForText("dog = 1")
  }

  /** A document that is gone is not open - the same lifetime rule [OpenDocumentOutline] keeps. */
  @Test
  fun leavingTheNotebookClosesTheDocument() {
    startAppWithOneNotebook()
    compose.onNodeWithText(TITLE).performClick()
    waitForText(CELL_SOURCE)

    // The strip's one-pane arrow - the browser is what sits beneath the notebook.
    compose.onNodeWithContentDescription("Back to files").performClick()
    waitForText(WORKSPACE_SECTION)

    assertNull(open.at(TITLE))
  }

  private fun startAppWithOneNotebook() {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking { repository.saveNotebook(notebook()) }
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            notebookRepository = repository,
            workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
          ),
          importTitle = null,
          onImportConsumed = {},
          openDocuments = open,
          // Pinned for `NotebookNavigationTest`'s reason: a physical tablet's own rotation puts
          // this on either side of the two-pane threshold, where the strip's "Back to files"
          // arrow disappears instead (`MainAreaTabStrip`).
          twoPane = false,
        )
      }
    }
    compose.enterBaseSpace()
  }

  private fun notebook() = Notebook(TITLE, TITLE, "python3", listOf(Cell("c1", CellKind.CODE, CELL_SOURCE)))

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val TITLE = "Registered.ipynb"

    /** One line, because a cell's field is one node holding all of its source. */
    const val CELL_SOURCE = "cat = 1"
    const val WORKSPACE_SECTION = "Workspace"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
