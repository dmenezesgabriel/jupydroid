package com.jupydroid.app.terminal

/**
 * Where one character goes on the screen, and what it displaces getting there: the pending wrap,
 * insert mode, the column a combining mark belongs to, and how far the cursor then advances.
 *
 * Transcribed from `TerminalEmulator.emitCodePoint` (termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 - see [TerminalGrid]).
 *
 * **Split out of [TerminalScreen] on detekt's `TooManyFunctions` at P4f-3a**, and detekt was
 * pointing at a real seam: these five functions exist only for each other - nothing but [write]
 * calls any of them - while every other operation on a screen is one a control character or an
 * escape sequence asks for by name.
 *
 * ```
 * CharacterPlacement(screen).write('a'.code)
 * ```
 */
class CharacterPlacement(private val screen: TerminalScreen) {
  private val cursor get() = screen.cursor

  /** Draw [codePoint] at the cursor, wrapping, inserting and advancing as the modes require. */
  fun write(codePoint: Int) {
    screen.lastEmittedCodePoint = codePoint
    val drawn = screen.charset.substitute(codePoint)
    val width = CharacterWidth.of(drawn)
    if (!wrapBeforeWriting(width)) return
    makeRoomForInsert(width)
    screen.grid.setChar(columnToWrite(width), cursor.row, drawn, screen.currentStyle)
    if (screen.autoWrap && width > 0) cursor.aboutToAutoWrap = cursor.column == cursor.rightMargin - width
    cursor.column = minOf(cursor.column + width, cursor.rightMargin - 1)
  }

  /**
   * Wrap to the next row if this character cannot fit in the last column. Returns false when the
   * character is to be dropped instead, which is a wide one at the right margin with
   * [TerminalScreen.autoWrap] off - a case the specification leaves open and the reference resolves
   * by ignoring it.
   */
  private fun wrapBeforeWriting(width: Int): Boolean {
    val inLastColumn = cursor.column == cursor.rightMargin - 1
    val wideCannotFit = inLastColumn && width == WIDE
    val pendingWrap = inLastColumn && cursor.aboutToAutoWrap && width == 1
    if (!screen.autoWrap) return !wideCannotFit
    if (wideCannotFit || pendingWrap) wrapToNextRow()
    return true
  }

  /**
   * The row the cursor leaves is marked as continuing into the next one, which is what
   * [TerminalScreen.backspace] reads to step back over a wrap.
   */
  private fun wrapToNextRow() {
    screen.grid.rowAt(cursor.row).lineWrap = true
    cursor.column = cursor.leftMargin
    if (cursor.row + 1 < cursor.bottomMargin) cursor.row++ else screen.scrollUpOneLine()
  }

  /** Under IRM, shift the rest of the row right by the width about to be written over it. */
  private fun makeRoomForInsert(width: Int) {
    if (!screen.insertMode || width <= 0) return
    val destination = cursor.column + width
    if (destination >= cursor.rightMargin) return
    screen.grid.blockCopy(cursor.column, cursor.row, cursor.rightMargin - destination, 1, destination, cursor.row)
  }

  /**
   * A combining mark belongs to the column *before* the cursor, since the cursor has already moved
   * past the character it modifies - unless a wrap is pending, in which case that character is the
   * one the cursor is still sitting on.
   */
  private fun columnToWrite(width: Int): Int {
    val combining = width <= 0 && cursor.column > 0 && !cursor.aboutToAutoWrap
    return maxOf(0, cursor.column - if (combining) 1 else 0)
  }

  private companion object {
    const val WIDE = 2
  }
}
