package com.jupydroid.extensions.ai.domain

/**
 * The file jupyter-ai reads the user's MCP servers from, and hands to the agent each time a chat's
 * session starts (`get_mcp_settings`,
 * `usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_manager.py` in the shipped rootfs).
 *
 * ```
 * host.fileOpener.open(McpSettingsFile.PATH, McpSettingsFile.EMPTY)
 * ```
 */
object McpSettingsFile {
  /**
   * Relative to the workspace root. jupyter-ai looks for `.jupyter` going up from the chat's own
   * folder (`find_dot_dir`, `usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/directories.py`), and
   * every chat is at the root.
   */
  const val PATH = ".jupyter/mcp_settings.json"

  /** A new file: no servers, under the one key `McpSettings` requires (`mcp_server_models.py`). */
  const val EMPTY = "{\n  \"mcp_servers\": []\n}\n"
}
