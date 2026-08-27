package com.jupydroid.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.shell.SEARCH_WORKSPACE_BOX
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The shell's side area: one activity bar, one panel at a time, and the document's own headings
 * reaching a panel that lives in a different `NavEntry`.
 *
 * The pieces have their own tests - `SidePanelRailTest`, `TableOfContentsSidePanelTest`,
 * `OpenDocumentOutlineTest` - and what is left is the wiring, which is the part that could be
 * right in every piece and wrong assembled.
 */
@RunWith(AndroidJUnit4::class)
class SidePaneNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  /** `filebrowser` is rank 100 and opens with the shell; the ToC tab does not. */
  @Test
  fun theShellOpensOnFilesWithTheRailBesideIt() {
    startApp(twoPane = true)

    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertIsDisplayed()
  }

  /**
   * **One panel at a time** - `SideBarHandler._onCurrentChanged` hides the outgoing widget and
   * shows the incoming one (`packages/application/src/shell.ts:2630-2643`). `Workspace` is the
   * breadcrumb only the file browser draws.
   */
  @Test
  fun theRailSwapsTheFileBrowserForTheTableOfContents() {
    startApp(twoPane = true)
    waitForText(WORKSPACE)

    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).performClick()

    waitForText(NO_HEADINGS)
    compose.onNodeWithText(NO_HEADINGS).assertIsDisplayed()
    assertGone(WORKSPACE)
  }

  /**
   * **The seam this whole phase exists for.** The panel is in the list pane and the headings are
   * derived in the notebook's own `NavEntry`, so a heading on screen here is proof that
   * `OpenDocumentOutline` carried it across - nothing in the list pane can compute one.
   */
  @Test
  fun theOpenNotebooksHeadingsReachThePanelInTheOtherPane() {
    startApp(twoPane = true)
    waitForText(WORKSPACE)
    compose.onNodeWithText(TITLE).performClick()
    waitForText(HEADING)

    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).performClick()

    waitForContentDescription("Table of contents $HEADING")
    compose.onNodeWithContentDescription("Table of contents $HEADING").assertIsDisplayed()
  }

  /**
   * `collapse()` sets `currentTitle = null` and leaves the bar visible
   * (`packages/application/src/shell.ts:2383-2394`) - so the way back is one tap, not a gesture.
   */
  @Test
  fun pickingTheShowingPanelCollapsesTheAreaAndLeavesTheRail() {
    startApp(twoPane = true)
    waitForText(WORKSPACE)

    compose.onNodeWithContentDescription(FILES).performClick()

    assertGone(WORKSPACE)
    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
  }

  /**
   * The rail's third item, and the panel behind it - `order: 1` on VS Code's sidebar container
   * (`search.contribution.ts:59`), which is the only reference that registers one at all.
   */
  @Test
  fun theRailSwapsTheFileBrowserForSearch() {
    startApp(twoPane = true)
    waitForText(WORKSPACE)

    compose.onNodeWithContentDescription(SEARCH).performClick()

    waitForText(SEARCH)
    compose.onNodeWithText(SEARCH).assertIsDisplayed()
    assertGone(WORKSPACE)
  }

  /** The other half of the toggle, on the panel P2x-2 added - see the ToC's own case above. */
  @Test
  fun pickingSearchAgainCollapsesTheAreaAndLeavesTheRail() {
    startApp(twoPane = true)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()
    waitForText(SEARCH)

    compose.onNodeWithContentDescription(SEARCH).performClick()

    assertGone(WORKSPACE)
    assertNoPanelTitle()
    compose.onNodeWithContentDescription(SEARCH).assertIsDisplayed()
  }

  /**
   * **What a search found survives leaving the panel and coming back**, which is what the reference
   * does: its view is a registered `ViewPane` whose model outlives being hidden, so switching to
   * the explorer and back does not cost you the walk you just paid for.
   *
   * Here that is a property of *where the ViewModel is scoped*, not of anything the panel does:
   * `viewModel { }` inside the `Explorer` entry resolves the entry's own `ViewModelStore` through
   * `rememberViewModelStoreNavEntryDecorator` (`Navigation.kt:177-180`), which the panel switch
   * does not touch. Pinned rather than assumed, because it would be silently lost by scoping it
   * anywhere else.
   */
  @Test
  fun whatASearchFoundSurvivesLeavingThePanelAndComingBack() {
    startApp(twoPane = true)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()
    waitForContentDescription(SEARCH_WORKSPACE_BOX)
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performTextInput(SEARCHED_TERM)
    waitForText(SEARCHED_LINE)

    compose.onNodeWithContentDescription(FILES).performClick()
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()

    waitForText(SEARCHED_LINE)
    compose.onNodeWithText(SEARCHED_LINE).assertIsDisplayed()
  }

  /**
   * **A window that narrows while a panel is up still has to leave the browser reachable.**
   *
   * `sidePanel` is `rememberSaveable`, so it survives the activity relaunch a rotation causes; the
   * rail is drawn only where there are two panes. Between them, a tablet rotated to portrait -
   * 1280dp landscape is two panes, 800dp portrait is one - used to draw the table of contents in
   * the list pane with no rail to switch away from it, and the file browser could not be reached
   * again without killing the app. P2x-2 widened that from two panels to three, which is why it
   * is fixed here rather than recorded.
   *
   * `p2o-4`'s own header states the rule: *"where the shell draws one pane there is no side area"*.
   */
  @Test
  fun aWindowThatNarrowsWhileAPanelIsUpStillDrawsTheBrowser() {
    val twoPane = mutableStateOf(true)
    startApp(twoPane)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()
    waitForText(SEARCH)

    twoPane.value = false

    waitForText(WORKSPACE)
    compose.onNodeWithText(WORKSPACE).assertIsDisplayed()
  }

  /** The collapsed area is the same problem with nothing on screen at all, rather than the wrong thing. */
  @Test
  fun aWindowThatNarrowsWhileTheAreaIsCollapsedStillDrawsTheBrowser() {
    val twoPane = mutableStateOf(true)
    startApp(twoPane)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(FILES).performClick()
    assertGone(WORKSPACE)

    twoPane.value = false

    waitForText(WORKSPACE)
    compose.onNodeWithText(WORKSPACE).assertIsDisplayed()
  }

  /** A rail on a 320dp phone would spend a quarter of the screen on chrome; P2o-4 owns that bar. */
  @Test
  fun aCompactWindowHasNoRail() {
    startApp(twoPane = false)

    waitForText(WORKSPACE)
    val rail = compose.onAllNodesWithContentDescription(TABLE_OF_CONTENTS).fetchSemanticsNodes()
    assert(rail.isEmpty()) { "a compact window drew ${rail.size} rail item(s)" }
  }

  private fun startApp(twoPane: Boolean) = startApp(mutableStateOf(twoPane))

  /**
   * The width as **state**, so a test can narrow the window on a shell that is already running -
   * which is the half of a rotation that composition sees. `rememberSaveable` keeps its value
   * across the recomposition exactly as it does across the relaunch.
   */
  private fun startApp(twoPane: MutableState<Boolean>) {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking { repository.saveNotebook(notebook()) }
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowFor(twoPane.value))) {
        JupyterDroidTheme {
          MainNavigation(
            appContainer = AppContainer(
              notebookRepository = repository,
              workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
              workspaceSearcher = FakeWorkspaceSearcher(mapOf(SEARCHABLE_FILE to SEARCHED_LINE)),
            ),
            importTitle = null,
            onImportConsumed = {},
            twoPane = twoPane.value,
          )
        }
      }
    }
  }

  private fun notebook() = Notebook(
    TITLE,
    TITLE,
    "python3",
    listOf(Cell("c1", CellKind.MARKDOWN, "# $HEADING")),
  )

  /**
   * The window a test needs is part of the test - `ForcedSize` changes the layout constraints,
   * and the list pane prefers 360dp, so a wide case run at the emulator's 320dp leaves the detail
   * pane no width at all. `TwoPaneLayoutTest` records what that cost.
   */
  private fun windowFor(twoPane: Boolean) = if (twoPane) DpSize(1280.dp, 800.dp) else DpSize(360.dp, 800.dp)

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForContentDescription(description: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
  }

  /**
   * A collapsed area draws no panel at all, and the panel's *title* is what says so - `Search` is
   * still on the rail as a content description, so a text query is the one that can tell the two
   * apart.
   */
  private fun assertNoPanelTitle() {
    val found = compose.onAllNodesWithText(SEARCH).fetchSemanticsNodes()
    assert(found.isEmpty()) { "expected the collapsed area to draw no panel, found ${found.size} title(s)" }
  }

  private fun assertGone(text: String) {
    val found = compose.onAllNodesWithText(text).fetchSemanticsNodes()
    assert(found.isEmpty()) { "expected '$text' to be gone, found ${found.size} node(s)" }
  }

  private companion object {
    const val TITLE = "Outlined.ipynb"
    const val HEADING = "Chapter one"

    /** The breadcrumb only the file browser draws, which is how the two panels are told apart. */
    const val WORKSPACE = "Workspace"
    const val NO_HEADINGS = "No headings"
    const val FILES = "Files"
    const val SEARCH = "Search"
    const val TABLE_OF_CONTENTS = "Table of contents"

    /** A workspace the search panel can actually find something in - see the persistence case. */
    const val SEARCHABLE_FILE = "notes.py"
    const val SEARCHED_LINE = "print(cat)"
    const val SEARCHED_TERM = "cat"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
