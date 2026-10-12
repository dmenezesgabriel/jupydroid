package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [WorkspaceRepository] owes a text file somebody else may write: the half of the port
 * the fake can be held to, since it holds files but creates, renames and deletes nothing.
 *
 * The same four promises `NotebookRepositoryContract` makes for a notebook's save. "Someone else"
 * is a save with no stamp - the port's own unconditional write, which is what a shell's
 * `echo changed > notes.txt` is to this repository. Every text differs in length from the one
 * before it, so no filesystem's clock can make two stamps equal.
 */
abstract class WorkspaceTextSaveContract {
  protected abstract fun repository(): WorkspaceRepository

  /** The editor's save must not replace what was written under it, and what they wrote stays. */
  @Test
  fun aSaveOverATextFileWrittenSinceItWasReadIsRefused() = runTest {
    val repository = repository()
    val opened = repository.opened()
    repository.writeText(NOTES, "theirs, written since")

    val refusal = runCatching { repository.writeText(NOTES, "ours", opened.stamp) }

    assertTrue("expected a refusal, got $refusal", refusal.exceptionOrNull() is FileChangedOnDiskException)
    assertEquals("theirs, written since", repository.readText(NOTES))
  }

  /** Each save answers with the stamp the next one expects, so an editor's own writes never refuse it. */
  @Test
  fun aTextSaveHandedTheStampOfTheLastSaveGoesThrough() = runTest {
    val repository = repository()
    val opened = repository.opened()

    val first = repository.writeText(NOTES, "one edit", opened.stamp)
    repository.writeText(NOTES, "and then another", first)

    assertEquals("and then another", repository.readText(NOTES))
  }

  /** A file that already holds what the save would write has not changed in any way that matters. */
  @Test
  fun aTextSaveOfWhatIsAlreadyThereIsNotAConflict() = runTest {
    val repository = repository()
    val opened = repository.opened()
    repository.writeText(NOTES, "the same either way")

    repository.writeText(NOTES, "the same either way", opened.stamp)

    assertEquals("the same either way", repository.readText(NOTES))
  }

  /** The stamp of a read is the stamp of the save before it, so reopening owes no conflict. */
  @Test
  fun aTextReadAnswersTheStampTheLastSaveLeft() = runTest {
    val repository = repository()
    val opened = repository.opened()

    val saved = repository.writeText(NOTES, "one edit", opened.stamp)

    assertEquals(saved, repository.readStampedText(NOTES).stamp)
    assertEquals("one edit", repository.readStampedText(NOTES).text)
  }

  private suspend fun WorkspaceRepository.opened() = run {
    writeText(NOTES, "seeded")
    readStampedText(NOTES)
  }

  private companion object {
    const val NOTES = "notes.txt"
  }
}
