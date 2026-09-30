package com.jupydroid.app.terminal

/**
 * What a [TerminalEmulator] tells the surface around it, as opposed to what it writes back to the
 * shell.
 *
 * The reference's `TerminalOutput` is one abstract class carrying both directions; they are split
 * here because each binds to something different - these to the ViewModel, the shell's replies to
 * `TerminalGateway.send`.
 *
 * ```
 * object Silent : TerminalNotices {
 *   override fun onBell() = Unit
 *   override fun onTitleChanged(oldTitle: String?, newTitle: String?) = Unit
 *   override fun onColorsChanged() = Unit
 *   override fun onClipboardCopy(text: String) = Unit
 * }
 * ```
 */
interface TerminalNotices {
  /** BEL (`^G`) arrived outside a string sequence. */
  fun onBell()

  /**
   * The window title changed - and **only** when it changed: a title set to what it already was,
   * or popped off the stack onto the same string, reports nothing. Either side may be `null`,
   * which is the title before anything set one.
   */
  fun onTitleChanged(oldTitle: String?, newTitle: String?)

  /**
   * The colour table was written to - **every attempt, including one that parsed nothing.** The
   * reference reports beside its `tryParseColor` rather than inside it and never asks whether the
   * spec was a colour, so `ESC ] 4 ; 5 ; ? BEL` leaves the table alone and still reports.
   */
  fun onColorsChanged()

  /** `OSC 52` decoded a payload and it should be copied to the system clipboard. */
  fun onClipboardCopy(text: String)
}
