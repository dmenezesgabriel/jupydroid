package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jupydroid.app.theme.FocusRingIconButton

/**
 * The Console's overflow - `TerminalActionsMenu`'s own precedent: a menu rather than bare icons,
 * because `Icons.Filled.Clear` is the glyph `NotebookBar` had to retire from Interrupt the moment a
 * second cross (the in-pane Close the tab strip since took over) could sit on the same bar - a text
 * row carries no such ambiguity.
 *
 * **Interrupt Kernel sits here rather than on the bar itself, matching both references**: neither
 * JupyterLab's `ConsolePanel` toolbar (`console-extension/schema/tracker.json:63-72`) nor VS
 * Code's Interactive Window toolbar (`package.json:949-985`) places Interrupt where it is always
 * visible - both reach it only from a menu or the palette. Disabled while nothing is running, the
 * same rule `NotebookBar`'s own Interrupt button already follows.
 *
 * ```kotlin
 * ConsoleActionsMenu(onClear = viewModel::clear, onInterrupt = viewModel::interrupt, running = running)
 * ```
 */
@Composable
fun ConsoleActionsMenu(onClear: () -> Unit, onInterrupt: () -> Unit, running: Boolean) {
  var open by remember { mutableStateOf(false) }
  Box {
    FocusRingIconButton(onClick = { open = true }) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Console actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text(INTERRUPT_KERNEL) },
        enabled = running,
        onClick = {
          open = false
          onInterrupt()
        },
      )
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

/** `console:interrupt-kernel`'s own label (`console-extension/src/index.ts:1133`). */
internal const val INTERRUPT_KERNEL = "Interrupt Kernel"
