package com.jupydroid.app.terminal

/** What a code point becomes when the bytes that were meant to carry it are not valid UTF-8. */
const val UNICODE_REPLACEMENT_CHAR: Int = 0xFFFD

/** What one byte fed to a [Utf8Decoder] produced. */
sealed interface Utf8Result {
  /** The byte was taken and there is nothing to write yet - a sequence is still being built. */
  data object Consumed : Utf8Result

  /** A whole code point arrived. */
  data class Decoded(val codePoint: Int) : Utf8Result

  /**
   * The partial sequence in progress was ill-formed. Write [UNICODE_REPLACEMENT_CHAR] for the
   * bytes already taken, then feed [byte] again - it starts a sequence of its own.
   */
  data class Abandoned(val byte: Byte) : Utf8Result
}

/**
 * The byte-to-code-point half of `TerminalEmulator.processByte`
 * (`terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java`, termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's `LICENSE.md`
 * exception).
 *
 * **Pure and push-driven, where the reference is neither.** Upstream this is a method on the
 * emulator that calls back into it, so nothing can test it without a screen; here it is a value
 * returned to the caller. Push matters for this app specifically: bytes arrive in WebSocket frames
 * and a multi-byte character straddles a frame boundary whenever the shell writes one near an edge,
 * so a decoder that could block on a stream - as JediTerm's `TerminalDataStream` does - would not
 * work at all.
 *
 * ```
 * val decoder = Utf8Decoder()
 * decoder.decode(0xE6.toByte())   // Consumed
 * decoder.decode(0x9E.toByte())   // Consumed
 * decoder.decode(0x9D.toByte())   // Decoded(0x679D), the code point for 枝
 * ```
 */
class Utf8Decoder {
  private val partial = ByteArray(MAX_SEQUENCE_BYTES)
  private var bytesTaken = 0
  private var bytesToFollow = 0

  /** Feed one byte of the shell's output. */
  fun decode(byte: Byte): Utf8Result = if (bytesToFollow > 0) continueSequence(byte) else startSequence(byte)

  /** Forget any half-built sequence, as a terminal reset does. */
  fun reset() {
    bytesTaken = 0
    bytesToFollow = 0
  }

  private fun startSequence(byte: Byte): Utf8Result {
    val value = byte.toInt()
    bytesToFollow = continuationsExpectedBy(value)
    if (bytesToFollow == 0) return startedNothing(value)
    partial[0] = byte
    bytesTaken = 1
    return Utf8Result.Consumed
  }

  /** How many continuation bytes this lead byte promises; 0 when it leads no sequence at all. */
  private fun continuationsExpectedBy(value: Int): Int = when {
    value and HIGH_BIT == 0 -> 0
    value and TWO_BYTE_MASK == TWO_BYTE_LEAD -> 1
    value and THREE_BYTE_MASK == THREE_BYTE_LEAD -> 2
    value and FOUR_BYTE_MASK == FOUR_BYTE_LEAD -> MAX_CONTINUATION_BYTES
    else -> 0
  }

  /**
   * A byte that begins no sequence: below 0x80 it *is* the code point, and otherwise it is a
   * stray continuation byte or a five-byte lead, neither of which can start anything valid.
   */
  private fun startedNothing(value: Int): Utf8Result =
    Utf8Result.Decoded(if (value and HIGH_BIT == 0) value else UNICODE_REPLACEMENT_CHAR)

  private fun continueSequence(byte: Byte): Utf8Result {
    // Anything but 10xxxxxx ends the sequence without completing it, and is not consumed by it.
    if (byte.toInt() and CONTINUATION_MASK != CONTINUATION_LEAD) return abandon(byte)
    partial[bytesTaken++] = byte
    return if (--bytesToFollow > 0) Utf8Result.Consumed else finishSequence()
  }

  private fun abandon(byte: Byte): Utf8Result {
    reset()
    return Utf8Result.Abandoned(byte)
  }

  private fun finishSequence(): Utf8Result {
    val codePoint = assemble()
    reset()
    return classify(codePoint)
  }

  /** The payload bits of the lead byte, then six from each continuation byte. */
  private fun assemble(): Int {
    var codePoint = partial[0].toInt() and LEAD_BYTE_MASKS[bytesTaken]
    for (index in 1 until bytesTaken) {
      codePoint = (codePoint shl CONTINUATION_BITS) or (partial[index].toInt() and CONTINUATION_PAYLOAD)
    }
    return if (isOverlong(codePoint)) UNICODE_REPLACEMENT_CHAR else codePoint
  }

  /** A code point spelled in more bytes than it needs, which is not a valid encoding of it. */
  private fun isOverlong(codePoint: Int): Boolean = (bytesTaken > 1 && codePoint <= MAX_ONE_BYTE) ||
    (bytesTaken > 2 && codePoint < MAX_TWO_BYTE) ||
    (bytesTaken == MAX_SEQUENCE_BYTES && codePoint < MAX_THREE_BYTE)

  private fun classify(codePoint: Int): Utf8Result = when {
    // "It is not possible to use a C1 control obtained from decoding the UTF-8 text" - xterm.
    // They are unused today and a binary stream full of them would wreck the terminal's state.
    codePoint in C1_FIRST..C1_LAST -> Utf8Result.Consumed
    Character.getType(codePoint).let { it == UNASSIGNED || it == SURROGATE } ->
      Utf8Result.Decoded(UNICODE_REPLACEMENT_CHAR)
    else -> Utf8Result.Decoded(codePoint)
  }

  private companion object {
    const val MAX_SEQUENCE_BYTES = 4

    /** A four-byte sequence is a lead byte and this many continuations - the longest there is. */
    const val MAX_CONTINUATION_BYTES = 3

    /** Set on every byte of a multi-byte sequence and on no ASCII byte. */
    const val HIGH_BIT = 0b1000_0000

    const val CONTINUATION_MASK = 0b1100_0000
    const val CONTINUATION_LEAD = 0b1000_0000

    /** The six payload bits a continuation byte carries. */
    const val CONTINUATION_PAYLOAD = 0b0011_1111
    const val CONTINUATION_BITS = 6

    const val TWO_BYTE_MASK = 0b1110_0000
    const val TWO_BYTE_LEAD = 0b1100_0000
    const val THREE_BYTE_MASK = 0b1111_0000
    const val THREE_BYTE_LEAD = 0b1110_0000
    const val FOUR_BYTE_MASK = 0b1111_1000
    const val FOUR_BYTE_LEAD = 0b1111_0000

    /** The largest code point each sequence length is allowed to be spelled *shorter* than. */
    const val MAX_ONE_BYTE = 0x7F
    const val MAX_TWO_BYTE = 0x7FF
    const val MAX_THREE_BYTE = 0xFFFF

    const val C1_FIRST = 0x80
    const val C1_LAST = 0x9F

    val UNASSIGNED = Character.UNASSIGNED.toInt()
    val SURROGATE = Character.SURROGATE.toInt()

    /** Indexed by how many bytes the sequence turned out to be; 0 and 1 are never asked for. */
    val LEAD_BYTE_MASKS = intArrayOf(0, 0, 0b0001_1111, 0b0000_1111, 0b0000_0111)
  }
}
