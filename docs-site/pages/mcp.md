# MCP servers

**MCP servers** is the button on the [Chat](chat.md) panel — visible with or without a chat
open — that opens `.jupyter/mcp_settings.json`, the file that tells each chat's agent which
Model Context Protocol servers it may call. The `.jupyter` folder is hidden from the
[Files](files.md) explorer, so this button is the way in.

![The Chat panel on a tablet with no chat open, the MCP servers button beside New chat](images/mcp-chat-list-light.png#only-light)
![The Chat panel on a tablet with no chat open, the MCP servers button beside New chat](images/mcp-chat-list-dark.png#only-dark)

## The file

With no file yet, **MCP servers** writes one holding an empty list and opens it in a tab:

```json
{
  "mcp_servers": []
}
```

![The mcp_settings.json tab on a tablet showing the empty mcp_servers list](images/mcp-settings-empty-light.png#only-light)
![The mcp_settings.json tab on a tablet showing the empty mcp_servers list](images/mcp-settings-empty-dark.png#only-dark)

Each entry names a server, the command that starts it and its arguments:

```json
{"mcp_servers":[{"name":"quince","command":"python3","args":["/home/jupydroid/quince_mcp.py"]}]}
```

![The mcp_settings.json tab on a tablet after the quince server was typed in](images/mcp-settings-server-light.png#only-light)
![The mcp_settings.json tab on a tablet after the quince server was typed in](images/mcp-settings-server-dark.png#only-dark)

The agent reads the file when a chat's session starts, so a server added here is there for the
next question a chat asks — an MCP tool the agent calls appears in the transcript like any other
tool call, under the server's name.

For the chats themselves see [Chat](chat.md); for the harnesses that host the agents see
[Spaces](spaces.md).
