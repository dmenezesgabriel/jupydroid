package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A notebook matched as the cells it is, rather than as the JSON it is stored in.
 *
 * The reference holds a notebook's matches per **cell**: `INotebookCellMatchNoModel` is
 * `{ index, contentResults, webviewResults }`
 * (`vscode/…/search/common/searchNotebookHelpers.ts:18-22`). This is the `index` plus
 * `contentResults` half; outputs are `webviewResults` and are not searched here - see
 * `GAP-001` in `docs/architecture.md` §11.
 */
class MatchedCellsTest {
  private fun notebookOf(vararg sources: String) = Notebook(
    id = "analysis.ipynb",
    title = "analysis.ipynb",
    kernelId = "python3",
    cells = sources.mapIndexed { at, source -> Cell(id = "c$at", kind = CellKind.CODE, source = source) },
  )

  /** The cell is the index, zero-based, as `rawCell#${index}` is (`searchNotebookHelpers.ts:20`). */
  @Test
  fun aMatchNamesTheCellItIsIn() {
    val notebook = notebookOf("import os", "print(cat)")

    val matches = matchedCellsIn(notebook, Regex("cat"))

    assertEquals(listOf(1), matches.map { it.cell })
    assertEquals("print(cat)", matches.single().text)
    assertEquals(6..8, matches.single().range)
  }

  /**
   * **The line is within the cell, not within the file**, which is the whole reason a cell has to
   * be named at all: two cells both have a line 1.
   */
  @Test
  fun lineNumbersStartAgainInEveryCell() {
    val notebook = notebookOf("a = 1\ncat = 2", "cat = 3")

    val matches = matchedCellsIn(notebook, Regex("cat"))

    assertEquals(listOf(2, 1), matches.map { it.line })
    assertEquals(listOf(0, 1), matches.map { it.cell })
  }

  /** A cell with nothing in it contributes nothing - a file with no match is never a result. */
  @Test
  fun aCellWithNoMatchContributesNothing() {
    val notebook = notebookOf("import os", "print(cat)", "import sys")

    assertEquals(listOf(1), matchedCellsIn(notebook, Regex("cat")).map { it.cell })
  }

  /** All three `cell_type`s are searched, because all three have a source the find bar searches. */
  @Test
  fun everyCellKindIsSearched() {
    val notebook = Notebook(
      id = "analysis.ipynb",
      title = "analysis.ipynb",
      kernelId = "python3",
      cells = listOf(
        Cell(id = "c0", kind = CellKind.MARKDOWN, source = "# the cat"),
        Cell(id = "c1", kind = CellKind.RAW, source = "cat"),
        Cell(id = "c2", kind = CellKind.CODE, source = "cat = 1"),
      ),
    )

    assertEquals(listOf(0, 1, 2), matchedCellsIn(notebook, Regex("cat")).map { it.cell })
  }

  @Test
  fun anEmptyNotebookFindsNothing() {
    assertTrue(matchedCellsIn(notebookOf(), Regex("cat")).isEmpty())
  }

  /**
   * A cell's own text is what a landing has to resolve against, so the offsets are the cell's -
   * [MatchedLine.rangeIn] fed the cell's source, exactly as a plain file's is fed the file's.
   */
  @Test
  fun aCellsOffsetsAreResolvedAgainstThatCellsSource() {
    val notebook = notebookOf("import os", "print(os)\nprint(cat)")

    val match = matchedCellsIn(notebook, Regex("cat")).single()

    assertEquals(16..18, match.rangeIn(notebook.cells[1].source))
  }
}
