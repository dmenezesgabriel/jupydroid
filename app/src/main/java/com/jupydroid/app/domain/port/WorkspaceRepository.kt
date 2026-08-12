package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.WorkspaceEntry

/**
 * Reads the directory the kernel, the server and the app all share.
 *
 * Since X1a that is one tree - the kernel's `HOME`, the server's `--ServerApp.root_dir` and the
 * notebook repository's root - so a notebook can `open('data.csv')` on a file the user can see.
 * This is what lets the user see it back.
 *
 * Paths are **relative to the workspace root** throughout, which is `Contents.IModel.path` in
 * JupyterLab: `""` is the root, `reports/summary.ipynb` is a notebook one level down. Nothing above
 * the root is addressable - `list("../..")` is refused, not served, the way jupyter_server's
 * `_validate_path` refuses anything resolving outside `root_dir`.
 *
 * ```
 * repository.list("reports")   // entries, directories first, natural name order
 * ```
 */
interface WorkspaceRepository {
  /**
   * What is in [path], already in the order the browser shows it.
   *
   * Ordered here rather than by the caller because the order is part of the behaviour, not a
   * presentation choice: `Private.sort` puts directories first and compares names naturally.
   * Hidden entries are left out, as `ContentsManager.allow_hidden` (false by default) leaves them
   * out of the reference.
   */
  suspend fun list(path: String): List<WorkspaceEntry>

  /**
   * The whole file at [path] as text.
   *
   * One string rather than a stream: this serves an editor, and `Contents.IModel.content` for a
   * text file is likewise the whole thing (`fileFormat: 'text'` in the type registry). Refuses a
   * path outside the root, and a path that names nothing, the same way [list] does.
   */
  suspend fun readText(path: String): String

  /**
   * Replaces the file at [path] with [text], creating it when it is not there.
   *
   * Whole-file too, because that is what `contents.save` sends. The parent directory must already
   * exist - creating one is a separate act with its own name, as `newUntitled(type: 'directory')`
   * is in the reference.
   */
  suspend fun writeText(path: String, text: String)

  /**
   * Creates an empty file in [directory] and answers with its path.
   *
   * `newUntitled({path, type: "file", ext})`, whose name the *server* supplies: `untitled` plus
   * the extension, incremented past whatever is already there
   * (`services/contents/manager.py:608-613`). So the caller chooses a type, never a name - naming
   * comes after, from the rename the reference opens on the new item.
   *
   * @param extension without the dot, as `createNewFile({ ext: 'txt' })` passes it.
   */
  suspend fun createFile(directory: String, extension: String): String

  /**
   * Creates a folder in [directory] and answers with its path.
   *
   * `Untitled Folder`, counting with a **space** - the server passes `insert=" "` for a directory
   * and for nothing else (`manager.py:603-604`), so the second one is `Untitled Folder 1`.
   */
  suspend fun createFolder(directory: String): String
}
