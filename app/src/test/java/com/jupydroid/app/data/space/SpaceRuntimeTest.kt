package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_IDLE_SHUTDOWN_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Every Space process runs under proot with the Space as `/` (ADR-0073), so the command line is
 * proot's and every path the process is handed is named inside the root.
 */
class SpaceRuntimeTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val nativeDir = File("/data/app/abc==/lib/arm64")
  private val baseDir = File("/data/data/com.jupydroid.app/files/spaces/base")
  private val runtime = SpaceRuntime(spaceDir = baseDir, nativeLibraryDir = nativeDir)

  private val spaceDir = File("/data/data/com.jupydroid.app/files/spaces/space-1")
  private val userSpaceRuntime = SpaceRuntime(spaceDir = spaceDir, nativeLibraryDir = nativeDir)

  @Test
  fun command_runsTheImagesOwnInterpreterUnderProot() {
    val command = runtime.command("-c", "print(1)")

    assertEquals(File(nativeDir, ProotCommand.PROOT).path, command.first())
    assertEquals(listOf(SPACE_INTERPRETER, "-c", "print(1)"), command.takeLast(3))
  }

  /** A Space's root is its own directory, `base` included: no Space reads another's system. */
  @Test
  fun command_makesTheSpaceItselfTheRoot() {
    assertEquals(spaceDir.path, rootOf(userSpaceRuntime.command()))
    assertEquals(baseDir.path, rootOf(runtime.command()))
  }

  /**
   * The workspace is where a process starts, which is what a terminal inherits when its `POST`
   * names no cwd (`terminado/management.py:225-232`).
   */
  @Test
  fun command_startsInTheWorkspaceInsideTheRoot() {
    val command = runtime.command()

    assertEquals("/home/jupydroid", command[command.indexOf("-w") + 1])
  }

  @Test
  fun environment_placesHomeAndTmpInsideTheRoot() {
    val env = runtime.environment()

    assertEquals("/home/jupydroid", env["HOME"])
    assertEquals("/tmp", env["TMPDIR"])
  }

  /**
   * Nothing a Space process is handed may name a host path: proot would translate it into the root,
   * where it does not exist. This is also what retires `PYTHONHOME`, `LD_LIBRARY_PATH` and
   * `JUPYDROID_SPACE_ROOT`, which existed only because the image was not mounted at `/`.
   */
  @Test
  fun environment_namesNothingOutsideTheRoot() {
    val env = SpaceRuntime(spaceWithHandler(), nativeDir).environment()

    assertTrue("a host path leaked into $env", env.values.none { "/data/" in it || tempFolder.root.path in it })
    assertFalse(env.keys.any { it in setOf("PYTHONHOME", "LD_LIBRARY_PATH", "JUPYDROID_SPACE_ROOT") })
  }

  /**
   * Every Space process, not just the one being debugged. Android refuses some syscalls
   * with SECCOMP_RET_TRAP, and an unhandled SIGSYS terminates the process - which is the
   * only reason `import numpy` used to be fatal. The handler turns the
   * refusal back into the ENOSYS the kernel already returned. Setting it here rather than
   * per-launcher is what makes the server, the kernel and every future subprocess behave
   * the same; a Space where only some processes survive a refusal is the worst outcome.
   */
  @Test
  fun environment_preloadsTheSigsysHandlerByItsPathInsideTheRoot() {
    val preload = SpaceRuntime(spaceWithHandler(), nativeDir).environment().getValue("LD_PRELOAD")

    assertEquals("/lib/libsigsys_report.so", preload)
  }

  /**
   * The reports must go to a file, not stderr: ipykernel captures a kernel's stderr and
   * forwards it as cell output, so reporting there prints "SIGSYS seccomp-refused" into the
   * user's notebook on every numpy import. Measured on device before this was set.
   */
  @Test
  fun environment_sendsSigsysReportsToALogFileRatherThanTheUsersCellOutput() {
    val env = SpaceRuntime(spaceWithHandler(), nativeDir).environment()

    assertEquals("/tmp/sigsys.log", env["JUPYDROID_SIGSYS_LOG"])
  }

  /** A Space extracted before the handler existed must not get a preload pointing at nothing. */
  @Test
  fun environment_omitsThePreloadWhenTheHandlerIsNotInTheRootfs() {
    val empty = tempFolder.newFolder("space-without-handler")

    assertFalse(SpaceRuntime(empty, nativeDir).environment().containsKey("LD_PRELOAD"))
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

    val preload = SpaceRuntime(installed, nativeDir).environment().getValue("LD_PRELOAD")

    assertEquals("/lib/libsigsys_report.so /lib/libmusl_at_shim.so", preload)
  }

  /** And a rootfs with the shim but no reporter still gets the shim: they are independent. */
  @Test
  fun environment_preloadsTheAtShimOnItsOwn() {
    val installed = spaceWith("lib/libmusl_at_shim.so")

    assertEquals("/lib/libmusl_at_shim.so", SpaceRuntime(installed, nativeDir).environment().getValue("LD_PRELOAD"))
  }

  /**
   * Console scripts are not Python source: `/usr/bin/pylsp` is a `#!` script proot execs as the
   * kernel would. Running one through [SpaceRuntime.command] would produce `python3 <path>` - so the
   * direct-exec builder must not prepend the interpreter, while keeping the same environment
   * [SpaceRuntime.processBuilder] carries.
   */
  @Test
  fun execProcessBuilder_execsTheProgramDirectlyUnderProot() {
    val builder = runtime.execProcessBuilder(listOf(SPACE_PYLSP, "--version"))

    assertEquals(listOf(SPACE_PYLSP, "--version"), builder.command().takeLast(2))
    assertFalse(builder.command().contains(SPACE_INTERPRETER))
    assertEquals(SPACE_SHELL, builder.environment()["SHELL"])
  }

  /** What proot itself reads reaches it, beside what the process it runs reads. */
  @Test
  fun processBuilder_givesProotItsLoader() {
    val env = runtime.processBuilder(listOf("-c", "pass")).environment()

    assertEquals(File(nativeDir, ProotCommand.PROOT_LOADER).path, env["PROOT_LOADER"])
  }

  /** Android's own PATH would leak toybox into the Space; the image supplies its own. */
  @Test
  fun environment_confinesPathToTheImage() {
    assertEquals(
      "/pkgs/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
      runtime.environment()["PATH"],
    )
  }

  /**
   * `SHELL` names the Space's shell, and it is load-bearing rather than tidy.
   *
   * A cell's capture escape - `files = !ls` - reaches `IPython/utils/_process_common.py:86-90`,
   * which passes it as `Popen(executable=...)`. It is also what `jupyter_server_terminals` consults
   * before falling back to `which("sh")`, and what pexpect resolves for the printing escape - so one
   * setting decides the shell for all three, and it is the one the server pins for terminals.
   */
  @Test
  fun environment_namesTheShellTheServerPinsForTerminals() {
    assertEquals(SPACE_SHELL, runtime.environment()["SHELL"])
    assertTrue(
      jupyterServerArgs("/home/jupydroid", "t", DEFAULT_IDLE_SHUTDOWN_MINUTES).any {
        "[\"$SPACE_SHELL\"]" in it
      },
    )
  }

  /**
   * musl's own resolver cannot be redirected: this
   * joins the other two preloads exactly like they join each other, filtered by presence alone.
   */
  @Test
  fun environment_preloadsTheGetaddrinfoShimBesideTheOthersWhenTheRootfsHasIt() {
    val installed = spaceWithHandler().apply { File(this, "lib/libgetaddrinfo_shim.so").writeText("ELF") }

    val preload = SpaceRuntime(installed, nativeDir).environment().getValue("LD_PRELOAD")

    assertEquals("/lib/libsigsys_report.so /lib/libgetaddrinfo_shim.so", preload)
  }

  /** Sourced from Android's ConnectivityManager, bypassing /etc/resolv.conf entirely. */
  @Test
  fun environment_namesTheConfiguredNameserversWhenTheShimIsPresent() {
    val installed = spaceWith("lib/libgetaddrinfo_shim.so")
    val runtime = SpaceRuntime(installed, nativeDir, dnsServers = DnsServerSource { listOf("8.8.8.8", "1.1.1.1") })

    assertEquals("8.8.8.8,1.1.1.1", runtime.environment()["JUPYDROID_NAMESERVERS"])
  }

  /** No servers to report yet (e.g. no network observed since launch) - omitted, not blank. */
  @Test
  fun environment_omitsNameserversWhenNoneAreReported() {
    val installed = spaceWith("lib/libgetaddrinfo_shim.so")
    val runtime = SpaceRuntime(installed, nativeDir, dnsServers = DnsServerSource { emptyList() })

    assertFalse(runtime.environment().containsKey("JUPYDROID_NAMESERVERS"))
  }

  /** A Space extracted before the shim existed must not get nameservers a shim cannot use. */
  @Test
  fun environment_omitsNameserversWhenTheShimIsNotInTheRootfs() {
    val installed = tempFolder.newFolder("space-without-getaddrinfo-shim")
    val runtime = SpaceRuntime(installed, nativeDir, dnsServers = DnsServerSource { listOf("8.8.8.8") })

    assertFalse(runtime.environment().containsKey("JUPYDROID_NAMESERVERS"))
  }

  /**
   * PEP 668's answer for this interpreter: installs ground in the Space's own user base,
   * not the image's site-packages. Measured on-device: bare `pip install` is
   * refused by `EXTERNALLY-MANAGED` even with `--user`; with `PYTHONUSERBASE` + `PIP_USER`
   * + `PIP_BREAK_SYSTEM_PACKAGES` the same command lands under `pkgs/` and imports from there.
   */
  @Test
  fun environment_groundsPipInstallsInTheSpacesOwnUserBase() {
    assertEquals("/pkgs", runtime.environment()["PYTHONUSERBASE"])
  }

  /**
   * The three flags the unflagged command stands in for: `--user` (ground in PYTHONUSERBASE),
   * `--only-binary=:all:` (never start compiling under a phone), and `--break-system-packages`
   * (the marker is Alpine's, and the user base is the managed answer - verified on-device,
   * where `--user` alone is still refused).
   */
  @Test
  fun environment_makesUserBinaryAndMarkerTolerantThePipDefaults() {
    val env = runtime.environment()

    assertEquals("1", env["PIP_USER"])
    assertEquals(":all:", env["PIP_ONLY_BINARY"])
    assertEquals("1", env["PIP_BREAK_SYSTEM_PACKAGES"])
  }

  /**
   * The environment and [SpaceDirs.ensure] name the same directories, so a process can never be
   * pointed at a writable dir the Space's first launch did not provision.
   */
  @Test
  fun environment_namesTheDirsSpaceDirsProvisions() {
    val env = userSpaceRuntime.environment()
    val dirs = SpaceDirs(spaceDir)

    assertEquals(dirs.inRoot(dirs.workspace), env["HOME"])
    assertEquals(dirs.inRoot(dirs.tmp), env["TMPDIR"])
    assertEquals(dirs.inRoot(dirs.pkgs), env["PYTHONUSERBASE"])
    assertEquals(dirs.inRoot(dirs.runtime), env["JUPYTER_RUNTIME_DIR"])
  }

  /**
   * One runtime dir per Space, set on *every* process rather than only the server: kernel
   * connection files, `jupyter --runtime-dir` and the app-side `JupyterRuntimeFile` poll all agree
   * on where a Space's server is. Inside the root it is `/run`; the app reads it at `<space>/run`.
   */
  @Test
  fun processBuilder_givesTheChildTheSpacesRuntimeDirectory() {
    val env = userSpaceRuntime.processBuilder(listOf("-c", "pass")).environment()

    assertEquals("/run", env["JUPYTER_RUNTIME_DIR"])
  }

  @Test
  fun execProcessBuilder_givesTheChildTheSpacesRuntimeDirectory() {
    val env = userSpaceRuntime.execProcessBuilder(listOf(SPACE_SHELL, "-c", "pass")).environment()

    assertEquals("/run", env["JUPYTER_RUNTIME_DIR"])
  }

  private fun rootOf(command: List<String>): String = command[command.indexOf("-r") + 1]

  private fun spaceWithHandler(): File = spaceWith("lib/libsigsys_report.so")

  private fun spaceWith(library: String): File = tempFolder.newFolder().apply {
    File(this, library).apply { parentFile!!.mkdirs() }.writeText("ELF")
  }
}
