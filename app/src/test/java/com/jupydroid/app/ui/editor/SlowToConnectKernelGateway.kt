package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SessionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/**
 * A kernel that takes its time coming up, which on a device is the ordinary case: installing the
 * Space, starting `jupyter_server` and starting the kernel is the slow part of a run, and the
 * tablet measured fourteen seconds of it.
 */
internal class SlowToConnectKernelGateway(private val fake: FakeKernelGateway = FakeKernelGateway()) :
  KernelGateway by fake {
  private val connectable = CompletableDeferred<Unit>()

  /** Every execute's source, in order - the record that proves a cell stopped during connect never arrived. */
  val executedSource = mutableListOf<String>()

  override suspend fun connect(
    space: Space,
    documentPath: String,
    kernel: KernelChoice?,
    sessionType: SessionType?,
  ): KernelSession {
    connectable.await()
    return KernelSession(
      id = "slow-${space.id}",
      spaceId = space.id,
      kernelId = "kernel-1",
      kernelName = "python3",
      serverSessionId = "server-session-1",
    )
  }

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> {
    executedSource += code
    return fake.execute(session, cellId, code)
  }

  fun letItConnect() = connectable.complete(Unit)
}
