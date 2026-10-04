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
  private val runtime = SpaceRuntime(spaceDir = baseDir, nativeInterpreter = interpreter)

  private val spaceDir = File("/data/data/com.jupydroid.app/files/spaces/space-1")
  private val userSpaceRuntime =
    SpaceRuntime(spaceDir = spaceDir, nativeInterpreter = interpreter, rootfsDir = baseDir)

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
   * only reason `import numpy` used to be fatal. The handler turns the
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

  /**
   * Console scripts (`usr/local/bin/pylsp`) are not Python source: their link resolves to the
   * native `shebang-exec` trampoline, which execs on its own and reads the `#!` line. Running
   * one through [SpaceRuntime.command] would produce `python <path>` and feed a shell script's
   * path to the interpreter as argv - so the direct-exec builder must not prepend it, while
   * keeping the same environment [SpaceRuntime.processBuilder] carries.
   */
  @Test
  fun execProcessBuilder_execsTheScriptDirectlyUnderTheSpaceEnvironment() {
    val builder = runtime.execProcessBuilder(listOf("$baseDir/usr/local/bin/pylsp", "--version"))

    assertEquals("$baseDir/usr/local/bin/pylsp", builder.command().first())
    assertFalse(builder.command().contains(interpreter.path))
    assertEquals("$baseDir/usr", builder.environment()["PYTHONHOME"])
    assertEquals(spaceShellIn(baseDir).path, builder.environment()["SHELL"])
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
   * takes the pexpect path and searches `PATH`, worked.
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
   * (measured on device: `openssl_cafile_env='SSL_CERT_FILE'`). Unconditional,
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

  /**
   * musl's own resolver cannot be redirected: this
   * joins the other two preloads exactly like they join each other, filtered by presence alone.
   */
  @Test
  fun environment_preloadsTheGetaddrinfoShimBesideTheOthersWhenTheRootfsHasIt() {
    val installed = spaceWithHandler().apply { File(this, "lib/libgetaddrinfo_shim.so").writeText("ELF") }

    val preload = SpaceRuntime(installed, interpreter).environment().getValue("LD_PRELOAD")

    assertEquals(
      listOf("lib/libsigsys_report.so", "lib/libgetaddrinfo_shim.so").joinToString(" ") { File(installed, it).path },
      preload,
    )
  }

  @Test
  fun environment_preloadsTheGetaddrinfoShimOnItsOwn() {
    val installed = tempFolder.newFolder("space-with-getaddrinfo-shim-only").apply {
      File(this, "lib").mkdirs()
      File(this, "lib/libgetaddrinfo_shim.so").writeText("ELF")
    }

    assertEquals(
      File(installed, "lib/libgetaddrinfo_shim.so").path,
      SpaceRuntime(installed, interpreter).environment().getValue("LD_PRELOAD"),
    )
  }

  /** Sourced from Android's ConnectivityManager, bypassing /etc/resolv.conf entirely. */
  @Test
  fun environment_namesTheConfiguredNameserversWhenTheShimIsPresent() {
    val installed = tempFolder.newFolder("space-with-shim-and-servers").apply {
      File(this, "lib").mkdirs()
      File(this, "lib/libgetaddrinfo_shim.so").writeText("ELF")
    }
    val runtime = SpaceRuntime(installed, interpreter, dnsServers = DnsServerSource { listOf("8.8.8.8", "1.1.1.1") })

    assertEquals("8.8.8.8,1.1.1.1", runtime.environment()["JUPYDROID_NAMESERVERS"])
  }

  /** No servers to report yet (e.g. no network observed since launch) - omitted, not blank. */
  @Test
  fun environment_omitsNameserversWhenNoneAreReported() {
    val installed = tempFolder.newFolder("space-with-shim-and-no-servers").apply {
      File(this, "lib").mkdirs()
      File(this, "lib/libgetaddrinfo_shim.so").writeText("ELF")
    }
    val runtime = SpaceRuntime(installed, interpreter, dnsServers = DnsServerSource { emptyList() })

    assertFalse(runtime.environment().containsKey("JUPYDROID_NAMESERVERS"))
  }

  /** A Space extracted before the shim existed must not get nameservers a shim cannot use. */
  @Test
  fun environment_omitsNameserversWhenTheShimIsNotInTheRootfs() {
    val installed = tempFolder.newFolder("space-without-getaddrinfo-shim")
    val runtime = SpaceRuntime(installed, interpreter, dnsServers = DnsServerSource { listOf("8.8.8.8") })

    assertFalse(runtime.environment().containsKey("JUPYDROID_NAMESERVERS"))
  }

  /**
   * PEP 668's answer for this interpreter: installs ground in the Space's own user base,
   * not the rootfs's site-packages. Measured on-device: bare `pip install` is
   * refused by `EXTERNALLY-MANAGED` even with `--user`; with `PYTHONUSERBASE` + `PIP_USER`
   * + `PIP_BREAK_SYSTEM_PACKAGES` the same command lands under `pkgs/` and imports from
   * there, leaving `usr/lib/python3.12/site-packages` untouched.
   */
  @Test
  fun environment_groundsPipInstallsInTheSpacesOwnUserBase() {
    assertEquals(
      "/data/data/com.jupydroid.app/files/spaces/base/pkgs",
      runtime.environment()["PYTHONUSERBASE"],
    )
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
   * A `--user` install drops console scripts in `pkgs/bin` - pip's own warning on-device was
   * "which is not on PATH". It precedes the rootfs entries so a user's newer tool shadows the
   * shipped one, the same precedence `~/.local/bin` takes over `/usr/bin` on a desktop.
   */
  @Test
  fun environment_putsUserInstalledConsoleScriptsOnPathAheadOfTheRootfs() {
    val path = runtime.environment().getValue("PATH").split(":")

    assertEquals("/data/data/com.jupydroid.app/files/spaces/base/pkgs/bin", path.first())
  }

  /**
   * A user Space pairs its own directory with the shared rootfs: everything a Space *writes* -
   * its home, its scratch, its installed packages, its server's runtime files - grounds in
   * `spaceDir`, so two Spaces cannot collide on any of them.
   */
  @Test
  fun environment_derivesTheWritableDirsFromTheSpaceRatherThanTheSharedRootfs() {
    val env = userSpaceRuntime.environment()

    assertEquals("/data/data/com.jupydroid.app/files/spaces/space-1/home/jupydroid", env["HOME"])
    assertEquals("/data/data/com.jupydroid.app/files/spaces/space-1/tmp", env["TMPDIR"])
    assertEquals("/data/data/com.jupydroid.app/files/spaces/space-1/pkgs", env["PYTHONUSERBASE"])
    assertEquals("/data/data/com.jupydroid.app/files/spaces/space-1/run", env["JUPYTER_RUNTIME_DIR"])
  }

  /**
   * And everything a Space *reads* - the stdlib anchor, the shell, the shebang
   * trampoline's root, the library path, the CA bundle - comes off the shared
   * image, so a user Space carries no copy of the toolchain.
   */
  @Test
  fun environment_derivesTheSharedToolsFromTheRootfsRatherThanTheSpace() {
    val env = userSpaceRuntime.environment()

    assertEquals("/data/data/com.jupydroid.app/files/spaces/base/usr", env["PYTHONHOME"])
    assertEquals(spaceShellIn(baseDir).path, env["SHELL"])
    assertEquals(baseDir.path, env["JUPYDROID_SPACE_ROOT"])
    assertEquals(
      "/data/data/com.jupydroid.app/files/spaces/base/etc/ssl/certs/ca-certificates.crt",
      env["SSL_CERT_FILE"],
    )
    assertEquals("/data/data/com.jupydroid.app/files/spaces/base/etc/ssl/certs", env["SSL_CERT_DIR"])
    assertEquals(
      "/data/data/com.jupydroid.app/files/spaces/base/usr/lib:/data/data/com.jupydroid.app/files/spaces/base/lib",
      env["LD_LIBRARY_PATH"],
    )
  }

  /**
   * One runtime dir per Space, and set on *every* process rather than only the server:
   * kernel connection files, `jupyter --runtime-dir` and the app-side
   * `JupyterRuntimeFile` poll all agree on where a Space's server is, which is the
   * assumption [JupyterServerLauncher] was already built on. For `base` the value is
   * the path the launcher used to inject - unchanged.
   */
  @Test
  fun environment_namesTheSpacesOwnRuntimeDirectory() {
    assertEquals("$baseDir/run", runtime.environment()["JUPYTER_RUNTIME_DIR"])
    assertEquals("$spaceDir/run", userSpaceRuntime.environment()["JUPYTER_RUNTIME_DIR"])
  }

  /**
   * Same split on PATH: the Space's `pkgs/bin` keeps precedence, but the toolchain
   * entries it precedes belong to the shared image, not the Space.
   */
  @Test
  fun environment_pairsTheSpacesConsoleScriptsWithTheSharedRootfsOnPath() {
    val path = userSpaceRuntime.environment().getValue("PATH").split(":")

    assertEquals(
      listOf(
        "/data/data/com.jupydroid.app/files/spaces/space-1/pkgs/bin",
        "/data/data/com.jupydroid.app/files/spaces/base/usr/local/bin",
        "/data/data/com.jupydroid.app/files/spaces/base/usr/bin",
        "/data/data/com.jupydroid.app/files/spaces/base/bin",
      ),
      path,
    )
  }

  /**
   * The handler is read out of the shared image, but its log is the Space's own -
   * a second Space's refusals must not write into base's `tmp`.
   */
  @Test
  fun environment_findsTheSigsysHandlerInTheRootfsAndWritesItsLogIntoTheSpace() {
    val sharedRootfs = tempFolder.newFolder("shared-rootfs").apply {
      File(this, "lib").mkdirs()
      File(this, "lib/libsigsys_report.so").writeText("ELF")
    }
    val space = tempFolder.newFolder("space-1")

    val env = SpaceRuntime(space, interpreter, rootfsDir = sharedRootfs).environment()

    assertEquals(File(sharedRootfs, "lib/libsigsys_report.so").path, env["LD_PRELOAD"])
    assertEquals(File(space, "tmp/sigsys.log").path, env["JUPYDROID_SIGSYS_LOG"])
  }

  /** Same for the resolver shim: probed on the rootfs, since that is where it is built. */
  @Test
  fun environment_findsTheGetaddrinfoShimInTheRootfsForASpaceWithItsOwnDir() {
    val sharedRootfs = tempFolder.newFolder("shared-rootfs-dns").apply {
      File(this, "lib").mkdirs()
      File(this, "lib/libgetaddrinfo_shim.so").writeText("ELF")
    }
    val runtime = SpaceRuntime(
      tempFolder.newFolder("space-2"),
      interpreter,
      dnsServers = DnsServerSource { listOf("8.8.8.8") },
      rootfsDir = sharedRootfs,
    )

    assertEquals("8.8.8.8", runtime.environment()["JUPYDROID_NAMESERVERS"])
  }

  /** The child env, not only the map: the server's runtime dir reaches it either way. */
  @Test
  fun processBuilder_givesTheChildTheSpacesRuntimeDirectory() {
    val env = userSpaceRuntime.processBuilder(listOf("-c", "pass")).environment()

    assertEquals("$spaceDir/run", env["JUPYTER_RUNTIME_DIR"])
  }

  @Test
  fun execProcessBuilder_givesTheChildTheSpacesRuntimeDirectory() {
    val env = userSpaceRuntime.execProcessBuilder(listOf("$baseDir/bin/sh", "-c", "pass")).environment()

    assertEquals("$spaceDir/run", env["JUPYTER_RUNTIME_DIR"])
  }
}
