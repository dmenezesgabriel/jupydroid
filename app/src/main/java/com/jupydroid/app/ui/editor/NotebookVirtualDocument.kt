package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.port.LanguagePosition

/**
 * One `file://` document standing for the notebook's code cells under the language server -
 * `docs/PLAN.md` P5d-1, the reference's `VirtualDocument`
 * (`reference/research_repos/jupyterlab/packages/lsp/src/virtual/document.ts`) cut to what a
 * Python-only notebook needs: no foreign-language extraction, no editor maps - the cells'
 * sources joined whole, and the (cell, line) <-> virtual line map the answer positions and
 * `publishDiagnostics` ranges both travel.
 *
 * A cell is a fragment, not a module: `import os` in one cell must satisfy `os.path` in the
 * next or every ask is a `NameError`-shaped false positive, which is why the cells are
 * concatenated at all. Two blank lines sit between them, the reference's
 * `blankLinesBetweenCells`, so a line of one cell never runs into a line of the next.
 *
 * ```
 * val document = NotebookVirtualDocument(notebookId, cells)
 * languageServer.changeDocument(BASE_SPACE, document.uri, document.text)
 * document.positionOf(cellId, cursorOffset)
 * ```
 */
class NotebookVirtualDocument(notebookId: String, cells: List<Cell>) {
  /** A virtual line that belongs to a cell: the cell's id and the zero-based line inside it. */
  data class CellLine(val cellId: String, val line: Int)

  /** One code cell's claim on the document: where its first line sits and how many it owns. */
  private data class Block(val cellId: String, val source: String, val virtualStart: Int, val lines: Int)

  /** The document `pylsp` sees - `python` is the language id the adapter's auto-open carries. */
  val uri: String = "file:///jupyterdroid/notebooks/$notebookId.py"

  /** The whole text a `didOpen`/`didChange` sync sends. */
  val text: String

  private val blocks: List<Block>

  init {
    var nextVirtualLine = 0
    val codeCells = cells.filter { it.kind == CellKind.CODE }
    blocks =
      codeCells.map { cell ->
        Block(cell.id, cell.source, nextVirtualLine, cell.source.lineCount()).also {
          nextVirtualLine += it.lines + BLANK_LINES_BETWEEN_CELLS
        }
      }
    text = codeCells.joinToString(SEPARATOR) { it.source + "\n" }
  }

  /**
   * The caret in [cellId] at [sourceOffset] as LSP speaks it - the cell's own line/character
   * shifted by the virtual lines the cells before it own. Null when the cell names no code
   * cell, which a markdown cell's ask turns into the kernel's turn.
   */
  fun positionOf(cellId: String, sourceOffset: Int): LanguagePosition? {
    val block = blocks.firstOrNull { it.cellId == cellId } ?: return null
    val before = block.source.take(sourceOffset.coerceIn(0, block.source.length))
    return LanguagePosition(
      line = block.virtualStart + before.count { it == '\n' },
      character = sourceOffset - (before.lastIndexOf('\n') + 1),
    )
  }

  /**
   * The [position] the server reports in virtual coordinates as an offset inside [cellId]'s
   * own source - what a `textEdit` range needs before it can be spliced into the cell's field.
   * Null when the position lands on another cell or on a separator, a range the popup must
   * never apply.
   */
  fun cellOffsetOf(cellId: String, position: LanguagePosition): Int? {
    val block = blocks.firstOrNull { it.cellId == cellId } ?: return null
    val line = position.line - block.virtualStart
    return if (line in 0 until block.lines) cellOffset(block, line, position.character) else null
  }

  /** The code-unit offset `line`/`character` sits at inside [block]'s source, clamped at the end. */
  private fun cellOffset(block: Block, line: Int, character: Int): Int {
    var offset = 0
    repeat(line) { offset = block.source.indexOf('\n', offset) + 1 }
    return (offset + character).coerceAtMost(block.source.length)
  }

  /** Whose line [virtualLine] is - null on the separators and past the document's end. */
  fun cellLineAt(virtualLine: Int): CellLine? {
    val block = blocks.firstOrNull { virtualLine in it.virtualStart until it.virtualStart + it.lines }
    return block?.let { CellLine(it.cellId, virtualLine - it.virtualStart) }
  }

  private fun String.lineCount(): Int = count { it == '\n' } + 1

  private companion object {
    /** `blankLinesBetweenCells` in the reference - the gap that keeps two cells' lines apart. */
    const val BLANK_LINES_BETWEEN_CELLS = 2

    /** One block's trailing newline, plus the blank lines joining it to the next block. */
    const val SEPARATOR = "\n\n"
  }
}
