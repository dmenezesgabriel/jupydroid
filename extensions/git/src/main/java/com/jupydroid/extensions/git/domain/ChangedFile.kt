package com.jupydroid.extensions.git.domain

/**
 * One line of `git status --porcelain`: a file and the two letters git gives it.
 *
 * @param path relative to the repository's root, as git prints it; a rename's new name.
 * @param index the index's letter, `X` in git's short format; `' '` when the index has no change.
 * @param workTree the work tree's letter, `Y`; `'?'` in both for an untracked file.
 */
data class ChangedFile(val path: String, val index: Char, val workTree: Char)

/** A heading the panel lists changes under, in the order jupyterlab-git's `FileList` draws them. */
enum class ChangeStage { Conflicted, Staged, Changed, Untracked }

/**
 * The headings this file is listed under: jupyterlab-git's `decodeStage`
 * (jupyterlab-git@b62a87b7 `src/utils.ts`). A file changed in both the index and the work tree is
 * listed twice, under Staged and under Changed, as `FileList` lists a partially-staged one.
 *
 * ```
 * ChangedFile("a.txt", index = 'M', workTree = 'M').stages() // [Staged, Changed]
 * ```
 */
fun ChangedFile.stages(): List<ChangeStage> = when {
  workTree in UNMERGED[index].orEmpty() -> listOf(ChangeStage.Conflicted)
  index == '?' && workTree == '?' -> listOf(ChangeStage.Untracked)
  else -> listOfNotNull(
    ChangeStage.Staged.takeIf { index != ' ' },
    ChangeStage.Changed.takeIf { workTree != ' ' },
  )
}

/**
 * Each index letter mapped to the work-tree letters that make the pair unmerged
 * (https://git-scm.com/docs/git-status#_short_format).
 */
private val UNMERGED: Map<Char, String> = mapOf('D' to "DU", 'A' to "UA", 'U' to "DAU")
