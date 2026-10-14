package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeWorkspaceWatcher
import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.WorkspaceChange
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An open document follows its file: where this app moves it, whether it is deleted, and what
 * anything else writes to it.
 *
 * vscode@3f3380c `textFileEditorModelManager.ts` `onDidFilesChange` for the writes, and
 * `Context._onFileChanged` (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`) for the moves.
 */
class DocumentFileFollowTest {
  private val changes = MutableSharedFlow<WorkspaceChange>()
  private val watcher = FakeWorkspaceWatcher()
  private var path = NOTES
  private val written = mutableListOf<FileStamp>()
  private var gone = false

  @Test
  fun aWriteToTheFileIsHandedItsStamp() = runTest {
    followed()

    watcher.announceWrite(NOTES, FIRST)
    settle()

    assertEquals(listOf(FIRST), written)
  }

  /** A cell writing in chunks closes the file many times; vscode aggregates them for 75 ms. */
  @Test
  fun aBurstOfWritesIsFollowedOnceAtItsLastStamp() = runTest {
    followed()

    watcher.announceWrite(NOTES, FIRST)
    watcher.announceWrite(NOTES, SECOND)
    watcher.announceWrite(NOTES, THIRD)
    settle()

    assertEquals(listOf(THIRD), written)
  }

  @Test
  fun aWriteToAnotherFileIsNotFollowed() = runTest {
    followed()

    watcher.announceWrite("other.txt", FIRST)
    settle()

    assertTrue(written.isEmpty())
  }

  /** The watch moves with the file, so the old name's writes are someone else's. */
  @Test
  fun aRenameMovesTheWatchToTheNewName() = runTest {
    followed()

    changes.emit(WorkspaceChange.Renamed(from = NOTES, to = MOVED))
    settle()
    watcher.announceWrite(NOTES, FIRST)
    watcher.announceWrite(MOVED, SECOND)
    settle()

    assertEquals(MOVED, path)
    assertEquals(listOf(SECOND), written)
  }

  @Test
  fun aDeleteOfTheFileIsReported() = runTest {
    followed()

    changes.emit(WorkspaceChange.Deleted("other.txt"))
    settle()
    assertEquals(false, gone)
    changes.emit(WorkspaceChange.Deleted(NOTES))
    settle()

    assertTrue(gone)
  }

  /**
   * `advanceUntilIdle` stops once only `backgroundScope` work is left, which is all a follow is, so
   * time is moved past the settle by hand.
   */
  private fun TestScope.settle() {
    advanceTimeBy(PAST_THE_SETTLE_MILLIS)
    runCurrent()
  }

  private fun TestScope.followed() {
    DocumentFileFollow(
      changes = changes,
      watcher = watcher,
      path = { path },
      onMoved = { path = it },
      onGone = { gone = true },
      onWritten = { written += it },
    ).followIn(backgroundScope)
    settle()
  }

  private companion object {
    const val NOTES = "notes.txt"
    const val MOVED = "renamed.txt"
    const val PAST_THE_SETTLE_MILLIS = 1_000L
    val FIRST = FileStamp(modifiedNanos = 1, size = 1)
    val SECOND = FileStamp(modifiedNanos = 2, size = 2)
    val THIRD = FileStamp(modifiedNanos = 3, size = 3)
  }
}
