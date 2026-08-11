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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskNotebookRepositoryTest {
  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * [com.jupydroid.app.domain.model.PreservedJson] is by design unequal across a round trip: a
   * notebook built in memory has none and one read from a file carries the file it came from.
   * Dropping it leaves the modelled fields, which is what these tests are about - the blob
   * itself is `NotebookRoundTripTest`'s subject.
   */
  private fun Notebook.withoutPreserved(): Notebook = copy(
    preserved = JsonObject(emptyMap()),
    cells = cells.map { it.copy(preserved = JsonObject(emptyMap())) },
  )

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
    assertEquals(created, repository.readNotebook(created.id).withoutPreserved())
  }

  @Test
  fun saveAndRead_roundTripsCodeAndMarkdownCells() = runTest {
    val repository = repository()
    val notebook =
      Notebook(
        // Title and id are the same string, and since X1b that is the invariant rather than a
        // coincidence: the name *is* the file, so a round trip through disk can only return the
        // name on disk. JupyterLab has the same shape - a path, with the name derived from it.
        id = "nb-1",
        title = "nb-1",
        kernelId = "python3",
        cells =
          listOf(
            Cell(id = "c1", kind = CellKind.CODE, source = "print(1)"),
            Cell(id = "c2", kind = CellKind.MARKDOWN, source = "# Title"),
          ),
      )

    repository.saveNotebook(notebook)

    assertEquals(notebook, repository.readNotebook("nb-1").withoutPreserved())
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

    val renamed = repository.renameNotebook(created.id, "Renamed")

    assertEquals("Renamed", repository.readNotebook(renamed).title)
  }

  @Test
  fun deleteNotebook_removesFileAndSummary() = runTest {
    val repository = repository()
    val created = repository.createNotebook(title = "Untitled", kernelId = "python3")

    repository.deleteNotebook(created.id)

    assertFalse(notebookFile(created.id).exists())
    assertTrue(repository.observeNotebooks().first().isEmpty())
  }

  /**
   * The order is the files' own mtimes, the way Jupyter reads `last_modified`, so this sets
   * those - there is no longer a field in the JSON to set. Read back through a second
   * repository because summaries are scanned in `init` and refreshed only after a mutation.
   */
  @Test
  fun observeNotebooks_listsNewestFirst() = runTest {
    val repository = repository()
    repository.saveNotebook(notebook(id = "older"))
    repository.saveNotebook(notebook(id = "newer"))
    notebookFile("older").setLastModified(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli())
    notebookFile("newer").setLastModified(Instant.parse("2026-01-02T00:00:00Z").toEpochMilli())

    val summaries = DiskNotebookRepository(tempFolder.root).observeNotebooks().first()

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

  /**
   * Regression: nbformat's source lines already end in newlines, and joining them with
   * another one put a blank line between every line of every notebook Jupyter wrote. It was
   * in every screenshot from Step 3 onwards before anyone read one closely.
   */
  @Test
  fun readNotebook_doesNotDoubleTheNewlinesInAJupyterWrittenSource() = runTest {
    val repository = repository()
    notebookFile("jupyter").writeText(
      """
      {"cells":[{"cell_type":"code","source":["import sys\n","print(sys.version)\n","x = 1"]}],
       "metadata":{},"nbformat":4,"nbformat_minor":5}
      """.trimIndent(),
    )

    val source = repository.readNotebook("jupyter").cells.single().source

    assertEquals("import sys\nprint(sys.version)\nx = 1", source)
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

  /**
   * A6: `updated_at` used to live in `metadata.jupydroid` and nothing bumped it on save, so an
   * edited notebook kept the time it was created. Jupyter stores no such field - `last_modified`
   * is the file's mtime - so neither do we.
   *
   * Since X1b the whole `jupydroid` namespace is gone: the title was the last thing in it, and the
   * title is now the file's name. A `.ipynb` this app writes is one any other tool would write.
   */
  @Test
  fun saveNotebook_writesNoMetadataOfItsOwn() = runTest {
    val repository = repository()

    repository.saveNotebook(notebook(id = "nb-1"))

    val metadata = Json.parseToJsonElement(notebookFile("nb-1").readText())
      .jsonObject.getValue("metadata").jsonObject
    assertEquals(false, metadata.containsKey("jupydroid"))
  }

  @Test
  fun aFileStillCarryingTheOldTimestampIsListedByItsMtime() = runTest {
    notebookFile("legacy").writeText(LEGACY_WITH_UPDATED_AT)
    notebookFile("legacy").setLastModified(Instant.parse("2026-03-04T05:06:07Z").toEpochMilli())

    val summary = repository().observeNotebooks().first().single()

    assertEquals(Instant.parse("2026-03-04T05:06:07Z"), summary.updatedAt)
    // Its file is `legacy.ipynb`, so that is its name: the `metadata.jupydroid.title` this fixture
    // still carries is no longer read. Files like it are renamed once by NotebookWorkspaceMigration
    // before they are ever shown, so nobody sees a notebook lose its name.
    assertEquals("legacy", summary.title)
  }

  private fun notebook(id: String): Notebook =
    Notebook(id = id, title = id, kernelId = "python3", cells = emptyList())

  companion object {
    /** As this app wrote them until 2026-08-07: the stored timestamp is stale by construction. */
    private val LEGACY_WITH_UPDATED_AT =
      """
      {
        "cells": [],
        "metadata": {
          "kernelspec": {"name": "python3", "display_name": "Python 3"},
          "jupydroid": {"title": "Legacy", "updated_at": "2020-01-01T00:00:00Z"}
        },
        "nbformat": 4,
        "nbformat_minor": 5
      }
      """.trimIndent()

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
            "source": ["print(\n", "1)"]
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
