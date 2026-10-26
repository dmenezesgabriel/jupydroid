package com.jupydroid.app

import com.jupydroid.extension.api.WorkspaceFileOpener

/**
 * A file opener that keeps each path it was asked to open, with the text a new file would start as.
 *
 * ```
 * val fileOpener = RecordingWorkspaceFileOpener()
 * SidePanelHost("", server = null, spaceId = "base", RichTextMarkdownRenderer, activeCell, fileOpener)
 * ```
 */
class RecordingWorkspaceFileOpener : WorkspaceFileOpener {
  val opened = mutableListOf<Pair<String, String>>()

  override fun open(path: String, initialText: String) {
    opened += path to initialText
  }
}
