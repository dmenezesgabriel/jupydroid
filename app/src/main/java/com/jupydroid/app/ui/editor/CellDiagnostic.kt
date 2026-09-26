package com.jupydroid.app.ui.editor

/**
 * One language-server diagnostic mapped onto its cell: the one-based [line] its range starts
 * on (CodeMirror's own convention for where a lint-gutter icon lands on a multi-line range),
 * the LSP severity number (1 error, 2 warning, 3 information, 4 hint), and what it says.
 */
data class CellDiagnostic(
  val line: Int,
  val severity: Int,
  val code: String?,
  val source: String?,
  val message: String,
)

/** The LSP `DiagnosticSeverity` numbers, named once for every `when` the UI runs. */
const val SEVERITY_ERROR = 1
const val SEVERITY_WARNING = 2
const val SEVERITY_INFORMATION = 3
const val SEVERITY_HINT = 4

/** The LSP severity number as its protocol name - `"error"`, never `1`, where a reader sees it. */
internal fun severityName(severity: Int): String = when (severity) {
  SEVERITY_WARNING -> "warning"
  SEVERITY_INFORMATION -> "information"
  SEVERITY_HINT -> "hint"
  else -> "error"
}

/**
 * What a cell's diagnostics read as - the gutter mark is a painted square TalkBack cannot
 * reach, so the field states them: `"diagnostic on line 2: error, undefined name 'x'
 * (pyflakes)"`, entries joined `"; "`. CodeMirror announces its lint gutter entries the same
 * way - severity first, then the message.
 */
internal fun diagnosticSummary(diagnostics: List<CellDiagnostic>): String =
  diagnostics.joinToString("; ") { diagnostic ->
    val at = "diagnostic on line ${diagnostic.line}: ${severityName(diagnostic.severity)}, ${diagnostic.message}"
    diagnostic.source?.let { "$at ($it)" } ?: at
  }
