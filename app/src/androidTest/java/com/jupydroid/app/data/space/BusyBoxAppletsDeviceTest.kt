package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * An applet runs **in this app's process**, which is the only place the question means anything.
 *
 * **P4b-2, and the gate P4b was missing.** P4b proved the shell through `adb shell run-as`, which
 * is not zygote-spawned and so carries no app seccomp filter. Under the filter every applet died:
 * Alpine's busybox sets `CONFIG_FEATURE_SUID`, so each dispatch calls `setgid`/`setuid`
 * (`libbb/appletlib.c:965`), both refused to apps by bionic's `SECCOMP_BLOCKLIST_APP.TXT`, and the
 * shell was killed by SIGSYS about seven milliseconds after exec having printed nothing - which
 * `run-as` could never show. Measured in `evidence/p4-terminal/busybox-under-app-seccomp.md`.
 *
 * So this uses a plain [ProcessBuilder] from the instrumented test, which runs *inside* the app
 * process: the child is a sibling of the one `jupyter_server` forks, under the same filter, with
 * the same environment. **It must never shell out through `run-as`** - that is the whole defect.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.BusyBoxAppletsDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class BusyBoxAppletsDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there are no applets to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /** The shell itself, through an applet rather than a builtin - `echo` alone would prove nothing. */
  @Test
  fun theShellForksAnAppletAndLives() {
    val run = run(spaceShellIn(baseDir).path, "-c", "ls -d /")

    assertSurvived(run)
    assertEquals("/", run.output.trim())
  }

  /** An applet reached directly, so a broken dispatch cannot hide behind the shell. */
  @Test
  fun anAppletReachedByItsOwnNameLives() {
    val run = run(File(baseDir, "bin/ls").path, "-d", "/")

    assertSurvived(run)
    assertEquals("/", run.output.trim())
  }

  /**
   * The Space's `PATH` decides whose coreutils an unqualified name gets, and
   * `evidence/p4-terminal/busybox-on-device.txt` deferred asserting it to here: under `run-as` an
   * unqualified `ls` is Android's toybox, because `run-as` keeps Android's environment.
   */
  @Test
  fun anUnqualifiedNameFindsTheSpacesOwnCoreutils() {
    val run = run(spaceShellIn(baseDir).path, "-c", "ls --help 2>&1 | head -1")

    assertSurvived(run)
    assertTrue("expected BusyBox's ls, got: ${run.output.trim()}", "BusyBox" in run.output)
  }

  /**
   * A refusal the handler swallowed would leave the applet working and the log non-empty.
   *
   * `mbind` is excluded by number rather than the check being dropped: numpy issues it on import
   * and this runtime has tolerated it since step 8.7 (`docs/environment-fidelity.md:24-28`). The
   * log is shared by every Space process and appended to, so any suite that has touched Python
   * puts it there - which is how this test first went red, running beside [PtySupportTest] after
   * passing alone.
   *
   * It covers the interpreter side only: busybox carries no SIGSYS handler of its own, so a
   * refusal *there* kills it rather than being logged. That half is what the other tests assert.
   */
  @Test
  fun nothingWasRefusedWhileTheAppletsRan() {
    run(spaceShellIn(baseDir).path, "-c", "ls -d / && echo done")

    val unexpected = File(baseDir, "tmp/sigsys.log").takeIf { it.isFile }?.readText().orEmpty()
      .lines().filter { it.isNotBlank() && TOLERATED_REFUSAL !in it }

    assertEquals("seccomp refused a syscall other than mbind while an applet ran", emptyList<String>(), unexpected)
  }

  private fun assertSurvived(run: AppletRun) {
    // Java reports a signalled child as 128 + signal, so a seccomp kill is 159 - the same
    // encoding `JupyterServerLauncher.exitCode` documents.
    assertEquals(
      "${run.command} exited ${run.exitCode}" +
        (if (run.exitCode == SIGSYS_EXIT) " (SIGSYS: built with CONFIG_FEATURE_SUID?)" else "") +
        "; output: ${run.output.take(OUTPUT_EXCERPT)}",
      0,
      run.exitCode,
    )
  }

  private fun run(vararg command: String): AppletRun {
    val process = ProcessBuilder(command.toList())
      .apply {
        // Android's own PATH and linker variables would otherwise leak in, exactly as
        // SpaceRuntime.processBuilder guards against for the interpreter.
        environment().clear()
        environment().putAll(baseSpaceRuntime(context).environment())
      }
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    return AppletRun(command.joinToString(" "), process.waitFor(), output)
      .also { Log.i(TAG, "applet run: $it") }
  }

  private data class AppletRun(val command: String, val exitCode: Int, val output: String)

  private companion object {
    const val SIGSYS_EXIT = 159

    /** `mbind` on aarch64: numpy issues it on import, tolerated since step 8.7. */
    const val TOLERATED_REFUSAL = "syscall=235"
    const val OUTPUT_EXCERPT = 200

    /** `adb logcat -s JupydroidSpace`, the filter every Space investigation already uses. */
    const val TAG = "JupydroidSpace"
  }
}
