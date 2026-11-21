package com.jupydroid.app.domain.model

/**
 * The probe as a POSIX sh script for the Space's shell: the persona's own two checks - `command -v`
 * for `which`, then `<executable> --version` bounded by busybox `timeout` in the `timeout -s KILL`
 * shape `boundedShellArgs` uses (`app/src/main/java/com/jupydroid/app/data/space/BoundedSpaceShell.kt`).
 *
 * Lines starting `harness-probe:` are the probe's own; every other line is the executable's
 * `--version` output, which [presenceFromProbe] reads for a version. `absent` is the whole answer
 * when `command -v` finds nothing; otherwise `path=` names where it was found and `version-rc=`
 * closes the run - the bare `echo` before it keeps a `--version` that forgets its trailing newline
 * from merging into the marker.
 *
 * ```
 * ChatHarness(id = "opencode", ...).probeScript()
 * ```
 */
fun ChatHarness.probeScript(): String = """
  #!/bin/sh
  path=${'$'}(command -v $executable 2>/dev/null)
  if [ -z "${'$'}path" ]; then
    echo 'harness-probe:absent'
    exit 0
  fi
  echo "harness-probe:path=${'$'}path"
  timeout -s KILL $PROBE_VERSION_TIMEOUT_SECONDS $executable --version 2>&1
  rc=$?
  echo
  echo "harness-probe:version-rc=${'$'}rc"
""".trimIndent()

/**
 * What [lines] - the output of [probeScript] run in a Space - say about this harness.
 *
 * Null when the probe did not answer: no `absent` and no `path=` marker, or a `path=` with no
 * closing `version-rc=`. The persona raises on a non-zero `--version` before it reads stdout, so a
 * version is trusted only when `version-rc=0`; for a harness with a [ChatHarness.requiredVersion]
 * a missing or out-of-range version is [HarnessPresence.Incompatible], never "Installed".
 *
 * ```
 * opencode.presenceFromProbe(listOf("harness-probe:absent"))   // NotInstalled
 * ```
 */
fun ChatHarness.presenceFromProbe(lines: List<String>): HarnessPresence? {
  val marks = lines.filter { it.startsWith(PROBE_MARK_PREFIX) }
  val rc = completedRun(marks)
  return when {
    PROBE_ABSENT in marks -> HarnessPresence.NotInstalled
    rc == null -> null
    else -> verdictFor(lines, rc)
  }
}

/** The `--version` exit status, or null when the markers don't describe a finished run. */
private fun completedRun(marks: List<String>): Int? {
  val found = marks.any { it.startsWith(PROBE_PATH) }
  val rc = marks.firstOrNull { it.startsWith(PROBE_RC) }
    ?.removePrefix(PROBE_RC)
    ?.toIntOrNull()
  return if (found) rc else null
}

/** The persona's verdict on a finished probe run: installed or incompatible, by its own rules. */
private fun ChatHarness.verdictFor(lines: List<String>, rc: Int): HarnessPresence {
  val version = versionPrinted(lines, rc)
  val range = requiredVersion ?: return HarnessPresence.Installed(version)
  val accepted = version != null && range.contains(version)
  return if (accepted) HarnessPresence.Installed(version) else HarnessPresence.Incompatible(version)
}

/** The first `X.Y.Z` outside the probe's own lines, or null when `--version` did not exit 0. */
private fun versionPrinted(lines: List<String>, rc: Int): HarnessVersion? {
  if (rc != VERSION_OK) return null
  return lines.filterNot { it.startsWith(PROBE_MARK_PREFIX) }.firstNotNullOfOrNull(::firstVersionIn)
}

/**
 * The first `X.Y.Z` in [line], group for group - `re.search(r"(\d+\.\d+\.\d+)")`'s leftmost match.
 * A component too large for an `Int` reads as no version rather than failing the parse.
 */
private fun firstVersionIn(line: String): HarnessVersion? {
  val parts = FIRST_SEMVER.find(line)?.groupValues?.drop(1)?.mapNotNull(String::toIntOrNull).orEmpty()
  if (parts.size != VERSION_PARTS) return null
  val (major, minor, patch) = parts
  return HarnessVersion(major, minor, patch)
}

private const val PROBE_MARK_PREFIX = "harness-probe:"
private const val PROBE_ABSENT = "harness-probe:absent"
private const val PROBE_PATH = "harness-probe:path="
private const val PROBE_RC = "harness-probe:version-rc="
private const val PROBE_VERSION_TIMEOUT_SECONDS = 15
private const val VERSION_OK = 0
private const val VERSION_PARTS = 3
private val FIRST_SEMVER = Regex("(\\d+)\\.(\\d+)\\.(\\d+)")
