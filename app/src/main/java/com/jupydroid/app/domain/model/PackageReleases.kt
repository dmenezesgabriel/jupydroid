package com.jupydroid.app.domain.model

/**
 * A PyPI package as its own index lists it: every version it published, and the wheel filenames
 * each published - a version with no wheel at all kept as an empty list, so "publishes source only"
 * is a fact this holds rather than one it infers from a missing key.
 *
 * ```
 * PackageReleases("2.0.1", mapOf("2.0.1" to listOf("duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.whl")))
 * ```
 */
data class PackageReleases(val latestVersion: String, val wheelNamesByVersion: Map<String, List<String>>)

/**
 * [name] as PyPI itself compares it: lower case, and every run of `-`, `_` and `.` read as one `-`
 * (PEP 503). A requirement pip echoes keeps the spelling it was typed with, so `Scikit_Learn` and
 * `scikit-learn` have to find the same package.
 *
 * ```
 * normalizedPackageName("Scikit_Learn")   // "scikit-learn"
 * ```
 */
fun normalizedPackageName(name: String): String = name.lowercase().replace(PUNCTUATION_RUN, "-")

private val PUNCTUATION_RUN = Regex("[-_.]+")

/**
 * Why this Space's pip cannot install [packageName] at all, as the sentence a cell shows - or null
 * when [this] lists a release with a wheel pip would install, which is the answer for every package
 * that installs.
 *
 * The claim is made only when PyPI's release files confirm it (ADR-0165), because pip's own text
 * says the same thing for a name that exists nowhere.
 *
 * ```
 * releases.platformRefusal("muslonly", WheelTarget.KERNEL)   // "muslonly ships no manylinux aarch64 wheel …"
 * ```
 */
fun PackageReleases.platformRefusal(packageName: String, target: WheelTarget): String? = when {
  wheelNamesByVersion.values.any { names -> names.any { wheelFitsTarget(it, target) } } -> null
  wheelNamesByVersion.values.all { it.isEmpty() } -> sourceOnlyRefusal(packageName)
  else ->
    "$packageName ships no ${target.platform} wheel for Python ${target.pythonVersion} in any release, and " +
      "this Space's pip installs wheels only, so pip cannot install it here.${latestReleaseSentence(target)}"
}

/** The case psycopg2 and mysqlclient are in: an sdist for every release, and no wheel for anyone. */
private fun sourceOnlyRefusal(packageName: String): String =
  "$packageName publishes source only - no wheel in any release - and this Space's pip installs wheels only, " +
    "so pip cannot install it here."

/** What the latest release does ship for this machine, or that it ships nothing for it. */
private fun PackageReleases.latestReleaseSentence(target: WheelTarget): String {
  val shipped = wheelNamesByVersion[latestVersion].orEmpty()
  val forMachine = shipped.flatMap(::platformTagsOf)
    .filter { it.endsWith("_${target.architecture}") }
    .distinct()
    .sorted()
  return when {
    shipped.isEmpty() -> " Latest release $latestVersion ships no wheel at all."
    forMachine.isEmpty() -> " Latest release $latestVersion ships no wheel for ${target.architecture} at all."
    else -> " Latest release $latestVersion ships ${forMachine.joinToString(", ")} instead${muslClause(forMachine)}."
  }
}

/**
 * Said only when every tag named is a musl one: a `musllinux` wheel fits the Space's musl
 * interpreter but not its glibc kernel, and a wheel of another family named without a reason
 * reads as an unfinished sentence, where a wrong reason would be worse.
 */
private fun muslClause(tags: List<String>): String = if (tags.all { it.startsWith("musllinux") }) {
  ", built for musl, which the Space's kernel does not run"
} else {
  ""
}

/** `manylinux aarch64`, the platform a refusal names. */
private val WheelTarget.platform: String
  get() = "$platformFamily $architecture"

/** `cp312` as a person reads it: `3.12`. */
private val WheelTarget.pythonVersion: String
  get() = pythonTag.removePrefix("cp").let { "${it.first()}.${it.drop(1)}" }
