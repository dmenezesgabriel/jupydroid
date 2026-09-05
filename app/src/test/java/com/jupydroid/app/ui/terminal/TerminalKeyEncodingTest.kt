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

  // --- P4j-3a: keys a soft keyboard cannot produce, shared by TerminalExtraKeysRow's own taps ---

  @Test
  fun homeIsCsiHInNormalModeAndSs3HInApplicationMode() {
    assertEquals(
      "\u001B[H",
      terminalKeystroke(Key.MoveHome, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false),
    )
    assertEquals(
      "\u001BOH",
      terminalKeystroke(Key.MoveHome, ctrlPressed = false, keyCode = 0, applicationCursorKeys = true),
    )
  }

  @Test
  fun endIsCsiFInNormalModeAndSs3FInApplicationMode() {
    assertEquals(
      "\u001B[F",
      terminalKeystroke(Key.MoveEnd, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false),
    )
    assertEquals(
      "\u001BOF",
      terminalKeystroke(Key.MoveEnd, ctrlPressed = false, keyCode = 0, applicationCursorKeys = true),
    )
  }

  /** No application-mode variant - the VT220 tilde encoding is the same regardless of DECCKM. */
  @Test
  fun pageUpAndPageDownAreTildeSequences() {
    assertEquals(
      "\u001B[5~",
      terminalKeystroke(Key.PageUp, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false),
    )
    assertEquals(
      "\u001B[6~",
      terminalKeystroke(Key.PageDown, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false),
    )
  }

  @Test
  fun tabIsTheHorizontalTabByte() {
    val bytes = terminalKeystroke(Key.Tab, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false)

    assertEquals("\t", bytes)
  }

  @Test
  fun escapeIsTheBareEscapeByte() {
    val bytes = terminalKeystroke(Key.Escape, ctrlPressed = false, keyCode = 0, applicationCursorKeys = false)

    assertEquals("\u001B", bytes)
  }

  // --- P4j-3a: a tapped Ctrl/Alt latch applied to soft-keyboard text, one character at a time ---

  @Test
  fun aConsumedCtrlLatchConvertsTheFirstCharacterToItsControlByte() {
    val (bytes, next) = applyModifierLatches(
      "cd",
      TerminalModifierLatches(ctrl = TerminalModifierLatch(isActive = true)),
    )

    assertEquals("\u0003d", bytes)
    assertEquals(TerminalModifierLatch(), next.ctrl)
  }

  @Test
  fun aConsumedAltLatchPrefixesTheFirstCharacterWithEscape() {
    val (bytes, next) = applyModifierLatches("f", TerminalModifierLatches(alt = TerminalModifierLatch(isActive = true)))

    assertEquals("\u001Bf", bytes)
    assertEquals(TerminalModifierLatch(), next.alt)
  }

  @Test
  fun aLockedCtrlLatchConvertsEveryCharacter() {
    val (bytes, next) =
      applyModifierLatches(
        "ab",
        TerminalModifierLatches(ctrl = TerminalModifierLatch(isActive = true, isLocked = true)),
      )

    assertEquals("\u0001\u0002", bytes)
    assertEquals(TerminalModifierLatch(isActive = true, isLocked = true), next.ctrl)
  }

  @Test
  fun anInactiveLatchLeavesTextUnchanged() {
    val (bytes, next) = applyModifierLatches("cd", TerminalModifierLatches())

    assertEquals("cd", bytes)
    assertEquals(TerminalModifierLatches(), next)
  }

  /** Termux's own consumption happens at the read, independent of whether a mapping exists. */
  @Test
  fun aConsumedCtrlLatchOnANonLetterLeavesItLiteralButStillConsumesTheLatch() {
    val (bytes, next) = applyModifierLatches(
      "1",
      TerminalModifierLatches(ctrl = TerminalModifierLatch(isActive = true)),
    )

    assertEquals("1", bytes)
    assertEquals(TerminalModifierLatch(), next.ctrl)
  }
}
