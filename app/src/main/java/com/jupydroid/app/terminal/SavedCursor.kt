package com.jupydroid.app.terminal

/**
 * What DECSC (`ESC 7`) puts away and DECRC (`ESC 8`) puts back.
 *
 * Transcribed from `TerminalEmulator.SavedScreenState` and the `saveCursor` / `restoreCursor` pair
 * (termux-app `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 - see [TerminalGrid]).
 *
 * **The DEC private modes are not here yet, and that is the reference's list minus what cannot be
 * set.** Upstream a save also carries the DECSET flags, of which a restore puts back exactly two -
 * autowrap and origin mode. Neither can be changed until `CSI ? Pm h` exists, which is **P4f-4**;
 * that step adds them here, with the test that can finally tell them apart.
 *
 * ```
 * val saved = SavedCursor(row = 0, column = 3, style = CellStyle.NORMAL, graphicSets = GraphicSetState.ASCII)
 * ```
 */
data class SavedCursor(val row: Int, val column: Int, val style: CellStyle, val graphicSets: GraphicSetState) {
  companion object {
    /**
     * What a restore finds when nothing has saved. The reference builds its `SavedScreenState` in
     * the constructor and `reset()` fills it with exactly these, so a `DECRC` before any `DECSC`
     * homes the cursor rather than doing nothing.
     */
    val INITIAL = SavedCursor(row = 0, column = 0, style = CellStyle.NORMAL, graphicSets = GraphicSetState.ASCII)
  }
}
