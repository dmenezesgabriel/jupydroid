package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.KernelSessionModel

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

  /**
   * What [sessions] answers - the models [start] minted, keyed by session id so a reattach
   * returns one row the way the server's `get_session(path)` would, and [shutdownSession]'s
   * removal is the DELETE it stands for.
   */
  private val issued = LinkedHashMap<String, KernelSessionModel>()

  override suspend fun start(documentPath: String): StartedKernelSession {
    failWith?.let { throw it }
    startCount++
    issued[serverSessionId] = KernelSessionModel(
      serverSessionId = serverSessionId,
      kernelId = kernelId,
      kernelName = kernelId,
      path = documentPath,
      name = documentPath.substringAfterLast('/'),
      type = if (documentPath.endsWith(".ipynb")) "notebook" else "file",
    )
    return StartedKernelSession(kernelId = kernelId, serverSessionId = serverSessionId)
  }

  override suspend fun sessions(): List<KernelSessionModel> {
    failWith?.let { throw it }
    return issued.values.toList()
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
    issued.remove(serverSessionId)
  }
}
