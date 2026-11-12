package com.jupydroid.app.ui.explorer

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
 * The Recents section's overflow - `docmanager:clear-recents` and, today, nothing else.
 *
 * **Behind a menu rather than beside the heading**, which is the reference's own arrangement: a
 * view pane's header carries its actions in a toolbar whose secondary half is a `…`
 * (`viewPane.ts:693`, `toolbar.ts:135`), and the header itself is the collapse rather than a
 * button among buttons (`paneview.ts:285-292`). The heading's one control used to be a bare
 * `Clear` icon, so the only thing a user could do to this section was destroy it.
 *
 * **Drawn only while the section is open** - every CSS rule that reveals a pane header's actions
 * requires `.expanded` (`paneview.css:79-82`, `paneviewlet.css:26-27`). On a touch surface that
 * matters more than it does under a mouse: there is no hover to reveal anything, so a shut
 * section's heading is one target and not two.
 *
 * ```
 * if (!collapsed) RecentsActionsMenu(onClear = viewModel::clearRecents)
 * ```
 */
@Composable
fun RecentsActionsMenu(onClear: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    // Named for the surface it acts on, as every other overflow in this app is - the reference's
    // own label for this glyph is *"More Actions…"* (`toolbar.ts:734`), which says nothing about
    // *whose* actions on a screen that has several.
    FocusRingIconButton(onClick = { open = true }) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Recent actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text(CLEAR_RECENT_DOCUMENTS) },
        onClick = {
          open = false
          onClear()
        },
      )
    }
  }
}

/** `docmanager:clear-recents`' own label (`docmanager-extension/src/recents.ts:73`). */
internal const val CLEAR_RECENT_DOCUMENTS = "Clear Recent Documents"
