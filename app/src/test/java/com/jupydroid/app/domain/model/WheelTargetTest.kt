package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelTargetTest {
  /** A Space is Alpine on musl for arm64 (ADR-0003), on the interpreter `BUNDLED_PYTHON_VERSION` names. */
  @Test
  fun aSpaceInstallsForTheImagesInterpreterOnMuslForArm64() {
    assertEquals(WheelTarget("cp312", "musllinux", "aarch64"), WheelTarget.SPACE)
  }

  /** The tag is derived, so a rootfs bump is one edit and not two that can disagree. */
  @Test
  fun thePythonTagFollowsTheBundledInterpreter() {
    assertEquals("cp313", WheelTarget.cpythonTagOf("3.13"))
    assertEquals(WheelTarget.cpythonTagOf(BUNDLED_PYTHON_VERSION), WheelTarget.SPACE.pythonTag)
  }
}
