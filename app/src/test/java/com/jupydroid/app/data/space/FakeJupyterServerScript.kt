package com.jupydroid.app.data.space

import java.io.File

/**
 * A `#!/bin/sh` script standing in for the native interpreter, so a JVM test can drive
 * [JupyterServerLauncher.start] end to end without a rootfs.
 *
 * It ignores the `-m jupyter_server ...` arguments and does the two things the launcher observes of
 * a real server: it writes a `jpserver-*.json` into `$JUPYTER_RUNTIME_DIR` and stays alive. It also
 * records its cwd beside that file. Every command is absolute or a shell builtin, because the Space
 * environment replaces `PATH` with directories a temp folder does not have.
 */
object FakeJupyterServerScript {
  /** A server that reports [port] and then runs until it is destroyed. */
  fun listening(dir: File, port: Int): File = script(
    dir,
    """
    pwd > "${'$'}JUPYTER_RUNTIME_DIR/cwd"
    echo '{"port": $port}' > "${'$'}JUPYTER_RUNTIME_DIR/jpserver-1.json"
    exec /bin/sleep 30
    """,
  )

  /** A server that prints [last] and exits before reporting a port. */
  fun dying(dir: File, last: String): File = script(dir, "echo '$last'\nexit 1")

  private fun script(dir: File, body: String): File = File(dir, "fake-python").apply {
    writeText("#!/bin/sh\n${body.trimIndent()}\n")
    setExecutable(true)
  }
}
