package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.ErrorLogCaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * A start archives the server log the run before it left, so the restart after a crash does not
 * overwrite the crash's own account of itself, and prunes the archive under the error log's caps
 * (ADR-0167). Real files under a temporary Space: what is asserted is what a person would `ls`.
 */
class ServerLogRotationTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val caps = ErrorLogCaps(maxBytes = 1_000, maxAgeDays = 7)

  /** 2026-11-21T14:30:00.123Z - a fixed "now" so archive names and age are exact. */
  private val nowMillis = Instant.parse("2026-11-21T14:30:00.123Z").toEpochMilli()

  @Test
  fun rotateServerLog_movesTheLiveLogIntoTheArchiveUnderItsStamp() {
    val dirs = spaceWithLiveLog("the run that crashed\n")

    rotateServerLog(dirs, caps, nowMillis)

    val archives = dirs.serverLogs.listFiles().orEmpty().toList()
    assertEquals(1, archives.size)
    assertEquals("jupyter_server-20261121-143000-123.log", archives[0].name)
    assertEquals("the run that crashed\n", archives[0].readText())
    assertFalse("the live path is the next run's alone", jupyterServerLogFile(dirs.runtime).exists())
  }

  @Test
  fun rotateServerLog_archivesNothingWhereTheRunLeftNoLogOrAnEmptyOne() {
    val dirs = SpaceDirs(spaceDir()).apply { ensure() }

    rotateServerLog(dirs, caps, nowMillis)
    jupyterServerLogFile(dirs.runtime).writeText("")
    rotateServerLog(dirs, caps, nowMillis)

    assertFalse("no archive for an absent or empty log", dirs.serverLogs.exists())
  }

  @Test
  fun rotateServerLog_prunesArchivesOlderThanTheAgeCap() {
    val dirs = SpaceDirs(spaceDir()).apply {
      serverLogs.mkdirs()
      File(serverLogs, "jupyter_server-20261101-000000-000.log").writeText("eight days old\n")
      File(serverLogs, "jupyter_server-20261120-000000-000.log").writeText("yesterday\n")
    }

    rotateServerLog(dirs, caps, nowMillis)

    val kept = dirs.serverLogs.listFiles().orEmpty().map(File::getName)
    assertEquals(listOf("jupyter_server-20261120-000000-000.log"), kept)
  }

  @Test
  fun rotateServerLog_dropsTheOldestArchivesUntilTheTotalFitsAndNeverTheNewest() {
    val dirs = SpaceDirs(spaceDir()).apply {
      serverLogs.mkdirs()
      // 400 + 400 + 1,200 bytes against a 1,000-byte cap: both old archives go, and the newest
      // stays whole even over the cap - it is the most recent postmortem.
      File(serverLogs, "jupyter_server-20261119-000000-000.log").writeText("a".repeat(400))
      File(serverLogs, "jupyter_server-20261120-000000-000.log").writeText("b".repeat(400))
      File(serverLogs, "jupyter_server-20261121-000000-000.log").writeText("c".repeat(1_200))
    }

    rotateServerLog(dirs, caps, nowMillis)

    val kept = dirs.serverLogs.listFiles().orEmpty().map(File::getName)
    assertEquals(listOf("jupyter_server-20261121-000000-000.log"), kept)
  }

  @Test
  fun rotateServerLog_leavesAFileThatNamesNoRunAlone() {
    val dirs = SpaceDirs(spaceDir()).apply {
      serverLogs.mkdirs()
      File(serverLogs, "README.txt").writeText("not ours\n")
      File(serverLogs, "jupyter_server-2026-13-45.log").writeText("names no stamp\n")
    }

    rotateServerLog(dirs, caps, nowMillis)

    val kept = dirs.serverLogs.listFiles().orEmpty().map(File::getName).sorted()
    assertEquals(listOf("README.txt", "jupyter_server-2026-13-45.log"), kept)
  }

  @Test
  fun rotateServerLog_givesASameMillisecondCollisionItsOwnName() {
    val dirs = spaceWithLiveLog("second run\n")
    rotateServerLog(dirs, caps, nowMillis)

    jupyterServerLogFile(dirs.runtime).writeText("third run\n")
    rotateServerLog(dirs, caps, nowMillis)

    val archives = dirs.serverLogs.listFiles().orEmpty().associate { it.name to it.readText() }
    assertEquals(
      mapOf(
        "jupyter_server-20261121-143000-123.log" to "second run\n",
        "jupyter_server-20261121-143000-123-2.log" to "third run\n",
      ),
      archives,
    )
  }

  private fun spaceWithLiveLog(content: String): SpaceDirs {
    val dirs = SpaceDirs(spaceDir()).apply { ensure() }
    jupyterServerLogFile(dirs.runtime).writeText(content)
    return dirs
  }

  private fun spaceDir(): File = tempFolder.newFolder("space-1").apply {
    File(this, "config.json").writeText("{}")
  }
}
