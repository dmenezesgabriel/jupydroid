package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * A modifier that reports when the caret enters or leaves a cell's input area - the one signal
 * that moves a notebook between its two [CellMode]s.
 *
 * `hasFocus` rather than `isFocused`, because the node this goes on is a container and the field
 * that actually takes focus is a descendant. It is the same question JupyterLab asks with
 * `widget.editorWidget.node.contains(target)` (`packages/notebook/src/widget.ts:3550`), and it is
 * why the modifier belongs on the input area rather than on the whole cell: focus anywhere in a
 * cell selects it, but only focus *inside the editor* means edit mode.
 *
 * **Only transitions are reported.** `onFocusChanged` fires on any change to the focus state,
 * including ones that leave `hasFocus` where it was; reporting those would be a stream of no-ops
 * the ViewModel would have to filter itself.
 *
 * ```
 * Box(modifier = onCellInputFocus { focused -> onCellFocusChanged(cell.id, focused) })
 * ```
 */
@Composable
internal fun onCellInputFocus(report: (focused: Boolean) -> Unit): Modifier {
  var had by remember { mutableStateOf(false) }
  return Modifier.onFocusChanged { focus ->
    if (focus.hasFocus == had) return@onFocusChanged
    had = focus.hasFocus
    report(focus.hasFocus)
  }
}
