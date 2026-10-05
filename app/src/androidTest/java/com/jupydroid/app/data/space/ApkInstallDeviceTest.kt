package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **`apk add` in a Space installs into that Space alone** - by name, through the same
 * `SpaceRuntime` environment a terminal's shell inherits.
 *
 * Two facts carry it. The image's `etc/profile` puts the `sbin` dirs on a login PATH, but a
 * Space's shell is never login (`JupyterServerLauncher` pins it so), so `SpaceRuntime`'s PATH
 * is the only one - and it has to reach `/sbin/apk` itself. And the install lands inside this
 * root only: `jq` and its `lib/apk/db` entry under the private dir, base's rootfs untouched.
 *
 * In the app process, where the seccomp filter applies - see [SpaceProcessRun] for why `run-as`
 * would answer a different question. `Assume`-skipped where no rootfs ships. Needs the tablet
 * online - the same constraint any `apk add` carries.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ApkInstallDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ApkInstallDeviceTest {
  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
    check(privateImage) { "apk-probe's image did not land" }
  }

  /**
   * One ordered script rather than separate tests, for the reason [PipInstallDeviceTest]
   * states: each later assertion reads the state the earlier command left. `jq` is the probe
   * because the image does NOT ship it, so its presence can only be this install.
   */
  @Test(timeout = 120_000)
  fun anApkInstallLandsInThatSpaceAlone() {
    val whereApk = runInSpace(SPACE_SHELL, "-c", "command -v apk", runtime = runtime)
    assertEquals("apk does not resolve on the Space's PATH: ${whereApk.output}", "/sbin/apk", whereApk.output.trim())

    val install = runInSpace(SPACE_SHELL, "-c", "apk add jq", runtime = runtime)
    assertEquals("apk add jq failed: ${install.output.take(OUTPUT_EXCERPT)}", 0, install.exitCode)
    assertTrue("no ${privateRoot.dir}/usr/bin/jq after a clean add", File(privateRoot.dir, "usr/bin/jq").isFile)

    val answer = runInSpace(SPACE_SHELL, "-c", "echo '{\"a\":1}' | jq .a", runtime = runtime)
    assertEquals("jq installed but did not run: ${answer.output}", "1", answer.output.trim())

    assertFalse("the install reached base's rootfs", File(baseDir, "usr/bin/jq").isFile)
    assertEquals(emptyList<String>(), seccompRefusalsIn(privateRoot.dir))
  }

  private companion object {
    const val OUTPUT_EXCERPT = 400

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val baseDir: File get() = baseSpaceDir(context)

    /** A Space this test owns: `apk add` mutates its rootfs, which no other suite should read. */
    val privateRoot by lazy { PrivateRootUnderTest(context, "apk-probe") }

    /** The same environment a process in that Space gets in the app: proot plus its DNS source. */
    val runtime: SpaceRuntime by lazy {
      SpaceDirs(privateRoot.dir).ensure()
      SpaceRuntime(privateRoot.dir, File(privateRoot.nativeDir), androidDnsServerSource(context))
    }

    /** The image, extracted once for the class - 407 MB, so not per method. */
    val privateImage: Boolean by lazy {
      privateRoot.extract()
      true
    }

    @JvmStatic
    @AfterClass
    fun removeThePrivateRoot() {
      privateRoot.delete()
    }
  }
}
