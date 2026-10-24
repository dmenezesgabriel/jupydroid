package com.jupydroid.extensions.ai.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What is offered under a code block of an agent's answer, at its right, as jupyter-chat's toolbar is
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/code-blocks/code-toolbar.tsx`). Labelled
 * rather than an icon with a tooltip: a touchscreen has no hover.
 *
 * ```
 * host.markdown.Draw(message.body, Modifier.padding(8.dp)) { code -> CodeBlockActions(code) }
 * ```
 */
@Composable
fun CodeBlockActions(code: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    CopyButton(code)
  }
}

/**
 * Puts [code] on the clipboard and reads "Copied" for a second, as jupyter-chat's tooltip reads
 * "Copied!" (`copy-button.tsx`): Android shows its own confirmation only from API 33, and this runs from 32.
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
  TextButton(onClick = {
    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CHAT_CLIP_LABEL, code))) }
    copies++
  }) { Text(if (copies > 0) "Copied" else "Copy") }
}

/** jupyter-chat's `setTimeout(..., 1000)` (`copy-button.tsx`). */
private const val COPIED_SHOWN_MILLIS = 1_000L

/** What the system's clipboard preview names the text's source. */
private const val CHAT_CLIP_LABEL = "Chat"
