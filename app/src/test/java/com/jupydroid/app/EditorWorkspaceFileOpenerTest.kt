@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app

import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.domain.port.WorkspaceFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Opening a file a panel names: written first when it is missing, then opened as any file is. */
class EditorWorkspaceFileOpenerTest {
  /** A workspace that holds only [present], and records each act on it in [acts], in order. */
  private class SeedingWorkspaceFiles(
    private val present: Set<String> = emptySet(),
    private val failure: IOException? = null,
  ) : WorkspaceFiles by FakeWorkspaceFiles() {
    val acts = mutableListOf<String>()

    override suspend fun createIfAbsent(path: String, text: String): Boolean {
      failure?.let { throw it }
      if (path in present) return false
      acts += "wrote $path: $text"
      return true
    }
  }

  @Test
  fun aMissingFileIsWrittenBeforeItIsOpened() = runTest {
    val files = SeedingWorkspaceFiles()
    val opener = EditorWorkspaceFileOpener(files, this) { files.acts += "opened $it" }

    opener.open(".jupyter/mcp_settings.json", "{}")
    advanceUntilIdle()

    assertEquals(listOf("wrote .jupyter/mcp_settings.json: {}", "opened .jupyter/mcp_settings.json"), files.acts)
  }

  @Test
  fun aFileAlreadyThereIsOpenedAsItIs() = runTest {
    val files = SeedingWorkspaceFiles(present = setOf("notes.json"))
    val opener = EditorWorkspaceFileOpener(files, this) { files.acts += "opened $it" }

    opener.open("notes.json", "{}")
    advanceUntilIdle()

    assertEquals(listOf("opened notes.json"), files.acts)
  }

  /** The editor says what it cannot read, which is more than a press that does nothing would say. */
  @Test
  fun aFileThatCouldNotBeWrittenIsStillOpened() = runTest {
    val files = SeedingWorkspaceFiles(failure = IOException("No space left on device"))
    val opener = EditorWorkspaceFileOpener(files, this) { files.acts += "opened $it" }

    opener.open("notes.json", "{}")
    advanceUntilIdle()

    assertEquals(listOf("opened notes.json"), files.acts)
  }
}
