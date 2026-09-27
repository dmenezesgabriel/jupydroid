package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageCapabilities
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageLocation
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageSignatureHelp
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
  private val serverScope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private val _diagnostics = MutableSharedFlow<PublishDiagnostics>(extraBufferCapacity = 64)

  override val diagnostics: SharedFlow<PublishDiagnostics> = _diagnostics

  /** What one server was last told about a document - the text lets a sync skip a no-change. */
  private data class SyncedDocument(val version: Int, val text: String)

  /** A live server plus the document state only it may see - versions live with the process. */
  private inner class RunningServer(
    val process: Process,
    val channel: JsonRpcChannel,
    val capabilities: LanguageCapabilities,
    val documents: MutableMap<String, SyncedDocument>,
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

    /**
     * `didOpen` for a document this server has not heard of - called under [documentLock],
     * so a version never goes out twice. A change to a never-opened uri opens it, which is
     * the caller's check and this method's `in documents` guard together.
     */
    suspend fun open(uri: String, languageId: String, text: String) {
      if (uri in documents) return
      documents[uri] = SyncedDocument(version = 0, text = text)
      channel.notify("textDocument/didOpen", LspWire.didOpenParams(uri, languageId, text))
    }
  }

  override suspend fun capabilities(space: Space): LanguageCapabilities = pool.serverFor(space).capabilities

  override suspend fun openDocument(space: Space, uri: String, languageId: String, text: String) {
    val server = pool.serverFor(space)
    server.documentLock.withLock { server.open(uri, languageId, text) }
  }

  override suspend fun changeDocument(space: Space, uri: String, text: String) {
    val server = pool.serverFor(space)
    server.documentLock.withLock {
      val synced = server.documents[uri]
      if (synced == null) {
        // `_sendChange`'s rule: a sync can never be the first frame a document hears.
        // pylsp speaks Python only, which is the language id an auto-open carries.
        server.open(uri, "python", text)
        return@withLock
      }
      // Every ask syncs first, so this is the hot path: the text the diagnostics watch already
      // sent goes out again unchanged, and pylsp re-analyzes the document it already has.
      if (synced.text == text) return@withLock
      // The whole-text change at a version that only counts up - `sendFullTextChange`'s shape.
      val version = synced.version + 1
      server.documents[uri] = SyncedDocument(version, text)
      server.channel.notify("textDocument/didChange", LspWire.didChangeParams(uri, version, text))
    }
  }

  override suspend fun closeDocument(space: Space, uri: String) {
    val server = pool.serverFor(space)
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

  /** @see LanguageServerPort.signatureHelp */
  override suspend fun signatureHelp(space: Space, uri: String, position: LanguagePosition): LanguageSignatureHelp? =
    positions.signatureHelp(space, uri, position)

  /** @see LanguageServerPort.definition */
  override suspend fun definition(space: Space, uri: String, position: LanguagePosition): List<LanguageLocation> =
    positions.definition(space, uri, position)

  /** @see LanguageServerPort.rangeFormatting */
  override suspend fun rangeFormatting(
    space: Space,
    uri: String,
    range: LanguageRange,
    options: LanguageFormatOptions,
  ): List<LanguageEdit> = positions.rangeFormatting(space, uri, range, options)

  /**
   * The `TextDocumentPositionParams` asks - completion and hover share one wire shape:
   * params that name a document and a point in it, one request timeout, replies decoded at
   * the wire objects. An inner class rather than loose methods because it uses
   * [ServerPool.serverFor] and [requestTimeout], and a holder rather than the class's own scope so the
   * position-ask shape stays greppable in one place (P5e-4's signature help lands here too).
   */
  private val positions = PositionRequests()

  private inner class PositionRequests {
    suspend fun completion(space: Space, uri: String, position: LanguagePosition): List<LanguageCompletion> =
      LspCompletion.completionItems(request(pool.serverFor(space), "textDocument/completion", uri, position)["result"])

    /**
     * A server that never advertised `hoverProvider` is never asked - LSP's rule that an
     * undeclared capability is an unsupported one. The scripted default's `true` is pylsp's.
     */
    suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover? {
      val server = pool.serverFor(space)
      if (!server.capabilities.hoverProvider) return null
      return LspMarkup.hoverFrom(request(server, "textDocument/hover", uri, position)["result"])
    }

    /**
     * `hover`'s gate one method later: a server that never advertised `signatureHelpProvider`
     * is never asked. The scripted default advertises the object form pylsp itself replies.
     */
    suspend fun signatureHelp(space: Space, uri: String, position: LanguagePosition): LanguageSignatureHelp? {
      val server = pool.serverFor(space)
      if (!server.capabilities.signatureHelpProvider) return null
      return LspSignatureHelp.signatureHelpFrom(request(server, "textDocument/signatureHelp", uri, position)["result"])
    }

    /**
     * The same gate one method further: a server that never advertised `definitionProvider`
     * is never asked. An empty list is "nothing defines this" - the miss a go-to-definition
     * surfaces as such.
     */
    suspend fun definition(space: Space, uri: String, position: LanguagePosition): List<LanguageLocation> {
      val server = pool.serverFor(space)
      if (!server.capabilities.definitionProvider) return emptyList()
      log.reached(
        "lsp.ask",
        mapOf("method" to "definition", "uri" to uri, "at" to "${position.line}:${position.character}"),
      )
      val locations = LspDefinition.locationsFrom(request(server, "textDocument/definition", uri, position)["result"])
      log.reached(
        "lsp.answer",
        mapOf("method" to "definition", "found" to "${locations.size}", "uris" to locations.joinToString { it.uri }),
      )
      return locations
    }

    /**
     * The same gate one method further: `documentRangeFormattingProvider` absent is an ask
     * never sent. `options` is the params' required field - the editor's own indentation
     * setting, which the shipped pylsp's autopep8 plugin accepts and ignores (its config
     * comes from `pylsp.plugins.pycodestyle.*`, not this object); a spec-conforming server
     * would honour it, and the spec leaves no way to ask without it.
     */
    suspend fun rangeFormatting(
      space: Space,
      uri: String,
      range: LanguageRange,
      options: LanguageFormatOptions,
    ): List<LanguageEdit> {
      val server = pool.serverFor(space)
      if (!server.capabilities.documentRangeFormattingProvider) return emptyList()
      log.reached("lsp.ask", mapOf("method" to "rangeFormatting", "uri" to uri))
      val edits =
        LspFormatting.editsFrom(
          request(server, "textDocument/rangeFormatting", LspWire.rangeFormattingParams(uri, range, options))["result"],
        )
      log.reached("lsp.answer", mapOf("method" to "rangeFormatting", "edits" to "${edits.size}"))
      return edits
    }

    /** One `TextDocumentPositionParams` request - the params every point-in-document method shares. */
    private suspend fun request(
      server: RunningServer,
      method: String,
      uri: String,
      position: LanguagePosition,
    ): JsonObject = request(server, method, LspWire.positionParams(uri, position))

    private suspend fun request(server: RunningServer, method: String, params: JsonObject): JsonObject = try {
      server.channel.request(method, params, requestTimeout)
    } catch (e: TimeoutCancellationException) {
      throw IllegalStateException("$method returned no reply within $requestTimeout", e)
    }
  }

  override suspend fun stop(space: Space) {
    val server = pool.remove(space) ?: return
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
   * The running servers, one per [Space] - the spawn/handshake/register half of the lifecycle,
   * an inner class for the reason [PositionRequests] is one: it uses the class's own
   * [installSpace]/[spawn]/[requestTimeout], and a named holder keeps the "which process does a
   * Space have" logic greppable in one place.
   */
  private val pool = ServerPool()

  private inner class ServerPool {
    private val startup = Mutex()
    private val servers = mutableMapOf<String, RunningServer>()

    /**
     * The running server for [space], starting one if needed - inside [startup], so a call made
     * while the handshake is still in flight queues behind it exactly like the reference's
     * `sendOpenWhenReady`, and a dead process respawns on the next call rather than in the
     * background.
     */
    suspend fun serverFor(space: Space): RunningServer = startup.withLock {
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

    /** Out of the map without a shutdown - the caller owns the graceful `shutdown`/`exit` pair. */
    suspend fun remove(space: Space): RunningServer? = startup.withLock { servers.remove(space.id) }

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
          drainStderr(process.errorStream, log)
          servers[space.id] = it
        }
      }.getOrElse {
        channel.close()
        process.destroy()
        throw it
      }
    }
  }

  /** The pylsp console script a rootfs [baseDir] carries at `usr/local/bin`. */
  internal companion object {
    fun pylspIn(baseDir: File): File = File(baseDir, "usr/local/bin/pylsp")
  }
}

/**
 * stderr drains on its own daemon - a server's log may never occupy the protocol stream,
 * and an undrained pipe buffer is a deadlock a `window/logMessage` flood could cause.
 */
private fun drainStderr(stderr: InputStream, log: SpaceLifecycleLog) = thread(isDaemon = true, name = "lsp-stderr") {
  BufferedReader(stderr.reader()).forEachLine { log.reached("lsp.stderr", mapOf("line" to it)) }
}
