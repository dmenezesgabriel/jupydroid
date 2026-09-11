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
  private val baseDir = File("/data/data/com.jupydroid.app/files/spaces/base")
  private val runtime = SpaceRuntime(baseDir = baseDir, nativeInterpreter = interpreter)

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

  /**
   * **The x86_64 interposer joins the preload when the rootfs carries it**, and the arm64 rootfs
   * does not carry it - musl already issues the `*at` syscalls there, so it would be dead code
   * (`tools/rootfs/musl_at_shim.c`). One list rather than a per-ABI branch in Kotlin: the rootfs
   * that needs the shim is the rootfs that ships it.
   */
  @Test
  fun environment_preloadsTheAtShimBesideTheReporterWhenTheRootfsHasBoth() {
    val installed = spaceWithHandler().apply { File(this, "lib/libmusl_at_shim.so").writeText("ELF") }

    val preload = SpaceRuntime(installed, interpreter).environment().getValue("LD_PRELOAD")

    assertEquals(
      listOf("lib/libsigsys_report.so", "lib/libmusl_at_shim.so").joinToString(" ") { File(installed, it).path },
      preload,
    )
  }

  /** The arm64 rootfs has the reporter and no shim, and must be preloaded with exactly the one. */
  @Test
  fun environment_preloadsOnlyTheReporterWhenThereIsNoAtShim() {
    val installed = spaceWithHandler()

    assertEquals(
      File(installed, "lib/libsigsys_report.so").path,
      SpaceRuntime(installed, interpreter).environment().getValue("LD_PRELOAD"),
    )
  }

  /** And a rootfs with the shim but no reporter still gets the shim: they are independent. */
  @Test
  fun environment_preloadsTheAtShimOnItsOwn() {
    val installed = tempFolder.newFolder("space-with-shim-only").apply {
      File(this, "lib").mkdirs()
      File(this, "lib/libmusl_at_shim.so").writeText("ELF")
    }

    assertEquals(
      File(installed, "lib/libmusl_at_shim.so").path,
      SpaceRuntime(installed, interpreter).environment().getValue("LD_PRELOAD"),
    )
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

  /**
   * `SHELL` names the Space's shell, and it is load-bearing rather than tidy.
   *
   * A cell's capture escape - `files = !ls` - reaches `IPython/utils/_process_common.py:86-90`:
   *
   *     executable = None
   *     if shell and os.name == 'posix' and 'SHELL' in os.environ:
   *         executable = os.environ['SHELL']
   *     p = subprocess.Popen(cmd, shell=shell, executable=executable, ...)
   *
   * Unset, `executable` stays None and CPython falls back to the absolute `/bin/sh`, which does
   * not exist on Android - so the command runs nowhere and the cell gets an empty string back.
   * Measured before this was set: `getoutput repr: ''` for `echo` itself, while `!echo`, which
   * takes the pexpect path and searches `PATH`, worked (`evidence/p4-terminal/shell-escapes.md`).
   *
   * The same variable is what `jupyter_server_terminals` consults before falling back to
   * `which("sh")`, and what pexpect resolves for the printing escape - so one setting decides
   * the shell for all three rather than each finding its own.
   */
  @Test
  fun environment_namesTheSpacesShellSoNothingFallsBackToTheDevicesRoot() {
    val shell = runtime.environment().getValue("SHELL")

    assertEquals(spaceShellIn(baseDir).path, shell)
  }

  /**
   * The rootfs ships a real CA bundle, and OpenSSL/musl both honour these two variables
   * (measured: `openssl_cafile_env='SSL_CERT_FILE'`, evidence/p7e-0-runtime-probe/). Unconditional,
   * unlike the preloads: the bundle always ships, so there is no older-Space case to guard.
   */
  @Test
  fun environment_pointsOpensslAtTheShippedCaBundle() {
    val env = runtime.environment()

    assertEquals(
      "/data/data/com.jupydroid.app/files/spaces/base/etc/ssl/certs/ca-certificates.crt",
      env["SSL_CERT_FILE"],
    )
  }

  @Test
  fun environment_pointsOpensslAtTheShippedCaDirectoryToo() {
    val env = runtime.environment()

    assertEquals("/data/data/com.jupydroid.app/files/spaces/base/etc/ssl/certs", env["SSL_CERT_DIR"])
  }
}
