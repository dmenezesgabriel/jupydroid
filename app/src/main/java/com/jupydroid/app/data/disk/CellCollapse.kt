package com.jupydroid.app.data.disk

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Where a collapsed cell records that it is collapsed.
 *
 * **Two different keys, and not the symmetric pair the names suggest.**
 *
 * A hidden **source** is `metadata.jupyter.source_hidden`, which is JupyterLab's
 * `saveCollapseState` (`packages/cells/src/widget.ts:472-492`).
 *
 * A hidden **output** is plain `metadata.collapsed`. `jupyter.outputs_hidden` exists in the
 * nbformat types and is exactly what JupyterLab refuses to write - "Do not set
 * jupyter.outputs_hidden since it is redundant" (`widget.ts:1437`, citing nbformat issue 137) -
 * and never reads: `loadCollapseState` calls `collapsed` "the source of truth"
 * (`widget.ts:1452-1458`). Honouring the tidier-looking key would make us disagree with the
 * front end these notebooks are opened in.
 *
 * Both are written only when true - by `NotebookJsonWriter`, which is where the writing has always
 * happened - because that is what Jupyter writes: un-collapsing *deletes*
 * the key rather than setting it to `false`, and takes the `jupyter` object with it when nothing
 * else is left in it. A notebook we save must not be fuller than one Jupyter saves. An entirely
 * empty metadata object is then equal to `CellFile`'s default, which kotlinx omits - so an
 * expanded cell writes no metadata key at all.
 *
 * The readers are total on purpose - a `jupyter` that is not an object, or a `collapsed` that is
 * not a boolean, reads as "not hidden" rather than throwing. What is in a cell's metadata is
 * whatever wrote the file, and none of it is worth failing to open a notebook over.
 */
internal fun JsonObject.sourceHidden(): Boolean =
  ((this["jupyter"] as? JsonObject)?.get("source_hidden") as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject.outputsHidden(): Boolean = (this["collapsed"] as? JsonPrimitive)?.booleanOrNull == true

/**
 * And a **third** key, at the top of the metadata rather than inside `jupyter`:
 * `jp-MarkdownHeadingCollapsed` (`packages/cells/src/widget.ts:156`). Written only when true and
 * *deleted* when false, exactly as the pair above - `headingCollapsed`'s setter calls
 * `deleteMetadata` on the way down (`:2263-2276`).
 */
internal fun JsonObject.headingCollapsed(): Boolean =
  (this[HEADING_COLLAPSED_KEY] as? JsonPrimitive)?.booleanOrNull == true

internal const val HEADING_COLLAPSED_KEY = "jp-MarkdownHeadingCollapsed"

/**
 * And a **fourth** key: `scrolled`, plain at the top of the metadata like `collapsed` beside it.
 * Written only when true and *deleted* when false (`packages/cells/src/widget.ts:1511-1526`,
 * `saveScrolledState`), the same rule as every key above.
 *
 * **`true` is the only value read as scrolled.** nbformat also allows the string `"auto"`, and
 * JupyterLab does not implement it - `loadScrolledState` reads anything but `true` as unscrolled
 * ("We don't have the notion of 'auto' scrolled, so we make it false", `widget.ts:1532-1537`).
 * `booleanOrNull` already answers `null` for a string, which is why this reader needs no special
 * case for it: `"auto"` and a missing key fall through to the same `false` the reference produces.
 */
internal fun JsonObject.outputsScrolled(): Boolean = (this["scrolled"] as? JsonPrimitive)?.booleanOrNull == true
