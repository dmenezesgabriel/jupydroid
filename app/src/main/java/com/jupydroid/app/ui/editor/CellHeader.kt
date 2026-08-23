package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
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
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette

/**
 * The strip above a cell: how long its last run took, and the way into [CellMenu]. Its Run/Stop
 * control left for the gutter in P2v-1 - [CellGutter], left of the editor - and its execution
 * prompt followed in P2v-2, replaced by the count at that gutter's foot: `[7]:` above the editor
 * and `[7]` at its foot are one fact twice.
 */
@Composable
internal fun CellHeader(
  cell: Cell,
  execution: CellExecution?,
  canPaste: Boolean,
  /** The tick this cell shows while a run is being marked, or null when none is - see [CellMark]. */
  mark: CellMark?,
  onToggleSelected: () -> Unit,
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
    // Leading, where a Material selection list puts its tick, and before the duration so the eye
    // runs down one column of ticks rather than hunting them among four different labels.
    if (mark != null) {
      Checkbox(
        checked = mark.checked,
        onCheckedChange = { onToggleSelected() },
        // On the Checkbox itself, so a screen reader reads which cell and whether it is in the
        // run off one node - the same rule the undo buttons cost a flow to learn.
        modifier = Modifier.semantics { contentDescription = mark.label },
      )
    }
    // How long the last one took, in the reference's own `EXECUTION_TIME_COLOR`. Absent while a
    // cell is running rather than counting up: a ticking label would recompose the header on
    // every frame, and the spinner in the gutter already says the same thing.
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
      IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Cell actions")
      }
      CellActions(
        expanded = menuOpen,
        cell = cell,
        canPaste = canPaste,
        onDismiss = { menuOpen = false },
        onAction = onCellAction,
      )
    }
  }
}
