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
 * val emulator = TerminalEmulator(columns = 80, rows = 24, notices = Silent)
 * "ls\r\n".toByteArray().let { emulator.append(it, it.size) }
 * ```
 */
class TerminalEmulator(
  columns: Int,
  rows: Int,
  private val notices: TerminalNotices,
  transcriptRows: Int = DEFAULT_TRANSCRIPT_ROWS,
) {
  val screen = TerminalScreen(columns, rows, totalRowsFor(transcriptRows))
  private val decoder = Utf8Decoder()
  private val parser = EscapeParser(screen)

  /** Feed the first [length] bytes of [buffer], typically one read from the pseudoterminal. */
  fun append(buffer: ByteArray, length: Int) {
    for (index in 0 until length) processByte(buffer[index])
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
   */
  private fun processCodePoint(codePoint: Int) {
    if (handleControlCharacter(codePoint)) return
    if (parser.accept(codePoint)) return
    // A control character nothing above claimed is dropped, never drawn.
    if (codePoint >= FIRST_PRINTABLE) screen.writeCodePoint(codePoint)
  }

  /** The C0 controls this state acts on; false means "not a control character of ours". */
  private fun handleControlCharacter(codePoint: Int): Boolean {
    when (codePoint) {
      NUL -> Unit
      CAN, SUB -> abandonSequence()
      BEL -> notices.onBell()
      BS -> screen.backspace()
      HT -> screen.horizontalTab()
      LF, VT, FF -> screen.newLine()
      CR -> screen.carriageReturn()
      SO -> screen.charset.mapToGL(1)
      SI -> screen.charset.mapToGL(0)
      ESC -> parser.start()
      else -> return false
    }
    return true
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
     * Rows of screen plus scrollback, when the caller names none. The reference's default;
     * JupyterLab's terminal defaults to 1000 (`packages/terminal/src/tokens.ts`) and P4h is where
     * this app chooses between them, because P4h owns the scrollback.
     */
    const val DEFAULT_TRANSCRIPT_ROWS = 2000
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
