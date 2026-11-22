package com.jupydroid.app.ui.editor

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
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The things "Rename symbol…"'s dialog has to get right that only a running screen can show -
 * [RenameFileDialog]'s own list, the widget being VS Code's inline rename input read as a
 * dialog: autofocused field, the current identifier selected so typing replaces it, an
 * unchanged name a no-op, and a name jedi could never hold refused before the ask.
 */
@RunWith(AndroidJUnit4::class)
class RenameSymbolDialogTest {
  @get:Rule val compose = createComposeRule()

  /**
   * [RenameFileDialog]'s own fix applies here: without it the dialog opens with no caret and
   * the first keystroke goes nowhere - audit A1c exactly, which no JVM test can see because
   * `performTextInput` focuses the node itself.
   */
  @Test
  fun openingTheDialogPutsTheCaretInTheField() {
    showDialog(currentName = "total")

    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).assertIsFocused()
  }

  /** The whole identifier is selected, so typing replaces the symbol rather than editing it. */
  @Test
  fun theCurrentNameIsSelectedWhole() {
    showDialog(currentName = "compute_total")

    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).assert(
      SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, 13)),
    )
  }

  /**
   * Confirming without editing must not rename - the reference's own rule, `newName` equal to
   * the old being a close rather than an ask: a rename that changed nothing would still push a
   * document change the user never asked for.
   */
  @Test
  fun confirmingAnUnchangedNameRenamesNothing() {
    showDialog(currentName = "total")

    compose.onNodeWithText("Rename").performClick()

    assertEquals(emptyList<String>(), confirmed)
    assertEquals(1, dismissed)
  }

  /** `my-var` can never be jedi's answer to anything - refused here, not asked anyway. */
  @Test
  fun aNonIdentifierCannotBeConfirmed() {
    showDialog(currentName = "total")

    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).performTextInput("my-var")

    compose.onNodeWithText("Rename").assertIsNotEnabled()
  }

  @Test
  fun aRefusedNameSaysWhichRuleItBroke() {
    showDialog(currentName = "total")

    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).performTextInput("2fa")

    compose.onNodeWithText("must start with a letter", substring = true).assertExists()
  }

  @Test
  fun anEditedNameIsConfirmed() {
    showDialog(currentName = "total")

    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_SYMBOL_FIELD_TAG).performTextInput("summed")
    compose.onNodeWithText("Rename").performClick()

    assertEquals(listOf("summed"), confirmed)
  }

  @Test
  fun cancellingRenamesNothing() {
    showDialog(currentName = "total")

    compose.onNodeWithText("Cancel").performClick()

    assertEquals(emptyList<String>(), confirmed)
    assertEquals(1, dismissed)
  }

  private val confirmed = mutableListOf<String>()
  private var dismissed = 0

  private fun showDialog(currentName: String) {
    compose.setContent {
      JupydroidTheme {
        RenameSymbolDialog(
          currentName = currentName,
          onConfirm = { confirmed += it },
          onDismiss = { dismissed++ },
        )
      }
    }
  }
}
