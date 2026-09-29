package com.jupydroid.app.data.space

import android.os.Build
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
 * `run-as` could never show.
 *
 * [runInSpace] is what keeps that: it starts the child from the instrumented test, in the app
 * process, and P7e-1 moved it there so [PythonInterpreterDeviceTest] could not grow a second copy
 * that forgot why.
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
    val run = runInSpace(spaceShellIn(baseDir).path, "-c", "ls -d /")

    assertSurvived(run)
    assertEquals("/", run.output.trim())
  }

  /** An applet reached directly, so a broken dispatch cannot hide behind the shell. */
  @Test
  fun anAppletReachedByItsOwnNameLives() {
    val run = runInSpace(File(baseDir, "bin/ls").path, "-d", "/")

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
    val run = runInSpace(spaceShellIn(baseDir).path, "-c", "ls --help 2>&1 | head -1")

    assertSurvived(run)
    assertTrue("expected BusyBox's ls, got: ${run.output.trim()}", "BusyBox" in run.output)
  }

  /**
   * A refusal the handler swallowed would leave the applet working and the log non-empty.
   *
   * The log is shared by every Space process and appended to, so any suite that has touched Python
   * puts lines in it - which is how this test first went red, running beside [PtySupportTest]
   * after passing alone. [seccompRefusalsIn] is where the one tolerated refusal is named.
   *
   * It covers the interpreter side only: busybox carries no SIGSYS handler of its own, so a
   * refusal *there* kills it rather than being logged. That half is what the other tests assert.
   */
  @Test
  fun nothingWasRefusedWhileTheAppletsRan() {
    runInSpace(spaceShellIn(baseDir).path, "-c", "ls -d / && echo done")

    assertEquals(
      "seccomp refused a syscall other than mbind while an applet ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }

  private fun assertSurvived(run: SpaceProcessRun) {
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

  private companion object {
    const val SIGSYS_EXIT = 159
    const val OUTPUT_EXCERPT = 200
  }
}
