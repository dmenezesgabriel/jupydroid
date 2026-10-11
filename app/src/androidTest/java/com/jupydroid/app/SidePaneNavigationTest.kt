package com.jupydroid.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.shell.SEARCH_WORKSPACE_BOX
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

  /** The other half of the toggle, on the search panel - see the ToC's own case above. */
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
   *
   * **The query is what is asserted, and the results moved next door** - measured rather than
   * preferred. Under `ForcedSize` the list pane lays out at **zero height**: the query box measures
   * `Rect(0,0,0,0)` here while the rail beside it measures properly, because
   * `currentWindowAdaptiveInfo()` reads the *activity's* window and not the override (this file's
   * own `windowFor`, and `TwoPaneLayoutTest` records the same). This test used to read a result row
   * in that pane, which a `Column` lays out anyway by overflowing a parent with no room - a
   * `LazyColumn` composes only what fits, so there is nothing there to read.
   *
   * A box still holding *"cat"* proves the whole of the claim: a rebuilt `WorkspaceSearchViewModel`
   * starts at `SearchQuery("")`, so surviving text is a surviving instance. The results half is
   * `SearchSidePanelTest.whatWasFoundSurvivesThePanelLeavingComposition`, where the panel is hosted
   * on its own and has a window to be laid out in.
   */
  @Test
  fun whatASearchFoundSurvivesLeavingThePanelAndComingBack() {
    startApp(twoPane = true)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()
    waitForContentDescription(SEARCH_WORKSPACE_BOX)
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performTextInput(SEARCHED_TERM)
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).assertTextEquals(SEARCHED_TERM)

    compose.onNodeWithContentDescription(FILES).performClick()
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()

    waitForContentDescription(SEARCH_WORKSPACE_BOX)
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).assertTextEquals(SEARCHED_TERM)
  }

  /**
   * **A window that narrows onto a panel the bar cannot leave still draws the browser.**
   *
   * `sidePanel` is `rememberSaveable`, so it survives the activity relaunch a rotation causes; the
   * rail is drawn only where there are two panes. Between them, a tablet rotated to portrait -
   * 1280dp landscape is two panes, 800dp portrait is one - used to draw the table of contents in
   * the list pane with no rail to switch away from it, and the file browser could not be reached
   * again without killing the app.
   *
   * **The bottom bar narrows this rather than reopening it.** The bar the narrowed window lands on can
   * leave Files and Search, so those two survive - see the case below. The table of contents has no
   * item on it, so it is the one panel that still has to fall back, and that is all that is left of
   * the defect.
   */
  @Test
  fun aWindowThatNarrowsOntoTheOutlineStillDrawsTheBrowser() {
    val twoPane = mutableStateOf(true)
    startApp(twoPane)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).performClick()
    waitForText(NO_HEADINGS)

    twoPane.value = false

    waitForText(WORKSPACE)
    compose.onNodeWithText(WORKSPACE).assertIsDisplayed()
  }

  /**
   * **And a window that narrows onto Search keeps it**, which is what the bar buys: the earlier
   * rule sent every panel back to the browser because a narrowed window had no control at all, and a
   * user who rotated mid-search lost the search. The bar can leave Search, so nothing has to.
   */
  @Test
  fun aWindowThatNarrowsOntoSearchKeepsIt() {
    val twoPane = mutableStateOf(true)
    startApp(twoPane)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()
    waitForContentDescription(SEARCH_WORKSPACE_BOX)

    twoPane.value = false

    waitForContentDescription(SEARCH_WORKSPACE_BOX)
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).assertIsDisplayed()
    assertGone(WORKSPACE)
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

  /**
   * **A one-pane window draws the bar, not the rail** - a rail there would spend a quarter of a
   * 320dp screen on chrome, a cost already measured and not reopened here.
   *
   * The table of contents is what tells the two controls apart: it is on the rail and not on the
   * bar, so its absence beside two present destinations is the whole assertion.
   */
  @Test
  fun aOnePaneWindowDrawsTheBarAndNotTheRail() {
    startApp(twoPane = false)

    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(SEARCH).assertIsDisplayed()
    val rail = compose.onAllNodesWithContentDescription(TABLE_OF_CONTENTS).fetchSemanticsNodes()
    assert(rail.isEmpty()) { "a one-pane window drew ${rail.size} rail item(s)" }
  }

  /**
   * And the reverse: two panes get the rail and **no** bar under it. Counted rather than asserted
   * present, because both controls name their items the same - two Files items would be the tell.
   */
  @Test
  fun aTwoPaneWindowDrawsTheRailAndNoBar() {
    startApp(twoPane = true)

    waitForText(WORKSPACE)
    val files = compose.onAllNodesWithContentDescription(FILES).fetchSemanticsNodes()
    assertEquals("two panes drew a bar under the rail", 1, files.size)
  }

  /**
   * **The reason this step exists**: workspace search had no route at all where the shell draws one
   * pane. The rail was the only control that could select it and the rail is drawn only beside two
   * panes, so a phone - and the tablet in portrait at 800dp - could not reach it.
   */
  @Test
  fun theBarReachesSearchWhereTheRailIsNotDrawn() {
    startApp(twoPane = false)
    waitForText(WORKSPACE)

    compose.onNodeWithContentDescription(SEARCH).performClick()

    waitForContentDescription(SEARCH_WORKSPACE_BOX)
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).assertIsDisplayed()
  }

  /** And back again, which is the half that makes it navigation rather than a one-way door. */
  @Test
  fun theBarReturnsToTheBrowser() {
    startApp(twoPane = false)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()
    waitForContentDescription(SEARCH_WORKSPACE_BOX)

    compose.onNodeWithContentDescription(FILES).performClick()

    waitForText(WORKSPACE)
    compose.onNodeWithText(WORKSPACE).assertIsDisplayed()
  }

  /**
   * **Back reaches the start destination before it leaves the app.** Android's own principle: the
   * start destination *"is also the last screen the user sees when they return to the launcher
   * after pressing the Back button"* (`developer.android.com/guide/navigation/principles`). The
   * browser is this app's root, so Back out of a one-pane Search selects Files rather than exiting
   * - and `NavDisplay` cannot do it, because there is one entry on the stack and nothing to pop.
   */
  @Test
  fun backFromSearchReachesTheBrowserRatherThanLeaving() {
    startApp(twoPane = false)
    waitForText(WORKSPACE)
    compose.onNodeWithContentDescription(SEARCH).performClick()
    waitForContentDescription(SEARCH_WORKSPACE_BOX)

    Espresso.pressBack()

    waitForText(WORKSPACE)
    compose.onNodeWithText(WORKSPACE).assertIsDisplayed()
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
              launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
              enteredSpacePorts = { _, _ ->
                EnteredSpacePorts(
                  notebookRepository = repository,
                  workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
                  workspaceSearcher = FakeWorkspaceSearcher(mapOf(SEARCHABLE_FILE to SEARCHED_LINE)),
                )
              },
            ),
            sharedNotebook = null,
            onImportConsumed = {},
            twoPane = twoPane.value,
          )
        }
      }
    }
    compose.enterBaseSpace()
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

    /** The base Space's name - `BASE_SPACE.name`, the one row every device holds. */
    const val TABLE_OF_CONTENTS = "Table of contents"

    /** A workspace the search panel can actually find something in - see the persistence case. */
    const val SEARCHABLE_FILE = "notes.py"
    const val SEARCHED_LINE = "print(cat)"
    const val SEARCHED_TERM = "cat"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
