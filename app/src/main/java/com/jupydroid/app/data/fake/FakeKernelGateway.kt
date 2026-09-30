package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.CompletionMatch
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stub [KernelGateway] echoing the code back; `BaseSpaceKernelGateway` is the real one.
 *
 * It registers what it mints into [registry], so the Sessions and Tabs panel lists and shuts
 * down sessions on a device with no Space exactly as on one with it - which is also what
 * keeps the emulator's "run → row appears → shut down → No Kernel" loop verifiable.
 */
class FakeKernelGateway(private val registry: KernelSessionRegistry = KernelSessionRegistry()) : KernelGateway {
  /** Minted per connect, because a real server's session ids never collide - not even on reuse. */
  private val issuedCount = AtomicInteger()

  /** The `kernel` field of each [connect], so a preference's path through the call is assertable. */
  val connectsAskedFor = mutableListOf<KernelChoice?>()

  /** What [changeKernel] was asked for, in order - the picker's answer reaching the gateway. */
  val kernelChanges = mutableListOf<KernelChoice>()

  override suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice?): KernelSession {
    connectsAskedFor += kernel
    val serial = issuedCount.incrementAndGet()
    return KernelSession(
      id = "fake-${space.id}-$serial",
      spaceId = space.id,
      kernelId = when (kernel) {
        is KernelChoice.Running -> kernel.kernelId
        else -> "fake-kernel-$serial"
      },
      kernelName = when (kernel) {
        is KernelChoice.Spec -> kernel.name
        is KernelChoice.Running -> kernel.kernelId
        null -> space.kernels.firstOrNull()?.name ?: "python3"
      },
      // A server session id shaped like the server's, so a caller that builds a URL from it
      // builds a plausible one. There is no server here to file it with.
      serverSessionId = "fake-session-${space.id}-$serial",
    ).also { registry.register(it, documentPath) }
  }

  /**
   * The fake's [changeKernel]: `Spec` mints a fresh kernel id the way `PATCH api/sessions` would
   * start one; `Running` binds the id it was handed. The rebind is announced on the shared
   * [registry] - there is no `BaseSpaceKernelGateway` above this fake on a device with no Space,
   * so the announcement is its own job here.
   */
  override suspend fun changeKernel(session: KernelSession, kernel: KernelChoice): KernelSession {
    kernelChanges += kernel
    val rebound = when (kernel) {
      is KernelChoice.Spec -> session.copy(
        kernelId = "fake-kernel-${issuedCount.incrementAndGet()}",
        kernelName = kernel.name,
      )
      is KernelChoice.Running -> session.copy(kernelId = kernel.kernelId, kernelName = kernel.kernelId)
    }
    registry.rebind(rebound)
    return rebound
  }

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
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
  override suspend fun complete(session: KernelSession, code: String, cursorOffset: Int): CompletionResult {
    val start = identifierStartBefore(code, cursorOffset)
    val prefix = code.substring(start, cursorOffset)
    return CompletionResult(
      matches = if (prefix.isEmpty()) {
        emptyList()
      } else {
        BUILTINS.filter { it.startsWith(prefix) }.map { CompletionMatch(it, it) }
      },
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

  override suspend fun executionHistory(session: KernelSession, entries: Int): List<String> = executed.takeLast(entries)

  override suspend fun disconnect(session: KernelSession) = Unit

  /** Which sessions were shut down, so a test can tell "the command ran" from "it did nothing". */
  val shutDownSessions = mutableListOf<KernelSession>()

  override suspend fun shutdown(session: KernelSession) {
    shutDownSessions += session
    registry.end(session.serverSessionId)
  }

  private fun identifierStartBefore(code: String, cursorOffset: Int): Int {
    var start = cursorOffset.coerceIn(0, code.length)
    while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] == '_')) start--
    return start
  }

  companion object {
    private val BUILTINS = listOf("print", "range", "len", "sum", "sorted", "int", "input", "isinstance")

    /**
     * The session [connect] mints for `spaceId` on its `sessionNumber`-th call, so a fixture
     * that must equal it (a [com.jupydroid.app.data.fake.FakeKernelDebugger.emit] key) reads the
     * scheme instead of restating literals that can drift from it.
     */
    fun mints(sessionNumber: Int, spaceId: String = "base"): KernelSession = KernelSession(
      id = "fake-$spaceId-$sessionNumber",
      spaceId = spaceId,
      kernelId = "fake-kernel-$sessionNumber",
      kernelName = "python3",
      serverSessionId = "fake-session-$spaceId-$sessionNumber",
    )
  }
}
