package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.Spacing
import com.jupydroid.extension.api.FocusRingTextButton

/** The list key of the footer; no cell id can equal it, which is what lets the list keep it apart from the cells. */
internal const val ADD_CELL_FOOTER_KEY = "add-cell-footer"

/**
 * The notebook's last row: one button per kind of cell to append, so adding a cell does not mean
 * opening a cell's `⋮` menu. JupyterLab draws the same row after the last cell
 * (`packages/notebook/src/notebookfooter.ts:47-53`); there is no hover on a touch screen, so VS Code's
 * between-cells toolbar does not carry over.
 *
 * The footer names a kind and never a position - where the cell lands is [appendCell]'s decision.
 *
 * ```
 * AddCellFooter(onAdd = { kind -> edits.appendCell(kind) })
 * ```
 */
@Composable
internal fun AddCellFooter(onAdd: (CellKind) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
  ) {
    FocusRingTextButton(onClick = { onAdd(CellKind.CODE) }) { Text("Code") }
    FocusRingTextButton(onClick = { onAdd(CellKind.MARKDOWN) }) { Text("Markdown") }
  }
}
