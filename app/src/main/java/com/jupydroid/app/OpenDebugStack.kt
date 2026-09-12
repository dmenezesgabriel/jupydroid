package com.jupydroid.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jupydroid.app.domain.model.DebugStack

/**
 * Where the open notebook publishes its stop's call stack and variables, and the shell's side
 * panel reads it - [OpenDocumentOutline]'s own seam, crossed for the same reason: the panel is in
 * the list pane and a [DebugStack] is resolved in the detail pane's `EditorViewModel`, which the
 * panel cannot reach.
 *
 * **Already nullable on its own**, unlike [DocumentOutline] - a document always publishes one once
 * it is open, and only its *headings* can be empty, where a [DebugStack] simply does not exist
 * until something stops. So [current] is published straight from `EditorUiState.debugStack`, with
 * no wrapper metadata of its own.
 *
 * ```
 * val debugPanel = remember { OpenDebugStack() }   // MainNavigation
 * debugPanel.current                                // the side panel
 * ```
 */
@Stable
class OpenDebugStack {
  /** The open notebook's stopped call stack, or null while nothing that has one is open or stopped. */
  var current by mutableStateOf<DebugStack?>(null)
    private set

  /** The open notebook's own state, replacing whatever was there - including null. */
  fun publish(stack: DebugStack?) {
    current = stack
  }

  /** The document has gone, so the panel empties - [OpenDocumentOutline.clear]'s own reason. */
  fun clear() {
    current = null
  }
}
