package com.jupydroid.app.data.space

import java.io.File

/**
 * A `#!/bin/sh` script installed where a rootfs carries `pylsp`, so a JVM test can drive
 * [startPylsp] without a rootfs.
 *
 * It records what [startPylsp] set up - its cwd and the three variables that place a Space's
 * writes - into `$TMPDIR/pylsp-env`, then exits. Only shell builtins, because the Space
 * environment replaces `PATH` with directories a temp folder does not have.
 */
object FakePylspScript {
  /** Installs the script at [StdioLanguageServer.pylspIn] of [rootfsDir]. */
  fun installIn(rootfsDir: File): File = StdioLanguageServer.pylspIn(rootfsDir).apply {
    parentFile.mkdirs()
    writeText(
      """
      #!/bin/sh
      { pwd; echo "${'$'}HOME"; echo "${'$'}PYTHONUSERBASE"; echo "${'$'}TMPDIR"; } > "${'$'}TMPDIR/pylsp-env"
      """.trimIndent() + "\n",
    )
    setExecutable(true)
  }

  /** The lines the script wrote for [spaceDir]: cwd, `HOME`, `PYTHONUSERBASE`, `TMPDIR`. */
  fun recordedIn(spaceDir: File): List<String> = File(spaceDir, "tmp/pylsp-env").readLines()
}
