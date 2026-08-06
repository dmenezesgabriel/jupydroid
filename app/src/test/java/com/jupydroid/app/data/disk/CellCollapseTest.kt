package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    val metadata = codeCell(sourceHidden = true).toFile().metadata

    assertEquals("""{"jupyter":{"source_hidden":true}}""", Json.encodeToString(JsonObject.serializer(), metadata))
  }

  /** Un-collapsing deletes the key rather than writing false, which is what Jupyter does. */
  @Test
  fun toFile_writesNoCollapseMetadataForAnExpandedCell() {
    val metadata = codeCell(sourceHidden = false).toFile().metadata

    assertEquals("{}", Json.encodeToString(JsonObject.serializer(), metadata))
  }

  @Test
  fun roundTrip_keepsAHiddenSource() {
    val cell = codeCell(sourceHidden = true)

    assertTrue(cell.toFile().toDomain().sourceHidden)
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
    val metadata = codeCell(outputsHidden = true).toFile().metadata

    assertEquals("""{"collapsed":true}""", Json.encodeToString(JsonObject.serializer(), metadata))
  }

  /** Both collapses at once, and they must not overwrite each other's key. */
  @Test
  fun toFile_writesBothKeysWhenBothAreHidden() {
    val metadata = codeCell(sourceHidden = true, outputsHidden = true).toFile().metadata

    assertEquals(
      """{"jupyter":{"source_hidden":true},"collapsed":true}""",
      Json.encodeToString(JsonObject.serializer(), metadata),
    )
  }

  @Test
  fun roundTrip_keepsBothCollapses() {
    val read = codeCell(sourceHidden = true, outputsHidden = true).toFile().toDomain()

    assertTrue(read.sourceHidden)
    assertTrue(read.outputsHidden)
  }

  private fun cellOf(json: String): Cell = Json.decodeFromString(CellFile.serializer(), json).toDomain()

  private fun codeCell(sourceHidden: Boolean = false, outputsHidden: Boolean = false) = Cell(
    id = "c1",
    kind = CellKind.CODE,
    source = "x = 1",
    sourceHidden = sourceHidden,
    outputsHidden = outputsHidden,
  )
}
