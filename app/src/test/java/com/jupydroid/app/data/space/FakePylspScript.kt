package com.jupydroid.app.data.space

import java.io.File

/**
 * A `#!/bin/sh` script installed where a Space's root carries `pylsp`, so a JVM test can drive
 * [startPylsp] under [FakeProotScript] without an image.
 *
 * It records what [startPylsp] set up - its cwd as the Space sees it, and the three variables that
 * place a Space's writes - into the Space's `tmp/pylsp-env`, then exits. `FAKE_PROOT_ROOT` stands
 * in for the translation real proot does.
 */
object FakePylspScript {
  /** Installs the script at [SPACE_PYLSP] inside [root]. */
  fun installIn(root: File): File = FakeProotScript.program(
    root,
    SPACE_PYLSP,
    """
    { echo "${'$'}{PWD#${'$'}FAKE_PROOT_ROOT}"; echo "${'$'}HOME"; echo "${'$'}PYTHONUSERBASE"; echo "${'$'}TMPDIR"; } \
      > "${'$'}FAKE_PROOT_ROOT${'$'}TMPDIR/pylsp-env"
    """,
  )

  /** The lines the script wrote in [spaceDir]: cwd, `HOME`, `PYTHONUSERBASE`, `TMPDIR`. */
  fun recordedIn(spaceDir: File): List<String> = File(spaceDir, "tmp/pylsp-env").readLines()
}
