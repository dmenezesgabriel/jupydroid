package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shell terminado spawns is a path this app owns, not the result of a `PATH` search.
 *
 * Unpinned, `jupyter_server_terminals/app.py:initialize_configurables` resolves the shell as
 * `[os.environ.get("SHELL") or which("sh")]` and then **appends `-l`**, because the append is
 * guarded on `shell_override is None` and this server's stdout is a file rather than a tty
 * (`JupyterServerLauncher.start` calls `redirectOutput`). That would start busybox ash as a login
 * shell sourcing a profile nobody audited, out of whatever `PATH` happened to hold.
 *
 * The argument list is asserted rather than a running server, exactly as [JupyterServerRootDirTest]
 * and [JupyterServerLifecycleArgsTest] do: a real jupyter_server needs a Space, which is the tablet
 * gate's to prove.
 */
class JupyterServerTerminalShellTest {
  private val args = jupyterServerArgs(rootDir = "/home/jupydroid", token = "t")

  @Test
  fun theShellIsPinnedRatherThanSearchedFor() {
    assertEquals(
      """--ServerApp.terminado_settings={"shell_command": ["/bin/sh"]}""",
      args.single { it.startsWith("--ServerApp.terminado_settings=") },
    )
  }

  /**
   * A **list**, never a bare string. `initialize_configurables` runs `shlex.split` over a string
   * value, so a path is one quoting rule away from being torn in two; a list reaches `ptyprocess`
   * as the argv it already is.
   */
  @Test
  fun theShellCommandIsAListSoNothingSplitsThePath() {
    val value = args.single { it.startsWith("--ServerApp.terminado_settings=") }

    assertTrue("expected a JSON list for shell_command, got $value", """"shell_command": [""" in value)
  }

  @Test
  fun theOtherArgumentsAreStillThere() {
    assertTrue(args.contains("--IdentityProvider.token=t"))
    assertTrue(args.contains("--ServerApp.root_dir=/home/jupydroid"))
  }
}
