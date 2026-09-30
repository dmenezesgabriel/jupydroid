package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **`root_dir` and the server's own cwd are the same directory, because upstream they cannot be
 * anything else.**
 *
 * `jupyter_server` declares no default for `root_dir` in its `Unicode()`; the `@default` under it
 * returns `os.getcwd()` (`serverapp.py:1842-1848`). So everywhere but here the two are the same
 * directory by construction - you run `jupyter lab` somewhere and that somewhere becomes both.
 * This app is the one configuration that does not produce it: `--ServerApp.root_dir` was passed
 * explicitly while `ProcessBuilder` was left at the app process's cwd, which on Android is `/`.
 *
 * **Kernels never noticed and terminals cannot avoid it.** A kernel's cwd is derived from
 * `root_dir` (`services/kernels/kernelmanager.py:211-218`, `cwd_for_path`), so eight phases of
 * runtime work went by. A terminal's is *inherited*: `PtyWithClients` is handed `cwd=None` whenever
 * the `POST` carries none (`terminado/management.py:225-232`), which is what JupyterLab's own
 * launcher sends. A shell opening on a read-only `/` is a terminal gate failing outright.
 *
 * Every reference sets this at the spawn point rather than leaving it to inheritance - Termux
 * `chdir`s the forked child to the same constant it uses for `$HOME`
 * (`terminal-emulator/src/main/jni/termux.c:101`, `TermuxShellEnvironment.java:78,101`), Winlator
 * calls `ProcessBuilder.directory` (`core/ProcessHelper.java:68`), VS Code resolves an absolute cwd
 * and *fails the launch* if it is not a directory (`terminalProcess.ts:261-273`).
 */
class JupyterServerWorkingDirTest {
  private val baseDir = File("/data/data/com.jupydroid.app/files/spaces/base")
  private val workspace = notebookWorkspaceIn(baseDir)

  @Test
  fun theServerRunsInTheDirectoryItIsRootedAt() {
    assertEquals(workspace, serverProcess().directory())
  }

  /**
   * The two are asserted together, in one test, because the invariant is their *equality* - a
   * launcher that set a working directory unrelated to `--ServerApp.root_dir` would satisfy the
   * test above and still be the defect this closes.
   */
  @Test
  fun theWorkingDirectoryIsTheRootDirArgumentAndNotSomeOtherDirectory() {
    val process = serverProcess()

    val rootDirArg = process.command().single { it.startsWith("--ServerApp.root_dir=") }
    assertEquals("--ServerApp.root_dir=${process.directory()?.path}", rootDirArg)
  }

  private fun serverProcess() = jupyterServerProcess(
    runtime = SpaceRuntime(baseDir, File("/lib/libpython3.so")),
    runtimeDir = File(baseDir, "run"),
    rootDir = workspace,
    shell = spaceShellIn(baseDir),
    token = "t",
  )
}
