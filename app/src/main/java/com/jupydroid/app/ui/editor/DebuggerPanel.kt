package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.theme.Spacing

/**
 * `Show call stack and variables`, on whichever surface the window has room for -
 * [CellActionPresentation]'s own rule, reused for [TableOfContentsPanel]'s own reason: a
 * `ModalBottomSheet` anchored nowhere in particular is heavier than the job needs once there is
 * room for a `DropdownMenu` anchored to the button that opened it.
 *
 * **JupyterLab's own Debugger sidebar has no "show" command to mirror** - it registers on the
 * shell's *right* area (`packages/debugger-extension/src/index.ts:1357`) and is simply always
 * there once a debugger-capable kernel starts. This app has one side area
 * ([SidePanel.DEBUGGER][com.jupydroid.app.SidePanel]'s own doc), so this row gives it the same
 * explicit toggle every other panel here already has.
 *
 * **Unlike [TableOfContentsPanel], a pick never dismisses this**: every row is read-only, so
 * there is nothing to choose and [onDismiss] is reached only the ordinary way, by tapping outside.
 *
 * Called unconditionally by [NotebookActionsMenu], inside the same `Box` its own button anchors
 * from - [expanded] is what decides whether anything renders.
 */
@Composable
fun DebuggerPanel(
  expanded: Boolean,
  stack: DebugStack?,
  onDismiss: () -> Unit,
  presentation: CellActionPresentation = currentCellActionPresentation(),
) {
  if (!expanded) return
  when (presentation) {
    CellActionPresentation.MENU -> DebuggerMenu(stack, onDismiss)
    CellActionPresentation.SHEET -> DebuggerSheet(stack, onDismiss)
  }
}

@Composable
private fun DebuggerMenu(stack: DebugStack?, onDismiss: () -> Unit) {
  DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
    DebugStackRows(stack, modifier = Modifier.padding(Spacing.md))
  }
}

/** `navigationBarsPadding` for [CellActionSheet]'s own reason: the sheet draws under the gesture bar otherwise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebuggerSheet(stack: DebugStack?, onDismiss: () -> Unit) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
    ) {
      DebugStackRows(stack, modifier = Modifier.padding(Spacing.md))
    }
  }
}

/**
 * The call stack and the top frame's variables, shared with
 * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel] - the surfaces differ in
 * where they are drawn, never in what a stop looks like.
 *
 * Flat, undecorated rows: P6a-8's own phone scope has no per-frame reselection and no nested
 * variable expansion, both named as deferred gaps in `docs/PLAN.md`'s Open risks rather than
 * dropped silently.
 */
@Composable
internal fun DebugStackRows(stack: DebugStack?, modifier: Modifier = Modifier) {
  if (stack == null) {
    NotStoppedPlaceholder(modifier)
    return
  }
  Column(modifier = modifier) {
    stack.frames.forEach { frame -> Text(text = "${frame.name} — line ${frame.line}") }
    stack.groups.forEach { group ->
      Text(
        text = group.scope,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.sm),
      )
      group.variables.forEach { variable -> Text(text = variableRowText(variable)) }
    }
  }
}

private fun variableRowText(variable: DebugVariable): String =
  variable.type?.let { "${variable.name}: $it = ${variable.value}" } ?: "${variable.name} = ${variable.value}"

/** One placeholder for "nothing is open" and "nothing has stopped" - [NoHeadingsPlaceholder]'s own shape. */
@Composable
private fun NotStoppedPlaceholder(modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth().padding(Spacing.lg)) {
    Text(text = "Not stopped", style = MaterialTheme.typography.titleSmall)
    Text(
      text = "The call stack and variables show once a breakpoint stops the kernel.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = Spacing.sm),
    )
  }
}
