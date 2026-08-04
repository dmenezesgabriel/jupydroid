package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SpaceAbiTest {
  @Test
  fun of_mapsAndroidAbiNamesToDebianLayout() {
    assertEquals(SpaceAbi.X86_64, SpaceAbi.of("x86_64"))
    assertEquals(SpaceAbi.ARM64, SpaceAbi.of("arm64-v8a"))
  }

  /** Debian puts the loader under lib64/ on x86_64 but lib/ on arm64, with different names. */
  @Test
  fun loaderLinkPath_followsDebianPerArchitecture() {
    assertEquals("lib64/ld-linux-x86-64.so.2", SpaceAbi.X86_64.loaderLinkPath)
    assertEquals("lib/ld-linux-aarch64.so.1", SpaceAbi.ARM64.loaderLinkPath)
  }

  @Test
  fun triplet_followsDebianMultiarch() {
    assertEquals("x86_64-linux-gnu", SpaceAbi.X86_64.triplet)
    assertEquals("aarch64-linux-gnu", SpaceAbi.ARM64.triplet)
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
