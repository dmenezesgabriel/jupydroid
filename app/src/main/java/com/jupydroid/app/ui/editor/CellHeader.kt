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
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * The strip above a cell: what it is or how many times it has run, its Run/Stop control, and
 * the way into [CellMenu].
 */
@Composable
internal fun CellHeader(
  cell: Cell,
  executionCount: Int?,
  running: Boolean,
  onRun: () -> Unit,
  onStop: () -> Unit,
  onCellAction: (CellAction) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  // No gesture on the Row itself. It briefly carried a long press - a workaround for having no
  // button - and that made the whole strip one target: it took the ripple, it took focus, and
  // any further control added here would have been competing with a parent already claiming
  // every gesture. JupyterLab puts six independent buttons on a cell; this has to stay a place
  // buttons can be added, not a single hit area.
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
  ) {
    Text(
      // The bracket notation a Jupyter user already reads as "this cell has run N times".
      text = executionCount?.let { "[$it]" } ?: cell.kind.label(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    if (cell.kind == CellKind.CODE) RunControl(running = running, onRun = onRun, onStop = onStop)
    Spacer(modifier = Modifier.weight(1f))
    // Visible, not just long-pressable. A thin strip that happens to react to a long press is
    // not something anyone finds; JupyterLab puts real buttons on the cell for the same
    // actions, and only hides them below 760px - which is every screen this app runs on.
    // Boxed together on purpose: a DropdownMenu anchors to its parent layout node, and left in
    // the full-width Row it opened at the far left - a whole cell away from the button tapped.
    Box {
      IconButtonSized(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Cell actions")
      }
      CellMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, onAction = onCellAction)
    }
  }
}

@Composable
private fun RunControl(running: Boolean, onRun: () -> Unit, onStop: () -> Unit) {
  if (!running) {
    IconButtonSized(onClick = onRun) {
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
  IconButtonSized(onClick = onStop) {
    // The universal stop square, drawn rather than imported: it is the only glyph this screen
    // would need from material-icons-extended.
    Box(
      modifier = Modifier
        .size(12.dp)
        .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.extraSmall)
        .semantics { contentDescription = "Stop cell" },
    )
  }
}

@Composable
private fun IconButtonSized(onClick: () -> Unit, content: @Composable () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) { content() }
}

internal fun CellKind.label(): String = when (this) {
  CellKind.CODE -> "code"
  CellKind.MARKDOWN -> "markdown"
}
