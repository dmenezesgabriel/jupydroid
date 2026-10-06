package com.jupydroid.app.data.space

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.formattingOptions
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
 * **The adapter drives the real `pylsp` the rootfs ships.**
 *
 * [LanguageServerDeviceTest] proves the premises - the console script execs and answers over
 * stdio. This proves what the editor plugs into: [StdioLanguageServer] as the app composes it
 * (`baseSpaceLanguageServerPort` does this same wiring), a full session through the port rather
 * than raw frames -
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
      assertTrue("pylsp must declare completionProvider, or completion has no source", capabilities.completionProvider)

      val uri = "file:///home/jupydroid/p5b-probe.py"
      languageServer.openDocument(BASE_SPACE, uri, "python", "import os\nos.path.jo\n")
      val items = languageServer.completion(BASE_SPACE, uri, LanguagePosition(line = 1, character = 10))

      assertTrue(
        "jedi offered nothing for `os.path.jo` - got ${items.map { it.label }}",
        items.any { it.label.startsWith("join") },
      )
      // The row reads the signature-resolved label and is ordered/tinted by the server's own
      // sortText/kind - [LspSurfaceProbeTest] measured kind=3, sortText="ajoin" for this item.
      val join = items.first { it.label.startsWith("join") }
      assertEquals("pylsp must name join a function (CompletionItemKind 3)", 3, join.kind)
      assertTrue("pylsp must send sortText for ordering - got ${join.sortText}", !join.sortText.isNullOrEmpty())
    } finally {
      languageServer.stopAll()
    }
  }

  /**
   * A `didOpen` carrying an undefined name makes the real `pylsp` push a
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
      languageServer.stopAll()
    }
  }

  /**
   * `textDocument/hover` on a name the document defines makes the real `pylsp` answer
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

      // pylsp's own off-symbol reply is `{"contents": ""}` (its hover.py), which the wire
      // reads as nothing-to-show rather than an empty card.
      val nothing = languageServer.hover(BASE_SPACE, uri, LanguagePosition(line = 2, character = 0))
      assertEquals("an empty line has no symbol to describe", null, nothing)
    } finally {
      languageServer.stopAll()
    }
  }

  /**
   * `textDocument/signatureHelp` inside `os.path.join(` makes the real `pylsp` answer jedi's
   * signature - the reply [LspSurfaceProbeTest] measured byte-for-byte: label
   * `join(path: LiteralString, /, *paths: LiteralString) -> LiteralString`, string-labeled
   * `path`/`paths` parameters, `activeSignature`/`activeParameter` zero. A scripted server
   * can only pretend to know a signature; this is the knowing.
   */
  @Test(timeout = 120_000)
  fun signatureHelpInsideACall_answersTheSignature() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    try {
      val capabilities = languageServer.capabilities(BASE_SPACE)
      assertTrue(
        "pylsp must declare signatureHelpProvider, or the signature popup has nothing to show",
        capabilities.signatureHelpProvider,
      )

      val uri = "file:///home/jupydroid/p5e4-probe.py"
      languageServer.openDocument(BASE_SPACE, uri, "python", "import os\nos.path.join(\"a\", \"b\")\n")

      // `os.path.join(` ends at character 13 on line 1; 14 sits the caret inside the call,
      // exactly where the probe asked and got `join(path…)`.
      val help = languageServer.signatureHelp(BASE_SPACE, uri, LanguagePosition(line = 1, character = 14))

      assertTrue("pylsp answered nothing inside os.path.join(", help != null)
      val signature = help!!.signatures.first()
      assertTrue("expected a join( signature - got ${signature.label}", signature.label.startsWith("join("))
      assertEquals("path", signature.parameters.first().label)
    } finally {
      languageServer.stopAll()
    }
  }

  /**
   * `textDocument/definition` on a call site makes the real `pylsp` answer jedi's
   * `Location[]` - the reply [LspSurfaceProbeTest] measured byte-for-byte: one location
   * back into the same
   * document, its range covering the name in the `def` line (chars 4-17 there -
   * `def compute_total`). A scripted server can only pretend to know where a name is
   * defined; this is the knowing.
   */
  @Test(timeout = 120_000)
  fun definitionOnACallSite_answersTheDefLine() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    try {
      val capabilities = languageServer.capabilities(BASE_SPACE)
      assertTrue(
        "pylsp must declare definitionProvider, or go-to-definition has nothing to ask",
        capabilities.definitionProvider,
      )

      val uri = "file:///home/jupydroid/p5e5-probe.py"
      languageServer.openDocument(
        BASE_SPACE,
        uri,
        "python",
        "def compute_total(values):\n    return sum(values)\n\ncompute_total([1, 2])\n",
      )

      // Character 4 of line 3 is on `compute_total` - the call site a caret sits inside.
      val locations = languageServer.definition(BASE_SPACE, uri, LanguagePosition(line = 3, character = 4))

      assertTrue("pylsp answered no locations for compute_total", locations.isNotEmpty())
      val target = locations.first()
      assertEquals("the definition must point back into this document", uri, target.uri)
      assertEquals(0, target.range.start.line)
      assertEquals("the name in `def compute_total` starts at character 4", 4, target.range.start.character)
      assertEquals("and ends at 17 - the probe's exact range", 17, target.range.end.character)
    } finally {
      languageServer.stopAll()
    }
  }

  /**
   * `textDocument/rangeFormatting` over a sloppily-spaced document makes the real
   * `pylsp` answer through autopep8, whose `pylsp_format_range` answers ONE whole-document
   * `TextEdit` even for a range ask
   * (the p5e6-format flow's `lsp.answer edits:1`). The cell-scoped slicing that makes a
   * whole-document edit safe for one cell is `NotebookVirtualDocument`'s job and its own
   * tests; what a scripted server cannot fake is autopep8 rewriting `x=1` to `x = 1` at all.
   */
  @Test(timeout = 120_000)
  fun rangeFormattingOnSloppySpacing_answersAutopep8sWholeDocumentEdit() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    try {
      val capabilities = languageServer.capabilities(BASE_SPACE)
      assertTrue(
        "pylsp must declare documentRangeFormattingProvider, or \"Format cell\" has nothing to call",
        capabilities.documentRangeFormattingProvider,
      )

      val uri = "file:///home/jupydroid/p5e6-probe.py"
      languageServer.openDocument(BASE_SPACE, uri, "python", "x=1\ny=2\n")

      val edits = languageServer.rangeFormatting(
        BASE_SPACE,
        uri,
        LanguageRange(LanguagePosition(0, 0), LanguagePosition(1, 3)),
        IndentUnit.SPACES_4.formattingOptions(),
      )

      assertTrue("pylsp answered no edits for `x=1`", edits.isNotEmpty())
      assertTrue(
        "autopep8's answer must carry the E225 fix - got ${edits.map { it.newText.take(60) }}",
        edits.any { it.newText.contains("x = 1") && it.newText.contains("y = 2") },
      )
    } finally {
      languageServer.stopAll()
    }
  }

  /**
   * `textDocument/rename` on a call site makes the real `pylsp` answer jedi's
   * `WorkspaceEdit` - the reply [LspSurfaceProbeTest] measured byte-for-byte:
   * `documentChanges` carrying ONE
   * whole-document `TextEdit` whose `newText` holds the new name at the `def` line and the
   * call site both. A scripted server can only pretend to rename; this is the rename.
   */
  @Test(timeout = 120_000)
  fun renameOnACallSite_answersTheWholeDocumentEdit() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    try {
      val capabilities = languageServer.capabilities(BASE_SPACE)
      assertTrue("pylsp must declare renameProvider, or rename has nothing to call", capabilities.renameProvider)

      val uri = "file:///home/jupydroid/p5e7-probe.py"
      languageServer.openDocument(
        BASE_SPACE,
        uri,
        "python",
        "def compute_total(values):\n    return sum(values)\n\ncompute_total([1, 2])\n",
      )

      // Character 4 of line 3 is on `compute_total` - the call site a caret sits inside.
      val edit = languageServer.rename(BASE_SPACE, uri, LanguagePosition(line = 3, character = 4), "summed")

      // Flattened rather than `changes[uri]`: the reply's document uri is the server's own
      // quoting of the opened one, which only has to round-trip inside the same `file://`.
      val newTexts = edit.changes.values.flatten().map { it.newText }
      assertTrue("pylsp answered no edits for compute_total", newTexts.isNotEmpty())
      assertTrue(
        "the one edit must rename the def and the call site - got ${newTexts.map { it.take(60) }}",
        newTexts.any { it.contains("def summed") && it.contains("summed([1, 2])") },
      )
    } finally {
      languageServer.stopAll()
    }
  }

  /** Lazy means lazy: a port never called leaves no process behind to stop. */
  @Test(timeout = 60_000)
  fun stopOnANeverStartedServerIsANoOp() = runBlocking {
    val languageServer = baseSpaceLanguageServerPort(context)!!
    languageServer.stopAll()
  }

  /** No syscall was refused while the adapter ran a session - [LanguageServerDeviceTest]'s bar. */
  @Test
  fun nothingWasRefusedWhileTheAdapterRan() {
    assertEquals(
      "seccomp refused a syscall other than mbind while pylsp ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }
}
