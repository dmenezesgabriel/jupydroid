package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.port.FileChangedOnDiskException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StampedFileWriteTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private fun target(): File = File(tempFolder.root, "notes.txt")

  /** Moves the file's time without touching its bytes or its length - a rewrite the size cannot show. */
  private fun File.writtenLaterAs(text: String) {
    val before = lastModified()
    writeText(text)
    setLastModified(before + A_MINUTE)
  }

  /**
   * The same length on purpose: the kernel rewriting `x = 1` as `x = 2` changes no size, so the
   * time alone has to be enough to refuse.
   */
  @Test
  fun aFileRewrittenSinceItsStampIsRefusedAndLeftAsItWasRewritten() {
    target().writeText("ours 1")
    val read = target().stamp()
    target().writtenLaterAs("theirs")

    val refusal = assertThrows(FileChangedOnDiskException::class.java) {
      target().writeIfUnchanged(read, "ours 2".toByteArray(), named = "notes.txt")
    }

    assertEquals("theirs", target().readText())
    assertTrue("expected the message to name the file, got ${refusal.message}", "'notes.txt'" in refusal.message!!)
  }

  @Test
  fun aFileUnchangedSinceItsStampIsWritten() {
    target().writeText("before")

    target().writeIfUnchanged(target().stamp(), "after".toByteArray(), named = "notes.txt")

    assertEquals("after", target().readText())
  }

  /** `touch`, or a tool that rewrote the same bytes: nothing would be lost, so nothing is asked. */
  @Test
  fun aFileWhoseTimeMovedButAlreadyHoldsTheBytesIsWritten() {
    target().writeText("same")
    val read = target().stamp()
    target().writtenLaterAs("same")

    target().writeIfUnchanged(read, "same".toByteArray(), named = "notes.txt")

    assertEquals("same", target().readText())
  }

  /** A deleted file is written back, as a save always has: there is nothing on disk to lose. */
  @Test
  fun aFileThatIsGoneIsWrittenBack() {
    target().writeText("before")
    val read = target().stamp()
    target().delete()

    target().writeIfUnchanged(read, "after".toByteArray(), named = "notes.txt")

    assertEquals("after", target().readText())
  }

  @Test
  fun aWriteWithNoStampReplacesWhateverIsThere() {
    target().writeText("ours 1")
    target().writtenLaterAs("theirs")

    target().writeIfUnchanged(null, "ours 2".toByteArray(), named = "notes.txt")

    assertEquals("ours 2", target().readText())
  }

  @Test
  fun theStampAnsweredIsTheOneTheFileHasAfterTheWrite() {
    target().writeText("before")
    val read = target().stamp()

    val written = target().writeIfUnchanged(read, "after, and longer".toByteArray(), named = "notes.txt")

    assertEquals(target().stamp(), written)
    assertNotEquals(read, written)
  }

  private companion object {
    const val A_MINUTE = 60_000L
  }
}
