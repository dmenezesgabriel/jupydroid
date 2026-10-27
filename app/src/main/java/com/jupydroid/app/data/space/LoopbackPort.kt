package com.jupydroid.app.data.space

import java.net.InetAddress
import java.net.ServerSocket

/**
 * A loopback port the OS reports free now, closed again so a server can bind it next.
 *
 * Bind 0, read, close, pass: how jupyter_client picks kernel ports
 * (`usr/lib/python3.12/site-packages/jupyter_client/connect.py` `write_connection_file`). Another
 * process may take the port in between. For the MCP server that fails safe: jupyter_server 2.20.0's
 * `ExtensionManager.start_extension` logs the failed bind and serves on without tools, and the
 * server's token keeps one Space's agent out of a server that is not its own.
 *
 * ```
 * jupyterServerArgs(rootDir, token, idleShutdownMinutes, mcpPort = freeLoopbackPort())
 * ```
 */
fun freeLoopbackPort(): Int = ServerSocket(0, 0, InetAddress.getByName(LOOPBACK_HOST)).use { it.localPort }

/** The one address every Space server binds, the Jupyter server's and its MCP server's alike. */
internal const val LOOPBACK_HOST = "127.0.0.1"
