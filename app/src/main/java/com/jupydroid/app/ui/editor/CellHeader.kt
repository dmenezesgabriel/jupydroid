package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.extension.api.FocusRingIconButton
import com.jupydroid.extension.api.focusRing

/**
 * The glyph every cell's Run control draws - **outlined, not filled**.
 *
 * A filled triangle at rest reads as "playing"; outlined reads as available, which is what an
 * idle control means (ADR-0057). A value rather
 * than an inline argument so `CellRunIconTest` has something to pin - without it the departure
 * back to filled is a one-token edit no gate would see.
 *
 * Scope is this file's three run controls. `NotebookBar`'s *Run all* and `SelectionTopBar`'s
 * play act on a selection rather than on a cell's own state, and keep their filled glyphs.
 */
internal val cellRunIcon: ImageVector = Icons.Outlined.PlayArrow

/**
 * The strip above a cell: where its run got to, its Run/Stop control, and the way into
 * [CellMenu].
 */
@Composable
internal fun CellHeader(
  cell: Cell,
  executionCount: Int?,
  execution: CellExecution?,
  canPaste: Boolean,
  /** The tick this cell shows while a run is being marked, or null when none is - see [CellMark]. */
  mark: CellMark?,
  onToggleSelected: () -> Unit,
  onRun: () -> Unit,
  onStop: () -> Unit,
  onCellAction: (CellAction) -> Unit,
  /** Whether a session can answer the inspect rows - passed to [CellActions] untouched. */
  inspection: Boolean = false,
) {
  var menuOpen by remember { mutableStateOf(false) }
  val status = execution?.status ?: ExecutionStatus.NOT_STARTED
  // No gesture on the Row itself. It briefly carried a long press - a workaround for having no
  // button - and that made the whole strip one target: it took the ripple, it took focus, and
  // any further control added here would have been competing with a parent already claiming
  // every gesture. JupyterLab puts six independent buttons on a cell; this has to stay a place
  // buttons can be added, not a single hit area.
  //
  // No vertical padding either: every child is a full-size IconButton now, and one already
  // reserves more height than this padding added.
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().semantics { stateDescription = status.spokenState() },
  ) {
    // Leading, where a Material selection list puts its tick, and before the prompt so the eye
    // runs down one column of ticks rather than hunting them among four different labels.
    if (mark != null) CellSelectionCheckbox(mark, onToggleSelected)
    // Nothing at all on a markdown or raw cell, which is the reference's own arrangement:
    // `_setPrompt`'s only caller is `CodeCell._updatePrompt`
    // (`packages/cells/src/widget.ts:1693-1700`), so their prompt node is drawn and left textless.
    // Composed away rather than drawn empty - an empty `Text` still measures its line height and
    // still sits between the collapser and the Run control.
    executionLabel(cell.kind, status, executionCount)?.let { prompt ->
      Text(
        text = prompt,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    // Three kinds, three answers, and they are `runCell`'s own switch: code executes, markdown
    // renders - so it gets Run but never a spinner or Stop, since nothing of it reaches a kernel -
    // and raw falls through `default: break` (`packages/notebook/src/cellexecutor.ts:36-141`), so it
    // gets no Run at all. A button that lights up and does nothing is what this app already
    // refuses to ship in the menu next door.
    when (cell.kind) {
      CellKind.CODE -> RunControl(status = status, onRun = onRun, onStop = onStop)
      CellKind.MARKDOWN ->
        FocusRingIconButton(onClick = onRun) { Icon(cellRunIcon, contentDescription = "Run cell") }
      CellKind.RAW -> Unit
    }
    // How long the last one took, in the reference's own `EXECUTION_TIME_COLOR`. Absent while a
    // cell is running rather than counting up: a ticking label would recompose the header on
    // every frame, and the spinner beside it already says the same thing.
    runDurationLabel(execution?.took)?.let { took ->
      Text(
        text = took,
        style = MaterialTheme.typography.labelSmall,
        color = editorPalette.executionTime,
        modifier = Modifier.padding(start = Spacing.sm),
      )
    }
    Spacer(modifier = Modifier.weight(1f))
    // Gone while a run is marked: [SelectionTopBar] is what acts on the selection, and a second
    // menu on one of its members would be two answers to "what does this apply to".
    if (mark != null) return@Row
    // Visible, not just long-pressable. A thin strip that happens to react to a long press is
    // not something anyone finds; JupyterLab puts real buttons on the cell for the same
    // actions, and only hides them below 760px - which is every screen this app runs on.
    // Boxed together on purpose: a DropdownMenu anchors to its parent layout node, and left in
    // the full-width Row it opened at the far left - a whole cell away from the button tapped.
    Box {
      FocusRingIconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Cell actions")
      }
      CellActions(
        expanded = menuOpen,
        cell = cell,
        canPaste = canPaste,
        inspection = inspection,
        onDismiss = { menuOpen = false },
        onAction = onCellAction,
      )
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
  // (before this).
  if (status == ExecutionStatus.QUEUED) {
    FocusRingIconButton(onClick = onRun, enabled = false) {
      Icon(cellRunIcon, contentDescription = "Run cell")
    }
    return
  }
  if (status != ExecutionStatus.RUNNING) {
    FocusRingIconButton(onClick = onRun) {
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
  FocusRingIconButton(onClick = onStop) {
    // The real glyph now that material-icons-extended is a dependency. It was a drawn Box
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

/** The tick a run is marked with, leading the header; see [CellMark]. */
@Composable
internal fun CellSelectionCheckbox(mark: CellMark, onToggleSelected: () -> Unit) {
  Checkbox(
    checked = mark.checked,
    onCheckedChange = { onToggleSelected() },
    // On the Checkbox itself, so a screen reader reads which cell and whether it is in the
    // run off one node - the same rule the undo buttons cost a flow to learn. The ring is here
    // because `Checkbox` names its own `ripple()`, which `FocusRingIndication` cannot reach.
    modifier = Modifier.focusRing().semantics { contentDescription = mark.label },
  )
}
