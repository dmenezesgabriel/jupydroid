package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHarnessProbeTest {
  private val opencode = CHAT_HARNESS_CATALOG.first { it.id == "opencode" }
  private val claude = CHAT_HARNESS_CATALOG.first { it.id == "claude-agent-acp" }

  /**
   * The script asks the persona's own two questions in the Space's shell: `command -v` for the
   * `which` check, then `<executable> --version` bounded - `_check_opencode`'s
   * `usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.py` in the
   * shipped rootfs.
   */
  @Test
  fun probeScript_asksWhichThenVersionForTheHarnesssExecutable() {
    val script = opencode.probeScript()
    assertTrue(script.contains("command -v opencode"))
    assertTrue(script.contains("opencode --version"))
    assertTrue(script.contains("timeout"))
    val claudeScript = claude.probeScript()
    assertTrue(claudeScript.contains("command -v claude-agent-acp"))
    assertTrue(claudeScript.contains("claude-agent-acp --version"))
  }

  /** The whole script, byte for byte: the markers are the contract [presenceFromProbe] reads back. */
  @Test
  fun probeScript_isTheScriptByteForByte() {
    assertEquals(
      """
      #!/bin/sh
      path=${'$'}(command -v opencode 2>/dev/null)
      if [ -z "${'$'}path" ]; then
        echo 'harness-probe:absent'
        exit 0
      fi
      echo "harness-probe:path=${'$'}path"
      timeout -s KILL 15 opencode --version 2>&1
      rc=$?
      echo
      echo "harness-probe:version-rc=${'$'}rc"
      """.trimIndent(),
      opencode.probeScript(),
    )
  }

  /** `command -v` finding nothing is the whole answer: the persona's `which` check fails. */
  @Test
  fun presenceFromProbe_findsNoExecutable_isNotInstalled() {
    assertEquals(HarnessPresence.NotInstalled, opencode.presenceFromProbe(listOf("harness-probe:absent")))
  }

  /**
   * No `harness-probe:` markers at all, or a `path=` with no closing `version-rc=`, means the
   * probe never answered - which is not the same as answering "not installed".
   */
  @Test
  fun presenceFromProbe_probeDidNotAnswer_isNull() {
    assertNull(opencode.presenceFromProbe(emptyList()))
    assertNull(opencode.presenceFromProbe(listOf("noise", "more noise")))
    assertNull(
      opencode.presenceFromProbe(
        listOf("harness-probe:path=/usr/local/bin/opencode", "1.19.3"),
      ),
    )
  }

  /** The executable answered `--version` cleanly: installed, with the version it printed. */
  @Test
  fun presenceFromProbe_versionAnswered_isInstalledWithIt() {
    val presence = opencode.presenceFromProbe(
      listOf(
        "harness-probe:path=/usr/local/bin/opencode",
        "1.19.3",
        "",
        "harness-probe:version-rc=0",
      ),
    )
    assertEquals(HarnessPresence.Installed(HarnessVersion(1, 19, 3)), presence)
  }

  /**
   * The persona's `re.search(r"(\d+\.\d+\.\d+)")` takes the leftmost match, so the first
   * `X.Y.Z` in the output is the version - here `2.0.0`, not the build stamp's `2024.11.21`.
   */
  @Test
  fun presenceFromProbe_takesTheLeftmostSemver() {
    val presence = opencode.presenceFromProbe(
      listOf(
        "harness-probe:path=/usr/local/bin/opencode",
        "opencode 2.0.0 build 2024.11.21.5",
        "harness-probe:version-rc=0",
      ),
    )
    assertEquals(HarnessPresence.Incompatible(HarnessVersion(2, 0, 0)), presence)
  }

  /** The `path=` line is the probe's own, not version output: digits in it are never read. */
  @Test
  fun presenceFromProbe_digitsInThePathLineAreNotTheVersion() {
    val presence = claude.presenceFromProbe(
      listOf(
        "harness-probe:path=/opt/1.2.3/bin/claude-agent-acp",
        "0.97.0",
        "harness-probe:version-rc=0",
      ),
    )
    assertEquals(HarnessPresence.Installed(HarnessVersion(0, 97, 0)), presence)
  }

  /** The persona raises on a non-zero `--version` before it ever reads stdout, so neither do we. */
  @Test
  fun presenceFromProbe_failedVersionRun_isNotTrusted() {
    val presence = opencode.presenceFromProbe(
      listOf(
        "harness-probe:path=/usr/local/bin/opencode",
        "1.19.3",
        "harness-probe:version-rc=1",
      ),
    )
    assertEquals(HarnessPresence.Incompatible(null), presence)
  }

  /** Inside `>=1.0.0,<2` the persona lists the harness; its own floor and a mid-range build hold. */
  @Test
  fun presenceFromProbe_opencodeInsideItsRange_isInstalled() {
    assertEquals(
      HarnessPresence.Installed(HarnessVersion(1, 0, 0)),
      opencode.presenceFromProbe(installedOutput("1.0.0")),
    )
    assertEquals(
      HarnessPresence.Installed(HarnessVersion(1, 19, 3)),
      opencode.presenceFromProbe(installedOutput("opencode 1.19.3")),
    )
  }

  /** Below the floor or at the ceiling the persona refuses at import, so it is not "Installed". */
  @Test
  fun presenceFromProbe_opencodeOutsideItsRange_isIncompatible() {
    assertEquals(
      HarnessPresence.Incompatible(HarnessVersion(0, 9, 9)),
      opencode.presenceFromProbe(installedOutput("0.9.9")),
    )
    assertEquals(
      HarnessPresence.Incompatible(HarnessVersion(2, 0, 0)),
      opencode.presenceFromProbe(installedOutput("2.0.0")),
    )
    assertEquals(
      HarnessPresence.Incompatible(HarnessVersion(2, 0, 1)),
      opencode.presenceFromProbe(installedOutput("2.0.1")),
    )
  }

  /** Found but no readable version is still a refusal for a persona that requires one. */
  @Test
  fun presenceFromProbe_opencodeWithNoReadableVersion_isIncompatible() {
    assertEquals(HarnessPresence.Incompatible(null), opencode.presenceFromProbe(installedOutput("no version here")))
    assertEquals(HarnessPresence.Incompatible(null), opencode.presenceFromProbe(installedOutput("1.19.3", rc = 1)))
  }

  /** A version component too large for an Int is no version at all, not a crash in the parse. */
  @Test
  fun presenceFromProbe_versionComponentOverflowingInt_isNoVersion() {
    assertEquals(
      HarnessPresence.Incompatible(null),
      opencode.presenceFromProbe(installedOutput("99999999999.0.0.0")),
    )
  }

  /**
   * claude's persona checks `shutil.which` alone, so any answer - a version, none, even a failed
   * `--version` - leaves it installed; the version is what was found, never a requirement.
   */
  @Test
  fun presenceFromProbe_claudeIsPresenceOnly() {
    val installed = claude.presenceFromProbe(
      listOf("harness-probe:path=/usr/bin/claude-agent-acp", "0.97.0", "harness-probe:version-rc=0"),
    )
    assertEquals(HarnessPresence.Installed(HarnessVersion(0, 97, 0)), installed)
    val noVersion = claude.presenceFromProbe(
      listOf("harness-probe:path=/usr/bin/claude-agent-acp", "not a version", "harness-probe:version-rc=1"),
    )
    assertEquals(HarnessPresence.Installed(null), noVersion)
  }

  /** The verdict reads the harness's own range, not a hardcoded one: a `>=2,<3` harness refuses 1.x. */
  @Test
  fun presenceFromProbe_usesTheHarnesssOwnRange() {
    val future = ChatHarness(
      id = "future",
      name = "Future",
      executable = "future",
      requiredVersion = HarnessVersionRange(HarnessVersion(2, 0, 0), HarnessVersion(3, 0, 0)),
    )
    val accepted = future.presenceFromProbe(
      listOf("harness-probe:path=/usr/local/bin/future", "2.5.0", "harness-probe:version-rc=0"),
    )
    assertEquals(HarnessPresence.Installed(HarnessVersion(2, 5, 0)), accepted)
    val refused = future.presenceFromProbe(
      listOf("harness-probe:path=/usr/local/bin/future", "1.19.3", "harness-probe:version-rc=0"),
    )
    assertEquals(HarnessPresence.Incompatible(HarnessVersion(1, 19, 3)), refused)
  }

  /** The shape `probeScript` writes when the executable answers: path, its `--version` text, rc. */
  private fun installedOutput(versionText: String, rc: Int = 0): List<String> = listOf(
    "harness-probe:path=/usr/local/bin/opencode",
    versionText,
    "",
    "harness-probe:version-rc=$rc",
  )
}
