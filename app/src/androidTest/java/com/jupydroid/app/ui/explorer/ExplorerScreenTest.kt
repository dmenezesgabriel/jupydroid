package com.jupydroid.app.ui.explorer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.theme.JupyterDroidTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The explorer on a device: rows that can be read and tapped, and a breadcrumb that gets back.
 *
 * `ExplorerViewModelTest` argues what is listed and where "up" goes; what it cannot answer is
 * whether any of it is on screen and reachable by a finger.
 */
@RunWith(AndroidJUnit4::class)
class ExplorerScreenTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  @Test
  fun itListsTheWorkspace() {
    given("notes.txt", "reports/")
    showExplorer()

    compose.onNodeWithText("reports").assertIsDisplayed()
    compose.onNodeWithText("notes.txt").assertIsDisplayed()
  }

  @Test
  fun tappingADirectoryShowsWhatIsInside() {
    given("reports/summary.ipynb")
    showExplorer()

    compose.onNodeWithText("reports").performClick()

    waitForText("summary.ipynb")
    compose.onNodeWithText("summary.ipynb").assertIsDisplayed()
  }

  /** The crumb for an ancestor is a target, which is what makes the breadcrumb navigation. */
  @Test
  fun tappingACrumbGoesBackToThatDirectory() {
    given("reports/summary.ipynb", "notes.txt")
    showExplorer()
    compose.onNodeWithText("reports").performClick()
    waitForText("summary.ipynb")

    compose.onNodeWithText("Workspace").performClick()

    waitForText("notes.txt")
    compose.onNodeWithText("notes.txt").assertIsDisplayed()
  }

  /**
   * A directory and a file are told apart without reading the name.
   *
   * The reference distinguishes them with an icon per file type
   * (`packages/filebrowser/src/listing.ts` via the icon registry), which is what a scanning eye
   * uses; the content description is what a screen reader uses instead.
   */
  @Test
  fun aDirectoryAndAFileAreDistinguishable() {
    given("reports/", "notes.txt")
    showExplorer()

    compose.onNodeWithContentDescription("Folder").assertIsDisplayed()
    compose.onNodeWithContentDescription("File").assertIsDisplayed()
  }

  @Test
  fun tappingAFileReportsIt() {
    given("notes.txt")
    val opened = mutableListOf<String>()
    showExplorer(onOpenFile = { opened += it.path })

    compose.onNodeWithText("notes.txt").performClick()

    compose.runOnIdle { assertEquals(listOf("notes.txt"), opened) }
  }

  /**
   * A file nothing can open says so, rather than absorbing the tap.
   *
   * Every file is listed, but until the plain-text editor lands only `.ipynb` has anywhere to go -
   * and a row that does nothing when tapped is indistinguishable from one that is broken. The
   * reference never has this state: its default factory opens any text file in CodeMirror.
   */
  @Test
  fun aFileNothingCanOpenSaysSoRatherThanDoingNothing() {
    given("data.csv")
    val opened = mutableListOf<String>()
    showExplorer(onOpenFile = { opened += it.path }, canOpenFile = { false })

    compose.onNodeWithText("data.csv").performClick()

    waitForText("No editor for data.csv yet")
    compose.runOnIdle { assertEquals(emptyList<String>(), opened) }
  }

  /**
   * No back arrow when there is nowhere to go back to.
   *
   * The explorer is pushed from Recents today and will be the root once Recents becomes a section
   * of it; a bar that keeps its arrow either way would offer a route out of the first screen.
   */
  @Test
  fun theBackArrowIsAbsentWhenTheExplorerIsTheRoot() {
    given("notes.txt")
    showExplorer(onBack = null)

    compose.onAllNodesWithContentDescription("Back to recents").assertCountEquals(0)
  }

  private fun showExplorer(
    onOpenFile: (WorkspaceEntry) -> Unit = {},
    canOpenFile: (WorkspaceEntry) -> Boolean = { true },
    onBack: (() -> Unit)? = {},
  ) {
    compose.setContent {
      JupyterDroidTheme {
        ExplorerScreen(
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          onOpenFile = onOpenFile,
          canOpenFile = canOpenFile,
          onBack = onBack,
        )
      }
    }
    waitForAnyRow()
  }

  private fun waitForAnyRow() = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText("Workspace").fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun given(vararg paths: String) {
    paths.forEach { path ->
      val file = File(temp.root, path)
      if (path.endsWith("/")) file.mkdirs() else file.apply { parentFile?.mkdirs() }.writeText("x")
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
