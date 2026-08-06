package com.jupydroid.app.domain.model

import java.time.Instant

enum class CellKind { CODE, MARKDOWN }

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
)

data class Notebook(
  val id: String,
  val title: String,
  val kernelId: String,
  val cells: List<Cell>,
  val updatedAt: Instant,
)

/** Lightweight row for the Home recent list; avoids loading every cell. */
data class NotebookSummary(
  val id: String,
  val title: String,
  val updatedAt: Instant,
)
