package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.theme.CodeText
import com.jupydroid.app.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The call stack and variables, docked directly above [DebuggerControlBar]'s own toggle - a
 * Material 3 *standard* (non-modal) bottom sheet's own shape, not
 * [androidx.compose.material3.ModalBottomSheet]'s: a paused kernel's data is meant to be glanced
 * at while the control bar and the stopped line above stay reachable, never a blocking overlay a
 * scrim puts the rest of the screen behind.
 *
 * **JupyterLab's own Debugger sidebar has no "show" command to mirror** - it registers on the
 * shell's *right* area (`packages/debugger-extension/src/index.ts:1357`) and is simply always
 * there once a debugger-capable kernel starts. This app has one side area
 * ([SidePanel.DEBUGGER][com.jupydroid.app.SidePanel]'s own doc), so a one-pane window - which has
 * none - gets this instead, toggled from [DebuggerControlBar] rather than a menu three taps deep:
 * the same "a paused kernel beats all three" rule that bar's own doc already states for its
 * step/continue controls applies to seeing why it paused.
 *
 * **One shape at every one-pane width**, unlike [CellActions]' own `MENU`/`SHEET` split: this has
 * no button of its own to anchor a `DropdownMenu` to, and [fillMaxHeight] already bounds it the
 * way a modal sheet's own height would, so there is nothing the split would still buy here.
 */
@Composable
internal fun DebuggerVariablesSheet(
  stack: DebugStack?,
  onExpand: suspend (Int) -> List<DebugVariable>,
  onSelectFrame: suspend (Int) -> Unit,
  onEvaluate: suspend (String) -> DebugVariable?,
  modifier: Modifier = Modifier,
) {
  Surface(tonalElevation = SHEET_ELEVATION, modifier = modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      DebugStackRows(stack, onExpand = onExpand, onSelectFrame = onSelectFrame, onEvaluate = onEvaluate)
    }
  }
}

/** Leaves the stopped line and the rest of the notebook visible above the sheet on any window. */
private const val SHEET_HEIGHT_FRACTION = 0.4f

/** [CellCompletions]' own `POPUP_ELEVATION` - the same "a surface drawn over the notebook" role. */
private val SHEET_ELEVATION = 3.dp

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
 * to keep one.
 *
 * **A frame other than the top one is selectable on tap** (P6a-15), closing [DebugStack]'s own
 * other named gap: [onSelectFrame] is [NotebookDebugger.selectFrame], asked the same way [onExpand]
 * is. The tapped row is tracked locally (`remember(stack.frames)`) rather than added to
 * [DebugStack] itself - the frame list is the same object across a reselection (only [groups]
 * changes), so keying on it does not lose the highlight when [onSelectFrame] answers, and a wholly
 * new stop (a fresh `frames` list) resets it back to the top frame without extra state to clear.
 *
 * **An ad hoc expression can be run against the top frame** (P6a-17) - [onEvaluate] is
 * [NotebookDebugger.evaluate], the same "ask the debugger, keep no session here" shape [onExpand]
 * already uses. Its own answer renders as one more variable-shaped row ([variableRowText]) rather
 * than a second layout, since it already is one DAP-side.
 */
@Composable
internal fun DebugStackRows(
  stack: DebugStack?,
  onExpand: suspend (Int) -> List<DebugVariable> = { emptyList() },
  onSelectFrame: suspend (Int) -> Unit = {},
  onEvaluate: suspend (String) -> DebugVariable? = { null },
  modifier: Modifier = Modifier,
) {
  if (stack == null) {
    NotStoppedPlaceholder(modifier)
    return
  }
  var selectedFrameId by remember(stack.frames) { mutableStateOf(stack.frames.firstOrNull()?.id) }
  val scope = rememberCoroutineScope()
  Column(modifier = modifier) {
    stack.frames.forEach { frame ->
      Text(
        text = "${frame.name} — line ${frame.line}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (frame.id == selectedFrameId) MaterialTheme.colorScheme.primary else Color.Unspecified,
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            selectedFrameId = frame.id
            scope.launch { onSelectFrame(frame.id) }
          }
          .padding(horizontal = Spacing.md, vertical = Spacing.xs),
      )
    }
    EvaluateRow(stack, onEvaluate)
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
 * A single-line expression, submitted on the IME's own Done action - a debugger-scoped REPL of
 * one line, not a persistent watch list this phone-scoped surface has no room for. Cleared on any
 * change to [stack] - a fresh stop or a reselected frame alike - the same reason [VariableRow]'s
 * own children reset on a new [remember] key: an answer from a scope the panel has since moved
 * away from is stale, not merely old.
 */
@Composable
private fun EvaluateRow(stack: DebugStack, onEvaluate: suspend (String) -> DebugVariable?) {
  var expression by remember(stack) { mutableStateOf(TextFieldValue("")) }
  var result by remember(stack) { mutableStateOf<DebugVariable?>(null) }
  val scope = rememberCoroutineScope()
  fun submit() {
    val code = expression.text.trim()
    if (code.isNotBlank()) scope.launch { result = onEvaluate(code) }
  }
  Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
    OutlinedTextField(
      value = expression,
      onValueChange = { expression = it },
      modifier = Modifier.fillMaxWidth().testTag(EVALUATE_FIELD_TAG),
      singleLine = true,
      label = { Text("Evaluate") },
      placeholder = { Text("e.g. x + 1") },
      // Same reason BreakpointConditionDialog's own field needs this: a Python expression is
      // case-sensitive, and the IME's default sentence-capitalization would otherwise silently
      // rewrite a lowercase identifier typed first.
      keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        imeAction = ImeAction.Done,
      ),
      keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    result?.let {
      Text(text = variableRowText(it), style = codeTextStyle(), modifier = Modifier.padding(top = Spacing.xs))
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

/** So an instrumented test can find [EvaluateRow]'s own field without depending on its label text. */
const val EVALUATE_FIELD_TAG = "debugger-evaluate-field"

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
