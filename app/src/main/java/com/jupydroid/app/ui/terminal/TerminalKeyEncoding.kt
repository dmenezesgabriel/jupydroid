package com.jupydroid.app.ui.terminal

import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_Z
import androidx.compose.ui.input.key.Key

private const val ESC = "\u001B"
private const val LOWEST_CONTROL_BYTE = 1

/**
 * What a hardware key reaching the terminal means on the wire - 2 (arrows,
 * Ctrl+letter) and P4j-3a (Home/End/PageUp/PageDown/Tab/Escape, the rest of what a soft keyboard
 * cannot produce - `TerminalExtraKeysRow`'s own taps go through this too, so the row sends the
 * exact bytes a hardware key of the same name already does, not a second encoding scheme):
 * an arrow or Home/End moves the cursor the way the shell asked for it (CSI in normal mode, SS3
 * in DECCKM's application mode - [TerminalEmulator.isApplicationCursorKeys], `vttest`'s own
 * distinction), PageUp/PageDown are the VT220 tilde sequences (no application-mode variant),
 * and Ctrl+letter is the matching C0 control byte (Ctrl-C is `0x03`, the SIGINT byte a running
 * process's own line discipline answers to). Read against Termux's own `KeyHandler.java`, not
 * invented: `KEYCODE_MOVE_HOME`/`_END` are `"\033OH"`/`"\033[H"` and `"\033OF"`/`"\033[F"` by the
 * same `cursorApp` branch arrows take, `KEYCODE_PAGE_UP`/`_DOWN` are `"\033[5~"`/`"\033[6~"`,
 * `KEYCODE_TAB` is `"\011"` and `KEYCODE_ESCAPE` is a bare `"\033"`.
 *
 * Takes [key]/[ctrlPressed]/[keyCode] rather than a whole platform `KeyEvent`, so a plain JVM
 * test can drive every branch - `PythonTypingRulesTest`'s own reason applies here too:
 * `android.view.KeyEvent`'s own getters are stubbed to throw outside an instrumented test, and
 * this decision never needs one.
 *
 * @return null for a key [TerminalInputField]'s caller should let `BasicTextField` handle
 *   normally instead (typed characters, its own caret movement).
 */
internal fun terminalKeystroke(key: Key, ctrlPressed: Boolean, keyCode: Int, applicationCursorKeys: Boolean): String? =
  cursorKeySequence(key, applicationCursorKeys)
    ?: pageKeySequence(key)
    ?: literalKeySequence(key)
    ?: controlByteSequence(ctrlPressed, keyCode)

private fun controlByteSequence(ctrlPressed: Boolean, keyCode: Int): String? =
  if (ctrlPressed) controlByte(keyCode)?.toString() else null

/** Arrows and Home/End: one letter, CSI in normal mode or SS3 once the shell sets DECCKM. */
private fun cursorKeySequence(key: Key, applicationCursorKeys: Boolean): String? {
  val letter = when (key) {
    Key.DirectionUp -> 'A'
    Key.DirectionDown -> 'B'
    Key.DirectionRight -> 'C'
    Key.DirectionLeft -> 'D'
    Key.MoveHome -> 'H'
    Key.MoveEnd -> 'F'
    else -> return null
  }
  val introducer = if (applicationCursorKeys) "${ESC}O" else "$ESC["
  return "$introducer$letter"
}

/** The VT220 tilde encoding - the same in both cursor-key modes, unlike arrows and Home/End. */
private fun pageKeySequence(key: Key): String? {
  val code = when (key) {
    Key.PageUp -> "5"
    Key.PageDown -> "6"
    else -> return null
  }
  return "$ESC[$code~"
}

private fun literalKeySequence(key: Key): String? = when (key) {
  Key.Tab -> "\t"
  Key.Escape -> ESC
  else -> null
}

/**
 * `Ctrl+A` through `Ctrl+Z` map onto bytes 1-26 on the wire - `A` is 1, so `C` (SIGINT) is 3.
 * `KEYCODE_A`..`KEYCODE_Z` is Android's own contiguous range, so the offset into it is the byte.
 */
private fun controlByte(keyCode: Int): Char? {
  if (keyCode !in KEYCODE_A..KEYCODE_Z) return null
  return controlByteFromOffset(keyCode - KEYCODE_A)
}

/**
 * The same offset arithmetic as the `keyCode`-keyed overload above, for a character coming out of
 * the soft-keyboard text diff rather than a hardware `KeyEvent` - [applyModifierLatches]'s own
 * reason. Termux's own `codePoint - 'a' + 1` (`TerminalView.inputCodePoint`).
 */
private fun controlByte(letter: Char): Char? {
  val lower = letter.lowercaseChar()
  if (lower !in 'a'..'z') return null
  return controlByteFromOffset(lower - 'a')
}

private fun controlByteFromOffset(offset: Int): Char = (LOWEST_CONTROL_BYTE + offset).toChar()

/**
 * [text], one character at a time, through whichever of [latches]' `ctrl`/`alt` is active first -
 * Termux's own `readControlKey()`/`readAltKey()` are consulted per `inputCodePoint`, not once per
 * string, so a latch that auto-clears after the character that used it correctly modifies only
 * the first character of a multi-character diff (a paste, an IME's own multi-char commit) and
 * leaves the rest literal - [TerminalModifierLatch.consume]'s own one-shot-unless-locked rule. A
 * non-letter under an active Ctrl latch is sent literal (there is no control byte for it) but the
 * latch is still consumed - Termux's own read happens regardless of whether a mapping exists.
 *
 * Ctrl takes priority over Alt when both are somehow active; combining them ("Ctrl+Alt+letter")
 * is not attempted - 3a gate needs only one latch armed at a time.
 *
 * @return the bytes to actually send, and the latch pair afterward.
 */
internal fun applyModifierLatches(
  text: String,
  latches: TerminalModifierLatches,
): Pair<String, TerminalModifierLatches> {
  var current = latches
  val encoded = buildString {
    for (char in text) {
      val (fragment, next) = encodeCharacter(char, current)
      append(fragment)
      current = next
    }
  }
  return encoded to current
}

/** One character of [applyModifierLatches]' own loop - split out so neither exceeds detekt's limits. */
private fun encodeCharacter(char: Char, latches: TerminalModifierLatches): Pair<String, TerminalModifierLatches> {
  val (ctrlWasActive, nextCtrl) = latches.ctrl.consume()
  // Alt is not consulted at all when Ctrl already fired this character - untouched, not just unused.
  val (altWasActive, nextAlt) = if (ctrlWasActive) false to latches.alt else latches.alt.consume()
  val fragment = when {
    ctrlWasActive -> (controlByte(char) ?: char).toString()
    altWasActive -> "$ESC$char"
    else -> char.toString()
  }
  return fragment to latches.copy(ctrl = nextCtrl, alt = nextAlt)
}
