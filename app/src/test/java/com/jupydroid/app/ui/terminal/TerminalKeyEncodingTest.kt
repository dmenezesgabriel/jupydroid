package com.jupydroid.app.ui.terminal

import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_C
import android.view.KeyEvent.KEYCODE_ENTER
import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `docs/PLAN.md`'s P4j-2: what a hardware key means on the wire, decided from plain values
 * (`Key`, a `Boolean`, an `Int` key code) rather than a whole platform `KeyEvent` - its own
 * getters are stubbed to throw outside an instrumented test, and this decision never needs one.
 */
class TerminalKeyEncodingTest {
  @Test
  fun upArrowIsCsiAInNormalMode() {
    val bytes = terminalKeystroke(Key.DirectionUp, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false)

    assertEquals("\u001B[A", bytes)
  }

  @Test
  fun upArrowIsSs3AInApplicationMode() {
    val bytes = terminalKeystroke(Key.DirectionUp, ctrlPressed = false, keyCode = 0, applicationCursorKeys = true)

    assertEquals("\u001BOA", bytes)
  }

  @Test
  fun eachArrowMapsToItsOwnLetter() {
    assertEquals(
      "\u001B[B",
      terminalKeystroke(Key.DirectionDown, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false),
    )
    assertEquals(
      "\u001B[C",
      terminalKeystroke(Key.DirectionRight, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false),
    )
    assertEquals(
      "\u001B[D",
      terminalKeystroke(Key.DirectionLeft, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false),
    )
  }

  @Test
  fun ctrlCIsTheSigintByte() {
    val bytes = terminalKeystroke(Key.C, ctrlPressed = true, keyCode = KEYCODE_C, applicationCursorKeys = false)

    assertEquals("\u0003", bytes)
  }

  @Test
  fun ctrlAIsByteOne() {
    val bytes = terminalKeystroke(Key.A, ctrlPressed = true, keyCode = KEYCODE_A, applicationCursorKeys = false)

    assertEquals("\u0001", bytes)
  }

  @Test
  fun aLetterWithoutCtrlIsNotEncoded() {
    val bytes = terminalKeystroke(Key.C, ctrlPressed = false, keyCode = KEYCODE_C, applicationCursorKeys = false)

    assertNull(bytes)
  }

  @Test
  fun anUnrelatedKeyIsNotEncoded() {
    val bytes =
      terminalKeystroke(Key.Enter, ctrlPressed = false, keyCode = KEYCODE_ENTER, applicationCursorKeys = false)

    assertNull(bytes)
  }
}
