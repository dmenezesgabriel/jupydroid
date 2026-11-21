package com.jupydroid.app.domain.model

/**
 * Whether pip, installing for [target], would pick the wheel named [fileName].
 *
 * The rule `installable()` in `scripts/read_wheel_platforms.py` counts PyPI with: the Python tag
 * matches, or is `py3`, or is an `abi3` wheel of an older CPython; the platform is `any` or a
 * `<family>_…_<arch>` tag of [target]. A `musllinux` wheel is not one for the kernel - it is built
 * for the Space's musl interpreter, which cells do not run (ADR-0166).
 *
 * Total rather than throwing: the names come from PyPI's index, and one this app did not expect is
 * a wheel it cannot install rather than a failure in the path that draws a cell's output.
 *
 * ```
 * wheelFitsTarget("pkg-1.0-cp312-abi3-manylinux_2_17_aarch64.whl", WheelTarget.KERNEL)   // true
 * ```
 */
fun wheelFitsTarget(fileName: String, target: WheelTarget): Boolean {
  val parts = wheelPartsOf(fileName) ?: return false
  val (python, abi, platform) = parts.takeLast(WHEEL_TAG_COUNT)
  return pythonFits(python, abi, target.pythonTag) && platformFits(platform, target)
}

/**
 * The platform tags [fileName] carries, dot-split - what a refusal names when it says what a
 * package ships instead - or empty when the name is not a wheel.
 */
internal fun platformTagsOf(fileName: String): List<String> = wheelPartsOf(fileName)?.last()?.split(".").orEmpty()

/** PEP 427's five parts, or six with a build number, or null when [fileName] is not a wheel. */
private fun wheelPartsOf(fileName: String): List<String>? {
  if (!fileName.endsWith(".whl")) return null
  return fileName.removeSuffix(".whl").split("-").takeIf { it.size in WHEEL_PART_COUNTS }
}

/** Five parts, or six with a build number. */
private val WHEEL_PART_COUNTS = 5..6

/** A wheel's last three parts are its Python tag, its ABI tag and its platform tag. */
private const val WHEEL_TAG_COUNT = 3

/**
 * Whether the wheel's Python tags cover [wanted]: the same tag, a `py3` wheel, or an `abi3` wheel of
 * an older CPython - which is what `cp311-abi3` means to a `cp312` interpreter.
 */
private fun pythonFits(python: String, abi: String, wanted: String): Boolean {
  val tags = python.split(".")
  if (wanted in tags || "py3" in tags) return true
  return abi == "abi3" && tags.any { tag -> tag.isAbi3For(wanted) }
}

/** Whether an `abi3` tag of an older CPython - `cp311` for `cp312` - runs on [wanted]. */
private fun String.isAbi3For(wanted: String): Boolean {
  val wantedMinor = wanted.minorOfCpythonTag() ?: return false
  return minorOfCpythonTag()?.let { it <= wantedMinor } == true
}

/** The minor version of a tag like `cp312` (12) or `cp39` (9), or null for anything else. */
private fun String.minorOfCpythonTag(): Int? = removePrefix("cp").drop(1).toIntOrNull()

/** Whether any of the dot-joined platform tags is `any`, or of [target]'s family and architecture. */
private fun platformFits(platform: String, target: WheelTarget): Boolean = platform.split(".").any { tag ->
  tag == "any" ||
    (tag.startsWith("${target.platformFamily}_") && tag.endsWith("_${target.architecture}"))
}
