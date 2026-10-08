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
 * **`pip install` grounds in the Space's own user base**, not the rootfs's
 * site-packages.
 *
 * The user's report was exact: Alpine ships this interpreter with the PEP 668
 * `EXTERNALLY-MANAGED` marker, so `pip install pandas` refused until
 * `--break-system-packages` was passed - and then landed on the *system*
 * site-packages. `SpaceRuntime.environment()` now carries `PYTHONUSERBASE` +
 * `PIP_ONLY_BINARY`/`PIP_BREAK_SYSTEM_PACKAGES`, and base's own `usr/pip.conf` carries
 * `user = true` (`SpacePipConfig`), which this test exercises end to end inside the app's
 * own seccomp context.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.PipInstallDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PipInstallDeviceTest {
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
   * One ordered script rather than separate tests, because uninstalling first is the
   * precondition the later assertions read: `tabulate` is chosen precisely because the
   * rootfs does NOT ship it, so "already satisfied" cannot fake a pass.
   *
   * Needs the tablet online - the same constraint any `pip install` carries.
   */
  @Test
  fun anUnflaggedPipInstallLandsInTheSpacesUserBase() {
    runInSpace(
      SPACE_SHELL,
      "-c",
      "pip3 uninstall -y $PROBE_PACKAGE >/dev/null 2>&1; rm -rf /pkgs",
    )

    val install = runInSpace(SPACE_SHELL, "-c", "pip3 install $PROBE_PACKAGE")
    try {
      assertEquals("unflagged pip install failed: ${install.output.take(OUTPUT_EXCERPT)}", 0, install.exitCode)

      val siteDir = File(baseDir, "pkgs/lib/python3.12/site-packages/$PROBE_PACKAGE")
      assertTrue(
        "expected $siteDir to hold the install; pip said: ${install.output.take(OUTPUT_EXCERPT)}",
        siteDir.isDirectory,
      )

      // maxdepth 1: jedi bundles typeshed *stubs* for tabulate deeper in the tree, and those
      // are not an install.
      val system = runInSpace(
        SPACE_SHELL,
        "-c",
        "find /usr/lib/python3.12/site-packages -maxdepth 1 -name '$PROBE_PACKAGE*'",
      )
      assertEquals("the system site-packages picked up the install", "", system.output.trim())

      val imported = runInSpace(
        SPACE_SHELL,
        "-c",
        "python3 -c 'import $PROBE_PACKAGE; print($PROBE_PACKAGE.__file__)'",
      )
      assertTrue(
        "import resolved outside the user base: ${imported.output.trim()}",
        imported.output.trim().startsWith("/pkgs/"),
      )

      // pip's own warning names the gap this closes: "pkgs/bin ... is not on PATH".
      val script = runInSpace(SPACE_SHELL, "-c", "command -v $PROBE_PACKAGE")
      assertEquals("/pkgs/bin/$PROBE_PACKAGE", script.output.trim())
    } finally {
      runInSpace(SPACE_SHELL, "-c", "pip3 uninstall -y $PROBE_PACKAGE >/dev/null 2>&1 || true")
    }
  }

  /**
   * **A venv's own pip installs into the venv**, unflagged, and its console script runs.
   *
   * With `--user` in the environment the venv's pip refused every install; base's `usr/pip.conf`
   * is `<sys.prefix>/pip.conf` for base alone (`SpacePipConfig`), so the venv's pip never sees it.
   * The console script is a `#!` file under `filesDir`, which proot execs through its shebang line.
   */
  @Test
  fun aVenvsPlainPipInstallLandsInTheVenvAndItsConsoleScriptRuns() {
    runInSpace(SPACE_SHELL, "-c", "rm -rf $PROBE_VENV && python3 -m venv $PROBE_VENV")
    try {
      val install = runInSpace(SPACE_SHELL, "-c", "$PROBE_VENV/bin/pip install $PROBE_PACKAGE")
      assertEquals("venv pip install failed: ${install.output.take(OUTPUT_EXCERPT)}", 0, install.exitCode)

      val imported = runInSpace("$PROBE_VENV/bin/python", "-c", "import $PROBE_PACKAGE; print($PROBE_PACKAGE.__file__)")
      assertTrue("import resolved outside the venv: ${imported.output}", imported.output.startsWith("$PROBE_VENV/"))

      val script = runInSpace("$PROBE_VENV/bin/$PROBE_PACKAGE", "--help")
      assertEquals("console script failed: ${script.output.take(OUTPUT_EXCERPT)}", 0, script.exitCode)
    } finally {
      runInSpace(SPACE_SHELL, "-c", "rm -rf $PROBE_VENV")
    }
  }

  private companion object {
    /** Small, pure-Python, wheel-published - and deliberately absent from requirements.lock. */
    const val PROBE_PACKAGE = "tabulate"

    /** In the Space's own tmp, so no run leaves a venv in the person's workspace. */
    const val PROBE_VENV = "/tmp/probe-venv"
    const val OUTPUT_EXCERPT = 300
  }
}
