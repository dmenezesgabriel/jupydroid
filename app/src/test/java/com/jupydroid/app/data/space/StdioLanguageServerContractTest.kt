package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageServerPortContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * The real [StdioLanguageServer] held to the port contract over a [ScriptedLanguageServer] -
 * actual pipes, actual framing, actual process lifecycle; only the ELF is absent.
 */
class StdioLanguageServerContractTest : LanguageServerPortContract() {
  private var spawned: ScriptedLanguageServer? = null

  override fun languageServer(): LanguageServerPort = StdioLanguageServer(
    installSpace = { InstalledSpace(File("/dev/null"), 7, false) },
    spawn = { ScriptedLanguageServer().also { spawned = it } },
  )

  /**
   * A real `publishDiagnostics` frame down the wire - pushed while the scripted server sits
   * idle, which is the only time a test-thread write cannot interleave with its own replies.
   */
  override suspend fun pushDiagnostics(uri: String) {
    spawned?.pushNotification(publishDiagnosticsNotification(uri))
      ?: error("pushDiagnostics before any call started a server")
  }
}

/** The `textDocument/publishDiagnostics` shape pylsp emits after a `didOpen`/`didChange`. */
internal fun publishDiagnosticsNotification(uri: String): JsonObject = Json.parseToJsonElement(
  """{"jsonrpc":"2.0","method":"textDocument/publishDiagnostics","params":""" +
    """{"uri":"$uri","diagnostics":[{"range":{"start":{"line":0,"character":6},""" +
    """"end":{"line":0,"character":7}},"severity":1,"code":"F821","source":"pyflakes",""" +
    """"message":"undefined name 'x'"}]}}""",
).jsonObject
