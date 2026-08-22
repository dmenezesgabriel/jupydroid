package com.jupydroid.app

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
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

  /** A rail on a 320dp phone would spend a quarter of the screen on chrome; P2o-4 owns that bar. */
  @Test
  fun aCompactWindowHasNoRail() {
    startApp(twoPane = false)

    waitForText(WORKSPACE)
    val rail = compose.onAllNodesWithContentDescription(TABLE_OF_CONTENTS).fetchSemanticsNodes()
    assert(rail.isEmpty()) { "a compact window drew ${rail.size} rail item(s)" }
  }

  private fun startApp(twoPane: Boolean) {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking { repository.saveNotebook(notebook()) }
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowFor(twoPane))) {
        JupyterDroidTheme {
          MainNavigation(
            appContainer = AppContainer(
              notebookRepository = repository,
              workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
            ),
            importTitle = null,
            onImportConsumed = {},
            twoPane = twoPane,
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
    const val TABLE_OF_CONTENTS = "Table of contents"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
