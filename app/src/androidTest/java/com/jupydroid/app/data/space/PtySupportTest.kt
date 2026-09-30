package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Whether this device can make a pseudoterminal, without which there is no terminal at all.
 *
 * **Instrumented rather than a JVM test, and on the tablet rather than the emulator**, because the
 * answer is a property of the kernel's seccomp filter for *this* architecture and of musl's
 * `forkpty`, neither of which a JVM test on the host can reach. `Assume`-skipped where no rootfs
 * ships, so it is green-by-skip on x86_64 instead of answering for the wrong libc.
 *
 * It stays: if a future Android release withdraws `/dev/ptmx` from apps, the
 * terminal stops working and this is what says so, rather than a shell that opens onto nothing.
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.PtySupportTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PtySupportTest {
  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, and a pty here would answer for the wrong libc",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  /** `pty.fork()` succeeds and the child's own writes come back on the master. */
  @Test
  fun aChildInAPseudoterminalIsHeard() {
    val probe = probe("child_is_heard")

    assertRaisedNothing(probe)
    assertOutputContains(probe, "child-speaks")
  }

  /**
   * What terminado actually does: the shell is `execv`'d *inside* the pty rather than forked into
   * it, so the probe reports `os.isatty(1)` from the other side of the exec.
   */
  @Test
  fun aProcessExecdInsideAPseudoterminalKeepsIt() {
    val probe = probe("exec_keeps_the_pty")

    assertRaisedNothing(probe)
    assertOutputContains(probe, "exec-in-pty True")
  }

  /** `TIOCSWINSZ` on the master is what `set_size` rides; it has to reach the process inside. */
  @Test
  fun aResizeOnTheMasterReachesTheProcessInside() {
    val probe = probe("resize_reaches_the_child")

    assertRaisedNothing(probe)
    assertOutputContains(probe, "winsize 132x43")
  }

  private fun assertRaisedNothing(probe: JsonObject) =
    assertEquals("the probe raised: $probe", JsonNull, probe.getValue("raised"))

  private fun assertOutputContains(probe: JsonObject, expected: String) {
    val output = probe.getValue("output").jsonPrimitive.content
    assertTrue("expected '$expected' on the master, read: ${output.replace("\r\n", "\\r\\n")}", expected in output)
  }

  private fun probe(name: String): JsonObject {
    assertEquals("the probe process failed: ${outcome.output}", 0, outcome.exitCode)
    // The last line, not the whole output: PythonRunner folds stderr in, so a warning from the
    // runtime would otherwise be parsed as the report.
    return Json.parseToJsonElement(outcome.output.trim().lines().last()).jsonObject.getValue(name).jsonObject
  }

  private companion object {
    /**
     * One run for all three questions. A fresh interpreter out of the Space costs seconds, and the
     * probes are independent of each other inside it - each forks its own pty and reaps its child.
     */
    val outcome: ProcessOutcome by lazy {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      runBlocking {
        baseSpaceInstaller(context).installIfNeeded()
        PythonRunner(baseSpaceRuntime(context)).run("-c", PTY_PROBE)
      }.also { reportForTheRecord(context, it) }
    }

    /**
     * The probe's own answer, on the tablet, under the tag the rest of the runtime reports under.
     *
     * **Logged rather than left to the assertions**, because `connectedAndroidTest` uninstalls both
     * APKs when it finishes and takes the Space with them - so `run-as` afterwards finds no package
     * and there is nothing left to pull. A green assertion says *that* a pty was made; this says
     * what came back, which is what a device gate has to hold, logcat included.
     *
     * The refusal log comes with it: `SpaceRuntime` sends every seccomp-refused syscall to
     * `tmp/sigsys.log` rather than stderr, so a pty that worked *while* something was refused would
     * otherwise look identical to one that worked cleanly.
     */
    private fun reportForTheRecord(context: Context, outcome: ProcessOutcome) {
      Log.i(TAG, "pty probe exit=${outcome.exitCode} report=${outcome.output}")
      val refusals = File(baseSpaceDir(context), "tmp/sigsys.log").takeIf { it.isFile }?.readText()
      Log.i(TAG, "pty probe seccomp refusals: ${refusals?.trim()?.ifBlank { "(none)" } ?: "(no log)"}")
    }

    /** `adb logcat -s JupydroidSpace`, the same filter every Space investigation uses. */
    const val TAG = "JupydroidSpace"
  }
}
