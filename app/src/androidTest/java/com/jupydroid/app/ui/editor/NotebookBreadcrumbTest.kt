package com.jupydroid.app.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notebook's breadcrumb (P2w-1): where a nested document lives, and the way back to its folder.
 *
 * Instrumented rather than JVM because every question here is about *layout* - which row is above
 * which, whether a row is drawn at all, and what three rows cost. The segmenting itself is
 * `PathCrumbsTest`.
 */
@RunWith(AndroidJUnit4::class)
class NotebookBreadcrumbTest {
  @get:Rule val compose = createComposeRule()

  /**
   * **Above the actions**, which is where `BreadcrumbsControl` sits relative to a tab title in VS
   * Code (`parts/editor/breadcrumbsControl.ts`) - and since P8e the title itself is the tab's, so
   * the trail is the first row this bar draws. Asserted at the narrowest window the app is driven
   * at, because that is where a second row is hardest to afford.
   */
  @Test
  fun theTrailSitsAboveTheActions() {
    showTopBar(crumbs = NESTED)

    val crumb = compose.onNodeWithText(FOLDER).getUnclippedBoundsInRoot()
    val actions = compose.onNodeWithContentDescription(FIND_ACTION).getUnclippedBoundsInRoot()

    assertTrue("the folder crumb at $crumb is not above the actions at $actions", crumb.bottom <= actions.top)
  }

  /**
   * **Absent at the root, not blank.** VS Code renders `"no elements"` for a document with no
   * ancestry; 48dp spent saying nothing is the trade P2v declined the run gutter over.
   */
  @Test
  fun aNotebookAtTheRootDrawsNoTrailAtAll() {
    showTopBar(crumbs = emptyList())

    compose.onNodeWithText(ROOT).assertDoesNotExist()
    compose.onNodeWithText(FOLDER).assertDoesNotExist()
  }

  /** Every segment is a folder, so the root is reachable from a document two levels down. */
  @Test
  fun everyAncestorIsOnScreen() {
    showTopBar(crumbs = NESTED)

    compose.onNodeWithText(ROOT).assertIsDisplayed()
    compose.onNodeWithText(FOLDER).assertIsDisplayed()
  }

  /**
   * **A tap reports the folder, which is JupyterLab's behaviour and not VS Code's**: the reference
   * this app takes behaviour from calls `this._model.cd(destination)`
   * (`packages/filebrowser/src/crumbs.ts:415`), where VS Code opens a picker.
   */
  @Test
  fun tappingASegmentReportsThatFoldersPath() {
    showTopBar(crumbs = NESTED)

    compose.onNodeWithText(FOLDER).performClick()

    assertEquals(listOf("reports"), browsed)
  }

  /** The root crumb's path is the empty string, which is the workspace root and not "no target". */
  @Test
  fun tappingTheRootReportsTheWorkspaceRoot() {
    showTopBar(crumbs = NESTED)

    compose.onNodeWithText(ROOT).performClick()

    assertEquals(listOf(""), browsed)
  }

  /**
   * **It collapses over one fraction**, and lands where a notebook that never had a trail lands:
   * collapsed, both bars are the action row alone.
   */
  @Test
  fun theTrailGivesItsHeightBackAsTheNotebookIsRead() {
    showTopBar(crumbs = NESTED)

    val whole = actionRowTop(NESTED, collapse = 0f)
    val collapsed = actionRowTop(NESTED, collapse = 1f)
    val neverHadOne = actionRowTop(emptyList(), collapse = 1f)

    assertTrue("the actions start at $collapsed collapsed, against $whole whole", collapsed < whole)
    assertEquals("collapsed, a trail costs what a notebook without one costs", neverHadOne, collapsed)
  }

  /**
   * **What the second row costs, as a number.** Since P8e the bar is at most two rows - the trail
   * over Material's 64dp action row, the same 112dp P2q-2 settled for the title strip it replaced
   * (`docs/DECISIONS.md`) - and this pins the cost as measured rather than described.
   *
   * **Measured as a difference, not as a total**, because a total is not the same on two devices:
   * `EditorTopBar` consumes `TopAppBarDefaults.windowInsets`, and the status bar above it is 24dp
   * here and something else on the tablet. What each row *reserves* is the same everywhere.
   */
  @Test
  fun theSecondRowsCostIsPinnedAgainstTheOneRowBar() {
    showTopBar(crumbs = emptyList())

    val nothing = actionRowTop(emptyList(), collapse = 1f)
    val withTrail = actionRowTop(NESTED, collapse = 0f)

    assertEquals("the trail reserves one interactive minimum", ROW, withTrail - nothing)
    assertEquals("so the two-row bar is 112dp", TWO_ROW_BAR, ACTION_ROW + (withTrail - nothing))
  }

  /**
   * Where the action row starts, with [crumbs] drawn and the bar [collapse]d that far.
   *
   * Read off the Find action rather than off a tagged container: it is a node the app draws for
   * its own reasons, so it cannot drift from what is on screen, and only *differences* between
   * two of these are asserted. (It used to be read off the back arrow, which is the tab strip's
   * own node now and is simply not drawn on this bar.)
   */
  private fun actionRowTop(crumbs: List<Pair<String, String>>, collapse: Float): Dp {
    shown.value = crumbs
    collapsed.floatValue = collapse
    compose.waitForIdle()
    return compose.onNodeWithContentDescription(FIND_ACTION).getUnclippedBoundsInRoot().top
  }

  private val browsed = mutableListOf<String>()

  /** What the bar is showing. State rather than an argument: the rule allows one composition. */
  private val shown = mutableStateOf(emptyList<Pair<String, String>>())
  private val collapsed = mutableFloatStateOf(0f)

  private fun showTopBar(crumbs: List<Pair<String, String>>) {
    shown.value = crumbs
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(COMPACT)) {
        JupyterDroidTheme {
          EditorTopBar(
            canUndo = false,
            canRedo = false,
            onUndo = {},
            onRedo = {},
            crumbs = shown.value,
            collapsibleRegion = collapsed.floatValue,
            onGoToFolder = { browsed += it },
          )
        }
      }
    }
  }

  private companion object {
    /** The emulator's own window, and the narrowest this app is driven at. */
    val COMPACT = DpSize(320.dp, 640.dp)

    const val ROOT = "Workspace"
    const val FOLDER = "reports"

    /** Material's interactive minimum, which is what the collapsing row of this bar reserves. */
    val ROW = 48.dp

    /** Material's own container height for a bar's action row, which this app has never changed. */
    val ACTION_ROW = 64.dp

    /** The measured two-row bar: a 48dp collapsible row over Material's 64dp action row. */
    val TWO_ROW_BAR = 112.dp

    /** What `ancestorCrumbsFor("reports/Nested.ipynb", ROOT_LABEL)` produces. */
    val NESTED = listOf("" to ROOT, "reports" to FOLDER)
  }
}
