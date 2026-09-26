package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.flow.SharedFlow

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
  /**
   * The LSP `CompletionItemKind` number - what tints the row's glyph. Null when the server
   * omitted it; the P5e-0 probe measured pylsp sending `3` (Function) for `join`.
   */
  val kind: Int? = null,
  /**
   * The order the server stated the row in - pylsp emits things like `ajoin`, and VS Code's
   * `compareItems` sorts on it before falling back to the label.
   */
  val sortText: String? = null,
)

/** Which markup a payload speaks - LSP's `MarkupKind`, spelled the way Kotlin spells enums. */
enum class LanguageMarkupKind { MARKDOWN, PLAINTEXT }

/**
 * A `MarkupContent` pair - kind and text - which is also the shape every legacy `contents`
 * form (MarkedString, MarkedString[]) is normalized into at the wire.
 */
data class LanguageMarkup(val kind: LanguageMarkupKind, val value: String)

/**
 * A `textDocument/hover` reply: what the symbol under the position is, marked up, and the
 * range it covers when the server stated one. `contents` is the whole answer - pylsp fills it
 * with a fenced signature block plus the docstring.
 */
data class LanguageHover(val contents: LanguageMarkup, val range: LanguageRange? = null)

/**
 * The slice of `initialize`'s reply a caller may gate a feature on, grown as features land.
 * [positionEncoding] names the unit [LanguagePosition.character] counts - declared
 * `utf-16`, the protocol default, unless the server negotiated otherwise.
 */
data class LanguageCapabilities(val completionProvider: Boolean, val positionEncoding: String)

/**
 * One entry a `publishDiagnostics` carries: where, how bad, what it says. [severity] is the
 * LSP `DiagnosticSeverity` number - 1 error, 2 warning, 3 information, 4 hint - normalized
 * to 1 when the server omitted it, the protocol's own default.
 */
data class LanguageDiagnostic(
  val range: LanguageRange,
  val severity: Int,
  val code: String? = null,
  val source: String? = null,
  val message: String,
)

/** A `textDocument/publishDiagnostics` push: every diagnostic the server reports for [uri]. */
data class PublishDiagnostics(val uri: String, val diagnostics: List<LanguageDiagnostic>)

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
   * `textDocument/hover` at [position] in the document at [uri] - `docs/PLAN.md` P5e-3.
   * Null is a real answer, not a failure: the server says nothing where nothing is defined.
   */
  suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover?

  /**
   * Every `textDocument/publishDiagnostics` a running server pushes, typed - the stream the
   * reference's `ILSPFeatureOptions`' diagnostic signal feeds a gutter from. Hot: a
   * publication made while nothing listens is gone, which is also the signal's semantics.
   */
  val diagnostics: SharedFlow<PublishDiagnostics>

  /**
   * `shutdown` + `exit`, the process ended - P1's rule for a subprocess this app owns: it dies
   * with the Space, never outliving it. A [space] whose server never ran stops as a no-op.
   */
  suspend fun stop(space: Space)
}
