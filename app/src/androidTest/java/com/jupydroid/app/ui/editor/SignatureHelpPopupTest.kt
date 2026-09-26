package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.port.LanguageParameter
import com.jupydroid.app.domain.port.LanguageSignature
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P5e-4's own gate: typing `(` inside a cell asks `textDocument/signatureHelp` at the caret
 * and draws the signature card above the caret - the active parameter lit, `n/N` controls
 * when overloads exist. Driven through the real field so the focus gate, the trigger scan,
 * the debounce and the popup are all under test together.
 */
@RunWith(AndroidJUnit4::class)
class SignatureHelpPopupTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun typingTheOpener_showsTheSignatureCard() {
    val field = setContent()

    focusField(field, "os.path.join")
    compose.onNode(hasSetTextAction()).performTextInput("(")

    compose.mainClock.advanceTimeBy(GRACE_MS)
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsDisplayed()
    compose.onNodeWithText(JOIN_LABEL).assertIsDisplayed()
  }

  /** The ask went where the caret is - the virtual-document position is the notebook's half. */
  @Test
  fun theAsk_goesAtTheCaret() {
    val asks = mutableListOf<Int>()
    val field = setContent { code, offset ->
      asks += offset
      HELP
    }

    focusField(field, "os.path.join")
    compose.onNode(hasSetTextAction()).performTextInput("(")

    // The popup displaying proves the ask ran - it only draws a non-null answer.
    compose.mainClock.advanceTimeBy(GRACE_MS)
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsDisplayed()
    // "os.path.join()" with the caret between the pair is offset 13.
    assertEquals(listOf(13), asks)
  }

  /** While the card is up every change inside the call re-asks - `onModelContentChange`. */
  @Test
  fun typingInsideTheCall_reasks() {
    val asks = mutableListOf<Int>()
    val field = setContent { code, offset ->
      asks += offset
      HELP
    }

    focusField(field, "os.path.join")
    compose.onNode(hasSetTextAction()).performTextInput("(")
    compose.mainClock.advanceTimeBy(GRACE_MS)
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsDisplayed()
    compose.onNode(hasSetTextAction()).performTextInput(",")

    compose.waitUntil(timeoutMillis = 2_000) { asks.size >= 2 }
    assertEquals(listOf(13, 14), asks)
  }

  /**
   * Typing `)` steps over the auto-closed partner and lands the caret outside the call -
   * `insideUnclosedParen` says no, the card is `cancel()`'d the way the model does it.
   */
  @Test
  fun closingTheCall_clearsTheCard() {
    val field = setContent()

    focusField(field, "os.path.join")
    compose.onNode(hasSetTextAction()).performTextInput("(")
    compose.mainClock.advanceTimeBy(GRACE_MS)
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsDisplayed()

    compose.onNode(hasSetTextAction()).performTextInput(")")
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsNotDisplayed()
  }

  /** A server that answers nothing shows nothing - `result: null` is a real answer. */
  @Test
  fun aNullAnswer_showsNothing() {
    val field = setContent { _, _ -> null }

    focusField(field, "os.path.join")
    compose.onNode(hasSetTextAction()).performTextInput("(")

    compose.mainClock.advanceTimeBy(GRACE_MS)
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsNotDisplayed()
  }

  /**
   * `triggerChars` is the gate while the widget is hidden: landing the caret inside a call
   * and typing an ordinary char never asked - only `(`, `,`, `=` open the card.
   */
  @Test
  fun anOrdinaryChar_withNoTrigger_asksNothing() {
    val asks = mutableListOf<Int>()
    val field = setContent { code, offset ->
      asks += offset
      HELP
    }

    focusField(field, "os.path.join(x)", caret = 13)
    compose.onNode(hasSetTextAction()).performTextInput("x")

    compose.mainClock.advanceTimeBy(GRACE_MS)
    assertTrue("an 'x' inside parens is not a trigger char", asks.isEmpty())
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsNotDisplayed()
  }

  /** Two signatures draw the controls; the arrows step between them the way `next()` cycles. */
  @Test
  fun severalSignatures_stepThroughThem() {
    val field = setContent { _, _ -> TWO }

    focusField(field, "os.path.join")
    compose.onNode(hasSetTextAction()).performTextInput("(")

    compose.mainClock.advanceTimeBy(GRACE_MS)
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsDisplayed()
    compose.onNodeWithContentDescription("$SIGNATURE_COUNT 1 of 2").assertIsDisplayed()
    compose.onNodeWithText(FIRST_LABEL).assertIsDisplayed()

    compose.onNodeWithContentDescription(NEXT_SIGNATURE).performClick()
    compose.onNodeWithContentDescription("$SIGNATURE_COUNT 2 of 2").assertIsDisplayed()
    compose.onNodeWithText(SECOND_LABEL).assertIsDisplayed()

    // `parameterHints.cycle` defaults true - next on the last wraps to the first.
    compose.onNodeWithContentDescription(NEXT_SIGNATURE).performClick()
    compose.onNodeWithContentDescription("$SIGNATURE_COUNT 1 of 2").assertIsDisplayed()
  }

  /** The card never takes the field's focus - typing keeps landing in the cell. */
  @Test
  fun theCard_neverStealsTyping() {
    val field = setContent()

    focusField(field, "os.path.join")
    compose.onNode(hasSetTextAction()).performTextInput("(")
    compose.mainClock.advanceTimeBy(GRACE_MS)
    compose.onNodeWithContentDescription(SIGNATURE_HELP).assertIsDisplayed()
    compose.onNode(hasSetTextAction()).performTextInput("a")

    compose.runOnIdle { assertEquals("os.path.join(a)", field.text.toString()) }
  }

  private fun setContent(
    signatureHelpAt: suspend (code: String, cursorOffset: Int) -> LanguageSignatureHelp? = { _, _ -> HELP },
  ): TextFieldState {
    lateinit var field: TextFieldState
    compose.setContent {
      JupyterDroidTheme {
        Box(modifier = Modifier.width(FIELD_WIDTH).testTag(FIELD_TAG)) {
          field = rememberTextFieldState("")
          CodeCellField(
            state = field,
            onSourceEdited = {},
            signatureHelpAt = signatureHelpAt,
          )
        }
      }
    }
    return field
  }

  /** Seed the code and caret, then focus - the pipeline only asks for a focused field. */
  private fun focusField(field: TextFieldState, code: String, caret: Int = code.length) {
    compose.runOnIdle {
      field.edit {
        replace(0, length, code)
        placeCursorBeforeCharAt(caret)
      }
    }
    compose.onNode(hasSetTextAction()).requestFocus()
  }

  private companion object {
    const val JOIN_LABEL = "join(path: LiteralString, /, *paths: LiteralString) -> LiteralString"
    const val FIRST_LABEL = "first(a)"
    const val SECOND_LABEL = "second(b, c)"
    const val FIELD_TAG = "signature-help-field"
    val FIELD_WIDTH = 300.dp

    /** Long enough that an ask would have landed - the debounce is a measured 120ms. */
    const val GRACE_MS = 600L

    val HELP = LanguageSignatureHelp(
      signatures = listOf(
        LanguageSignature(
          label = JOIN_LABEL,
          parameters = listOf(
            LanguageParameter("path"),
            LanguageParameter("paths"),
          ),
        ),
      ),
      activeSignature = 0,
      activeParameter = 0,
    )

    val TWO = LanguageSignatureHelp(
      signatures = listOf(
        LanguageSignature(label = FIRST_LABEL, parameters = listOf(LanguageParameter("a"))),
        LanguageSignature(label = SECOND_LABEL, parameters = listOf(LanguageParameter("b"), LanguageParameter("c"))),
      ),
    )
  }
}
