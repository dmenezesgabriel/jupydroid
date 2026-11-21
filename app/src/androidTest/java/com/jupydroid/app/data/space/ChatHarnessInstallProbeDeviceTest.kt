package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.asset.AssetHarnessScripts
import com.jupydroid.app.data.disk.DiskSpaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CHAT_HARNESS_CATALOG
import com.jupydroid.app.domain.model.ChatHarness
import com.jupydroid.app.domain.model.HarnessPresence
import com.jupydroid.app.domain.model.presenceFromProbe
import com.jupydroid.app.domain.model.probeScript
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * **What each catalog harness's install costs a Space**: the cold duration the settings
 * section's install bound is set from, and the disk the install adds - the "what it adds"
 * size. Each harness's shipped script runs through `SpaceShell` - the port the section will
 * call - on a base reset to a cold state first, so the number is the first install's, not a
 * warm re-run's.
 *
 * The disk delta is `du -skx /` before and after: `-x` keeps the `/proc` and `/dev` proot
 * binds out of the count (`ProotCommand.argv` in
 * `app/src/main/java/com/jupydroid/app/data/space/ProotCommand.kt`), and everything else
 * under `/` is the Space's own directory, so the difference is exactly what the install put
 * there - the binary, the apk'd runtime, `node_modules`, the npm cache.
 *
 * `@After` removes both harnesses again: the app ships no uninstall, and the section's demo
 * needs base to read not-installed. A harness that joins the catalog gets its own measured
 * test here - "compatible" is a measurement, not a claim.
 *
 * ```
 * DEVJOB_MAX_SEC=7200 devjob ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ChatHarnessInstallProbeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ChatHarnessInstallProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /** Best effort: a failed cleanup is reported by the next run's own reset, not hidden here. */
  @After
  fun leaveBaseWithoutHarnesses() {
    runCatching {
      runBlocking {
        shell.run(BASE_SPACE.id, OPENCODE_RESET, RESET_BOUND).toList()
        shell.run(BASE_SPACE.id, CLAUDE_AGENT_ACP_RESET, RESET_BOUND).toList()
      }
    }
  }

  private val shell by lazy {
    spaceShell(context, DiskSpaceRepository(spacesDir(context), spaceImageInstaller(context)))
      ?: error("spaceShell is null on arm64")
  }

  private val scripts by lazy { AssetHarnessScripts(context.assets::open) }

  /** opencode's cold install: the pinned musl tarball fetched into `/usr/local/bin`. */
  @Test(timeout = 2_700_000)
  fun opencodeInstallIsMeasured() {
    publish("opencode", measureInstall(harness("opencode"), OPENCODE_RESET))
  }

  /** claude's cold install first adds Alpine's `nodejs` and `npm` through `apk` - the longer arm. */
  @Test(timeout = 2_700_000)
  fun claudeAgentAcpInstallIsMeasured() {
    publish("claude-agent-acp", measureInstall(harness("claude-agent-acp"), CLAUDE_AGENT_ACP_RESET))
  }

  /**
   * [harness]'s shipped script, run cold in base through `SpaceShell`: reset, weigh, timed
   * run, weigh, probe - returning the report lines the run printed to logcat and file.
   */
  private fun measureInstall(harness: ChatHarness, resetScript: String): String {
    runReset(resetScript)
    val beforeKiB = spaceKilobytes()
    val begin = TimeSource.Monotonic.markNow()
    val events = runBlocking {
      shell.run(BASE_SPACE.id, scripts.installScript(harness), INSTALL_BOUND).toList()
    }
    val elapsed = begin.elapsedNow()
    assertInstallSucceeded(events)
    val addedKiB = spaceKilobytes() - beforeKiB
    val presence = probePresence(harness)
    return reportOf(harness, elapsed, addedKiB, presence, events.last())
  }

  /** The cold state the measurement needs: what a previous install or probe left, gone. */
  private fun runReset(script: String) {
    val events = runBlocking { shell.run(BASE_SPACE.id, script, RESET_BOUND).toList() }
    assertEquals(
      "reset did not exit 0 (last=${events.last()}); lines: ${events.outputLines().takeLast(OUTPUT_TAIL)}",
      SpaceShellEvent.Finished(0),
      events.last(),
    )
  }

  private fun assertInstallSucceeded(events: List<SpaceShellEvent>) {
    assertTrue(
      "install did not exit 0 (last=${events.last()}); tail: " +
        events.outputLines().takeLast(OUTPUT_TAIL).joinToString(" | "),
      events.last() == SpaceShellEvent.Finished(0),
    )
  }

  /** The persona's own question after the install: the probe must now answer Installed. */
  private fun probePresence(harness: ChatHarness): HarnessPresence {
    val events = runBlocking { shell.run(BASE_SPACE.id, harness.probeScript(), PROBE_BOUND).toList() }
    val presence = harness.presenceFromProbe(events.outputLines())
    check(presence is HarnessPresence.Installed) {
      "probe after install answered $presence for ${harness.id}; lines: ${events.outputLines()}"
    }
    return presence
  }

  /** The Space's disk in KiB - `du`'s `<kib>\t/` line, first field. */
  private fun spaceKilobytes(): Long {
    val events = runBlocking { shell.run(BASE_SPACE.id, DU_SCRIPT, DU_BOUND).toList() }
    val line = events.outputLines().lastOrNull { it.isNotBlank() }
      ?: error("du printed no lines: ${events.takeLast(3)}")
    return line.trim().split(WHITESPACE).first().toLongOrNull()
      ?: error("du's last line '$line' is not '<kib> <path>': expected `du -skx /` output")
  }

  private fun reportOf(
    harness: ChatHarness,
    elapsed: Duration,
    addedKiB: Long,
    presence: HarnessPresence,
    finished: SpaceShellEvent,
  ): String = buildString {
    appendLine("== chat harness install measurement")
    appendLine("device: ${Build.MODEL}")
    appendLine("harness-id: ${harness.id}")
    appendLine("install-ms: ${elapsed.inWholeMilliseconds}")
    appendLine("added-kib: $addedKiB")
    appendLine("probe: $presence")
    appendLine("install-exit: $finished")
  }

  private fun publish(harnessId: String, report: String) {
    File(context.getExternalFilesDir(null), "chat-harness-install-$harnessId.txt").writeText(report)
    Log.i(SPACE_LOG_TAG, report)
  }

  private fun harness(id: String): ChatHarness = CHAT_HARNESS_CATALOG.firstOrNull { it.id == id }
    ?: error("no catalog harness with id '$id'; catalog: ${CHAT_HARNESS_CATALOG.map { it.id }}")

  private companion object {
    /** Above the scripts' own worst case - `apk` 600 s + `npm` 1200 s - so a slow install reports its own exit, not 137. */
    val INSTALL_BOUND = 35.minutes

    val RESET_BOUND = 2.minutes
    val DU_BOUND = 5.minutes
    val PROBE_BOUND = 1.minutes

    const val DU_SCRIPT = "du -skx /"
    const val OUTPUT_TAIL = 5
    val WHITESPACE = Regex("\\s+")

    const val OPENCODE_RESET = "rm -f /usr/local/bin/opencode"

    /**
     * The adapter, the apk'd runtime and the npm cache gone, so the measured run includes the
     * `apk add nodejs npm` arm - the worst case the install bound must cover. `npm uninstall`
     * runs while npm still exists; `apk del` order matters because npm depends on nodejs.
     */
    val CLAUDE_AGENT_ACP_RESET = """
      if command -v npm >/dev/null 2>&1; then
        npm uninstall -g @agentclientprotocol/claude-agent-acp >/dev/null 2>&1 || true
      fi
      apk del npm >/dev/null 2>&1 || true
      apk del nodejs >/dev/null 2>&1 || true
      rm -rf "${'$'}HOME/.npm"
    """.trimIndent()
  }
}
