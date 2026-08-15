package com.jupydroid.app.data.space

/**
 * A [KernelLifecycle] that hands out predictable kernel ids and records interrupts, so the
 * gateway's use of it can be asserted without a server.
 */
class FakeKernelLifecycle(
  private val kernelId: String = "kernel-1",
  private val serverSessionId: String = "server-session-1",
  private val failWith: Exception? = null,
) : KernelLifecycle {
  var startCount = 0
    private set
  val interrupted = mutableListOf<String>()
  val restarted = mutableListOf<String>()
  val shutDownSessions = mutableListOf<String>()

  override suspend fun start(documentPath: String): StartedKernelSession {
    failWith?.let { throw it }
    startCount++
    return StartedKernelSession(kernelId = kernelId, serverSessionId = serverSessionId)
  }

  override suspend fun interrupt(kernelId: String) {
    failWith?.let { throw it }
    interrupted += kernelId
  }

  override suspend fun restart(kernelId: String) {
    failWith?.let { throw it }
    restarted += kernelId
  }

  override suspend fun shutdownSession(serverSessionId: String) {
    failWith?.let { throw it }
    shutDownSessions += serverSessionId
  }
}
