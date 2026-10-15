package com.jupydroid.extensions.git.ui

/**
 * What the panel says where there is no repository: jupyterlab-git's `_renderWarning`, which names
 * the directory by its basename and has a second form for the root, which has none
 * (jupyterlab-git@b62a87b7 `src/components/GitPanel.tsx`). Its closing offer - initialize or clone - is
 * left out, because the panel offers neither yet.
 *
 * ```
 * notARepositoryMessage("projects/notes") // "notes is not a Git repository."
 * ```
 */
internal fun notARepositoryMessage(path: String): String {
  val name = path.trim('/').substringAfterLast('/')
  if (name.isEmpty()) return "You are not currently in a Git repository."
  return "$name is not a Git repository."
}
