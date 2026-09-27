package com.jupydroid.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.jupydroid.app.domain.model.TextEdit

/**
 * A document a screen is showing, addressed the way a replace has to address it.
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
 * Implemented by the ViewModels themselves rather than by an adapter around them: [path] has to be
 * read **live**, because a rename moves the file while the document carries on, and the ViewModel
 * is what learns of that (`Context._updatePath`).
 */
interface OpenDocument {
  /** Where this document lives **now** - see the interface docstring for why that word matters. */
  val path: String

  /** The text of the resource [cell] names, or null when this document has no such resource. */
  fun sourceOf(cell: Int?): String?

  /** Rewrites the resource [cell] names. What that costs - a save, a revision - is the document's. */
  fun applyEdits(cell: Int?, edits: List<TextEdit>)
}

/**
 * The documents open right now, so something that is not their screen can reach them by path.
 *
 * **A map-shaped holder, because the back stack now holds every open document** (P8d -
 * `NotebookBackStack` stacks rather than replaces, the way `shell.add(widget, 'main')` adds a
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
