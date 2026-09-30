package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `NotebookToCModel.getHeadings()` (`packages/notebook/src/toc.ts:245-322`): what the table of
 * contents is a list *of*, before anything folds, numbers or draws it.
 *
 * Its own file rather than a corner of [CellHeadingsTest], which is what the file-length gate
 * asked for when the printed headings arrived - and the seam it pointed at was already
 * there: that one is about the sections a heading owns *in the notebook*, and this is about the
 * entries a panel lists.
 */
class TableOfContentsHeadingsTest {
  private fun md(id: String, source: String) = Cell(id, CellKind.MARKDOWN, source)

  private fun code(id: String) = Cell(id, CellKind.CODE, "print(1)")

  private val notebook = EditorUiState(
    cells = listOf(
      md("h1", "# Chapter"),
      code("a"),
      md("h2", "## Part"),
      code("b"),
      md("h1b", "# Next chapter"),
      code("c"),
    ),
    loaded = true,
  )

  // --- the table of contents -------------------------------------------------------

  /**
   * `NotebookToCModel.getHeadings()` walks the notebook's cells and emits a **flat list**, one
   * entry per heading, in document order - the nesting is built later, at render time
   * (`packages/notebook/src/toc.ts:245-322`, `packages/toc/src/toctree.tsx:57-102`). Ours is
   * simpler by construction: a cell already collapses to at most one heading
   * ([Cell.headingLevel]'s "lowest number wins"), so an entry is 1:1 with a markdown cell that
   * has one, with no per-cell multiplicity to model.
   */
  @Test
  fun theTableOfContentsIsOneEntryPerHeadingInDocumentOrder() {
    assertEquals(
      listOf(
        TocHeading("h1", 1, "Chapter"),
        TocHeading("h2", 2, "Part"),
        TocHeading("h1b", 1, "Next chapter"),
      ),
      notebook.tableOfContentsHeadings(),
    )
  }

  /** A code or raw cell, and a markdown cell with no heading, contribute no entry. */
  @Test
  fun aCellThatHeadsNothingHasNoEntry() {
    val mixed = EditorUiState(
      cells = listOf(code("a"), md("p", "just prose"), Cell("r", CellKind.RAW, "# not markdown")),
      loaded = true,
    )

    assertEquals(emptyList<TocHeading>(), mixed.tableOfContentsHeadings())
  }

  @Test
  fun anEmptyNotebookHasAnEmptyTableOfContents() {
    assertEquals(emptyList<TocHeading>(), EditorUiState(loaded = true).tableOfContentsHeadings())
  }

  /** The entry's text is the same heading [Cell.headingLevel] picked, not just its number. */
  @Test
  fun theEntryTextIsTheHeadingTheLevelCameFrom() {
    val tied = EditorUiState(cells = listOf(md("h", "### Sub\n\n# Top\n\n## Mid")), loaded = true)

    assertEquals(listOf(TocHeading("h", 1, "Top")), tied.tableOfContentsHeadings())
  }

  // --- headings a code cell printed -------------------------------------------------

  private fun htmlOutput(html: String) = CellOutput.DisplayData(
    mimeBundle = mapOf("text/html" to JsonPrimitive(html)),
  )

  /**
   * `CodeCell.getHeadings` reads the cell's **outputs** for headings as well
   * (`packages/cells/src/widget.ts:1312-1370`), which is what `toc:display-outputs-numbering`
   * turns on. Ours reads what the output actually renders as, so an entry always points at
   * something on screen.
   */
  @Test
  fun aCodeCellsPrintedHeadingIsAnEntry() {
    val printed = EditorUiState(
      cells = listOf(md("h1", "# Chapter"), code("out")),
      outputs = mapOf("out" to listOf(htmlOutput("<h2>Printed</h2><p>body</p>"))),
      loaded = true,
    )

    assertEquals(
      listOf(TocHeading("h1", 1, "Chapter"), TocHeading("out", 2, "Printed", key = "out#0.0")),
      printed.tableOfContentsHeadings(),
    )
  }

  /** `includeOutput` off leaves the outputs out entirely, which is the command's whole job. */
  @Test
  fun withOutputHeadingsOffOnlyTheSourceIsRead() {
    val printed = EditorUiState(
      cells = listOf(code("out")),
      outputs = mapOf("out" to listOf(htmlOutput("<h2>Printed</h2>"))),
      loaded = true,
    )

    assertEquals(emptyList<TocHeading>(), printed.tableOfContentsHeadings(includeOutput = false))
  }

  /** Several headings in one output are several entries, each with a key of its own to fold by. */
  @Test
  fun everyHeadingInAnOutputGetsItsOwnEntry() {
    val printed = EditorUiState(
      cells = listOf(code("out")),
      outputs = mapOf("out" to listOf(htmlOutput("<h1>One</h1><h2>Two</h2>"))),
      loaded = true,
    )

    val entries = printed.tableOfContentsHeadings()
    assertEquals(listOf("One", "Two"), entries.map { it.text })
    assertEquals(listOf("out#0.0", "out#0.1"), entries.map { it.key })
    // Both jump to the cell that printed them: it is the thing on screen this app can scroll to.
    assertEquals(listOf("out", "out"), entries.map { it.cellId })
  }

  /** An output with no heading in it - a table, an image, a traceback - contributes nothing. */
  @Test
  fun anOutputWithNoHeadingIsNotAnEntry() {
    val printed = EditorUiState(
      cells = listOf(code("out")),
      outputs = mapOf(
        "out" to listOf(htmlOutput("<table><tr><td>1</td></tr></table>"), CellOutput.Stream(StreamName.STDOUT, "hi")),
      ),
      loaded = true,
    )

    assertEquals(emptyList<TocHeading>(), printed.tableOfContentsHeadings())
  }
}
