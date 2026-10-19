package com.jupydroid.app.ui.terminal

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalTextToolbar
import kotlinx.coroutines.launch

/**
 * The platform's floating text toolbar over the selection, offering Copy - the `ActionMode`
 * `TextSelectionCursorController` starts (termux-app `3df69d1d`), reached through Compose's
 * [LocalTextToolbar], which on Android is that same floating `ActionMode`. [bounds] is in root
 * coordinates, where the toolbar is placed; `null` means nothing is selected, and hides it.
 *
 * ```
 * TerminalSelectionToolbar(bounds = rootBounds, onCopy = { selector.copyInto(copyToClipboard) })
 * ```
 */
@Composable
fun TerminalSelectionToolbar(bounds: Rect?, onCopy: () -> Unit) {
  val toolbar = LocalTextToolbar.current
  val currentOnCopy by rememberUpdatedState(onCopy)
  DisposableEffect(toolbar, bounds) {
    if (bounds == null) toolbar.hide() else toolbar.showMenu(bounds, onCopyRequested = { currentOnCopy() })
    onDispose { toolbar.hide() }
  }
}

/**
 * Writes text to Compose's [LocalClipboard]: the clipboard is a service Compose already provides,
 * and a test swaps it out through the same local, so no port of this app's own stands in front of it.
 *
 * ```
 * val copyToClipboard = rememberTerminalClipboardCopy()
 * copyToClipboard("sel42word")
 * ```
 */
@Composable
fun rememberTerminalClipboardCopy(): (String) -> Unit {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  return { text ->
    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(TERMINAL_CLIP_LABEL, text))) }
  }
}

/** [local] - a rectangle in these coordinates - in the root's, which is where the toolbar is placed. */
internal fun LayoutCoordinates.rootRectOf(local: Rect): Rect =
  Rect(localToRoot(local.topLeft), localToRoot(local.bottomRight))

/** What the system's clipboard preview names the text's source. */
private const val TERMINAL_CLIP_LABEL = "Terminal"
