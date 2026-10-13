package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The colour table a shell reads and writes through `OSC`, and the XParseColor forms it names a
 * colour with.
 *
 * `parse` is `TerminalTest.testParseColor` whole (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). The table's own tests are ours: upstream only reaches it through the emulator,
 * and its `TerminalColors` is a **process-global static** where this is a constructor parameter.
 */
class TerminalColorsTest {
  private fun colors() = TerminalColors(TerminalPalette.REFERENCE, RecordingNotices())

  /** `testParseColor`, every case. */
  @Test
  fun everyXParseColorFormIsRead() {
    assertEquals(OPAQUE or 0x0000FA, TerminalColors.parse("#0000FA"))
    assertEquals(OPAQUE or 0x000000, TerminalColors.parse("#000000"))
    assertEquals(OPAQUE or 0x000000, TerminalColors.parse("#000"))
    assertEquals(OPAQUE or 0x000000, TerminalColors.parse("#000000000"))
    assertEquals(OPAQUE or 0x53186F, TerminalColors.parse("#53186f"))

    assertEquals(OPAQUE or 0xFF00FF, TerminalColors.parse("rgb:F/0/F"))
    assertEquals(OPAQUE or 0x0000FA, TerminalColors.parse("rgb:00/00/FA"))
    assertEquals(OPAQUE or 0x53186F, TerminalColors.parse("rgb:53/18/6f"))

    assertEquals(PARSE_FAILED, TerminalColors.parse("invalid_0000FA"))
    assertEquals(PARSE_FAILED, TerminalColors.parse("#3456"))
  }

  /**
   * The shapes `testParseColor` does not reach, all of which the reference answers `0` to - by
   * `NumberFormatException` or `IndexOutOfBoundsException` rather than by a guard, which is why
   * they are worth pinning: a transcription that guards has to guard to the same answer.
   */
  @Test
  fun aSpecThatNamesNoColourIsRefused() {
    assertEquals(PARSE_FAILED, TerminalColors.parse(""))
    assertEquals(PARSE_FAILED, TerminalColors.parse("#"))
    assertEquals(PARSE_FAILED, TerminalColors.parse("rgb:"))
    assertEquals(PARSE_FAILED, TerminalColors.parse("rgb://"))
    assertEquals(PARSE_FAILED, TerminalColors.parse("rgb:AABBCC"))
    assertEquals(PARSE_FAILED, TerminalColors.parse("#zzzzzz"))
    assertEquals(PARSE_FAILED, TerminalColors.parse("red"))
  }

  /** Each component is scaled to eight bits, so one hex digit is multiplied by seventeen. */
  @Test
  fun aComponentIsScaledToEightBitsByItsWidth() {
    assertEquals(OPAQUE or 0xAABBCC, TerminalColors.parse("#AABBCC"))
    assertEquals(OPAQUE or 0xFFFFFF, TerminalColors.parse("#FFF"))
    assertEquals(OPAQUE or 0x111111, TerminalColors.parse("rgb:1/1/1"))
    assertEquals(OPAQUE or 0xABCDEF, TerminalColors.parse("rgb:ABAB/CDCD/EFEF"))
    assertEquals(OPAQUE or 0xABDE01, TerminalColors.parse("rgb:ABC/DEF/012"))
  }

  /** A table starts on its palette, takes a colour, and gives it back on reset. */
  @Test
  fun oneIndexIsSetAndResetOnItsOwn() {
    val colors = colors()
    val wasFive = colors.at(5)
    colors.tryParse(5, "#00FF00")
    assertEquals(OPAQUE or 0x00FF00, colors.at(5))
    assertNotEquals(wasFive, colors.at(5))
    colors.reset(5)
    assertEquals(wasFive, colors.at(5))
  }

  /** A spec that names no colour leaves the entry alone rather than blanking it. */
  @Test
  fun aSpecThatNamesNoColourLeavesTheEntryAlone() {
    val colors = colors()
    colors.tryParse(5, "#00FF00")
    colors.tryParse(5, "not a colour")
    assertEquals(OPAQUE or 0x00FF00, colors.at(5))
  }

  /** Resetting the whole table puts every one of the 259 back, not only the ones that changed. */
  @Test
  fun resettingTheTablePutsEveryIndexBack() {
    val colors = colors()
    val before = IntArray(CellStyle.INDEXED_COLOR_COUNT) { colors.at(it) }
    for (index in 0 until CellStyle.INDEXED_COLOR_COUNT) colors.tryParse(index, "#123456")
    colors.reset()
    for (index in 0 until CellStyle.INDEXED_COLOR_COUNT) {
      assertEquals("index $index", before[index], colors.at(index))
    }
  }

  /**
   * **Every attempted write reports, including one that parsed nothing.** The reference calls
   * `onColorsChanged` beside `tryParseColor` rather than inside it, and does not look at whether it
   * worked - measured: `ESC ] 4 ; 5 ; ? BEL` leaves the table alone and still reports.
   */
  @Test
  fun everyAttemptedWriteReportsEvenWhenItParsedNothing() {
    val notices = RecordingNotices()
    val colors = TerminalColors(TerminalPalette.REFERENCE, notices)
    assertEquals(0, notices.colorsChanged)
    colors.tryParse(5, "#00FF00")
    assertEquals(1, notices.colorsChanged)
    colors.tryParse(5, "?")
    assertEquals(2, notices.colorsChanged)
    colors.reset(5)
    assertEquals(3, notices.colorsChanged)
    colors.reset()
    assertEquals(4, notices.colorsChanged)
  }

  /** A new palette replaces every one of the 259, and the screen hears that it must redraw. */
  @Test
  fun adoptingAnotherPalettePutsEveryIndexAtItsColourAndReports() {
    val notices = RecordingNotices()
    val colors = TerminalColors(TerminalPalette.VS_CODE_LIGHT, notices)
    colors.adopt(TerminalPalette.VS_CODE_DARK)
    for (index in 0 until CellStyle.INDEXED_COLOR_COUNT) {
      assertEquals("index $index", TerminalPalette.VS_CODE_DARK.at(index), colors.at(index))
    }
    assertEquals(1, notices.colorsChanged)
  }

  /**
   * A colour a program set by `OSC` belongs to the palette it was set against, so a new palette
   * drops it - termux-app `3df69d1d` `checkForFontAndColors` resets after `updateWith`, and
   * xterm.js 5.5.0 `ThemeService._setTheme` rebuilds the ANSI table from the theme.
   */
  @Test
  fun adoptingAnotherPaletteDropsAColourAProgramSet() {
    val colors = TerminalColors(TerminalPalette.VS_CODE_LIGHT, RecordingNotices())
    colors.tryParse(5, "#00FF00")
    colors.adopt(TerminalPalette.VS_CODE_DARK)
    assertEquals(TerminalPalette.VS_CODE_DARK.at(5), colors.at(5))
  }

  /** A relaunch that changes no theme hands the same palette back, and must not cost the shell's colours. */
  @Test
  fun adoptingThePaletteInForceKeepsAColourAProgramSetAndReportsNothing() {
    val notices = RecordingNotices()
    val colors = TerminalColors(TerminalPalette.VS_CODE_LIGHT, notices)
    colors.tryParse(5, "#00FF00")
    colors.adopt(TerminalPalette.VS_CODE_LIGHT)
    assertEquals(OPAQUE or 0x00FF00, colors.at(5))
    assertEquals(1, notices.colorsChanged)
  }

  /** `OSC 104;5` after a theme change puts the entry back to the palette now in force. */
  @Test
  fun resettingOneIndexAfterAdoptingReturnsToTheNewPalette() {
    val colors = TerminalColors(TerminalPalette.VS_CODE_LIGHT, RecordingNotices())
    colors.adopt(TerminalPalette.VS_CODE_DARK)
    colors.tryParse(5, "#00FF00")
    colors.reset(5)
    assertEquals(TerminalPalette.VS_CODE_DARK.at(5), colors.at(5))
  }

  /**
   * The 216-cube and the 24 greys are **generated**, so this checks the generator against the
   * reference's literal table at its corners and seams rather than restating 240 numbers - the
   * same call the width ranges take.
   */
  @Test
  fun theCubeAndTheGreysAreGeneratedToTheReferencesOwnTable() {
    val palette = TerminalPalette.REFERENCE
    assertEquals(OPAQUE or 0x000000, palette.at(16))
    assertEquals(OPAQUE or 0x00005F, palette.at(17))
    assertEquals(OPAQUE or 0x0000FF, palette.at(21))
    assertEquals(OPAQUE or 0x005F00, palette.at(22))
    assertEquals(OPAQUE or 0xFFFFFF, palette.at(231))
    assertEquals(OPAQUE or 0x080808, palette.at(232))
    assertEquals(OPAQUE or 0xEEEEEE, palette.at(255))
  }

  /** The sixteen ANSI entries and the three dynamic ones are the reference's, literally. */
  @Test
  fun theSixteenAnsiEntriesAndTheThreeDynamicOnesAreTheReferences() {
    val palette = TerminalPalette.REFERENCE
    assertEquals(OPAQUE or 0x000000, palette.at(0))
    assertEquals(OPAQUE or 0xCD0000, palette.at(1))
    assertEquals(OPAQUE or 0x6495ED, palette.at(4))
    assertEquals(OPAQUE or 0x7F7F7F, palette.at(8))
    assertEquals(OPAQUE or 0x5C5CFF, palette.at(12))
    assertEquals(OPAQUE or 0xFFFFFF, palette.at(15))
    assertEquals(OPAQUE or 0xFFFFFF, palette.at(CellStyle.COLOR_INDEX_FOREGROUND))
    assertEquals(OPAQUE or 0x000000, palette.at(CellStyle.COLOR_INDEX_BACKGROUND))
    assertEquals(OPAQUE or 0xFFFFFF, palette.at(CellStyle.COLOR_INDEX_CURSOR))
  }

  /**
   * VS Code's sixteen `terminal.ansi*` registered defaults, dark theme - none of them overridden
   * by a default theme (`grep -rn 'ansi' extensions/theme-defaults/themes/` is empty across all
   * ten), so the registry's own `dark` field is the resolved value.
   * `terminalColorRegistry.ts:102-247`. Indices 256-258 are [TerminalPalette.REFERENCE]'s
   * placeholders still - see [TerminalPalette.VS_CODE_DARK]'s KDoc.
   */
  @Test
  fun theVsCodeDarkRampMatchesTerminalColorRegistry() {
    val palette = TerminalPalette.VS_CODE_DARK
    assertEquals(OPAQUE or 0x000000, palette.at(0)) // ansiBlack
    assertEquals(OPAQUE or 0xCD3131, palette.at(1)) // ansiRed
    assertEquals(OPAQUE or 0x0DBC79, palette.at(2)) // ansiGreen
    assertEquals(OPAQUE or 0xE5E510, palette.at(3)) // ansiYellow
    assertEquals(OPAQUE or 0x2472C8, palette.at(4)) // ansiBlue
    assertEquals(OPAQUE or 0xBC3FBC, palette.at(5)) // ansiMagenta
    assertEquals(OPAQUE or 0x11A8CD, palette.at(6)) // ansiCyan
    assertEquals(OPAQUE or 0xE5E5E5, palette.at(7)) // ansiWhite
    assertEquals(OPAQUE or 0x666666, palette.at(8)) // ansiBrightBlack
    assertEquals(OPAQUE or 0xF14C4C, palette.at(9)) // ansiBrightRed
    assertEquals(OPAQUE or 0x23D18B, palette.at(10)) // ansiBrightGreen
    assertEquals(OPAQUE or 0xF5F543, palette.at(11)) // ansiBrightYellow
    assertEquals(OPAQUE or 0x3B8EEA, palette.at(12)) // ansiBrightBlue
    assertEquals(OPAQUE or 0xD670D6, palette.at(13)) // ansiBrightMagenta
    assertEquals(OPAQUE or 0x29B8DB, palette.at(14)) // ansiBrightCyan
    assertEquals(OPAQUE or 0xE5E5E5, palette.at(15)) // ansiBrightWhite
  }

  /** VS Code's sixteen `terminal.ansi*` registered defaults, light theme. Same citation. */
  @Test
  fun theVsCodeLightRampMatchesTerminalColorRegistry() {
    val palette = TerminalPalette.VS_CODE_LIGHT
    assertEquals(OPAQUE or 0x000000, palette.at(0)) // ansiBlack
    assertEquals(OPAQUE or 0xCD3131, palette.at(1)) // ansiRed
    assertEquals(OPAQUE or 0x107C10, palette.at(2)) // ansiGreen
    assertEquals(OPAQUE or 0x949800, palette.at(3)) // ansiYellow
    assertEquals(OPAQUE or 0x0451A5, palette.at(4)) // ansiBlue
    assertEquals(OPAQUE or 0xBC05BC, palette.at(5)) // ansiMagenta
    assertEquals(OPAQUE or 0x0598BC, palette.at(6)) // ansiCyan
    assertEquals(OPAQUE or 0x555555, palette.at(7)) // ansiWhite
    assertEquals(OPAQUE or 0x666666, palette.at(8)) // ansiBrightBlack
    assertEquals(OPAQUE or 0xCD3131, palette.at(9)) // ansiBrightRed
    assertEquals(OPAQUE or 0x14CE14, palette.at(10)) // ansiBrightGreen
    assertEquals(OPAQUE or 0xB5BA00, palette.at(11)) // ansiBrightYellow
    assertEquals(OPAQUE or 0x0451A5, palette.at(12)) // ansiBrightBlue
    assertEquals(OPAQUE or 0xBC05BC, palette.at(13)) // ansiBrightMagenta
    assertEquals(OPAQUE or 0x0598BC, palette.at(14)) // ansiBrightCyan
    assertEquals(OPAQUE or 0xA5A5A5, palette.at(15)) // ansiBrightWhite
  }

  /**
   * The cube and the greys are shared arithmetic, not per-palette data - both new palettes must
   * agree with [TerminalPalette.REFERENCE] at the same corners
   * [theCubeAndTheGreysAreGeneratedToTheReferencesOwnTable] already pins, proving the factored
   * generator didn't drift when it grew a second caller.
   */
  @Test
  fun theCubeAndTheGreysAreSharedAcrossEveryPalette() {
    for (palette in listOf(TerminalPalette.VS_CODE_DARK, TerminalPalette.VS_CODE_LIGHT)) {
      assertEquals(OPAQUE or 0x000000, palette.at(16))
      assertEquals(OPAQUE or 0x00005F, palette.at(17))
      assertEquals(OPAQUE or 0xFFFFFF, palette.at(231))
      assertEquals(OPAQUE or 0x080808, palette.at(232))
      assertEquals(OPAQUE or 0xEEEEEE, palette.at(255))
    }
  }

  private companion object {
    const val OPAQUE = 0xFF shl 24

    /** What the reference answers for a spec it cannot read, and what it tests `!= 0` against. */
    const val PARSE_FAILED = 0
  }
}
