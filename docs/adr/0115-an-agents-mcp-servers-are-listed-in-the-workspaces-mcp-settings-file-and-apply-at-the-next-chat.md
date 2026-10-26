# ADR-0115 — An agent's MCP servers are listed in the workspace's MCP settings file, and apply at the next chat

Status: accepted
Date: 2026-10-26

## Context

jupyter-ai already hands an agent MCP servers, and the image already ships that half. Each time a
chat's ACP session starts, `get_mcp_settings`
(`usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_manager.py` in the shipped
rootfs) reads `mcp_settings.json` from the nearest `.jupyter` going up from the chat's folder
(`find_dot_dir`, `jupyter_ai_persona_manager/directories.py`). Every chat is at the workspace root
([ADR-0111](0111-a-chat-is-a-chat-file-at-the-workspace-root-managed-through-the-contents-api.md)),
so the file is `<workspace>/.jupyter/mcp_settings.json`. Its schema is `McpSettings`
(`jupyter_ai_persona_manager/mcp_server_models.py`): one key, `mcp_servers`, a list of stdio servers
(`name`, `command`, `args`, `env`) and HTTP servers (`type: "http"`, `name`, `url`, `headers`). It is
read again for every session, with no cache. A file that does not parse is logged
(`Failed to read or parse MCP config`) and treated as absent. `builtin_mcp_servers` defaults to
`jupyter_server_mcp`'s HTTP server only when that package imports, and the image does not ship it,
so the user's file is the only source. `_get_mcp_servers`
(`usr/lib/python3.12/site-packages/jupyter_ai_acp_client/default_acp_client.py`) hands the list to
the agent on `session/new` and `session/load`, and an HTTP server only when the agent advertises
`mcp_capabilities.http`.

The app gave the user no way to reach the file. The explorer hides dot-entries
(`app/src/main/java/com/jupydroid/app/data/disk/WorkspaceListing.kt`), nothing in the Chat named it,
and the Contents API refuses a hidden path while `ContentsManager.allow_hidden` is false.

The references:

- vscode@3f3380c `src/vs/workbench/contrib/mcp/browser/mcpCommands.ts`: `OpenMcpResourceCommand`
  checks `fileService.exists`, writes `{ servers: {} }` if the file is missing, and opens it in the
  ordinary editor. `OpenWorkspaceFolderMcpResourceCommand` does the same for a folder's
  `.vscode/mcp.json`. `mcpLanguageFeatures.ts` adds code lenses and diagnostics, and
  `AddConfigurationAction` is a guided add. VS Code restarts a server when the file changes.
- jupyter-ai@5b23f55d `docs/source/users/index.md` §Custom MCP servers: the user writes the file by
  hand, and jupyter-ai offers no UI for it.
- jupyterlab, vscode-jupyter, intellij-community, termux-app and winlator configure no MCP servers.

2 of the 7 references configure MCP servers, and the image already has the server half. VS Code is
the only one with a way in from the UI, and that way is a plain file in the ordinary editor.

The agent is opencode v1.19.3 (anomalyco/opencode@e3c4eb24 `packages/opencode/src/acp/service.ts`).
`initialize` advertises `mcpCapabilities: {http, sse}`. `registerMcpServers` runs on every session
it creates or loads, skips a server whose name and config it has already registered for that
session, and calls `sdk.mcp.add` for the rest. A failed add is dropped silently (`Effect.ignore`).
It never removes a server. `closeSession` forgets the session's record of what it added, but the
servers stay.

## Decision drivers

- The agent gets the servers the user lists, through the path the image already has. No code of
  ours goes into the image.
- One schema: jupyter-ai's. A second one of ours would have to be kept in step with it.
- Keep the ADR-0104 boundary: the extension sees only `:extension-api`.
- Reachable with no chat open and with no server running.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| How the user edits it | by hand with no UI (jupyter-ai); the ordinary editor, created if missing (VS Code); a guided Add Server (VS Code) | the ordinary file editor, created if missing. A form per server type is a second schema |
| Who writes the file | the Contents API; the host's own workspace files | the host. The API refuses a hidden path, and the host opens the file in its own editor anyway |
| How the panel asks | a hook added to `SidePanelHost`; the extension opening files itself | a `WorkspaceFileOpener` handed in `SidePanelHost`, as `NotebookActiveCell` is. An extension cannot build against `:app` |
| Where the button sits | the chat view's own menus (VS Code); beside New chat on the chat list | beside New chat, so it is there with no chat open |
| When an edit applies | when the file changes (VS Code); when the next session starts (jupyter-ai) | when the next session starts, which is what the image does |

## Decision

**MCP servers, beside New chat on the Chat's list, opens `.jupyter/mcp_settings.json` in the host's
file editor. If the file is missing, it is written first as `{"mcp_servers": []}`. The agent gets
what the file lists when the next chat opened starts its session.**

- `WorkspaceFileOpener` (`extension-api/src/main/java/com/jupydroid/extension/api/WorkspaceFileOpener.kt`)
  opens a workspace-relative path in the host's editor, written with the given text first if nothing
  is there. The host hands it to a panel in `SidePanelHost`.
- `EditorWorkspaceFileOpener` (`app/src/main/java/com/jupydroid/app/EditorWorkspaceFileOpener.kt`)
  writes through `WorkspaceFiles.createIfAbsent`
  (`app/src/main/java/com/jupydroid/app/domain/port/WorkspaceFiles.kt`), on the application's scope,
  and opens the file through the shell's document opener. `createIfAbsent` creates the folders above
  the file, as VS Code's `writeFile` creates the parent first
  (vscode@3f3380c `src/vs/platform/files/common/fileService.ts`). A file that is already there is
  opened as it is.
- `McpSettingsFile` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/McpSettingsFile.kt`)
  holds the path and the empty file. `ChatList` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatList.kt`)
  draws New chat and MCP servers in one row, two 48 dp buttons. The icon is Material's Dns, the
  closest match to VS Code's `Codicon.server` on its MCP entries.

On SM-X216B, a stdio server listed in the file was called by opencode in a new chat, and again after
the app was killed and relaunched. opencode names an MCP tool `<server>_<tool>`, and the chat's row
shows that name. When the session started, `jupyter_server` logged
`MCP servers loaded: 0 builtin, 1 from settings file.`

## Consequences

- An edit applies at the next chat opened, not to a chat already talking.
  `_init_client_session` (`usr/lib/python3.12/site-packages/jupyter_ai_acp_client/base_acp_persona.py`)
  runs once per persona, which means once per live chat. A chat that is already talking keeps the
  servers it started with.
- A server removed from the file stays registered in opencode until opencode restarts, because
  `registerMcpServers` only adds. This is opencode's behaviour, and nothing of ours has to undo it.
- Nothing in the Chat reports a mistake. A file that does not parse goes to the server log only, and
  opencode drops a server it fails to add silently. The editor highlights JSON, so a syntax error
  can be seen, but a valid file with a wrong command cannot.
- The explorer still hides `.jupyter/`. That is deliberate: MCP servers is the way in, as VS Code's
  command is, and listing dot-entries is a separate decision.
- There is no guided Add Server, no schema diagnostics, and no view that lists or starts servers.
  `jupyter_server_mcp`'s notebook tools are not in the image, so the built-in list stays empty.
- Do not route the write through the Contents API. It refuses the hidden path for as long as
  `allow_hidden` is false, and turning that on would also expose every dot-file to the web client.
