package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The symbol row types into whichever cell has the caret.
 *
 * The row lives outside every cell - it sits above the keyboard, one per screen - so it needs a
 * way to reach the field that is currently focused. This is that seam, and keeping it this small
 * is what avoids hoisting `TextFieldState` out of the field it belongs to.
 *
 * **A key on the row must do exactly what the same key on the keyboard does.** That is not free:
 * a programmatic `state.edit { }` bypasses the `InputTransformation`, so typing `(` this way would
 * insert a lone bracket where the keyboard gives `()`. Applying [PythonTypingRules] inside the
 * edit is what keeps the two honest, and it is the same thing `PythonTypingRulesTest` does.
 */
class FocusedCellInputTest {
  private val input = FocusedCellInput()

  @Test
  fun withNoCellFocusedThereIsNothingToTypeInto() {
    assertFalse(input.isAttached)

    input.type(":")
  }

  @Test
  fun typingGoesIntoTheAttachedField() {
    val state = attached("x")

    input.type(":")

    assertEquals("x:", state.text.toString())
  }

  /** The keyboard's `(` gives `()`; the row's must too, or the two disagree about the same key. */
  @Test
  fun aBracketFromTheRowAutoClosesJustAsTheKeyboardsDoes() {
    val state = attached("print")

    input.type("(")

    assertEquals("print()", state.text.toString())
    assertEquals("the caret sits between them", TextRange(6), state.selection)
  }

  @Test
  fun aQuoteFromTheRowAutoClosesToo() {
    val state = attached("name = ")

    input.type("'")

    assertEquals("name = ''", state.text.toString())
  }

  /**
   * Typing a closer where one already sits steps over it rather than doubling it - the same rule
   * the keyboard gets, and the one that makes auto-close bearable.
   */
  @Test
  fun typingACloserOverItsPartnerStepsPastIt() {
    val state = attached("f()", caret = 2)

    input.type(")")

    assertEquals("f()", state.text.toString())
    assertEquals(TextRange(3), state.selection)
  }

  /** Four spaces, which is what the rules use for a fresh indent level. */
  @Test
  fun indentInsertsOneLevel() {
    val state = attached("x = 1")

    input.indent()

    assertEquals("x = 1    ", state.text.toString())
  }

  @Test
  fun theCaretMovesLeftAndRight() {
    val state = attached("abcd", caret = 2)

    input.moveCaret(-1)
    assertEquals(TextRange(1), state.selection)

    input.moveCaret(1)
    assertEquals(TextRange(2), state.selection)
  }

  /** Past either end there is nowhere to go, and a caret outside the text is a crash. */
  @Test
  fun theCaretStopsAtBothEnds() {
    val state = attached("ab", caret = 0)

    input.moveCaret(-1)
    assertEquals(TextRange(0), state.selection)

    input.moveCaret(5)
    assertEquals(TextRange(2), state.selection)
  }

  /**
   * A cell losing focus to another must not leave the row typing into the one it left. Detaching
   * is guarded by identity for the same reason `cellFocusChanged` is: the reports arrive in no
   * guaranteed order, and a stale "I lost focus" would otherwise unhook the field that just
   * gained it.
   */
  @Test
  fun aStaleDetachDoesNotUnhookTheFieldThatJustGainedFocus() {
    val left = TextFieldState("old")
    val gained = attached("new")

    input.detach(left)

    assertTrue(input.isAttached)
    input.type("!")
    assertEquals("new!", gained.text.toString())
  }

  private fun attached(text: String, caret: Int = text.length): TextFieldState =
    TextFieldState(text, TextRange(caret)).also(input::attach)
}
