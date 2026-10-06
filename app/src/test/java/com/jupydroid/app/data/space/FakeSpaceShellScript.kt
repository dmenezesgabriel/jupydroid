package com.jupydroid.app.data.space

import java.io.File

/**
 * A `#!/bin/sh` script standing in for a Space's own `/bin/sh` as a startup script's outer shell, so
 * a JVM test can run the script under [FakeProotScript] without an image.
 *
 * It runs the script - the fourth argument [startupScriptArgs] passes - on the host's `/bin/sh` and
 * skips the in-root `timeout`, which [FakeProotScript] cannot translate. The bound and the status
 * it produces are `SpaceStartupScriptDeviceTest`'s to prove.
 */
object FakeSpaceShellScript {
  /** Installs the stand-in at [SPACE_SHELL] in [root]. */
  fun installIn(root: File): File = FakeProotScript.program(root, SPACE_SHELL, "exec /bin/sh -c \"${'$'}4\"")
}
