package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.Spacing
import com.jupydroid.extension.api.FocusRingDropdownMenuItem
import com.jupydroid.extension.api.FocusRingIconButton

/**
 * `toc:show-panel`, on whichever surface the window has room for - [CellActionSurface]'s own rule
 * and the same reason: a `ModalBottomSheet` anchored nowhere in particular is heavier than the job
 * needs once there is room for a `DropdownMenu` anchored to the button that opened it. Reusing
 * [CellActionPresentation] rather than inventing a second width rule for the same picker shape.
 *
 * `packages/toc-extension/src/index.ts:161-172` just activates the reference's own sidebar tab.
 * This is the **narrow** adaptation of it, for a window with no side area to activate: a shell
 * that has one draws `TableOfContentsSidePanel` there instead, which is the reference's own
 * persistent shape, and `onShowTableOfContentsPanel` is what picks between the two.
 *
 * Every entry is drawn, indented by level and never folded: `toc:toggle-collapse` belongs to the
 * pane, because a fold on a surface that is opened, picked from and gone would never survive to be
 * looked at twice - see [TableOfContentsRows].
 *
 * Picking a row both jumps to it and closes the surface, in the one step [CellActionSurface]'s own
 * `choose` already establishes for a picker here - so a caller never has to remember the second
 * half itself.
 *
 * Called unconditionally by [NotebookActionsMenu], inside the same `Box` its own button anchors from -
 * [expanded] is what decides whether anything renders, exactly as [CellActionSurface] itself
 * works, so a `DropdownMenu` here anchors to that button rather than to wherever this composable
 * would otherwise sit on the screen.
 */
@Composable
fun TableOfContentsPanel(
  expanded: Boolean,
  headings: List<TocHeading>,
  onHeadingSelected: (String) -> Unit,
  onDismiss: () -> Unit,
  /** `toc:display-numbering` / `toc:display-h1-numbering` - see [tocPrefixes]. */
  numbering: TocNumbering = TocNumbering(),
  presentation: CellActionPresentation = currentCellActionPresentation(),
) {
  if (!expanded) return
  val choose: (String) -> Unit = { cellId ->
    onHeadingSelected(cellId)
    onDismiss()
  }
  // Nothing is folded here - see [TableOfContentsRows] for why the overlay has no disclosures.
  val rows = headings.tocRows(collapsed = emptySet(), numbering = numbering)
  when (presentation) {
    CellActionPresentation.MENU -> TableOfContentsMenu(rows, onDismiss, choose)
    CellActionPresentation.SHEET -> TableOfContentsSheet(rows, onDismiss, choose)
  }
}

@Composable
private fun TableOfContentsMenu(rows: List<TocRow>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
  DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
    TableOfContentsRows(rows, onChoose)
  }
}

/**
 * `navigationBarsPadding` for [CellActionSheet]'s own reason: the sheet draws to the bottom edge
 * under edge-to-edge, and the last row would otherwise sit under the gesture bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableOfContentsSheet(rows: List<TocRow>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    SheetStatusBarFollowsTheme()
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
    ) {
      TableOfContentsRows(rows, onChoose)
    }
  }
}

/**
 * The rows themselves, shared with `TableOfContentsSidePanel` - the surfaces differ in where they
 * are drawn and whether a pick closes them, never in what a heading looks like or is called.
 *
 * [onToggleCollapsed] is null on the overlay and that is the adaptation rather than an omission:
 * the reference's ToC collapse state is rebuilt on every refresh and never saved
 * (`packages/notebook/src/toc.ts:274,293`), so on a surface that is opened, picked from and gone
 * a fold would never survive to be looked at twice. The side panel is the one that outlives a jump.
 *
 * **A row answers a tap and nothing else.** The reference mounts a context menu on `.jp-tocItem`
 * whose one command is `toc:run-cells` - *"Select and Run Cell(s) for this Heading"*
 * (`toc-extension/schema/registry.json:18-24`, declared at `notebook-extension/index.ts:351`) -
 * and VS Code gives an outline entry the same menu (`notebook.section.*`). There is no row action
 * here at all; `docs/architecture.md` §11's `GAP-044` records it.
 */
@Composable
internal fun TableOfContentsRows(
  rows: List<TocRow>,
  onChoose: (String) -> Unit,
  onToggleCollapsed: ((String) -> Unit)? = null,
) {
  if (rows.isEmpty()) {
    EmptyTableOfContents()
    return
  }
  rows.forEach { row ->
    FocusRingDropdownMenuItem(
      text = { Text(row.prefix + row.heading.text) },
      onClick = { onChoose(row.heading.cellId) },
      // The disclosure goes where the indent would have been, so a row that owns something and one
      // that does not still line up at the same rank.
      leadingIcon = { TocRowDisclosure(row, onToggleCollapsed) },
      modifier = Modifier.fillMaxWidth().semantics { contentDescription = tocRowDescription(row.heading) },
    )
  }
}

/**
 * The chevron that folds an entry's own subtree, drawn only where there is one - the reference's
 * condition too (`packages/toc/src/tocitem.tsx`), and this app's rule for the cell header's
 * collapser: a control for a state the row is not in is one it already refuses to draw.
 *
 * `ChevronRight`/`ExpandMore` rather than `ExpandLess`/`ExpandMore`, which is `CellHeader`'s
 * reasoning one rank along: this is a tree node's disclosure and the row itself stays put.
 *
 * **Its own control inside the row, so a tap on it is not a pick.** The name goes on the button
 * rather than on the icon (ADR-0143) - and names the entry, since a screen reader meets one of these
 * per row.
 */
@Composable
private fun TocRowDisclosure(row: TocRow, onToggleCollapsed: ((String) -> Unit)?) {
  val indent = Modifier.padding(start = tocIndent(row.heading.level))
  if (onToggleCollapsed == null || !row.ownsSection) {
    Spacer(modifier = indent.width(TOC_DISCLOSURE_WIDTH))
    return
  }
  FocusRingIconButton(
    onClick = { onToggleCollapsed(row.heading.key) },
    modifier = indent.width(TOC_DISCLOSURE_WIDTH).semantics {
      contentDescription = if (row.collapsed) "Expand ${row.heading.text}" else "Collapse ${row.heading.text}"
    },
  ) {
    Icon(
      imageVector = if (row.collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
  }
}

@Composable
private fun EmptyTableOfContents() {
  Text(
    text = "No headings in this notebook",
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(Spacing.lg),
  )
}

/** One level's indent, so H2 sits under H1 the way a tree's second rank does. */
private fun tocIndent(level: Int) = ((level - 1) * TOC_INDENT_STEP_DP).dp

/**
 * Named for the accessibility tree as well as the eye, like `cellMenuDescription` - a bare heading
 * text would collide with the same text on the notebook cell behind the surface.
 */
private fun tocRowDescription(heading: TocHeading) = "Table of contents ${heading.text}"

private const val TOC_INDENT_STEP_DP = 16

/** One disclosure's worth of room, kept by the rows that have none so every rank lines up. */
private val TOC_DISCLOSURE_WIDTH = 32.dp
