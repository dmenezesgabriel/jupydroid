package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The chevron that folds a heading's section away, drawn **beside the heading it folds** - and
 * only while that heading is rendered.
 *
 * **Rendered only is P2v-2's, and it departs from the reference.** JupyterLab's prompt node carries
 * the caret in both states: `InputArea` builds it once and hides only the *editor* when the cell
 * renders (`packages/cells/src/inputarea.ts:53-56,111-119`), which P2n-3 transcribed. Composing it
 * beside an open source costs 48dp of the editor's width - measured, 80dp against 32dp
 * (`MarkdownCellTest.anOpenHeadingsSourceStartsWhereProseDoes`) - for an affordance nobody can use
 * mid-typing, since folding the section would close what is being written. ADR-0057 carries the
 * trade and its cost. [MarkdownCell] is where the state is decided; this composable is
 * unconditional and knows nothing about it.
 *
 * **Where it goes is the reference's, and this app had it somewhere else.** JupyterLab appends the
 * caret to the cell's *prompt node* (`packages/cells/src/widget.ts:2386-2408`) and pins it at
 * `position: absolute; right: 0; top: 0; bottom: 0` (`packages/cells/style/widget.css:154-168`) -
 * the right edge of the 64px prompt column, which is immediately to the left of the content. Six
 * per-level rules then set `background-position-y: calc(0.3 * <that level's font size>)` so the
 * 25px caret keeps to the heading's **first line** as the heading shrinks: at `h1`'s 29px in a
 * ~43px line box that is an icon centre of 21.2px against a line centre of 21.5px.
 *
 * This app drew it in its own header strip instead - a row above the heading, beside the Run
 * control, with nothing connecting it to what it folds
 * (before P2n-3).
 *
 * **IntelliJ is empty for this and it was checked**, so nobody searches it again: everything under
 * `notebooks/` that collapses collapses *outputs* (`CollapsingComponent`,
 * `NotebookOutputCollapseAllInSelectedCellsAction`). Markdown heading folding is in its
 * closed-source Jupyter plugin, the same gap that made `EXECUTION_TIME_COLOR`'s placement
 * unreadable.
 *
 * A tree node's disclosure rather than a card's expander, which is what the two icon pairs mean in
 * Material: `ChevronRight` says "there is a level under this one, closed", `ExpandMore` says it is
 * open. `ExpandLess`/`ExpandMore` would claim the cell itself folds, and the cell is exactly what
 * stays on screen.
 *
 * **The name goes on the button, not on the icon inside it** - the rule a flow cost us in P2b:
 * `contentDescription` on the `Icon` lands on a child node, so the label and the state would be
 * one level apart. And it names what a tap will *do*, as every other control here does.
 *
 * ```
 * HeadingCollapser(collapsed = cell.headingCollapsed) { act(CellAction.ToggleHeadingCollapse) }
 * ```
 */
@Composable
internal fun HeadingCollapser(fold: HeadingFold, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  // **Centred on the heading's own first line, not on the cell.** Top-aligned inside its 48dp
  // button the caret sat 10dp below the heading's line centre - measured on device, 316 against
  // 306 (before this). A box exactly one heading line tall puts the icon
  // where the reference's `calc(0.3 * <level font size>)` puts it, and it tracks the level the way
  // the reference's six rules do because it reads the same [headingStyle] the heading is drawn in.
  //
  // The button overflows the box rather than being shrunk by it, so the 48dp Material reserves for
  // a press survives a heading whose line is shorter than that.
  val line = headingStyle(fold.level).lineHeight
  Box(
    modifier = modifier.height(with(LocalDensity.current) { line.toDp() }),
    contentAlignment = Alignment.Center,
  ) {
    IconButton(
      onClick = onToggle,
      modifier = Modifier.semantics {
        contentDescription = if (fold.collapsed) EXPAND_HEADING else COLLAPSE_HEADING
      },
    ) {
      Icon(
        imageVector = if (fold.collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/**
 * That a cell is a heading, which level, and whether its section is folded - or null on a cell that
 * is not one.
 *
 * One nullable rather than three parameters, the idiom [CellMark] already uses here: the level and
 * the folded state are read together by the one control that draws them, and folded into a single
 * type they cannot be passed separately and disagree. [level] is here because the caret's vertical
 * placement is a function of it, which is the reference's own arrangement - six
 * `[data-heading-level]` rules, each restating that level's font size
 * (`packages/cells/style/widget.css:184-213`).
 */
@Immutable
data class HeadingFold(val level: Int, val collapsed: Boolean)

/** Named so a flow and a screen reader read the same word for the same control. */
internal const val COLLAPSE_HEADING = "Collapse heading"
internal const val EXPAND_HEADING = "Expand heading"
