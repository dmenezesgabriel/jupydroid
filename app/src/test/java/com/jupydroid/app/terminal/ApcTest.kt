package com.jupydroid.app.terminal

import org.junit.Test

/**
 * `ESC _`, Application Program Command: every byte discarded until a bare `ST` (`ESC \\`).
 *
 * Transcribed from `ApcTest.java`'s one test (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]).
 */
class ApcTest {
  /**
   * `testApcConsumed`. Kitty's graphics-protocol probe draws nothing - if the protocol were
   * implemented it would answer on stdin instead, and if not (as here) it is silently swallowed,
   * matching xterm. The second case embeds the APC's own introducer and a non-terminating `ESC`
   * inside the string: neither restarts a sequence, both are discarded along with everything else.
   */
  @Test
  fun apcConsumed() {
    EmulatorFixture(2, 2)
      .enterString("${ESC}_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA$ESC\\")
      .assertLinesAre("  ", "  ")

    EmulatorFixture(12, 2)
      .enterString("hello ${ESC}_some\u0013${ESC}_\\apc#end$ESC\\ world")
      .assertLinesAre("hello  world", "            ")
  }

  /**
   * Read off `RefScreen`: a control character the reference specially handles - here a carriage
   * return - is discarded along with the rest of an APC body rather than acting on the screen.
   * The reference's own reasoning is why: its early return for `ESC_APC` runs *before* the C0
   * switch that would otherwise return the carriage, not only before `BEL`'s ring and `ESC`'s new
   * sequence - `terminal/EscapeParser.kt`.
   */
  @Test
  fun apcDiscardsAControlCharacterRatherThanActingOnIt() {
    EmulatorFixture(8, 2)
      .enterString("ab${ESC}_cd\rEF$ESC\\gh")
      .assertLinesAre("abgh    ", "        ")
      .assertCursorAt(0, 4)
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
