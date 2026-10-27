package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.NotebookCellSummary
import com.jupydroid.extension.api.NotebookCellType
import com.jupydroid.extension.api.NotebookCells

/**
 * An open notebook kept in a list, which an insert really changes, so a test reads the cells the way the
 * editor would show them: in order, with the new one active.
 *
 * ```
 * val notebook = InMemoryNotebookCells("untitled.ipynb", listOf("a" to "x = 1"), activeCellId = "a")
 * notebook.insert(0, NotebookCellType.CODE, "print(42)")
 * notebook.sources // ["print(42)", "x = 1"]
 * ```
 *
 * @param path null for no notebook open, which leaves it with no cells to start from.
 */
internal class InMemoryNotebookCells(
  override val path: String?,
  cells: List<Pair<String, String>> = emptyList(),
  activeCellId: String? = null,
) : NotebookCells {
  private val kept = cells.map { (id, source) -> KeptCell(id, NotebookCellType.CODE, source) }.toMutableList()

  override var activeCellId: String? = activeCellId
    private set

  /** How many inserts were made, by any caller. */
  var inserts: Int = 0
    private set

  override val available: Boolean get() = path != null && activeCellId != null

  override val cells: List<NotebookCellSummary> get() = kept.map { NotebookCellSummary(it.id, it.type) }

  val sources: List<String> get() = kept.map { it.source }

  val types: List<NotebookCellType> get() = kept.map { it.type }

  override fun insert(index: Int, type: NotebookCellType, source: String): String? {
    if (path == null) return null
    require(index in 0..kept.size) { "cell index $index is outside 0..${kept.size}" }
    inserts++
    val id = "new-$inserts"
    kept.add(index, KeptCell(id, type, source))
    activeCellId = id
    return id
  }

  private class KeptCell(val id: String, val type: NotebookCellType, val source: String)
}
