package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SpaceAbiTest {
  @Test
  fun of_mapsAndroidAbiNamesToMuslLayout() {
    assertEquals(SpaceAbi.X86_64, SpaceAbi.of("x86_64"))
    assertEquals(SpaceAbi.ARM64, SpaceAbi.of("arm64-v8a"))
  }

  /** musl names its loader per architecture and keeps it in /lib on both. */
  @Test
  fun loaderLinkPath_followsMuslPerArchitecture() {
    assertEquals("lib/ld-musl-x86_64.so.1", SpaceAbi.X86_64.loaderLinkPath)
    assertEquals("lib/ld-musl-aarch64.so.1", SpaceAbi.ARM64.loaderLinkPath)
  }

  @Test
  fun of_rejectsAbisWeShipNoRootfsFor() {
    try {
      SpaceAbi.of("armeabi-v7a")
      fail("expected armeabi-v7a to be rejected")
    } catch (e: IllegalArgumentException) {
      assertTrue("message must name the ABI, was '${e.message}'", e.message!!.contains("armeabi-v7a"))
    }
  }
}
