package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpaceRuntimeTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val interpreter = File("/data/app/abc==/lib/arm64/libpython3.so")
  private val runtime = SpaceRuntime(
    baseDir = File("/data/data/com.jupydroid.app/files/spaces/base"),
    nativeInterpreter = interpreter,
  )

  @Test
  fun command_execsTheNativeInterpreterNotTheOneInTheRootfs() {
    val command = runtime.command("-c", "print(1)")

    assertEquals("/data/app/abc==/lib/arm64/libpython3.so", command.first())
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

    // musl has no multiarch directories: everything is in /usr/lib and /lib.
    assertEquals(
      listOf(
        "/data/data/com.jupydroid.app/files/spaces/base/usr/lib",
        "/data/data/com.jupydroid.app/files/spaces/base/lib",
      ),
      libraryPath,
    )
  }

  /**
   * Every Space process, not just the one being debugged. Android refuses some syscalls
   * with SECCOMP_RET_TRAP, and an unhandled SIGSYS terminates the process - which is the
   * only reason `import numpy` used to be fatal (evidence/step8.7). The handler turns the
   * refusal back into the ENOSYS the kernel already returned. Setting it here rather than
   * per-launcher is what makes the server, the kernel and every future subprocess behave
   * the same; a Space where only some processes survive a refusal is the worst outcome.
   */
  @Test
  fun environment_preloadsTheSigsysHandlerIntoEverySpaceProcess() {
    val installed = spaceWithHandler()

    val preload = SpaceRuntime(installed, interpreter).environment().getValue("LD_PRELOAD")

    assertEquals(File(installed, "lib/libsigsys_report.so").path, preload)
  }

  /**
   * The reports must go to a file, not stderr: ipykernel captures a kernel's stderr and
   * forwards it as cell output, so reporting there prints "SIGSYS seccomp-refused" into the
   * user's notebook on every numpy import. Measured on device before this was set.
   */
  @Test
  fun environment_sendsSigsysReportsToALogFileRatherThanTheUsersCellOutput() {
    val installed = spaceWithHandler()

    val env = SpaceRuntime(installed, interpreter).environment()

    assertEquals(File(installed, "tmp/sigsys.log").path, env["JUPYDROID_SIGSYS_LOG"])
  }

  /** A Space extracted before the handler existed must not get a preload pointing at nothing. */
  @Test
  fun environment_omitsThePreloadWhenTheHandlerIsNotInTheRootfs() {
    val empty = tempFolder.newFolder("space-without-handler")

    assertFalse(SpaceRuntime(empty, interpreter).environment().containsKey("LD_PRELOAD"))
  }

  private fun spaceWithHandler(): File = tempFolder.newFolder("space-with-handler").apply {
    File(this, "lib").mkdirs()
    File(this, "lib/libsigsys_report.so").writeText("ELF")
  }

  /** Android's own PATH would leak toybox into the Space; the rootfs supplies its own. */
  @Test
  fun environment_confinesPathToTheRootfs() {
    val path = runtime.environment().getValue("PATH")

    assertTrue("expected only rootfs entries, got $path", path.split(":").all { it.startsWith("/data/data/") })
  }
}
