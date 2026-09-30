package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette

/**
 * The panel a cell's source is typed into: its surface, the line around it and the clearance
 * inside it.
 *
 * **The border is drawn whether or not the cell is active**, which is the half this app did not
 * have: `.jp-InputArea-editor` carries `1px solid var(--jp-cell-editor-border-color)`
 * unconditionally (`packages/cells/style/inputarea.css:18-26`), and the cell being typed into
 * swaps that colour for `--jp-cell-editor-active-border-color`, which is the brand colour
 * (`packages/notebook/style/base.css:160-164`). With the collapser transparent at rest, this is
 * the *only* thing that says where a cell's editor ends, which is the reference's own
 * arrangement.
 *
 * **One factory rather than three call sites**, which is the other half. The code and raw fields
 * each spelled this out in [EditorCell] and the markdown field wrote its own using
 * `colorScheme.surfaceVariant` - a different colour for the same role, and a divergence nothing
 * would have caught.
 *
 * Two departures from the reference, both deliberate:
 *
 * - **The corner stays Material's `shapes.small` where the reference squares it**
 *   (`border-radius: 0`, same rule). A cell's outputs, its collapsed placeholder and its
 *   completion popup are all drawn on that radius, so squaring only the editor would make it the
 *   odd panel in its own cell. Internal consistency beats literal parity on a shape nobody reads
 *   as information.
 * - **No inner glow.** The reference adds `inset 0 0 2px var(--md-blue-300)` in edit mode. Compose
 *   has no inset shadow, and `Modifier.shadow` is not its substitute: an elevation casts *outward*
 *   and would lift the field off the cell, which the reference never does. The border colour
 *   carries the state on its own, measured at 3.23:1 dark and 4.03:1 light - see
 *   `EditorPaletteTest`. Same reasoning as `EditorPalette.currentSearchMatch`, which declined
 *   IntelliJ's outline for the same "not expressible here" reason.
 *
 * `Modifier.border` strokes *inside* the layout bounds, so this adds no space and the content
 * sits where `.background().padding()` alone put it - asserted by `CellEditorBoxTest`, because
 * the gutter's x is settled elsewhere and must not move here.
 *
 * ```
 * CodeCellField(state = state, modifier = Modifier.fillMaxWidth().cellEditorBox(editing))
 * ```
 *
 * @param editing whether this is the cell the caret is in - see [cellIsBeingEdited].
 */
@Composable
internal fun Modifier.cellEditorBox(editing: Boolean): Modifier {
  val palette = editorPalette
  val shape = MaterialTheme.shapes.small
  return this
    .background(palette.cellBackground, shape)
    .border(BORDER_WIDTH, if (editing) palette.stripeSelected else palette.cellEditorBorder, shape)
    .padding(Spacing.sm)
}

/** `--jp-border-width`, 1px in both reference themes. */
private val BORDER_WIDTH = 1.dp
