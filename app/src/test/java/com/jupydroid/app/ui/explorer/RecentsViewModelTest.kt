package com.jupydroid.app.ui.explorer

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeExplorerSettingsRepository
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.domain.model.ExplorerSettings
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.shownWhileBrowsing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Recents section: what was opened, whether the section is shut, and what a tap does with one.
 *
 * Its own class because the section is its own thing - `docmanager`'s `RecentsManager` in the
 * reference, never `filebrowser`'s - which is what [RecentsViewModel] was split out of
 * [ExplorerViewModel] to say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentsViewModelTest {
  @get:Rule val dispatcher = MainDispatcherRule()

  /**
   * The root carries Recents; a subdirectory does not.
   *
   * A recents list is not a property of the folder you are standing in - the reference's own
   * surfaces are global, a File menu submenu and a sidebar panel, never per-directory. The rule is
   * `shownWhileBrowsing`'s, applied where the directory is known, which since P7c is the screen.
   */
  @Test
  fun theRootCarriesRecentsAndADirectoryDoesNot() = runTest {
    val viewModel = recents(recent("reports/summary.ipynb"))

    val atTheRoot = viewModel.uiState.value.documents.shownWhileBrowsing("")
    val inTheFolder = viewModel.uiState.value.documents.shownWhileBrowsing("reports")

    assertEquals(listOf("reports/summary.ipynb"), atTheRoot.map { it.path })
    assertTrue("a subdirectory must not carry Recents", inTheFolder.isEmpty())
  }

  /** Directories first, which is `RecentsMenu.updateItems`' only sorting rule. */
  @Test
  fun recentsListDirectoriesFirst() = runTest {
    val viewModel = recents(recent("reports/summary.ipynb"), recentFolder("reports"))

    val shown = viewModel.uiState.value.documents.shownWhileBrowsing("")

    assertEquals(listOf("reports", "reports/summary.ipynb"), shown.map { it.path })
  }

  /**
   * A recent folder navigates the browser it lives in; only a document opens.
   *
   * The reference makes the same split - `openRecent` prefers the file browser's `openPath` for a
   * `contentType: 'directory'` and sends everything else to `docmanager:open`
   * (`packages/mainmenu-extension/src/recents.ts`).
   */
  @Test
  fun openingARecentFolderIsHandedToTheBrowserRatherThanToAnEditor() = runTest {
    val viewModel = recents(recentFolder("reports"))
    var browsed: String? = null
    var opened: String? = null

    viewModel.open(viewModel.uiState.value.documents.single(), onFolder = {
      browsed = it
    }, onDocument = { opened = it })

    assertEquals("reports", browsed)
    assertNull("a folder must not be handed to a document editor", opened)
  }

  @Test
  fun openingARecentDocumentHandsItToTheEditor() = runTest {
    val viewModel = recents(recent("reports/summary.ipynb"))
    var browsed: String? = null
    var opened: String? = null

    viewModel.open(viewModel.uiState.value.documents.single(), onFolder = {
      browsed = it
    }, onDocument = { opened = it })

    assertEquals("reports/summary.ipynb", opened)
    assertNull("a document must not move the browser", browsed)
  }

  /**
   * The section arrives open, which is the reference's own default: a view's `collapsed` is
   * `!!viewDescriptor.collapsed` unless a stored state says otherwise
   * (`viewContainerModel.ts:517`).
   */
  @Test
  fun theSectionArrivesOpen() = runTest {
    assertEquals(false, recents(recent("notes.txt")).uiState.value.collapsed)
  }

  /**
   * `setExpanded(!isExpanded())` - the reference's header toggles rather than collapsing one way
   * (`paneview.ts:285-292`), so the same act has to bring it back.
   */
  @Test
  fun theHeadingTogglesTheSectionShutAndOpenAgain() = runTest {
    val viewModel = recents(recent("notes.txt"))

    viewModel.toggle()
    assertEquals(true, viewModel.uiState.value.collapsed)

    viewModel.toggle()
    assertEquals(false, viewModel.uiState.value.collapsed)
  }

  /**
   * Collapsing hides the rows and keeps the list: `setCollapsed` writes a view's *state*, never
   * its contents, and Clear is the only thing that empties this section.
   */
  @Test
  fun collapsingTheSectionDoesNotForgetWhatWasOpened() = runTest {
    val viewModel = recents(recent("notes.txt"))

    viewModel.toggle()

    assertEquals(listOf("notes.txt"), viewModel.uiState.value.documents.map { it.path })
  }

  /**
   * A section shut before the process died comes back shut - the reference stores `collapsed` and
   * reads it back into the view's state on the next launch (`viewContainerModel.ts:456-461`,
   * `:90-108`).
   */
  @Test
  fun aSectionShutBeforeTheProcessDiedComesBackShut() = runTest {
    val viewModel = recents(
      settings = FakeExplorerSettingsRepository(ExplorerSettings(recentsCollapsed = true)),
      opened = arrayOf(recent("notes.txt")),
    )

    assertEquals(true, viewModel.uiState.value.collapsed)
  }

  /** And the toggle writes through, on every change, as `setCollapsed` does. */
  @Test
  fun togglingIsWrittenToTheStore() = runTest {
    val settings = FakeExplorerSettingsRepository()
    val viewModel = recents(settings = settings, opened = arrayOf(recent("notes.txt")))

    viewModel.toggle()

    assertEquals(true, settings.observeSettings().first().recentsCollapsed)
  }

  /** `docmanager:clear-recents`, which the reference offers from the same submenu. */
  @Test
  fun clearingEmptiesTheSection() = runTest {
    val viewModel = recents(recent("notes.txt"))

    viewModel.clear()

    assertTrue(viewModel.uiState.value.documents.isEmpty())
  }

  private fun recents(vararg opened: RecentDocument) = recents(FakeExplorerSettingsRepository(), opened)

  private fun recents(settings: FakeExplorerSettingsRepository, opened: Array<out RecentDocument>) =
    RecentsViewModel(FakeRecentDocumentsRepository(opened.toList()), settings)

  private fun recent(path: String) = RecentDocument(path, WorkspaceEntryKind.FILE)

  private fun recentFolder(path: String) = RecentDocument(path, WorkspaceEntryKind.DIRECTORY)
}
