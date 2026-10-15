package com.jupydroid.extensions.git.ui

import com.jupydroid.extensions.git.domain.ChangeStage
import com.jupydroid.extensions.git.domain.ChangedFile

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

/**
 * What [file] is called under [stage]: jupyterlab-git's `FileItem` reads the index's letter under
 * Staged and the work tree's everywhere else, and names it through `get_status`
 * (jupyterlab-git@b62a87b7 `src/components/FileItem.tsx`).
 *
 * ```
 * changeLabel(ChangedFile("a.txt", ' ', 'M'), ChangeStage.Changed) // "Modified"
 * ```
 */
internal fun changeLabel(file: ChangedFile, stage: ChangeStage): String {
  if (stage == ChangeStage.Conflicted) return "Conflicted"
  val letter = letterUnder(file, stage)
  return STATUS_LABELS[letter] ?: "Unmodified"
}

/**
 * The letter on [file]'s row under [stage]: `!` for a conflict and `U` for an untracked file, which
 * git itself marks `?` (jupyterlab-git@b62a87b7 `src/components/FileItem.tsx`, `badge_code`).
 *
 * ```
 * changeBadge(ChangedFile("c.txt", '?', '?'), ChangeStage.Untracked) // "U"
 * ```
 */
internal fun changeBadge(file: ChangedFile, stage: ChangeStage): String = when {
  stage == ChangeStage.Conflicted -> "!"
  file.workTree == '?' -> "U"
  else -> letterUnder(file, stage).toString()
}

private fun letterUnder(file: ChangedFile, stage: ChangeStage): Char =
  if (stage == ChangeStage.Staged) file.index else file.workTree

/** git's short-format letters (https://git-scm.com/docs/git-status#_short_format), as `get_status` names them. */
private val STATUS_LABELS: Map<Char, String> = mapOf(
  'M' to "Modified",
  'A' to "Added",
  'D' to "Deleted",
  'R' to "Renamed",
  'C' to "Copied",
  'U' to "Updated",
  '?' to "Untracked",
  '!' to "Ignored",
)
