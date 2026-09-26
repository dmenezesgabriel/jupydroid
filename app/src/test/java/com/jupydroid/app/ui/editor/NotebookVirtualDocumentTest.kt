package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.port.LanguagePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/PLAN.md` P5d-1: one `file://` document stands for the notebook's code cells under the
 * language server - the reference's `VirtualDocument` (`packages/lsp/src/virtual/document.ts`)
 * cut to what a Python-only notebook needs. Cells join with two blank lines between them, the
 * reference's `blankLinesBetweenCells`, and the (cell, line) <-> virtual line map is what lets
 * a completion ask and a `publishDiagnostics` range land where they belong.
 */
class NotebookVirtualDocumentTest {

  @Test
  fun theText_concatenatesCodeCellsWithTwoBlankLinesBetween() {
    val document =
      documentOf(
        code("c1", "a = 1\nb = 2"),
        code("c2", "c = 3"),
      )

    assertEquals("a = 1\nb = 2\n\n\nc = 3\n", document.text)
  }

  @Test
  fun markdownAndRawCells_areNotInTheDocument() {
    val document =
      documentOf(
        code("c1", "a = 1"),
        Cell("m1", CellKind.MARKDOWN, "# not python"),
        Cell("r1", CellKind.RAW, "nor this"),
        code("c2", "b = 2"),
      )

    assertEquals("a = 1\n\n\nb = 2\n", document.text)
  }

  @Test
  fun theUri_namesTheNotebookAsAPythonFile() {
    val document = NotebookVirtualDocument("Analysis.ipynb", listOf(code("c1", "x")))

    assertTrue(
      "pylsp wants a file scheme naming the notebook: ${document.uri}",
      document.uri.startsWith("file://") && document.uri.endsWith("Analysis.ipynb.py"),
    )
  }

  @Test
  fun positionOf_aCursorInTheFirstCell_isItsLineAndCharacter() {
    val document = documentOf(code("c1", "import os\nos.path.jo"), code("c2", "y = 1"))

    assertEquals(LanguagePosition(1, 10), document.positionOf("c1", "import os\nos.path.jo".length))
  }

  @Test
  fun positionOf_aCursorInALaterCell_landsPastTheCellsAndSeparatorsBeforeIt() {
    // c1 is three lines; two separator lines follow it, so c2's line 0 is virtual line 5.
    val document = documentOf(code("c1", "a\nb\nc"), code("c2", "os.path.jo"))

    assertEquals(LanguagePosition(5, 10), document.positionOf("c2", "os.path.jo".length))
  }

  @Test
  fun positionOf_anOffsetMidCell_countsItsOwnLinesOnly() {
    val document = documentOf(code("c1", "a = 1"), code("c2", "x\ny\nos.path.jo"))

    // c2's cursor sits on its own line 2; virtually that is 1 + 2 + 2 = 5.
    assertEquals(LanguagePosition(5, 10), document.positionOf("c2", "x\ny\nos.path.jo".length))
  }

  @Test
  fun positionOf_aCellOutsideTheDocument_isNull() {
    val document = documentOf(code("c1", "a = 1"), Cell("m1", CellKind.MARKDOWN, "# hi"))

    assertNull(document.positionOf("m1", 4))
    assertNull(document.positionOf("no-such-cell", 0))
  }

  @Test
  fun cellLineAt_aVirtualLine_mapsBackToItsCellAndLine() {
    val document = documentOf(code("c1", "a\nb"), code("c2", "x\ny\nz"))

    assertEquals(NotebookVirtualDocument.CellLine("c2", 2), document.cellLineAt(6))
    assertEquals(NotebookVirtualDocument.CellLine("c1", 0), document.cellLineAt(0))
  }

  @Test
  fun cellLineAt_aSeparatorLine_isNull() {
    val document = documentOf(code("c1", "a\nb"), code("c2", "x"))

    assertNull(document.cellLineAt(2))
    assertNull(document.cellLineAt(3))
    assertEquals(NotebookVirtualDocument.CellLine("c2", 0), document.cellLineAt(4))
  }

  @Test
  fun cellLineAt_beyondTheText_isNull() {
    val document = documentOf(code("c1", "a"))

    assertNull(document.cellLineAt(1))
    assertNull(document.cellLineAt(99))
  }

  @Test
  fun cellOffsetOf_aVirtualPosition_isTheOffsetInsideTheCell() {
    val document = documentOf(code("c1", "a\nb"), code("c2", "print(undef_name)"))

    // c2 starts on virtual line 4 (2 cell lines + 2 separators); its (4, 6) is offset 6.
    assertEquals(6, document.cellOffsetOf("c2", LanguagePosition(4, 6)))
  }

  @Test
  fun cellOffsetOfClamped_aRangeEndingPastTheCell_clampsToTheCellsEnd() {
    val document = documentOf(code("c1", "a\nb"), code("c2", "print(undef_name)"))

    // A range whose end lands on a separator or a later cell underlines only what this
    // cell owns - the CodeMirror lint gutter's own convention of the mark landing on the
    // start line, so a stray `end` cannot wander into the next cell's text.
    assertEquals(
      "print(undef_name)".length,
      document.cellOffsetOfClamped("c2", LanguagePosition(9, 3)),
    )
    assertEquals(6, document.cellOffsetOfClamped("c2", LanguagePosition(4, 6)))
    assertNull(document.cellOffsetOfClamped("no-such-cell", LanguagePosition(0, 0)))
  }

  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)

  private fun documentOf(vararg cells: Cell) = NotebookVirtualDocument("Analysis.ipynb", cells.toList())
}
