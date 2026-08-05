package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.model.Notebook
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskNotebookRepositoryTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private fun repository(): DiskNotebookRepository = DiskNotebookRepository(tempFolder.root)

  private fun notebookFile(id: String): File = File(tempFolder.root, "$id.ipynb")

  @Test
  fun createNotebook_writesNbformatFileThatRoundTrips() = runTest {
    val repository = repository()

    val created = repository.createNotebook(title = "Untitled", kernelId = "python3")

    assertTrue(notebookFile(created.id).exists())
    val decoded = Json.decodeFromString<NotebookFile>(notebookFile(created.id).readText())
    assertEquals(4, decoded.nbformat)
    assertEquals(5, decoded.nbformatMinor)
    assertEquals("python3", decoded.metadata.kernelspec?.name)
    assertTrue(decoded.cells.isEmpty())
    assertEquals(created, repository.readNotebook(created.id))
  }

  @Test
  fun saveAndRead_roundTripsCodeAndMarkdownCells() = runTest {
    val repository = repository()
    val notebook =
      Notebook(
        id = "nb-1",
        title = "Data",
        kernelId = "python3",
        cells =
          listOf(
            Cell(id = "c1", kind = CellKind.CODE, source = "print(1)"),
            Cell(id = "c2", kind = CellKind.MARKDOWN, source = "# Title"),
          ),
        updatedAt = Instant.parse("2026-01-02T03:04:05Z"),
      )

    repository.saveNotebook(notebook)

    assertEquals(notebook, repository.readNotebook("nb-1"))
    val decoded = Json.decodeFromString<NotebookFile>(notebookFile("nb-1").readText())
    assertEquals(listOf("code", "markdown"), decoded.cells.map { it.cellType })
    assertEquals(listOf("print(1)", "# Title"), decoded.cells.map { it.source })
  }

  /**
   * Regression: toDomain/toFile dropped outputs and execution_count, so opening a notebook
   * another tool had run and saving it - a rename was enough - silently destroyed its
   * results. Someone else's work, lost without a message.
   */
  @Test
  fun saveAndRead_keepsCellOutputsAndExecutionCounts() = runTest {
    val repository = repository()
    val notebook = repository.createNotebook("With Outputs", "python3")
    val ran = notebook.copy(
      cells = listOf(
        Cell(
          id = "c1",
          kind = CellKind.CODE,
          source = "print('hi')",
          outputs = listOf(CellOutput.Stream(StreamName.STDOUT, "hi\n")),
          executionCount = 4,
        ),
      ),
    )

    repository.saveNotebook(ran)
    val reread = repository.readNotebook(notebook.id)

    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "hi\n")), reread.cells.single().outputs)
    assertEquals(4, reread.cells.single().executionCount)
  }

  @Test
  fun renameNotebook_updatesStoredTitle() = runTest {
    val repository = repository()
    val created = repository.createNotebook(title = "Untitled", kernelId = "python3")

    repository.renameNotebook(created.id, "Renamed")

    assertEquals("Renamed", repository.readNotebook(created.id).title)
  }

  @Test
  fun deleteNotebook_removesFileAndSummary() = runTest {
    val repository = repository()
    val created = repository.createNotebook(title = "Untitled", kernelId = "python3")

    repository.deleteNotebook(created.id)

    assertFalse(notebookFile(created.id).exists())
    assertTrue(repository.observeNotebooks().first().isEmpty())
  }

  @Test
  fun observeNotebooks_listsNewestFirst() = runTest {
    val repository = repository()
    repository.saveNotebook(
      notebook(id = "older", updatedAt = Instant.parse("2026-01-01T00:00:00Z")),
    )
    repository.saveNotebook(
      notebook(id = "newer", updatedAt = Instant.parse("2026-01-02T00:00:00Z")),
    )

    val summaries = repository.observeNotebooks().first()

    assertEquals(listOf("newer", "older"), summaries.map { it.id })
  }

  @Test
  fun readNotebook_missingFile_throwsMeaningfulError() = runTest {
    val repository = repository()

    try {
      repository.readNotebook("ghost")
      fail("expected IllegalArgumentException")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message.orEmpty().contains("ghost"))
    }
  }

  @Test
  fun readNotebook_corruptFile_throwsMeaningfulError() = runTest {
    val repository = repository()
    notebookFile("broken").writeText("{not json")

    try {
      repository.readNotebook("broken")
      fail("expected IllegalArgumentException")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message.orEmpty().contains("broken"))
    }
  }

  @Test
  fun readForeignNotebook_withArraySource_andUnknownKeys() = runTest {
    val repository = repository()
    notebookFile("foreign").writeText(FOREIGN_NOTEBOOK_JSON)

    val notebook = repository.readNotebook("foreign")

    assertEquals("foreign", notebook.title)
    assertEquals("python3", notebook.kernelId)
    assertEquals(1, notebook.cells.size)
    assertEquals("print(\n1)", notebook.cells.single().source)
    assertEquals(CellKind.CODE, notebook.cells.single().kind)
  }

  @Test
  fun readNotebook_unknownCellType_throwsMeaningfulError() = runTest {
    val repository = repository()
    notebookFile("odd").writeText(UNKNOWN_CELL_TYPE_JSON)

    try {
      repository.readNotebook("odd")
      fail("expected IllegalArgumentException")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message.orEmpty().contains("raw"))
    }
  }

  private fun notebook(id: String, updatedAt: Instant): Notebook =
    Notebook(
      id = id,
      title = id,
      kernelId = "python3",
      cells = emptyList(),
      updatedAt = updatedAt,
    )

  companion object {
    // Jupyter writes source as an array of lines and files often carry unknown keys.
    private val FOREIGN_NOTEBOOK_JSON =
      """
      {
        "cells": [
          {
            "cell_type": "code",
            "execution_count": 1,
            "metadata": {"collapsed": false},
            "outputs": [{"output_type": "stream", "name": "stdout", "text": ["1\n"]}],
            "source": ["print(", "1)"]
          }
        ],
        "metadata": {
          "kernelspec": {"name": "python3", "display_name": "Python 3", "language": "python"},
          "language_info": {"name": "python"}
        },
        "nbformat": 4,
        "nbformat_minor": 5
      }
      """.trimIndent()

    private val UNKNOWN_CELL_TYPE_JSON =
      """
      {
        "cells": [{"cell_type": "raw", "source": "x"}],
        "metadata": {},
        "nbformat": 4,
        "nbformat_minor": 5
      }
      """.trimIndent()
  }
}
