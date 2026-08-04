package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SpaceAbiTest {
  @Test
  fun of_mapsAndroidAbiNamesToMuslLayout() {
    assertEquals(SpaceAbi.ARM64, SpaceAbi.of("arm64-v8a"))
  }

  /** musl names its loader per architecture and keeps it in /lib. */
  @Test
  fun loaderLinkPath_followsMuslPerArchitecture() {
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

  /**
   * The emulator case, and the reason it must be a clean rejection rather than a crash:
   * Android's x86_64 seccomp filter kills musl startup, so there is no Space to run there
   * however the rootfs is built (evidence/step8.3/musl-is-arm64-only.txt).
   */
  @Test
  fun of_rejectsX86_64BecauseNoSpaceCanRunThere() {
    try {
      SpaceAbi.of("x86_64")
      fail("expected x86_64 to be rejected")
    } catch (e: IllegalArgumentException) {
      assertTrue("message must name the ABI, was '${e.message}'", e.message!!.contains("x86_64"))
    }
  }
}
