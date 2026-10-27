package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.NotebookCellType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two commands jupyter-ai-tools@4d1c823 `toolkits/notebook.py` `insert_cell` sends when the server
 * has no RTC, answered over the open notebook as jupyterlab-ai-commands@2743d32
 * `src/notebook-commands.ts` answers them over JupyterLab's.
 */
class NotebookLabCommandsTest {
  private val notebook = InMemoryNotebookCells("untitled.ipynb", listOf("a" to "x = 1", "b" to "y = 2"), "b")
  private val commands = NotebookLabCommands(notebook)

  @Test
  fun notebookInfoListsTheCellsInOrderAndTheActiveOne() {
    val info = done(commands.answer(GET_NOTEBOOK_INFO, path("untitled.ipynb")))

    assertEquals("untitled.ipynb", info.text("notebookPath"))
    assertEquals("untitled.ipynb", info.text("notebookName"))
    assertEquals(2, info.getValue("cellCount").jsonPrimitive.int)
    assertEquals("b", info.text("activeCellId"))
    assertEquals("code", info.text("activeCellType"))
    val cells = info.getValue("cells").jsonArray.map { it.jsonObject.text("cellId") to it.jsonObject.text("cellType") }
    assertEquals(listOf("a" to "code", "b" to "code"), cells)
  }

  /** `findWidget` matches a path after `PathExt.normalize` (jupyterlab@7f0e690 `docmanager/src/manager.ts`). */
  @Test
  fun notebookInfoFindsTheOpenNotebookByAPathThatNormalizesToIt() {
    val info = done(commands.answer(GET_NOTEBOOK_INFO, path("./untitled.ipynb")))

    assertEquals("untitled.ipynb", info.text("notebookPath"))
  }

  @Test
  fun notebookInfoWithNoPathIsTheOpenNotebooks() {
    val info = done(commands.answer(GET_NOTEBOOK_INFO, JsonObject(emptyMap())))

    assertEquals("untitled.ipynb", info.text("notebookPath"))
  }

  @Test
  fun aNotebookThatIsNotTheOpenOneIsRefusedByItsPath() {
    val failed = commands.answer(GET_NOTEBOOK_INFO, path("other.ipynb"))

    assertEquals(
      LabCommandOutcome.Failed("other.ipynb is not the open notebook (untitled.ipynb is)"),
      failed,
    )
  }

  @Test
  fun anyCommandIsRefusedWithNoNotebookOpen() {
    val failed = NotebookLabCommands(InMemoryNotebookCells(path = null)).answer(GET_NOTEBOOK_INFO, path("a.ipynb"))

    assertEquals(LabCommandOutcome.Failed("a.ipynb is not the open notebook (none is open)"), failed)
  }

  /** What `insert_cell` at index 0 sends: the first cell, `above`. */
  @Test
  fun aCellAddedAboveTheFirstBecomesTheFirstAndActive() {
    val added = done(commands.answer(ADD_CELL, addCell(reference = "a", position = "above")))

    assertEquals(listOf("print(42)", "x = 1", "y = 2"), notebook.sources)
    assertEquals(notebook.activeCellId, added.text("cellId"))
    assertEquals("code cell added successfully", added.text("message"))
    assertEquals("print(42)", added.text("content"))
    assertEquals("code", added.text("cellType"))
    assertEquals("above", added.text("position"))
  }

  /** What `insert_cell` past the last index sends: the last cell, `below`. */
  @Test
  fun aCellAddedBelowTheLastBecomesTheLast() {
    done(commands.answer(ADD_CELL, addCell(reference = "b", position = "below")))

    assertEquals(listOf("x = 1", "y = 2", "print(42)"), notebook.sources)
  }

  /** `insert_cell` into a notebook with no cells sends no reference cell, and `add-cell` appends. */
  @Test
  fun aCellAddedToANotebookWithNoCellsIsItsOnlyCell() {
    val empty = InMemoryNotebookCells("untitled.ipynb")

    done(NotebookLabCommands(empty).answer(ADD_CELL, addCell(reference = null, position = "below")))

    assertEquals(listOf("print(42)"), empty.sources)
  }

  /** `getCellTarget`: no reference cell is the active one. */
  @Test
  fun aCellAddedWithNoReferenceGoesBesideTheActiveCell() {
    done(commands.answer(ADD_CELL, addCell(reference = null, position = "above")))

    assertEquals(listOf("x = 1", "print(42)", "y = 2"), notebook.sources)
  }

  @Test
  fun aCellAddedOfAMarkdownTypeIsMarkdown() {
    done(commands.answer(ADD_CELL, addCell(reference = "a", position = "above", type = "markdown")))

    assertEquals(NotebookCellType.MARKDOWN, notebook.types.first())
  }

  @Test
  fun anUnknownReferenceCellIsRefusedByItsId() {
    val failed = commands.answer(ADD_CELL, addCell(reference = "gone", position = "above"))

    assertEquals(LabCommandOutcome.Failed("Cell with ID 'gone' not found in notebook"), failed)
    assertEquals(0, notebook.inserts)
  }

  @Test
  fun anUnknownCellTypeIsRefusedWithTheTypesThereAre() {
    val failed = commands.answer(ADD_CELL, addCell(reference = "a", position = "above", type = "sql"))

    assertEquals(LabCommandOutcome.Failed("Invalid cell type: 'sql'. Expected one of: code, markdown, raw"), failed)
  }

  @Test
  fun anUnknownPositionIsRefusedWithThePositionsThereAre() {
    val failed = commands.answer(ADD_CELL, addCell(reference = "a", position = "inside"))

    assertEquals(LabCommandOutcome.Failed("Invalid cell position: 'inside'. Expected one of: above, below"), failed)
  }

  @Test
  fun aCellAddedToANotebookThatIsNotOpenIsRefusedAndChangesNothing() {
    val args = JsonObject(addCell(reference = "a", position = "above") + ("notebookPath" to JsonPrimitive("b.ipynb")))

    val failed = commands.answer(ADD_CELL, args)

    assertEquals(LabCommandOutcome.Failed("b.ipynb is not the open notebook (untitled.ipynb is)"), failed)
    assertEquals(0, notebook.inserts)
  }

  @Test
  fun aCommandThisAppDoesNotAnswerIsRefusedByItsName() {
    val failed = commands.answer("jupyterlab-ai-commands:run-cell", path("untitled.ipynb"))

    assertEquals(LabCommandOutcome.Failed("jupyterlab-ai-commands:run-cell is not a command this app answers"), failed)
  }

  private fun done(outcome: LabCommandOutcome): JsonObject {
    check(outcome is LabCommandOutcome.Done) { "expected Done, got $outcome" }
    return outcome.result
  }

  private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

  private fun path(notebookPath: String) = buildJsonObject { put("notebookPath", JsonPrimitive(notebookPath)) }

  private fun addCell(reference: String?, position: String, type: String = "code") = buildJsonObject {
    put("notebookPath", JsonPrimitive("untitled.ipynb"))
    put("referenceCellId", reference?.let(::JsonPrimitive) ?: JsonNull)
    put("content", JsonPrimitive("print(42)"))
    put("cellType", JsonPrimitive(type))
    put("position", JsonPrimitive(position))
  }

  private companion object {
    const val GET_NOTEBOOK_INFO = "jupyterlab-ai-commands:get-notebook-info"
    const val ADD_CELL = "jupyterlab-ai-commands:add-cell"
  }
}
