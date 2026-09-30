package com.jupydroid.app.ui.terminal

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
import com.jupydroid.app.domain.model.TerminalFontSize

/**
 * The terminal's overflow: `terminal:increase-font`/`terminal:decrease-font`
 * (`packages/terminal-extension/src/index.ts:64-66`), the only two commands the reference offers
 * for this surface, filed under its own Settings menu (`:342-349`) and the command palette. This
 * app has no Settings screen, so - [com.jupydroid.app.ui.editor.NotebookActionsMenu]'s own
 * precedent for exactly this situation - they sit on the terminal's own bar.
 *
 * **A stepper, not a gesture.** Termux exposes the same setting through pinch-to-zoom
 * (`TermuxTerminalViewClient.onScale`), but no reference this app follows for its own *command
 * surface* names a gesture for this, and no `pointerInput` gesture exists anywhere in this
 * codebase yet - 3d is the step that establishes one, not this.
 *
 * Each row disables itself at its own bound rather than disappearing - the reference's own
 * `isEnabled` (`:771`, `:791`), read through a menu row instead of a command palette entry.
 */
@Composable
fun TerminalActionsMenu(fontSizeSp: Int, onIncreaseFontSize: () -> Unit, onDecreaseFontSize: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Terminal actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text(INCREASE_TERMINAL_FONT_SIZE) },
        enabled = fontSizeSp < TerminalFontSize.MAX,
        onClick = {
          open = false
          onIncreaseFontSize()
        },
      )
      DropdownMenuItem(
        text = { Text(DECREASE_TERMINAL_FONT_SIZE) },
        enabled = fontSizeSp > TerminalFontSize.MIN,
        onClick = {
          open = false
          onDecreaseFontSize()
        },
      )
    }
  }
}

/** `terminal:increase-font`'s own label (`packages/terminal-extension/src/index.ts:767`). */
internal const val INCREASE_TERMINAL_FONT_SIZE = "Increase Terminal Font Size"

/** `terminal:decrease-font`'s own label (`:787`). */
internal const val DECREASE_TERMINAL_FONT_SIZE = "Decrease Terminal Font Size"
