package com.jupydroid.app.domain.model

import java.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * The three `cell_type`s nbformat 4 defines.
 *
 * [RAW] is the one that never reaches a kernel and never renders: `RawCell` is an editor and
 * nothing else (`packages/cells/src/widget.ts:2671-2694`), and `runCell`'s switch falls through to
 * `default: break` for it (`packages/notebook/src/cellexecutor.ts:140-141`). nbconvert is what it
 * is for, which is why its metadata carries a `format`.
 */
enum class CellKind { CODE, MARKDOWN, RAW }

/**
 * The nbformat object this was read from, kept verbatim so saving cannot destroy what the app
 * does not model - `tags`, `slideshow`, `attachments`, a widget's mime payload, an output's own
 * metadata. Empty for anything the app created itself.
 *
 * nbformat is an **open** format and says so in its own types: every interface in JupyterLab's
 * `packages/nbformat/src/index.ts` extends `PartialJSONObject`. "These keys, plus anything else"
 * is the contract, so a writer that rebuilds a cell from the fields it recognises is a shredder.
 * Preserving the remainder is the same idea protobuf calls unknown-field retention.
 *
 * It rides on the domain type rather than being merged from disk at save time because a
 * duplicated cell has a new id: there would be nothing on disk to merge it with, and JupyterLab's
 * duplicate keeps the original's metadata by cloning its JSON.
 */
typealias PreservedJson = JsonObject

data class Cell(
  val id: String,
  val kind: CellKind,
  val source: String,
  /** What the kernel last produced for this cell; persisted in nbformat like Jupyter's. */
  val outputs: List<CellOutput> = emptyList(),
  val executionCount: Int? = null,
  /**
   * Collapsed source. A view state, but a persisted one - Jupyter writes it into the cell's
   * metadata so a notebook opens the way it was left.
   */
  val sourceHidden: Boolean = false,
  /** Collapsed outputs. Independent of [sourceHidden], and stored under a different key. */
  val outputsHidden: Boolean = false,
  /**
   * This heading is hiding the cells under it - the section it owns, not a half of itself.
   *
   * Persisted like the two above and under a third key again, `jp-MarkdownHeadingCollapsed`
   * (`packages/cells/src/widget.ts:156`, `:2263-2276`). Meaningless on a cell that heads nothing,
   * and the transforms refuse to set it there; which cells it *hides* is derived rather than
   * stored, because the reference recomputes that itself
   * (`com.jupydroid.app.ui.editor.hiddenCellIds`).
   */
  val headingCollapsed: Boolean = false,
  /** See [PreservedJson]. Copied by `copy`, so a duplicated cell keeps its neighbour's too. */
  val preserved: PreservedJson = JsonObject(emptyMap()),
)

/**
 * A notebook's content. Deliberately **without** a "last updated" time: when it was last written
 * is a fact about the stored file, not about the cells, and a field here would be one a caller
 * could set and a writer would have to remember to honour. It is
 * [com.jupydroid.app.domain.model.WorkspaceEntry.updatedAt]'s, read off the file when the
 * workspace is listed - which is where the reference keeps it too (`last_modified`).
 */
data class Notebook(
  val id: String,
  val title: String,
  val kernelId: String,
  val cells: List<Cell>,
  /** See [PreservedJson]: `language_info`, `widgets`, `authors` and anything a newer tool wrote. */
  val preserved: PreservedJson = JsonObject(emptyMap()),
)
