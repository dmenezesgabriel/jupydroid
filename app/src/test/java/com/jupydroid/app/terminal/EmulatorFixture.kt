package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/** A [TerminalNotices] that counts what it was told, standing in for the reference's mock. */
internal class RecordingNotices : TerminalNotices {
  var bellsRung: Int = 0
    private set

  /**
   * Every old-to-new pair, in order - `MockTerminalOutput.titleChanges`. **A list rather than the
   * latest**, because that is what proves a pop onto the title already showing reports nothing.
   */
  val titleChanges: MutableList<Pair<String?, String?>> = mutableListOf()

  override fun onBell() {
    bellsRung++
  }

  /** How often the colour table was written to - `MockTerminalOutput.colorsChanged`. */
  var colorsChanged: Int = 0
    private set

  override fun onTitleChanged(oldTitle: String?, newTitle: String?) {
    titleChanges += oldTitle to newTitle
  }

  override fun onColorsChanged() {
    colorsChanged++
  }

  /** Every payload copied, in order - `MockTerminalOutput.clipboardPuts`. */
  val clipboardCopies: MutableList<String> = mutableListOf()

  override fun onClipboardCopy(text: String) {
    clipboardCopies += text
  }
}

/** A [TerminalReplies] that keeps what was written back, standing in for the reference's mock. */
internal class RecordingReplies : TerminalReplies {
  private val written = StringBuilder()

  override fun reply(text: String) {
    written.append(text)
  }

  /** What has been written since the last read, which is how `TerminalTestCase` reads it too. */
  fun takeReply(): String = written.toString().also { written.clear() }
}

/**
 * The emulator under test plus the assertions its reference suites are written in.
 *
 * Transcribed from `TerminalTestCase.java` (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 * A fixture rather than a base class: JUnit 4 needs no inheritance for this, and a test that wants
 * two terminals - which several do, re-sizing between assertions - cannot have one as `this`.
 *
 * ```
 * EmulatorFixture(3, 2).enterString("abc").assertLinesAre("abc", "   ")
 * ```
 */
internal class EmulatorFixture(private val columns: Int, private val rows: Int) {
  val notices = RecordingNotices()
  val replies = RecordingReplies()
  val emulator = TerminalEmulator(columns, rows, notices, replies)
  private val screen get() = emulator.screen

  fun enterString(text: String): EmulatorFixture = append(text.toByteArray(Charsets.UTF_8))

  fun append(bytes: ByteArray): EmulatorFixture {
    emulator.append(bytes, bytes.size)
    assertInvariants()
    return this
  }

  /**
   * `TerminalTestCase.resize`: resize, then re-check the invariants a resize must still hold.
   * [cellWidthPixels]/[cellHeightPixels] are P4i-2b's own addition, both defaulting to "unchanged" -
   * `TerminalEmulator.resize`'s own KDoc says why a plain call must not reset them.
   */
  fun resize(columns: Int, rows: Int, cellWidthPixels: Int? = null, cellHeightPixels: Int? = null): EmulatorFixture {
    emulator.resize(columns, rows, cellWidthPixels, cellHeightPixels)
    assertInvariants()
    return this
  }

  /** `TerminalTestCase.getStyleAt`, for testing only - what a cell is packed with. */
  fun getStyleAt(row: Int, column: Int): CellStyle = screen.grid.styleAt(row, column)

  /** `TerminalTestCase.assertLineWraps`: whether each row, in order from the top, wraps into the next. */
  fun assertLineWraps(vararg lines: Boolean): EmulatorFixture {
    lines.forEachIndexed { row, expected -> assertEquals("line $row", expected, screen.grid.rowAt(row).lineWrap) }
    return this
  }

  /** `TerminalTestCase.assertForegroundIndices`: one palette index expected per cell, row by row. */
  fun assertForegroundIndices(vararg lines: IntArray): EmulatorFixture {
    lines.forEachIndexed { row, expected ->
      expected.forEachIndexed { column, expectedColor ->
        assertEquals("line $row, column $column", expectedColor, getStyleAt(row, column).foreColor)
      }
    }
    return this
  }

  fun assertLinesAre(vararg lines: String): EmulatorFixture {
    assertEquals("screen height", screen.rows, lines.size)
    lines.forEachIndexed { row, expected -> assertLineIs(row, expected) }
    return this
  }

  fun assertLineIs(row: Int, expected: String): EmulatorFixture {
    val line = screen.grid.rowAt(row)
    val actual = String(line.text, 0, line.spaceUsed)
    if (actual !=
      expected
    ) {
      fail("Line $row: expected '$expected' (${expected.length}), was '$actual' (${actual.length})")
    }
    return this
  }

  fun assertLineStartsWith(row: Int, vararg codePoints: Int): EmulatorFixture {
    val chars = screen.grid.rowAt(row).text
    var index = 0
    codePoints.forEachIndexed { position, expected ->
      val first = chars[index++]
      val actual = if (first.isHighSurrogate()) Character.toCodePoint(first, chars[index++]) else first.code
      assertEquals("code point $position of line $row", expected, actual)
    }
    return this
  }

  /**
   * The scrollback, newest first: `assertHistoryStartsWith("333")` is the row that scrolled off
   * most recently. From `TerminalTestCase.assertHistoryStartsWith`.
   */
  fun assertHistoryStartsWith(vararg rows: String): EmulatorFixture {
    val available = screen.grid.activeTranscriptRows
    assertTrue("asked for ${rows.size} history rows, only $available are live", available >= rows.size)
    rows.forEachIndexed { index, expected -> assertLineIs(-index - 1, expected) }
    return this
  }

  /** `TerminalTestCase.assertEnteringStringGivesResponse`: what the shell gets back for one input. */
  fun assertReplyTo(input: String, expected: String): EmulatorFixture {
    enterString(input)
    assertEquals("reply to ${input.replace("\u001B", "ESC")}", expected, replies.takeReply())
    return this
  }

  /** `TerminalTestCase.assertColor`: what the colour table holds at one index. */
  fun assertColor(index: Int, expected: Int): EmulatorFixture {
    val actual = screen.colors.at(index)
    assertEquals("colour $index", Integer.toHexString(expected), Integer.toHexString(actual))
    return this
  }

  fun assertCursorAt(row: Int, column: Int): EmulatorFixture {
    val actual = screen.cursor.row to screen.cursor.column
    assertEquals("cursor", row to column, actual)
    return this
  }

  /**
   * What must hold of the screen after every write, from `TerminalTestCase.assertInvariants`.
   *
   * **Rows are checked through `rowAt` rather than over the ring's slots**, which is where the
   * reference indexes `mLines` directly. `RowRing` keeps its slots private on purpose (P4e-4), and
   * every *live* row - the screen plus the transcript - is reachable by external index, so the
   * "one row object in two places" check still has everything it can meaningfully catch.
   *
   * **The alternate screen keeps no history**, which is the reference's last two assertions in
   * their observable form: upstream compares `mAltBuffer`'s total rows against its screen rows, and
   * what that structure *means* is that nothing ever reaches its transcript. Checked after every
   * write rather than once, and needing no field exposed for a test to read.
   */
  fun assertInvariants(): EmulatorFixture {
    val seen = mutableSetOf<TerminalRow>()
    for (row in -screen.grid.activeTranscriptRows until screen.rows) {
      val line = screen.grid.rowAt(row)
      assertTrue("row $row is also live elsewhere", seen.add(line))
      assertEquals("row $row does not fill the screen width", screen.columns, widthOf(line, row))
    }
    if (screen.buffers.isAlternateLive) {
      assertEquals("the alternate screen kept history", 0, screen.grid.activeTranscriptRows)
    }
    return this
  }

  private fun widthOf(line: TerminalRow, row: Int): Int {
    var width = 0
    var index = 0
    while (index < line.spaceUsed) {
      val codePoint = codePointAt(line, index, row)
      assertFalse(
        "row $row: an unassigned code point reached the screen",
        Character.getType(codePoint) == Character.UNASSIGNED.toInt(),
      )
      val advance = CharacterWidth.of(codePoint)
      assertFalse("row $row starts with a combining character", width == 0 && advance <= 0 && index == 0)
      width += advance
      index += Character.charCount(codePoint)
    }
    return width
  }

  /** The code point at [index], checking on the way that a surrogate is half of a well-formed pair. */
  private fun codePointAt(line: TerminalRow, index: Int, row: Int): Int {
    val first = line.text[index]
    if (!first.isHighSurrogate()) {
      assertFalse("row $row: low surrogate with no high one before it", first.isLowSurrogate())
      return first.code
    }
    assertTrue("row $row: high surrogate not followed by a low one", line.text[index + 1].isLowSurrogate())
    return Character.toCodePoint(first, line.text[index + 1])
  }
}
