package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * **A script runs in a Space's own shell, bounded, its output streamed** - the port the settings
 * section installs a harness through, proven end to end against the real proot where no JVM test
 * can stand in: the bound (busybox `timeout`) and the sweep (proot's `--kill-on-exit`) live on the
 * device (`SpaceStartupScriptDeviceTest` proves the same mechanism for the startup script).
 *
 * The probe tests are the section's own question: a catalog harness's `probeScript` runs in base
 * and answers what `presenceFromProbe` reads - `NotInstalled` for one the image lacks,
 * `Installed` for `python3`, which it ships.
 *
 * ```
 * adb shell am instrument -w -e class com.jupydroid.app.data.space.ProotSpaceShellDeviceTest \
 *   com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ProotSpaceShellDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  private val shell by lazy {
    spaceShell(context, DiskSpaceRepository(spacesDir(context), spaceImageInstaller(context)))
      ?: error("spaceShell is null on arm64")
  }

  /** The section's question end to end: the catalog probe execs in base and finds nothing. */
  @Test
  fun aHarnessProbeRunsInTheSpaceAndAnswersNotInstalled() = runBlocking {
    val harness = CHAT_HARNESS_CATALOG.first()
    val events = shell.run(BASE_SPACE.id, harness.probeScript(), 1.minutes).toList()

    val lines = events.outputLines()
    Log.i(TAG, "probe ${harness.id}: ${lines.joinToString(" | ")}; last=${events.last()}")
    assertEquals(SpaceShellEvent.Finished(0), events.last())
    assertEquals(HarnessPresence.NotInstalled, harness.presenceFromProbe(lines))
  }

  /** The found path: `python3` ships in the image, so its probe must answer Installed with a 3.x. */
  @Test
  fun aProbeFindsWhatTheImageShips() = runBlocking {
    val python = ChatHarness(id = "python3", name = "Python", executable = "python3")
    val events = shell.run(BASE_SPACE.id, python.probeScript(), 1.minutes).toList()

    val lines = events.outputLines()
    val presence = python.presenceFromProbe(lines)
    Log.i(TAG, "python3 probe: ${lines.joinToString(" | ")}; presence=$presence")
    assertTrue("$presence is not Installed: $lines", presence is HarnessPresence.Installed)
    assertEquals(3, (presence as HarnessPresence.Installed).version?.major)
  }

  /** What the section shows while an install runs: the lines, stderr merged, then the status. */
  @Test
  fun outputLinesAndTheExitStatusComeBack() = runBlocking {
    val events = shell.run(BASE_SPACE.id, "echo one; echo two >&2; exit 3", 1.minutes).toList()
    val lines = events.outputLines()

    assertTrue("stdout missing: $lines", "one" in lines)
    assertTrue("stderr missing: $lines", "two" in lines)
    assertEquals(SpaceShellEvent.Finished(3), events.last())
  }

  /** The port's own bound: a silent `sleep` is killed at 2 s and reports 137, nothing left running. */
  @Test
  fun aScriptPastItsTimeoutIsKilled() {
    val clock = TimeSource.Monotonic.markNow()
    val events = runBlocking {
      shell.run(BASE_SPACE.id, "sleep 3033", timeout = 2.seconds).toList()
    }

    Log.i(TAG, "bounded run: last=${events.last()} after ${clock.elapsedNow()}")
    assertEquals(SpaceShellEvent.Finished(137), events.last())
    assertEquals(emptyList<LiveProcess>(), liveProcesses().filter { it.command == "sleep 3033" })
    assertTrue("the bound took ${clock.elapsedNow()}", clock.elapsedNow() < 15.seconds)
  }

  private fun List<SpaceShellEvent>.outputLines(): List<String> =
    filterIsInstance<SpaceShellEvent.Output>().map { it.line }

  private companion object {
    const val TAG = "JupydroidSpace"
  }
}
