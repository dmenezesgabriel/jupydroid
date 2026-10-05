package com.jupydroid.app.data.space

import java.io.File

/**
 * The command line and environment for running a process in a Space.
 *
 * **A Space is its own system** (ADR-0073): [spaceDir] holds the whole image plus what the Space
 * writes - its `home`, `tmp`, `pkgs` and `run` - and every process runs under proot with
 * [spaceDir] as `/`. So the image runs as it was built: `/usr/bin/python3` is simply itself, and
 * every path a process is handed is named inside the root ([SpaceDirs.inRoot]). A host path would
 * be translated into the root, where it does not exist.
 *
 * proot is the one thing exec'd from [nativeLibraryDir], the only directory whose SELinux context
 * permits `execve` at targetSdk 36 ([ProotCommand]).
 *
 * ```
 * SpaceRuntime(spaceDir, File(applicationInfo.nativeLibraryDir)).command("-m", "jupyter_server")
 * ```
 */
class SpaceRuntime(
  private val spaceDir: File,
  nativeLibraryDir: File,
  private val dnsServers: DnsServerSource = DnsServerSource { emptyList() },
) {
  /** The writable half's layout, from the one home [SpaceDirs.ensure] provisions too. */
  private val dirs = SpaceDirs(spaceDir)

  /** Every process starts in the workspace, which a terminal inherits when it is given no cwd. */
  private val proot = ProotCommand(nativeLibraryDir, spaceDir, dirs.inRoot(dirs.workspace))

  /** The Space's interpreter run with [args], under proot. */
  fun command(vararg args: String): List<String> = proot.argv(listOf(SPACE_INTERPRETER) + args)

  /** What a process in the Space sees, every path named inside its root. */
  fun environment(): Map<String, String> = mapOf(
    // No Space process is an Android process, so none of them get a tombstone from
    // debuggerd. Without this a fatal signal leaves nothing at all to debug.
    "PYTHONFAULTHANDLER" to "1",
    "HOME" to dirs.inRoot(dirs.workspace),
    "TMPDIR" to dirs.inRoot(dirs.tmp),
    // jupyter_server writes `jpserver-*.json` and kernel connection files here, and the
    // app reads the same dir back through `JupyterRuntimeFile` to learn the bound port.
    // On every Space process, not only the server, so `jupyter --runtime-dir`, kernels
    // and terminals agree with the app about which Space a runtime file belongs to.
    "JUPYTER_RUNTIME_DIR" to dirs.inRoot(dirs.runtime),
    // One setting decides the shell for all three things that go looking for one, and it is
    // load-bearing for the third rather than merely tidy:
    //   - jupyter_server_terminals consults it before falling back to `which("sh")`
    //     (`JupyterServerLauncher` pins `shell_command` outright, so this is its belt);
    //   - pexpect resolves it for a cell's printing escape, `!ls`, which would otherwise search PATH;
    //   - and `IPython/utils/_process_common.py:86-90` passes it as `Popen(executable=...)` for the
    //     *capture* escape, `files = !ls`.
    "SHELL" to SPACE_SHELL,
    // PEP 668: Alpine marks this interpreter EXTERNALLY-MANAGED, so bare `pip install` is
    // refused - and even `--user` is refused without `--break-system-packages` (measured
    // on-device). The managed answer is the Space's own user base: PYTHONUSERBASE puts
    // `pkgs/lib/...` on sys.path, PIP_USER makes installs ground there unflagged, and the
    // three together are inherited by kernel, terminal, `%pip` and LSP alike.
    "PYTHONUSERBASE" to dirs.inRoot(dirs.pkgs),
    "PIP_USER" to "1",
    "PIP_ONLY_BINARY" to ":all:",
    "PIP_BREAK_SYSTEM_PACKAGES" to "1",
    // pkgs/bin first, so a user's console script shadows the shipped one - the precedence
    // ~/.local/bin takes over /usr/bin on a desktop. The rest is the image's own login PATH
    // (etc/profile in the shipped rootfs) - a Space's shell is never login, so this is the
    // only PATH a terminal sees, and /sbin/apk must resolve through it.
    "PATH" to listOf(
      dirs.inRoot(File(dirs.pkgs, "bin")),
      "/usr/local/sbin",
      "/usr/local/bin",
      "/usr/sbin",
      "/usr/bin",
      "/sbin",
      "/bin",
    ).joinToString(":"),
  ) + sigsysHandler() + dnsResolverShim()

  /**
   * Android refuses syscalls outside bionic's allowlist with SECCOMP_RET_TRAP, and an
   * unhandled SIGSYS terminates the process - the whole reason `import numpy` used to die.
   * The handler reports the syscall and
   * returns, leaving in place the ENOSYS the kernel already set.
   *
   * Omitted rather than dangling when absent, so a Space extracted by an older version
   * gets a clean environment instead of a preload pointing at nothing. Presence is checked on the
   * host; the preload is named inside the root, where the process's loader looks for it.
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
    val logPath = "${dirs.inRoot(dirs.tmp)}/$SIGSYS_LOG"
    val log = if (inImage(SIGSYS_HANDLER)) mapOf("JUPYDROID_SIGSYS_LOG" to logPath) else emptyMap()
    val preloads = PRELOADS.filter(::inImage)
    if (preloads.isEmpty()) return emptyMap()
    // Space-separated, which is what the musl loader splits `LD_PRELOAD` on - the same as glibc.
    return mapOf("LD_PRELOAD" to preloads.joinToString(" ") { "/$it" }) + log
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
    if (!inImage(GETADDRINFO_SHIM)) return emptyMap()
    val servers = dnsServers.current()
    return if (servers.isEmpty()) emptyMap() else mapOf("JUPYDROID_NAMESERVERS" to servers.joinToString(","))
  }

  /**
   * A [ProcessBuilder] running the Space's interpreter with [args], with Android's own
   * environment replaced by the Space's. Shared so the short-lived [PythonRunner] and the
   * long-lived server launcher cannot drift apart on how a Space process is set up.
   */
  fun processBuilder(args: List<String>, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder =
    spaceProcess(command(*args.toTypedArray()), extraEnv)

  /**
   * A [ProcessBuilder] that execs [args] as given - with **no interpreter prefix** - under proot
   * and the Space environment. Console scripts such as [SPACE_PYLSP] must run this way: proot
   * execs one through its `#!` line as the kernel would, while pushing it through [command] would
   * make it `python3 <path>`.
   */
  fun execProcessBuilder(args: List<String>, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder =
    spaceProcess(proot.argv(args), extraEnv)

  private fun spaceProcess(argv: List<String>, extraEnv: Map<String, String>): ProcessBuilder =
    ProcessBuilder(argv).apply {
      // Android's PATH and linker variables would otherwise leak into the Space.
      environment().clear()
      environment().putAll(proot.environment() + this@SpaceRuntime.environment() + extraEnv)
    }

  private fun inImage(relative: String): Boolean = File(spaceDir, relative).isFile

  private companion object {
    /** Built by tools/rootfs/Dockerfile; see tools/rootfs/sigsys_report.c. */
    const val SIGSYS_HANDLER = "lib/libsigsys_report.so"

    /** Built into the x86_64 image alone; see tools/rootfs/musl_at_shim.c. */
    const val AT_SHIM = "lib/libmusl_at_shim.so"

    /** Built into both ABIs - the bug it answers is real on the tablet too; see getaddrinfo_shim.c. */
    const val GETADDRINFO_SHIM = "lib/libgetaddrinfo_shim.so"

    /** In load order: the reporter first, so it is armed before the shims' own calls run. */
    val PRELOADS = listOf(SIGSYS_HANDLER, AT_SHIM, GETADDRINFO_SHIM)

    /** In the Space's own tmp/, which [SpaceDirs.ensure] provisions before any process runs. */
    const val SIGSYS_LOG = "sigsys.log"
  }
}
