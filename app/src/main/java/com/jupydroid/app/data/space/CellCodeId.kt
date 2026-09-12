package com.jupydroid.app.data.space

/**
 * The path ipykernel's own `dumpCell` handler will use for a cell's [code], computable without a
 * round trip so [RealKernelDebugger] can tell an already-dumped cell from a new one.
 *
 * `hashSeed`/`tmpFilePrefix`/`tmpFileSuffix` come from the kernel's own `debug_info` reply -
 * runtime data, not a constant: `tmpFilePrefix` embeds the kernel process's own PID
 * (`ipykernel/compiler.py`'s `get_tmp_directory`), confirmed against a live capture
 * (`evidence/p6a-5-cell-code-id/`).
 *
 * ```
 * CellCodeId.codeId("x = 1", hashSeed = 3339675911L, tmpFilePrefix = "/tmp/ipykernel_1/", tmpFileSuffix = ".py")
 * // "/tmp/ipykernel_1/3427300630.py"
 * ```
 */
internal object CellCodeId {
  /** Mirrors JupyterLab's own `DebuggerConfig.getCodeId` (`packages/debugger/src/config.ts:18-31`). */
  fun codeId(code: String, hashSeed: Long, tmpFilePrefix: String, tmpFileSuffix: String): String =
    "$tmpFilePrefix${murmur2(code, hashSeed)}$tmpFileSuffix"

  /**
   * Murmur2 (32-bit, x86 variant), transcribed from the reference's own
   * `packages/debugger/src/hash.ts:30-70` and cross-checked against ipykernel's own Python
   * transcription (`ipykernel/compiler.py`'s `murmur2_x86`) against a live kernel - both must
   * agree with what a real kernel names a dumped cell, not just with each other
   * (`evidence/p6a-5-cell-code-id/`).
   *
   * UTF-8 bytes, not UTF-16 code units - `hash.ts`'s `TextEncoder` and Python's
   * `str.encode(..., "utf8")` both count bytes, so a multi-byte character changes the hash by
   * more than one unit; [CellCodeIdTest] pins this with a café/☕ fixture.
   *
   * Kept in `Long` throughout, masked to 32 bits after every multiply: the largest possible
   * product (`0xFFFFFFFF * 0x5BD1E995`) is ~6.6e18, safely under `Long.MAX_VALUE` - so no term
   * here ever needs `UInt`/`ULong`.
   */
  fun murmur2(str: String, seed: Long): Long {
    val data = str.toByteArray(Charsets.UTF_8)
    val length = data.size
    val remaining = length and TAIL_MASK
    val roundedEnd = length - remaining
    var h = (seed xor length.toLong()) and MASK_32

    var i = 0
    while (i < roundedEnd) {
      h = mixBlock(h, wordAt(data, i))
      i += WORD_BYTES
    }
    h = mixTail(h, data, roundedEnd, remaining)

    h = h xor (h ushr AVALANCHE_SHIFT_1)
    h = (h * M) and MASK_32
    return h xor (h ushr AVALANCHE_SHIFT_2)
  }

  /** Little-endian 32-bit word from four bytes, the block murmur2 mixes one round at a time. */
  private fun wordAt(data: ByteArray, i: Int): Long = data[i].toUnsignedLong() or
    (data[i + 1].toUnsignedLong() shl BYTE_SHIFT_1) or
    (data[i + 2].toUnsignedLong() shl BYTE_SHIFT_2) or
    (data[i + LAST_BYTE_OFFSET].toUnsignedLong() shl BYTE_SHIFT_3)

  private fun mixBlock(h: Long, word: Long): Long {
    var k = (word * M) and MASK_32
    k = k xor (k ushr BLOCK_MIX_SHIFT)
    k = (k * M) and MASK_32
    return ((h * M) and MASK_32) xor k
  }

  /** The 1-3 leftover bytes murmur2 folds in once, after every full 4-byte block is mixed. */
  private fun mixTail(h: Long, data: ByteArray, roundedEnd: Int, remaining: Int): Long {
    if (remaining == NO_TAIL) return h
    var tail = data[roundedEnd].toUnsignedLong()
    if (remaining >= TAIL_TWO_BYTES) tail = tail or (data[roundedEnd + 1].toUnsignedLong() shl BYTE_SHIFT_1)
    if (remaining == TAIL_THREE_BYTES) tail = tail or (data[roundedEnd + 2].toUnsignedLong() shl BYTE_SHIFT_2)
    return ((h xor tail) * M) and MASK_32
  }

  private fun Byte.toUnsignedLong(): Long = toLong() and BYTE_MASK

  private const val M = 0x5BD1E995L
  private const val MASK_32 = 0xFFFFFFFFL
  private const val BYTE_MASK = 0xFFL
  private const val TAIL_MASK = 0x03
  private const val NO_TAIL = 0
  private const val TAIL_TWO_BYTES = 2
  private const val TAIL_THREE_BYTES = 3
  private const val WORD_BYTES = 4
  private const val LAST_BYTE_OFFSET = 3
  private const val BYTE_SHIFT_1 = 8
  private const val BYTE_SHIFT_2 = 16
  private const val BYTE_SHIFT_3 = 24
  private const val BLOCK_MIX_SHIFT = 24
  private const val AVALANCHE_SHIFT_1 = 13
  private const val AVALANCHE_SHIFT_2 = 15
}
