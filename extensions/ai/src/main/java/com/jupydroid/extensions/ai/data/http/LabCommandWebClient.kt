package com.jupydroid.extensions.ai.data.http

import java.util.UUID

/**
 * This app as one jupyterlab-commands-toolkit web client, shared by every chat's events socket: each
 * socket receives every command, and the server keeps the first result it is sent and ignores the rest
 * (jupyterlab-commands-toolkit@7c87c5d `tools.py`, `handle_command_result`). So a command that two
 * sockets both ran would have changed the notebook twice. Only the first socket to [take] a request runs
 * it.
 *
 * ```
 * val webClient = LabCommandWebClient()  // once, in AiExtension
 * OkHttpAgentChat(server, labCommands = webClient, notebookCells = host.notebookCells)
 * ```
 *
 * @param id what the acks name as `client_id`. JupyterLab makes a new one per page load (`src/index.ts`),
 *   and this is made once per `AiExtension`, which the app builds once per process.
 */
class LabCommandWebClient(val id: String = UUID.randomUUID().toString()) {
  /** The requests already taken, newest last; old ones fall off, since the server waits on none for long. */
  private val taken = object : LinkedHashMap<String, Unit>() {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>): Boolean = size > REMEMBERED
  }

  /**
   * Whether this socket should run [command]: one for this client or for every client, which no other
   * socket has taken. A command with no request id is never taken, since without an id two sockets could
   * not tell they had both run it.
   */
  internal fun take(command: LabCommand): Boolean {
    val requestId = command.requestId
    val forUs = command.clientId == null || command.clientId == id
    return requestId != null && forUs && synchronized(taken) { taken.put(requestId, Unit) == null }
  }

  private companion object {
    /** Far more than one turn's commands: `insert_cell` sends two. */
    const val REMEMBERED = 256
  }
}
