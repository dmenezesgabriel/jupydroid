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
 * **The Space's shell can run the Space's Python**.
 *
 * Before this step it could not: `python3 --version` answered *Permission denied* with **rc=126**
 * on the tablet, while `command -v python3` resolved fine. The
 * kernel was never affected - it is exec'd from `nativeLibraryDir` directly - so nothing in the
 * notebook suite could see this, and nothing did.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.PythonInterpreterDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PythonInterpreterDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no interpreter to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /**
   * Unqualified, because `PATH` is what a user types against and `PATH` is what made the plan's
   * first design wrong: `usr/bin` precedes `bin`, so a link in `bin/` would never have been found.
   */
  @Test
  fun theShellRunsTheSpacesOwnPython() {
    val run = runInSpace(spaceShellIn(baseDir).path, "-c", "python3 --version")

    assertRan(run)
    assertTrue("expected a CPython version, got: ${run.output.trim()}", run.output.startsWith("Python 3."))
  }

  /** The other two names the image ships, which reach the ELF only because they are relative. */
  @Test
  fun theOtherShippedNamesReachItToo() {
    val run = runInSpace(spaceShellIn(baseDir).path, "-c", "python --version && python3.12 --version")

    assertRan(run)
    assertEquals("both names should have answered", 2, run.output.lines().count { it.startsWith("Python 3.") })
  }

  /**
   * A link that runs but cannot find its own stdlib would pass the version check and fail every
   * real use, so this is the assertion that says the *interpreter* works rather than the exec.
   */
  @Test
  fun pythonReachesItsOwnStandardLibrary() {
    val run = runInSpace(spaceShellIn(baseDir).path, "-c", "python3 -c 'import json, sys; print(sys.prefix)'")

    assertRan(run)
    assertEquals(File(baseDir, "usr").path, run.output.trim())
  }

  /** A refusal the SIGSYS handler swallowed would leave the interpreter working and the log dirty. */
  @Test
  fun nothingWasRefusedWhileTheInterpreterRan() {
    runInSpace(spaceShellIn(baseDir).path, "-c", "python3 -c 'import json'")

    assertEquals(
      "seccomp refused a syscall other than mbind while the interpreter ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }

  /**
   * **The measurement the trampoline's design is written from.**
   *
   * An earlier reading took rc=126 for the W^X refusal on
   * an ELF under `filesDir`. That is an over-attribution: [TarZstdExtractor] writes **no executable
   * bit at all**, so EACCES is explained by mode 0600 on its own and the two causes were never
   * separated. They matter separately for `usr/bin/pip`, which is a *script* - there is no ELF to
   * point a symlink at, so the fix is a `chmod` if the mode was the whole story and a different
   * design if it was not.
   *
   * So: a script this app owns, in this app's own `filesDir`, with its executable bit set, run the
   * way a shell would run it. The assertion is what the device answers, and 126 is what it answers
   * today - the exec is refused with the bit set, so **SELinux is the binding cause** and no amount
   * of `chmod` reaches `pip`. Should a future platform allow it, this goes red, which is the point
   * of pinning it.
   */
  @Test
  fun aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet() {
    val script = File(context.filesDir, "p7e-1-exec-probe.sh").apply {
      writeText("#!${spaceShellIn(baseDir).path}\necho ran\n")
      check(setExecutable(true, false)) { "could not set the executable bit on '$this'" }
    }

    val run = runInSpace(spaceShellIn(baseDir).path, "-c", "'${script.path}'; echo rc=$?")

    script.delete()
    assertTrue(
      "a chmod 0755 script under filesDir now runs: ${run.output.trim()}. " +
        "The exec bit was the binding cause after all, so the shebang trampoline is answering " +
        "a constraint that has gone away.",
      "rc=126" in run.output,
    )
  }

  private fun assertRan(run: SpaceProcessRun) {
    // Java reports a signalled child as 128 + signal, so a seccomp kill is 159 - the same
    // encoding `JupyterServerLauncher.exitCode` documents. 126 is the refusal the trampoline fixes.
    assertEquals(
      "${run.command} exited ${run.exitCode}" +
        (if (run.exitCode == EXEC_REFUSED) " (EACCES: is usr/bin/python3.12 still a plain file?)" else "") +
        "; output: ${run.output.take(OUTPUT_EXCERPT)}",
      0,
      run.exitCode,
    )
  }

  private companion object {
    const val EXEC_REFUSED = 126
    const val OUTPUT_EXCERPT = 200
  }
}
