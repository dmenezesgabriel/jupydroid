package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadingAnchorsTest {
  private fun md(id: String, source: String) = Cell(id, CellKind.MARKDOWN, source)

  private val notebook = EditorUiState(
    cells = listOf(
      md("intro", "# Intro\n\nSee [results](#Results)."),
      md("body", "## Method\n\nprose\n\n## Results"),
      md("news", "### What's new?"),
      Cell("out", CellKind.CODE, "display(Markdown('## Printed table'))"),
    ),
    outputs = mapOf(
      "out" to listOf(CellOutput.DisplayData(mimeBundle = mapOf("text/markdown" to JsonPrimitive("## Printed table")))),
    ),
    loaded = true,
  )

  /** `createHeaderId`: the heading's text, a space made `-` (jupyterlab@7f0e690 `rendermime/src/renderers.ts`). */
  @Test
  fun jupyterLabsIdNamesTheHeading() {
    assertEquals("intro", notebook.cellOfHeadingAnchor("Intro"))
    assertEquals("out", notebook.cellOfHeadingAnchor("Printed-table"))
  }

  /** A markdown cell can hold more than the one heading the table of contents lists for it. */
  @Test
  fun everyHeadingInACellIsAnAnchor() {
    assertEquals("body", notebook.cellOfHeadingAnchor("Results"))
  }

  /** VS Code's `slugify` on both sides (vscode@3f3380c6 `markdown-language-features/notebook/index.ts`). */
  @Test
  fun aSlugNamesTheHeadingWhenNoIdDoes() {
    assertEquals("body", notebook.cellOfHeadingAnchor("results"))
    assertEquals("news", notebook.cellOfHeadingAnchor("whats-new"))
    assertEquals("out", notebook.cellOfHeadingAnchor("printed-table"))
  }

  /** JavaScript's `\s` is Unicode-aware, so a no-break space slugs as a space does. */
  @Test
  fun aNoBreakSpaceSlugsAsASpace() {
    assertEquals("whats-new", headingSlug("What's\u00A0new?"))
  }

  /** The id is case-kept, so it tells two headings apart where their slugs collide. */
  @Test
  fun anExactIdWinsOverAnEarlierSlug() {
    val collide = EditorUiState(cells = listOf(md("a", "# C++"), md("b", "# C")), loaded = true)

    assertEquals("b", collide.cellOfHeadingAnchor("C"))
    assertEquals("a", collide.cellOfHeadingAnchor("C++"))
  }

  /** `querySelector` answers the first match in the document. */
  @Test
  fun theFirstOfTwoEqualHeadingsWins() {
    val twice = EditorUiState(cells = listOf(md("a", "# Notes"), md("b", "# Notes")), loaded = true)

    assertEquals("a", twice.cellOfHeadingAnchor("Notes"))
  }

  @Test
  fun aFragmentNoHeadingHasNamesNothing() {
    assertNull(notebook.cellOfHeadingAnchor("Conclusion"))
    assertNull(notebook.cellOfHeadingAnchor("-"))
  }

  /** A raw cell is never rendered, so a heading in it is not on screen to land on. */
  @Test
  fun aRawCellHasNoAnchors() {
    val raw = EditorUiState(cells = listOf(Cell("r", CellKind.RAW, "# Raw")), loaded = true)

    assertNull(raw.cellOfHeadingAnchor("Raw"))
  }
}
