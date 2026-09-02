package com.jupydroid.app.terminal

/**
 * One row's styles: a [CellStyle] per column, packed as the `Long`s [CellStyle] already is.
 *
 * The counterpart of [RowText], and split from [TerminalRow] for the reason that was: one class
 * for the storage and one for what is stored. Upstream both live on `TerminalRow` as bare arrays -
 * `mText` and `mStyle` - and here the char array had already earned a class of its own at P4e-3
 * while the style array had not, because until P4f-5e nothing wrote a style without also writing a
 * character. DECCARA is what does.
 *
 * ```
 * val styles = RowStyles(columns = 80)
 * styles.set(0, CellStyle.NORMAL)
 * ```
 */
internal class RowStyles(columns: Int) {
  private val packed = LongArray(columns)

  fun at(column: Int): CellStyle = CellStyle(packed[column])

  fun set(column: Int, style: CellStyle) {
    packed[column] = style.bits
  }

  /** Every column to one style, which is what clearing a row does. */
  fun fill(style: CellStyle) = packed.fill(style.bits)
}
