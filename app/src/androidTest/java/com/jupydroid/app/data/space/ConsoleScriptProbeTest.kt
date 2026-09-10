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
import java.nio.file.Files

/**
 * **P7e-2a: the measurements P7e-2's design is written from, before a line of C.**
 *
 * `usr/bin/pip` is a `#!/usr/bin/python3` script under `filesDir`, and
 * [PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet] already
 * settled that SELinux refuses to `execve` it whatever its mode. The design is a native trampoline
 * reached by name through `usr/local/bin`, which reads the shebang and execs the interpreter - the
 * fourth use of the pattern `docs/DECISIONS.md` "Exec model" already runs three times. These
 * assertions pin the three facts that design turns on, so a platform change that breaks one goes
 * red here.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ConsoleScriptProbeTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ConsoleScriptProbeTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }
  private val shell by lazy { spaceShellIn(baseDir).path }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there are no console scripts to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /**
   * **The load-bearing one.** If a console script does not run when handed straight to the
   * interpreter, the trampoline cannot work and the whole design is wrong. `-m pip` proves pip is
   * importable; this proves the *script file* is too, which is what the trampoline will exec.
   */
  @Test
  fun theInterpreterRunsAConsoleScriptHandedToItDirectly() {
    val pip = File(baseDir, "usr/bin/pip").path
    val run = runInSpace(shell, "-c", "python3 '$pip' --version")

    assertEquals("python3 $pip --version exited ${run.exitCode}: ${run.output.trim()}", 0, run.exitCode)
    assertTrue("expected a pip version, got: ${run.output.trim()}", run.output.trim().startsWith("pip "))
  }

  /**
   * The trampoline lives two symlinks deep: `usr/local/bin/<name>` -> `bin/<trampoline>` ->
   * `nativeLibraryDir/lib….so`. `bin/busybox`'s own chain is one hop shallower, so this is the one
   * that has never been exercised - a relative link out of `usr/local/bin` reaching the native
   * library through `bin/`.
   */
  @Test
  fun aRelativeTwoLevelSymlinkOutOfUsrLocalBinExecsThroughTheNativeLibrary() {
    val localBin = File(baseDir, "usr/local/bin").apply { mkdirs() }
    // Point at a real applet name so busybox dispatches once the chain resolves. The relative
    // target is computed the way `BusyBoxApplets` computes its own - `usr/local/bin` is one level
    // deeper than `usr/bin`, so this is `../../../bin/busybox`, and getting it wrong is exactly
    // what this test is here to catch before the installer hardcodes it.
    val busyboxRel = localBin.toPath().relativize(File(baseDir, "bin/busybox").toPath()).toString()
    val link = File(localBin, "true").toPath()
    Files.deleteIfExists(link)
    Files.createSymbolicLink(link, File(busyboxRel).toPath())
    try {
      val run = runInSpace(shell, "-c", "'$link' ; echo rc=$?")
      assertTrue("a two-level relative symlink did not exec: ${run.output.trim()}", "rc=0" in run.output)
    } finally {
      Files.deleteIfExists(link)
    }
  }

  /**
   * `usr/local/bin` is empty in the image and first on `PATH` (`SpaceRuntime.kt:42`), which is what
   * lets the trampoline links shadow the real scripts without touching them. Both halves, on the
   * device rather than in the tarball.
   */
  @Test
  fun usrLocalBinIsEmptyAndFirstOnThePath() {
    val localBin = File(baseDir, "usr/local/bin")
    assertEquals(
      "usr/local/bin already holds files; a trampoline link would not be shadowing cleanly",
      emptyList<String>(),
      localBin.list()?.toList().orEmpty().filterNot { it == "true" },
    )

    val path = runInSpace(shell, "-c", "echo \$PATH").output.trim()
    assertTrue(
      "PATH does not start with usr/local/bin: $path",
      path.startsWith(File(baseDir, "usr/local/bin").path + ":"),
    )
  }

  /**
   * What the shell hands a `usr/local/bin` entry as `argv[0]` when it is reached by name through
   * `PATH` - the string the trampoline will `basename()`. Measured by shadowing `python3` with a
   * link and asking the interpreter what it sees: `basename` has to be right for both a bare name
   * and a full path, and this says which one busybox `ash` actually passes.
   */
  @Test
  fun aPathResolvedNameArrivesAsArgv0() {
    val localBin = File(baseDir, "usr/local/bin").apply { mkdirs() }
    val interpRel = localBin.toPath().relativize(File(baseDir, "usr/bin/python3.12").toPath()).toString()
    val link = File(localBin, "python3").toPath()
    Files.deleteIfExists(link)
    Files.createSymbolicLink(link, File(interpRel).toPath())
    try {
      val run = runInSpace(shell, "-c", "python3 -c 'import sys; print(sys.orig_argv[0])'")
      val argv0 = run.output.trim()
      Log.i(SPACE_LOG_TAG, "P7e-2a argv[0] for a PATH-resolved name: '$argv0'")
      assertEquals("python3 did not run through the shadow link: $argv0", 0, run.exitCode)
      assertTrue(
        "argv[0] '$argv0' does not basename to the command name",
        File(argv0).name == "python3",
      )
    } finally {
      Files.deleteIfExists(link)
    }
  }
}
