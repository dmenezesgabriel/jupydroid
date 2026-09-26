package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageCapabilities
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.PublishDiagnostics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * `pylsp` - or any LSP server that speaks JSON-RPC over stdio - as a [LanguageServerPort].
 *
 * One process per [Space], started lazily on first use and stopped with it: [installSpace]
 * and [spawn] are the same provisioning seam [BaseSpaceServer] takes, because a language
 * server has no business running on a device that cannot unpack the rootfs, and no business
 * starting at app launch - the port's own rule, and `completionsAt`'s: typing alone boots no
 * Python runtime.
 *
 * The document protocol mirrors `connection.ts`: `didOpen`s before initialization has
 * finished are held by the startup lock and flush once the handshake lands; a change to a
 * never-opened uri opens it (`_sendChange`); changes carry the document's whole text and a
 * version that only ever counts up; shutdown is `shutdown` then `exit`. The frames' own
 * shapes live in [LspWire].
 */
class StdioLanguageServer(
  private val installSpace: suspend () -> InstalledSpace,
  private val spawn: suspend (InstalledSpace) -> Process,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val requestTimeout: Duration = 30.seconds,
  private val shutdownGrace: Duration = 15.seconds,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : LanguageServerPort {
  private val startup = Mutex()
  private val servers = mutableMapOf<String, RunningServer>()
  private val serverScope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private val _diagnostics = MutableSharedFlow<PublishDiagnostics>(extraBufferCapacity = 64)

  override val diagnostics: SharedFlow<PublishDiagnostics> = _diagnostics

  /** A live server plus the document state only it may see - versions live with the process. */
  private inner class RunningServer(
    val process: Process,
    val channel: JsonRpcChannel,
    val capabilities: LanguageCapabilities,
    val documents: MutableMap<String, Int>,
  ) {
    /** Serializes didOpen/didChange/didClose so a version never goes out twice. */
    val documentLock = Mutex()

    /**
     * `textDocument/publishDiagnostics` off this server's channel onto the port's stream - one
     * job per server, so a stopped server's notifications die with it rather than emit forever.
     */
    val diagnosticsPump: Job = serverScope.launch {
      channel.notifications.collect { frame ->
        LspNotifications.publishDiagnosticsFrom(frame)?.let { _diagnostics.emit(it) }
      }
    }
  }

  override suspend fun capabilities(space: Space): LanguageCapabilities = serverFor(space).capabilities

  override suspend fun openDocument(space: Space, uri: String, languageId: String, text: String) {
    val server = serverFor(space)
    server.documentLock.withLock { openOn(server, uri, languageId, text) }
  }

  override suspend fun changeDocument(space: Space, uri: String, text: String) {
    val server = serverFor(space)
    server.documentLock.withLock {
      if (uri !in server.documents) {
        // `_sendChange`'s rule: a sync can never be the first frame a document hears.
        // pylsp speaks Python only, which is the language id an auto-open carries.
        openOn(server, uri, "python", text)
        return@withLock
      }
      // The whole-text change at a version that only counts up - `sendFullTextChange`'s shape.
      val version = (server.documents[uri] ?: 0) + 1
      server.documents[uri] = version
      server.channel.notify("textDocument/didChange", LspWire.didChangeParams(uri, version, text))
    }
  }

  override suspend fun closeDocument(space: Space, uri: String) {
    val server = serverFor(space)
    server.documentLock.withLock {
      if (server.documents.remove(uri) == null) return@withLock
      server.channel.notify("textDocument/didClose", LspWire.didCloseParams(uri))
    }
  }

  override suspend fun completion(space: Space, uri: String, position: LanguagePosition): List<LanguageCompletion> =
    positions.completion(space, uri, position)

  /** @see LanguageServerPort.hover */
  override suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover? =
    positions.hover(space, uri, position)

  /**
   * The `TextDocumentPositionParams` asks - completion and hover share one wire shape:
   * params that name a document and a point in it, one request timeout, replies decoded at
   * the wire objects. An inner class rather than loose methods because it uses [serverFor]
   * and [requestTimeout], and a holder rather than the class's own scope so the
   * position-ask shape stays greppable in one place (P5e-4's signature help lands here too).
   */
  private val positions = PositionRequests()

  private inner class PositionRequests {
    suspend fun completion(space: Space, uri: String, position: LanguagePosition): List<LanguageCompletion> =
      LspWire.completionItems(request(space, "textDocument/completion", uri, position)["result"])

    suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover? =
      LspMarkup.hoverFrom(request(space, "textDocument/hover", uri, position)["result"])

    /** One `TextDocumentPositionParams` request - the params every point-in-document method shares. */
    private suspend fun request(space: Space, method: String, uri: String, position: LanguagePosition): JsonObject =
      try {
        serverFor(space)
          .channel
          .request(method, LspWire.positionParams(uri, position), requestTimeout)
      } catch (e: TimeoutCancellationException) {
        throw IllegalStateException("$method returned no reply within $requestTimeout", e)
      }
  }

  override suspend fun stop(space: Space) {
    val server = startup.withLock { servers.remove(space.id) } ?: return
    log.reached("lsp.stop", mapOf("space" to space.id))
    withContext(ioDispatcher + NonCancellable) {
      withTimeoutOrNull(shutdownGrace) {
        runCatching { server.channel.request("shutdown", buildJsonObject {}) }
        runCatching { server.channel.notify("exit", buildJsonObject {}) }
        server.process.waitFor(shutdownGrace.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      }
      server.diagnosticsPump.cancel()
      server.channel.close()
      if (server.process.isAlive) server.process.destroy()
    }
  }

  /**
   * The running server for [space], starting one if needed - inside [startup], so a call made
   * while the handshake is still in flight queues behind it exactly like the reference's
   * `sendOpenWhenReady`, and a dead process respawns on the next call rather than in the
   * background.
   */
  private suspend fun serverFor(space: Space): RunningServer = startup.withLock {
    servers[space.id]?.takeIf { it.process.isAlive }?.let { return@withLock it }
    // A dead server's channel and process are already gone; close/destroy is cheap and not
    // worth a graceful shutdown - `exit` only matters to a process still there to hear it.
    servers.remove(space.id)?.let {
      it.diagnosticsPump.cancel()
      it.channel.close()
      it.process.destroy()
    }
    startServer(space)
  }

  /**
   * Spawns, handshakes and registers a fresh server for [space] - called under [startup]'s
   * lock, so at most one handshake per Space is ever in flight. A failed handshake leaves no
   * half-open channel and no interpreter running.
   */
  private suspend fun startServer(space: Space): RunningServer {
    val installed = installSpace()
    val process = spawn(installed)
    log.reached("lsp.start", mapOf("space" to space.id, "rootfs" to "${installed.version}"))
    val channel = JsonRpcChannel(process.inputStream, process.outputStream, log)
    return runCatching {
      val reply = channel.request("initialize", LspWire.initializeParams(), requestTimeout)
      RunningServer(process, channel, LspWire.capabilitiesFrom(reply["result"]), mutableMapOf()).also {
        channel.notify("initialized", buildJsonObject {})
        drainStderr(process.errorStream)
        servers[space.id] = it
      }
    }.getOrElse {
      channel.close()
      process.destroy()
      throw it
    }
  }

  /**
   * stderr drains on its own daemon - a server's log may never occupy the protocol stream,
   * and an undrained pipe buffer is a deadlock a `window/logMessage` flood could cause.
   */
  private fun drainStderr(stderr: InputStream) = thread(isDaemon = true, name = "lsp-stderr") {
    BufferedReader(stderr.reader()).forEachLine { log.reached("lsp.stderr", mapOf("line" to it)) }
  }

  private suspend fun openOn(server: RunningServer, uri: String, languageId: String, text: String) {
    if (uri in server.documents) return
    server.documents[uri] = 0
    server.channel.notify("textDocument/didOpen", LspWire.didOpenParams(uri, languageId, text))
  }

  /** The pylsp console script a rootfs [baseDir] carries at `usr/local/bin`. */
  internal companion object {
    fun pylspIn(baseDir: File): File = File(baseDir, "usr/local/bin/pylsp")
  }
}
