package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which table-of-contents entries are folded, and the two ways that changes.
 *
 * **The ToC's own state and not the notebook's.** `syncCollapseState` defaults to false
 * (`packages/toc-extension/schema/registry.json:32-69`), so folding an entry here hides rows in
 * that list and leaves `jp-MarkdownHeadingCollapsed` - the notebook's own section fold, which
 * [withHeadingCollapsed] writes - completely alone.
 *
 * **Held rather than persisted**, which is the reference's own lifetime: it rebuilds this on every
 * `getHeadings()` and never saves it (`packages/notebook/src/toc.ts:274,293`).
 *
 * Its own type rather than two lambdas beside the screen: both writes are small, both are about
 * one set, and putting them here is what keeps `EditorScreen` under detekt's complexity gate.
 *
 * ```
 * val folds = remember { TocFolds() }
 * folds.toggleAll(headings)   // toc:toggle-collapse
 * ```
 */
@Stable
class TocFolds {
  var collapsed by mutableStateOf(emptySet<String>())
    private set

  /** One entry's disclosure. An id nothing answers to is inert, as everywhere in this package. */
  fun toggle(cellId: String) {
    collapsed = if (cellId in collapsed) collapsed - cellId else collapsed + cellId
  }

  /**
   * `toc:toggle-collapse` - one command whose half is picked by `someExpanded`
   * (`packages/toc-extension/src/index.ts:174-210`): anything still open folds the rest, and only a
   * fully folded tree unfolds.
   */
  fun toggleAll(headings: List<TocHeading>) {
    collapsed = if (headings.tocSomeExpanded(collapsed)) headings.tocSectionOwners() else emptySet()
  }
}
