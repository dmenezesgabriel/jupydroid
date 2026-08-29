package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transcribed from `terminal-emulator/src/test/java/com/termux/terminal/TextStyleTest.java`
 * (termux-app `3df69d1d`, Apache 2.0 - see [CellStyle]), plus the one asymmetry upstream's own
 * tests walk past.
 */
class CellStyleTest {
  private val allEffects = intArrayOf(
    0,
    CellStyle.BOLD,
    CellStyle.ITALIC,
    CellStyle.UNDERLINE,
    CellStyle.BLINK,
    CellStyle.INVERSE,
    CellStyle.INVISIBLE,
    CellStyle.STRIKETHROUGH,
    CellStyle.PROTECTED,
    CellStyle.DIM,
  )

  @Test
  fun everyIndexedColourPairAndEffectRoundTrips() {
    for (effect in allEffects) {
      for (foreground in 0 until CellStyle.INDEXED_COLOR_COUNT) {
        for (background in 0 until CellStyle.INDEXED_COLOR_COUNT) {
          val encoded = CellStyle.encode(foreground, background, effect)
          assertEquals(foreground, encoded.foreColor)
          assertEquals(background, encoded.backColor)
          assertEquals(effect, encoded.effect)
        }
      }
    }
  }

  @Test
  fun twentyFourBitColoursRoundTripOnBothSides() {
    val channels = intArrayOf(255, 240, 127, 1, 0)
    for (red in channels) {
      for (green in channels) {
        for (blue in channels) {
          val argb = OPAQUE or (red shl 16) or (green shl 8) or blue
          assertEquals(argb, CellStyle.encode(argb, 0, 0).foreColor)
          assertEquals(argb, CellStyle.encode(0, argb, 0).backColor)
        }
      }
    }
  }

  @Test
  fun everyCombinationOfTwoEffectsRoundTrips() {
    for (first in allEffects) {
      for (second in allEffects) {
        val combined = first or second
        assertEquals(combined, CellStyle.encode(0, 0, combined).effect)
      }
    }
  }

  @Test
  fun strikethroughSurvivesEncoding() {
    val encoded = CellStyle.encode(
      CellStyle.COLOR_INDEX_FOREGROUND,
      CellStyle.COLOR_INDEX_BACKGROUND,
      CellStyle.STRIKETHROUGH,
    )
    assertNotEquals(0, encoded.effect and CellStyle.STRIKETHROUGH)
  }

  @Test
  fun protectedIsSetOnlyWhenItWasEncoded() {
    val strikethroughOnly =
      CellStyle.encode(CellStyle.COLOR_INDEX_FOREGROUND, CellStyle.COLOR_INDEX_BACKGROUND, CellStyle.STRIKETHROUGH)
    assertEquals(0, strikethroughOnly.effect and CellStyle.PROTECTED)

    val both = CellStyle.encode(
      CellStyle.COLOR_INDEX_FOREGROUND,
      CellStyle.COLOR_INDEX_BACKGROUND,
      CellStyle.STRIKETHROUGH or CellStyle.PROTECTED,
    )
    assertNotEquals(0, both.effect and CellStyle.PROTECTED)
  }

  /**
   * Upstream `encode` masks the effect to nine bits and `decodeEffect` reads eleven, so the two
   * truecolor markers at bits 9 and 10 decode as effect. `testEncodingSingle` never sees it,
   * because indexed colours set neither marker, and `testEncoding24Bit` - the only test that sets
   * them - never asks for the effect back. Nothing here may read a colour marker as an attribute.
   */
  @Test
  fun aTruecolorStyleHasNoEffectBitsItWasNotGiven() {
    assertEquals(0, CellStyle.encode(OPAQUE or 0x808080, 0, 0).effect)
    assertEquals(0, CellStyle.encode(0, OPAQUE or 0x808080, 0).effect)
    assertEquals(
      CellStyle.BOLD,
      CellStyle.encode(OPAQUE or 0x808080, OPAQUE or 0x102030, CellStyle.BOLD).effect,
    )
  }

  /** The default a blank cell is cleared to: the terminal's own foreground on its own background. */
  @Test
  fun theNormalStyleIsTheTwoSpecialColourIndicesAndNoEffect() {
    assertEquals(CellStyle.COLOR_INDEX_FOREGROUND, CellStyle.NORMAL.foreColor)
    assertEquals(CellStyle.COLOR_INDEX_BACKGROUND, CellStyle.NORMAL.backColor)
    assertEquals(0, CellStyle.NORMAL.effect)
  }

  /**
   * `ScreenBufferTest` passes a literal `0` where a style is wanted, and so will `TerminalGrid`'s
   * callers - so what the zero long decodes to is a contract rather than an accident of layout.
   */
  @Test
  fun theZeroLongIsPaletteIndexZeroOnBothSidesWithNoEffect() {
    val zero = CellStyle(0L)
    assertEquals(0, zero.foreColor)
    assertEquals(0, zero.backColor)
    assertEquals(0, zero.effect)
  }

  /** The nine attribute bits are distinct and none of them reaches the colour fields. */
  @Test
  fun theAttributeBitsAreDistinctAndBelowTheColourFields() {
    val seen = mutableSetOf<Int>()
    for (effect in allEffects.filter { it != 0 }) {
      assertTrue("attribute $effect is a single bit", effect.countOneBits() == 1)
      assertTrue("attribute $effect is used twice", seen.add(effect))
      assertTrue("attribute $effect overlaps the colour fields", effect < 1 shl 9)
    }
  }

  private companion object {
    /** Opaque alpha: what marks a colour as 24-bit rather than an index into the palette. */
    const val OPAQUE = 0xFF shl 24
  }
}
