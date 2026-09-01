package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The byte-to-code-point half of `TerminalEmulator.processByte`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 *
 * Upstream this is unreachable except through the emulator, so these are written against the
 * behaviours its comments and `UnicodeInputTest` name rather than transcribed from a test class.
 */
class Utf8DecoderTest {
  private val decoder = Utf8Decoder()

  private fun feed(vararg bytes: Int): List<Utf8Result> = bytes.map { decoder.decode(it.toByte()) }

  private fun decoded(vararg bytes: Int): List<Int> =
    feed(*bytes).filterIsInstance<Utf8Result.Decoded>().map { it.codePoint }

  @Test
  fun asciiPassesThroughOneByteAtATime() {
    assertEquals(listOf('a'.code, 'b'.code), decoded('a'.code, 'b'.code))
  }

  @Test
  fun aMultiByteSequenceYieldsNothingUntilItsLastByte() {
    val results = feed(0xC3, 0xB1) // U+00F1 LATIN SMALL LETTER N WITH TILDE
    assertEquals(Utf8Result.Consumed, results[0])
    assertEquals(Utf8Result.Decoded(0x00F1), results[1])
  }

  @Test
  fun threeAndFourByteSequencesDecode() {
    assertEquals(listOf(0x679C), decoded(0xE6, 0x9E, 0x9C)) // 枝
    assertEquals(listOf(0x1F428), decoded(0xF0, 0x9F, 0x90, 0xA8)) // 🐨
  }

  /**
   * A sequence split across two calls is the case this transport actually has: bytes arrive in
   * WebSocket frames and a multi-byte character straddles a frame boundary whenever the shell
   * writes one near an edge. JediTerm covers the `char`-level version of this
   * (`EmulatorTest.testSplitSurrogatePair`); neither reference covers the byte-level one.
   */
  @Test
  fun aSequenceSplitAcrossTwoFeedsStillDecodes() {
    assertEquals(Utf8Result.Consumed, decoder.decode(0xE6.toByte()))
    assertEquals(Utf8Result.Consumed, decoder.decode(0x9E.toByte()))
    assertEquals(Utf8Result.Decoded(0x679C), decoder.decode(0x9C.toByte()))
  }

  @Test
  fun anOverlongEncodingBecomesTheReplacementCharacter() {
    // U+0020 encoded in two bytes rather than one.
    assertEquals(listOf(UNICODE_REPLACEMENT_CHAR), decoded(0xC0, 0xA0))
  }

  @Test
  fun aCodePointDecodingToAC1ControlIsDroppedEntirely() {
    // xterm: "It is not possible to use a C1 control obtained from decoding the UTF-8 text".
    assertEquals(emptyList<Int>(), decoded(0xC2, 0x9F))
  }

  @Test
  fun unassignedAndSurrogateCodePointsBecomeTheReplacementCharacter() {
    assertEquals(listOf(UNICODE_REPLACEMENT_CHAR), decoded(0xF3, 0x82, 0x95, 0x81)) // U+C2541
    assertEquals(listOf(UNICODE_REPLACEMENT_CHAR), decoded(0xED, 0xA0, 0x80)) // U+D800
  }

  @Test
  fun aByteThatCanStartNoSequenceIsTheReplacementCharacter() {
    assertEquals(listOf(UNICODE_REPLACEMENT_CHAR), decoded(0xFF))
  }

  /**
   * "It must not consume the successor bytes as part of the ill-formed subsequence whenever those
   * successor bytes themselves constitute part of a well-formed subsequence" - Unicode 6.2 §3.9.
   * The abandoned sequence is reported separately because the reference writes its replacement
   * character straight to the screen, bypassing the escape state machine.
   */
  @Test
  fun anIllFormedSequenceIsAbandonedAndItsSuccessorByteIsOffered() {
    assertEquals(Utf8Result.Consumed, decoder.decode(0xEF.toByte()))
    assertEquals(Utf8Result.Abandoned('a'.code.toByte()), decoder.decode('a'.code.toByte()))
    assertEquals(Utf8Result.Decoded('a'.code), decoder.decode('a'.code.toByte()))
  }
}
