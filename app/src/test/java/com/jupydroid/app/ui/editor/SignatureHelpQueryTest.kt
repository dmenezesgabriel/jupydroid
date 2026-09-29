package com.jupydroid.app.ui.editor

import com.jupydroid.app.syntax.PythonSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a signature-help ask is worth a round trip - VS Code's
 * `parameterHintsModel` semantics. From hidden, only a trigger char asks - pylsp's measured
 * `triggerCharacters` are `(`, `,`, `=` - and only inside a call still open. While the hints
 * are up, every content change inside the call re-asks.
 *
 * `|` marks the caret; `armed` models the model's `isTriggered` - pending or active alike.
 */
class SignatureHelpQueryTest {
  private val syntax: SyntaxHighlighter = PythonSyntax()

  @Test
  fun aTriggerCharInsideAnOpenCall_asks() {
    assertEquals(SignatureHelpQuery("os.path.join(", 13), queryIn("os.path.join(|", armed = false, typed = '('))
    assertEquals(SignatureHelpQuery("join(path=", 10), queryIn("join(path=|", armed = false, typed = '='))
  }

  /**
   * The gate is the char *typed*, not the char before the caret - `onTypeHint` reads the last
   * char of the inserted text. Typing `,` then a space still asked, because the comma was
   * the trigger; a space alone inside a call never opened anything.
   */
  @Test
  fun theTypedChar_isTheTrigger_notTheCharBeforeTheCaret() {
    assertEquals(SignatureHelpQuery("join(path, ", 11), queryIn("join(path, |", armed = false, typed = ','))
    assertNull(queryIn("join(path, |", armed = false, typed = ' '))
  }

  /**
   * Typing an ordinary char inside a call never opened the hints in VS Code - `triggerChars`
   * is the gate while the widget is hidden, and a plain letter is not one.
   */
  @Test
  fun anOrdinaryCharInsideACall_whileUnarmed_staysQuiet() {
    assertNull(queryIn("join(pa|", armed = false, typed = 'a'))
    assertNull(queryIn("join(path, |", armed = false, typed = null))
  }

  /**
   * While the hints are up, `onModelContentChange` re-asks on every content change and
   * `onCursorChange` on a non-mouse caret move - a null [typed] is a move, not a keystroke.
   */
  @Test
  fun insideACall_whileArmed_reasks() {
    assertEquals(SignatureHelpQuery("join(pa", 7), queryIn("join(pa|", armed = true, typed = 'a'))
    assertEquals(SignatureHelpQuery("join(path, ", 11), queryIn("join(path, |", armed = true, typed = null))
  }

  /** A closed call is nowhere to ask, whatever was typed or is showing. */
  @Test
  fun aClosedCall_staysQuiet() {
    assertNull(queryIn("join(path)|", armed = true, typed = null))
    assertNull(queryIn("answer = 42|", armed = true, typed = '2'))
  }

  /** The trigger char must sit inside a real call - `"("| ` asks nobody. */
  @Test
  fun aTriggerCharInsideALiteral_staysQuiet() {
    assertNull(queryIn("x = '(|'", armed = false, typed = '('))
    assertNull(queryIn("f(x)  # (|", armed = false, typed = '('))
  }

  private fun queryIn(marked: String, armed: Boolean, typed: Char?): SignatureHelpQuery? {
    val cursor = marked.indexOf('|')
    require(cursor >= 0) { "no cursor in \"$marked\"" }
    val source = marked.replace("|", "")
    return signatureHelpQueryAt(source, cursor, syntax.spansOf(source), armed = armed, typedChar = typed)
  }
}
