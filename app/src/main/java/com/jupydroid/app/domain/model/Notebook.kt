package com.jupydroid.app.domain.model

import java.time.Instant
import kotlinx.serialization.json.JsonObject

enum class CellKind { CODE, MARKDOWN }

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
  /** See [PreservedJson]. Copied by `copy`, so a duplicated cell keeps its neighbour's too. */
  val preserved: PreservedJson = JsonObject(emptyMap()),
)

data class Notebook(
  val id: String,
  val title: String,
  val kernelId: String,
  val cells: List<Cell>,
  val updatedAt: Instant,
  /** See [PreservedJson]: `language_info`, `widgets`, `authors` and anything a newer tool wrote. */
  val preserved: PreservedJson = JsonObject(emptyMap()),
)

/** Lightweight row for the Home recent list; avoids loading every cell. */
data class NotebookSummary(
  val id: String,
  val title: String,
  val updatedAt: Instant,
)
