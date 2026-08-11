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
}
