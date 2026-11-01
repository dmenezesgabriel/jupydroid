package com.jupydroid.extension.api

/**
 * Opens a file of the workspace in the host's own editor, for a panel whose settings live in one.
 *
 * VS Code's MCP command in an extension's shape: when the file is missing it is created with a
 * starting text, and then it is opened in the ordinary editor (vscode@3f3380c
 * `src/vs/workbench/contrib/mcp/browser/mcpCommands.ts`, `OpenMcpResourceCommand`). A file already
 * there is opened as it is.
 *
 * The host opens it as its explorer would, so a file whose suffix a [DocumentContribution] claims
 * opens in that contribution's tab, revealed where it is already open.
 *
 * ```
 * val open = { host.fileOpener.open(".jupyter/mcp_settings.json", "{\"mcp_servers\": []}") }
 * Button(onClick = open) { Text("MCP servers") }
 * ```
 */
public fun interface WorkspaceFileOpener {
  /** Opens [path], relative to the workspace root, writing [initialText] to it first when nothing is there. */
  public fun open(path: String, initialText: String)
}
