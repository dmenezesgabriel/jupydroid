package com.jupydroid.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.port.DebugVariable

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

  /**
   * Fetches a compound variable's own children (P6a-14) - [NotebookDebugger.expand][
   * com.jupydroid.app.ui.editor.NotebookDebugger.expand], published alongside [current] the same
   * way [DocumentOutline.jumpTo] travels with its own headings: the panel is in the list pane and
   * owns no session of its own to ask a kernel with.
   */
  private var expandVariable: suspend (Int) -> List<DebugVariable> = { emptyList() }

  /** The open notebook's own state, replacing whatever was there - including null. */
  fun publish(stack: DebugStack?, expand: suspend (Int) -> List<DebugVariable>) {
    current = stack
    expandVariable = expand
  }

  /** [expandVariable], named for what the panel actually asks of it rather than what it stores. */
  suspend fun expand(variablesReference: Int): List<DebugVariable> = expandVariable(variablesReference)

  /**
   * The document has gone, so the panel empties - [OpenDocumentOutline.clear]'s own reason,
   * [DocumentOutline.jumpTo]'s own reason applied to [expandVariable] too: nothing here should
   * outlive the debugger that published it.
   */
  fun clear() {
    current = null
    expandVariable = { emptyList() }
  }
}
