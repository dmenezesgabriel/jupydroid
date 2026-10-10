package com.jupydroid.app.ui.console

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.ui.editor.ExecutionStatus

/**
 * One submitted input in the Console's transcript, and what the kernel did with it.
 *
 * Reuses [CellOutput]/[ExecutionStatus] exactly as the notebook's own cells do, since
 * `KernelGateway.execute` already produces both and a Console entry needs nothing more than a
 * notebook cell's run does. Lives in `ui/console` rather than `domain/model`: [ExecutionStatus]
 * itself lives in `ui/editor`, and `domain` may import nothing project-local
 * (`docs/architecture.md`, `ArchitectureGuardTest`) - a Console is a screen like any other, so its
 * transcript model belongs beside the other screens' models. An ordered transcript is a plain
 * `List<ConsoleEntry>`; nothing yet needs a wrapper around one.
 *
 * [id] and [executionCount] are named after `Cell.id`/`Cell.executionCount`
 * exactly: `KernelGateway.execute` correlates streamed events by an id the same way it does for a
 * cell, and the kernel's own count (`KernelEvent.ExecutionCount`) is what the prompt shows once it
 * arrives - not the entry's position in the list, which is what [ConsoleTranscript] showed before
 * a `ConsoleViewModel` existed to run anything.
 *
 * ```kotlin
 * ConsoleEntry("e1", "1 + 1", listOf(CellOutput.Stream(StreamName.STDOUT, "2\n")), ExecutionStatus.STOPPED_OK, 1)
 * ```
 */
data class ConsoleEntry(
  val id: String,
  val input: String,
  val outputs: List<CellOutput>,
  val status: ExecutionStatus,
  val executionCount: Int? = null,
  /** The `input()` this entry's run is blocked on, or null - a notebook cell's own rule, by entry. */
  val inputRequest: KernelInputRequest? = null,
  /** A `clear_output(wait=True)` not yet spent by an output - the notebook's `clearsPending`, by entry. */
  val clearPending: Boolean = false,
)
