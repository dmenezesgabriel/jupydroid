package com.jupydroid.app.data.space

import java.io.File

/**
 * Makes the gnu payload's own pip install into the kernel's user base, and leaves a venv's pip
 * alone - the same argument [SpacePipConfig] makes for the musl side.
 *
 * pip's SITE config file is `<sys.prefix>/pip.conf` (`pip/_internal/configuration.py` in the
 * pinned rootfs), and the payload's interpreter has `sys.prefix` `/opt/gnu/python`, so
 * `opt/gnu/python/pip.conf` is read by the kernel's `%pip` only: `user = true` grounds installs
 * in `PYTHONUSERBASE` (`/pkgs-gnu`, [SpaceKernelSpec.DEFAULT_KERNEL_ENV]) unflagged. The musl
 * image's `usr/pip.conf` is never read here - a different prefix - and a venv's pip reads its
 * own prefix's, so the two user bases cannot contaminate each other (ADR-0166).
 *
 * Written only when missing: once there it is the Space's own file to edit (ADR-0073).
 *
 * ```
 * GnuPipConfig.ensure(spaceDirIn(spacesDir, space.id))
 * ```
 */
object GnuPipConfig {
  private const val PIP_CONF_PATH = "opt/gnu/python/pip.conf"

  /** Writes `opt/gnu/python/pip.conf` under [spaceDir] unless a file is already there. */
  fun ensure(spaceDir: File) {
    val pipConf = File(spaceDir, PIP_CONF_PATH)
    if (pipConf.exists()) return
    pipConf.parentFile?.mkdirs()
    pipConf.writeText("[install]\nuser = true\n")
  }
}
