package com.jupydroid.app.data.space

import java.io.File

/**
 * A `#!/bin/sh` script standing in for a Space's `/usr/bin/python3`, so a JVM test can drive
 * [JupyterServerLauncher.start] end to end under [FakeProotScript] without an image.
 *
 * It ignores the `-m jupyter_server ...` arguments and does the two things the launcher observes of
 * a real server: it writes a `jpserver-*.json` into `$JUPYTER_RUNTIME_DIR` and stays alive. It also
 * records its cwd, as the Space sees it, beside that file. `FAKE_PROOT_ROOT` prefixes the in-root
 * runtime dir where real proot would translate it.
 */
object FakeJupyterServerScript {
  /** A server in [root] that reports [port] and then runs until it is destroyed. */
  fun listening(root: File, port: Int): File = interpreter(
    root,
    """
    runtime="${'$'}FAKE_PROOT_ROOT${'$'}JUPYTER_RUNTIME_DIR"
    echo "${'$'}{PWD#${'$'}FAKE_PROOT_ROOT}" > "${'$'}runtime/cwd"
    echo '{"port": $port}' > "${'$'}runtime/jpserver-1.json"
    exec /bin/sleep 30
    """,
  )

  /** A server in [root] that prints [last] and exits before reporting a port. */
  fun dying(root: File, last: String): File = interpreter(root, "echo '$last'\nexit 1")

  private fun interpreter(root: File, body: String): File = FakeProotScript.program(root, SPACE_INTERPRETER, body)
}
