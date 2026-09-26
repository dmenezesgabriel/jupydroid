package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.BASE_SPACE
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

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

  protected companion object {
    const val URI = "file:///home/jupydroid/contract.py"
    const val LANGUAGE_ID = "python"
  }
}
