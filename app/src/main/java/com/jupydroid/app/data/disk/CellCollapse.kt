package com.jupydroid.app.data.disk

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Where a collapsed cell records that it is collapsed.
 *
 * A hidden **source** is `metadata.jupyter.source_hidden`, which is JupyterLab's
 * `saveCollapseState` (`packages/cells/src/widget.ts:472-492`).
 *
 * Written only when true, because that is what Jupyter writes: un-collapsing *deletes* the key
 * rather than setting it to `false`, and takes the `jupyter` object with it when nothing else
 * is left in it. A notebook we save must not be fuller than one Jupyter saves.
 *
 * The reader is total on purpose - a `jupyter` that is not an object, or a `source_hidden` that
 * is not a boolean, reads as "not hidden" rather than throwing. What is in a cell's metadata is
 * whatever wrote the file, and none of it is worth failing to open a notebook over.
 */
internal fun JsonObject.sourceHidden(): Boolean =
  ((this["jupyter"] as? JsonObject)?.get("source_hidden") as? JsonPrimitive)?.booleanOrNull == true

internal fun collapseMetadata(sourceHidden: Boolean): JsonObject = buildJsonObject {
  if (sourceHidden) putJsonObject("jupyter") { put("source_hidden", true) }
}
