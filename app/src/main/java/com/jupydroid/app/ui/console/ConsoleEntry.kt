package com.jupydroid.app.ui.console

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.ui.editor.ExecutionStatus

/**
 * One submitted input in the Console's transcript, and what the kernel did with it - P6b-2.
 *
 * Reuses [CellOutput]/[ExecutionStatus] exactly as the notebook's own cells do, since
 * `KernelGateway.execute` already produces both and a Console entry needs nothing more than a
 * notebook cell's run does. Lives in `ui/console` rather than `domain/model`: [ExecutionStatus]
 * itself lives in `ui/editor`, and `domain` may import nothing project-local
 * (`docs/architecture.md`, `ArchitectureGuardTest`) - a Console is a screen like any other, so its
 * transcript model belongs beside the other screens' models. An ordered transcript is a plain
 * `List<ConsoleEntry>`; nothing yet needs a wrapper around one.
 *
 * ```kotlin
 * ConsoleEntry("1 + 1", listOf(CellOutput.Stream(StreamName.STDOUT, "2\n")), ExecutionStatus.STOPPED_OK)
 * ```
 */
data class ConsoleEntry(val input: String, val outputs: List<CellOutput>, val status: ExecutionStatus)
