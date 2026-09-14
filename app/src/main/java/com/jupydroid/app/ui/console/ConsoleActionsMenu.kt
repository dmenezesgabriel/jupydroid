package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The Console's overflow - `console:clear` (P6b-7) is the only command here with nothing else to
 * trigger it yet. `TerminalActionsMenu`'s own precedent: a one-row menu rather than a bare icon,
 * because `Icons.Filled.Clear` is the glyph `NotebookBar` had to retire from Interrupt the moment a
 * second cross (`DocumentDismissAction`'s Close, at two panes) could sit on the same bar - a text
 * row carries no such ambiguity.
 *
 * ```kotlin
 * ConsoleActionsMenu(onClear = viewModel::clear)
 * ```
 */
@Composable
fun ConsoleActionsMenu(onClear: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Console actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text(CLEAR_CONSOLE_CELLS) },
        onClick = {
          open = false
          onClear()
        },
      )
    }
  }
}

/** `console:clear`'s own label (`console-extension/src/index.ts:998`). */
internal const val CLEAR_CONSOLE_CELLS = "Clear Console Cells"
