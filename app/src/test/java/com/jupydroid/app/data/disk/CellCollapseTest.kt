package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a collapsed cell records that it is collapsed, which is **two different keys** and not
 * the symmetric pair the name suggests.
 *
 * A hidden input is `metadata.jupyter.source_hidden` (JupyterLab `saveCollapseState`,
 * `packages/cells/src/widget.ts:472-492`). A hidden output is plain `metadata.collapsed` -
 * JupyterLab refuses to write `jupyter.outputs_hidden` in as many words ("Do not set
 * jupyter.outputs_hidden since it is redundant", `widget.ts:1437`, citing nbformat issue 137)
 * and reads `collapsed` as "the source of truth" (`widget.ts:1452-1458`).
 *
 * Both are written only when true: JupyterLab *deletes* the key to un-collapse, so writing
 * `false` would leave a notebook fuller than the one Jupyter itself writes.
 */
class CellCollapseTest {
  @Test
  fun toDomain_readsAHiddenSource() {
    val cell = cellOf("""{"cell_type":"code","source":"x = 1","metadata":{"jupyter":{"source_hidden":true}}}""")

    assertTrue(cell.sourceHidden)
  }

  @Test
  fun toDomain_leavesACellWithNoCollapseMetadataExpanded() {
    val cell = cellOf("""{"cell_type":"code","source":"x = 1","metadata":{}}""")

    assertFalse(cell.sourceHidden)
  }

  /** `source_hidden: false` is legal even though Jupyter deletes the key instead. */
  @Test
  fun toDomain_readsAnExplicitFalseAsExpanded() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{"jupyter":{"source_hidden":false}}}""")

    assertFalse(cell.sourceHidden)
  }

  /** Another tool's `jupyter` block is not a reason to throw; only the one key is ours. */
  @Test
  fun toDomain_ignoresOtherKeysUnderJupyter() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{"jupyter":{"outputs_hidden":true}}}""")

    assertFalse(cell.sourceHidden)
  }

  @Test
  fun toFile_writesAHiddenSourceUnderJupyter() {
    val metadata = codeCell(sourceHidden = true).metadataOf()

    assertEquals("""{"jupyter":{"source_hidden":true}}""", Json.encodeToString(JsonObject.serializer(), metadata))
  }

  /** Un-collapsing deletes the key rather than writing false, which is what Jupyter does. */
  @Test
  fun toFile_writesNoCollapseMetadataForAnExpandedCell() {
    val metadata = codeCell(sourceHidden = false).metadataOf()

    assertEquals("{}", Json.encodeToString(JsonObject.serializer(), metadata))
  }

  @Test
  fun roundTrip_keepsAHiddenSource() {
    val cell = codeCell(sourceHidden = true)

    assertTrue(cell.rewritten().sourceHidden)
  }

  @Test
  fun toDomain_readsHiddenOutputsFromCollapsed() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{"collapsed":true}}""")

    assertTrue(cell.outputsHidden)
  }

  /**
   * `jupyter.outputs_hidden` is the key the name suggests and the one Jupyter does **not**
   * use: JupyterLab refuses to write it ("Do not set jupyter.outputs_hidden since it is
   * redundant", `widget.ts:1437`) and `loadCollapseState` reads only `collapsed`
   * (`widget.ts:1452-1458`). Honouring it here would make us disagree with the front end
   * every notebook is opened in.
   */
  @Test
  fun toDomain_doesNotTreatJupyterOutputsHiddenAsTheSourceOfTruth() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{"jupyter":{"outputs_hidden":true}}}""")

    assertFalse(cell.outputsHidden)
  }

  @Test
  fun toFile_writesHiddenOutputsAsCollapsed() {
    val metadata = codeCell(outputsHidden = true).metadataOf()

    assertEquals("""{"collapsed":true}""", Json.encodeToString(JsonObject.serializer(), metadata))
  }

  /** Both collapses at once, and they must not overwrite each other's key. */
  @Test
  fun toFile_writesBothKeysWhenBothAreHidden() {
    val metadata = codeCell(sourceHidden = true, outputsHidden = true).metadataOf()

    assertEquals(
      """{"jupyter":{"source_hidden":true},"collapsed":true}""",
      Json.encodeToString(JsonObject.serializer(), metadata),
    )
  }

  @Test
  fun roundTrip_keepsBothCollapses() {
    val read = codeCell(sourceHidden = true, outputsHidden = true).rewritten()

    assertTrue(read.sourceHidden)
    assertTrue(read.outputsHidden)
  }

  // --- the third key, which is a heading hiding its section (P2f) ------------------------

  @Test
  fun toDomain_readsACollapsedHeading() {
    val cell = cellOf(
      """{"cell_type":"markdown","source":"# H","metadata":{"jp-MarkdownHeadingCollapsed":true}}""",
    )

    assertTrue(cell.headingCollapsed)
  }

  /** At the top of the metadata, not inside `jupyter` - `MARKDOWN_HEADING_COLLAPSED` (`widget.ts:156`). */
  @Test
  fun toDomain_doesNotReadTheHeadingKeyFromInsideJupyter() {
    val cell = cellOf(
      """{"cell_type":"markdown","source":"# H","metadata":{"jupyter":{"jp-MarkdownHeadingCollapsed":true}}}""",
    )

    assertFalse(cell.headingCollapsed)
  }

  @Test
  fun toFile_writesACollapsedHeadingAtTheTopOfTheMetadata() {
    val metadata = headingCell(headingCollapsed = true).metadataOf()

    assertEquals(
      """{"jp-MarkdownHeadingCollapsed":true}""",
      Json.encodeToString(JsonObject.serializer(), metadata),
    )
  }

  /** Deleted rather than written false, as `headingCollapsed`'s setter does (`widget.ts:2263-2276`). */
  @Test
  fun toFile_writesNothingForAnExpandedHeading() {
    assertEquals("{}", Json.encodeToString(JsonObject.serializer(), headingCell(headingCollapsed = false).metadataOf()))
  }

  @Test
  fun roundTrip_keepsACollapsedHeading() {
    assertTrue(headingCell(headingCollapsed = true).rewritten().headingCollapsed)
  }

  // --- the fourth key: a cell whose outputs are scrolled rather than hidden (P2g-2a) -----

  @Test
  fun toDomain_readsScrolledOutputs() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{"scrolled":true}}""")

    assertTrue(cell.outputsScrolled)
  }

  @Test
  fun toDomain_leavesACellWithNoScrolledKeyUnscrolled() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{}}""")

    assertFalse(cell.outputsScrolled)
  }

  /**
   * `'auto'` is a value nbformat allows and JupyterLab does not implement -
   * `loadScrolledState` reads anything but `true` as unscrolled and says so in its own comment
   * ("We don't have the notion of 'auto' scrolled, so we make it false", `widget.ts:1532-1537`).
   * Reading it any other way here would disagree with the front end these notebooks are opened in.
   */
  @Test
  fun toDomain_readsAutoAsUnscrolled() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{"scrolled":"auto"}}""")

    assertFalse(cell.outputsScrolled)
  }

  @Test
  fun toFile_writesScrolledOutputsAsTrue() {
    val metadata = codeCell(outputsScrolled = true).metadataOf()

    assertEquals("""{"scrolled":true}""", Json.encodeToString(JsonObject.serializer(), metadata))
  }

  /** Deleted rather than written false - the same rule every other collapse key follows. */
  @Test
  fun toFile_writesNoScrolledKeyForAnUnscrolledCell() {
    assertEquals("{}", Json.encodeToString(JsonObject.serializer(), codeCell(outputsScrolled = false).metadataOf()))
  }

  /** A foreign `"auto"` does not survive a save: this app has no third state to preserve it in. */
  @Test
  fun toFile_dropsAForeignAutoOnAnUnscrolledCell() {
    val cell = cellOf("""{"cell_type":"code","source":"x","metadata":{"scrolled":"auto"}}""")

    assertEquals("{}", Json.encodeToString(JsonObject.serializer(), cell.metadataOf()))
  }

  @Test
  fun roundTrip_keepsScrolledOutputs() {
    assertTrue(codeCell(outputsScrolled = true).rewritten().outputsScrolled)
  }

  private fun cellOf(json: String): Cell {
    val raw = Json.parseToJsonElement(json) as JsonObject
    return Json.decodeFromJsonElement(CellFile.serializer(), raw).toDomain(raw)
  }

  /** Through the real writer, so what is asserted is what lands in the file. */
  private fun Cell.metadataOf(): JsonObject =
    (notebookOf(this)["cells"] as JsonArray).single().jsonObject["metadata"] as JsonObject

  /** Written and read straight back, which is the round trip a save-then-open performs. */
  private fun Cell.rewritten(): Cell = cellOf(
    (notebookOf(this)["cells"] as JsonArray).single().jsonObject.toString(),
  )

  private fun notebookOf(cell: Cell): JsonObject = Notebook("nb", "Title", "python3", listOf(cell)).toFileJson()

  private fun codeCell(
    sourceHidden: Boolean = false,
    outputsHidden: Boolean = false,
    outputsScrolled: Boolean = false,
  ) = Cell(
    id = "c1",
    kind = CellKind.CODE,
    source = "x = 1",
    sourceHidden = sourceHidden,
    outputsHidden = outputsHidden,
    outputsScrolled = outputsScrolled,
  )

  private fun headingCell(headingCollapsed: Boolean) = Cell(
    id = "h1",
    kind = CellKind.MARKDOWN,
    source = "# Chapter",
    headingCollapsed = headingCollapsed,
  )
}
