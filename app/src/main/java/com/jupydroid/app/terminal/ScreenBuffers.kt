package com.jupydroid.app.terminal

/**
 * The two screen buffers a shell switches between, which of them is live, and the cursor each one
 * saved.
 *
 * Transcribed from `TerminalEmulator`'s `mMainBuffer` / `mAltBuffer` / `mScreen` triple and its
 * `mSavedStateMain` / `mSavedStateAlt` pair (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). `CSI ? 47`/`1047`/`1049` are what move the pointer, and [ModeSwitching] carries
 * the ordering they need.
 *
 * **A class of its own rather than four fields on [TerminalScreen]**, which sits at ten functions
 * against detekt's cap of eleven - the same wall that split [TabStops] out of [TerminalCursor] and
 * [CharacterPlacement] out of the screen. Two grids, two saved cursors and the swap between them
 * are one responsibility.
 *
 * ```
 * val buffers = ScreenBuffers(columns = 80, rows = 24, totalRows = 2000)
 * buffers.makeAlternateLive(true)   // CSI ? 1049 h
 * ```
 */
class ScreenBuffers(columns: Int, rows: Int, totalRows: Int) {
  private val main = TerminalGrid(columns, totalRows, rows)

  /**
   * **Exactly as many rows as the screen**, so it holds no scrollback: `RowRing.scrollDownOneLine`
   * grows the transcript only while `activeTranscriptRows < totalRows - screenRows`, which here is
   * never. What scrolls off the alternate screen is gone, not filed - the reference's own
   * `new TerminalBuffer(columns, rows, rows)`, and what its `assertInvariants` checks.
   */
  private val alternate = TerminalGrid(columns, rows, rows)

  private var savedForMain: SavedCursor = SavedCursor.INITIAL
  private var savedForAlternate: SavedCursor = SavedCursor.INITIAL

  /** The one being written to. [TerminalScreen.grid] is this, and nothing else names either buffer. */
  var live: TerminalGrid = main
    private set

  val isAlternateLive: Boolean get() = live === alternate

  /**
   * What DECSC put away **for the buffer that is live**. A save inside the alternate screen is not
   * what a restore back on the main one finds, which is what makes entering and leaving carry the
   * cursor across: upstream `saveCursor`/`restoreCursor` pick their `SavedScreenState` the same way.
   */
  var savedCursor: SavedCursor
    get() = if (isAlternateLive) savedForAlternate else savedForMain
    set(value) {
      if (isAlternateLive) savedForAlternate = value else savedForMain = value
    }

  /** Make the alternate screen live, or the main one. Says nothing about the cursor - see [ModeSwitching]. */
  fun makeAlternateLive(on: Boolean) {
    live = if (on) alternate else main
  }

  /**
   * **The scrollback is the main screen's, whichever buffer is live.** `CSI 3 J` and RIS both name
   * `mMainBuffer` upstream rather than the current pointer, so a program that clears the scrollback
   * from inside the alternate screen clears the one the user will see when it exits.
   */
  fun clearTranscript() = main.clearTranscript()

  /** RIS, which resets *both* saved cursors and leaves the pointer where it is. */
  fun forgetSavedCursors() {
    savedForMain = SavedCursor.INITIAL
    savedForAlternate = SavedCursor.INITIAL
  }
}
