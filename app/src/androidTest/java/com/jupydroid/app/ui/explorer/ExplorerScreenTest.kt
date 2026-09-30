package com.jupydroid.app.ui.explorer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeExplorerSettingsRepository
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

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

  /**
   * The section is on screen at the root, and it is what the app now opens on.
   *
   * `ExplorerViewModelTest` argues what belongs in the list; only a device can say whether a finger
   * finds it above the listing.
   */
  @Test
  fun theRootShowsRecentsAboveTheListing() {
    given("notes.txt")
    showExplorer(recents = listOf(RecentDocument("notes.txt", WorkspaceEntryKind.FILE)))

    waitForText(RECENTS_HEADING)
    compose.onNodeWithText(RECENTS_HEADING).assertIsDisplayed()
  }

  /** Nothing opened yet, so no heading for an empty section - a label over nothing is noise. */
  @Test
  fun aWorkspaceWithNothingOpenedShowsNoRecentsHeading() {
    given("notes.txt")
    showExplorer()

    compose.onAllNodesWithText(RECENTS_HEADING).assertCountEquals(0)
  }

  @Test
  fun tappingARecentOpensIt() {
    given("reports/summary.ipynb")
    var opened: String? = null
    showExplorer(
      onOpenRecent = { opened = it },
      recents = listOf(RecentDocument("reports/summary.ipynb", WorkspaceEntryKind.FILE)),
    )
    waitForText(RECENTS_HEADING)

    // The recent row carries the whole path; the listing below shows `reports` as a folder row,
    // so matching the bare name would find the wrong node.
    compose.onNodeWithText("reports/summary.ipynb").performClick()

    assertEquals("reports/summary.ipynb", opened)
  }

  /**
   * `docmanager:clear-recents`, now behind the section's own overflow rather than beside its
   * heading - the reference's view-pane header puts its secondary actions in a `…` menu
   * (`viewPane.ts:693`, `toolbar.ts:135`), and the heading itself is the collapse.
   */
  @Test
  fun theSectionCanBeClearedFromItsOverflow() {
    given("notes.txt")
    showExplorer(recents = listOf(RecentDocument("notes.txt", WorkspaceEntryKind.FILE)))
    waitForText(RECENTS_HEADING)

    compose.onNodeWithContentDescription(RECENTS_ACTIONS).performClick()
    compose.onNodeWithText(CLEAR_RECENTS).performClick()

    waitUntilGone(RECENTS_HEADING)
  }

  /**
   * The heading collapses the section, and **from anywhere on its row** - the reference toggles on
   * a click or a tap anywhere in the header, not on the chevron alone
   * (`paneview.ts:285-292`).
   */
  @Test
  fun tappingTheHeadingHidesTheRowsAndKeepsTheHeading() {
    given("notes.txt")
    showExplorer(recents = listOf(RecentDocument(RECENT_ROW, WorkspaceEntryKind.FILE)))
    waitForText(RECENTS_HEADING)

    compose.onNodeWithText(RECENTS_HEADING).performClick()

    waitUntilGone(RECENT_ROW)
    compose.onNodeWithText(RECENTS_HEADING).assertIsDisplayed()
  }

  /** `setExpanded(!isExpanded())`: the same row brings the rows back. */
  @Test
  fun tappingTheHeadingAgainBringsTheRowsBack() {
    given("notes.txt")
    showExplorer(recents = listOf(RecentDocument(RECENT_ROW, WorkspaceEntryKind.FILE)))
    waitForText(RECENTS_HEADING)
    compose.onNodeWithText(RECENTS_HEADING).performClick()
    waitUntilGone(RECENT_ROW)

    compose.onNodeWithText(RECENTS_HEADING).performClick()

    waitForText(RECENT_ROW)
  }

  /**
   * What the section is doing, said to a screen reader - the reference's header is a `role=button`
   * carrying an aria-expanded state (`paneview.ts:261-263`), which is what this maps onto. The
   * chevron's own glyph is evidence, not an assertion: no instrumented test can read a vector.
   */
  @Test
  fun theHeadingSaysWhetherItIsOpenOrShut() {
    given("notes.txt")
    showExplorer(recents = listOf(RecentDocument(RECENT_ROW, WorkspaceEntryKind.FILE)))
    waitForText(RECENTS_HEADING)
    compose.onNodeWithText(RECENTS_HEADING).assert(hasStateDescription(EXPANDED))

    compose.onNodeWithText(RECENTS_HEADING).performClick()

    compose.onNodeWithText(RECENTS_HEADING).assert(hasStateDescription(COLLAPSED))
  }

  /**
   * And the state is an *action*, not only a label: TalkBack offers "expand" on a shut section and
   * "collapse" on an open one, which is what the reference's aria-expanded amounts to for a screen
   * reader. Asserted because the KDoc claims it and nothing else could check it.
   */
  @Test
  fun theHeadingOffersTheActionThatMatchesItsState() {
    given("notes.txt")
    showExplorer(recents = listOf(RecentDocument(RECENT_ROW, WorkspaceEntryKind.FILE)))
    waitForText(RECENTS_HEADING)
    compose.onNodeWithText(RECENTS_HEADING).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))

    compose.onNodeWithText(RECENTS_HEADING).performClick()

    compose.onNodeWithText(RECENTS_HEADING).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
  }

  /**
   * A shut section offers no actions, which is the reference's own rule rather than a simplification
   * - every CSS rule that reveals a pane header's actions requires `.expanded`
   * (`paneview.css:79-82`, `paneviewlet.css:26-27`).
   */
  @Test
  fun aCollapsedSectionOffersNoActions() {
    given("notes.txt")
    showExplorer(recents = listOf(RecentDocument(RECENT_ROW, WorkspaceEntryKind.FILE)))
    waitForText(RECENTS_HEADING)

    compose.onNodeWithText(RECENTS_HEADING).performClick()

    waitUntilGone(RECENT_ROW)
    compose.onAllNodesWithContentDescription(RECENTS_ACTIONS).assertCountEquals(0)
  }

  /**
   * Every row says when it was last modified.
   *
   * This is where A6's rule lives now that Recents is a history: `DirListing` shows a
   * `last_modified` column by default (`listing.ts:259-261`), humanized. A file written moments
   * ago reads as "just now".
   */
  @Test
  fun everyRowShowsWhenItWasLastModified() {
    given("notes.txt")
    showExplorer()

    waitForText("just now")
    compose.onNodeWithText("just now").assertIsDisplayed()
  }

  /**
   * A file this app did not write appears without the screen being touched.
   *
   * The workspace is the kernel's `HOME`, so a cell calling `open(..., 'w')` puts a file
   * in the directory being browsed and nothing tells the listing. The reference answers exactly
   * this with a poll - `FileBrowserModel` runs `cd('.')` on a `Poll` at
   * `DEFAULT_REFRESH_INTERVAL = 10000` with `standby: 'when-hidden'`
   * (`packages/filebrowser/src/model.ts:26,97-106`) - because its own `fileChanged` signal only
   * ever reports changes the *client* made.
   *
   * Nothing is tapped here on purpose: a refresh that needed an interaction would not be the
   * behaviour the reference has.
   */
  @Test
  fun aFileWrittenBySomethingElseAppearsWithoutTouchingTheScreen() {
    given("notes.txt")
    showExplorer()
    compose.onNodeWithText("notes.txt").assertIsDisplayed()

    File(temp.root, "written_by_a_cell.csv").writeText("region,amount")

    compose.waitUntil(POLL_TIMEOUT_MILLIS) {
      compose.onAllNodesWithText("written_by_a_cell.csv").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("written_by_a_cell.csv").assertIsDisplayed()
  }

  /** The other half of the same rule: something removed elsewhere stops being listed. */
  @Test
  fun aFileRemovedBySomethingElseStopsBeingListed() {
    given("notes.txt", "removed_by_a_cell.txt")
    showExplorer()
    compose.onNodeWithText("removed_by_a_cell.txt").assertIsDisplayed()

    assertTrue(File(temp.root, "removed_by_a_cell.txt").delete())

    compose.waitUntil(POLL_TIMEOUT_MILLIS) {
      compose.onAllNodesWithText("removed_by_a_cell.txt").fetchSemanticsNodes().isEmpty()
    }
  }

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
   * No back arrow when there is nowhere to go back to.
   *
   * The explorer is the first screen, so this is its only shape - a bar that kept an
   * arrow would offer a route out of the screen the app opens on.
   */
  @Test
  fun theBackArrowIsAbsentWhenTheExplorerIsTheRoot() {
    given("notes.txt")
    showExplorer(onBack = null)

    compose.onAllNodesWithContentDescription("Back to recents").assertCountEquals(0)
  }

  /**
   * The filebrowser's New drop-down, on a phone: a "+" in the bar opens Notebook / Python file /
   * Folder. The creation itself is tested by `ExplorerViewModelTest`; this proves the menu is on
   * screen and reachable, and that a notebook created in the browsed directory shows up in the
   * list it opened from - `path: this.model.path` (`browser.ts:558-566`).
   */
  @Test
  fun theNewMenuCreatesANotebookInTheDirectoryBeingBrowsed() {
    showExplorer()

    compose.onNodeWithContentDescription("New").performClick()
    compose.onNodeWithText("New notebook").performClick()

    waitForText("Untitled.ipynb")
    compose.onNodeWithText("Untitled.ipynb").assertIsDisplayed()
  }

  @Test
  fun theNewMenuCreatesAPythonFile() {
    showExplorer()

    compose.onNodeWithContentDescription("New").performClick()
    compose.onNodeWithText("New Python file").performClick()

    waitForText("untitled.py")
    compose.onNodeWithText("untitled.py").assertIsDisplayed()
  }

  @Test
  fun theNewMenuCreatesAFolder() {
    showExplorer()

    compose.onNodeWithContentDescription("New").performClick()
    compose.onNodeWithText("New folder").performClick()

    waitForText("Untitled Folder")
    compose.onNodeWithContentDescription("Folder").assertIsDisplayed()
  }

  /**
   * A row's own actions, behind the gesture Android puts them behind.
   *
   * The reference opens these from a right-click context menu on the row
   * (`filebrowser-extension`'s `filebrowser:rename`); Winlator's browser puts the same two behind
   * a per-row menu as well (`ContainerFileManagerFragment.showListItemMenu`). Long press is the
   * touch equivalent, and it is what Home's notebook rows already use, so the app has one gesture
   * for "what else can I do to this row" rather than two.
   */
  @Test
  fun aLongPressOnARowOffersRenaming() {
    given("notes.txt")
    showExplorer()

    compose.onNodeWithText("notes.txt").performTouchInput { longClick() }

    compose.onNodeWithText("Rename").assertIsDisplayed()
  }

  /** The dialog opens with the stem selected, so typing replaces the name and keeps `.txt`. */
  @Test
  fun renamingARowMovesTheFileAndShowsTheNewName() {
    given("notes.txt")
    showExplorer()

    compose.onNodeWithText("notes.txt").performTouchInput { longClick() }
    compose.onNodeWithText("Rename").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("kept")
    compose.onNodeWithText("Rename").performClick()

    waitForText("kept.txt")
    compose.runOnIdle { assertEquals(listOf("kept.txt"), temp.root.list()?.toList()) }
  }

  /**
   * A taken name is asked about rather than refused, which is the reference's one recoverable
   * failure: `renameFile` catches the 409 and offers `"%1" already exists, overwrite?`.
   */
  @Test
  fun renamingOntoATakenNameAsksBeforeReplacing() {
    given("notes.txt", "taken.txt")
    showExplorer()

    compose.onNodeWithText("notes.txt").performTouchInput { longClick() }
    compose.onNodeWithText("Rename").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("taken")
    compose.onNodeWithText("Rename").performClick()

    waitForText("Overwrite")
    compose.onNodeWithText("\"taken.txt\" already exists, overwrite?").assertIsDisplayed()
  }

  /**
   * Delete asks first, and the question names what would go.
   *
   * `DirListing.delete` (`listing.ts:451-500`) puts the dialog in front of `_delete` and focuses
   * Cancel "to protect from accidental deletion"; a Compose dialog has no focused default, so the
   * protection here is that Cancel is the dismiss button and Delete is drawn in the error colour.
   */
  @Test
  fun deletingARowAsksFirstAndCancellingKeepsTheFile() {
    given("notes.txt")
    showExplorer()

    compose.onNodeWithText("notes.txt").performTouchInput { longClick() }
    compose.onNodeWithText("Delete").performClick()
    compose.onNodeWithText("Are you sure you want to permanently delete: notes.txt?").assertIsDisplayed()
    compose.onNodeWithText("Cancel").performClick()

    compose.onNodeWithText("notes.txt").assertIsDisplayed()
    compose.runOnIdle { assertEquals(listOf("notes.txt"), temp.root.list()?.toList()) }
  }

  @Test
  fun confirmingRemovesTheRowAndTheFile() {
    given("notes.txt", "keep.txt")
    showExplorer()

    compose.onNodeWithText("notes.txt").performTouchInput { longClick() }
    compose.onNodeWithText("Delete").performClick()
    confirmDelete()

    waitUntilGone("notes.txt")
    compose.runOnIdle { assertEquals(listOf("keep.txt"), temp.root.list()?.toList()) }
  }

  /**
   * A folder with something in it is refused, and the explorer says so where it says everything
   * else - the server sharing this workspace refuses the same thing (`filemanager.py:607-613`).
   */
  @Test
  fun aFolderWithSomethingInItIsRefusedOutLoud() {
    given("reports/summary.ipynb")
    showExplorer()

    compose.onNodeWithText("reports").performTouchInput { longClick() }
    compose.onNodeWithText("Delete").performClick()
    confirmDelete()

    waitForText("reports")
    compose.runOnIdle { assertTrue(File(temp.root, "reports/summary.ipynb").exists()) }
  }

  private fun showExplorer(
    onOpenFile: (WorkspaceEntry) -> Unit = {},
    onBack: (() -> Unit)? = {},
    onOpenRecent: (String) -> Unit = {},
    recents: List<RecentDocument> = emptyList(),
  ) {
    compose.setContent {
      JupyterDroidTheme {
        ExplorerScreen(
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          // Explore, don't create: this test keeps its view-model noise out and a FakeWorkspace
          // would eat `WorkspaceEntry` rows. The repository is unused by the screen save to
          // construct the ViewModel, so the DiskNotebookRepository(temp.root) is inert here.
          notebookRepository = DiskNotebookRepository(temp.root),
          recentDocuments = FakeRecentDocumentsRepository(recents),
          explorerSettings = FakeExplorerSettingsRepository(),
          onOpenFile = onOpenFile,
          onOpenRecent = onOpenRecent,
          onBack = onBack,
        )
      }
    }
    waitForAnyRow()
  }

  private fun waitForAnyRow() = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText("Workspace").fetchSemanticsNodes().isNotEmpty()
  }

  /**
   * The dialog's button, not its title - both say "Delete".
   *
   * That is the reference's doing rather than ours: `DirListing.delete` titles the dialog with the
   * action name and labels the accept button with it too (`listing.ts:483-495`).
   */
  private fun confirmDelete() = compose.onAllNodesWithText("Delete").filterToOne(hasClickAction()).performClick()

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitUntilGone(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
  }

  private fun given(vararg paths: String) {
    paths.forEach { path ->
      val file = File(temp.root, path)
      if (path.endsWith("/")) file.mkdirs() else file.apply { parentFile?.mkdirs() }.writeText("x")
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L

    /** Longer than one refresh interval, so a poll has a turn before this gives up. */
    const val POLL_TIMEOUT_MILLIS = 30_000L
    const val RECENTS_HEADING = "Recent"

    /**
     * The one recent the collapse cases seed - a path **outside** the listing on purpose: the
     * seeded file is a row of its own below, so a recent naming it could never be asserted gone.
     */
    const val RECENT_ROW = "reports/summary.ipynb"

    /** The section's overflow, named for the surface it acts on as every other menu here is. */
    const val RECENTS_ACTIONS = "Recent actions"

    /** `docmanager:clear-recents`' own label (`docmanager-extension/src/recents.ts:73`). */
    const val CLEAR_RECENTS = "Clear Recent Documents"

    /** What the heading says it is doing, in the two states the reference's aria-expanded has. */
    const val EXPANDED = "Expanded"
    const val COLLAPSED = "Collapsed"
  }
}
