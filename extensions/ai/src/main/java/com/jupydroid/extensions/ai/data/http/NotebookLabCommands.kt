package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.NotebookCellSummary
import com.jupydroid.extension.api.NotebookCellType
import com.jupydroid.extension.api.NotebookCells
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What a command came to: the result a web client posts for it, or the error it posts instead. */
internal sealed interface LabCommandOutcome {
  data class Done(val result: JsonObject) : LabCommandOutcome

  data class Failed(val error: String) : LabCommandOutcome
}

/**
 * The JupyterLab commands jupyter-ai-tools@4d1c823 `toolkits/notebook.py` `insert_cell` sends when the
 * server has no RTC, answered over the open notebook with the arguments, results and errors of
 * jupyterlab-ai-commands@2743d32 `src/notebook-commands.ts`, so the agent reads what it would read from
 * JupyterLab.
 *
 * Two differences, both because the notebook is the editor's and not a JupyterLab widget: a path that is
 * not the open notebook is refused rather than opened, and `add-cell` puts its cell beside a lone empty
 * cell rather than in its place, as the chat's Insert does (ADR-0114). `isDirty` is always false, since
 * the editor saves each edit itself.
 *
 * ```
 * NotebookLabCommands(host.notebookCells).answer("jupyterlab-ai-commands:add-cell", args)
 * ```
 */
internal class NotebookLabCommands(private val notebook: NotebookCells) {
  /**
   * [name] run with [args]; one this app does not answer fails by its name, as an unknown command
   * does. The notebook's own failures — `insert`'s `IllegalArgumentException` included — are a
   * Failed result the agent reads, not an exception that kills the chat's flow.
   */
  @Suppress("TooGenericExceptionCaught")
  fun answer(name: String, args: JsonObject): LabCommandOutcome = try {
    when (name) {
      GET_NOTEBOOK_INFO -> LabCommandOutcome.Done(notebookInfo(args))
      ADD_CELL -> LabCommandOutcome.Done(addCell(args))
      else -> LabCommandOutcome.Failed("$name is not a command this app answers")
    }
  } catch (refusal: LabCommandRefusal) {
    LabCommandOutcome.Failed(refusal.message)
  } catch (failure: Exception) {
    if (failure is CancellationException) throw failure
    LabCommandOutcome.Failed("${failure::class.java.simpleName}: ${failure.message}")
  }

  private fun notebookInfo(args: JsonObject): JsonObject {
    val path = openNotebookAt(args)
    val cells = notebook.cells
    val active = cells.firstOrNull { it.id == notebook.activeCellId }
    return buildJsonObject {
      put("success", true)
      put("notebookName", path.substringAfterLast('/'))
      put("notebookPath", path)
      put("cellCount", cells.size)
      put("activeCellId", active?.id)
      put("activeCellType", active?.type?.wireName ?: "unknown")
      putJsonArray("cells") { cells.forEach { addJsonObject { putCell(it) } } }
      putJsonObject("notebookMetadata") {}
      put("isDirty", false)
    }
  }

  private fun addCell(args: JsonObject): JsonObject {
    val path = openNotebookAt(args)
    val type = cellTypeOf(args.text("cellType") ?: NotebookCellType.CODE.wireName)
    val position = args.text("position") ?: BELOW
    if (position != ABOVE && position != BELOW) {
      refuse("Invalid cell position: '$position'. Expected one of: $ABOVE, $BELOW")
    }
    val content = args.text("content").orEmpty()
    val id = notebook.insert(insertIndex(args.text("referenceCellId"), position), type, content)
      ?: refuse("$path is not the open notebook (none is open)")
    return buildJsonObject {
      put("success", true)
      put("message", "${type.wireName} cell added successfully")
      put("cellId", id)
      put("content", content)
      put("cellType", type.wireName)
      put("position", position)
    }
  }

  /** The open notebook's path, when [args] names it or names none: `findWidget`, or `currentWidget`. */
  private fun openNotebookAt(args: JsonObject): String {
    val open = notebook.path
    val asked = args.text("notebookPath")
      ?: return open ?: refuse("No active notebook and no notebook path provided")
    if (open != null && normalized(asked) == normalized(open)) return open
    refuse("$asked is not the open notebook (${open?.let { "$it is" } ?: "none is open"})")
  }

  /** `add-cell`'s index: the end of a notebook with no cells, else beside the reference cell. */
  private fun insertIndex(referenceCellId: String?, position: String): Int {
    val cells = notebook.cells
    if (cells.isEmpty()) return 0
    val reference = referenceIndex(cells, referenceCellId)
    return if (position == ABOVE) reference else reference + 1
  }

  /** `getCellTarget`: the cell [id] names, or the active cell when it names none. */
  private fun referenceIndex(cells: List<NotebookCellSummary>, id: String?): Int {
    val index = cells.indexOfFirst { it.id == (id ?: notebook.activeCellId) }
    if (index >= 0) return index
    refuse(id?.let { "Cell with ID '$it' not found in notebook" } ?: "No active cell or invalid active cell index")
  }

  private fun cellTypeOf(name: String): NotebookCellType = NotebookCellType.entries.firstOrNull { it.wireName == name }
    ?: refuse("Invalid cell type: '$name'. Expected one of: code, markdown, raw")

  private class LabCommandRefusal(override val message: String) : Exception(message)

  private fun refuse(message: String): Nothing = throw LabCommandRefusal(message)

  private companion object {
    const val GET_NOTEBOOK_INFO = "jupyterlab-ai-commands:get-notebook-info"
    const val ADD_CELL = "jupyterlab-ai-commands:add-cell"
    const val ABOVE = "above"
    const val BELOW = "below"
  }
}

/** nbformat's `cell_type`, which is what the commands carry. */
private val NotebookCellType.wireName: String get() = name.lowercase()

private fun JsonObjectBuilder.putCell(cell: NotebookCellSummary) {
  put("cellId", cell.id)
  put("cellType", cell.type.wireName)
}

/** [key]'s string; null when it is absent, JSON `null` or not a string. */
private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * [path] as JupyterLab's `PathExt.normalize` leaves it, `.` and `..` resolved and no leading slash
 * (jupyterlab@7f0e690 `packages/coreutils/src/path.ts`), so `./a.ipynb` is `a.ipynb`.
 */
private fun normalized(path: String): String = path.split('/').fold(ArrayDeque<String>()) { kept, segment ->
  when (segment) {
    "", "." -> Unit
    ".." -> kept.removeLastOrNull()
    else -> kept.addLast(segment)
  }
  kept
}.joinToString("/")
