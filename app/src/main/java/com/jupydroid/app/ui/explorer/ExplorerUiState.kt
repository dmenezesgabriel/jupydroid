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
  /**
   * The path a creation just made, until the listing has scrolled to it - or null.
   *
   * An older defect, on the surface it moved to: a created row that lands below the fold makes the
   * screen look unchanged. Home answered it by prepending and scrolling to the top; the browser
   * orders by name, so the row can land anywhere and the answer has to name it. The reference gets
   * there by a different route - `_createNew` hands its result to `rename()`, which selects and
   * scrolls to the item (`browser.ts:529-546`).
   */
  val justCreated: String? = null,
) {
  /** The root is where the explorer stops going up; from anywhere else there is a parent. */
  val canGoUp: Boolean get() = path.isNotEmpty()
}

/**
 * What the "+" menu can make: the reference's New widget has exactly these three
 * (`packages/filebrowser-extension`), and `new_untitled` is typed for the same reason.
 *
 * On a phone the reference's per-filetype list collapses to the one type this app both creates and
 * opens; a fuller menu would mostly offer things nothing here could open.
 */
enum class NewItem {
  NOTEBOOK,
  PYTHON_FILE,
  FOLDER,
}
