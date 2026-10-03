package com.jupydroid.app

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The explorer's open-row marking across a rename and a close - the shell's own copy of
 * `docmanager`'s `_contextsForPath` read, which answers by where the document lives *now*.
 *
 * A navigation key keeps the path it was opened under - replacing it would drop the entry's
 * `ViewModelStore` - so a rename is reported beside the key into `openDocumentPaths`, and the
 * row's `selected` state is read from there. A screen re-reports its path once it has loaded,
 * which keeps the map honest while a tab lives; the second test is the case where no document
 * ever arrives to correct it, so the closed tab's answer is the one that stays.
 */
@RunWith(AndroidJUnit4::class)
class OpenDocumentMarkingTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * `a.ipynb` open and renamed to `b.ipynb`: the mark follows the file - the row for the name
   * it was renamed to is selected, and the row it left is gone once the listing re-reads.
   */
  @Test
  fun aRenamedTabMarksTheRowItsFileMovedTo() {
    startAppWithNotebook("a.ipynb")

    listingRow("a.ipynb").performClick()
    waitForTab("a.ipynb")
    renameTab(from = "a.ipynb", to = "b.ipynb")
    waitForTab("b.ipynb")

    waitForRow("b.ipynb").assertIsSelected()
    compose.waitUntil(ROW_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText("a.ipynb") and hasContentDescription("File")).fetchSemanticsNodes().isEmpty()
    }
  }

  /**
   * `a.ipynb` open, renamed to `b.ipynb`, closed; a broken `a.ipynb` written again and tapped -
   * the open fails, so no screen reports a live path and whatever the closed tab left in
   * `openDocumentPaths` is the whole answer. Before the reconcile dropped dead keys the dead
   * entry titled the tab `b.ipynb` and marked `b.ipynb`'s row for a document that never opened.
   */
  @Test
  fun aClosedTabsLivePathStopsAnswering() {
    startAppWithNotebook("a.ipynb")

    listingRow("a.ipynb").performClick()
    waitForTab("a.ipynb")
    renameTab(from = "a.ipynb", to = "b.ipynb")
    waitForTab("b.ipynb")
    closeTab("b.ipynb")

    File(tempFolder.root, "a.ipynb").writeText("{")
    waitForRow("b.ipynb")
    waitForRow("a.ipynb").performClick()
    waitForTab("a.ipynb")

    compose.onNodeWithContentDescription("Tab: a.ipynb").assertExists()
    compose.onAllNodesWithContentDescription("Tab: b.ipynb").assertCountEquals(0)
    listingRow("a.ipynb").assertIsSelected()
    listingRow("b.ipynb").assertIsNotSelected()
  }

  private fun listingRow(title: String) = compose.onNode(hasText(title) and hasContentDescription("File"))

  private fun waitForRow(title: String): SemanticsNodeInteraction {
    compose.waitUntil(ROW_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText(title) and hasContentDescription("File")).fetchSemanticsNodes().isNotEmpty()
    }
    return listingRow(title)
  }

  private fun waitForTab(title: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithContentDescription("Tab: $title").fetchSemanticsNodes().isNotEmpty()
  }

  private fun renameTab(from: String, to: String) {
    compose.onNodeWithContentDescription("Tab: $from").performTouchInput { longClick() }
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput(to)
    compose.onNodeWithText("Rename").performClick()
  }

  private fun closeTab(title: String) {
    compose.onNodeWithContentDescription("Close tab: $title").performClick()
  }

  private fun startAppWithNotebook(name: String) {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      repository.saveNotebook(
        Notebook(name, name, "python3", listOf(Cell("$name-c1", CellKind.CODE, "print(1)"))),
      )
    }
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            notebookRepository = repository,
            workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
            kernelGateway = FakeKernelGateway(),
          ),
          importTitle = null,
          onImportConsumed = {},
          // Two panes, because `openDocumentPath` is the list-pane marking and there is no list
          // pane beside the document at one.
          twoPane = true,
        )
      }
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L

    // The recreated row appears only when the listing's poll re-reads the directory,
    // so it waits past `EXPLORER_REFRESH_INTERVAL` rather than a test-step timeout.
    const val ROW_TIMEOUT_MILLIS = 20_000L
  }
}
