package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.port.CompletionMatch
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The completion list anchors on the caret, so its top edge belongs at or below the caret's
 * bottom - the same `CompletionPopupPosition` space the finding's card is read in by
 * `DiagnosticMarkTapTest.theDetailCardsTopClearsTheBottomOfTheLineItDescribes`: `anchorBounds` is
 * the padded box `cellEditorBox` draws, while the caret rect is measured in `TextLayoutResult`
 * coordinates inside that padding. Only the top edge is read: the caret draws no node, so its x
 * has nothing a semantics read can name.
 */
@RunWith(AndroidJUnit4::class)
class CompletionPopupAnchorTest {
  @get:Rule val compose = createComposeRule()

  private var keyboard: SoftwareKeyboardController? = null

  @Test
  fun theCompletionListsTopClearsTheBottomOfItsCaret() {
    setContent()

    compose.onNode(hasSetTextAction()).requestFocus()
    // The ask debounces 250ms of real time (`rememberCellCompletions`' delay runs on the main
    // dispatcher, not the test clock), so the list's own node is what shows it answered.
    compose.waitUntil(POPUP_TIMEOUT_MS) {
      compose.onAllNodesWithContentDescription(COMPLETIONS_DESCRIPTION).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription(COMPLETIONS_DESCRIPTION).assertIsDisplayed()

    // The keyboard that focus brought up takes the window's bottom half, so the list finds room
    // neither below the caret nor above it and the provider's pin-to-top fallback covers the
    // line - a placement failure the anchor has nothing to do with. Hiding it gives the
    // placement its room back; the window manager repositions the popup off the test's clock,
    // so the bounds are polled until the popup lands.
    compose.runOnIdle { keyboard?.hide() }
    compose.waitForIdle()
    compose.waitUntil(POPUP_TIMEOUT_MS) {
      compose.onNodeWithContentDescription(COMPLETIONS_DESCRIPTION).fetchSemanticsNode().boundsOnScreen().top >=
        compose.onNodeWithText(QUERY).fetchSemanticsNode().boundsOnScreen().bottom - 1
    }

    val popup = compose.onNodeWithContentDescription(COMPLETIONS_DESCRIPTION).fetchSemanticsNode().boundsOnScreen()
    val inner = compose.onNodeWithText(QUERY).fetchSemanticsNode().boundsOnScreen()
    val box = compose.onNodeWithTag(FIELD_TAG).fetchSemanticsNode().boundsOnScreen()
    assertPopupAlignedToAnchor(popup = popup, inner = inner, box = box)
  }

  /** The field the notebook's cells wear - `cellEditorBox`, the padded panel [EditorCell] draws. */
  private fun setContent(): TextFieldState {
    val field = TextFieldState(QUERY, TextRange(QUERY.length))
    compose.setContent {
      keyboard = LocalSoftwareKeyboardController.current
      JupyterDroidTheme {
        Box(modifier = Modifier.width(FIELD_WIDTH).testTag(FIELD_TAG)) {
          CodeCellField(
            state = field,
            onSourceEdited = {},
            modifier = Modifier.fillMaxWidth().cellEditorBox(editing = true),
            completionsAt = { _, _ -> CompletionResult(MATCHES, 0, QUERY.length) },
          )
        }
      }
    }
    return field
  }

  private companion object {
    const val QUERY = "pri"
    const val FIELD_TAG = "completion-anchor-field"
    const val COMPLETIONS_DESCRIPTION = "Completions"

    /** The 250ms debounce, the ask and the popup's own window settling, with margin. */
    const val POPUP_TIMEOUT_MS = 5_000L
    val FIELD_WIDTH = 300.dp
    val MATCHES = listOf(CompletionMatch("print", "print"))
  }
}
