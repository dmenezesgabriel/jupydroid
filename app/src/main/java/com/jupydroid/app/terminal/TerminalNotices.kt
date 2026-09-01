package com.jupydroid.app.terminal

/**
 * What a [TerminalEmulator] tells the surface around it, as opposed to what it writes back to the
 * shell.
 *
 * The reference's `TerminalOutput` is one abstract class carrying both directions; they are split
 * here because P4i binds them to different things - these to the ViewModel, the shell's replies to
 * `TerminalGateway.send`.
 *
 * ```
 * object Silent : TerminalNotices { override fun onBell() = Unit }
 * ```
 */
interface TerminalNotices {
  /** BEL (`^G`) arrived outside a string sequence. */
  fun onBell()
}
