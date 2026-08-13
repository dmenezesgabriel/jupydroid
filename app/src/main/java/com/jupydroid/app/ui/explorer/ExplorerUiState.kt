package com.jupydroid.app.ui.explorer

import com.jupydroid.app.domain.model.WorkspaceEntry

/**
 * One directory of the workspace, as the explorer shows it.
 *
 * @property path where we are, relative to the workspace root - `""` is the root
 * @property entries already ordered: directories first, then names naturally (`inWorkspaceOrder`)
 * @property crumbs each ancestor as `path to label`, root first, so a crumb is a target
 * @property error what went wrong reaching a directory, or null
 * @property nameTaken the name a rename asked for and found occupied, or null. Non-null puts the
 *   reference's question on screen - `"%1" already exists, overwrite?` - and only a taken name
 *   gets there, exactly as only a 409 does in `renameFile`; an invalid name is never offered an
 *   overwrite, because it is simply not a name.
 */
data class ExplorerUiState(
  val path: String = "",
  val entries: List<WorkspaceEntry> = emptyList(),
  val crumbs: List<Pair<String, String>> = emptyList(),
  val error: String? = null,
  val nameTaken: String? = null,
) {
  /** The root is where the explorer stops going up; from anywhere else there is a parent. */
  val canGoUp: Boolean get() = path.isNotEmpty()
}

/**
 * The path split for the breadcrumb, root first.
 *
 * `packages/filebrowser/src/crumbs.ts` shows the same ancestry and makes each segment a target.
 * The root is labelled rather than shown as an empty string, because a crumb with no text is not
 * something a finger can aim at.
 */
internal fun crumbsFor(path: String, rootLabel: String): List<Pair<String, String>> {
  val segments = path.split("/").filter { it.isNotEmpty() }
  return buildList {
    add("" to rootLabel)
    segments.forEachIndexed { at, segment ->
      add(segments.take(at + 1).joinToString("/") to segment)
    }
  }
}
