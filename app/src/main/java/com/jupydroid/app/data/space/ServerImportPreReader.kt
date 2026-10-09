package com.jupydroid.app.data.space

import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import kotlin.time.TimeSource

/**
 * Runs `import jupyter_server.serverapp` once in each installed Space and throws the process away,
 * so the page cache holds the image's Python when **Run** starts the server (ADR-0081). From an
 * evicted cache that took the server's start 3.54 s -> 2.37 s on the tablet, holding nothing after.
 *
 * One Space at a time, in [spaceIds]' order - base first - so the reads do not compete. A Space is
 * skipped when it is not installed, or when its server is in [running] by the time its turn comes:
 * that server already did the read. Nothing here touches a server or a lock a start takes, so a
 * **Run** tapped meanwhile goes ahead beside the pre-read rather than waiting on it.
 *
 * A pre-read is an optimisation, never a reason for anything to fail: a failed one is logged and
 * the next Space is read. The start and end of each are logged, so a fast boot that followed a
 * pre-read is not mistaken for a fast server.
 *
 * ```
 * ServerImportPreReader(
 *   spaceIds = { spaceRepository.spaces().map { it.id } },
 *   isInstalled = { true },
 *   running = spaceServers.running,
 *   importServer = { id -> PythonRunner(spaceRuntime(context, spaceWithId(id))).run("-c", SERVER_IMPORT) },
 *   log = LogcatSpaceLifecycleLog,
 * ).preReadAll()
 * ```
 */
class ServerImportPreReader(
  private val spaceIds: suspend () -> List<String>,
  private val isInstalled: suspend (String) -> Boolean,
  private val running: StateFlow<Set<String>>,
  private val importServer: suspend (String) -> ProcessOutcome,
  private val log: SpaceLifecycleLog,
) {
  suspend fun preReadAll() {
    for (spaceId in spaceIds()) {
      if (isInstalled(spaceId) && spaceId !in running.value) preRead(spaceId)
    }
  }

  /** Only a process that cannot start is caught - [IOException], what [ProcessBuilder.start] throws. */
  private suspend fun preRead(spaceId: String) {
    log.reached(PRE_READ_STARTED, mapOf("space" to spaceId))
    val began = TimeSource.Monotonic.markNow()
    try {
      val exitCode = importServer(spaceId).exitCode
      val elapsedMs = began.elapsedNow().inWholeMilliseconds
      log.reached(PRE_READ_FINISHED, mapOf("space" to spaceId, "exitCode" to "$exitCode", "elapsedMs" to "$elapsedMs"))
    } catch (failure: IOException) {
      log.failed(PRE_READ_FAILED, failure)
    }
  }

  companion object {
    /** What is imported: the module the server's boot was measured to be (ADR-0081). */
    const val SERVER_IMPORT = "import jupyter_server.serverapp"

    const val PRE_READ_STARTED = "server.preread.started"
    const val PRE_READ_FINISHED = "server.preread.finished"
    const val PRE_READ_FAILED = "server.preread.failed"
  }
}
