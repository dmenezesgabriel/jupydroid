# JD-055 — An agent uses the MCP servers the user configures

Milestone: `M7` · State: in-progress · Depends on: JD-046 · Closes: —

## Outcome

From the Chat, the user opens the workspace's `.jupyter/mcp_settings.json` in the file editor, lists
MCP servers in it, and the agent in the next chat they open can call those servers' tools. Today the
file cannot be reached at all: the explorer hides dot-entries
(`app/src/main/java/com/jupydroid/app/data/disk/WorkspaceListing.kt`), and nothing in the Chat names it.

## Demo

On SM-X216B, with opencode installed and signed in in base (ADR-0106):

1. Open Chat. Tap *MCP servers*. A file editor tab opens on `.jupyter/mcp_settings.json`, holding
   `{"mcp_servers": []}` because the file did not exist.
2. Add a stdio server that runs a stdlib-only Python MCP fixture in the workspace, one tool with a
   distinctive answer. Save.
3. Back in Chat, New chat. Ask opencode to call that tool. The tool-call row names the tool, and the
   answer carries the fixture's distinctive value.
4. Kill and relaunch. *MCP servers* opens the file with the server still in it, and a new chat still
   gets the tool.

The item is not `shipped` until this has run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai` | FULL (server), ABSENT (UI) | jupyter-ai@5b23f55d `docs/source/users/index.md` §Custom MCP servers; `jupyter_ai_persona_manager` 0.2.2 `persona_manager.py` `get_mcp_settings`, `mcp_server_models.py`; `jupyter_ai_acp_client` 0.3.3 `default_acp_client.py` `_get_mcp_servers` | The user writes `.jupyter/mcp_settings.json` by hand; the persona manager reads it for each ACP session and the client hands its servers to the agent on `session/new` and `session/load` |
| `vscode` | FULL | vscode@3f3380c `src/vs/workbench/contrib/mcp/browser/mcpCommands.ts` (`OpenMcpResourceCommand`, `OpenWorkspaceFolderMcpResourceCommand`, `AddConfigurationAction`), `src/vs/workbench/contrib/mcp/browser/mcpLanguageFeatures.ts` | A command opens the workspace's `mcp.json` in the ordinary editor, creating it with an empty `{ servers: {} }` first if it is missing |
| `jupyterlab` | ABSENT | — | |
| `vscode-jupyter` | ABSENT | — | Its only `mcp` hits are test notebooks |
| `intellij-community` | ABSENT | — | Its only `mcp` hits are a bundled webview demo's assets; AI Assistant is closed |
| `termux-app` | ABSENT | — | |
| `winlator` | ABSENT | — | |
| the shipped rootfs | FULL (server) | `usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_manager.py` | The server half above, already in the image |

The agent: opencode v1.19.3 (opencode@e3c4eb2 `packages/opencode/src/acp/service.ts`) advertises
`mcpCapabilities: {http, sse}` and calls `registerMcpServers` on both `newSession` and `loadSession`,
which `sdk.mcp.add`s each server. It adds and never removes.

**Count:** 2 of 7 configure MCP servers, plus the server half already in the image. VS Code is the
only one with a way in from the UI, and it is a plain file opened in the ordinary editor.

## Implementation inventory

### jupyter_ai_persona_manager 0.2.2 (the pinned wheel, also in the shipped rootfs)

| Concern | Files |
|---|---|
| Schema | `mcp_server_models.py`: `McpSettings{mcp_servers: [McpServerStdio{name, command, args, env[{name,value}]} \| McpServerHttp{type:"http", name, url, headers[{name,value}]}]}` |
| Where the file is | `persona_manager.py` `get_dotjupyter_dir`: `directories.py` `find_dot_dir`, the nearest `.jupyter` going up from the chat's directory, stopping at `root_dir`. Chats are at the workspace root (ADR-0111), so it is `<workspace>/.jupyter/mcp_settings.json` |
| When it is read | `get_mcp_settings`, on every call: no cache, no restart |
| Error state | A file that does not parse is logged (`Failed to read or parse MCP config`) and treated as absent; nothing reaches the chat |
| Built-ins | `builtin_mcp_servers` defaults to `jupyter_server_mcp`'s HTTP server only when that package imports. The image does not ship it, so the list is empty |

### jupyter_ai_acp_client 0.3.3

| Concern | Files |
|---|---|
| Handed to the agent | `default_acp_client.py` `_get_mcp_servers`, from `create_session` and `_load_session_rpc`; HTTP servers only when `agent_capabilities.mcp_capabilities.http` |
| When a session starts | `base_acp_persona.py` `_init_client_session`: once per persona instance, which is once per live chat model. A chat already talking keeps its servers; **the next chat opened** gets the edit |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Open the config | `mcpCommands.ts` `OpenMcpResourceCommand.run`: `fileService.exists` → `createFile(resource, JSON.stringify({ servers: {} }, null, '\t'))` → `editorService.openEditor` |
| Workspace folder file | `OpenWorkspaceFolderMcpResourceCommand`: `.vscode/mcp.json` in the folder |
| Editing aids | `mcpLanguageFeatures.ts`: code lenses and diagnostics over `mcp.json`; `AddConfigurationAction` is a guided add |

**Where they disagree:** VS Code restarts servers when the file changes; jupyter-ai hands them over
only when a session starts. Ours follows the image, which is jupyter-ai's.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| How the user edits it | jupyter-ai → by hand, no UI; VS Code → the ordinary editor, created if missing; VS Code → a guided Add Server | A form for each server type is a second schema to keep in step with jupyter-ai's | The ordinary file editor, created with `{"mcp_servers": []}` if missing, as VS Code does, in jupyter-ai's schema |
| Who writes the file | the Contents API; the host's `WorkspaceRepository` | `ContentsManager.allow_hidden` is false, so the API refuses a hidden path | The host, which opens it in its own file editor anyway |
| How the panel asks | a new host hook in `SidePanelHost`; the extension opening files itself | An extension cannot build against `:app` (ADR-0104) | A `WorkspaceFileOpener` in `:extension-api`, handed in `SidePanelHost`, as `NotebookActiveCell` is |
| When an edit applies | VS Code → on change; jupyter-ai → at the next session | Restarting a running agent needs code in the image | At the next chat opened, which is the image's behaviour; the ADR says so |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| The agent gets the servers | the shipped rootfs's persona manager and ACP client | `jupyter_ai_acp_client` 0.3.3 `default_acp_client.py` | present, never exercised on device |
| The file reachable | hidden by `WorkspaceListing.kt` | vscode@3f3380c `mcpCommands.ts` `OpenMcpResourceCommand` | present but unreachable |
| A panel opens a file in the editor | `app/src/main/java/com/jupydroid/app/DocumentOpeners.kt` `rememberDocumentOpener`, not in `SidePanelHost` | — | absent from the extension API |

## Deviations

- No guided Add Server and no schema diagnostics in the editor. The file editor already highlights
  JSON; a form is a second schema to keep in step.

## Open decisions

- **Where the action sits.** Beside New chat on the chat list, so it is reachable with no chat open
  and needs no server. VS Code puts its MCP entry points in the chat view's own menus. Settled in T1.

## Surface

| Layer | Files |
|---|---|
| `:extension-api` | `extension-api/src/main/java/com/jupydroid/extension/api/WorkspaceFileOpener.kt`, `SidePanelHost.kt` |
| `:app` host | `app/src/main/java/com/jupydroid/app/EditorWorkspaceFileOpener.kt`, `ShellSidePane.kt`, `Navigation.kt` |
| `:extensions:ai` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/McpSettingsFile.kt`, `ui/ChatList.kt`, `ui/ChatsPanel.kt`, `AiExtension.kt` |
| unit tests | `app/src/test/java/com/jupydroid/app/EditorWorkspaceFileOpenerTest.kt`, `extensions/ai/src/test/java/com/jupydroid/extensions/ai/domain/McpSettingsFileTest.kt` |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ui/shell/ChatListSidePanelTest.kt` |
| `.maestro/` flow + `scripts/` driver | `.maestro/chat-mcp-settings.yaml`, `scripts/chat-mcp-settings-driver.sh` |

## Tasks

- [x] T1 — *MCP servers* on the chat list opens `.jupyter/mcp_settings.json` in the file editor,
  writing `{"mcp_servers": []}` first when it is missing, through a `WorkspaceFileOpener` the host
  hands in `SidePanelHost`
- [ ] T2 — The demo: a stdlib Python stdio MCP fixture configured in the file, a new chat in which
  opencode calls its tool, and the same after a kill, on SM-X216B. A defect found is fixed red first
- [ ] T3 — The ADR (where the file is, how it opens, when an edit applies, opencode's add-only
  registration); delete this PRD; update the backlog

## Out of scope

- A guided Add Server, and listing or starting servers from the Chat (VS Code's MCP view).
- `jupyter_server_mcp`'s notebook tools: `JD-048`.
- Applying an edit to a chat already talking, or removing a server from a running agent.
