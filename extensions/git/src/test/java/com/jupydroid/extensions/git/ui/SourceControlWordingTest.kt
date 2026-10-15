package com.jupydroid.extensions.git.ui

import com.jupydroid.extensions.git.domain.ChangeStage
import com.jupydroid.extensions.git.domain.ChangedFile
import org.junit.Assert.assertEquals
import org.junit.Test

/** jupyterlab-git's own warning, which names the directory and has a second form for the root. */
class SourceControlWordingTest {
  @Test
  fun aSubdirectoryIsNamedByItsLastSegment() {
    assertEquals("notes is not a Git repository.", notARepositoryMessage("projects/notes"))
  }

  @Test
  fun theWorkspaceRootHasNoNameToGive() {
    assertEquals("You are not currently in a Git repository.", notARepositoryMessage(""))
  }

  @Test
  fun aWorkTreeChangeIsNamedByItsLetter() {
    val labels = "MADRC".map { letter -> changeLabel(file(' ', letter), ChangeStage.Changed) }

    assertEquals(listOf("Modified", "Added", "Deleted", "Renamed", "Copied"), labels)
  }

  /** `FileItem` reads the index's letter under Staged and the work tree's everywhere else. */
  @Test
  fun aPartlyStagedFileReadsEachSideUnderItsOwnHeading() {
    val file = file('A', 'M')

    assertEquals("Added" to "A", shown(file, ChangeStage.Staged))
    assertEquals("Modified" to "M", shown(file, ChangeStage.Changed))
  }

  @Test
  fun anUntrackedFileIsBadgedUAndCalledUntracked() {
    val file = file('?', '?')

    assertEquals("Untracked" to "U", shown(file, ChangeStage.Untracked))
  }

  @Test
  fun aConflictIsBadgedWithABangWhateverItsLetters() {
    val file = file('U', 'U')

    assertEquals("Conflicted" to "!", shown(file, ChangeStage.Conflicted))
  }

  private fun shown(file: ChangedFile, stage: ChangeStage) = changeLabel(file, stage) to changeBadge(file, stage)

  private fun file(index: Char, workTree: Char) = ChangedFile(path = "a.txt", index = index, workTree = workTree)
}
