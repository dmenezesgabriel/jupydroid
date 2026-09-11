package com.jupydroid.app.data.space

import java.io.File

/**
 * The command line and environment for running a Space's Python.
 *
 * Two things here are not obvious and both are load-bearing:
 *
 * 1. The interpreter is exec'd from [nativeInterpreter], not from `usr/bin/python3`
 *    inside the rootfs. Only the APK native library directory carries a SELinux
 *    context that permits execve at targetSdk 36 (docs/DECISIONS.md, "Exec model").
 *    A pleasant side effect is that `sys.executable` is then a path the kernel
 *    subprocess can be launched from too, so kernelspecs need no special casing.
 * 2. `PYTHONHOME` is required, because Python derives its prefix from `argv[0]` and
 *    that now sits in the native library directory, nowhere near the stdlib.
 *
 * ```
 * SpaceRuntime(baseDir, nativeInterpreter).command("-m", "jupyter_server")
 * ```
 */
class SpaceRuntime(private val baseDir: File, private val nativeInterpreter: File) {
  fun command(vararg args: String): List<String> = listOf(nativeInterpreter.path) + args

  fun environment(): Map<String, String> = mapOf(
    "PYTHONHOME" to inBase("usr"),
    // No Space process is an Android process, so none of them get a tombstone from
    // debuggerd. Without this a fatal signal leaves nothing at all to debug.
    "PYTHONFAULTHANDLER" to "1",
    "HOME" to notebookWorkspaceIn(baseDir).path,
    "TMPDIR" to inBase("tmp"),
    // One setting decides the shell for all three things that go looking for one, and it is
    // load-bearing for the third rather than merely tidy:
    //   - jupyter_server_terminals consults it before falling back to `which("sh")` (P4c pins
    //     `shell_command` outright, so this is its belt);
    //   - pexpect resolves it for a cell's printing escape, `!ls`, which would otherwise search PATH;
    //   - and `IPython/utils/_process_common.py:86-90` passes it as `Popen(executable=...)` for the
    //     *capture* escape, `files = !ls`. Unset, that stays None and CPython falls back to the
    //     absolute `/bin/sh`, which does not exist on Android - so the command runs nowhere and the
    //     cell gets an empty string. Measured: `evidence/p4-terminal/shell-escapes.md`.
    "SHELL" to spaceShellIn(baseDir).path,
    // The rootfs is not mounted at `/` on the device, so the console-script trampoline
    // (`tools/rootfs/shebang_exec.c`) prefixes this onto an absolute shebang: `#!/usr/bin/python3`
    // becomes `<root>/usr/bin/python3`. One home for what a Space process sees, beside SHELL.
    "JUPYDROID_SPACE_ROOT" to baseDir.path,
    "PATH" to listOf(inBase("usr/local/bin"), inBase("usr/bin"), inBase("bin")).joinToString(":"),
    // Inherited by dlopen'd extension modules, unlike the interpreter's DT_RUNPATH.
    // musl has no multiarch directories, so these two cover the whole rootfs.
    "LD_LIBRARY_PATH" to listOf(inBase("usr/lib"), inBase("lib")).joinToString(":"),
    // The rootfs ships a real CA bundle, and both OpenSSL and musl's own resolver honour these two
    // variables (P7e-0: `openssl_cafile_env='SSL_CERT_FILE'`). Unconditional, unlike the preloads
    // below - the bundle always ships, so there is no older-Space case to guard against.
    "SSL_CERT_FILE" to inBase("etc/ssl/certs/ca-certificates.crt"),
    "SSL_CERT_DIR" to inBase("etc/ssl/certs"),
  ) + sigsysHandler()

  /**
   * Android refuses syscalls outside bionic's allowlist with SECCOMP_RET_TRAP, and an
   * unhandled SIGSYS terminates the process - the whole reason `import numpy` used to die
   * (evidence/step8.7/sigsys-syscall-named.txt). The handler reports the syscall and
   * returns, leaving in place the ENOSYS the kernel already set.
   *
   * Omitted rather than dangling when absent, so a Space extracted by an older version
   * gets a clean environment instead of a preload pointing at nothing.
   *
   * **[AT_SHIM] rides the same variable, and only the rootfs that needs it carries it.** On
   * x86_64 musl issues four legacy syscalls Android refuses - `lstat`, `access`, `mkdir`,
   * `unlink` - where bionic issues the `*at` forms; on arm64 those four are not in the kernel ABI
   * at all, so musl already issues `*at` and the shim would be dead code. Which ABI gets it is
   * therefore decided in `tools/rootfs/Dockerfile`, not here: the rootfs that needs the shim is
   * the rootfs that ships it, and this just preloads what it finds.
   *
   * The reports go to a log file, never stderr: ipykernel captures a kernel's stderr and
   * forwards it as cell output, so reporting there would print "SIGSYS seccomp-refused" into
   * the user's notebook on every numpy import. Measured on device, not guessed.
   */
  private fun sigsysHandler(): Map<String, String> {
    val handler = File(baseDir, SIGSYS_HANDLER)
    val log = if (handler.isFile) mapOf("JUPYDROID_SIGSYS_LOG" to inBase(SIGSYS_LOG)) else emptyMap()
    val preloads = PRELOADS.map { File(baseDir, it) }.filter { it.isFile }
    if (preloads.isEmpty()) return emptyMap()
    // Space-separated, which is what the musl loader splits `LD_PRELOAD` on - the same as glibc.
    return mapOf("LD_PRELOAD" to preloads.joinToString(" ") { it.path }) + log
  }

  /**
   * A [ProcessBuilder] with Android's own environment replaced by the Space's.
   * Shared so the short-lived [PythonRunner] and the long-lived server launcher
   * cannot drift apart on how a Space process is set up.
   */
  fun processBuilder(args: List<String>, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder =
    ProcessBuilder(command(*args.toTypedArray())).apply {
      // Android's PATH and linker variables would otherwise leak into the Space.
      environment().clear()
      environment().putAll(this@SpaceRuntime.environment() + extraEnv)
    }

  private fun inBase(relative: String): String = File(baseDir, relative).path

  private companion object {
    /** Built by tools/rootfs/Dockerfile; see tools/rootfs/sigsys_report.c. */
    const val SIGSYS_HANDLER = "lib/libsigsys_report.so"

    /** Built into the x86_64 image alone; see tools/rootfs/musl_at_shim.c. */
    const val AT_SHIM = "lib/libmusl_at_shim.so"

    /** In load order: the reporter first, so it is armed before the shim's own calls run. */
    val PRELOADS = listOf(SIGSYS_HANDLER, AT_SHIM)

    /** In tmp/ because it always exists in the rootfs, unlike run/ before the server starts. */
    const val SIGSYS_LOG = "tmp/sigsys.log"
  }
}
