package com.jupydroid.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.COULD_NOT_SAVE
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.FileNotFoundException

/**
 * **A notebook that cannot be saved says so, and stays on screen while it says it.**
 *
 * The half a ViewModel test cannot make. `EditorScreen` draws `EditorUiState.error` *instead of*
 * the document, so reporting a failed save through that field would have taken the reader's unsaved
 * work off the screen - a worse outcome than the crash this replaced. Hence
 * `EditorUiState.saveFailure` and a banner over the cells.
 *
 * The cause is reachable rather than hypothetical: `WorkspaceRepository.changes` announces what
 * this app moved, never what the kernel did, so a cell that removes the notebook's folder leaves
 * this editor writing to a path that is not there.
 */
@RunWith(AndroidJUnit4::class)
class SaveFailureBannerTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  /** Reads like the tree it was, refuses every write - the directory went while this was open. */
  private class VanishedNotebookRepository(private val stillReadable: NotebookRepository) :
    NotebookRepository by stillReadable {
    override suspend fun saveNotebook(notebook: Notebook): Unit =
      throw FileNotFoundException("${notebook.id}: open failed: ENOENT (No such file or directory)")
  }

  @Test
  fun aNotebookThatCannotBeSavedSaysSoWithoutLeavingTheScreen() {
    startAppWithANotebookNothingCanWrite()
    compose.onNodeWithText(TITLE).performClick()
    waitForText(CELL_SOURCE)

    compose.onNodeWithText(CELL_SOURCE).performTextInput("x")
    waitForText(COULD_NOT_SAVE, whole = false)

    // **Both at once**, which is the assertion: the reason is reported and the work is still there.
    compose.onNodeWithText("ENOENT", substring = true).assertExists()
    compose.onNodeWithText(CELL_SOURCE, substring = true).assertExists()
  }

  private fun startAppWithANotebookNothingCanWrite() {
    val readable = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      readable.saveNotebook(Notebook(TITLE, TITLE, "python3", listOf(Cell("c1", CellKind.CODE, CELL_SOURCE))))
    }
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            enteredSpacePorts = { _, _ ->
              EnteredSpacePorts(
                notebookRepository = VanishedNotebookRepository(readable),
                workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
              )
            },
          ),
          importTitle = null,
          onImportConsumed = {},
        )
      }
    }
    compose.enterBaseSpace()
  }

  private fun waitForText(text: String, whole: Boolean = true) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text, substring = !whole).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val TITLE = "Unwritable.ipynb"
    const val CELL_SOURCE = "cat = 1"
    const val TIMEOUT_MILLIS = 10_000L
  }
}
