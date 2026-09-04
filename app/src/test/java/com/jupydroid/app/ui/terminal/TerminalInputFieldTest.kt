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
}
