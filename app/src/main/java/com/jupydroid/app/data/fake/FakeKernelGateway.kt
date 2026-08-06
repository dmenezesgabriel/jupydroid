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
  override suspend fun connect(space: Space): KernelSession =
    KernelSession(
      id = "fake-${space.id}",
      spaceId = space.id,
      kernelId = space.kernels.firstOrNull()?.id ?: "python3",
    )

  override suspend fun execute(
    session: KernelSession,
    cellId: String,
    code: String,
  ): Flow<KernelEvent> =
    flow {
      emit(KernelEvent.ExecutionCount(cellId, 1))
      emit(KernelEvent.Output(cellId, CellOutput.Stream(StreamName.STDOUT, code)))
      emit(KernelEvent.Idle)
    }

  override suspend fun interrupt(session: KernelSession) = Unit

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

  override suspend fun disconnect(session: KernelSession) = Unit

  private fun identifierStartBefore(code: String, cursorOffset: Int): Int {
    var start = cursorOffset.coerceIn(0, code.length)
    while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] == '_')) start--
    return start
  }

  private companion object {
    val BUILTINS = listOf("print", "range", "len", "sum", "sorted", "int", "input", "isinstance")
  }
}
