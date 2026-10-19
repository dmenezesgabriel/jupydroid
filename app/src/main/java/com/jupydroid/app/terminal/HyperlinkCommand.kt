package com.jupydroid.app.terminal

/**
 * `OSC 8`, a hyperlink: `8 ; params ; URI` opens a link that the characters written after it carry,
 * and an empty URI closes it (https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3cb5feda).
 *
 * jediterm's `JediEmulator` OSC `case 8` (jediterm `377b76e6`) is the parse; termux-app has no
 * `OSC 8` at all. **The URI is everything after the params' `;`**, where jediterm splits every `;`
 * and drops what follows a second one - a URI may hold a `;`. The params, `id=` today, only tell a
 * renderer which cells to underline together on hover, which nothing here does, so they are skipped.
 * The link is [TerminalScreen.currentLink], beside [TerminalScreen.currentStyle] and not in it: a
 * graphic rendition reset does not close a link.
 *
 * ```
 * HyperlinkCommand(screen).apply(";https://example.org")   // the link is open
 * HyperlinkCommand(screen).apply(";")                      // and closed
 * ```
 */
class HyperlinkCommand(private val screen: TerminalScreen) {
  /** [textParameter] is what follows `8;`: `params;URI`. Without its `;` it is malformed, and ignored. */
  fun apply(textParameter: String) {
    val separator = textParameter.indexOf(PARAMS_SEPARATOR)
    if (separator < 0) return
    screen.currentLink = textParameter.substring(separator + 1).ifEmpty { null }
  }

  private companion object {
    const val PARAMS_SEPARATOR = ';'
  }
}
