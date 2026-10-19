package com.jupydroid.app.ui.terminal

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
 * The platform's floating text toolbar over the selection, offering Copy and Paste - the `ActionMode`
 * `TextSelectionCursorController` starts (termux-app `3df69d1d`), reached through Compose's
 * [LocalTextToolbar], which on Android is that same floating `ActionMode`. [bounds] is in root
 * coordinates, where the toolbar is placed; `null` hides it.
 *
 * **One `ActionMode` for as long as it is shown.** Compose's `AndroidTextToolbar.showMenu` starts one
 * only when none is running and otherwise `invalidate`s it, which moves it; `hide` finishes it. So
 * new bounds are only shown, never hidden first: a hide between two bounds made the platform fade a
 * new toolbar in at every step of a handle's drag. The reference's `render` invalidates its one
 * `ActionMode` the same way.
 *
 * ```
 * TerminalSelectionToolbar(rootBounds, onCopy = { selector.copyInto(copyToClipboard) }, onPaste = pasteFromClipboard)
 * ```
 */
@Composable
fun TerminalSelectionToolbar(bounds: Rect?, onCopy: () -> Unit, onPaste: () -> Unit) {
  val toolbar = LocalTextToolbar.current
  val currentOnCopy by rememberUpdatedState(onCopy)
  val currentOnPaste by rememberUpdatedState(onPaste)
  LaunchedEffect(toolbar, bounds) {
    if (bounds == null) {
      toolbar.hide()
    } else {
      toolbar.showMenu(bounds, onCopyRequested = { currentOnCopy() }, onPasteRequested = { currentOnPaste() })
    }
  }
  DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
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

/**
 * Reads the clipboard's text from Compose's [LocalClipboard] and hands it to [paste] - nothing, when
 * the clipboard holds no text, as `terminal:paste` pastes only `if (clipboardData)`
 * (`packages/terminal-extension/src/index.ts:717`, jupyterlab `7f0e6906`).
 *
 * ```
 * val pasteFromClipboard = rememberTerminalClipboardPaste(viewModel.emulator::paste)
 * pasteFromClipboard()
 * ```
 */
@Composable
fun rememberTerminalClipboardPaste(paste: (String) -> Unit): () -> Unit {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  return {
    scope.launch {
      val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
      if (!text.isNullOrEmpty()) paste(text.toString())
    }
  }
}

/** [local] - a rectangle in these coordinates - in the root's, which is where the toolbar is placed. */
internal fun LayoutCoordinates.rootRectOf(local: Rect): Rect =
  Rect(localToRoot(local.topLeft), localToRoot(local.bottomRight))

/** What the system's clipboard preview names the text's source. */
private const val TERMINAL_CLIP_LABEL = "Terminal"
