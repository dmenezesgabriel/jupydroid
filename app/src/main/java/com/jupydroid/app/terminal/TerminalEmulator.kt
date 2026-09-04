package com.jupydroid.app.terminal

/**
 * A shell's bytes become a screen: UTF-8 decoding, the C0 control characters, and the printable
 * code points in between.
 *
 * Transcribed from `TerminalEmulator.append` / `processByte` / `processCodePoint`
 * (`terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java`, termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's `LICENSE.md`
 * exception). **What is here is the reference's ground state** - which is why a control character
 * it does not name is dropped rather than drawn; everything after an `ESC` belongs to
 * [EscapeParser].
 *
 * ```
 * val emulator = TerminalEmulator(columns = 80, rows = 24, notices = Silent, replies = Ignored)
 * "ls\r\n".toByteArray().let { emulator.append(it, it.size) }
 * ```
 */
class TerminalEmulator(
  columns: Int,
  rows: Int,
  private val notices: TerminalNotices,
  replies: TerminalReplies,
  palette: TerminalPalette = TerminalPalette.REFERENCE,
  transcriptRows: Int = DEFAULT_TRANSCRIPT_ROWS,
  cellWidthPixels: Int = DEFAULT_CELL_WIDTH_PIXELS,
  cellHeightPixels: Int = DEFAULT_CELL_HEIGHT_PIXELS,
) {
  val screen = TerminalScreen(columns, rows, totalRowsFor(transcriptRows), TerminalColors(palette, notices))

  /** What the shell has called this terminal, which P4i draws and `CSI 22/23 t` stacks. */
  val title = WindowTitle(notices)
  private val decoder = Utf8Decoder()
  private val reporting = TerminalReporting(screen, replies, title, cellWidthPixels, cellHeightPixels)
  private val pasteCommand = PasteCommand(screen.modes, replies)
  private val parser = EscapeParser(
    screen,
    reporting,
    title,
    ColorCommands(screen.colors, replies),
    ClipboardCommand(notices),
    DeviceControlString(replies),
  )

  /** DECSET 25 (DECTCEM): whether the cursor should be drawn. P4i reads this to decide. */
  val isCursorEnabled: Boolean get() = screen.modes.isSet(DecMode.CURSOR_ENABLED)

  /** Feed the first [length] bytes of [buffer], typically one read from the pseudoterminal. */
  fun append(buffer: ByteArray, length: Int) {
    for (index in 0 until length) processByte(buffer[index])
  }

  /** Text pasted into the terminal as though typed - see [PasteCommand]. */
  fun paste(text: String) = pasteCommand.paste(text)

  /**
   * The public entry point. [cellWidthPixels]/[cellHeightPixels] default to "unchanged" - a plain
   * columns/rows resize (P4h-3's own shape) must not reset what a caller already reported, so
   * both are read together or not at all, mirroring the reference's own
   * `resize(columns, rows, cellWidthPixels, cellHeightPixels)` - it never means "reset one and
   * leave the other," since a caller that has measured a cell always has both.
   */
  fun resize(columns: Int, rows: Int, cellWidthPixels: Int? = null, cellHeightPixels: Int? = null) {
    screen.resize(columns, rows)
    if (cellWidthPixels != null && cellHeightPixels != null) {
      reporting.updateCellPixels(cellWidthPixels, cellHeightPixels)
    }
  }

  /** Back to a freshly opened terminal, keeping what is on screen. */
  fun reset() {
    decoder.reset()
    parser.finishSequence()
    screen.reset()
  }

  /**
   * An abandoned sequence writes its replacement character **straight to the screen** rather than
   * going back through [processCodePoint]: the reference bypasses its own state machine here, and
   * it has to, since a replacement character is not something the shell sent.
   */
  private fun processByte(byte: Byte) {
    when (val result = decoder.decode(byte)) {
      is Utf8Result.Consumed -> Unit
      is Utf8Result.Decoded -> processCodePoint(result.codePoint)
      is Utf8Result.Abandoned -> {
        screen.writeCodePoint(UNICODE_REPLACEMENT_CHAR)
        processByte(result.byte)
      }
    }
  }

  /**
   * The C0 controls are dispatched **before** the escape state is looked at, as the reference
   * dispatches them: a carriage return inside a half-read sequence moves the cursor and the
   * sequence goes on.
   *
   * **Two of them ask the state first, and only two.** A `BEL` ends an `OSC` string where it would
   * otherwise ring, and an `ESC` opens that string's terminator where it would otherwise start a
   * new sequence - which is the reference's `case 7` and `case 27`. **`ESC` also asks a second
   * state**, still at the same `case 27`: inside a `DCS` string it is dropped outright rather than
   * looked ahead, since that reference guard is `mEscapeState == ESC_P` - a state of its own, not a
   * widening of the `OSC` one, per `docs/DECISIONS.md`. Every other control keeps acting on the
   * screen while a string collects, measured rather than assumed: a carriage return inside an `OSC`
   * returns the carriage and is not collected into the title.
   *
   * **One state asks first and takes everything, not just two controls.** Inside
   * [EscapeParser.isApplicationProgramCommand] the reference's own early return runs *before* this
   * method's switch at all - "the APC string might be arbitrary non-printable characters, so
   * handle that early" - so not even `CR` or `CAN` act while an APC string is open; every byte is
   * discarded into it.
   */
  private fun processCodePoint(codePoint: Int) {
    if (parser.isApplicationProgramCommand) {
      parser.accept(codePoint)
      return
    }
    if (handleControlCharacter(codePoint) || parser.accept(codePoint)) return
    // A control character nothing above claimed is dropped, never drawn.
    if (codePoint >= FIRST_PRINTABLE) screen.writeCodePoint(codePoint)
  }

  /** The C0 controls this state acts on; false means "not a control character of ours". */
  private fun handleControlCharacter(codePoint: Int): Boolean {
    when (codePoint) {
      NUL -> Unit
      CAN, SUB -> abandonSequence()
      BEL -> if (parser.isCollectingString) parser.accept(BEL) else notices.onBell()
      BS -> screen.backspace()
      HT -> screen.horizontalTab()
      LF, VT, FF -> screen.newLine()
      CR -> screen.carriageReturn()
      SO -> screen.charset.mapToGL(1)
      SI -> screen.charset.mapToGL(0)
      ESC -> onEscape()
      else -> return false
    }
    return true
  }

  /**
   * `case 27`. Inside an `OSC` string this is the first half of its terminator; inside a `DCS`
   * string it is dropped outright, per [EscapeParser.isDeviceControlString]'s own KDoc; otherwise
   * it starts a new sequence. Split out of [handleControlCharacter] to keep that `when` under
   * detekt's cyclomatic-complexity cap - the branch count did not change, only where it is counted.
   */
  private fun onEscape() {
    when {
      parser.isCollectingString -> parser.accept(ESC)
      parser.isDeviceControlString -> Unit
      else -> parser.start()
    }
  }

  /**
   * CAN and SUB abandon a sequence where it stands and leave a `DEL` on the screen - which the
   * reference writes straight through, bypassing its own state machine, since a `DEL` nobody sent
   * is not something to re-parse. Outside a sequence both do nothing at all.
   */
  private fun abandonSequence() {
    if (!parser.isParsing) return
    parser.finishSequence()
    screen.writeCodePoint(DELETE)
  }

  companion object {
    /**
     * Rows of screen plus scrollback, when the caller names none. JupyterLab's own default -
     * `packages/terminal/src/tokens.ts:107,160`, `scrollback: 1000` - taken over the reference's
     * 2000, since P4h-3 is where this app chose between them: JupyterLab is authoritative for
     * *behaviour*, and scrollback depth is behaviour, not presentation.
     */
    const val DEFAULT_TRANSCRIPT_ROWS = 1000

    /**
     * What `CSI 14 t` and `CSI 16 t` report a cell as when nobody has measured one. The
     * reference takes these as constructor arguments too, and its own tests pass 13 by 15;
     * **P4i is what measures a real cell** and passes it here.
     */
    const val DEFAULT_CELL_WIDTH_PIXELS = 13
    const val DEFAULT_CELL_HEIGHT_PIXELS = 15

    const val MIN_TRANSCRIPT_ROWS = 100
    const val MAX_TRANSCRIPT_ROWS = 50_000

    private const val NUL = 0
    private const val BEL = 7
    private const val BS = 8
    private const val HT = 9
    private const val LF = 10
    private const val VT = 11
    private const val FF = 12
    private const val CR = 13
    private const val SO = 14
    private const val SI = 15
    private const val CAN = 24
    private const val ESC = 27
    private const val SUB = 26

    /** What CAN and SUB leave behind when they abandon a sequence. */
    private const val DELETE = 127

    /** Space. Everything below it is a control character. */
    private const val FIRST_PRINTABLE = 32

    private fun totalRowsFor(transcriptRows: Int): Int =
      if (transcriptRows in MIN_TRANSCRIPT_ROWS..MAX_TRANSCRIPT_ROWS) transcriptRows else DEFAULT_TRANSCRIPT_ROWS
  }
}
