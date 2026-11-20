package com.jupydroid.app.domain.model

/**
 * The wheel tags a Space's pip installs for: one Python tag, one platform family, one architecture.
 *
 * A Space is Alpine on musl for arm64 (ADR-0003), and its pip installs wheels only - no C toolchain
 * in the image (`tools/rootfs/Dockerfile`), `PIP_ONLY_BINARY=:all:` on every process
 * (`SpaceRuntime.environment`) - so a wheel must carry all three tags to be installable at all.
 * `scripts/read_wheel_platforms.py` counts PyPI with the same three.
 *
 * ```
 * wheelFitsTarget("pandas-3.1.1-cp312-cp312-musllinux_1_2_aarch64.whl", WheelTarget.SPACE)   // true
 * ```
 */
data class WheelTarget(
  /** The CPython tag, e.g. `cp312`; [cpythonTagOf] derives it from an interpreter version. */
  val pythonTag: String,
  /** The platform family a Space can run: `musllinux`, never `manylinux`, which is built for glibc. */
  val platformFamily: String,
  /** The machine, as a wheel tag spells it. */
  val architecture: String,
) {
  companion object {
    /** What every Space's pip installs for, its interpreter taken from [BUNDLED_PYTHON_VERSION]. */
    val SPACE: WheelTarget = WheelTarget(cpythonTagOf(BUNDLED_PYTHON_VERSION), "musllinux", "aarch64")

    /** `3.12` as a wheel's Python tag: `cp312`. */
    fun cpythonTagOf(version: String): String = "cp" + version.replace(".", "")
  }
}
