package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSessions
import kotlinx.coroutines.flow.SharedFlow

/**
 * The no-Space answer to "what sessions is the server holding": whatever the fake gateway minted
 * into the same [registry].
 *
 * Sharing the registry is the fidelity, not a convenience - it is what makes a per-row shutdown
 * on the emulator reach an open notebook the way `session.disposed` does against a real server,
 * so the panel's whole loop (run → row → shut down → No Kernel) is testable there.
 *
 * ```
 * val registry = KernelSessionRegistry()
 * FakeKernelGateway(registry) to FakeKernelSessions(registry)
 * ```
 */
class FakeKernelSessions(private val registry: KernelSessionRegistry = KernelSessionRegistry()) : KernelSessions {
  override val ended: SharedFlow<String> = registry.ended

  /** The fake gateway rebinds through the shared registry, exactly like the real one's. */
  override val rebound: SharedFlow<KernelSession> = registry.rebound

  override suspend fun running(): List<KernelSessionModel> = registry.all.map { tracked ->
    KernelSessionModel(
      serverSessionId = tracked.kernelSession.serverSessionId,
      kernelId = tracked.kernelSession.kernelId,
      kernelName = tracked.kernelSession.kernelName,
      path = tracked.documentPath,
      name = tracked.documentPath.substringAfterLast('/'),
      type = tracked.type.wire,
    )
  }

  override suspend fun shutdown(serverSessionId: String) {
    registry.end(serverSessionId)
  }
}
