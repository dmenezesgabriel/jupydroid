package com.jupydroid.app.data.space

import java.io.File

/**
 * The command line and environment for running a Space's Python.
 *
 * A runtime is two directories: [spaceDir] is the Space's own writable half - its
 * `home`, `tmp`, `pkgs` and `run` - while [rootfsDir] is the shared image the
 * interpreter, stdlib, shell and CA bundle are read out of. `base` ships both on
 * one directory; any other Space pairs its own dir with base's rootfs.
 *
 * Two things here are not obvious and both are load-bearing:
 *
 * 1. The interpreter is exec'd from [nativeInterpreter], not from `usr/bin/python3`
 *    inside the rootfs. Only the APK native library directory carries a SELinux
 *    context that permits execve at targetSdk 36 (ADR-0002).
 *    A pleasant side effect is that `sys.executable` is then a path the kernel
 *    subprocess can be launched from too, so kernelspecs need no special casing.
 * 2. `PYTHONHOME` is required, because Python derives its prefix from `argv[0]` and
 *    that now sits in the native library directory, nowhere near the stdlib.
 *
 * ```
 * SpaceRuntime(spaceDir, nativeInterpreter).command("-m", "jupyter_server")
 * ```
 */
class SpaceRuntime(
  private val spaceDir: File,
  private val nativeInterpreter: File,
  private val dnsServers: DnsServerSource = DnsServerSource { emptyList() },
  private val rootfsDir: File = spaceDir,
) {
  fun command(vararg args: String): List<String> = listOf(nativeInterpreter.path) + args

  fun environment(): Map<String, String> = mapOf(
    "PYTHONHOME" to inRootfs("usr"),
    // No Space process is an Android process, so none of them get a tombstone from
    // debuggerd. Without this a fatal signal leaves nothing at all to debug.
    "PYTHONFAULTHANDLER" to "1",
    "HOME" to notebookWorkspaceIn(spaceDir).path,
    "TMPDIR" to inSpace("tmp"),
    // jupyter_server writes `jpserver-*.json` and kernel connection files here, and the
    // app reads the same dir back through `JupyterRuntimeFile` to learn the bound port.
    // On every Space process, not only the server, so `jupyter --runtime-dir`, kernels
    // and terminals agree with the app about which Space a runtime file belongs to.
    "JUPYTER_RUNTIME_DIR" to inSpace("run"),
    // One setting decides the shell for all three things that go looking for one, and it is
    // load-bearing for the third rather than merely tidy:
    //   - jupyter_server_terminals consults it before falling back to `which("sh")`
    //     (`JupyterServerLauncher` pins `shell_command` outright, so this is its belt);
    //   - pexpect resolves it for a cell's printing escape, `!ls`, which would otherwise search PATH;
    //   - and `IPython/utils/_process_common.py:86-90` passes it as `Popen(executable=...)` for the
    //     *capture* escape, `files = !ls`. Unset, that stays None and CPython falls back to the
    //     absolute `/bin/sh`, which does not exist on Android - so the command runs nowhere and the
    //     cell gets an empty string.
    "SHELL" to spaceShellIn(rootfsDir).path,
    // The rootfs is not mounted at `/` on the device, so the console-script trampoline
    // (`tools/rootfs/shebang_exec.c`) prefixes this onto an absolute shebang: `#!/usr/bin/python3`
    // becomes `<root>/usr/bin/python3`. One home for what a Space process sees, beside SHELL.
    "JUPYDROID_SPACE_ROOT" to rootfsDir.path,
    // PEP 668: Alpine marks this interpreter EXTERNALLY-MANAGED, so bare `pip install` is
    // refused - and even `--user` is refused without `--break-system-packages` (measured
    // on-device). The managed answer is the Space's own user base: PYTHONUSERBASE puts
    // `pkgs/lib/...` on sys.path, PIP_USER makes installs ground there unflagged, and the
    // three together are inherited by kernel, terminal, `%pip` and LSP alike.
    "PYTHONUSERBASE" to inSpace("pkgs"),
    "PIP_USER" to "1",
    "PIP_ONLY_BINARY" to ":all:",
    "PIP_BREAK_SYSTEM_PACKAGES" to "1",
    // pkgs/bin first, so a user's console script shadows the shipped one - the precedence
    // ~/.local/bin takes over /usr/bin on a desktop.
    "PATH" to
      listOf(inSpace("pkgs/bin"), inRootfs("usr/local/bin"), inRootfs("usr/bin"), inRootfs("bin")).joinToString(":"),
    // Inherited by dlopen'd extension modules, unlike the interpreter's DT_RUNPATH.
    // musl has no multiarch directories, so these two cover the whole rootfs.
    "LD_LIBRARY_PATH" to listOf(inRootfs("usr/lib"), inRootfs("lib")).joinToString(":"),
    // The rootfs ships a real CA bundle, and both OpenSSL and musl's own resolver honour these two
    // variables - measured on the device, `ssl.get_default_verify_paths()` reports
    // `openssl_cafile_env='SSL_CERT_FILE'`. Unconditional, unlike the preloads
    // below - the bundle always ships, so there is no older-Space case to guard against.
    "SSL_CERT_FILE" to inRootfs("etc/ssl/certs/ca-certificates.crt"),
    "SSL_CERT_DIR" to inRootfs("etc/ssl/certs"),
  ) + sigsysHandler() + dnsResolverShim()

  /**
   * Android refuses syscalls outside bionic's allowlist with SECCOMP_RET_TRAP, and an
   * unhandled SIGSYS terminates the process - the whole reason `import numpy` used to die.
   * The handler reports the syscall and
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
    val handler = File(rootfsDir, SIGSYS_HANDLER)
    val log = if (handler.isFile) mapOf("JUPYDROID_SIGSYS_LOG" to inSpace(SIGSYS_LOG)) else emptyMap()
    val preloads = PRELOADS.map { File(rootfsDir, it) }.filter { it.isFile }
    if (preloads.isEmpty()) return emptyMap()
    // Space-separated, which is what the musl loader splits `LD_PRELOAD` on - the same as glibc.
    return mapOf("LD_PRELOAD" to preloads.joinToString(" ") { it.path }) + log
  }

  /**
   * musl's own resolver cannot be redirected to a writable resolv.conf - it reads the real
   * path through a raw inlined syscall no LD_PRELOAD can intercept
   * - so [DnsServerSource]'s addresses go straight to
   * `tools/rootfs/getaddrinfo_shim.c` as an environment variable instead of a file.
   *
   * Omitted, not blank, when there is nothing to report yet (e.g. no network observed since
   * launch): a shim with no configured nameservers falls through to musl's own (broken) lookup,
   * exactly as if this preload were absent, rather than querying an empty list forever.
   */
  private fun dnsResolverShim(): Map<String, String> {
    if (!File(rootfsDir, GETADDRINFO_SHIM).isFile) return emptyMap()
    val servers = dnsServers.current()
    return if (servers.isEmpty()) emptyMap() else mapOf("JUPYDROID_NAMESERVERS" to servers.joinToString(","))
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

  /**
   * A [ProcessBuilder] that execs [args] as given - with **no interpreter prefix** - under the
   * Space environment. Console scripts such as `usr/local/bin/pylsp` must run this way: their
   * `usr/local/bin` link resolves to `bin/shebang-exec`, a native trampoline ELF the kernel
   * permits to execve, which reads the `#!` line itself. Pushing the script path through
   * [command] would make it `python <path>` - argv[1] treated as a source file, which it is
   * not (measured green at `LanguageServerDeviceTest` on the tablet).
   */
  fun execProcessBuilder(args: List<String>, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder =
    ProcessBuilder(args).apply {
      environment().clear()
      environment().putAll(this@SpaceRuntime.environment() + extraEnv)
    }

  private fun inSpace(relative: String): String = File(spaceDir, relative).path

  private fun inRootfs(relative: String): String = File(rootfsDir, relative).path

  private companion object {
    /** Built by tools/rootfs/Dockerfile; see tools/rootfs/sigsys_report.c. */
    const val SIGSYS_HANDLER = "lib/libsigsys_report.so"

    /** Built into the x86_64 image alone; see tools/rootfs/musl_at_shim.c. */
    const val AT_SHIM = "lib/libmusl_at_shim.so"

    /** Built into both ABIs - the bug it answers is real on the tablet too; see getaddrinfo_shim.c. */
    const val GETADDRINFO_SHIM = "lib/libgetaddrinfo_shim.so"

    /** In load order: the reporter first, so it is armed before the shims' own calls run. */
    val PRELOADS = listOf(SIGSYS_HANDLER, AT_SHIM, GETADDRINFO_SHIM)

    /** In the Space's own tmp/: provisioned before any process runs, unlike run/. */
    const val SIGSYS_LOG = "tmp/sigsys.log"
  }
}
