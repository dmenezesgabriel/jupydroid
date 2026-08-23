package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.Spacing

/** The glyph every cell Run control draws - outlined, not filled (P2v-1). Pinned by CellRunIconTest. */
internal val cellRunIcon: ImageVector = Icons.Outlined.PlayArrow

/**
 * `cellRunGutter`, widened from the reference's 35px (`notebookOptions.ts:118-136`) to Material's
 * 48dp minimum: P2l-4 settled that a touch target is reserved space, not expanded bounds, so the
 * column is as wide as the press it holds rather than as wide as the glyph.
 */
internal val CELL_RUN_GUTTER = 48.dp

/**
 * What a Run / Stop button measures - Material's own 48dp minimum touch target, which is
 * [CELL_RUN_GUTTER]'s width too but is pinned separately: this is a **height** claim, and the day
 * the button shrinks the input floor shrinks with it instead of silently over-reserving.
 */
internal val CELL_RUN_CONTROL_SIZE = 48.dp

/** The smallest breathing room between the play and the bracket below it. */
internal val GUTTER_STACK_AIR = 8.dp

/**
 * The floor under a code cell's input area: enough height that the play hung from its top and the
 * bracket pinned to its bottom never crowd each other, even on a one-line cell.
 *
 * This is the app-side answer to the reference's editor floor - every code editor is created at
 * `lineCount × lineHeight + padding{top:12,bottom:12}` (`codeCell.ts:195-201`,
 * `cellEditorOptions.ts:170-172`), which is why VS Code can hang both ends off an editor without
 * them ever meeting. The count's line is **measured**, not guessed, the way [scrolledOutputsHeight]
 * bounds the outputs strip: at the tablet's 1.1 font scale a guessed dp value would starve the
 * bracket of room.
 */
@Composable
internal fun cellInputMinHeight(): Dp {
  val measurer = rememberTextMeasurer()
  val countStyle = executionCountStyle()
  val countLinePx = remember(countStyle) {
    measurer.measure(EXECUTION_COUNT_PROBE_TEXT, countStyle).size.height
  }
  return with(LocalDensity.current) { countLinePx.toDp() } + CELL_RUN_CONTROL_SIZE + GUTTER_STACK_AIR
}

/** A label the count always draws, whatever the kernel has counted - `[ ]` before a first run. */
private const val EXECUTION_COUNT_PROBE_TEXT = "[ ]"

/**
 * The run gutter (P2v-1): a fixed column left of the editor holding Run / Stop / spinner,
 * top-aligned with the editor's first line - where `CellHeader` held them until now
 * (`media/notebook.css:228-234` builds it outside the cell; `cellRenderer.ts:273` per cell).
 *
 * Drawn on **all three kinds**, which VS Code does not do and this app needs to: code's controls,
 * markdown's render-play, raw empty - so every editor starts at one x
 * (`docs/DECISIONS.md`, "The run gutter is re-taken"). The content is top-aligned by the caller's
 * Row, which is what puts Run beside the editor's first line.
 *
 * **The column is as tall as the editor** - the caller passes a height-filling modifier - because
 * since P2v-2 its foot carries the execution count (`[N]`, `[ ]`, `[*]`; code only), pinned where
 * `top = editorHeight - 22 + statusBarHeight` pins it in the reference
 * (`view/cellParts/cellExecution.ts:139,200`).
 *
 * ```
 * Row {
 *   CellGutter(cell.kind, status, executionCount, onRun, onStop, Modifier.fillMaxHeight())
 *   Box(Modifier.weight(1f)) { …the editor… }
 * }
 * ```
 */
@Composable
internal fun CellGutter(
  kind: CellKind,
  status: ExecutionStatus,
  /** The kernel's count for this cell, or null when it has none - see [executionCountLabel]. */
  executionCount: Int?,
  onRun: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.width(CELL_RUN_GUTTER)) {
    when (kind) {
      // Three kinds, three answers, and they are `runCell`'s own switch: code executes, markdown
      // renders - so it gets Run but never a spinner or Stop, since nothing of it reaches a
      // kernel - and raw falls through `default: break`
      // (`packages/notebook/src/cellexecutor.ts:36-141`), so it gets no Run at all. A button that
      // lights up and does nothing is what this app already refuses to ship in the menu next door.
      CellKind.CODE -> {
        RunControl(status = status, onRun = onRun, onStop = onStop, modifier = Modifier.align(Alignment.TopStart))
        executionCountLabel(status, executionCount)?.let { count ->
          Text(
            text = count,
            style = executionCountStyle(),
            // The reference states no colour of its own and draws the node at `opacity: .7`
            // (`media/notebook.css:256-266`) over whatever it inherits.
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
      CellKind.MARKDOWN ->
        IconButton(onClick = onRun, modifier = Modifier.align(Alignment.TopStart)) {
          Icon(cellRunIcon, contentDescription = "Run cell")
        }
      CellKind.RAW -> Unit
    }
  }
}

/**
 * Run / Stop / spinner for a code cell. `modifier` lands on whatever the status draws, so the
 * caller can pin it to the gutter's top without knowing which branch is live.
 */
@Composable
private fun RunControl(
  status: ExecutionStatus,
  onRun: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // A queued cell is spoken for but is not what the kernel is doing, so it gets neither. The
  // spinner would claim work that has not started, and Stop interrupts *the kernel* - offered on
  // three queued cells at once it reads as three separate things to cancel. Run is disabled
  // instead, which says "claimed, waiting" without promising either.
  //
  // Found by looking at the evidence: every queued cell had a spinner and a Stop button
  // (evidence/u4-execution-status/02-queued-and-running.png, before this).
  if (status == ExecutionStatus.QUEUED) {
    IconButton(onClick = onRun, enabled = false, modifier = modifier) {
      Icon(cellRunIcon, contentDescription = "Run cell")
    }
    return
  }
  if (status != ExecutionStatus.RUNNING) {
    IconButton(onClick = onRun, modifier = modifier) {
      Icon(cellRunIcon, contentDescription = "Run cell")
    }
    return
  }
  // Spinner *and* Stop, side by side in a Row of their own - as siblings of a Box they drew on
  // top of each other (P2v-2 caught it; bounds tests on a resting cell never could). The spinner
  // says the kernel is working, the button is the only way to take it back, since the cell has no
  // handle on the kernel's work.
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(
      modifier = Modifier.padding(start = Spacing.sm).size(SPINNER_SIZE).semantics { contentDescription = "Running" },
      strokeWidth = SPINNER_STROKE,
    )
    IconButton(onClick = onStop) {
      // The real glyph now that material-icons-extended is a dependency (X2a). It was a drawn Box
      // only because importing the whole icon set for one square was the worse trade; that reason
      // is gone. The button around it still reserves 48dp, which `CellTouchTargetTest` pins.
      Icon(
        imageVector = Icons.Filled.Stop,
        contentDescription = "Stop cell",
        tint = MaterialTheme.colorScheme.error,
      )
    }
  }
}

/**
 * The spinner beside a running cell. Deliberately smaller than the 24dp icons around it: it is a
 * status, not a control, and one that reads as a button invites a press that does nothing.
 */
private val SPINNER_SIZE = 16.dp

/** `CircularProgressIndicator`'s own default is 4dp, which at [SPINNER_SIZE] is a disc. */
private val SPINNER_STROKE = 2.dp
