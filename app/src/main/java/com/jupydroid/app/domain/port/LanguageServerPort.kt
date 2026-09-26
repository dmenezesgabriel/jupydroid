package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Space

/**
 * A position the way LSP speaks it: zero-based [line], and [character] counted in the encoding
 * negotiated at `initialize` - `utf-16` unless the server says otherwise, which is the one count
 * a text field's own indices already are.
 */
data class LanguagePosition(val line: Int, val character: Int)

data class LanguageRange(val start: LanguagePosition, val end: LanguagePosition)

/** A `CompletionItem.textEdit`: what accepting the match writes, and the range it replaces. */
data class LanguageEdit(val range: LanguageRange, val newText: String)

/**
 * One entry a `textDocument/completion` reply carries. [insertText] is the ordinary
 * `insertText`-or-`label` answer; [edit] is set when the server stated an explicit range -
 * which is how `np.ar` completing to `arange` does not become `np.np.arange`
 * ([CompletionResult]'s own reason for carrying the span).
 */
data class LanguageCompletion(
  val label: String,
  val insertText: String,
  val detail: String? = null,
  val edit: LanguageEdit? = null,
)

/**
 * The slice of `initialize`'s reply a caller may gate a feature on, grown as features land.
 * [positionEncoding] names the unit [LanguagePosition.character] counts - declared
 * `utf-16`, the protocol default, unless the server negotiated otherwise.
 */
data class LanguageCapabilities(val completionProvider: Boolean, val positionEncoding: String)

/**
 * One language server per [Space] - `pylsp` over stdio today (route 2, `docs/PLAN.md` P5),
 * shaped so the reference's `jupyter-lsp` websocket route (route 1) stays a swap behind it.
 *
 * The surface mirrors `ILSPConnection` (`packages/lsp/src/connection.ts`), the boundary
 * JupyterLab's own adapters drive: the server starts lazily on first use rather than at app
 * start, `didOpen`s queued behind a not-yet-finished `initialize` flush once it lands
 * (`sendOpenWhenReady`), a change to a document never opened implies the open (`_sendChange`),
 * and text syncs whole - a cell is small enough that computing deltas buys nothing, the
 * reference's own `sendFullTextChange`.
 *
 * ```
 * languageServer.completion(space, "file:///nb.py", LanguagePosition(2, 10))
 * ```
 */
interface LanguageServerPort {
  /**
   * The server's `initialize` answer, from a running server - starting it lazily if this is
   * the first call for [space]. Called on every operation anyway, so a caller asserting
   * readiness pays nothing after the first.
   */
  suspend fun capabilities(space: Space): LanguageCapabilities

  /** `textDocument/didOpen`; [uri] is a `file://`-style document identifier, [languageId] `python`. */
  suspend fun openDocument(space: Space, uri: String, languageId: String, text: String)

  /**
   * `textDocument/didChange`, the document's whole new text. Opens [uri] first when the server
   * has not seen it - `_sendChange`'s own rule, so a sync can never be the first thing a
   * document hears.
   */
  suspend fun changeDocument(space: Space, uri: String, text: String)

  suspend fun closeDocument(space: Space, uri: String)

  /** `textDocument/completion` at [position] in the document at [uri]. */
  suspend fun completion(space: Space, uri: String, position: LanguagePosition): List<LanguageCompletion>

  /**
   * `shutdown` + `exit`, the process ended - P1's rule for a subprocess this app owns: it dies
   * with the Space, never outliving it. A [space] whose server never ran stops as a no-op.
   */
  suspend fun stop(space: Space)
}
