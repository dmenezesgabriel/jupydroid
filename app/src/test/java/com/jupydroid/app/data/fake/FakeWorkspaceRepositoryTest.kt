package com.jupydroid.app.data.fake

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake holds a fixed listing, so it does not run `WorkspaceRepositoryContract` - it creates,
 * renames and deletes nothing. What it *does* do is hold files, and an export written here has to
 * come back byte for byte.
 */
class FakeWorkspaceRepositoryTest {
  @Test
  fun holdsBytesUnchanged() = runTest {
    val repository = FakeWorkspaceRepository()

    repository.writeBytes("report.zip", ZIP_HEADER)

    assertArrayEquals(ZIP_HEADER, repository.readBytes("report.zip"))
  }

  /** One store, so a path written as text is readable as bytes and the other way round. */
  @Test
  fun readsBackAsBytesWhatWasWrittenAsText() = runTest {
    val repository = FakeWorkspaceRepository()

    repository.writeText("notes.txt", "hello")

    assertArrayEquals("hello".toByteArray(), repository.readBytes("notes.txt"))
  }

  @Test
  fun readsBackAsTextWhatWasWrittenAsBytes() = runTest {
    val repository = FakeWorkspaceRepository()

    repository.writeBytes("notes.txt", "hello".toByteArray())

    assertEquals("hello", repository.readText("notes.txt"))
  }

  @Test
  fun seedsTextGivenAtConstruction() = runTest {
    val repository = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "seeded"))

    assertEquals("seeded", repository.readText("notes.txt"))
  }

  @Test
  fun refusesAPathItHoldsNothingFor() = runTest {
    val missing = runCatching { FakeWorkspaceRepository().readBytes("gone.zip") }

    assertTrue("expected a refusal naming the path, got $missing", missing.isFailure)
    assertTrue(missing.exceptionOrNull()?.message.orEmpty().contains("gone.zip"))
  }

  private companion object {
    val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)
  }
}
