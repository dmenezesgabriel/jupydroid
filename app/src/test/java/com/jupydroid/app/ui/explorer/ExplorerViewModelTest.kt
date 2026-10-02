package com.jupydroid.app.ui.explorer

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.domain.model.parentDirectory
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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

    viewModel.goTo(viewModel.uiState.value.path.parentDirectory())

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

    viewModel.create(NewItem.PYTHON_FILE)

    assertTrue("untitled.py" in viewModel.uiState.value.entries.map { it.name })
    assertTrue(File(temp.root, "reports/untitled.py").exists())
  }

  @Test
  fun creatingAFolderShowsItStraightAway() = runTest {
    given("keep.txt")
    val viewModel = explorer()

    viewModel.create(NewItem.FOLDER)

    assertTrue("Untitled Folder" in viewModel.uiState.value.entries.map { it.name })
  }

  /**
   * A new notebook is a *valid* notebook, not an empty file with an `.ipynb` name.
   *
   * Which is why it does not go through `create` routing it to the workspace: `new_untitled` gives
   * `type: "notebook"` its own branch and the server writes nbformat into it
   * (`manager.py:605-607`), and an empty file would fail to load the moment it was opened.
   */
  @Test
  fun creatingANotebookProducesOneThatCanBeRead() = runTest {
    given("keep.txt")
    val notebooks = DiskNotebookRepository(temp.root, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
    val viewModel = explorer(notebooks)

    viewModel.create(NewItem.NOTEBOOK)

    assertTrue("Untitled.ipynb" in viewModel.uiState.value.entries.map { it.name })
    assertEquals("Untitled.ipynb", notebooks.readNotebook("Untitled.ipynb").id)
  }

  /**
   * A rename shows the new name where the old one was, in the directory being browsed.
   *
   * Re-listed rather than patched in place, for the reason every other mutation here re-lists: the
   * workspace is shared with the kernel, so the directory may have moved on for reasons of its own
   * and a re-read is the only honest answer.
   */
  @Test
  fun renamingShowsTheNewNameInPlaceOfTheOld() = runTest {
    given("reports/summary.ipynb")
    val viewModel = explorer()
    viewModel.open(viewModel.uiState.value.entries.single())

    viewModel.rename(viewModel.uiState.value.entries.single(), "final.ipynb")

    assertEquals(listOf("final.ipynb"), viewModel.uiState.value.entries.map { it.name })
    assertTrue(File(temp.root, "reports/final.ipynb").exists())
  }

  /** An invalid name is refused and said out loud, and nothing on disk moves. */
  @Test
  fun aRefusedRenameIsReportedAndChangesNothing() = runTest {
    given("notes.txt")
    val viewModel = explorer()

    viewModel.rename(viewModel.uiState.value.entries.single(), "a/b")

    assertTrue("expected the refusal to be reported", viewModel.uiState.value.error != null)
    assertEquals(listOf("notes.txt"), viewModel.uiState.value.entries.map { it.name })
  }

  /**
   * A taken name is a question, not an error - which is the one refusal the user can resolve.
   *
   * `renameFile` catches only a 409 and asks `"%1" already exists, overwrite?`; anything else
   * rethrows (`docmanager/src/dialogs.ts`). So this must not land in `error`, where nothing can be
   * answered.
   */
  @Test
  fun aTakenNameAsksBeforeItReplacesAnything() = runTest {
    given("notes.txt", "taken.txt")
    val viewModel = explorer()

    viewModel.rename(viewModel.uiState.value.entries.first { it.name == "notes.txt" }, "taken.txt")

    assertEquals("taken.txt", viewModel.uiState.value.nameTaken)
    assertNull(viewModel.uiState.value.error)
    assertTrue("nothing may move before the answer", File(temp.root, "notes.txt").exists())
  }

  @Test
  fun answeringYesReplacesTheFileAndLeavesOne() = runTest {
    given("notes.txt", "taken.txt")
    val viewModel = explorer()
    File(temp.root, "notes.txt").writeText("the one that survives")
    viewModel.rename(viewModel.uiState.value.entries.first { it.name == "notes.txt" }, "taken.txt")

    viewModel.answerOverwrite(accepted = true)

    assertEquals(listOf("taken.txt"), viewModel.uiState.value.entries.map { it.name })
    assertEquals("the one that survives", File(temp.root, "taken.txt").readText())
    assertNull(viewModel.uiState.value.nameTaken)
  }

  /**
   * **The overwrite question is answered by its dialog, not by a re-list.** `listingOf` builds a
   * fresh state so nothing stale survives a navigation - but `nameTaken` is not stale state, it
   * is an unanswered question, and `KeepTheListingFresh` ticks whether or not a dialog is up.
   * One tick used to drop the ask the user had not answered; the reference's `showDialog` waits
   * for its button for as long as that takes (`docmanager/src/dialogs.ts`). The ask that
   * survives must still be *answerable* - `renamingPath` outlives it for exactly that.
   */
  @Test
  fun aPollDoesNotRetractTheOverwriteQuestion() = runTest {
    given("notes.txt", "taken.txt")
    File(temp.root, "notes.txt").writeText("the one that survives")
    val viewModel = explorer()
    viewModel.rename(viewModel.uiState.value.entries.first { it.name == "notes.txt" }, "taken.txt")

    viewModel.refresh()

    assertEquals("taken.txt", viewModel.uiState.value.nameTaken)
    viewModel.answerOverwrite(accepted = true)
    assertNull(viewModel.uiState.value.nameTaken)
    assertEquals("the one that survives", File(temp.root, "taken.txt").readText())
  }

  @Test
  fun answeringNoLeavesBothFilesAlone() = runTest {
    given("notes.txt", "taken.txt")
    val viewModel = explorer()
    viewModel.rename(viewModel.uiState.value.entries.first { it.name == "notes.txt" }, "taken.txt")

    viewModel.answerOverwrite(accepted = false)

    assertEquals(listOf("notes.txt", "taken.txt"), viewModel.uiState.value.entries.map { it.name })
    assertNull(viewModel.uiState.value.nameTaken)
  }

  @Test
  fun deletingRemovesTheRowAndTheFile() = runTest {
    given("notes.txt", "keep.txt")
    val viewModel = explorer()

    viewModel.delete(viewModel.uiState.value.entries.first { it.name == "notes.txt" })

    assertEquals(listOf("keep.txt"), viewModel.uiState.value.entries.map { it.name })
    assertTrue("the file must be gone", !File(temp.root, "notes.txt").exists())
  }

  /**
   * A folder with anything in it is refused out loud, and keeps its contents.
   *
   * The server serving this same tree refuses it (`filemanager.py:607-613`, no trash on Android),
   * so this is where the app says so - in the error line the explorer already has.
   */
  @Test
  fun deletingAFolderWithSomethingInItIsReportedAndChangesNothing() = runTest {
    given("reports/summary.ipynb")
    val viewModel = explorer()

    viewModel.delete(viewModel.uiState.value.entries.single())

    assertTrue("expected the refusal to be reported", viewModel.uiState.value.error != null)
    assertTrue("the folder must survive", File(temp.root, "reports/summary.ipynb").exists())
  }

  /**
   * **A poll that started before a navigation must not land on top of it** - found on the tablet,
   * where the notebook's breadcrumb is the first thing that navigates this browser from
   * outside it. Coming back into composition restarts `KeepTheListingFresh`, which calls
   * [ExplorerViewModel.refresh] at once; that re-lists whatever path is on screen, and it was
   * racing the crumb's own `goTo`. Two listings, whichever finished last winning, and the frame
   * showed the browser still standing in the folder the crumb had just left.
   */
  @Test
  fun aListingAlreadyInFlightDoesNotOverwriteWhereWeJustWent() = runTest {
    given("At Root.ipynb", "reports/Nested.ipynb")
    val viewModel = explorer(workspace = SlowListingWorkspaceRepository(disk(), slowPath = "reports"))
    viewModel.goTo("reports")

    // The poll reads the path on screen - "reports" - and its listing is the slow one, so it is
    // still in flight when the crumb navigates away.
    viewModel.refresh()
    viewModel.goTo("")
    advanceUntilIdle()

    assertEquals("", viewModel.uiState.value.path)
    assertEquals(listOf("reports", "At Root.ipynb"), viewModel.uiState.value.entries.map { it.name })
  }

  /**
   * **A file act that finishes mid-navigation re-lists where the user is going**, not the
   * directory still on screen. `move`'s re-list read `_uiState.value.path`, which trails
   * `browsing` while a listing is in flight - the same gap `refresh` reads `browsing` for - so a
   * rename answered while the crumb's listing was on its way cancelled that listing, re-listed
   * the directory just left, and left the browser standing there.
   */
  @Test
  fun aRenameFinishedMidNavigationRelistsWhereWeAreGoing() = runTest {
    given("At Root.ipynb", "reports/Nested.ipynb")
    val viewModel = explorer(workspace = SlowListingWorkspaceRepository(disk(), slowPath = "reports"))
    val entry = viewModel.uiState.value.entries.first { it.name == "At Root.ipynb" }
    viewModel.goTo("reports")

    viewModel.rename(entry, "Renamed.ipynb")
    advanceUntilIdle()

    assertEquals("reports", viewModel.uiState.value.path)
    assertEquals(listOf("Nested.ipynb"), viewModel.uiState.value.entries.map { it.name })
    assertTrue(File(temp.root, "Renamed.ipynb").exists())
  }

  /** The same for `relistAfter`'s arm - a delete landing while the listing is in flight. */
  @Test
  fun aDeleteFinishedMidNavigationRelistsWhereWeAreGoing() = runTest {
    given("At Root.ipynb", "reports/Nested.ipynb")
    val viewModel = explorer(workspace = SlowListingWorkspaceRepository(disk(), slowPath = "reports"))
    val entry = viewModel.uiState.value.entries.first { it.name == "At Root.ipynb" }
    viewModel.goTo("reports")

    viewModel.delete(entry)
    advanceUntilIdle()

    assertEquals("reports", viewModel.uiState.value.path)
    assertEquals(listOf("Nested.ipynb"), viewModel.uiState.value.entries.map { it.name })
  }

  /**
   * **And a cancelled listing says nothing to the user**, which the test above cannot catch.
   *
   * `runCatching` catches `CancellationException` like any other throwable, so cancelling the
   * in-flight listing wrote its own cancellation into the state as a failure: the browser drew
   * *"StandaloneCoroutine was cancelled"* in `colorScheme.error` across the top of the file list.
   * Every assertion above holds
   * while that banner is up, because none of them reads `error` - it was found by looking at a
   * frame the flow it belongs to had reported green.
   *
   * **Driven by a repository that cancels rather than by a race**, because the race is not
   * reproducible on a virtual scheduler: `advanceUntilIdle` resumes a cancelled `delay` at once,
   * so the error write lands *before* the newer listing's and is overwritten. On a device the
   * order is the other way round - `DiskWorkspaceRepository.list` is a `withContext(ioDispatcher)`
   * (`:39`), which throws its `CancellationException` when the block *finishes*, by which time the
   * newer listing has already written a clean state and `copy(error = …)` puts the banner back on
   * top of it. What is pinned here is the rule that makes the ordering stop mattering: a
   * cancellation is this ViewModel's own doing - the newer navigation winning, which
   * [aListingAlreadyInFlightDoesNotOverwriteWhereWeJustWent] exists to make happen - and there is
   * nothing to tell anyone about.
   */
  @Test
  fun aCancelledListingIsNotReportedAsAFailure() = runTest {
    given("At Root.ipynb")
    val viewModel = explorer(workspace = CancellingWorkspaceRepository(disk()))

    viewModel.goTo("reports")
    advanceUntilIdle()

    assertNull("a cancelled listing is not a failure to report", viewModel.uiState.value.error)
  }

  /** And a listing that fails for a real reason still says so - the guard must not eat those. */
  @Test
  fun aListingThatReallyFailedIsStillReported() = runTest {
    given("At Root.ipynb")
    val viewModel = explorer()

    viewModel.goTo("nowhere")
    advanceUntilIdle()

    assertNotNull("a real failure is still the user's to see", viewModel.uiState.value.error)
  }

  /** And the poll itself still re-lists, rather than being cancelled into doing nothing. */
  @Test
  fun refreshingStillRelistsWhereWeAre() = runTest {
    given("reports/Nested.ipynb")
    val viewModel = explorer()
    viewModel.goTo("reports")

    File(temp.root, "reports/Later.ipynb").writeText("x")
    viewModel.refresh()
    advanceUntilIdle()

    assertEquals("reports", viewModel.uiState.value.path)
    assertEquals(listOf("Later.ipynb", "Nested.ipynb"), viewModel.uiState.value.entries.map { it.name })
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
    workspace: WorkspaceRepository = disk(),
  ) = ExplorerViewModel(workspace, notebooks)

  private fun TestScope.disk() = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))

  private fun given(vararg paths: String) {
    paths.forEach { path ->
      val file = File(temp.root, path)
      if (path.endsWith("/")) file.mkdirs() else file.apply { parentFile?.mkdirs() }.writeText("x")
    }
  }
}
