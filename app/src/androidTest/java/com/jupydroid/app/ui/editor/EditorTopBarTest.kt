package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notebook's title is where its rename lives, which is where JupyterLab puts it -
 * `docmanager:rename` hangs off `[data-type="document-title"]`
 * (`packages/docmanager-extension/schema/plugin.json:73-77`).
 *
 * Instrumented because "is there something here to press" is not a question a JVM test can
 * answer, which is the whole reason `CellHeaderTest` exists.
 */
@RunWith(AndroidJUnit4::class)
class EditorTopBarTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theTitleIsOnScreen() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").assertIsDisplayed()
  }

  @Test
  fun tappingTheTitleOpensTheRenameDialog() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()

    compose.onNodeWithText("Rename notebook").assertIsDisplayed()
  }

  @Test
  fun renamingFromTheTitleReportsTheNewTitle() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Linear algebra")
    compose.onNodeWithText("Rename").performClick()

    assertEquals(listOf("Linear algebra"), renamed)
  }

  @Test
  fun cancellingRenamesNothing() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Linear algebra")
    compose.onNodeWithText("Cancel").performClick()

    assertEquals(emptyList<String>(), renamed)
  }

  /**
   * The bar carries the title and nothing that belongs to the kernel.
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
   * the cell menu cost three attempts (`docs/DECISIONS.md`). JupyterLab keeps both items in the
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

  /**
   * **The title is on a row of its own, above the actions.** That is the reference's arrangement
   * in single-document mode - the mode that shows one document at a time, which is this app's
   * shape: `#jp-title-panel` is a strip holding nothing but the document's name
   * (`packages/application/style/titlepanel.css:10-37`), inserted above the document's own toolbar
   * by `LabShell.mode`'s setter (`packages/application/src/shell.ts:690-705`).
   *
   * Asserted as a geometry rather than as a hierarchy: what the reader sees is that the name does
   * not share a line with the buttons, and a node's bounds are what say so.
   */
  @Test
  fun theTitleSitsAboveTheActions() {
    showTopBar()

    val title = compose.onNodeWithText("Intro to NumPy").getUnclippedBoundsInRoot()
    val undo = compose.onNodeWithContentDescription("Undo cell operation").getUnclippedBoundsInRoot()
    assertTrue("title $title overlaps the actions at $undo", title.bottom <= undo.top)
  }

  /**
   * **The whole file name is on screen at 320dp**, which is the width this app is driven at and
   * the one that decides. It was not: sharing the bar with undo, redo, search and the `⋮` left the
   * name about 64dp and it truncated to `Gutt…` - `evidence/p2n-gutter/before/` and `after/`, both
   * of them, since the bar was not what those flows were about.
   *
   * Measured against the same string drawn with nothing competing for the row, because Compose's
   * semantics carry the full text whether or not it fits: `onNodeWithText` finds a truncated title
   * exactly as it finds a whole one, so only the width can tell them apart.
   */
  @Test
  fun theWholeFileNameFitsAtTheNarrowestWidth() {
    compose.setContent {
      JupyterDroidTheme {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(COMPACT)) {
          Column {
            EditorTopBar(
              title = LONG_NAME,
              canUndo = true,
              canRedo = true,
              onRename = {},
              onUndo = {},
              onRedo = {},
              onBack = {},
            )
            Text(
              text = LONG_NAME,
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.testTag(UNCONSTRAINED_TITLE),
            )
          }
        }
      }
    }

    val drawn = compose.onAllNodesWithText(LONG_NAME)[0].getUnclippedBoundsInRoot()
    val unconstrained = compose.onNodeWithTag(UNCONSTRAINED_TITLE).getUnclippedBoundsInRoot()
    val shown = drawn.right - drawn.left
    val wanted = unconstrained.right - unconstrained.left
    assertTrue("the title draws $shown of the $wanted it wants", shown >= wanted)
  }

  /**
   * **Collapsed, the strip gives its 48dp back to the notebook.** That is what it is for: the row
   * cost a fourth cell's source at 320dp, measured, and four flows caught it
   * (`docs/DECISIONS.md`). Whole and gone are the two ends of [titleCollapseFraction].
   */
  @Test
  fun theTitlesRowGivesItsHeightBackAsTheNotebookIsRead() {
    var collapse by mutableStateOf(0f)
    compose.setContent {
      JupyterDroidTheme {
        EditorTopBar(
          title = LONG_NAME,
          canUndo = false,
          canRedo = false,
          onRename = {},
          onUndo = {},
          onRedo = {},
          onBack = {},
          titleCollapse = collapse,
        )
      }
    }

    val whole = compose.onNodeWithText(LONG_NAME).getUnclippedBoundsInRoot()
    val backAtTop = compose.onNodeWithContentDescription("Back to notebooks").getUnclippedBoundsInRoot().top

    collapse = 1f
    compose.waitForIdle()

    val backCollapsed = compose.onNodeWithContentDescription("Back to notebooks").getUnclippedBoundsInRoot().top
    assertTrue("the title row was $whole and did not reserve height", whole.bottom > whole.top)
    assertTrue(
      "the action row starts at $backCollapsed collapsed, against $backAtTop whole",
      backCollapsed < backAtTop,
    )
  }

  private val renamed = mutableListOf<String>()
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

  private fun showTopBar(canUndo: Boolean = false, canRedo: Boolean = false) {
    compose.setContent {
      JupyterDroidTheme {
        EditorTopBar(
          title = "Intro to NumPy",
          canUndo = canUndo,
          canRedo = canRedo,
          onRename = { renamed += it },
          onUndo = { undos++ },
          onRedo = { redos++ },
          onBack = {},
          onFind = { finds++ },
        )
      }
    }
  }

  private companion object {
    /** The emulator's own window, and the narrowest this app is driven at. */
    val COMPACT = DpSize(320.dp, 640.dp)

    /** A real notebook name, long enough that a shared bar has to cut it. */
    const val LONG_NAME = "Heading Sections.ipynb"

    /** The same string with a whole row to itself, which is the width the title wants. */
    const val UNCONSTRAINED_TITLE = "unconstrained-title"
  }
}
