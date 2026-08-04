package com.jupydroid.app.domain.model

import java.time.Instant

enum class CellKind { CODE, MARKDOWN }

data class Cell(
  val id: String,
  val kind: CellKind,
  val source: String,
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
