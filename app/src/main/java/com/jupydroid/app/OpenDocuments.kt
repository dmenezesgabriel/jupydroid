package com.jupydroid.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.flow.StateFlow

/**
 * A document a screen is showing, addressed the way a replace or a **close** has to address it.
 *
 * `ITextModelService`'s side of the seam: `BulkTextEdits` resolves a text model for **every**
 * resource it is about to edit, open or not (`bulkTextEdits.ts:227`), edits the models, and only
 * then saves (`replaceService.ts:136`). A replace that wrote the file instead would be overwritten
 * by the open editor's next debounced save, and the reader would watch their replacement vanish.
 *
 * **A cell is a resource, which is the reference's own arrangement rather than a convenience
 * here.** `ReplaceService.createEdits` builds an edit against `match.cell?.uri` for a notebook
 * match and against the file for anything else, and a notebook cell genuinely has a URI of its own
 * (`CellUri.parse`). So [cell] is null for the file itself and an index for one cell of a notebook -
 * the same index `MatchedLine.cell` carries, and for the same reason it carries an index rather
 * than an id (see `MatchedLine`).
 *
 * **The close half is the tab strip's**: a tab's dirty dot and `saveBeforeClose` have to
 * reach a document whose screen may not even be composed - a tab beneath the top is out of
 * composition while its ViewModel lives on, which is exactly what this registry already exists
 * for. `dirty` is a [StateFlow] because the dot has to *follow* the save, and [saveNow]'s callback
 * is the flush's own continuation - `context.save()` rejecting keeps the widget open
 * (`widgetmanager.ts:353-365`), so the caller only learns of the close that may proceed.
 *
 * Implemented by the ViewModels themselves rather than by an adapter around them: [path] has to be
 * read **live**, because a rename moves the file while the document carries on, and the ViewModel
 * is what learns of that (`Context._updatePath`).
 */
interface OpenDocument {
  /**
   * Where this document lives **now** - see the interface docstring for why that word matters.
   *
   * Backed by snapshot state, so a composition that read it redraws when a rename moves it: a tab
   * title is `title.label` following `pathChanged` (`packages/docregistry/src/default.ts`), on
   * top or beneath (`RenamedTabBeneathTest`).
   */
  val path: String

  /**
   * Where it was opened - what the *navigation key* still carries, immutable. A tab's key keeps
   * it while [path] moves under a rename, so this is how the strip finds the document its key
   * opened - the inverse of [OpenDocuments.at]'s lookup by live path.
   */
  val openedAs: DocumentOpening

  /** Whether the document owes a write - `model.dirty`, live so a tab's dot follows the save. */
  val dirty: StateFlow<Boolean>

  /**
   * `docmanager:save`: the owed write is made first and [onSaved] runs only if it left nothing
   * dirty. On the way out that is the close itself - a failed save keeps the document open, as
   * `saveBeforeClose`'s does - and from the strip's save icon there is nothing to run afterwards,
   * because the document stays.
   */
  fun saveNow(onSaved: () -> Unit)

  /** The close dialog's Discard - `SaveHandler.dispose()`: owed work dropped, nothing writes again. */
  fun discardChanges()

  /**
   * `docmanager:reload` - `context.revert()`: the file is read back over what is open, and a
   * write the document owed is dropped with the edit it was for. Asking first is the caller's,
   * as it is the command's rather than the context's (jupyterlab@7f0e690
   * `packages/docmanager-extension/src/index.tsx`).
   */
  fun reloadFromDisk()

  /** The text of the resource [cell] names, or null when this document has no such resource. */
  fun sourceOf(cell: Int?): String?

  /** Rewrites the resource [cell] names. What that costs - a save, a revision - is the document's. */
  fun applyEdits(cell: Int?, edits: List<TextEdit>)
}

/**
 * Which open a tab is: the path it was opened under, and which of that path's opens.
 *
 * The path alone is not an identity once a rename moves a document off it: a file recreated at
 * the old name is a second document opened under the same path. jupyterlab@7f0e6906 gives every
 * main-area widget an id of its own for the same reason (`packages/apputils/src/mainareawidget.ts`,
 * `DOMUtils.createDomID`); [ordinal] is that id narrowed to what has to stay unique - the tabs open
 * on one path at once, since a navigation entry's `ViewModelStore` is keyed by its key.
 *
 * ```
 * DocumentOpening("a.ipynb")      // the first tab opened on a.ipynb
 * DocumentOpening("a.ipynb", 1)   // a.ipynb recreated while that tab, renamed, is still open
 * ```
 */
data class DocumentOpening(val path: String, val ordinal: Int = 0)

/**
 * The documents open right now, so something that is not their screen can reach them by path.
 *
 * **A map-shaped holder, because the back stack holds every open document**
 * (`NotebookBackStack` stacks rather than replaces, the way `shell.add(widget, 'main')` adds a
 * tab). Order is open order - the order a tab strip would draw - and lookup is by each
 * document's *live* [OpenDocument.path], because a rename moves the file while the document
 * carries on: the reference's `_contextsForPath` filters contexts by their current path for the
 * same reason.
 *
 * The same holder shape as [OpenDocumentOutline], for the same reason: the search panel and the
 * document are different `NavEntry`s, and every entry owns its own `ViewModelStore`
 * (`docs/architecture.md` §4), so neither can reach the other's state without something between
 * them.
 *
 * ```
 * val open = remember { OpenDocuments() }      // MainNavigation
 * open.at("reports/a.py")?.applyEdits(null, edits)
 * ```
 */
@Stable
class OpenDocuments {
  private val documents = mutableStateListOf<OpenDocument>()

  /** Every open document, oldest first - the tab order, once there is a strip to draw it. */
  val all: List<OpenDocument> get() = documents.toList()

  /** The document open on [path], or null when nothing is - then the engine reads the file. */
  fun at(path: String): OpenDocument? = documents.firstOrNull { it.path == path }

  /**
   * The document a tab opened as [opening] - where it started, not where it lives. A tab's key
   * knows only that, so this is the lookup that survives the document's own rename - including
   * one that landed while its screen was out of composition (`RecreatedPathOpeningTest`).
   */
  fun openedAs(opening: DocumentOpening): OpenDocument? = documents.firstOrNull { it.openedAs == opening }

  /**
   * A screen's document is ready. Registered **by identity**: a ViewModel is created once per
   * `NavEntry`, so a repeated call (a recomposition that re-runs `init`'s path, say) is not a
   * second document.
   */
  fun opened(document: OpenDocument) {
    if (documents.none { it === document }) documents.add(document)
  }

  /**
   * A screen's document has gone.
   *
   * **By identity, and the race is real**: reordering entries to bring a document forward can run
   * an outgoing ViewModel's `onCleared` after the incoming one has registered. Clearing
   * unconditionally there would empty the registry while a document was on screen.
   */
  fun closed(document: OpenDocument) {
    documents.remove(document)
  }
}
