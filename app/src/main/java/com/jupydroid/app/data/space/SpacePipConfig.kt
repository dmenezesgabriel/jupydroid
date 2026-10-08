package com.jupydroid.app.data.space

import java.io.File

/**
 * Makes the image's own pip install into the Space's user base, and leaves a venv's pip alone.
 *
 * `--user` cannot ride the environment as `PIP_USER`: every pip in the Space would inherit it, and
 * a venv's pip refuses it ("User site-packages are not visible in this virtualenv",
 * `pip/_internal/commands/install.py` in the shipped rootfs). pip's SITE config file is
 * `<sys.prefix>/pip.conf` (`pip/_internal/configuration.py`, pip 25.0.1 in the shipped rootfs), so
 * `usr/pip.conf` is read by `/usr/bin/python3`'s pip only, and a venv's pip reads its own prefix's.
 * The global files (`/etc/pip.conf`) would reach the venv too.
 *
 * Written only when missing: once there it is the Space's own file to edit (ADR-0073).
 *
 * ```
 * SpacePipConfig.ensure(spaceDirIn(spacesDir, space.id))
 * ```
 */
object SpacePipConfig {
  private const val PIP_CONF_PATH = "usr/pip.conf"

  /** Writes `usr/pip.conf` under [spaceDir] unless a file is already there. */
  fun ensure(spaceDir: File) {
    val pipConf = File(spaceDir, PIP_CONF_PATH)
    if (pipConf.exists()) return
    pipConf.parentFile?.mkdirs()
    pipConf.writeText("[install]\nuser = true\n")
  }
}
