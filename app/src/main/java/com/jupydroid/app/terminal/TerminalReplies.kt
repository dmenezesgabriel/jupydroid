package com.jupydroid.app.terminal

/**
 * What a [TerminalEmulator] writes **back to the shell**, as opposed to what it tells the surface
 * around it.
 *
 * The other half of the split: the reference's `TerminalOutput` is one abstract class carrying
 * both directions, and they are apart here because each binds to something different -
 * [TerminalNotices] to the ViewModel, and this to `TerminalGateway.send`.
 *
 * **A reply is text, not bytes.** Twenty-two of the reference's twenty-three write sites build a
 * `String` of ASCII and hand it over, and the one that passes a `byte[]` builds it from four ASCII
 * literals. Encoding belongs to the transport.
 *
 * ```
 * object Ignored : TerminalReplies { override fun reply(text: String) = Unit }
 * ```
 */
interface TerminalReplies {
  /** Send [text] to the shell as though it had been typed. */
  fun reply(text: String)
}
