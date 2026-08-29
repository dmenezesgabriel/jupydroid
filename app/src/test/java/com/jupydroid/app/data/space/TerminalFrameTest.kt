package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.TerminalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `[type, …content]` arrays terminado speaks, both directions.
 *
 * Every case here is read out of the shipped `terminado/websocket.py` rather than the reference's
 * TypeScript alone, because the two do not agree about how many types there are: `terminal.ts:124`
 * declares four and the socket sends a fifth, `setup`, which `default.ts` intercepts before it can
 * become a message.
 */
class TerminalFrameTest {
  /** `self.send_json_message(["setup", {}])` is the first thing `TermSocket.open` writes. */
  @Test
  fun read_turnsTheSetupFrameIntoReadiness() {
    assertEquals(TerminalEvent.Ready, TerminalFrame.read("""["setup", {}]"""))
  }

  @Test
  fun read_turnsStdoutIntoOutput() {
    assertEquals(TerminalEvent.Output("hello\r\n"), TerminalFrame.read("""["stdout", "hello\r\n"]"""))
  }

  /**
   * Escape sequences survive verbatim, which is the whole point of P4e-P4h existing later: this
   * layer must not interpret, strip or normalise a single byte.
   */
  @Test
  fun read_leavesEscapeSequencesExactlyAsTheyArrived() {
    val red = "\u001B[31mred\u001B[0m"

    assertEquals(TerminalEvent.Output(red), TerminalFrame.read("""["stdout", "\u001B[31mred\u001B[0m"]"""))
  }

  /** `on_pty_died` sends `["disconnect", 1]` and then closes; the shell is gone, not the network. */
  @Test
  fun read_turnsDisconnectIntoAnEndWithAReason() {
    val ended = TerminalFrame.read("""["disconnect", 1]""") as TerminalEvent.Ended

    assertEquals("the shell exited", ended.reason)
  }

  /**
   * A type this app does not act on is ignored rather than fatal, and so is a malformed frame.
   *
   * [JupyterFrame]'s rule, for its reason: one bad frame must not take the channel down. Here it is
   * also forward compatibility - `set_size` and `stdin` are defined on this wire and only ever
   * travel the other way, so a server that echoed one back must not stop a shell.
   */
  @Test
  fun read_ignoresWhatItCannotActOn() {
    assertNull(TerminalFrame.read("""["set_size", 24, 80]"""))
    assertNull(TerminalFrame.read("not json at all"))
    assertNull(TerminalFrame.read("""[]"""))
    assertNull(TerminalFrame.read("""{"not":"an array"}"""))
  }

  @Test
  fun stdin_isTheTypedTextInAOneElementPayload() {
    assertEquals("""["stdin","ls\n"]""", TerminalMessage.stdin("ls\n"))
  }

  /**
   * **Rows before columns**, which is the wire's order rather than a preference: terminado reads
   * the frame as `self.size = command[1:3]` and hands it to `resize_to_smallest`.
   */
  @Test
  fun setSize_putsRowsBeforeColumns() {
    assertEquals("""["set_size",24,80]""", TerminalMessage.setSize(rows = 24, cols = 80))
  }
}
