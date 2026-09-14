package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * `next`/`stepIn`/`stepOut`/`continue` (P6a-9) plus `terminate` (P6a-11), shown only while
 * [EditorUiState.stoppedAt] is non-null - [NotebookBar]'s own sibling, for the one state a
 * notebook being read never reaches on its own.
 *
 * **Continue is [Icons.Filled.PlayArrow]**, an exact match rather than a substitute: the
 * reference's own `continueIcon` is `runIcon` (`packages/ui-components/style/icons/toolbar/run.svg`,
 * `M8 5v14l11-7z`), which is Material's Play triangle path verbatim. **The three step icons are
 * not** - `packages/ui-components/style/icons/debugger/{step-over,step-into,step-out}.svg` draw a
 * loop-arrow-over-a-dot and a mirrored pair of arrows-into/out-of-a-dot that
 * `material-icons-extended` (the curated classic set this app depends on, not the newer variable
 * Material Symbols font) does not carry - confirmed by inspecting the dependency jar. Named as a
 * departure rather than silently guessed: `SkipNext`/`ArrowDownward`/`ArrowUpward` are the closest
 * stock shapes standing in for step-over/step-into/step-out.
 *
 * **Terminate reuses [Icons.Filled.Stop]**, the same glyph [NotebookBar]'s own Interrupt already
 * draws - JupyterLab's own `terminate` command takes `stopIcon` too
 * (`debugger.ts:150`/`index.ts:1258`). **Never disabled by [stepping]**: it is the escape hatch
 * for a request stuck in flight - P6a-10's own tablet reproduction needed exactly this (a kernel
 * restart) to get free of a debug session Interrupt alone could not touch, so the one control
 * meant to work when everything else is stuck cannot be the one still waiting on it.
 *
 * **[onToggleVariables] is null on a two-pane window**, the same rule [EditorTopBar]'s own
 * `onShowTableOfContents` already states:
 * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel] shows the call stack and
 * variables there instead, so a one-pane window is the only one with a surface to toggle at all.
 *
 * ```
 * DebuggerControlBar(stepping = state.stepping, onContinue = { … }, onNext = { … }, …)
 * ```
 */
@Composable
internal fun DebuggerControlBar(
  stepping: Boolean,
  onContinue: () -> Unit,
  onNext: () -> Unit,
  onStepIn: () -> Unit,
  onStepOut: () -> Unit,
  onTerminate: () -> Unit,
  onToggleVariables: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  BottomAppBar(modifier = modifier.imePadding()) {
    // Disabled for the length of one DAP round trip - a second tap before the kernel reports
    // where it landed would send a stale command for a stop it has already left.
    IconButton(onClick = onContinue, enabled = !stepping) {
      Icon(Icons.Filled.PlayArrow, contentDescription = "Continue")
    }
    IconButton(onClick = onNext, enabled = !stepping) {
      Icon(Icons.Filled.SkipNext, contentDescription = "Step over")
    }
    IconButton(onClick = onStepIn, enabled = !stepping) {
      Icon(Icons.Filled.ArrowDownward, contentDescription = "Step into")
    }
    IconButton(onClick = onStepOut, enabled = !stepping) {
      Icon(Icons.Filled.ArrowUpward, contentDescription = "Step out")
    }
    IconButton(onClick = onTerminate) {
      Icon(Icons.Filled.Stop, contentDescription = "Terminate")
    }
    if (onToggleVariables != null) {
      IconButton(onClick = onToggleVariables) {
        Icon(Icons.Filled.List, contentDescription = SHOW_DEBUGGER_PANEL)
      }
    }
  }
}
