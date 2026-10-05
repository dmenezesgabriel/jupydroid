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

/**
 * **`pylsp` runs in the Space's shell and answers LSP over stdio.**
 *
 * The route this app took is `python-lsp-server` as a direct stdio
 * subprocess off the `nativeLibraryDir` interpreter - no `jupyter-lsp` websocket hop.
 * These tests prove the two premises that route rests on, **in the app process**
 * through [runInSpace]/`ProcessBuilder`, the only place the seccomp filter applies
 * (a `run-as` probe would answer a question nobody asked - see [SpaceProcessRun]):
 *
 *  1. the console script runs by name - `/usr/bin/pylsp`, the image's own `#!` file,
 *     which proot execs through its shebang line; and
 *  2. it *serves* - a `Content-Length`-framed `initialize` gets a framed response with
 *     `capabilities`, and `shutdown`/`exit` ends the process cleanly. `pylsp --version`
 *     alone only proves the import; debugpy is the record of a package that imported
 *     fine and aborted on device the moment it did real work.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.LanguageServerDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LanguageServerDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no language server to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /** By name, through the Space's PATH - the way the terminal and [StdioLanguageServer] reach it. */
  @Test
  fun pylspRunsByNameAndReportsItsVersion() {
    val run = runInSpace(SPACE_SHELL, "-c", "pylsp --version")

    assertEquals("pylsp --version exited ${run.exitCode}: ${run.output.trim()}", 0, run.exitCode)
    assertTrue("expected a pylsp version, got: ${run.output.trim()}", run.output.trim().startsWith("pylsp v"))
  }

  /**
   * The premise itself: `pylsp` spawned with piped stdio, as the adapter spawns it, answers a real
   * `initialize`. Exec'd straight off its in-root path rather than
   * through a shell, so the request goes through proot and nothing else.
   */
  @Test(timeout = 120_000)
  fun pylspAnswersAnInitializeOverStdio() {
    LspStdioSession(baseSpaceRuntime(context).execProcessBuilder(listOf(SPACE_PYLSP)).start()).use { session ->
      val capabilities = session.initialize()
      assertTrue(
        "no capabilities in the initialize reply: $capabilities; stderr: ${session.stderr}",
        "\"capabilities\"" in capabilities,
      )

      assertEquals(
        "pylsp did not exit 0 after a clean shutdown; stderr: ${session.stderr}",
        0,
        session.shutdownAndExit(),
      )
    }
  }

  /** No syscall was refused while pylsp ran - a refusal the SIGSYS handler swallowed would hide. */
  @Test
  fun nothingWasRefusedWhilePylspRan() {
    runInSpace(SPACE_SHELL, "-c", "pylsp --version")

    assertEquals(
      "seccomp refused a syscall other than mbind while pylsp ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }
}
