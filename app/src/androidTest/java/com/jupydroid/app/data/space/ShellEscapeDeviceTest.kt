package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `!ls` and `%pip` in a cell, driven through IPython's own code rather than an approximation.
 *
 * **Why this exists as its own gate.** Both reach a shell, and neither obviously does: `%pip` looks
 * like it should need none, but `IPython/core/magics/packaging.py` runs
 * `self.shell.system(sys.executable + " -m pip …")`, and ipykernel binds `system` to `system_piped`
 * (`zmqshell.py:802`), which is `IPython/utils/_process_posix.py` - pexpect spawning
 * `which(os.environ.get("SHELL", "sh")) -c …` on a pty. So the whole notebook shell-escape surface
 * rides on the same busybox that P4b-2 fixed, and it is worth one test that says so.
 *
 * Driven through `PythonRunner` so the child is the app's own process, under the seccomp filter
 * that is the entire subject (`evidence/p4-terminal/busybox-under-app-seccomp.md`).
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ShellEscapeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ShellEscapeDeviceTest {
  @Test
  fun aBangEscapeRunsInTheSpacesShellAndItsOutputComesBack() {
    assertTrue("expected !ls to exit cleanly, got: $report", "bang-ls-ok" in report)
  }

  /**
   * `files = !ls`, which takes a different route from `!ls` and broke where `!ls` did not:
   * `_process_common.py:86-90` uses `Popen(shell=True, executable=os.environ.get('SHELL'))`, and
   * with `SHELL` unset CPython falls back to the absolute `/bin/sh` - a path Android does not have.
   * Measured empty before `SpaceRuntime` set `SHELL`.
   */
  @Test
  fun theCaptureEscapeGetsItsOutputBackToo() {
    assertTrue("expected `files = !ls` to capture the workspace, got: $report", "capture-ok" in report)
  }

  /** The magic IPython itself recommends over `!pip`, and the one the user asked for by name. */
  @Test
  fun thePipMagicReachesPipThroughTheSameShell() {
    assertTrue("expected %pip's pip to answer, got: $report", "pip-magic-ok" in report)
  }

  /** Which shell pexpect resolved - the reason `SHELL` is set rather than left to a PATH search. */
  @Test
  fun theShellIPythonPicksIsTheSpacesOwn() {
    assertTrue("pexpect resolved a shell outside the Space: $report", "shell-is-ours" in report)
  }

  private companion object {
    /** One interpreter start for all three questions; each is independent inside it. */
    val report: String by lazy {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      assumeTrue(
        "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}",
        runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
      )
      runBlocking {
        baseSpaceInstaller(context).installIfNeeded()
        PythonRunner(baseSpaceRuntime(context)).run("-c", PROBE)
      }.let { outcome ->
        assertEquals("the probe process failed: ${outcome.output}", 0, outcome.exitCode)
        outcome.output.also { Log.i(TAG, "shell escape probe:\n$it") }
      }
    }

    const val TAG = "JupydroidSpace"

    /**
     * `IPython.utils.process.system` is exactly what `system_piped` calls, so this is the `!`
     * path itself and not a re-implementation of it. `%pip`'s command line is transcribed from
     * `packaging.py` for the same reason.
     */
    val PROBE = """
      import os, shlex, sys
      from IPython.utils.process import system, getoutput

      base = os.path.normpath(os.path.join(os.environ["HOME"], "..", ".."))
      shell = os.environ.get("SHELL")
      import pexpect
      resolved = pexpect.which(shell or "sh")
      print("SHELL =", shell)
      print("pexpect resolved:", resolved)
      # Compared against the Space's own bin/sh, NOT its realpath: the applet link resolves into
      # nativeLibraryDir on purpose, which is the only place an ELF may be executed from.
      if resolved == os.path.join(base, "bin", "sh"):
          print("shell-is-ours")

      target = shlex.quote(os.environ["HOME"])
      # The printing escape, `!ls`: pexpect spawns the shell on a pty and streams to stdout.
      if system("ls -d " + target) == 0:
          print("bang-ls-ok")

      # The capture escape, `files = !ls`: Popen(shell=True, executable=os.environ["SHELL"]).
      captured = getoutput("ls -d " + target)
      print("capture repr:", repr(captured))
      if os.environ["HOME"] in captured:
          print("capture-ok")

      code = system(" ".join([shlex.quote(sys.executable), "-m", "pip", "--version"]))
      print("%pip exit code:", code)
      if code == 0:
          print("pip-magic-ok")
    """.trimIndent()
  }
}
