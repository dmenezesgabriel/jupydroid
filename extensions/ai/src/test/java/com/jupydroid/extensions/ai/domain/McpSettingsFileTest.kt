package com.jupydroid.extensions.ai.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where jupyter-ai looks for the MCP servers it hands an agent, and what a new file starts as. */
class McpSettingsFileTest {
  /** Chats sit at the workspace root, and `find_dot_dir` looks for `.jupyter` upward from a chat's own folder. */
  @Test
  fun theFileIsInTheWorkspaceRootsDotJupyter() {
    assertEquals(".jupyter/mcp_settings.json", McpSettingsFile.PATH)
  }

  /** `McpSettings` requires `mcp_servers`, so a file without it would fail validation and give the agent nothing. */
  @Test
  fun aNewFileListsNoServersUnderTheKeyJupyterAiReads() {
    val settings = Json.parseToJsonElement(McpSettingsFile.EMPTY).jsonObject

    assertEquals(setOf("mcp_servers"), settings.keys)
    assertEquals(JsonArray(emptyList()), settings.getValue("mcp_servers"))
  }
}
