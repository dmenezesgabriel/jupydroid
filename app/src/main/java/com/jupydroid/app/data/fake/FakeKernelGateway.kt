package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Environment
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Stub [KernelGateway] echoing the code back; replaced by the real one in Step 10. */
class FakeKernelGateway : KernelGateway {
  override suspend fun connect(environment: Environment): KernelSession =
    KernelSession(
      id = "fake-${environment.id}",
      environmentId = environment.id,
      kernelId = environment.kernels.firstOrNull()?.id ?: "python3",
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

  override suspend fun complete(
    session: KernelSession,
    code: String,
    cursorLine: Int,
    cursorColumn: Int,
  ): CompletionResult = CompletionResult(emptyList())

  override suspend fun disconnect(session: KernelSession) = Unit
}
