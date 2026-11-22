package com.jupydroid.app.ui.editor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import com.jupydroid.app.domain.model.CellKind
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Appends a cell of [kind] and brings it fully on screen.
 *
 * The footer sits where the new cell lands, at the bottom of the viewport, so a cell taller than the
 * room left there showed only its header. [CellRevealScroll] cannot do this: it scrolls to a cell
 * being typed into, and an appended cell is active in command mode until its field is focused, which
 * is also why stepping the selection must go on scrolling nothing (`CellScrollTargetTest`).
 *
 * ```
 * onAddCell = { kind -> scope.launch { edits.appendCellInView(kind, listState) } }
 * ```
 */
internal suspend fun NotebookCellEdits.appendCellInView(kind: CellKind, listState: LazyListState) {
  val newId = appendCell(kind)
  // Of what is drawn, not of the document: a cell inside a collapsed section never becomes a row,
  // and waiting for it would never end.
  if (uiState.value.cellsOnScreen.any { it.id == newId }) listState.revealItem(newId)
}

/**
 * Scrolls the item keyed [key] whole into view once the list has laid it out, and not at all when it
 * already is - the guard the search and the definition landing use, so a cell the reader can see
 * is not moved.
 *
 * Waits for the key and not for an item count: the footer is an item too, so the count after an
 * append is already past the new row's index before the row exists, and the check then read the
 * footer, which was whole, and scrolled nothing.
 *
 * ```
 * listState.revealItem(newId)
 * ```
 */
internal suspend fun LazyListState.revealItem(key: Any) {
  val laidOut = snapshotFlow { layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } }.filterNotNull().first()
  if (!isFullyVisible(laidOut.index)) animateScrollToItem(laidOut.index)
}
