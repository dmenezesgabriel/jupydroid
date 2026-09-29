package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
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

  // --- P5e-6: the range a format ask sends, and the slice its answer lands as -------------

  /**
   * A cell's format range is its whole block, `end` at the last line's own length - **not**
   * the LSP-canonical exclusive `(lines, 0)`: autopep8's `pylsp_format_range` adds one to the
   * end line before calling `fix_code`, so the canonical end would pull the first separator
   * line into its `line_range`, let its blank-line fixes rewrite it, and trip the
   * prefix/suffix guard into a no-op on the real server. Read off the shipped
   * `autopep8_format.py`, not assumed.
   */
  @Test
  fun rangeOf_aCell_isItsWholeBlock_endingOnItsLastLine() {
    val document = documentOf(code("c1", "a\nb"), code("c2", "x\ny\nz"), code("c3", "last"))

    assertEquals(LanguageRange(LanguagePosition(0, 0), LanguagePosition(1, 1)), document.rangeOf("c1"))
    // c2 starts on virtual line 4 (2 cell lines + 2 separators), owns lines 4..6.
    assertEquals(LanguageRange(LanguagePosition(4, 0), LanguagePosition(6, 1)), document.rangeOf("c2"))
    assertEquals(LanguageRange(LanguagePosition(9, 0), LanguagePosition(9, 4)), document.rangeOf("c3"))
  }

  @Test
  fun rangeOf_aCellOutsideTheDocument_isNull() {
    val document = documentOf(code("c1", "a"), Cell("m1", CellKind.MARKDOWN, "# hi"))

    assertNull(document.rangeOf("m1"))
    assertNull(document.rangeOf("no-such-cell"))
  }

  /**
   * The shipped formatter's own answer shape: `autopep8_format.py` returns one `TextEdit`
   * covering the **whole** document even for a range ask, so applying it means verifying
   * nothing outside the cell moved and slicing the cell's new lines out.
   */
  @Test
  fun cellSourceAfterEdits_aWholeDocumentEdit_slicesTheCellsFormattedSource() {
    val document = documentOf(code("c1", "x=1"), code("c2", "y=2"))
    // text is "x=1\n\n\ny=2\n"; autopep8's whole-document edit ends at (len(splitlines),0) = (4,0).
    val edits = listOf(edit(0, 0, 4, 0, "x=1\n\n\ny = 2\n"))

    assertEquals("y = 2", document.cellSourceAfterEdits("c2", edits))
  }

  /**
   * The guard's whole reason to exist: an answer that rewrote a separator or a different
   * cell can never be spliced back without corrupting the notebook, so it is refused.
   */
  @Test
  fun cellSourceAfterEdits_anEditThatMovedOutsideTheCell_isRefused() {
    val document = documentOf(code("c1", "x=1"), code("c2", "y=2"))

    // The formatter "fixed" c1 too - the prefix before c2 is no longer byte-identical.
    assertNull(document.cellSourceAfterEdits("c2", listOf(edit(0, 0, 4, 0, "x = 1\n\n\ny = 2\n"))))
    // One separator blank line went missing - same refusal from the other side.
    assertNull(document.cellSourceAfterEdits("c2", listOf(edit(0, 0, 4, 0, "x=1\n\ny = 2\n"))))
    // An edit whose range reaches a line the document does not have is refused, not clamped.
    assertNull(document.cellSourceAfterEdits("c2", listOf(edit(3, 0, 9, 0, "y = 2\n"))))
  }

  /** More than one edit applies, both inside the cell. */
  @Test
  fun cellSourceAfterEdits_severalEdits_applyInOrder() {
    val document = documentOf(code("c1", "x=1"), code("c2", "y=2\nz=3"))
    // c2's block is virtual lines 3..4 (1 cell line + 2 separators); both edits sit inside it.
    val edits = listOf(edit(3, 0, 3, 3, "y = 2"), edit(4, 0, 4, 3, "z = 3"))

    assertEquals("y = 2\nz = 3", document.cellSourceAfterEdits("c2", edits))
  }

  /** Nothing to apply and nobody to apply it to are both a refusal. */
  @Test
  fun cellSourceAfterEdits_noEditsOrNoCell_isNull() {
    val document = documentOf(code("c1", "x=1"))

    assertNull(document.cellSourceAfterEdits("c1", emptyList()))
    assertNull(document.cellSourceAfterEdits("m1", listOf(edit(0, 0, 1, 0, "x = 1\n"))))
  }

  /**
   * A cell that grew a trailing blank line is still its own source - autopep8's
   * end-of-file newline lands inside the cell's slice, not on a separator.
   */
  @Test
  fun cellSourceAfterEdits_theLastCell_keepsItsOwnTrailingNewline() {
    val document = documentOf(code("c1", "x=1"), code("c2", "y=2"))
    val edits = listOf(edit(0, 0, 4, 0, "x=1\n\n\ny = 2\n\n"))

    assertEquals("y = 2\n", document.cellSourceAfterEdits("c2", edits))
  }

  // --- P5e-7: a rename's whole-document WorkspaceEdit, sliced back into cells -------------

  /**
   * The probe's own shape: pylsp answers
   * `textDocument/rename` with one `TextEdit` spanning the whole document, `newText`
   * carrying the new name at the `def` line and the call site alike - so both cells'
   * sources come back, joined exactly the way [text] was built.
   */
  @Test
  fun cellSourcesAfterEdits_theProbesRename_renamesBothCells() {
    val document = documentOf(
      code("c1", "def total(items):\n    return sum(items)"),
      code("c2", "answer = total([1, 2])"),
    )
    val edits = listOf(
      edit(0, 0, 5, 0, "def summed(items):\n    return sum(items)\n\n\nanswer = summed([1, 2])\n"),
    )

    assertEquals(
      mapOf(
        "c1" to "def summed(items):\n    return sum(items)",
        "c2" to "answer = summed([1, 2])",
      ),
      document.cellSourcesAfterEdits(edits),
    )
  }

  /** Markdown cells were never in the document, so no rename can claim them back. */
  @Test
  fun cellSourcesAfterEdits_markdownCells_haveNoSourceToRename() {
    val document = documentOf(
      code("c1", "x = 1"),
      Cell("m1", CellKind.MARKDOWN, "# notes"),
      code("c2", "y = x"),
    )
    val edits = listOf(edit(0, 0, 4, 0, "z = 1\n\n\ny = z\n"))

    val sources = document.cellSourcesAfterEdits(edits)

    assertEquals(setOf("c1", "c2"), sources?.keys)
  }

  /**
   * The guard's whole reason to exist: an answer that rewrote a separator - cells merged,
   * a blank line eaten - can never be sliced back without corrupting a cell, so it is
   * refused rather than misapplied.
   */
  @Test
  fun cellSourcesAfterEdits_anAnswerThatMovedASeparator_isRefused() {
    val document = documentOf(code("c1", "x=1"), code("c2", "y=2"))

    // A separator blank line went missing - the parts no longer number the cells.
    assertNull(document.cellSourcesAfterEdits(listOf(edit(0, 0, 4, 0, "x=1\n\ny=2\n"))))
    // The separator run grew - whether the extra blanks are c1's tail or c2's head is a guess.
    assertNull(document.cellSourcesAfterEdits(listOf(edit(0, 0, 4, 0, "x=1\n\n\n\n\ny=2\n"))))
    // An edit whose range reaches a line the document does not have is refused, not clamped.
    assertNull(document.cellSourcesAfterEdits(listOf(edit(3, 0, 9, 0, "y = 2\n"))))
  }

  /** Nothing to apply is a refusal, not an empty map - an absent answer is not a rename. */
  @Test
  fun cellSourcesAfterEdits_noEdits_isNull() {
    val document = documentOf(code("c1", "x=1"))

    assertNull(document.cellSourcesAfterEdits(emptyList()))
  }

  /**
   * The verification: sources that do not rebuild the edited text byte-for-byte - here a
   * lost trailing newline - can only mean the split landed wrong.
   */
  @Test
  fun cellSourcesAfterEdits_anAnswerWithoutTheTrailingNewline_isRefused() {
    val document = documentOf(code("c1", "x=1"), code("c2", "y=2"))
    val edits = listOf(edit(0, 0, 4, 0, "x = 1\n\n\ny = 2"))

    assertNull(document.cellSourcesAfterEdits(edits))
  }

  private fun edit(startLine: Int, startChar: Int, endLine: Int, endChar: Int, newText: String) =
    LanguageEdit(LanguageRange(LanguagePosition(startLine, startChar), LanguagePosition(endLine, endChar)), newText)

  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)

  private fun documentOf(vararg cells: Cell) = NotebookVirtualDocument("Analysis.ipynb", cells.toList())
}
