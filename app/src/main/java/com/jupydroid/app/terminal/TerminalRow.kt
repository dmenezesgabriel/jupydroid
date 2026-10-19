package com.jupydroid.app.terminal

/**
 * One row of a terminal: a fixed number of columns, each holding a code point, any combining
 * characters written over it, and a [CellStyle].
 *
 * Transcribed from `terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java`
 * (`termux-app@3df69d1d`, commit `3df69d1da197dd9bd71a3bafd902dffd720576b4`,
 * Apache 2.0 under that repository's `LICENSE.md` exception).
 *
 * **The reference's `setChar` is one 117-line method and this one is nine**, because the shape it
 * has upstream is past this project's ceiling of 20 lines and complexity 15; [RowText] carries
 * the half of it that is about the char array rather than about the cell. The behaviour is
 * unchanged, and that was measured rather than reviewed: 12,000 scripted operations against the
 * Java, byte-identical state after every one.
 *
 * ```
 * val row = TerminalRow(columns = 80, style = CellStyle.NORMAL)
 * row.setChar(0, '中'.code, CellStyle.NORMAL)   // occupies columns 0 and 1
 * ```
 */
class TerminalRow(private val columns: Int, style: CellStyle) {
  /** Set when output ran past the last column rather than a newline ending the row. */
  var lineWrap: Boolean = false

  /** Set once anything but a single-width BMP character is written; deactivates the fast path. */
  internal var hasNonOneWidthOrSurrogateChars: Boolean = false
    private set

  private val packed = RowText(columns)

  /**
   * This row's styles, read and written directly the way [text] is. **Internal, like [RowText]**:
   * it is storage rather than a cell, and callers outside `terminal/` reach a style through
   * [TerminalGrid.styleAt].
   */
  internal val styles = RowStyles(columns)

  /** This row's `OSC 8` links, internal for [styles]' reason; read through [TerminalGrid.hyperlinkAt]. */
  internal val links = RowLinks(columns)

  init {
    clear(style)
  }

  /** The row's java chars; only the first [spaceUsed] of them are the row. */
  val text: CharArray get() = packed.chars

  /** How many java chars of [text] are the row. */
  val spaceUsed: Int get() = packed.used

  /** Whether every column is a space, which is what lets a scroll skip a row. */
  fun isBlank(): Boolean = packed.isBlank()

  /** The index into [text] where [column] begins. */
  fun findStartOfColumn(column: Int): Int = packed.findStartOfColumn(column)

  /** Every column back to a space carrying [style]. */
  fun clear(style: CellStyle) {
    packed.blank()
    styles.fill(style)
    links.clear()
    hasNonOneWidthOrSurrogateChars = false
  }

  /**
   * Write [codePoint] into [column], repairing whatever it displaces: the far half of a wide
   * character on either side, and the columns a change of width shifts. [link] is the `OSC 8` URI
   * the cell is written under, `null` for none.
   */
  fun setChar(column: Int, codePoint: Int, style: CellStyle, link: String? = null) {
    require(column in 0 until columns) {
      "column out of range: column=$column, codePoint=$codePoint, expected 0 until $columns"
    }
    styles.set(column, style)
    links.set(column, link)
    val width = CharacterWidth.of(codePoint)
    if (writeOnFastPath(column, codePoint, width)) return
    writeCodePoint(repairWideNeighbours(column, width, style), codePoint, width)
  }

  /**
   * Copy `[sourceX1, sourceX2)` of [source] to start at [destinationX] here. Copying from the
   * second half of a wide character copies a space, not half a glyph.
   */
  fun copyInterval(source: TerminalRow, sourceX1: Int, sourceX2: Int, destinationX: Int) {
    hasNonOneWidthOrSurrogateChars = hasNonOneWidthOrSurrogateChars || source.hasNonOneWidthOrSurrogateChars
    // A row copied onto itself would otherwise read back what this loop has already written.
    val sourceChars = if (this === source) source.text.copyOf() else source.text
    val end = source.findStartOfColumn(sourceX2)
    var charIndex = source.findStartOfColumn(sourceX1)
    var column = sourceX1
    var lastAdvance = 0
    var fromSecondHalfOfWide = sourceX1 > 0 && source.packed.startsWideCharacterAt(sourceX1 - 1)
    while (charIndex < end) {
      val codePoint = if (fromSecondHalfOfWide) ' '.code else codePointAt(sourceChars, charIndex)
      fromSecondHalfOfWide = false
      charIndex += charCountAt(sourceChars, charIndex)
      val width = CharacterWidth.of(codePoint)
      if (width > 0) {
        column += lastAdvance
        lastAdvance = width
      }
      setChar(column + destinationX - sourceX1, codePoint, source.styles.at(column), source.links.at(column))
    }
  }

  /**
   * A single-width BMP character written into a row that has never held anything else is one
   * array store. This is the common case by a wide margin, which is why the reference keeps a
   * flag for it.
   */
  private fun writeOnFastPath(column: Int, codePoint: Int, width: Int): Boolean {
    if (hasNonOneWidthOrSurrogateChars) return false
    val isPlain = codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT && width == 1
    if (isPlain) packed.chars[column] = codePoint.toChar() else hasNonOneWidthOrSurrogateChars = true
    return isPlain
  }

  /**
   * Blank the far half of any wide character this write lands on, and return the column actually
   * written to - a combining mark aimed at a wide character's second half belongs to its first.
   */
  private fun repairWideNeighbours(column: Int, width: Int, style: CellStyle): Int {
    val onSecondHalfOfWide = column > 0 && packed.startsWideCharacterAt(column - 1)
    if (width <= 0) return if (onSecondHalfOfWide) column - 1 else column
    if (onSecondHalfOfWide) setChar(column - 1, ' '.code, style)
    if (width == 2 && packed.startsWideCharacterAt(column + 1)) setChar(column + 1, ' '.code, style)
    return column
  }

  /** Splice [codePoint] into the column's chars, then fix up what a change of width moved. */
  private fun writeCodePoint(column: Int, codePoint: Int, width: Int) {
    val isCombining = width <= 0
    val columnStart = packed.findStartOfColumn(column)
    val oldWidth = CharacterWidth.at(packed.chars, columnStart)
    val oldCharsUsed = packed.charsUsedForColumn(column, columnStart, oldWidth)
    if (isCombining && !hasRoomForAnotherCombiningChar(columnStart, oldCharsUsed)) return

    // A combining character is appended to the column's contents so that it modifies them.
    val newCharsUsed = Character.charCount(codePoint) + if (isCombining) oldCharsUsed else 0
    val columnEnd = packed.splice(columnStart, oldCharsUsed, newCharsUsed)
    Character.toChars(codePoint, packed.chars, columnStart + if (isCombining) oldCharsUsed else 0)
    adjustForWidthChange(column, oldWidth, width, columnEnd)
  }

  private fun hasRoomForAnotherCombiningChar(columnStart: Int, oldCharsUsed: Int): Boolean =
    CharacterWidth.zeroWidthCountIn(packed.chars, columnStart, columnStart + oldCharsUsed) <
      MAX_COMBINING_CHARACTERS_PER_COLUMN

  /**
   * A wide character narrowing frees the column it also filled; a narrow one widening swallows the
   * column after it, and there is no column after the last one.
   */
  private fun adjustForWidthChange(column: Int, oldWidth: Int, newWidth: Int, columnEnd: Int) {
    if (oldWidth == 2 && newWidth == 1) {
      packed.insertSpaceAt(columnEnd)
      return
    }
    if (oldWidth != 1 || newWidth != 2) return
    require(column != columns - 1) {
      "cannot put a wide character in the last column: column=$column, expected 0 until ${columns - 1}"
    }
    // repairWideNeighbours has already blanked any wide character here, so this is one column.
    if (column == columns - 2) packed.truncateTo(columnEnd) else packed.dropCodePointAt(columnEnd)
  }

  companion object {
    /**
     * Combining characters beyond this many in one column are dropped.
     *
     * Unicode sets no limit on how many marks may combine, though real ones stay under ten
     * ("Section 3.6 Combination", Unicode 15.0); UAX15-D3's Stream-Safe Text Format caps a
     * sequence at 30, chosen as "a very wide margin". The reference picks 15 as enough for
     * terminal applications while keeping a row's memory bounded and a hostile stream from
     * growing it without end.
     */
    const val MAX_COMBINING_CHARACTERS_PER_COLUMN = 15
  }
}
