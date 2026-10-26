package com.jupydroid.extensions.ai.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import com.jupydroid.extension.api.NotebookActiveCell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What is offered under a code block of an agent's answer, at its right, as jupyter-chat's toolbar is
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/code-blocks/code-toolbar.tsx`): icon
 * buttons, its insert before its copy. Where jupyter-chat names each in a tooltip on hover, a long
 * press shows it here, since a touchscreen has no hover.
 *
 * ```
 * host.markdown.Draw(message.body, Modifier.padding(8.dp)) { code -> CodeBlockActions(code, host.activeCell) }
 * ```
 */
@Composable
fun CodeBlockActions(code: String, activeCell: NotebookActiveCell) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    InsertButton(code, activeCell)
    CopyButton(code)
  }
}

/**
 * Puts [code] in a new code cell below the notebook's active cell, the one insert both jupyter-chat
 * and VS Code offer (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/actions/codeBlockOperations.ts`).
 * Disabled with no active cell, and named so, as jupyter-chat's is (`code-toolbar.tsx`).
 */
@Composable
private fun InsertButton(code: String, activeCell: NotebookActiveCell) {
  val available = activeCell.available
  val description = if (available) INSERT_BELOW else "$INSERT_BELOW (no active cell)"
  CodeBlockAction(description, Icons.Outlined.AddBox, enabled = available) { activeCell.insertBelow(code) }
}

/**
 * Puts [code] on the clipboard and shows a check for a second, as jupyter-chat's icon reads "Copied!"
 * (`copy-button.tsx`): Android shows its own confirmation only from API 33, and this runs from 32.
 */
@Composable
private fun CopyButton(code: String) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  var copies by remember { mutableIntStateOf(0) }
  LaunchedEffect(copies) {
    if (copies > 0) delay(COPIED_SHOWN_MILLIS)
    copies = 0
  }
  val copied = copies > 0
  CodeBlockAction(if (copied) "Copied" else "Copy", if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy) {
    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CHAT_CLIP_LABEL, code))) }
    copies++
  }
}

/** An icon button named by [description], read by TalkBack and shown in a tooltip on a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeBlockAction(description: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState(),
  ) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = description) }
  }
}

/** jupyter-chat's `setTimeout(..., 1000)` (`copy-button.tsx`). */
private const val COPIED_SHOWN_MILLIS = 1_000L

/** What the system's clipboard preview names the text's source. */
private const val CHAT_CLIP_LABEL = "Chat"

/** jupyter-chat's insert below's own title (`code-toolbar.tsx`). */
private const val INSERT_BELOW = "Insert below active cell"
