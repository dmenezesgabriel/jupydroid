package com.jupydroid.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.InotifyWorkspaceWatcher
import com.jupydroid.app.domain.model.FileStamp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The watcher hears a write the way the device's kernel reports it: `CLOSE_WRITE` for a file
 * written in place, as a cell's `open(...).write` is, and `MOVED_TO` for one renamed over the name,
 * as this app's own save is (ADR-0089).
 *
 * Instrumented rather than JVM because `android.os.FileObserver` is the platform's inotify, and a
 * JVM test would be testing a stub of it.
 */
@RunWith(AndroidJUnit4::class)
class InotifyWorkspaceWatcherTest {
  @get:Rule val workspace = TemporaryFolder()

  @Test
  fun aWriteInPlaceIsHeardWithTheFilesStamp() = runBlocking {
    val heard = firstWriteHeardTo("reports/notes.txt") { File(it, "notes.txt").writeText("written in place") }

    assertEquals("written in place".length.toLong(), heard.size)
  }

  @Test
  fun aFileRenamedOverTheNameIsHeard() = runBlocking {
    val heard = firstWriteHeardTo("reports/notes.txt") { folder ->
      File(folder, ".~notes.txt").apply { writeText("renamed over") }.renameTo(File(folder, "notes.txt"))
    }

    assertEquals("renamed over".length.toLong(), heard.size)
  }

  /** A sibling's write comes first and is not reported, so the first stamp heard is the file's own. */
  @Test
  fun aWriteBesideTheFileIsNotHeard() = runBlocking {
    val heard = firstWriteHeardTo("reports/notes.txt") { folder ->
      File(folder, "other.txt").writeText("a sibling, much longer than the file")
      File(folder, "notes.txt").writeText("mine")
    }

    assertEquals("mine".length.toLong(), heard.size)
  }

  /**
   * Three documents open in one folder each hear their own file. inotify gives a folder one watch
   * descriptor however often it is added, so a second watch on it must not take the first's events.
   */
  @Test
  fun twoFilesWatchedInOneFolderAreEachHeard() = runBlocking {
    val folder = workspace.newFolder("reports")
    val watcher = InotifyWorkspaceWatcher(workspace.root)
    coroutineScope {
      val notes = async(start = CoroutineStart.UNDISPATCHED) { watcher.writesTo("reports/notes.txt").first() }
      val other = async(start = CoroutineStart.UNDISPATCHED) { watcher.writesTo("reports/other.txt").first() }
      delay(WATCH_ADDED_MILLIS)
      File(folder, "notes.txt").writeText("notes")
      File(folder, "other.txt").writeText("the other one")

      assertEquals("notes".length.toLong(), withTimeout(HEARD_WITHIN_MILLIS) { notes.await() }.size)
      assertEquals("the other one".length.toLong(), withTimeout(HEARD_WITHIN_MILLIS) { other.await() }.size)
    }
  }

  /** One document closing stops its own watch, not the folder's - the other is still heard. */
  @Test
  fun aFileStillWatchedIsHeardAfterItsNeighbourStops() = runBlocking {
    val folder = workspace.newFolder("reports")
    val watcher = InotifyWorkspaceWatcher(workspace.root)
    coroutineScope {
      val notes = async(start = CoroutineStart.UNDISPATCHED) { watcher.writesTo("reports/notes.txt").first() }
      val neighbour = launch(start = CoroutineStart.UNDISPATCHED) { watcher.writesTo("reports/other.txt").first() }
      delay(WATCH_ADDED_MILLIS)
      neighbour.cancelAndJoin()
      File(folder, "notes.txt").writeText("notes")

      assertEquals("notes".length.toLong(), withTimeout(HEARD_WITHIN_MILLIS) { notes.await() }.size)
    }
  }

  private suspend fun firstWriteHeardTo(path: String, write: (File) -> Unit): FileStamp {
    val folder = workspace.newFolder("reports")
    val watcher = InotifyWorkspaceWatcher(workspace.root)
    return coroutineScope {
      val heard = async(start = CoroutineStart.UNDISPATCHED) { watcher.writesTo(path).first() }
      // The watch is added on the collector's thread; a write before it lands is heard by no one.
      delay(WATCH_ADDED_MILLIS)
      write(folder)
      withTimeout(HEARD_WITHIN_MILLIS) { heard.await() }
    }
  }

  private companion object {
    const val WATCH_ADDED_MILLIS = 300L
    const val HEARD_WITHIN_MILLIS = 5_000L
  }
}
