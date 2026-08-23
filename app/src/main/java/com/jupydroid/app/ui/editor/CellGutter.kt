package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * The run gutter (P2v-1): a fixed column left of the editor holding Run / Stop / spinner,
 * top-aligned with the editor's first line - where `CellHeader` held them until now
 * (`media/notebook.css:228-234` builds it outside the cell; `cellRenderer.ts:273` per cell).
 *
 * Drawn on **all three kinds**, which VS Code does not do and this app needs to: code's controls,
 * markdown's render-play, raw empty - so every editor starts at one x
 * (`docs/DECISIONS.md`, "The run gutter is re-taken"). The content is top-aligned by the caller's
 * Row, which is what puts Run beside the editor's first line.
 *
 * ```
 * Row {
 *   CellGutter(cell.kind, status, onRun, onStop)
 *   Box(Modifier.weight(1f)) { …the editor… }
 * }
 * ```
 */
@Composable
internal fun CellGutter(
  kind: CellKind,
  status: ExecutionStatus,
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
      CellKind.CODE -> RunControl(status = status, onRun = onRun, onStop = onStop)
      CellKind.MARKDOWN -> IconButton(onClick = onRun) { Icon(cellRunIcon, contentDescription = "Run cell") }
      CellKind.RAW -> Unit
    }
  }
}

@Composable
private fun RunControl(status: ExecutionStatus, onRun: () -> Unit, onStop: () -> Unit) {
  // A queued cell is spoken for but is not what the kernel is doing, so it gets neither. The
  // spinner would claim work that has not started, and Stop interrupts *the kernel* - offered on
  // three queued cells at once it reads as three separate things to cancel. Run is disabled
  // instead, which says "claimed, waiting" without promising either.
  //
  // Found by looking at the evidence: every queued cell had a spinner and a Stop button
  // (evidence/u4-execution-status/02-queued-and-running.png, before this).
  if (status == ExecutionStatus.QUEUED) {
    IconButton(onClick = onRun, enabled = false) {
      Icon(cellRunIcon, contentDescription = "Run cell")
    }
    return
  }
  if (status != ExecutionStatus.RUNNING) {
    IconButton(onClick = onRun) {
      Icon(cellRunIcon, contentDescription = "Run cell")
    }
    return
  }
  // Spinner *and* Stop: the spinner says the kernel is working, the button is the only way to
  // take it back, since the cell has no handle on the kernel's work.
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

/**
 * The spinner beside a running cell. Deliberately smaller than the 24dp icons around it: it is a
 * status, not a control, and one that reads as a button invites a press that does nothing.
 */
private val SPINNER_SIZE = 16.dp

/** `CircularProgressIndicator`'s own default is 4dp, which at [SPINNER_SIZE] is a disc. */
private val SPINNER_STROKE = 2.dp
