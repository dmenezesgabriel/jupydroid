package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notebook's action row. The document's name is **not** here - it is the tab's now, the way
 * the reference's `title.label` is the `lm-TabBar-tab`'s: `docmanager:rename` hangs off
 * `[data-type="document-title"]` on that node (`packages/docmanager-extension/schema/plugin.json:73-77`),
 * so the rename affordance moved to `MainAreaTabStripTest` with the strip itself.
 *
 * Instrumented because "is there something here to press" is not a question a JVM test can
 * answer, which is the whole reason `CellHeaderTest` exists.
 */
@RunWith(AndroidJUnit4::class)
class EditorTopBarTest {
  @get:Rule val compose = createComposeRule()

  /**
   * The bar carries actions and nothing that belongs to the kernel.
   *
   * This used to assert the opposite - that Run All was still here - which was the right guard
   * while Run All lived on this bar. It moved to [NotebookBar], beside interrupt and restart and
   * within reach of a thumb, so the assertion is inverted rather than deleted: a kernel action
   * drifting back onto the app bar is exactly the regression worth catching, and `NotebookBarTest`
   * covers Run All being reachable in its new home.
   */
  @Test
  fun theBarCarriesNoKernelActions() {
    showTopBar()

    compose.onNodeWithContentDescription("Run all cells").assertDoesNotExist()
  }

  // --- P2a: undo and redo of cell operations ---

  /**
   * Both are on the bar even with nothing to take back, disabled rather than absent.
   *
   * A control that appears only once it would work is one nobody discovers, which is the lesson
   * the cell menu cost three attempts. JupyterLab keeps both items in the
   * Edit menu at all times and gates them with `isEnabled` (`notebook-extension/src/index.ts:4485`).
   */
  @Test
  fun bothAreOnTheBarAndDisabledWithNothingToTakeBack() {
    showTopBar(canUndo = false, canRedo = false)

    compose.onNodeWithContentDescription("Undo cell operation").assertIsDisplayed().assertIsNotEnabled()
    compose.onNodeWithContentDescription("Redo cell operation").assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun undoBecomesPressableOnceThereIsSomethingToTakeBack() {
    showTopBar(canUndo = true, canRedo = false)

    compose.onNodeWithContentDescription("Undo cell operation").performClick()

    assertEquals(1, undos)
    compose.onNodeWithContentDescription("Redo cell operation").assertIsNotEnabled()
  }

  @Test
  fun redoBecomesPressableOnceSomethingHasBeenTakenBack() {
    showTopBar(canUndo = false, canRedo = true)

    compose.onNodeWithContentDescription("Redo cell operation").performClick()

    assertEquals(1, redos)
  }

  private var finds = 0
  private var undos = 0
  private var redos = 0

  /**
   * `documentsearch:start`, and it is on the bar rather than in the View menu - so it is asserted
   * where a thumb finds it, one tap in.
   */
  @Test
  fun findIsOneTapFromTheNotebooksOwnBar() {
    showTopBar()

    compose.onNodeWithContentDescription(FIND_ACTION).performClick()

    assertEquals(1, finds)
  }

  /**
   * The bar is actions and nothing else: leaving the document is the tab strip's own act - the
   * ✕ beside a browser, the destination-named arrow below one pane - which is where JupyterLab's
   * tabbed mode, VS Code and IntelliJ all keep it. The arrow's own cases are
   * [MainAreaTabStripTest][com.jupydroid.app.ui.shell.MainAreaTabStripTest]'s; this asserts only
   * that none leaked back here.
   */
  @Test
  fun theBarHasNoDismissControl() {
    showTopBar()

    compose.onAllNodesWithContentDescription("Back", substring = true).assertCountEquals(0)
  }

  private fun showTopBar(canUndo: Boolean = false, canRedo: Boolean = false) {
    compose.setContent {
      JupyterDroidTheme {
        EditorTopBar(
          canUndo = canUndo,
          canRedo = canRedo,
          onUndo = { undos++ },
          onRedo = { redos++ },
          onFind = { finds++ },
        )
      }
    }
  }
}
