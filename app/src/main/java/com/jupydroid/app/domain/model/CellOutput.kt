package com.jupydroid.app.domain.model

enum class StreamName { STDOUT, STDERR }

/** A single Jupyter output message the kernel produced for a cell. */
sealed interface CellOutput {
  data class Stream(val name: StreamName, val text: String) : CellOutput

  data class Error(val name: String, val value: String, val traceback: List<String>) : CellOutput

  /** mime -> payload (base64 for images, plain text for text mimes), per the Jupyter protocol. */
  data class DisplayData(val mimeBundle: Map<String, String>) : CellOutput

  data class ExecuteResult(val mimeBundle: Map<String, String>, val executionCount: Int) : CellOutput

  data object ClearOutput : CellOutput
}
