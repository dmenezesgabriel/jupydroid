package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * **A startup script that outlives its timeout is killed with everything it started**, in base's
 * own image under the real proot - which no JVM test can stand in for, since both the bound
 * (busybox `timeout`) and the sweep (proot's `--kill-on-exit`) live on the device.
 *
 * The script backgrounds one `sleep` and waits on another, the shape of a `pip install` left
 * running. Each `sleep` takes a length no other process uses, so `/proc` can be searched for
 * leftovers by argv even after they would have been orphaned. The second test is the control:
 * without `--kill-on-exit` proot waits for the backgrounded `sleep`, which is why the flag is there.
 *
 * ```
 * adb shell am instrument -w -e class com.jupydroid.app.data.space.SpaceStartupScriptDeviceTest \
 *   com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SpaceStartupScriptDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val logDir by lazy { File(context.cacheDir, "startup-script-probe").apply { mkdirs() } }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  @Test
  fun aScriptPastItsTimeoutIsKilledWithWhatItStartedAndExits137() {
    val clock = TimeSource.Monotonic.markNow()
    val process = startupScriptProcess(baseSpaceRuntime(context), logDir, script(6101), timeout = 2.seconds).start()

    val exited = process.waitFor(BACKSTOP_SECONDS, TimeUnit.SECONDS)

    Log.i(TAG, "bounded script: exited=$exited status=${process.exitValueOrNull()} after ${clock.elapsedNow()}")
    assertTrue("proot still waiting ${clock.elapsedNow()} after a 2 s timeout", exited)
    assertEquals(137, process.exitValue())
    assertEquals(emptyList<LiveProcess>(), sleepsOf(6101))
  }

  @Test
  fun withoutKillOnExitProotWaitsForWhatTheScriptLeftRunning() {
    val unswept = baseSpaceRuntime(context).execProcessBuilder(startupScriptArgs(script(6102), timeout = 2.seconds))
    val process = unswept.redirectErrorStream(true).redirectOutput(File(logDir, "unswept.log")).start()

    val exited = process.waitFor(BACKSTOP_SECONDS, TimeUnit.SECONDS)

    Log.i(TAG, "unswept script: exited=$exited, leftovers=${sleepsOf(6102)}")
    assertFalse("proot exited although a backgrounded sleep was still running", exited)
    // SIGKILL to proot orphans the sleeps rather than ending them, so each is killed by pid too.
    process.destroyForcibly()
    sleepsOf(6102).forEach { android.os.Process.killProcess(it.pid) }
  }

  @Test
  fun aScriptsOwnExitStatusComesBackThroughProot() {
    val process = startupScriptProcess(
      baseSpaceRuntime(context),
      logDir,
      "echo nope; exit 3",
      timeout = 2.seconds,
    ).start()

    assertTrue(process.waitFor(BACKSTOP_SECONDS, TimeUnit.SECONDS))
    assertEquals(3, process.exitValue())
  }

  /** Backgrounds one `sleep` and waits on another, both [seconds] long. */
  private fun script(seconds: Int) = "sleep $seconds & sleep $seconds"

  private fun sleepsOf(seconds: Int): List<LiveProcess> = liveProcesses().filter { it.command == "sleep $seconds" }

  private fun Process.exitValueOrNull(): Int? = runCatching { exitValue() }.getOrNull()

  private companion object {
    const val TAG = "JupydroidSpace"

    /** Twice the 2 s bound, plus proot's own start - long enough that a miss is not a slow device. */
    const val BACKSTOP_SECONDS = 8L
  }
}
