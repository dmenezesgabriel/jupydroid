package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.TimeSource

/**
 * **What a Space that owns its whole system can run at targetSdk 36 - measured, not assumed.**
 *
 * A Space isolated down to its system packages needs three things this app has never done: a
 * rootfs of its own, a package manager writing into it, and the programs it installs running. Each
 * test pins what the tablet answers, so a later design argues with a number rather than a belief.
 *
 * Runs in this app's process ([runInSpace]), never `run-as`, which carries no seccomp filter. The
 * proot test runs only in a build made with `-Pjupydroid.isolationProbe` after
 * `scripts/stage_isolation_probe.py`; every other test needs only the shipped APK and, for `apk`
 * and `pip`, the network.
 *
 * ```
 * python3 scripts/stage_isolation_probe.py
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest -Pjupydroid.isolationProbe \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.SpaceIsolationProbeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SpaceIsolationProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }
  private val nativeDir by lazy { context.applicationInfo.nativeLibraryDir }
  private val loader by lazy { File(nativeDir, "libloader.so").path }
  private val privateRoot by lazy { File(spacesDir(context), PRIVATE_ROOT) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /** 407 MB a copy: not left behind for the next test, or on the device. */
  @After
  fun removeThePrivateRoot() {
    privateRoot.deleteRecursively()
  }

  /**
   * The venv's `python3` is a symlink to base's `usr/bin/python3`, a stable path, and the kernel
   * follows the chain into `nativeLibraryDir` - so `pyvenv.cfg` does sit beside the `sys.executable`
   * the interpreter sees. pip installs into the venv once `PIP_USER` is unset, which a venv refuses.
   */
  @Test
  fun aVirtualenvMadeInTheShellRunsAgainstItsOwnPrefixAndInstallsIntoIt() {
    val venv = File(baseDir, "tmp/isolation-venv").path
    val made = sh(
      "rm -rf $venv && python3 -m venv $venv && env -u PIP_USER $venv/bin/python -m pip install -q $PURE_WHEEL; " +
        "$venv/bin/python -c 'import sys, $PURE_WHEEL; print(sys.prefix); print($PURE_WHEEL.__file__)'",
    )

    sh("rm -rf $venv")
    assertEquals(made.output, venv, made.output.lines().first { it.startsWith("/") })
    assertTrue(made.output, made.output.contains("$venv/lib/python3.12/site-packages/$PURE_WHEEL/"))
  }

  /** The other half of the venv: its console scripts are files under `filesDir`, so `execve` refuses them. */
  @Test
  fun aVirtualenvsConsoleScriptIsRefusedLikeAnyScriptUnderFilesDir() {
    val venv = File(baseDir, "tmp/isolation-venv-script").path
    val run = sh("rm -rf $venv && python3 -m venv $venv && $venv/bin/pip3 --version; echo rc=$?")

    sh("rm -rf $venv")
    assertTrue(run.output, run.output.contains("rc=$EXEC_REFUSED"))
  }

  /** musl's loader, run as a program, maps and runs an ELF it is handed: only the loader is execve'd. */
  @Test
  fun anElfUnderFilesDirIsRefusedDirectlyButRunsThroughTheBundledLoader() {
    val direct = sh("$baseDir/sbin/apk --version; echo rc=$?")
    val loaded = sh("$loader $baseDir/sbin/apk --version")

    assertTrue(direct.output, direct.output.contains("rc=$EXEC_REFUSED"))
    assertTrue(loaded.output, loaded.output.startsWith("apk-tools "))
  }

  /**
   * The loader covers one exec, not the program's own: a shell it started still gets 126 for a
   * `filesDir` ELF. Every child would need the same rewrite - which is what an exec shim or proot is.
   */
  @Test
  fun aProgramStartedThroughTheLoaderCannotItselfExecAnElfUnderFilesDir() {
    val shell = File(baseDir, "tmp/busybox").path
    val run = sh(
      "cp ${File(nativeDir, "libbusybox.so").path} $shell && chmod 755 $shell && " +
        "$loader $shell sh -c '$shell true; echo direct=\$?; $loader $shell true; echo through-loader=\$?'",
    )

    sh("rm -f $shell")
    assertTrue(run.output, run.output.contains("direct=$EXEC_REFUSED"))
    assertTrue(run.output, run.output.contains("through-loader=0"))
  }

  /** A second copy of the image, with base's interpreter pointed at it by `PYTHONHOME` and the loader's library path. */
  @Test
  fun aPrivateRootfsCopyRunsTheInterpreterAgainstItsOwnPrefix() {
    extractPrivateRoot()
    val run = sh("${inPrivatePython()} -c 'import sys, ssl; print(sys.prefix)'")
    record("private rootfs size", sh("du -sm ${privateRoot.path}"))

    assertEquals(run.output, "${privateRoot.path}/usr", run.output.trim())
  }

  /** Why a private rootfs is a full copy: `link(2)` inside `filesDir` is refused, so there is no cheap clone. */
  @Test
  fun hardLinksInsideFilesDirAreRefused() {
    val link = File(baseDir, "tmp/isolation-hardlink").path
    val run = sh("ln $baseDir/usr/lib/libz.so.1 $link; echo rc=$?; rm -f $link")

    assertTrue(run.output, run.output.contains("Permission denied"))
  }

  /**
   * `apk --root` installs as this app's uid with scripts off, and what it installs runs through the
   * loader; base itself is untouched.
   */
  @Test
  fun apkInstallsIntoAPrivateRootfsAndLeavesBaseAlone() {
    extractPrivateRoot()
    // The same two packages the proot test installs, so the two timings compare like for like.
    val added = sh(
      "time sh -c '${inPrivateRoot("sbin/apk")} --root ${privateRoot.path} --no-scripts --no-cache add jq git " +
        "2>&1 | tail -1' 2>&1",
    )
    record("loader apk add", added)
    val ran = sh("echo '{\"isolated\":true}' | ${inPrivateRoot("usr/bin/jq")} .isolated")
    val base = sh("ls $baseDir/usr/bin/jq; echo rc=$?")

    assertTrue(added.output, added.output.startsWith("OK: 80 MiB in 54 packages"))
    assertEquals(ran.output, "true", ran.output.trim())
    assertTrue(base.output, base.output.contains("rc=1"))
  }

  /**
   * The loader rewrites no paths: git looks for its helpers at the absolute `/usr/libexec/git-core`,
   * which on Android is not the rootfs, so `https` has no helper. A translated root is what fixes this.
   */
  @Test
  fun aProgramThatExecsByAbsolutePathFailsWithoutATranslatedRoot() {
    extractPrivateRoot()
    sh("${inPrivateRoot("sbin/apk")} --root ${privateRoot.path} --no-scripts --no-cache add git >/dev/null 2>&1")
    val git = inPrivateRoot("usr/bin/git")
    val run = sh("$git --exec-path; timeout $NETWORK_SECONDS $git ls-remote $PUBLIC_REPO 2>&1 | head -1")

    assertTrue(run.output, run.output.startsWith("/usr/libexec/git-core"))
    assertTrue(run.output, run.output.contains("unable to find remote helper for 'https'"))
  }

  /** proot in `nativeLibraryDir`, its loader beside it: the private rootfs as `/`, with every exec translated. */
  @Test
  fun prootRunsAPrivateRootfsAsItsRoot() {
    assumeTrue("proot ships only under -Pjupydroid.isolationProbe", File(nativeDir, "libproot.so").exists())
    extractPrivateRoot()
    // The extractor writes no executable bit; proot checks it where the kernel would.
    sh("chmod -R u+x ${privateRoot.path}")
    File(privateRoot, "etc/resolv.conf").writeText("nameserver $PUBLIC_DNS\n")

    val python = inNativeDir("${proot()} /usr/bin/python3.12 -c 'import sys, os; print(sys.prefix, os.getuid())'")
    val shell = inNativeDir("${proot()} /bin/sh -c 'cat /etc/alpine-release'")
    val added = inNativeDir("time sh -c '${proot()} /sbin/apk add --no-cache jq git 2>&1 | tail -1' 2>&1")
    record("proot apk add", added)
    val git = inNativeDir("${proot()} /usr/bin/git ls-remote $PUBLIC_REPO 2>&1 | head -1")
    record("loader python x5", sh("time sh -c 'for i in 1 2 3 4 5; do ${inPrivatePython()} -c pass; done'"))
    record(
      "proot python x5",
      inNativeDir("time sh -c 'for i in 1 2 3 4 5; do ${proot()} /usr/bin/python3.12 -c pass; done'"),
    )
    record("proot git https", git)

    assertEquals(python.output, "/usr 0", python.output.trim())
    assertEquals(shell.output, "3.21.7", shell.output.trim())
    assertTrue(added.output, added.output.startsWith("OK: 80 MiB in 54 packages"))
    assertTrue("git's https helper ran under the translated root: ${git.output}", git.output.endsWith("HEAD\n"))
  }

  private fun sh(script: String): SpaceProcessRun = runInSpace(spaceShellIn(baseDir).path, "-c", script)

  private fun record(label: String, run: SpaceProcessRun) = Log.i(PROBE_TAG, "$label rc=${run.exitCode}\n${run.output}")

  private fun inPrivateRoot(program: String): String =
    "$loader --library-path ${privateRoot.path}/usr/lib:${privateRoot.path}/lib ${privateRoot.path}/$program"

  /** Base's interpreter ELF, which is the only Python there is, read against the private root's stdlib. */
  private fun inPrivatePython(): String =
    "PYTHONHOME=${privateRoot.path}/usr $loader --library-path ${privateRoot.path}/usr/lib:${privateRoot.path}/lib " +
      File(nativeDir, "libpython3.so").path

  /**
   * Run from inside `nativeLibraryDir` ([inNativeDir]): its path holds `==`, which `env` reads as an
   * assignment. `env -i`, because base's preloads are absolute paths proot would translate into the
   * private root, where they are not.
   */
  private fun proot(): String = "timeout $NETWORK_SECONDS env -i PROOT_LOADER=$nativeDir/libproot-loader.so " +
    "PROOT_TMP_DIR=${privateRoot.path}/tmp PATH=/usr/sbin:/usr/bin:/sbin:/bin HOME=/root " +
    "./libproot.so -r ${privateRoot.path} -0 -w / -b /dev -b /proc"

  private fun inNativeDir(script: String): SpaceProcessRun = sh("cd $nativeDir && $script")

  /** Fresh for every test that asks, so no test reads what another installed. */
  private fun extractPrivateRoot() {
    privateRoot.deleteRecursively()
    val start = TimeSource.Monotonic.markNow()
    val entries = runBlocking {
      TarZstdExtractor().extract({ context.assets.open("spaces/base/rootfs-arm64-v8a.tzst") }, privateRoot)
    }
    Log.i(PROBE_TAG, "private rootfs extracted entries=$entries ms=${start.elapsedNow().inWholeMilliseconds}")
  }

  companion object {
    private const val PROBE_TAG = "JupydroidIsolation"
    private const val PRIVATE_ROOT = "isolation-private-root"
    private const val EXEC_REFUSED = 126
    private const val NETWORK_SECONDS = 180

    /** Small, pure-Python and absent from the rootfs, so "already satisfied" cannot fake a pass. */
    private const val PURE_WHEEL = "tabulate"
    private const val PUBLIC_REPO = "https://github.com/octocat/Hello-World"
    private const val PUBLIC_DNS = "1.1.1.1"
  }
}
