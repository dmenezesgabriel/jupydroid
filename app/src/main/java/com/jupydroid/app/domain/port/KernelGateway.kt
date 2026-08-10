package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.flow.Flow

/**
 * What a kernel offers for the code under the cursor.
 *
 * [replacedStart] and [replacedEnd] are the half-open range of the code that a chosen match
 * replaces. Carrying them is not detail: for `np.ar` a kernel answers with whole names, and
 * without the range a caller has to guess how much of the line to drop - which is how a
 * completion turns into `np.np.arange`.
 */
data class CompletionResult(
  val matches: List<String>,
  val replacedStart: Int,
  val replacedEnd: Int,
) {
  companion object {
    val NONE = CompletionResult(emptyList(), 0, 0)
  }
}

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

  /**
   * Restarts the kernel behind this session: a fresh interpreter, and **every variable gone**.
   *
   * Distinct from [interrupt], which stops the work and keeps the state. The socket survives, so
   * the session stays valid - it is the process on the other end that is replaced.
   *
   * Outputs and execution counts are deliberately *not* the gateway's business to clear.
   * JupyterLab keeps them on a plain restart and offers "restart and clear" as a separate
   * command (`kernelmenu:restart-and-clear`), so a caller that wants them gone has to say so.
   */
  suspend fun restart(session: KernelSession)

  /**
   * [cursorOffset] is an absolute offset in characters, because that is what both ends of this
   * already speak: a text field's selection and the protocol's own `cursor_pos`. A line and
   * column in between would be two conversions that can only lose.
   */
  suspend fun complete(session: KernelSession, code: String, cursorOffset: Int): CompletionResult

  suspend fun disconnect(session: KernelSession)
}
