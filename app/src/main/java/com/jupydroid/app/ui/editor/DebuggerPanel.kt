package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.theme.CodeText
import com.jupydroid.app.theme.Spacing
import kotlinx.coroutines.launch

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
  onExpand: suspend (Int) -> List<DebugVariable>,
  presentation: CellActionPresentation = currentCellActionPresentation(),
) {
  if (!expanded) return
  when (presentation) {
    CellActionPresentation.MENU -> DebuggerMenu(stack, onDismiss, onExpand)
    CellActionPresentation.SHEET -> DebuggerSheet(stack, onDismiss, onExpand)
  }
}

@Composable
private fun DebuggerMenu(stack: DebugStack?, onDismiss: () -> Unit, onExpand: suspend (Int) -> List<DebugVariable>) {
  DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
    // No extra padding here - DebugStackRows carries its own per-row margin now (P6a-13), the
    // same split TableOfContentsPanel's own menu/sheet already keep with TableOfContentsRows.
    DebugStackRows(stack, onExpand = onExpand)
  }
}

/** `navigationBarsPadding` for [CellActionSheet]'s own reason: the sheet draws under the gesture bar otherwise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebuggerSheet(stack: DebugStack?, onDismiss: () -> Unit, onExpand: suspend (Int) -> List<DebugVariable>) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
    ) {
      DebugStackRows(stack, onExpand = onExpand)
    }
  }
}

/**
 * The call stack and the top frame's variables, shared with
 * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel] - the surfaces differ in
 * where they are drawn, never in what a stop looks like.
 *
 * **Padded and indented rather than bare `Text`** (P6a-13): every row carries the same
 * `Spacing.md` horizontal margin [SearchSidePanel][com.jupydroid.app.ui.shell.SearchSidePanel]'s
 * own rows already use, a variable sits one indent step (`Spacing.xl`) under its scope heading -
 * [TableOfContentsRows]' own per-level indentation, applied at the one level this flat model has -
 * and a value is drawn in [codeTextStyle], the same monospace this app already reserves for
 * anything that is source rather than prose.
 *
 * **A compound variable expands on tap** (P6a-14), closing the Open risks entry
 * `docs/PLAN.md` named for exactly this: [DebugVariable.variablesReference] was already fetched
 * from the kernel on every reply and then discarded. [onExpand] is [NotebookDebugger.expand] -
 * this composable owns no session of its own, so it asks the debugger for one instead of trying
 * to keep one. No per-frame reselection still, named on [DebugStack]'s own doc as the one gap
 * that remains.
 */
@Composable
internal fun DebugStackRows(
  stack: DebugStack?,
  onExpand: suspend (Int) -> List<DebugVariable> = { emptyList() },
  modifier: Modifier = Modifier,
) {
  if (stack == null) {
    NotStoppedPlaceholder(modifier)
    return
  }
  Column(modifier = modifier) {
    stack.frames.forEach { frame ->
      Text(
        text = "${frame.name} — line ${frame.line}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
      )
    }
    stack.groups.forEach { group ->
      Text(
        text = group.scope,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs).padding(top = Spacing.sm),
      )
      group.variables.forEach { variable -> VariableRow(variable, depth = 0, onExpand = onExpand) }
    }
  }
}

/**
 * One variable, and - once tapped - the children [DebugVariable.variablesReference] promised.
 * Recurses on itself for a grandchild rather than flattening the tree, so a `dict` of `list`s
 * indents exactly as deep as it actually nests.
 *
 * **Fetched once per expansion, not cached across collapses**: `remember(variable)` keys on the
 * variable's own identity, so re-expanding the same reference after collapsing it asks the kernel
 * again rather than trusting a stale snapshot from before the last step/continue.
 */
@Composable
private fun VariableRow(variable: DebugVariable, depth: Int, onExpand: suspend (Int) -> List<DebugVariable>) {
  var children by remember(variable) { mutableStateOf<List<DebugVariable>?>(null) }
  val scope = rememberCoroutineScope()
  val expandable = variable.variablesReference != 0
  val indent = Spacing.xl + Spacing.md * depth
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .let { row ->
        if (!expandable) {
          row
        } else {
          row.clickable {
            if (children != null) {
              children = null
            } else {
              scope.launch { children = onExpand(variable.variablesReference) }
            }
          }
        }
      }
      .padding(start = indent, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
  ) {
    // A fixed-width leading slot even for a leaf with nothing to disclose, so every variable's
    // text still lines up - TableOfContentsRows' own reason for TOC_DISCLOSURE_WIDTH.
    if (expandable) {
      Icon(
        imageVector = if (children != null) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
        contentDescription = null,
        modifier = Modifier.size(DISCLOSURE_ICON_SIZE),
      )
    } else {
      Spacer(modifier = Modifier.size(DISCLOSURE_ICON_SIZE))
    }
    Text(text = variableRowText(variable), style = codeTextStyle())
  }
  children?.forEach { child -> VariableRow(child, depth = depth + 1, onExpand = onExpand) }
}

private fun variableRowText(variable: DebugVariable): String =
  variable.type?.let { "${variable.name}: $it = ${variable.value}" } ?: "${variable.name} = ${variable.value}"

/**
 * Close to [CodeText.size]'s own 14sp cap-height, so the disclosure sits beside a variable row's
 * monospace text without dominating it - `TOC_DISCLOSURE_WIDTH`'s own reason, sized for this row's
 * own text instead of restated at that row's own value.
 */
private val DISCLOSURE_ICON_SIZE = 16.dp

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
