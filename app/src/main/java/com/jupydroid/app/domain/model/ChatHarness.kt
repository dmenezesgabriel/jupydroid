package com.jupydroid.app.domain.model

/**
 * A version three numbers deep, `X.Y.Z`, as an agent harness's `--version` prints one: the first
 * `\d+\.\d+\.\d+` the output carries, which is the only shape the ACP personas' requirement checks
 * read (`re.search(r"(\d+\.\d+\.\d+)", result.stdout)` in
 * `usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.py`, shipped in the
 * rootfs).
 *
 * Ordered numerically component by component, so `1.9.9` precedes `1.19.3` where a string compare
 * would not - the persona's own tuple comparison does the same.
 *
 * ```
 * HarnessVersion(1, 19, 3).toString()   // "1.19.3"
 * ```
 */
data class HarnessVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<HarnessVersion> {
  override fun compareTo(other: HarnessVersion): Int =
    compareValuesBy(this, other, HarnessVersion::major, HarnessVersion::minor, HarnessVersion::patch)

  override fun toString(): String = "$major.$minor.$patch"
}

/**
 * The versions of a harness its persona accepts: `minimum` inclusive, `maximumExclusive` when the
 * persona caps the line - opencode's `_check_opencode` refuses `v < (1,0,0)` or `v[0] >= 2`, which
 * for a three-component version is exactly `>=1.0.0,<2.0.0`.
 *
 * ```
 * HarnessVersionRange(HarnessVersion(1, 0, 0), HarnessVersion(2, 0, 0)).contains(HarnessVersion(1, 19, 3))  // true
 * ```
 */
data class HarnessVersionRange(val minimum: HarnessVersion, val maximumExclusive: HarnessVersion? = null) {
  fun contains(version: HarnessVersion): Boolean {
    val ceiling = maximumExclusive
    return version >= minimum && (ceiling == null || version < ceiling)
  }
}

/**
 * An agent CLI a Space's Chat can spawn, installable from the Space's settings: what the section
 * lists, probes and installs.
 *
 * [id] keys the shipped install script (`assets/chat-harnesses/<id>.sh`); [executable] is the file
 * `command -v` finds on the Space's PATH and `--version` answers for; [name] is what a person
 * reads, matching the persona's own `defaults.name`.
 *
 * [requiredVersion] is the persona's own requirement - null where the persona checks presence
 * alone, as claude's `shutil.which("claude-agent-acp")` does
 * (`usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/claude.py` in the shipped
 * rootfs); opencode's also reads `--version` and refuses outside its range
 * (`.../acp_personas/opencode.py`).
 */
data class ChatHarness(
  val id: String,
  val name: String,
  val executable: String,
  val requiredVersion: HarnessVersionRange? = null,
)

/**
 * What a harness probe found in a Space - the answer the settings section draws, and the same
 * question the persona's entry-point check asks when the server next loads its personas.
 */
sealed interface HarnessPresence {
  /** `command -v` found no executable on the Space's PATH - the persona's `which` check fails. */
  data object NotInstalled : HarnessPresence

  /**
   * Found, and everything the persona's check requires holds, so the persona will list the
   * harness. [version] is what a clean `--version` printed, null when it printed no `X.Y.Z`.
   */
  data class Installed(val version: HarnessVersion?) : HarnessPresence

  /**
   * Found, but the persona's check would refuse it: no readable in-range version where the persona
   * requires one (`_check_opencode`,
   * `usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.py` in the shipped
   * rootfs). [version] is the trusted reading - null when `--version` failed or printed none.
   */
  data class Incompatible(val version: HarnessVersion?) : HarnessPresence
}

/**
 * The harnesses the app offers to install, in the order the section lists them. An entry joins
 * only after that harness has run on the tablet - "compatible with the Chat" is a measurement,
 * not a claim.
 */
val CHAT_HARNESS_CATALOG: List<ChatHarness> = listOf(
  ChatHarness(
    id = "opencode",
    name = "OpenCode",
    executable = "opencode",
    requiredVersion = HarnessVersionRange(
      minimum = HarnessVersion(1, 0, 0),
      maximumExclusive = HarnessVersion(2, 0, 0),
    ),
  ),
  ChatHarness(
    id = "claude-agent-acp",
    name = "Claude",
    executable = "claude-agent-acp",
  ),
)
