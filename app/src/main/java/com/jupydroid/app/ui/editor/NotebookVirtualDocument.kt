package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import java.net.URI

/**
 * One `file://` document standing for the notebook's code cells under the language server -
 * 1, the reference's `VirtualDocument`
 * (`jupyterlab@7f0e6906 packages/lsp/src/virtual/document.ts`) cut to what a
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

  /**
   * The document `pylsp` sees - `python` is the language id the adapter's auto-open carries.
   *
   * Encoded, because a `Location` a server sends back is built *from the filesystem path*,
   * which is always quoted - pylsp's `uris.urlunparse` `quote()`s it - so a title with a
   * space would otherwise compare `!=` on every same-document location and drop it as
   * off-document (the first `p5e5-definition` run's miss). `URI`'s empty authority is what
   * keeps the `file:///` triple-slash rather than `file:/`.
   */
  val uri: String = URI("file", "", "/jupyterdroid/notebooks/$notebookId.py", null).toASCIIString()

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

  /**
   * [position] clamped into [cellId]'s block rather than dropped when it lands outside it -
   * the `end` half of a `publishDiagnostics` range, whose span may run onto the separator or
   * into the next cell. Only the part the cell owns is underlined: the mark's line is already
   * the range's *start* (CodeMirror's lint-gutter convention), so a stray end clamps to the
   * cell's own last offset rather than underlining another cell's text.
   */
  fun cellOffsetOfClamped(cellId: String, position: LanguagePosition): Int? {
    val block = blocks.firstOrNull { it.cellId == cellId } ?: return null
    return when (val line = position.line - block.virtualStart) {
      in Int.MIN_VALUE until 0 -> 0
      in block.lines..Int.MAX_VALUE -> block.source.length
      else -> cellOffset(block, line, position.character)
    }
  }

  /** The code-unit offset `line`/`character` sits at inside [block]'s source, clamped at the line's end. */
  private fun cellOffset(block: Block, line: Int, character: Int): Int {
    var offset = 0
    repeat(line) { offset = block.source.indexOf('\n', offset) + 1 }
    val lineEnd = block.source.indexOf('\n', offset).let { if (it < 0) block.source.length else it }
    return (offset + character).coerceIn(offset, lineEnd)
  }

  /**
   * The cell's whole block as the `range` a `textDocument/rangeFormatting` asks for - `end`
   * at the last line's own length, **not** the LSP-canonical exclusive `(lines, 0)`. The
   * shipped `pylsp` answers this through autopep8, whose `pylsp_format_range` adds one to the
   * end line before calling `fix_code`: the canonical end would pull the first separator
   * blank line into its `line_range`, let its blank-line fixes rewrite it, and trip
   * [cellSourceAfterEdits]'s suffix guard into a no-op. Read off the rootfs's
   * `autopep8_format.py`, not assumed.
   */
  fun rangeOf(cellId: String): LanguageRange? {
    val block = blocks.firstOrNull { it.cellId == cellId } ?: return null
    return LanguageRange(
      LanguagePosition(block.virtualStart, 0),
      LanguagePosition(block.virtualStart + block.lines - 1, block.source.substringAfterLast('\n').length),
    )
  }

  /**
   * [cellId]'s source as the formatter's [edits] leave it, or null when applying them is not
   * safe - the answer [rangeOf]'s ask gets back. The shipped autopep8 plugin returns **one
   * whole-document `TextEdit`** even for a range ask, so "apply" here means: splice the edits
   * into this synced [text], verify everything *outside* the cell - the prefix before it and
   * the suffix from its last line's end (its trailing newline, the separators and every later
   * cell) - is byte-identical, then slice out the cell's new lines. Any touch past the block's
   * own edges is a refusal, because the alternative is rewriting a cell nobody asked about.
   */
  fun cellSourceAfterEdits(cellId: String, edits: List<LanguageEdit>): String? =
    blocks.firstOrNull { it.cellId == cellId }?.let { block ->
      cellSpan(block)?.let { span ->
        edits.takeIf { it.isNotEmpty() }
          ?.let { text.withEditsApplied(it) }
          ?.withCellSliceChecked(span)
      }
    }

  /**
   * Every code cell's source as [edits] leave it, keyed by cell id - the answer a
   * `textDocument/rename` `WorkspaceEdit` gets: pylsp returns one
   * whole-document `TextEdit` whose `newText` carries the new name at every site, so all
   * cells move together or not at all. Applying means splicing [edits] into this synced
   * [text], then [reslicedSources]'s split-and-verify: a count that no longer matches the
   * code cells, or a reconstruction that differs, is a refusal rather than a guess.
   */
  fun cellSourcesAfterEdits(edits: List<LanguageEdit>): Map<String, String>? {
    val newText = edits.takeIf { it.isNotEmpty() }?.let { text.withEditsApplied(it) } ?: return null
    return reslicedSources(newText)
  }

  /**
   * [newText] - this document as the edits left it - split on the separator run and **verified**:
   * the parts rebuild it byte-for-byte or the answer is a refusal rather than a guess at which
   * cell a shifted blank line belongs to. Cells hold the answer's own text as-is: the trailing
   * newline each separator consumed is not a cell's to keep.
   */
  private fun reslicedSources(newText: String): Map<String, String>? {
    val parts = newText.split(SEPARATOR_RUN).takeIf { it.size == blocks.size } ?: return null
    val sources = parts.dropLast(1) + parts.last().removeSuffix("\n")
    return sources.takeIf { it.joinToString(SEPARATOR) { source -> "$source\n" } == newText }
      ?.let { blocks.map(Block::cellId).zip(it).toMap() }
  }

  /** The cell's own [text] span - [TextSpan.start] at its first line, [TextSpan.end] after its last. */
  private fun cellSpan(block: Block): TextSpan? = text.virtualOffset(block.virtualStart, 0)?.let { start ->
    text.virtualOffset(block.virtualStart + block.lines - 1, block.source.substringAfterLast('\n').length)
      ?.let { TextSpan(start, it) }
  }

  /** [start] and [end] as `substring` bounds - [end] exclusive, at the last line's own end. */
  private data class TextSpan(val start: Int, val end: Int)

  /**
   * This document (the formatted one) minus everything [span] does not cover - or null when
   * the edits touched text outside it: the prefix before the cell and the suffix from its
   * last line's end (its trailing newline, the separators and every later cell) must be
   * byte-identical, because the alternative is rewriting a cell nobody asked about.
   */
  private fun String.withCellSliceChecked(span: TextSpan): String? {
    val prefix = text.take(span.start)
    val suffix = text.substring(span.end)
    return if (startsWith(prefix) && endsWith(suffix)) substring(span.start, length - suffix.length) else null
  }

  /** Whose line [virtualLine] is - null on the separators and past the document's end. */
  fun cellLineAt(virtualLine: Int): CellLine? {
    val block = blocks.firstOrNull { virtualLine in it.virtualStart until it.virtualStart + it.lines }
    return block?.let { CellLine(it.cellId, virtualLine - it.virtualStart) }
  }

  private companion object {
    /** `blankLinesBetweenCells` in the reference - the gap that keeps two cells' lines apart. */
    const val BLANK_LINES_BETWEEN_CELLS = 2

    /** One block's trailing newline, plus the blank lines joining it to the next block. */
    const val SEPARATOR = "\n\n"

    /** The run a cell's trailing newline and [SEPARATOR] form - what splits the parts. */
    val SEPARATOR_RUN = Regex("\\n{3,}")
  }
}

private fun String.lineCount(): Int = count { it == '\n' } + 1

/**
 * [edits] spliced into this document text, newest positions first so each pair is still true
 * of the text before it - or null when a range names a spot the document does not have. A
 * `null` here is what turns a malformed answer into a refusal rather than a misplaced splice.
 * File-level on `String` because it is pure text surgery: nothing it reads lives in the class.
 */
private fun String.withEditsApplied(edits: List<LanguageEdit>): String? {
  val located = edits.map { edit ->
    virtualOffset(edit.range.start.line, edit.range.start.character)?.let { start ->
      virtualOffset(edit.range.end.line, edit.range.end.character)
        ?.takeIf { it >= start }
        ?.let { Triple(start, it, edit.newText) }
    }
  }
  if (null in located) return null
  val applied = StringBuilder(this)
  located.filterNotNull().sortedByDescending { it.first }
    .forEach { (start, end, newText) -> applied.replace(start, end, newText) }
  return applied.toString()
}

/**
 * The offset in this text a virtual `line`/`character` names - **strict**, unlike
 * [NotebookVirtualDocument]'s cell-offset clamp: an edit position past a line's end or past
 * the document is a position the answer cannot be trusted to have meant, so it refuses.
 * `character` may equal the line's own length - an end-exclusive position is still a spot.
 */
private fun String.virtualOffset(line: Int, character: Int): Int? {
  val offset = if (line >= 0 && character >= 0) {
    generateSequence(0) { start -> indexOf('\n', start).takeIf { it >= 0 }?.inc() }
      .drop(line)
      .firstOrNull()
  } else {
    null
  } ?: return null
  val lineEnd = indexOf('\n', offset).let { if (it < 0) length else it }
  return if (character <= lineEnd - offset) offset + character else null
}
