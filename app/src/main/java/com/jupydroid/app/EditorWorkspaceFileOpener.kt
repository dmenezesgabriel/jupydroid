package com.jupydroid.app

import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.extension.api.WorkspaceFileOpener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * A panel's file opened in a document tab as the explorer opens one, written first when it is missing.
 *
 * Through [WorkspaceFiles] rather than the server's Contents API, which refuses a hidden path while
 * `ContentsManager.allow_hidden` is false - and `.jupyter/` is hidden.
 *
 * ```
 * EditorWorkspaceFileOpener(spacePorts.workspaceFiles, appContainer.applicationScope + Dispatchers.Main, openDocument)
 * ```
 *
 * @param scope where the write and the open run; on Main, since opening moves the back stack, and on
 *   the application's scope, so closing the panel cannot cut the write short.
 * @param openDocument the shell's own opener, so the file lands in a tab as any file does.
 */
class EditorWorkspaceFileOpener(
  private val workspaceFiles: WorkspaceFiles,
  private val scope: CoroutineScope,
  private val openDocument: (String) -> Unit,
) : WorkspaceFileOpener {
  override fun open(path: String, initialText: String) {
    scope.launch {
      // Opened even when the write failed: the editor says what it cannot read, where a press
      // that opened nothing would say nothing.
      try {
        workspaceFiles.createIfAbsent(path, initialText)
      } catch (_: IOException) {
      }
      openDocument(path)
    }
  }
}
