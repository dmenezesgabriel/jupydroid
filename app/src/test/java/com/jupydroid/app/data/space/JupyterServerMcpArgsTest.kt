package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_IDLE_SHUTDOWN_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agent reaches the server's MCP port only through what the command line tells both halves: the
 * port jupyter-server-mcp binds (`MCPExtensionApp.mcp_port`, `MCPServer.host`) and the server entry
 * the persona manager hands the agent (`PersonaManager.builtin_mcp_servers`, whose default carries
 * no header - `usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_manager.py` in
 * the shipped rootfs). Asserted as arguments, as `JupyterServerLifecycleArgsTest` does: a real
 * server is a tablet gate.
 */
class JupyterServerMcpArgsTest {
  private val args = jupyterServerArgs(
    rootDir = "/home/jupydroid",
    token = "s3cret",
    idleShutdownMinutes = DEFAULT_IDLE_SHUTDOWN_MINUTES,
    mcpPort = 41234,
  )

  @Test
  fun theMcpServerBindsThePortItWasHandedOnLoopbackOnly() {
    assertTrue(args.toString(), "--MCPExtensionApp.mcp_port=41234" in args)
    assertTrue(args.toString(), "--MCPServer.host=127.0.0.1" in args)
  }

  @Test
  fun theAgentIsHandedThatPortsUrlAndTheServersTokenAsABearer() {
    val expected = "--PersonaManager.builtin_mcp_servers=" +
      "[{'type': 'http', 'name': 'Jupyter MCP Server', 'url': 'http://127.0.0.1:41234/mcp', " +
      "'headers': [{'name': 'Authorization', 'value': 'Bearer s3cret'}]}]"

    assertEquals(expected, args.single { it.startsWith("--PersonaManager.builtin_mcp_servers=") })
  }
}
