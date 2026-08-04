package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.flow.Flow

data class CompletionResult(val matches: List<String>)

/** A live kernel that one notebook (or the Console REPL) is executing against. */
data class KernelSession(
  val id: String,
  val spaceId: String,
  val kernelId: String,
)

/** Events streamed from [KernelGateway.execute]; the UI drives Running/Idle and outputs from these. */
sealed interface KernelEvent {
  data class ExecutionCount(val cellId: String, val count: Int) : KernelEvent

  data class Output(val cellId: String, val output: CellOutput) : KernelEvent

  data object Idle : KernelEvent
}

/** Talks to a real jupyter_server kernel; swapped behind the port in Step 10. */
interface KernelGateway {
  suspend fun connect(space: Space): KernelSession

  suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent>

  suspend fun interrupt(session: KernelSession)

  suspend fun complete(
    session: KernelSession,
    code: String,
    cursorLine: Int,
    cursorColumn: Int,
  ): CompletionResult

  suspend fun disconnect(session: KernelSession)
}
