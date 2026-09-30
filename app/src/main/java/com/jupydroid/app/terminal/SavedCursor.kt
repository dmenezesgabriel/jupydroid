package com.jupydroid.app.terminal

/**
 * What DECSC (`ESC 7`) puts away and DECRC (`ESC 8`) puts back.
 *
 * Transcribed from `TerminalEmulator.SavedScreenState` and the `saveCursor` / `restoreCursor` pair
 * (termux-app `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 - see [TerminalGrid]).
 *
 * **A save carries the DECSET flags; a restore puts back exactly two of them** - autowrap and
 * origin mode - because `restoreCursor` masks `mSavedDecFlags` down to that pair. So only those
 * two are kept here: storing the rest would be state nothing ever reads back. The differential is
 * what found them missing, not a reading of the source.
 *
 * ```
 * val saved = SavedCursor(row = 0, column = 3, style = CellStyle.NORMAL, graphicSets = GraphicSetState.ASCII)
 * ```
 */
data class SavedCursor(
  val row: Int,
  val column: Int,
  val style: CellStyle,
  val graphicSets: GraphicSetState,
  val autoWrap: Boolean = true,
  val originMode: Boolean = false,
) {
  companion object {
    /**
     * What a restore finds when nothing has saved. The reference builds its `SavedScreenState` in
     * the constructor and `reset()` fills it with exactly these, so a `DECRC` before any `DECSC`
     * homes the cursor rather than doing nothing.
     */
    val INITIAL = SavedCursor(row = 0, column = 0, style = CellStyle.NORMAL, graphicSets = GraphicSetState.ASCII)
  }
}
