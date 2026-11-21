package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelTargetTest {
  /**
   * A Space's cells run on the gnu/glibc interpreter for arm64 (ADR-0166), on the interpreter
   * `BUNDLED_PYTHON_VERSION` names.
   */
  @Test
  fun aSpaceKernelInstallsForTheGnuInterpreterOnGlibcForArm64() {
    assertEquals(WheelTarget("cp312", "manylinux", "aarch64"), WheelTarget.KERNEL)
  }

  /** The tag is derived, so a rootfs bump is one edit and not two that can disagree. */
  @Test
  fun thePythonTagFollowsTheBundledInterpreter() {
    assertEquals("cp313", WheelTarget.cpythonTagOf("3.13"))
    assertEquals(WheelTarget.cpythonTagOf(BUNDLED_PYTHON_VERSION), WheelTarget.KERNEL.pythonTag)
  }
}
