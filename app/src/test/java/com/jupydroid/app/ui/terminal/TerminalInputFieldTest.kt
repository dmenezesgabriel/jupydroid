package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.text.input.TextFieldState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The half of `TerminalInputField` that does not need a Compose test harness to prove -
 * `PythonTypingRulesTest`'s own reason: a real [TextFieldState] shows what the buffer looks like
 * after an edit without composing anything. `docs/PLAN.md`'s P4j-1.
 */
class TerminalInputFieldTest {
  @Test
  fun anAppendedSuffixIsSentAsTyped() {
    val sent = mutableListOf<String>()
    val state = TextFieldState("ls")

    val next = reportTerminalKeystroke(state, previous = "l", current = "ls", onType = sent::add)

    assertEquals(listOf("s"), sent)
    assertEquals("ls", next)
  }

  @Test
  fun aShortenedPrefixIsSentAsThatManyDeletes() {
    val sent = mutableListOf<String>()
    val state = TextFieldState("l")

    val next = reportTerminalKeystroke(state, previous = "ls", current = "l", onType = sent::add)

    assertEquals(listOf("\u007F"), sent)
    assertEquals("l", next)
  }

  /** [state] itself is cleared, not just the value this returns - the multi-line-by-default rule. */
  @Test
  fun aTrailingNewlineIsSentAndClearsTheField() {
    val sent = mutableListOf<String>()
    val state = TextFieldState("ls\n")

    val next = reportTerminalKeystroke(state, previous = "ls", current = "ls\n", onType = sent::add)

    assertEquals(listOf("\n"), sent)
    assertEquals("", next)
    assertEquals("", state.text.toString())
  }

  @Test
  fun noChangeSendsNothing() {
    val sent = mutableListOf<String>()
    val state = TextFieldState("ls")

    val next = reportTerminalKeystroke(state, previous = "ls", current = "ls", onType = sent::add)

    assertEquals(emptyList<String>(), sent)
    assertEquals("ls", next)
  }

  /**
   * The case the old KDoc said was "not sent at all": an edit that is neither a pure append nor a
   * shortened prefix. Measured on the tablet (`evidence/p4m-terminal-input/edit-pairs.md`) - an IME
   * that rewrites a token mid-line produces exactly this, and the old mapping dropped it silently,
   * so the shell and the field drifted apart. The general form deletes back to the common prefix
   * and retypes the divergent tail, which is what any line-editor-to-pty bridge does.
   */
  @Test
  fun aRewrittenTailIsDeletedBackToTheCommonPrefixAndRetyped() {
    val sent = mutableListOf<String>()
    val state = TextFieldState("ls -a")

    reportTerminalKeystroke(state, previous = "ls -l", current = "ls -a", onType = sent::add)

    assertEquals(listOf("\u007F", "a"), sent)
  }

  @Test
  fun aCharacterInsertedMidLineIsSentByRetypingFromThePrefix() {
    val sent = mutableListOf<String>()
    val state = TextFieldState("echo aXbc")

    reportTerminalKeystroke(state, previous = "echo abc", current = "echo aXbc", onType = sent::add)

    // common prefix "echo a"; delete "bc", retype "Xbc".
    assertEquals(listOf("\u007F\u007F", "Xbc"), sent)
  }

  /** A word autocorrected in place: `teh` -> `the`, a two-DEL rewrite rather than a dropped edit. */
  @Test
  fun aWordCorrectedInPlaceIsRetypedFromTheDivergence() {
    val sent = mutableListOf<String>()
    val state = TextFieldState("the cat")

    reportTerminalKeystroke(state, previous = "teh cat", current = "the cat", onType = sent::add)

    assertEquals(listOf("\u007F".repeat(6), "he cat"), sent)
  }
}
