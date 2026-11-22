package com.jupydroid.app.domain.model

/**
 * The wheel tags a Space's kernel pip installs for: one Python tag, one platform family, one
 * architecture.
 *
 * A Space's cells run on the gnu/glibc interpreter beside the musl rootfs (ADR-0166), and its pip
 * installs wheels only - no C toolchain in the image (`tools/rootfs/Dockerfile`),
 * `PIP_ONLY_BINARY=:all:` on every process (`SpaceRuntime.environment`) - so a wheel must carry
 * all three tags to be installable at all. `scripts/read_wheel_platforms.py` counts PyPI with the
 * same three. The musl interpreter's pip is a different target: a `musllinux` wheel fits it and
 * not this one.
 *
 * ```
 * wheelFitsTarget("pkg-1.0-cp312-abi3-manylinux_2_17_aarch64.whl", WheelTarget.KERNEL)   // true
 * ```
 */
data class WheelTarget(
  /** The CPython tag, e.g. `cp312`; [cpythonTagOf] derives it from an interpreter version. */
  val pythonTag: String,
  /** The platform family a Space's kernel can run: `manylinux`, never `musllinux`, which is built for musl. */
  val platformFamily: String,
  /** The machine, as a wheel tag spells it. */
  val architecture: String,
) {
  companion object {
    /** What every Space's kernel pip installs for, its interpreter taken from [BUNDLED_PYTHON_VERSION]. */
    val KERNEL: WheelTarget = WheelTarget(cpythonTagOf(BUNDLED_PYTHON_VERSION), "manylinux", "aarch64")

    /** `3.12` as a wheel's Python tag: `cp312`. */
    fun cpythonTagOf(version: String): String = "cp" + version.replace(".", "")
  }
}
