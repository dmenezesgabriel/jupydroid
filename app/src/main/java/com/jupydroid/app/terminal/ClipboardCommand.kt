package com.jupydroid.app.terminal

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * `OSC 52`, Manipulate Selection Data: a shell writing to the clipboard.
 *
 * Transcribed from the `52` arm of `TerminalEmulator.doOscSetTextParameters` (termux-app
 * `3df69d1d`, Apache 2.0 - see [TerminalGrid]), with `java.util.Base64` where the reference has
 * `android.util.Base64` - the departure `docs/PLAN.md`'s P4e row named, and the second of the two
 * `terminal/` was written to avoid.
 *
 * **`terminal/` can run the reference's own disabled test.** `OperatingSystemControlTest
 * .disabledTestSetClipboard` never ran upstream, for the reason its own comment states: *"Cannot
 * run this as a unit test since Base64 is a android.util class."* `java.util.Base64` runs anywhere
 * the JVM does, so `OperatingSystemClipboardTest` runs it for real.
 *
 * ```
 * ClipboardCommand(notices).apply("c;SGVsbG8=")   // "Hello" copied to the clipboard
 * ```
 */
class ClipboardCommand(private val notices: TerminalNotices) {
  /**
   * `52 ; Pc ; Pd`. `Pc` is a selection target this terminal does not distinguish and is skipped
   * up to the first `;` - **or the whole parameter, if there is none**, since `indexOf(";") + 1` is
   * `0` when there is no `;` at all. A payload that does not decode is swallowed rather than thrown.
   *
   * **The strict decoder, matching upstream's own `Base64.decode(str, Base64.DEFAULT)`.** A `\n`
   * inside a wrapped payload never reaches here at all - the C0 dispatch in
   * [TerminalEmulator.handleControlCharacter] consumes it as a screen linefeed *before* the string
   * collector sees it, the same guard that keeps a carriage return out of a title. So the text this
   * decodes is already newline-free by the time it arrives, and `java.util.Base64.getDecoder()` is
   * the faithful match - `getMimeDecoder()` would additionally paper over genuinely malformed input
   * the reference rejects.
   */
  fun apply(textParameter: String) {
    val payloadStart = textParameter.indexOf(SELECTION_SEPARATOR) + 1
    val payload = runCatching {
      String(Base64.getDecoder().decode(textParameter.substring(payloadStart)), StandardCharsets.UTF_8)
    }.getOrNull() ?: return
    notices.onClipboardCopy(payload)
  }

  private companion object {
    const val SELECTION_SEPARATOR = ';'
  }
}
