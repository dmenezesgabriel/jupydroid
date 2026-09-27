package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.BASE_SPACE
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * What every [LanguageServerPort] must do, whoever implements it.
 *
 * Deliberately narrow, the way [KernelDebuggerContract] is: assertions live only where any
 * honest implementation must agree - a server that answers `initialize`, accepts the document
 * verbs, and replies to `completion`. Wire format, framing and process lifecycle are an
 * implementation's own tests' business; a fake is not asked to fake them.
 */
abstract class LanguageServerPortContract {
  protected abstract fun languageServer(): LanguageServerPort

  @Test
  fun capabilities_reportsWhetherCompletionsAreOffered() = runBlocking {
    val capabilities = languageServer().capabilities(BASE_SPACE)

    // Every implementation states the capability surface honestly; the stub and the fake
    // both declare completion support, which is the feature P5c plugs into.
    assertTrue(capabilities.completionProvider)
  }

  @Test
  fun openDocument_thenCompletionAnswersItems() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(BASE_SPACE, URI, LANGUAGE_ID, "import os\nos.\n")

    val items = languageServer.completion(BASE_SPACE, URI, LanguagePosition(line = 1, character = 3))

    assertTrue("expected at least one completion item", items.isNotEmpty())
    assertTrue(items.all { it.label.isNotEmpty() })
  }

  @Test
  fun completion_itemsCarryAnInsertableText() = runBlocking {
    val items = languageServer().completion(BASE_SPACE, URI, LanguagePosition(line = 0, character = 0))

    // `insertText`-or-`label` is the LSP rule; every item must reduce to something a cell can
    // take, or the popup has nothing to insert on a tap.
    assertTrue(items.all { it.insertText.isNotEmpty() })
  }

  /**
   * `textDocument/hover` on a symbol the document carries answers its `LanguageHover` - P5e-3.
   * Every implementation's default answers one: the scripted server's canned reply and the
   * fake's configured answer alike, since a null answer would only say "nothing is here" about
   * a document that plainly defines `x`.
   */
  @Test
  fun hover_onADefinedName_answers() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(BASE_SPACE, URI, LANGUAGE_ID, "x = 1\n")

    val hover = languageServer.hover(BASE_SPACE, URI, LanguagePosition(line = 0, character = 0))

    assertTrue("a hover answer carries the markup it was asked about", hover != null)
    assertTrue(hover!!.contents.value.isNotEmpty())
  }

  /**
   * `textDocument/signatureHelp` inside a call answers its `LanguageSignatureHelp` - P5e-4.
   * Every implementation's default answers like the probe's `join`: the scripted server's
   * canned reply and the fake's configured answer alike.
   */
  @Test
  fun signatureHelp_insideACall_answers() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(BASE_SPACE, URI, LANGUAGE_ID, "import os\nos.path.join()\n")

    val help = languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(line = 1, character = 12))

    assertTrue("a signature help answer carries the signatures it was asked about", help != null)
    assertTrue(help!!.signatures.all { it.label.isNotEmpty() })
  }

  /**
   * `textDocument/definition` on a name the document defines answers `Location`s - P5e-5.
   * Every implementation's default answers one the probe's shape measured off real `pylsp`:
   * the scripted server's canned reply and the fake's configured answer alike, since an empty
   * answer would only say "nothing defines this" about a document that plainly defines
   * `compute_total`.
   */
  @Test
  fun definition_atADefinedName_answersALocationInTheDocument() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(
      BASE_SPACE,
      URI,
      LANGUAGE_ID,
      "def compute_total(items):\n    return sum(items)\n\ntotal = compute_total([1, 2])\n",
    )

    val locations = languageServer.definition(BASE_SPACE, URI, LanguagePosition(line = 3, character = 8))

    assertTrue("a definition reply is the locations it was asked about", locations.isNotEmpty())
    assertTrue(locations.all { it.uri.isNotEmpty() })
    assertTrue(locations.all { it.range.start.line >= 0 })
  }

  /**
   * `textDocument/rename` on a name the document defines answers a `WorkspaceEdit` naming it -
   * P5e-7. Every implementation's default answers like the probe's `documentChanges` reply:
   * the scripted server's canned `TextDocumentEdit` and the fake's configured answer alike,
   * since an empty answer would only say "nothing to change" about a document that plainly
   * renames `compute_total`.
   */
  @Test
  fun rename_atADefinedName_answersEditsForTheDocument() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(
      BASE_SPACE,
      URI,
      LANGUAGE_ID,
      "def compute_total(items):\n    return sum(items)\n\ntotal = compute_total([1, 2])\n",
    )

    val edit = languageServer.rename(BASE_SPACE, URI, LanguagePosition(line = 3, character = 8), "compute_sum")

    assertTrue(
      "a rename reply is the edits for the document it was asked about - got ${edit.changes.keys}",
      edit.changes[URI].orEmpty().isNotEmpty(),
    )
  }

  /** The document verbs are a lifecycle, not calls that may throw: open, change, close. */
  @Test
  fun documentLifecycle_isAccepted() = runBlocking {
    val languageServer = languageServer()

    languageServer.openDocument(BASE_SPACE, URI, LANGUAGE_ID, "x = 1\n")
    languageServer.changeDocument(BASE_SPACE, URI, "x = 2\n")
    languageServer.closeDocument(BASE_SPACE, URI)
  }

  /**
   * A change to a document never opened implies the open - `_sendChange`'s own rule
   * (`packages/lsp/src/connection.ts:661`), so syncing is legal before any explicit open.
   */
  @Test
  fun changeDocument_toAnUnopenedUriIsAccepted() = runBlocking {
    languageServer().changeDocument(BASE_SPACE, URI, "y = 1\n")
  }

  /** P1's rule, contract-side: stopping never complains, and a stopped server may start again. */
  @Test
  fun stop_thenUseRestartsLazily() = runBlocking {
    val languageServer = languageServer()
    languageServer.capabilities(BASE_SPACE)
    languageServer.stop(BASE_SPACE)

    val items = languageServer.completion(BASE_SPACE, URI, LanguagePosition(line = 0, character = 0))

    assertTrue(items.isNotEmpty())
    languageServer.stop(BASE_SPACE)
  }

  /**
   * A `textDocument/publishDiagnostics` the server pushes arrives on [diagnostics] typed -
   * uri, range, severity, message - rather than as raw frames each consumer parses again.
   */
  @Test
  fun diagnostics_aServerPushArrivesOnTheFlow() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(BASE_SPACE, URI, LANGUAGE_ID, "x = 1\n")

    // Subscribe before pushing: the stream is hot - a publication made while nothing
    // listens is gone, which is also the signal's own semantics.
    val received = async { languageServer.diagnostics.first { it.uri == URI } }
    yield()
    pushDiagnostics(URI)

    val pushed = withTimeout(10.seconds) { received.await() }
    assertEquals(URI, pushed.uri)
    assertTrue(pushed.diagnostics.all { it.message.isNotEmpty() })
  }

  /** A server-side `publishDiagnostics` for [uri], however the implementation fakes one. */
  protected abstract suspend fun pushDiagnostics(uri: String)

  protected companion object {
    const val URI = "file:///home/jupydroid/contract.py"
    const val LANGUAGE_ID = "python"
  }
}
