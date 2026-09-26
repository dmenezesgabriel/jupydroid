package com.jupydroid.app.data.space

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.LanguagePosition
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * **P5b: the adapter drives the real `pylsp` the rootfs ships.**
 *
 * P5a proved the premises - the script execs and answers over stdio. This proves the thing
 * P5c plugs into: [StdioLanguageServer] as the app composes it (`baseSpaceLanguageServerPort`
 * does this same wiring), a full session through the port rather than raw frames -
 * `initialize`, `didOpen`, a `textDocument/completion` jedi answers with real results, then
 * `shutdown`/`exit` ending the process with nothing refused.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.StdioLanguageServerDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class StdioLanguageServerDeviceTest {
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

  /**
   * The whole session, one process: handshake declares completions, a document opens, a real
   * `os.path.jo|` completion comes back naming `join`, and stop() leaves no interpreter behind.
   * `pylsp --version` answered none of this - a server that starts and then does nothing is
   * exactly the failure a port hides, so the port is what is tested.
   */
  @Test(timeout = 120_000)
  fun thePortDrivesARealPylspSession() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    try {
      val capabilities = languageServer.capabilities(BASE_SPACE)
      assertTrue("pylsp must declare completionProvider for P5c", capabilities.completionProvider)

      val uri = "file:///home/jupydroid/p5b-probe.py"
      languageServer.openDocument(BASE_SPACE, uri, "python", "import os\nos.path.jo\n")
      val items = languageServer.completion(BASE_SPACE, uri, LanguagePosition(line = 1, character = 10))

      assertTrue(
        "jedi offered nothing for `os.path.jo` - got ${items.map { it.label }}",
        items.any { it.label.startsWith("join") },
      )
      // P5e-2: the row reads the signature-resolved label and is ordered/tinted by the server's
      // own sortText/kind - the P5e-0 probe measured kind=3, sortText="ajoin" for this item.
      val join = items.first { it.label.startsWith("join") }
      assertEquals("pylsp must name join a function (CompletionItemKind 3)", 3, join.kind)
      assertTrue("pylsp must send sortText for ordering - got ${join.sortText}", !join.sortText.isNullOrEmpty())
    } finally {
      languageServer.stop(BASE_SPACE)
    }
  }

  /**
   * P5d-2a: a `didOpen` carrying an undefined name makes the real `pylsp` push a
   * `textDocument/publishDiagnostics` - pyflakes' own `undefined name` - arriving typed on the
   * port's stream, never requested. A scripted server can only pretend to lint; this is the
   * lint.
   */
  @Test(timeout = 120_000)
  fun publishDiagnosticsFromRealPylsp_arrivesOnThePortStream() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    try {
      val uri = "file:///home/jupydroid/p5d2-probe.py"
      val received = async { languageServer.diagnostics.first { it.uri == uri } }
      languageServer.openDocument(BASE_SPACE, uri, "python", "print(never_defined_lsp_name)\n")

      val pushed = withTimeout(60.seconds) { received.await() }

      assertTrue(
        "pylsp reported nothing about the undefined name - got ${pushed.diagnostics}",
        pushed.diagnostics.any { it.message.contains("never_defined_lsp_name") },
      )
    } finally {
      languageServer.stop(BASE_SPACE)
    }
  }

  /**
   * P5e-3: `textDocument/hover` on a name the document defines makes the real `pylsp` answer
   * its markdown - jedi's fenced signature plus docstring - typed through the port, and `null`
   * where nothing is defined. A scripted server can only pretend to know a symbol; this is the
   * knowing.
   */
  @Test(timeout = 120_000)
  fun hoverOnADefinedName_answersItsMarkdown() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    try {
      val uri = "file:///home/jupydroid/p5e3-probe.py"
      languageServer.openDocument(
        BASE_SPACE,
        uri,
        "python",
        "def compute_total(values):\n    return sum(values)\n\ncompute_total([1, 2])\n",
      )

      val hover = languageServer.hover(BASE_SPACE, uri, LanguagePosition(line = 3, character = 4))

      assertTrue("pylsp answered nothing for compute_total", hover != null)
      assertTrue(
        "expected the signature in the markup - got ${hover!!.contents.value.take(80)}",
        hover.contents.value.contains("compute_total"),
      )

      val nothing = languageServer.hover(BASE_SPACE, uri, LanguagePosition(line = 2, character = 0))
      assertTrue(
        "an empty line has no symbol to describe - got ${nothing?.contents?.value?.take(80)}",
        nothing == null || nothing.contents.value.isBlank(),
      )
    } finally {
      languageServer.stop(BASE_SPACE)
    }
  }

  /** Lazy means lazy: a port never called leaves no process behind to stop. */
  @Test(timeout = 60_000)
  fun stopOnANeverStartedServerIsANoOp() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    languageServer.stop(BASE_SPACE)
  }

  /** No syscall was refused while the adapter ran a session - same bar P5a set. */
  @Test
  fun nothingWasRefusedWhileTheAdapterRan() {
    assertEquals(
      "seccomp refused a syscall other than mbind while pylsp ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }
}
