package com.jupydroid.app

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard

/**
 * A clipboard that keeps what it was given, so the test reads it without the system's own.
 *
 * ```
 * val clipboard = RecordingClipboard(context.getSystemService(ClipboardManager::class.java))
 * CompositionLocalProvider(LocalClipboard provides clipboard) { TerminalPaneScreen(viewModel) }
 * ```
 */
class RecordingClipboard(override val nativeClipboard: NativeClipboard) : Clipboard {
  private var entry: ClipEntry? = null

  override suspend fun getClipEntry(): ClipEntry? = entry

  override suspend fun setClipEntry(clipEntry: ClipEntry?) {
    entry = clipEntry
  }

  fun text(): String? = entry?.clipData?.getItemAt(0)?.text?.toString()
}
