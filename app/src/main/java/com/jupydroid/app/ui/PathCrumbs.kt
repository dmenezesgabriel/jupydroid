package com.jupydroid.app.ui

import com.jupydroid.app.domain.model.parentDirectory

/**
 * What the root crumb is called, since its path is the empty string.
 *
 * Here rather than private to the browser's `ViewModel`, because since P2w-1 the open document
 * draws a trail of its own and the two must not disagree about what the root is called.
 */
internal const val ROOT_LABEL = "Workspace"

/**
 * The path split for the breadcrumb, root first.
 *
 * `packages/filebrowser/src/crumbs.ts` shows the same ancestry and makes each segment a target.
 * The root is labelled rather than shown as an empty string, because a crumb with no text is not
 * something a finger can aim at.
 *
 * ```
 * crumbsFor("reports/q1", ROOT_LABEL)   // ["" to "Workspace", "reports" to "reports", "reports/q1" to "q1"]
 * ```
 *
 * @param path the directory to split, relative to the workspace root - `""` is the root.
 * @param rootLabel what to call the crumb whose path is empty.
 * @return each ancestor as `path to label`, root first, so a crumb is a target.
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

/**
 * Where an open *document* lives: the crumbs of its folder, and **nothing for a document at the
 * root**.
 *
 * **Two departures from the reference, both deliberate.** VS Code's trail ends with the file
 * itself (`FileKind.FILE`, `breadcrumbsModel.ts:142`) because its breadcrumb sits under a *tab
 * bar*, and a tab is also a close target and a document selector. Ours sits under a title row that
 * is neither and already carries the name, so the name is not said twice and every segment is a
 * folder - which is what makes "a segment opens that folder" one rule rather than two. And where
 * VS Code renders `"no elements"` for a document with no ancestry, this returns empty so the row
 * can be **absent**: 48dp spent saying nothing is the trade P2v declined the run gutter over.
 *
 * ```
 * ancestorCrumbsFor("reports/Nested.ipynb", ROOT_LABEL)   // ["" to "Workspace", "reports" to "reports"]
 * ancestorCrumbsFor("At Root.ipynb", ROOT_LABEL)          // []
 * ```
 *
 * @param documentPath the document's own path, suffix included - `EditorUiState.notebookId`.
 * @param rootLabel what to call the crumb whose path is empty.
 * @return each ancestor folder as `path to label`, root first; empty at the workspace root.
 */
internal fun ancestorCrumbsFor(documentPath: String, rootLabel: String): List<Pair<String, String>> {
  val folder = documentPath.parentDirectory()
  return if (folder.isEmpty()) emptyList() else crumbsFor(folder, rootLabel)
}
