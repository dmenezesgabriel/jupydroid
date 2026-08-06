package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StreamName
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Saving a notebook must not destroy what this app does not model.
 *
 * nbformat is an **open** format and says so in its own types: every interface in
 * `packages/nbformat/src/index.ts` extends `PartialJSONObject` - `IBaseCell`,
 * `IBaseCellMetadata`, `INotebookMetadata`, `IBaseOutput`, `IMimeBundle`. "These keys, plus
 * anything else" is the contract, so a reader that keeps only the keys it recognises and a
 * writer that rebuilds from them is a shredder.
 *
 * The fixture is a notebook carrying the things real tools put there - nbgrader and Voilà
 * write cell metadata, RISE writes `slideshow`, jupyter-widgets writes object-valued mime
 * data and notebook-level `widgets`, and a pasted image becomes `attachments`.
 */
class NotebookRoundTripTest {
  @Test
  fun keepsCellMetadataItDoesNotModel() {
    val cell = savedCells().first().jsonObject("metadata")

    assertEquals(json("""["setup","slow"]"""), cell["tags"])
    assertEquals(json("""{"slide_type":"skip"}"""), cell["slideshow"])
    assertEquals(json("true"), cell["scrolled"])
    assertEquals(json("""{"grade_id":"cell-a1","points":5}"""), cell["nbgrader"])
  }

  /** Our two collapse keys live *inside* metadata; writing them must not evict its neighbours. */
  @Test
  fun keepsOtherKeysInsideTheJupyterNamespace() {
    val jupyter = savedCells().first().jsonObject("metadata").jsonObject("jupyter")

    assertEquals(json("true"), jupyter["outputs_hidden"])
  }

  /** A pasted image lives in `attachments`, which is a cell key nbformat defines and we do not. */
  @Test
  fun keepsACellsAttachments() {
    val markdown = savedCells()[1]

    assertEquals(ORIGINAL.cells()[1]["attachments"], markdown["attachments"])
  }

  /** An output carries metadata of its own - a figure's rendered size, for one. */
  @Test
  fun keepsTheMetadataOnAnOutput() {
    val output = savedCells().first().array("outputs").first() as JsonObject

    assertEquals(json("""{"image/png":{"width":420,"height":280}}"""), output["metadata"])
  }

  /**
   * `IMimeBundle`'s values are `MultilineString | PartialJSONObject`, so a mime payload may be
   * a whole JSON object - which is how every jupyter-widget view is stored.
   */
  @Test
  fun keepsAnObjectValuedMimePayload() {
    val data = (savedCells().first().array("outputs").first() as JsonObject).jsonObject("data")

    assertEquals(json("""{"model_id":"abc123","version_major":2}"""), data["application/vnd.jupyter.widget-view+json"])
  }

  @Test
  fun keepsNotebookMetadataItDoesNotModel() {
    val metadata = SAVED.jsonObject("metadata")

    assertEquals(json("""{"name":"python","version":"3.11.2"}"""), metadata["language_info"])
    assertEquals(json("""{"state":{}}"""), metadata["widgets"])
    assertEquals(json("""[{"name":"Ada"}]"""), metadata["authors"])
  }

  /** `kernelspec` is ours to write, but only the two fields we model - the rest is not. */
  @Test
  fun keepsExtraFieldsOnTheKernelspec() {
    val kernelspec = SAVED.jsonObject("metadata").jsonObject("kernelspec")

    assertEquals(json(""""python""""), kernelspec["language"])
    assertEquals("python3", (kernelspec["name"] as JsonElement).toString().trim('"'))
  }

  /** A notebook written by a newer tool may carry keys nbformat 4 never had. */
  @Test
  fun keepsUnknownTopLevelKeys() {
    assertEquals(json("""{"seen":true}"""), SAVED["some_future_key"])
  }

  /** The app still owns what it models: a re-read has to see the edit, not the old value. */
  @Test
  fun stillWritesTheFieldsTheAppOwns() {
    val saved = editedFirstCell { it.copy(source = "edited") }

    assertEquals("edited", saved["source"].toString().trim('"'))
  }

  /** Reading and writing untouched is the easy case; an edit is the one the app actually does. */
  @Test
  fun keepsWhatItDoesNotModelThroughAnEdit() {
    val saved = editedFirstCell { it.copy(source = "edited") }

    assertEquals(json("""["setup","slow"]"""), (saved["metadata"] as JsonObject)["tags"])
    assertEquals(json("""{"grade_id":"cell-a1","points":5}"""), (saved["metadata"] as JsonObject)["nbgrader"])
  }

  /** Collapsing writes *inside* metadata, so it is the edit most likely to evict a neighbour. */
  @Test
  fun keepsWhatItDoesNotModelThroughACollapse() {
    val metadata = editedFirstCell { it.copy(sourceHidden = true, outputsHidden = true) }["metadata"] as JsonObject

    assertEquals(json("""["setup","slow"]"""), metadata["tags"])
    assertEquals(json("true"), metadata["collapsed"])
    // Ours goes in beside theirs, in the namespace, without taking it over.
    assertEquals(json("""{"outputs_hidden":true,"source_hidden":true}"""), metadata["jupyter"])
  }

  /**
   * The reason this rides on the domain type rather than being merged from the file at save
   * time: a duplicate has a new id, so there would be nothing on disk to merge it with.
   * JupyterLab's duplicate keeps the metadata too - it clones the cell's JSON.
   */
  @Test
  fun aDuplicatedCellKeepsWhatTheOriginalCarried() {
    val notebook = ORIGINAL.toNotebook()
    val copy = notebook.cells.first().copy(id = "code-1-copy")

    val saved = notebook.copy(cells = notebook.cells + copy).toFileJson().array("cells").last() as JsonObject

    assertEquals("code-1-copy", saved["id"].toString().trim('"'))
    assertEquals(json("""["setup","slow"]"""), (saved["metadata"] as JsonObject)["tags"])
  }

  /** Re-running replaces the outputs, so the preserved ones must *not* be written back. */
  @Test
  fun writesTheNewOutputsWhenACellHasBeenRerun() {
    val fresh = listOf(CellOutput.Stream(StreamName.STDOUT, "ran again"))

    val saved = editedFirstCell { it.copy(outputs = fresh) }

    val written = (saved.array("outputs").single() as JsonObject)
    assertEquals(json(""""stream""""), written["output_type"])
    assertEquals(null, written["metadata"])
  }

  /** A cell the app created has nothing preserved and must still write valid nbformat. */
  @Test
  fun writesAValidCellForOneTheAppCreated() {
    val notebook = Notebook("nb", "New", "python3", listOf(Cell("c1", CellKind.CODE, "x = 1")), Instant.EPOCH)

    val cell = notebook.toFileJson().array("cells").single() as JsonObject

    // nbformat requires both on a code cell, so neither is omitted for being empty.
    assertEquals(json("[]"), cell["outputs"])
    assertEquals(json("null"), cell["execution_count"])
    assertEquals(json("{}"), cell["metadata"])
  }

  /** …and a markdown cell has neither, even if it used to be a code cell that did. */
  @Test
  fun writesNoOutputsOnAMarkdownCell() {
    val saved = editedFirstCell { it.copy(kind = CellKind.MARKDOWN) }

    assertEquals(null, saved["outputs"])
    assertEquals(null, saved["execution_count"])
  }

  private fun editedFirstCell(edit: (Cell) -> Cell): JsonObject {
    val notebook = ORIGINAL.toNotebook()
    val cells = listOf(edit(notebook.cells.first())) + notebook.cells.drop(1)
    return notebook.copy(cells = cells).toFileJson().array("cells").first() as JsonObject
  }

  private companion object {
    val ORIGINAL: JsonObject = json(
      """
      {
        "cells": [
          {
            "id": "code-1",
            "cell_type": "code",
            "source": "df.head()",
            "execution_count": 3,
            "metadata": {
              "tags": ["setup", "slow"],
              "slideshow": {"slide_type": "skip"},
              "scrolled": true,
              "nbgrader": {"grade_id": "cell-a1", "points": 5},
              "jupyter": {"outputs_hidden": true}
            },
            "outputs": [
              {
                "output_type": "display_data",
                "data": {
                  "text/plain": "a widget",
                  "application/vnd.jupyter.widget-view+json": {"model_id": "abc123", "version_major": 2}
                },
                "metadata": {"image/png": {"width": 420, "height": 280}}
              }
            ]
          },
          {
            "id": "md-1",
            "cell_type": "markdown",
            "source": "![shot](attachment:shot.png)",
            "metadata": {},
            "attachments": {"shot.png": {"image/png": "iVBORw0KGgo="}}
          }
        ],
        "metadata": {
          "kernelspec": {"name": "python3", "display_name": "Python 3", "language": "python"},
          "language_info": {"name": "python", "version": "3.11.2"},
          "widgets": {"state": {}},
          "authors": [{"name": "Ada"}]
        },
        "nbformat": 4,
        "nbformat_minor": 5,
        "some_future_key": {"seen": true}
      }
      """,
    ) as JsonObject

    /** The original, read into the domain and written straight back out - no edit in between. */
    val SAVED: JsonObject = ORIGINAL.toNotebook().toFileJson()

    fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    fun JsonObject.toNotebook() =
      Json { ignoreUnknownKeys = true }
        .decodeFromJsonElement(NotebookFile.serializer(), this)
        .toDomain(id = "nb", updatedAtFallback = Instant.EPOCH, preserved = this)

    fun savedCells(): List<JsonObject> = SAVED.array("cells").map { it as JsonObject }

    fun JsonObject.cells(): List<JsonObject> = array("cells").map { it as JsonObject }

    fun JsonObject.jsonObject(key: String): JsonObject = this[key] as JsonObject

    fun JsonObject.array(key: String): List<JsonElement> =
      (this[key] as kotlinx.serialization.json.JsonArray).toList()
  }
}
