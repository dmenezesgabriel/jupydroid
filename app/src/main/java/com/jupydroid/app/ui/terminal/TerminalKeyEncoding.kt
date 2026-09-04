package com.jupydroid.app.ui.terminal

import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_Z
import androidx.compose.ui.input.key.Key

private const val ESC = "\u001B"
private const val LOWEST_CONTROL_BYTE = 1

/**
 * What a hardware key reaching the terminal means on the wire - `docs/PLAN.md`'s P4j-2, the
 * encoder P4j-1's own KDoc deferred: an arrow moves the cursor the way the shell asked for it
 * (CSI in normal mode, SS3 in DECCKM's application mode - [TerminalEmulator.isApplicationCursorKeys],
 * `vttest`'s own distinction), and Ctrl+letter is the matching C0 control byte (Ctrl-C is
 * `0x03`, the SIGINT byte a running process's own line discipline answers to).
 *
 * Takes [key]/[ctrlPressed]/[keyCode] rather than a whole platform `KeyEvent`, so a plain JVM
 * test can drive every branch - `PythonTypingRulesTest`'s own reason applies here too:
 * `android.view.KeyEvent`'s own getters are stubbed to throw outside an instrumented test, and
 * this decision never needs one.
 *
 * @return null for a key [TerminalInputField]'s caller should let `BasicTextField` handle
 *   normally instead (typed characters, its own caret movement).
 */
internal fun terminalKeystroke(key: Key, ctrlPressed: Boolean, keyCode: Int, applicationCursorKeys: Boolean): String? {
  val arrow = arrowSequence(key, applicationCursorKeys)
  if (arrow != null) return arrow
  return if (ctrlPressed) controlByte(keyCode)?.toString() else null
}

private fun arrowSequence(key: Key, applicationCursorKeys: Boolean): String? {
  val letter = when (key) {
    Key.DirectionUp -> 'A'
    Key.DirectionDown -> 'B'
    Key.DirectionRight -> 'C'
    Key.DirectionLeft -> 'D'
    else -> return null
  }
  val introducer = if (applicationCursorKeys) "${ESC}O" else "$ESC["
  return "$introducer$letter"
}

/**
 * `Ctrl+A` through `Ctrl+Z` map onto bytes 1-26 on the wire - `A` is 1, so `C` (SIGINT) is 3.
 * `KEYCODE_A`..`KEYCODE_Z` is Android's own contiguous range, so the offset into it is the byte.
 */
private fun controlByte(keyCode: Int): Char? {
  val offset = keyCode - KEYCODE_A
  if (keyCode !in KEYCODE_A..KEYCODE_Z) return null
  return (LOWEST_CONTROL_BYTE + offset).toChar()
}
