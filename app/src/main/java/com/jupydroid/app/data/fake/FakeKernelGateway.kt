package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Stub [KernelGateway] echoing the code back; replaced by the real one in Step 10. */
class FakeKernelGateway : KernelGateway {
  override suspend fun connect(space: Space, documentPath: String): KernelSession =
    KernelSession(
      id = "fake-${space.id}",
      spaceId = space.id,
      kernelId = space.kernels.firstOrNull()?.id ?: "python3",
      // A server session id shaped like the server's, so a caller that builds a URL from it
      // builds a plausible one. There is no server here to file it with.
      serverSessionId = "fake-session-${space.id}",
    )

  override suspend fun execute(
    session: KernelSession,
    cellId: String,
    code: String,
  ): Flow<KernelEvent> =
    flow {
      // Recorded from inside the flow rather than from `execute`, because that is when the cell
      // actually runs: this one is cold, so a second collection is a second execution, and a real
      // kernel records once per `execute_request` it receives.
      executed += code
      emit(KernelEvent.ExecutionCount(cellId, 1))
      emit(KernelEvent.Output(cellId, CellOutput.Stream(StreamName.STDOUT, code)))
      emit(KernelEvent.Idle)
    }

  override suspend fun interrupt(session: KernelSession) = Unit

  /** Nothing to restart: there is no process, and the echo has no state to lose. */
  override suspend fun restart(session: KernelSession) = Unit

  /**
   * Answers from a handful of builtins rather than from nothing, because this is what the
   * editor's completion popup is built and tested against until a Space is running.
   */
  override suspend fun complete(
    session: KernelSession,
    code: String,
    cursorOffset: Int,
  ): CompletionResult {
    val start = identifierStartBefore(code, cursorOffset)
    val prefix = code.substring(start, cursorOffset)
    return CompletionResult(
      matches = if (prefix.isEmpty()) emptyList() else BUILTINS.filter { it.startsWith(prefix) },
      replacedStart = start,
      replacedEnd = cursorOffset,
    )
  }

  /**
   * What this gateway has been asked to run, oldest first - its honest answer to the same
   * question a real kernel answers out of IPython's sqlite history.
   *
   * Narrower than the real one in the way that matters and cannot be helped: a real kernel
   * remembers sittings this process was never running for, so a device with no Space shows fewer
   * entries rather than none. What both agree on is the rule the two history commands are built
   * on - what was executed can be stepped back to.
   */
  private val executed = mutableListOf<String>()

  override suspend fun executionHistory(session: KernelSession, entries: Int): List<String> =
    executed.takeLast(entries)

  override suspend fun disconnect(session: KernelSession) = Unit

  /** Which sessions were shut down, so a test can tell "the command ran" from "it did nothing". */
  val shutDownSessions = mutableListOf<KernelSession>()

  override suspend fun shutdown(session: KernelSession) {
    shutDownSessions += session
  }

  private fun identifierStartBefore(code: String, cursorOffset: Int): Int {
    var start = cursorOffset.coerceIn(0, code.length)
    while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] == '_')) start--
    return start
  }

  private companion object {
    val BUILTINS = listOf("print", "range", "len", "sum", "sorted", "int", "input", "isinstance")
  }
}
