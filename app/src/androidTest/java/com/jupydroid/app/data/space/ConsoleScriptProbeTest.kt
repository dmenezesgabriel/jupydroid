package com.jupydroid.app.data.space

import android.os.Build
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
import java.nio.file.Files

/**
 * **The console-script facts a Space under proot rests on, on the device.**
 *
 * The scripts are the image's own `usr/bin` `#!` files, run through their shebang line
 * by proot - the question [PythonInterpreterDeviceTest] answers for a probe file and this
 * answers for the shipped ones. It also pins the sweep: `usr/local/bin`, where the pre-proot
 * install linked every script onto `bin/shebang-exec`, holds none of it afterwards.
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

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there are no console scripts to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /**
   * The script file itself, by its in-root path - not a `PATH` lookup, so what execs is exactly
   * the `#!/usr/bin/python3` file the image ships.
   */
  @Test
  fun aShippedConsoleScriptExecsThroughItsOwnShebangLine() {
    val run = runInSpace(SPACE_SHELL, "-c", "/usr/bin/pip --version")

    assertEquals("/usr/bin/pip --version exited ${run.exitCode}: ${run.output.trim()}", 0, run.exitCode)
    assertTrue("expected a pip version, got: ${run.output.trim()}", run.output.trim().startsWith("pip "))
  }

  /**
   * **The installer's sweep, on the device.** `bin/shebang-exec` and the `usr/local/bin` links
   * onto it are the only surgery the archive cannot restore - they are not in it - so if the
   * sweep ever stops running, this is the assertion that goes red.
   */
  @Test
  fun installLeavesNothingOfThePreProotSurgeryInTheSpace() {
    assertFalse(
      "bin/shebang-exec survived the sweep",
      File(baseDir, "bin/shebang-exec").exists(),
    )

    val stale = File(baseDir, "usr/local/bin").listFiles().orEmpty().filter { entry ->
      Files.isSymbolicLink(entry.toPath()) &&
        (!entry.canonicalFile.exists() || !entry.canonicalPath.startsWith(baseDir.canonicalPath + "/"))
    }
    assertEquals(
      "usr/local/bin holds a link the sweep should have removed",
      emptyList<String>(),
      stale.map { it.name },
    )
  }

  /**
   * `pkgs/bin` first, so a user's console script shadows the shipped one - the precedence
   * `~/.local/bin` takes over `/usr/bin` on a desktop, named inside the Space's root. The rest is
   * the image's own login `PATH` (`etc/profile` in the shipped rootfs), `sbin` entries included,
   * since `/sbin/apk` resolves through it.
   */
  @Test
  fun theSpacesPathPutsTheUsersScriptsFirst() {
    val path = runInSpace(SPACE_SHELL, "-c", "echo \$PATH").output.trim()

    assertEquals("/pkgs/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", path)
  }
}
