package com.jupydroid.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three things the rename dialog has to get right that only a running screen can show.
 *
 * All three come from JupyterLab's `DocumentManagerDialogs.rename`
 * (`packages/docmanager/src/dialogs.ts:55-100`), and two of them were missing from the dialog
 * this app already shipped on Home.
 */
@RunWith(AndroidJUnit4::class)
class RenameFileDialogTest {
  @get:Rule val compose = createComposeRule()

  /**
   * JupyterLab passes `focusNodeSelector: 'input'`. Without it the dialog opens with no caret
   * and the first keystroke goes nowhere - which is audit A1c exactly, and which no JVM test
   * can see because `performTextInput` focuses the node itself.
   */
  @Test
  fun openingTheDialogPutsTheCaretInTheField() {
    showDialog(currentTitle = "Untitled")

    compose.onNodeWithTag(RENAME_FIELD_TAG).assertIsFocused()
  }

  /** `setSelectionRange(0, value.length - ext.length)`: typing replaces the name, not the `.py`. */
  @Test
  fun theStemIsSelectedAndTheExtensionIsNot() {
    showDialog(currentTitle = "untitled.py")

    compose.onNodeWithTag(RENAME_FIELD_TAG).assert(
      SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, 8)),
    )
  }

  /**
   * Confirming without editing must not rename.
   *
   * A write is what makes a notebook "updated", so a no-op rename would push it to the top of
   * Recents for nothing. JupyterLab returns early on the same condition, silently.
   */
  @Test
  fun confirmingAnUnchangedTitleRenamesNothing() {
    showDialog(currentTitle = "Untitled")

    compose.onNodeWithText("Rename").performClick()

    assertEquals(emptyList<String>(), confirmed)
    assertEquals(1, dismissed)
  }

  /**
   * A name the filesystem cannot hold is refused here, not silently rewritten.
   *
   * JupyterLab validates in this same dialog and names the rule rather than mangling the input:
   * *"Names must have nonzero length, and cannot include "/", "\\", or ":"* (`packages/docmanager/src/dialogs.ts`, `isValidFileName` and the error beside it). Since X1b
   * the name **is** the file, so `a/b` is not a preference we are declining - it is not a name.
   */
  @Test
  fun aNameWithASeparatorCannotBeConfirmed() {
    showDialog("Draft")

    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("reports/final")

    compose.onNodeWithText("Rename").assertIsNotEnabled()
  }

  @Test
  fun aRefusedNameSaysWhichCharactersAreAtFault() {
    showDialog("Draft")

    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("a:b")

    compose.onNodeWithText("Names must have nonzero length", substring = true).assertExists()
  }

  /** A leading dot hides the file from the explorer, from `ls`, and from jupyter_server. */
  @Test
  fun aHiddenNameCannotBeConfirmed() {
    showDialog("Draft")

    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput(".secret")

    compose.onNodeWithText("Rename").assertIsNotEnabled()
  }

  @Test
  fun anEditedTitleIsReportedTrimmed() {
    showDialog(currentTitle = "Untitled")

    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("  Math notes  ")
    compose.onNodeWithText("Rename").performClick()

    assertEquals(listOf("Math notes"), confirmed)
  }

  @Test
  fun aBlankTitleCannotBeConfirmed() {
    showDialog(currentTitle = "Untitled")

    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()

    compose.onNodeWithText("Rename").assertIsNotEnabled()
  }

  private val confirmed = mutableListOf<String>()
  private var dismissed = 0

  private fun showDialog(currentTitle: String) {
    compose.setContent {
      JupyterDroidTheme {
        RenameFileDialog(
          currentTitle = currentTitle,
          onConfirm = { confirmed += it },
          onDismiss = { dismissed++ },
        )
      }
    }
  }
}
