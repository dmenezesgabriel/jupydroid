package com.jupydroid.app.terminal

/**
 * `wcwidth(3)` for Unicode 15: how many columns a code point advances the cursor by.
 *
 * Transcribed from `terminal-emulator/src/main/java/com/termux/terminal/WcWidth.java`
 * (`reference/research_repos/termux-app`, commit `3df69d1da197dd9bd71a3bafd902dffd720576b4`).
 * That module is **Apache 2.0** under `termux-app/LICENSE.md`'s explicit exception to the
 * repository's GPLv3, inherited from Jack Palevich's Android Terminal Emulator.
 *
 * **One departure from `wcwidth(3)` is the reference's and is kept**: C0/C1 control characters
 * return 0 rather than -1. Nothing here ever draws a control character - the parser consumes them
 * - so a caller that had to distinguish "no width" from "not printable" does not exist.
 *
 * ```
 * CharacterWidth.of('中'.code)  // 2
 * CharacterWidth.of(0x0308)     // 0, a combining diaeresis
 * ```
 */
object CharacterWidth {
  /** The columns a code point occupies: 0, 1 or 2. */
  fun of(codePoint: Int): Int = when {
    isZeroWidthFormatOrControl(codePoint) -> 0
    inTable(CharacterWidthRanges.ZERO_WIDTH, codePoint) -> 0
    inTable(CharacterWidthRanges.WIDE_EASTASIAN, codePoint) -> 2
    else -> 1
  }

  /** The columns the code point starting at [index] of [chars] occupies, surrogate pairs included. */
  fun at(chars: CharArray, index: Int): Int {
    val first = chars[index]
    return if (first.isHighSurrogate()) of(Character.toCodePoint(first, chars[index + 1])) else of(first.code)
  }

  /**
   * How many of the code points in `chars[start until end]` are zero width, i.e. how many
   * combining characters a column already carries. Out-of-range starts count nothing rather than
   * throwing, which is what [TerminalRow]'s combining-character cap relies on.
   */
  fun zeroWidthCountIn(chars: CharArray, start: Int, end: Int): Int {
    if (start < 0 || start >= chars.size) return 0
    var count = 0
    var index = start
    while (index < end && index < chars.size) {
      val step = if (chars[index].isHighSurrogate()) 2 else 1
      if (at(chars, index) <= 0) count++
      index += step
    }
    return count
  }

  private const val NUL = 0x0000
  private const val COMBINING_GRAPHEME_JOINER = 0x034F
  private const val LINE_SEPARATOR = 0x2028
  private const val PARAGRAPH_SEPARATOR = 0x2029

  /** U+200B ZERO WIDTH SPACE .. U+200F RIGHT-TO-LEFT MARK. */
  private val ZERO_WIDTH_SPACE_TO_RIGHT_TO_LEFT_MARK = 0x200B..0x200F

  /** U+202A LEFT-TO-RIGHT EMBEDDING .. U+202E RIGHT-TO-LEFT OVERRIDE. */
  private val BIDIRECTIONAL_EMBEDDINGS_AND_OVERRIDES = 0x202A..0x202E

  /** U+2060 WORD JOINER .. U+2063 INVISIBLE SEPARATOR. */
  private val WORD_JOINER_TO_INVISIBLE_SEPARATOR = 0x2060..0x2063

  /** U+0000 .. U+001F, the C0 controls, which end where the printable ASCII range begins. */
  private val C0_CONTROLS = 0x0000..0x001F

  /** U+007F DELETE .. U+009F, i.e. DEL and the C1 controls. */
  private val DELETE_AND_C1_CONTROLS = 0x007F..0x009F

  /**
   * Formatting and control code points that advance the cursor by nothing and are not in the
   * combining table: the line and paragraph separators, the bidirectional embeddings and
   * overrides, the invisible operators, and C0/C1.
   */
  private fun isZeroWidthFormatOrControl(codePoint: Int): Boolean = codePoint == NUL ||
    codePoint == COMBINING_GRAPHEME_JOINER ||
    codePoint == LINE_SEPARATOR ||
    codePoint == PARAGRAPH_SEPARATOR ||
    codePoint in ZERO_WIDTH_SPACE_TO_RIGHT_TO_LEFT_MARK ||
    codePoint in BIDIRECTIONAL_EMBEDDINGS_AND_OVERRIDES ||
    codePoint in WORD_JOINER_TO_INVISIBLE_SEPARATOR ||
    codePoint in C0_CONTROLS ||
    codePoint in DELETE_AND_C1_CONTROLS

  /** Binary search over a table of ascending, disjoint `(low, high)` pairs. */
  private fun inTable(table: IntArray, codePoint: Int): Boolean {
    var low = 0
    var high = table.size / 2 - 1
    while (low <= high) {
      val middle = (low + high) / 2
      when {
        table[middle * 2 + 1] < codePoint -> low = middle + 1
        table[middle * 2] > codePoint -> high = middle - 1
        else -> return true
      }
    }
    return false
  }
}
