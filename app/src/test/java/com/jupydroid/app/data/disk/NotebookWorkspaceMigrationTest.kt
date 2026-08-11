package com.jupydroid.app.data.disk

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Moving every notebook into the workspace the kernel can see, exactly once.
 *
 * This is the destructive step of Phase X and it runs on somebody's real notebooks, so the cases
 * here are the ones that lose data if they are wrong: a name already taken, a file that cannot be
 * read, and **running twice** - which is not paranoia but this project's own scar tissue, since
 * the re-extract path was never exercised until a rootfs bump crash-looped the app
 * (`docs/PLAN.md`: "every install-time path needs a second install to be tested at all").
 */
class NotebookWorkspaceMigrationTest {
  @get:Rule val temp = TemporaryFolder()

  private val from get() = File(temp.root, "notebooks")
  private val to get() = File(temp.root, "spaces/base/home/jupydroid")

  @Test
  fun itMovesEveryNotebookIntoTheWorkspace() {
    notebook("alpha")
    notebook("beta")

    migrate()

    assertEquals(setOf("alpha.ipynb", "beta.ipynb"), to.list()?.toSet())
    assertFalse("the old copy must not be left behind", File(from, "alpha.ipynb").exists())
  }

  @Test
  fun itKeepsTheContents() {
    notebook("alpha", body = """{"nbformat":4,"cells":[]}""")

    migrate()

    assertEquals("""{"nbformat":4,"cells":[]}""", File(to, "alpha.ipynb").readText())
  }

  /**
   * A name already in the workspace is **not** overwritten.
   *
   * The workspace is a real directory the user and the kernel both write to, so a collision is an
   * ordinary event, not a corrupt state - and the file already there is the one that might be the
   * newer of the two. The incoming one gets a suffix; nothing is lost either way.
   */
  @Test
  fun aCollisionIsSuffixedRatherThanOverwritten() {
    notebook("alpha", body = "incoming")
    to.mkdirs()
    File(to, "alpha.ipynb").writeText("already here")

    migrate()

    assertEquals("already here", File(to, "alpha.ipynb").readText())
    assertEquals("incoming", File(to, "alpha-1.ipynb").readText())
  }

  @Test
  fun aSecondCollisionCountsOn() {
    notebook("alpha", body = "incoming")
    to.mkdirs()
    File(to, "alpha.ipynb").writeText("already here")
    File(to, "alpha-1.ipynb").writeText("also here")

    migrate()

    assertEquals("incoming", File(to, "alpha-2.ipynb").readText())
  }

  /** Not a notebook, not our business: it stays where it is rather than being swept along. */
  @Test
  fun itLeavesFilesThatAreNotNotebooks() {
    from.mkdirs()
    File(from, "notes.txt").writeText("hello")

    migrate()

    assertTrue(File(from, "notes.txt").exists())
    assertFalse(File(to, "notes.txt").exists())
  }

  /**
   * The second run must be a no-op, and this is the case the project has been burned by.
   *
   * By then the source directory is empty or gone, and the workspace holds the migrated files -
   * so a migration that ran again without its stamp would suffix every notebook a second time.
   */
  @Test
  fun runningTwiceChangesNothing() {
    notebook("alpha")

    migrate()
    migrate()

    assertEquals(setOf("alpha.ipynb"), to.list()?.toSet())
  }

  /** A notebook created *after* the migration must not be dragged back through it. */
  @Test
  fun aLaterNotebookInTheOldPlaceIsLeftAloneOnceMigrated() {
    notebook("alpha")
    migrate()

    notebook("stray")
    migrate()

    assertFalse("the migration already ran; it must not run again", File(to, "stray.ipynb").exists())
  }

  @Test
  fun nothingToMoveStillStampsSoItNeverRunsAgain() {
    migrate()

    notebook("late")
    migrate()

    assertFalse(File(to, "late.ipynb").exists())
  }

  private fun notebook(name: String, body: String = "{}") {
    from.mkdirs()
    File(from, "$name.ipynb").writeText(body)
  }

  private fun migrate() = NotebookWorkspaceMigration(
    legacyDir = from,
    workspaceDir = to,
    stampFile = File(temp.root, "spaces/base/.jupydroid/notebooks-migrated"),
  ).runOnce()
}
