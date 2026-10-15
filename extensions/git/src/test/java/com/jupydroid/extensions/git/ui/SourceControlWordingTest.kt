package com.jupydroid.extensions.git.ui

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
}
