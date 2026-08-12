package com.jupydroid.app.ui.explorer

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.domain.port.NotebookRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Moving around the workspace: what is listed, where "up" goes, and what a tap opens.
 *
 * JupyterLab's file browser is a *single pane* that replaces its contents as you descend, with a
 * breadcrumb above it - not a tree that expands in place (`packages/filebrowser`, `BreadCrumbs` +
 * `DirListing`). That is also the right shape for a phone, where a tree's indentation would eat
 * the width the names need, so this follows it rather than adapting it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerViewModelTest {
  @get:Rule val dispatcher = MainDispatcherRule()

  @get:Rule val temp = TemporaryFolder()

  @Test
  fun itOpensAtTheRoot() = runTest {
    given("notes.txt", "reports/")

    val viewModel = explorer()

    assertEquals("", viewModel.uiState.value.path)
    assertEquals(listOf("reports", "notes.txt"), viewModel.uiState.value.entries.map { it.name })
  }

  @Test
  fun openingADirectoryDescendsIntoIt() = runTest {
    given("reports/summary.ipynb")
    val viewModel = explorer()

    viewModel.open(viewModel.uiState.value.entries.single())

    assertEquals("reports", viewModel.uiState.value.path)
    assertEquals(listOf("summary.ipynb"), viewModel.uiState.value.entries.map { it.name })
  }

  /** `filebrowser:go-up`, which the reference also binds to Backspace. */
  @Test
  fun goingUpReturnsToTheParent() = runTest {
    given("reports/deep/file.txt")
    val viewModel = explorer()
    viewModel.open(viewModel.uiState.value.entries.single())
    viewModel.open(viewModel.uiState.value.entries.single())

    viewModel.goUp()

    assertEquals("reports", viewModel.uiState.value.path)
  }

  @Test
  fun theRootHasNoParentToGoUpTo() = runTest {
    given("notes.txt")
    val viewModel = explorer()

    assertEquals(false, viewModel.uiState.value.canGoUp)
  }

  @Test
  fun aDirectoryHasAParent() = runTest {
    given("reports/summary.ipynb")
    val viewModel = explorer()
    viewModel.open(viewModel.uiState.value.entries.single())

    assertTrue(viewModel.uiState.value.canGoUp)
  }

  /**
   * The breadcrumb is the path split into its segments, with the root first.
   *
   * The reference shows the same thing and makes each segment a target
   * (`packages/filebrowser/src/crumbs.ts`); on a phone it scrolls horizontally rather than
   * eliding, so the folder you are *in* is never the one that gets truncated away.
   */
  @Test
  fun theBreadcrumbIsThePathInSegments() = runTest {
    given("reports/deep/file.txt")
    val viewModel = explorer()
    viewModel.open(viewModel.uiState.value.entries.single())
    viewModel.open(viewModel.uiState.value.entries.single())

    val expected = listOf("" to "Workspace", "reports" to "reports", "reports/deep" to "deep")
    assertEquals(expected, viewModel.uiState.value.crumbs)
  }

  @Test
  fun aCrumbNavigatesStraightToThatDirectory() = runTest {
    given("reports/deep/file.txt")
    val viewModel = explorer()
    viewModel.open(viewModel.uiState.value.entries.single())
    viewModel.open(viewModel.uiState.value.entries.single())

    viewModel.goTo("")

    assertEquals("", viewModel.uiState.value.path)
  }

  /** A file is not the explorer's to open: it says which one, and something else decides. */
  @Test
  fun openingAFileReportsItRatherThanNavigating() = runTest {
    given("notes.txt")
    val viewModel = explorer()
    val opened = mutableListOf<String>()

    viewModel.open(viewModel.uiState.value.entries.single(), onOpenFile = { opened += it.path })

    assertEquals(listOf("notes.txt"), opened)
    assertEquals("", viewModel.uiState.value.path)
  }

  /**
   * A directory that vanished under us leaves the explorer where it was, saying so.
   *
   * The workspace is shared - a cell can `os.remove` the folder the user is looking at - so this
   * is an ordinary event, not a corrupt state.
   */
  @Test
  fun aDirectoryThatDisappearedIsReportedRatherThanCrashing() = runTest {
    given("reports/summary.ipynb")
    val viewModel = explorer()
    val entry = viewModel.uiState.value.entries.single()
    File(temp.root, "reports/summary.ipynb").delete()
    File(temp.root, "reports").delete()

    viewModel.open(entry)

    assertEquals("", viewModel.uiState.value.path)
    assertTrue("expected the failure to be reported", viewModel.uiState.value.error != null)
  }

  @Test
  fun aSuccessfulListingClearsAnEarlierError() = runTest {
    given("reports/summary.ipynb", "notes.txt")
    val viewModel = explorer()
    val reports = viewModel.uiState.value.entries.first { it.name == "reports" }
    File(temp.root, "reports/summary.ipynb").delete()
    File(temp.root, "reports").delete()
    viewModel.open(reports)

    viewModel.goTo("")

    assertNull(viewModel.uiState.value.error)
  }

  /**
   * A new file appears in the directory being browsed, named the way the server names one.
   *
   * `_createNew` passes `path: this.model.path` - the directory the browser is *showing*
   * (`packages/filebrowser/src/browser.ts:558-566`) - so creating while inside `reports/` creates
   * inside `reports/`, not at the root.
   */
  @Test
  fun creatingAFileCreatesItInTheDirectoryBeingBrowsed() = runTest {
    given("reports/keep.txt")
    val viewModel = explorer()
    viewModel.open(viewModel.uiState.value.entries.single())

    viewModel.createFile("py")

    assertTrue("untitled.py" in viewModel.uiState.value.entries.map { it.name })
    assertTrue(File(temp.root, "reports/untitled.py").exists())
  }

  @Test
  fun creatingAFolderShowsItStraightAway() = runTest {
    given("keep.txt")
    val viewModel = explorer()

    viewModel.createFolder()

    assertTrue("Untitled Folder" in viewModel.uiState.value.entries.map { it.name })
  }

  /**
   * A new notebook is a *valid* notebook, not an empty file with an `.ipynb` name.
   *
   * Which is why it does not go through `createFile("ipynb")`: `new_untitled` gives
   * `type: "notebook"` its own branch and the server writes nbformat into it
   * (`manager.py:605-607`), and an empty file would fail to load the moment it was opened.
   */
  @Test
  fun creatingANotebookProducesOneThatCanBeRead() = runTest {
    given("keep.txt")
    val notebooks = DiskNotebookRepository(temp.root, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
    val viewModel = explorer(notebooks)

    viewModel.createNotebook()

    assertTrue("Untitled.ipynb" in viewModel.uiState.value.entries.map { it.name })
    assertEquals("Untitled.ipynb", notebooks.readNotebook("Untitled.ipynb").id)
  }

  /**
   * The repository is given the *test* dispatcher, or nothing here waits for it.
   *
   * `DiskWorkspaceRepository` reads on `Dispatchers.IO`, and the ViewModel lists from `init`. On a
   * real IO dispatcher that coroutine suspends onto another thread and the test body races past
   * it, seeing an empty listing - which is exactly what every case here first reported.
   */
  private fun TestScope.explorer(
    notebooks: NotebookRepository =
      DiskNotebookRepository(temp.root, ioDispatcher = UnconfinedTestDispatcher(testScheduler)),
  ) = ExplorerViewModel(
    DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler)),
    notebooks,
  )

  private fun given(vararg paths: String) {
    paths.forEach { path ->
      val file = File(temp.root, path)
      if (path.endsWith("/")) file.mkdirs() else file.apply { parentFile?.mkdirs() }.writeText("x")
    }
  }
}
