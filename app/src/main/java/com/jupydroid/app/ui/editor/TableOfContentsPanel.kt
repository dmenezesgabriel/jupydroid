package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * `toc:show-panel`, on whichever surface the window has room for - [CellActionSurface]'s own rule
 * and the same reason: a `ModalBottomSheet` anchored nowhere in particular is heavier than the job
 * needs once there is room for a `DropdownMenu` anchored to the button that opened it. Reusing
 * [CellActionPresentation] rather than inventing a second width rule for the same picker shape.
 *
 * `packages/toc-extension/src/index.ts:161-172` just activates the reference's own sidebar tab;
 * there is no sidebar here, so this is the **narrow** adaptation of it - an editor with room for a
 * column gets [TableOfContentsPane] instead, which is the reference's own persistent shape.
 * [tocPresentationFor] is the rule, and it measures the editor rather than the window.
 *
 * The flat list is drawn indented by level rather than folded into a nested tree: the model stays
 * exactly [EditorUiState.tableOfContentsHeadings]'s own shape, and indentation is all a render
 * needs before collapse (`toc:toggle-collapse`, P2h-4) gives a reason to nest it.
 *
 * Picking a row both jumps to it and closes the surface, in the one step [CellActionSurface]'s own
 * `choose` already establishes for a picker here - so a caller never has to remember the second
 * half itself.
 *
 * Called unconditionally by [ViewMenu], inside the same `Box` its own button anchors from -
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
  presentation: CellActionPresentation = currentCellActionPresentation(),
) {
  if (!expanded) return
  val choose: (String) -> Unit = { cellId ->
    onHeadingSelected(cellId)
    onDismiss()
  }
  when (presentation) {
    CellActionPresentation.MENU -> TableOfContentsMenu(headings, onDismiss, choose)
    CellActionPresentation.SHEET -> TableOfContentsSheet(headings, onDismiss, choose)
  }
}

@Composable
private fun TableOfContentsMenu(headings: List<TocHeading>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
  DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
    TableOfContentsRows(headings, onChoose)
  }
}

/**
 * `navigationBarsPadding` for [CellActionSheet]'s own reason: the sheet draws to the bottom edge
 * under edge-to-edge, and the last row would otherwise sit under the gesture bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableOfContentsSheet(headings: List<TocHeading>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
    ) {
      TableOfContentsRows(headings, onChoose)
    }
  }
}

/**
 * The rows themselves, shared with [TableOfContentsPane] - the two surfaces differ in where they
 * are drawn and whether a pick closes them, never in what a heading looks like or is called.
 */
@Composable
internal fun TableOfContentsRows(headings: List<TocHeading>, onChoose: (String) -> Unit) {
  if (headings.isEmpty()) {
    EmptyTableOfContents()
    return
  }
  headings.forEach { heading ->
    DropdownMenuItem(
      text = { Text(heading.text, modifier = Modifier.padding(start = tocIndent(heading.level))) },
      onClick = { onChoose(heading.cellId) },
      modifier = Modifier.fillMaxWidth().semantics { contentDescription = tocRowDescription(heading) },
    )
  }
}

@Composable
private fun EmptyTableOfContents() {
  Text(
    text = "No headings in this notebook",
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(16.dp),
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
