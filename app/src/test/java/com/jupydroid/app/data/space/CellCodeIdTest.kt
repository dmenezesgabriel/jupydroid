package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixtures captured from one real kernel's live `debug_info`/`dumpCell` replies
 * (`evidence/p6a-5-cell-code-id/`) - the row's own gate is a parity test against a live reply,
 * not a value invented or copied from the reference's own tests (none exist there with a real
 * numeric hash, see the evidence README).
 */
class CellCodeIdTest {
  private val hashSeed = 3339675911L
  private val tmpFilePrefix = "/data/user/0/com.jupydroid.app/files/spaces/base/tmp/ipykernel_23682/"
  private val tmpFileSuffix = ".py"

  @Test
  fun murmur2_matchesTheLiveKernelForEmptyCode() {
    assertEquals(3990065800L, CellCodeId.murmur2("", hashSeed))
  }

  @Test
  fun murmur2_matchesTheLiveKernelForAsciiCode() {
    assertEquals(3427300630L, CellCodeId.murmur2("x = 1", hashSeed))
  }

  /** `hash.ts`'s `TextEncoder` and Python's `str.encode(..., "utf8")` both count bytes, not code units - pinned. */
  @Test
  fun murmur2_matchesTheLiveKernelForMultiByteCode() {
    assertEquals(1972177680L, CellCodeId.murmur2("café = '☕'\nprint(café)", hashSeed))
  }

  @Test
  fun codeId_reproducesTheLiveSourcePathForEachSample() {
    assertEquals("${tmpFilePrefix}3990065800.py", CellCodeId.codeId("", hashSeed, tmpFilePrefix, tmpFileSuffix))
    assertEquals(
      "${tmpFilePrefix}3427300630.py",
      CellCodeId.codeId("x = 1", hashSeed, tmpFilePrefix, tmpFileSuffix),
    )
    assertEquals(
      "${tmpFilePrefix}1972177680.py",
      CellCodeId.codeId("café = '☕'\nprint(café)", hashSeed, tmpFilePrefix, tmpFileSuffix),
    )
  }
}
