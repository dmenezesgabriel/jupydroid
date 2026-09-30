package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.IndentUnit
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
 * LSP's `FormattingOptions` - the one required field of `DocumentRangeFormattingParams`, so a
 * `textDocument/rangeFormatting` cannot be sent without it. The editor's own indentation
 * setting supplies it, the way VS Code sends `model.getOptions()`; [insertSpaces] is whether
 * an indent is spaces at all, [tabSize] how many spaces one level means.
 *
 * Read by the shipped `pylsp` and ignored - autopep8 is configured off
 * `pylsp.plugins.pycodestyle.*` (`_autopep8_config` in its plugin, which forwards no indent
 * key). Sent anyway: it is required by the spec, and it is what a spec-conforming server is
 * owed. [IndentUnit.formattingOptions] is where the user's setting lands on it.
 */
data class LanguageFormatOptions(val tabSize: Int, val insertSpaces: Boolean)

/**
 * The user's `indentUnit` as the `options` a `textDocument/rangeFormatting` carries:
 * `insertSpaces` is the unit not being a tab, and `tabSize` is the unit's own width - or the
 * reference's `tabSize` default of 4 for a tab unit (`extension.ts:963-972`), which is the
 * display width a `\t` would stand for even when it is the thing being inserted.
 */
fun IndentUnit.formattingOptions(): LanguageFormatOptions =
  LanguageFormatOptions(tabSize = spaces ?: TAB_DISPLAY_WIDTH, insertSpaces = this != IndentUnit.TAB)

/** The schema's `tabSize` default - a tab's width in spaces, when the unit *is* the tab. */
private const val TAB_DISPLAY_WIDTH = 4

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
   * omitted it; a probe measured pylsp sending `3` (Function) for `join`.
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
 * One `Location` a `textDocument/definition` reply carries: the document [uri] the definition
 * lives in and the [range] that names it - the `targetSelectionRange`-or-`range` VS Code's
 * `_openReference` reads when it jumps there.
 */
data class LanguageLocation(val uri: String, val range: LanguageRange)

/**
 * One parameter of a signature - `ParameterInformation`. [label] is the wire's string form,
 * or the signature label's own substring when the server sent the `[start, end]` pair form;
 * [labelRange] then names those offsets (end-exclusive), which is the only form a highlight
 * can never re-derive wrong - VS Code's `getParameterLabelOffsets` uses them verbatim.
 */
data class LanguageParameter(
  val label: String,
  val labelRange: Pair<Int, Int>? = null,
  val documentation: LanguageMarkup? = null,
)

/**
 * One signature a `textDocument/signatureHelp` reply offers - `SignatureInformation`.
 * [activeParameter] is the per-signature override LSP allows, which VS Code honours before
 * the reply-level index (`signature.activeParameter ?? hints.activeParameter`).
 */
data class LanguageSignature(
  val label: String,
  val parameters: List<LanguageParameter> = emptyList(),
  val documentation: LanguageMarkup? = null,
  val activeParameter: Int? = null,
)

/**
 * A `textDocument/signatureHelp` reply: the signatures the call offers, plus the server's own
 * pick of the active signature and parameter - both optional on the wire, defaulting to 0.
 */
data class LanguageSignatureHelp(
  val signatures: List<LanguageSignature>,
  val activeSignature: Int = 0,
  val activeParameter: Int = 0,
)

/**
 * The slice of `initialize`'s reply a caller may gate a feature on, grown as features land.
 * [positionEncoding] names the unit [LanguagePosition.character] counts - declared
 * `utf-16`, the protocol default, unless the server negotiated otherwise.
 */
data class LanguageCapabilities(
  val completionProvider: Boolean,
  val positionEncoding: String,
  val hoverProvider: Boolean = false,
  /**
   * `signatureHelpProvider` is a boolean **or** an options object (`triggerCharacters`,
   * which pylsp advertises `["(", ",", "="]`), so presence alone is not the test - only
   * absent-or-false is a no, the same rule [completionProvider] already applies.
   */
  val signatureHelpProvider: Boolean = false,
  /**
   * `definitionProvider` answers `true` **or** a `DefinitionOptions` object, so presence is
   * the test like [signatureHelpProvider]'s - pylsp replies a plain `true`, measured.
   */
  val definitionProvider: Boolean = false,
  /**
   * `documentRangeFormattingProvider` takes the same boolean-or-options shape
   * (`DocumentRangeFormattingOptions` is only `workDoneProgress`) - pylsp's `true`, probed.
   */
  val documentRangeFormattingProvider: Boolean = false,
  /**
   * `renameProvider` is a boolean **or** a `RenameOptions` object (`prepareProvider`), so
   * presence is the test like the providers above it - pylsp replies a plain `true`, the
   * measured against it.
   */
  val renameProvider: Boolean = false,
)

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
 * A `WorkspaceEdit` reply (LSP 3.17), normalized to the one question a notebook asks of it:
 * which documents the server wants rewritten, each value the `TextEdit`s for that document.
 * `documentChanges`' `TextDocumentEdit`s and the legacy `changes` map both normalize into
 * [changes]; resource operations (`create`/`rename`/`delete` files) are dropped at the wire,
 * because a cell editor applies text edits and nothing else.
 *
 * Keeping the uri keys is what makes a rename reaching *outside* the asked document - an
 * imported symbol's home in site-packages - visible to the caller rather than applied blind.
 */
data class LanguageWorkspaceEdit(val changes: Map<String, List<LanguageEdit>>)

/**
 * One language server per [Space] - `pylsp` over **stdio** today,
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
   * `textDocument/hover` at [position] in the document at [uri].
   * Null is a real answer, not a failure: the server says nothing where nothing is defined.
   */
  suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover?

  /**
   * `textDocument/signatureHelp` at [position] in the document at [uri].
   * Null where the caret is inside no call the server knows - which is also what an
   * empty `signatures` reply normalizes to.
   */
  suspend fun signatureHelp(space: Space, uri: String, position: LanguagePosition): LanguageSignatureHelp?

  /**
   * `textDocument/definition` at [position] in the document at [uri].
   * LSP allows `Location`, `Location[]` or `LocationLink[]` and `null`; implementations
   * normalize all of it to a location list - empty where nothing defines the symbol.
   */
  suspend fun definition(space: Space, uri: String, position: LanguagePosition): List<LanguageLocation>

  /**
   * `textDocument/rangeFormatting` for [range] in the document at [uri] - the ask behind
   * "Format cell". The caller's [options] are the editor's own
   * indentation settings, sent per spec. Implementations normalize `null` and `[]` replies
   * to an empty edit list - "already formatted" and "nothing to say" are the same answer.
   */
  suspend fun rangeFormatting(
    space: Space,
    uri: String,
    range: LanguageRange,
    options: LanguageFormatOptions,
  ): List<LanguageEdit>

  /**
   * `textDocument/rename` at [position] in the document at [uri] - the ask behind
   * "Rename symbol". The reply is a `WorkspaceEdit`: edits keyed by the document
   * they rewrite, which is what lets a caller apply the asked document's edits and see - not
   * silently apply - a rename reaching into files a notebook does not own. An absent
   * `renameProvider` or an empty reply is an empty edit set, never an error; a server's
   * `error` reply raises [LanguageServerException].
   */
  suspend fun rename(space: Space, uri: String, position: LanguagePosition, newName: String): LanguageWorkspaceEdit

  /**
   * Every `textDocument/publishDiagnostics` a running server pushes, typed - the stream the
   * reference's `ILSPFeatureOptions`' diagnostic signal feeds a gutter from. Hot: a
   * publication made while nothing listens is gone, which is also the signal's semantics.
   */
  val diagnostics: SharedFlow<PublishDiagnostics>

  /**
   * `shutdown` + `exit`, the process ended - the rule for a subprocess this app owns: it dies
   * with the Space, never outliving it. A [space] whose server never ran stops as a no-op.
   */
  suspend fun stop(space: Space)
}
