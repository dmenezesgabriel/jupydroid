package com.jupydroid.app.terminal

/**
 * The packed text of one terminal row, and the map between its columns and its java chars.
 *
 * Split out of [TerminalRow] because the two answer different questions - *where does column N
 * live in this array, and how do I make room there* is not *what code point and style is column N*
 * - and because the reference's `TerminalRow.setChar` had to be decomposed to be readable at all
 * (`terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java`, termux-app `3df69d1d`,
 * Apache 2.0). The behaviour is the reference's; the seam is this project's.
 *
 * A column is not an index: a surrogate pair costs two java chars, a wide character covers two
 * columns while occupying one, and a combining mark adds chars to the column it modifies.
 */
internal class RowText(private val columns: Int) {
  /**
   * The row's text. Grows past [columns] as surrogate pairs and combining marks are written; only
   * the first [used] chars are the row. Public so a renderer can hand it straight to a canvas,
   * which is why the reference exposes its own.
   */
  var chars: CharArray = CharArray((SPARE_CAPACITY_FACTOR * columns).toInt())
    private set

  /** How many java chars of [chars] are the row. */
  var used: Int = 0
    private set

  /** Every column back to a space. */
  fun blank() {
    chars.fill(' ')
    used = columns
  }

  fun isBlank(): Boolean = (0 until used).all { chars[it] == ' ' }

  /**
   * The index into [chars] where [column] begins. A column landing on the second half of a wide
   * character returns that character's own start.
   */
  fun findStartOfColumn(column: Int): Int {
    if (column == columns) return used
    var currentColumn = 0
    var charIndex = 0
    while (true) {
      val nextCharIndex = charIndex + charCountAt(chars, charIndex)
      currentColumn += CharacterWidth.of(codePointAt(chars, charIndex))
      if (currentColumn >= column) {
        // Past it means a wide character straddles the column, and starts before it.
        return if (currentColumn == column) skipCombiningChars(nextCharIndex) else charIndex
      }
      charIndex = nextCharIndex
    }
  }

  /** Whether a wide character *begins* at [column], i.e. whether `column + 1` is its second half. */
  fun startsWideCharacterAt(column: Int): Boolean {
    var charIndex = 0
    var currentColumn = 0
    while (charIndex < used && currentColumn <= column) {
      val codePoint = codePointAt(chars, charIndex)
      charIndex += charCountAt(chars, charIndex)
      val width = CharacterWidth.of(codePoint)
      if (width > 0) {
        if (currentColumn == column) return width == 2
        currentColumn += width
      }
    }
    return false
  }

  /** How many java chars the column starting at [columnStart] currently occupies. */
  fun charsUsedForColumn(column: Int, columnStart: Int, width: Int): Int {
    if (column + width < columns) return findStartOfColumn(column + width) - columnStart
    return used - columnStart // The last column runs to the end of the row.
  }

  /** Make the column at [columnStart] hold [newCharsUsed] chars, shifting the rest. Returns its end. */
  fun splice(columnStart: Int, oldCharsUsed: Int, newCharsUsed: Int): Int {
    val oldEnd = columnStart + oldCharsUsed
    val newEnd = columnStart + newCharsUsed
    if (newCharsUsed > oldCharsUsed) {
      shiftTailRight(oldEnd, newEnd)
    } else if (newCharsUsed < oldCharsUsed) {
      chars.copyInto(chars, newEnd, oldEnd, used)
    }
    used += newCharsUsed - oldCharsUsed
    return newEnd
  }

  /** Open one char at [index] and put a space in it - what a wide character narrowing leaves behind. */
  fun insertSpaceAt(index: Int) {
    shiftTailRight(index, index + 1)
    chars[index] = ' '
    used++
  }

  /** Drop the one code point at [index] - what a narrow character widening swallows. */
  fun dropCodePointAt(index: Int) {
    val end = index + charCountAt(chars, index)
    chars.copyInto(chars, index, end, used)
    used -= end - index
  }

  /** End the row at [index], which is how a wide character in the second to last column fits. */
  fun truncateTo(index: Int) {
    used = index
  }

  /** Move `[from, used)` to start at [to], growing [chars] by one row's worth if it must. */
  private fun shiftTailRight(from: Int, to: Int) {
    if (used + (to - from) <= chars.size) {
      chars.copyInto(chars, to, from, used)
      return
    }
    val grown = CharArray(chars.size + columns)
    chars.copyInto(grown, 0, 0, from)
    chars.copyInto(grown, to, from, used)
    chars = grown
  }

  private fun skipCombiningChars(from: Int): Int {
    var charIndex = from
    while (charIndex < used && CharacterWidth.at(chars, charIndex) <= 0) {
      charIndex += charCountAt(chars, charIndex)
    }
    return charIndex
  }

  private companion object {
    /** Headroom for the surrogate pairs and combining marks a row of `columns` columns may hold. */
    const val SPARE_CAPACITY_FACTOR = 1.5f
  }
}
