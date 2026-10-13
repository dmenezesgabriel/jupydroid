package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.SessionType

/**
 * A [KernelLifecycle] that hands out predictable kernel ids and records interrupts, so the
 * gateway's use of it can be asserted without a server.
 */
class FakeKernelLifecycle(
  private val kernelId: String = "kernel-1",
  private val kernelName: String = "python3",
  private val serverSessionId: String = "server-session-1",
  private val specs: KernelSpecModels = KernelSpecModels(
    default = "python3",
    specs = listOf(KernelSpec(name = "python3", displayName = "Python 3", language = "python")),
  ),
  private val failWith: Exception? = null,
) : KernelLifecycle {
  var startCount = 0
    private set
  val interrupted = mutableListOf<String>()
  val restarted = mutableListOf<String>()
  val shutDownSessions = mutableListOf<String>()
  val kernelChanges = mutableListOf<Pair<String, KernelChoice>>()

  /** The `kernel` field of each [start], so a preference's path through the gateway is assertable. */
  val startedWith = mutableListOf<KernelChoice?>()

  /**
   * What [sessions] answers - the models [start] minted, keyed by session id so a reattach
   * returns one row the way the server's `get_session(path)` would, and [shutdownSession]'s
   * removal is the DELETE it stands for.
   */
  private val issued = LinkedHashMap<String, KernelSessionModel>()
  private var mintedKernels = 0

  override suspend fun start(
    documentPath: String,
    kernel: KernelChoice?,
    sessionType: SessionType?,
  ): StartedKernelSession {
    failWith?.let { throw it }
    startCount++
    startedWith += kernel
    val started = kernel?.let { startedKernel(serverSessionId, it) }
      ?: StartedKernelSession(kernelId = kernelId, kernelName = kernelName, serverSessionId = serverSessionId)
    issued[serverSessionId] = sessionModel(documentPath, started, sessionType ?: SessionType.forPath(documentPath))
    return started
  }

  override suspend fun changeKernel(serverSessionId: String, kernel: KernelChoice): StartedKernelSession {
    failWith?.let { throw it }
    kernelChanges += serverSessionId to kernel
    val changed = startedKernel(serverSessionId, kernel)
    issued[serverSessionId] = issued.getValue(serverSessionId).let {
      it.copy(kernelId = changed.kernelId, kernelName = changed.kernelName)
    }
    return changed
  }

  /** What [setPath] was asked, in order - a rename reaching the server's session. */
  val movedSessions = mutableListOf<Pair<String, String>>()

  override suspend fun setPath(serverSessionId: String, documentPath: String) {
    failWith?.let { throw it }
    movedSessions += serverSessionId to documentPath
    issued[serverSessionId] = issued.getValue(serverSessionId).let {
      it.copy(path = documentPath, name = documentPath.substringAfterLast('/'))
    }
  }

  /** What the server would mint for [kernel]: a fresh id for a spec, the given id for a running one. */
  private fun startedKernel(serverSessionId: String, kernel: KernelChoice): StartedKernelSession = when (kernel) {
    is KernelChoice.Spec ->
      StartedKernelSession("minted-kernel-${++mintedKernels}", kernel.name, serverSessionId)
    is KernelChoice.Running ->
      StartedKernelSession(kernel.kernelId, kernel.kernelId, serverSessionId)
  }

  private fun sessionModel(documentPath: String, started: StartedKernelSession, type: SessionType) = KernelSessionModel(
    serverSessionId = started.serverSessionId,
    kernelId = started.kernelId,
    kernelName = started.kernelName,
    path = documentPath,
    name = documentPath.substringAfterLast('/'),
    type = type.wire,
  )

  override suspend fun startBareKernel(): BareKernel {
    failWith?.let { throw it }
    return BareKernel(kernelId, kernelName)
  }

  override suspend fun sessions(): List<KernelSessionModel> {
    failWith?.let { throw it }
    return issued.values.toList()
  }

  override suspend fun kernelSpecs(): KernelSpecModels {
    failWith?.let { throw it }
    return specs
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
