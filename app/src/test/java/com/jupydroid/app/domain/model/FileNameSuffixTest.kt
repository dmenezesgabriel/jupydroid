package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a file's name ends and its suffix begins, which decides what a rename selects and where
 * a de-duplicating counter goes.
 *
 * jupyter_server answers it in one place and asymmetrically
 * (`services/contents/manager.py:543-549`, from the server in our own rootfs):
 *
 * ```python
 * basename, dot, ext = filename.rpartition(".")
 * if ext != "ipynb":
 *     basename, dot, ext = filename.partition(".")
 * suffix = dot + ext
 * ```
 *
 * The `rpartition` first is what keeps `Untitled.ipynb` from being read as stem `Untitled` and
 * suffix `.ipynb` by luck; the fall back to `partition` is what gives `archive.tar.gz` its *full*
 * suffix, which is the comment the server carries above the line. Transcribed rather than
 * invented, because the same directory is served by that server and a second convention in it is
 * a second answer to one question.
 */
class FileNameSuffixTest {
  @Test
  fun aNotebookKeepsItsOwnSuffix() {
    assertEquals("Untitled" to ".ipynb", stemAndSuffixOf("Untitled.ipynb"))
  }

  @Test
  fun anOrdinaryFileSplitsAtItsFirstDot() {
    assertEquals("untitled" to ".txt", stemAndSuffixOf("untitled.txt"))
  }

  /** The server's own example, and the reason it does not simply `rpartition` everything. */
  @Test
  fun aDoubleSuffixIsTakenWhole() {
    assertEquals("archive" to ".tar.gz", stemAndSuffixOf("archive.tar.gz"))
  }

  /** A notebook is the one type read from the right, so `data.backup.ipynb` keeps the middle. */
  @Test
  fun aNotebookWithDotsInItsNameKeepsThemInTheStem() {
    assertEquals("data.backup" to ".ipynb", stemAndSuffixOf("data.backup.ipynb"))
  }

  @Test
  fun aNameWithNoDotIsAllStem() {
    assertEquals("README" to "", stemAndSuffixOf("README"))
  }

  @Test
  fun aDotfileIsAllSuffixTheWayThePartitionLeavesIt() {
    assertEquals("" to ".gitignore", stemAndSuffixOf(".gitignore"))
  }
}

/**
 * The counter goes on the stem, so the suffix survives - `untitled1.txt`, never `untitled.txt1`.
 *
 * `increment_filename(filename, path, insert)` (`manager.py:526-560`) splits the suffix off first
 * and rebuilds `f"{basename}{insert_i}{suffix}"`, so this is the same series [incrementedName]
 * already produces, applied to the stem.
 */
class IncrementedFileNameTest {
  @Test
  fun anUntakenNameIsLeftAlone() {
    assertEquals("untitled.txt", incrementedFileName("untitled.txt") { false })
  }

  @Test
  fun theCounterGoesBeforeTheSuffix() {
    assertEquals("untitled1.txt", incrementedFileName("untitled.txt") { it == "untitled.txt" })
  }

  @Test
  fun itKeepsCountingPastTheOnesThatAreTaken() {
    val taken = setOf("Untitled.ipynb", "Untitled1.ipynb", "Untitled2.ipynb")
    assertEquals("Untitled3.ipynb", incrementedFileName("Untitled.ipynb") { it in taken })
  }

  /**
   * A folder counts with a space, which is the `insert=" "` the server passes for a directory
   * (`manager.py:603-604`) - so `Untitled Folder 1`, not `Untitled Folder1`.
   */
  @Test
  fun aFolderCountsWithTheSpaceTheServerInserts() {
    assertEquals(
      "Untitled Folder 1",
      incrementedFileName("Untitled Folder", insert = " ") { it == "Untitled Folder" },
    )
  }
}
