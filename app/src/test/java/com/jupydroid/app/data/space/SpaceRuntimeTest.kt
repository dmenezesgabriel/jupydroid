package com.jupydroid.app.data.space

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceRuntimeTest {
  private val runtime = SpaceRuntime(
    baseDir = File("/data/data/com.jupydroid.app/files/spaces/base"),
    nativeInterpreter = File("/data/app/abc==/lib/x86_64/libpython3.so"),
  )

  @Test
  fun command_execsTheNativeInterpreterNotTheOneInTheRootfs() {
    val command = runtime.command("-c", "print(1)")

    assertEquals("/data/app/abc==/lib/x86_64/libpython3.so", command.first())
    assertEquals(listOf("-c", "print(1)"), command.drop(1))
  }

  @Test
  fun environment_pointsPythonAtTheRootfsRatherThanTheInterpretersOwnDirectory() {
    val env = runtime.environment()

    assertEquals("/data/data/com.jupydroid.app/files/spaces/base/usr", env["PYTHONHOME"])
    assertEquals("/data/data/com.jupydroid.app/files/spaces/base/home/jupydroid", env["HOME"])
    assertEquals("/data/data/com.jupydroid.app/files/spaces/base/tmp", env["TMPDIR"])
  }

  /**
   * DT_RUNPATH on the interpreter covers its own direct dependencies but is not
   * inherited, so dlopen'd extension modules (pyzmq's _zmq, and every other native
   * wheel) would not find libpthread. LD_LIBRARY_PATH is inherited and does.
   */
  @Test
  fun environment_setsLdLibraryPathForDlopenedExtensionModules() {
    val libraryPath = runtime.environment().getValue("LD_LIBRARY_PATH").split(":")

    assertTrue(
      "expected the triplet lib dir, got $libraryPath",
      libraryPath.contains("/data/data/com.jupydroid.app/files/spaces/base/usr/lib/x86_64-linux-gnu"),
    )
    assertTrue(libraryPath.contains("/data/data/com.jupydroid.app/files/spaces/base/usr/lib"))
  }

  /** Android's own PATH would leak toybox into the Space; the rootfs supplies its own. */
  @Test
  fun environment_confinesPathToTheRootfs() {
    val path = runtime.environment().getValue("PATH")

    assertTrue("expected only rootfs entries, got $path", path.split(":").all { it.startsWith("/data/data/") })
  }
}
