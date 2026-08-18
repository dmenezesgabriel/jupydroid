package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import org.junit.Assert.assertEquals
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

  // --- the View menu (P2f-2) --------------------------------------------------------------

  /**
   * Its own overflow rather than [NotebookBar]'s, so what is asserted here is that there is a
   * second way in at all - the question no JVM test answers.
   */
  @Test
  fun theViewMenuOffersBothHeadingCommands() {
    showTopBar()

    compose.onNodeWithContentDescription("View actions").performClick()

    compose.onNodeWithText(COLLAPSE_ALL_HEADINGS).assertIsDisplayed()
    compose.onNodeWithText(EXPAND_ALL_HEADINGS).assertIsDisplayed()
  }

  @Test
  fun collapseAllAsksForTheCollapse() {
    showTopBar()

    compose.onNodeWithContentDescription("View actions").performClick()
    compose.onNodeWithText(COLLAPSE_ALL_HEADINGS).performClick()

    assertEquals(listOf(true), headingsSetTo)
  }

  /** The opposite row carries the opposite argument, which is the whole of the pair's difference. */
  @Test
  fun expandAllAsksForTheExpand() {
    showTopBar()

    compose.onNodeWithContentDescription("View actions").performClick()
    compose.onNodeWithText(EXPAND_ALL_HEADINGS).performClick()

    assertEquals(listOf(false), headingsSetTo)
  }

  // --- the line-number gutter (P2g-1) -----------------------------------------------------

  /**
   * The row **names what tapping will do** rather than carrying a check beside a fixed label.
   *
   * That is this app's own rule - `COLLAPSE_CODE`/`EXPAND_CODE` in [cellActionItems] - and here it
   * is load-bearing rather than tidy: a toggled `DropdownMenuItem`'s state lives on a node
   * Android's accessibility tree gives no label to, so a Maestro flow cannot read it
   * (`docs/DECISIONS.md`). A label that flips is a label both trees can see.
   */
  @Test
  fun theViewMenuOffersToHideTheGutterWhileItIsShown() {
    showTopBar(lineNumbersShown = true)

    compose.onNodeWithContentDescription("View actions").performClick()

    compose.onNodeWithText(HIDE_LINE_NUMBERS).assertIsDisplayed()
  }

  @Test
  fun theViewMenuOffersToShowTheGutterOnceItIsHidden() {
    showTopBar(lineNumbersShown = false)

    compose.onNodeWithContentDescription("View actions").performClick()

    compose.onNodeWithText(SHOW_LINE_NUMBERS).assertIsDisplayed()
  }

  @Test
  fun theRowAsksForTheToggle() {
    showTopBar(lineNumbersShown = true)

    compose.onNodeWithContentDescription("View actions").performClick()
    compose.onNodeWithText(HIDE_LINE_NUMBERS).performClick()

    assertEquals(1, lineNumberToggles)
  }

  // --- notebook:toggle-autoclosing-brackets, universal (P2g-3) ----------------------------

  @Test
  fun theViewMenuOffersToDisableBracketsWhileTheyAreOn() {
    showTopBar(autoClosingBrackets = true)

    compose.onNodeWithContentDescription("View actions").performClick()

    compose.onNodeWithText(DISABLE_AUTO_CLOSING_BRACKETS).assertIsDisplayed()
  }

  @Test
  fun theViewMenuOffersToEnableBracketsOnceTheyAreOff() {
    showTopBar(autoClosingBrackets = false)

    compose.onNodeWithContentDescription("View actions").performClick()

    compose.onNodeWithText(ENABLE_AUTO_CLOSING_BRACKETS).assertIsDisplayed()
  }

  @Test
  fun theBracketsRowAsksForTheToggle() {
    showTopBar(autoClosingBrackets = true)

    compose.onNodeWithContentDescription("View actions").performClick()
    compose.onNodeWithText(DISABLE_AUTO_CLOSING_BRACKETS).performClick()

    assertEquals(1, autoClosingBracketsToggles)
  }

  private val renamed = mutableListOf<String>()
  private var undos = 0
  private var redos = 0
  private val headingsSetTo = mutableListOf<Boolean>()
  private var lineNumberToggles = 0
  private var autoClosingBracketsToggles = 0

  private fun showTopBar(
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    lineNumbersShown: Boolean = true,
    autoClosingBrackets: Boolean = true,
  ) {
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
          onSetAllHeadingsCollapsed = { headingsSetTo += it },
          lineNumbersShown = lineNumbersShown,
          onToggleLineNumbers = { lineNumberToggles++ },
          autoClosingBrackets = autoClosingBrackets,
          onToggleAutoClosingBrackets = { autoClosingBracketsToggles++ },
        )
      }
    }
  }
}
