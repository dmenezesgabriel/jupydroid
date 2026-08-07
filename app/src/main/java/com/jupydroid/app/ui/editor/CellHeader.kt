package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.editorPalette

/**
 * The strip above a cell: where its run got to, its Run/Stop control, and the way into
 * [CellMenu].
 */
@Composable
internal fun CellHeader(
  cell: Cell,
  executionCount: Int?,
  execution: CellExecution?,
  onRun: () -> Unit,
  onStop: () -> Unit,
  onCellAction: (CellAction) -> Unit,
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
    Text(
      text = executionLabel(cell.kind, status, executionCount),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    // Markdown gets Run as well - it renders the cell - but never a spinner or Stop, because
    // nothing of it ever reaches a kernel.
    if (cell.kind == CellKind.CODE) {
      RunControl(status = status, onRun = onRun, onStop = onStop)
    } else {
      IconButton(onClick = onRun) { Icon(Icons.Filled.PlayArrow, contentDescription = "Run cell") }
    }
    // How long the last one took, in the reference's own `EXECUTION_TIME_COLOR`. Absent while a
    // cell is running rather than counting up: a ticking label would recompose the header on
    // every frame, and the spinner beside it already says the same thing.
    runDurationLabel(execution?.took)?.let { took ->
      Text(
        text = took,
        style = MaterialTheme.typography.labelSmall,
        color = editorPalette.executionTime,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
    Spacer(modifier = Modifier.weight(1f))
    // Visible, not just long-pressable. A thin strip that happens to react to a long press is
    // not something anyone finds; JupyterLab puts real buttons on the cell for the same
    // actions, and only hides them below 760px - which is every screen this app runs on.
    // Boxed together on purpose: a DropdownMenu anchors to its parent layout node, and left in
    // the full-width Row it opened at the far left - a whole cell away from the button tapped.
    Box {
      IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Cell actions")
      }
      CellMenu(expanded = menuOpen, cell = cell, onDismiss = { menuOpen = false }, onAction = onCellAction)
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
      Icon(Icons.Filled.PlayArrow, contentDescription = "Run cell")
    }
    return
  }
  if (status != ExecutionStatus.RUNNING) {
    IconButton(onClick = onRun) {
      Icon(Icons.Filled.PlayArrow, contentDescription = "Run cell")
    }
    return
  }
  // Spinner *and* Stop: the spinner says the kernel is working, the button is the only way to
  // take it back, since the cell has no handle on the kernel's work.
  CircularProgressIndicator(
    modifier = Modifier.padding(start = 8.dp).size(16.dp).semantics { contentDescription = "Running" },
    strokeWidth = 2.dp,
  )
  IconButton(onClick = onStop) {
    // The universal stop square, drawn rather than imported: it is the only glyph this screen
    // would need from material-icons-extended. The 12dp is the *glyph*; the button around it
    // reserves 48dp of pressable area regardless, which `CellTouchTargetTest` pins.
    Box(
      modifier = Modifier
        .size(12.dp)
        .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.extraSmall)
        .semantics { contentDescription = "Stop cell" },
    )
  }
}
