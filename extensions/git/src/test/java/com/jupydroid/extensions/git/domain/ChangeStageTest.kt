package com.jupydroid.extensions.git.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** jupyterlab-git's `decodeStage`, row by row (jupyterlab-git@b62a87b7 `src/utils.ts`). */
class ChangeStageTest {
  /** git's short format lists these seven pairs as unmerged (https://git-scm.com/docs/git-status#_short_format). */
  @Test
  fun everyUnmergedPairIsConflictedAndNothingElse() {
    val unmerged = listOf("DD", "AU", "UD", "UA", "DU", "AA", "UU")

    unmerged.forEach { pair -> assertEquals(pair, listOf(ChangeStage.Conflicted), stagesOf(pair)) }
  }

  @Test
  fun anUntrackedFileIsUntrackedOnly() {
    assertEquals(listOf(ChangeStage.Untracked), stagesOf("??"))
  }

  @Test
  fun aChangeOnlyInTheIndexIsStaged() {
    assertEquals(listOf(ChangeStage.Staged), stagesOf("A "))
  }

  @Test
  fun aChangeOnlyInTheWorkTreeIsChanged() {
    assertEquals(listOf(ChangeStage.Changed), stagesOf(" M"))
  }

  /** A partially-staged file: jupyterlab-git's `FileList` lists it under both headings. */
  @Test
  fun aChangeInBothIsStagedAndChanged() {
    assertEquals(listOf(ChangeStage.Staged, ChangeStage.Changed), stagesOf("MM"))
  }

  private fun stagesOf(pair: String): List<ChangeStage> =
    ChangedFile(path = "a.txt", index = pair[0], workTree = pair[1]).stages()
}
