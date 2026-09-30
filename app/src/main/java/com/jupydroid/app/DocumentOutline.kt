package com.jupydroid.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jupydroid.app.ui.editor.TocHeading
import com.jupydroid.app.ui.editor.TocNumbering

/**
 * What the side panel needs to draw the open document's structure, and to reach a piece of it.
 *
 * This is the reference's `TableOfContents.Model` seen from the panel's side: the panel holds one
 * and reads it, and holds `null` when no document offers one (`packages/toc/src/panel.ts:41-60`).
 * The [title] is here because the panel's header takes it - `this._model?.title ?? 'Table of
 * Contents'` (`panel.ts:77-80`).
 *
 * Named for a *document* rather than for a notebook because the reference's own placeholder is:
 * *"The table of contents shows headings in notebooks and supported files"* (`panel.ts:32-36`).
 */
@Stable
class DocumentOutline(
  val title: String,
  val headings: List<TocHeading>,
  val numbering: TocNumbering,
  /**
   * Reaches the cell a heading was read from: reveals it when an ancestor's collapsed section
   * hides it, then scrolls. `EditorScreen`'s `jumpToHeading`, which is the only thing that can
   * do either - both need this entry's own `LazyListState`.
   */
  val jumpTo: (String) -> Unit,
)

/**
 * Where the open document publishes its outline and the shell's side panel reads it.
 *
 * **It exists because the two are different `NavEntry`s.** The panel is in the list pane and the
 * headings are derived in the detail pane's `EditorViewModel`, and every entry here owns its own
 * `ViewModelStore` (`docs/architecture.md` §4) - so the panel cannot reach the editor's state and
 * must not re-derive it. The reference has the same seam and the same answer: a settable, nullable
 * model, assigned from `labShell.currentChanged` (`packages/toc-extension/src/index.ts:316,353`).
 *
 * **Cleared when the document goes, and that is the half with a rule rather than a value.** The
 * reference disposes the model with the widget - `widget.disposed.connect(() => model?.dispose())`
 * (`index.ts:341-343`) - so a table of contents cannot outlive the document it lists. Ours clears
 * instead of disposing, which is the same observable outcome without leaving a dead object on the
 * panel; the reference's `onConnect` returns early when there is no current widget and so keeps a
 * disposed one (`index.ts:331-334`).
 *
 * Remembered by `MainNavigation` rather than owned by `AppContainer`, and the lifetime is the
 * argument: a process-scoped holder would survive a rotation still carrying a [DocumentOutline.jumpTo]
 * closed over a disposed composition's `LazyListState`.
 *
 * ```
 * val outline = remember { OpenDocumentOutline() }   // MainNavigation
 * outline.current?.headings ?: emptyList()           // the side panel
 * ```
 */
@Stable
class OpenDocumentOutline {
  /** The open document's outline, or null when nothing that has one is open. */
  var current by mutableStateOf<DocumentOutline?>(null)
    private set

  /** The open document offering its structure. Replaces whatever was there, as `toc.model =` does. */
  fun publish(outline: DocumentOutline) {
    current = outline
  }

  /** The document has gone, so the panel empties - see the class docstring for why that is a rule. */
  fun clear() {
    current = null
  }
}
