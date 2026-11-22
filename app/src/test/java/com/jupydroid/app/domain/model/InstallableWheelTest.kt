package com.jupydroid.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tag rule pip itself applies, as `installable()` in `scripts/read_wheel_platforms.py` states it
 * and as PyPI's own filenames spell it (PEP 427, PEP 600, PEP 656).
 */
class InstallableWheelTest {
  /** duckdb's aarch64 wheel: the interpreter the kernel runs and the libc it runs on. */
  @Test
  fun aManylinuxAarch64WheelForTheKernelsInterpreterFits() {
    val duckdb = "duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl"

    assertTrue(wheelFitsTarget(duckdb, WheelTarget.KERNEL))
  }

  /** A musllinux wheel is built for the Space's musl interpreter, which cells do not run. */
  @Test
  fun aMusllinuxWheelNeverFits() {
    assertFalse(wheelFitsTarget("pandas-3.1.1-cp312-cp312-musllinux_1_2_aarch64.whl", WheelTarget.KERNEL))
  }

  /** An `abi3` wheel of an older CPython runs on this one, which is why the rule reads the tag. */
  @Test
  fun anAbi3WheelOfAnOlderInterpreterFits() {
    assertTrue(wheelFitsTarget("joblib-1.4.2-cp311-abi3-manylinux_2_17_aarch64.whl", WheelTarget.KERNEL))
  }

  @Test
  fun anAbi3WheelOfANewerInterpreterDoesNot() {
    assertFalse(wheelFitsTarget("joblib-1.4.2-cp313-abi3-manylinux_2_17_aarch64.whl", WheelTarget.KERNEL))
  }

  /** A pure-Python wheel names no platform and no interpreter, and runs anywhere. */
  @Test
  fun aPy3WheelForAnyPlatformFits() {
    assertTrue(wheelFitsTarget("tabulate-0.10.0-py3-none-any.whl", WheelTarget.KERNEL))
  }

  @Test
  fun aWheelForAnotherArchitectureDoesNot() {
    assertFalse(wheelFitsTarget("pandas-3.1.1-cp312-cp312-manylinux_2_17_x86_64.whl", WheelTarget.KERNEL))
  }

  /** PEP 427 allows a build number, which makes six parts - a wheel PyPI does publish. */
  @Test
  fun aWheelWithABuildNumberFits() {
    assertTrue(wheelFitsTarget("numpy-2.3.4-1-cp312-cp312-manylinux_2_17_aarch64.whl", WheelTarget.KERNEL))
  }

  /** PEP 600 lets one wheel carry several platform tags, and any of them fitting is enough. */
  @Test
  fun aWheelCarryingSeveralPlatformTagsFitsWhenOneDoes() {
    val wheel = "numpy-2.3.4-cp312-cp312-manylinux_2_17_aarch64.manylinux_2_28_aarch64.whl"

    assertTrue(wheelFitsTarget(wheel, WheelTarget.KERNEL))
  }

  /**
   * Total rather than throwing: the filenames come from PyPI's own index, and a shape this app did
   * not expect is a wheel it cannot install, not a crash in a cell's output path.
   */
  @Test
  fun aNameThatIsNotAWheelDoesNotFit() {
    listOf("duckdb-2.0.1.tar.gz", "duckdb.whl", "duckdb-2.0.1-cp312-cp312.whl").forEach { name ->
      assertFalse(name, wheelFitsTarget(name, WheelTarget.KERNEL))
    }
  }
}
