# ADR-0118 — The app is the commands toolkit's web client, and the MCP port takes the server's token

Status: accepted
Date: 2026-10-27

## Context

The agent in the Chat could offer code but not put it in the notebook. A code block's Insert did that
by hand ([ADR-0114](0114-an-agents-answer-is-drawn-by-the-hosts-markdown-renderer-and-its-code-goes-below-the-active-cell.md)).
`builtin_mcp_servers`, which hands the agent jupyter-ai's own MCP server, was wired in the image but
empty ([ADR-0115](0115-an-agents-mcp-servers-are-listed-in-the-workspaces-mcp-settings-file-and-apply-at-the-next-chat.md)),
because the package that serves the notebook tools was not installed.

Of the seven references, one lets an agent add a cell: jupyter-ai, whose chain this app's Chat already
runs. VS Code (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/chatEditing/notebook/`) applies an
agent's notebook edits as a reviewable session, but the edit tool comes from an extension outside the
tree. vscode-jupyter@f01d80c's language-model tools add no cell. JupyterLab, intellij-community,
termux-app and winlator have nothing. jupyter-ai's server half is four packages (the submodules of
jupyter-ai@5b23f55d, read at these SHAs):

- jupyter-server-mcp@d1823a5 `jupyter_server_mcp/extension.py` `MCPExtensionApp` serves tools over MCP
  HTTP on its own port (`mcp_port`, default 3001), from a `mcp_tools` list or from entry points
  (`use_tool_discovery`), with a `mcp_middleware` hook.
- jupyter-ai-tools@4d1c823 `jupyter_ai_tools/toolkits/notebook.py` `insert_cell`. With RTC it edits the
  server's Y document. Without it, and `jupyter_ai_tools/utils.py` `rtc_available()` is false in this
  image (neither `jupyter_server_documents` nor `jupyter_server_ydoc` is installed), it runs
  `jupyterlab-ai-commands:get-notebook-info` and then `jupyterlab-ai-commands:add-cell` through
  `run_lab_command`, and edits nothing itself.
- jupyterlab-commands-toolkit@7c87c5d `jupyterlab_commands_toolkit/tools.py` `emit_and_wait_for_result`
  emits each command on the server's event bus, then waits for an ack within `ack_timeout` and a result
  within `command_timeout`, both posted by a web client. It keeps the first result for a `requestId` and
  ignores the rest (`handle_command_result`). JupyterLab's half is `src/index.ts`, which uses the
  `/api/events/subscribe` WebSocket and `POST /api/events`.
- jupyterlab-ai-commands@2743d32 `src/notebook-commands.ts` defines the two commands' arguments,
  results and errors.

Two constraints are this system's own. Android's loopback is shared by every app that holds `INTERNET`.
jupyter-server-mcp serves "outside Jupyter Server's token/XSRF protection" (`mcp_server.py`,
`host_origin_protection`), and its Host and Origin checks stop a browser but not another app. MCP
2025-11-25 asks a local HTTP server to "require an authorization token"
(https://modelcontextprotocol.io/specification/2025-11-25/basic/security_best_practices, Local MCP
Server Compromise). The second constraint is that two Spaces run at once
(`TwoSpacesAtOnceDeviceTest`), so a fixed port fails the second Space's bind. persona-manager 0.2.2
`_default_builtin_mcp_servers` builds `http://localhost:<MCPExtensionApp.mcp_port>/mcp` from config,
so it cannot follow a port of 0.

## Decision drivers

- An agent's cell lands in the notebook on screen and is saved like any other edit, through the
  editor that owns the notebook.
- Adopt jupyter-ai's server half unchanged. Patch no one's code, and add no new secret.
- No other app on the device reaches the tools.
- The agent sees no tool it cannot use. A tool the app does not answer costs the agent a turn.
- Keep the ADR-0104 boundary: the AI extension sees only `:extension-api`.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| How an edit reaches the screen | RTC: a Yjs client in the app plus `jupyter_server_documents`, which replaces the notebook's file model; RTC-free: a web client runs the two commands | RTC-free. The app is a web client, as JupyterLab is, and the notebook stays the editor's |
| Which tools the agent sees | every tool, through the `jupyter_ai` entry point (jupyter-ai) or the toolkit's own entry point; a list | the list, `mcp_tools = [insert_cell]` with discovery off. It grows only when the app answers more commands |
| Where the web client lives | a frontend plugin per extension (JupyterLab); the AI extension, which already owns the events socket | the AI extension, reaching the notebook through `:extension-api` |
| A command the app does not answer | silence, which costs the agent `command_timeout`; ack and then `success: false` | ack, then `success: false` naming the command or the path, as a JupyterLab command that throws does |
| Review before an edit applies | accept or reject each edit (VS Code); apply it directly (jupyter-ai) | apply directly. The agent's permission request ([ADR-0112](0112-an-agents-tool-calls-are-rows-that-ask-and-stop-cancels-its-reply.md)) is the gate, and the cell joins undo |
| Who may call the MCP port | anyone on loopback (upstream); a UDS behind a stdio proxy (jupyter-server-mcp#35, draft); the server's token as `Authorization: Bearer` (https://github.com/jupyter-ai-contrib/jupyter-server-mcp/issues/25); FastMCP's `auth=` | the token, through `mcp_middleware`. FastMCP's `auth=` is unreachable because `MCPServer` builds `FastMCP(self.name)` itself, and the UDS needs an unmerged patch |
| Which port | upstream's fixed 3001; 0 plus a runtime file; one picked per launch | per launch. The launcher binds 0, closes the socket and passes the port, as jupyter_client does for kernel ports |
| JupyterLab in the image | jupyter-ai-tools with its dependencies, whose `jupyterlab-ai-commands` → `jupyterlab-diff` → `jupyterlab-cell-input-footer` chain pulls in jupyterlab 4.6.4 (`uv pip compile`, aarch64-unknown-linux-musl); `--no-deps` with its Python dependencies locked | `--no-deps`. `jupyterlab-ai-commands` is only a labextension, which is exactly the half the app replaces |

## Decision

**The app answers jupyterlab-commands-toolkit's commands as its web client. The image's MCP server
lists only the tools whose commands the app answers, and it serves only a caller holding the Jupyter
server's own token, on a port picked per launch.**

- **The web client.** Each chat's events socket
  (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/PersonaEventsSubscription.kt`)
  reads command frames (`LabCommandFrames.kt`). One `LabCommandWebClient` per `AiExtension` lets only
  the first socket to take a `requestId` run it, because the toolkit keeps the first result. A second
  socket that also ran the command would insert the cell twice
  (`OkHttpAgentChatLabCommandsTest.twoOpenChatsRunACommandOnce`). The client acks, then
  `NotebookLabCommands` answers `get-notebook-info` and `add-cell` with jupyterlab-ai-commands' shapes,
  and the result goes back through `POST /api/events`. Any other command fails with its name. The
  notebook is reached through `NotebookCells`
  (`extension-api/src/main/java/com/jupydroid/extension/api/NotebookCells.kt`): the open notebook's path,
  cells and active cell, and `insert(index, type, source)`. That is one insert shared with the chat's
  Insert, implemented by `app/src/main/java/com/jupydroid/app/OpenNotebookCells.kt` over
  `NotebookCellEdits`. `add-cell`'s `referenceCellId` and `position` are turned into that index.
- **The list.** `tools/rootfs/jupyter_server_config.json` (shipped as
  `usr/etc/jupyter/jupyter_server_config.json`) sets `mcp_tools` to
  `jupyter_ai_tools.toolkits.notebook:insert_cell` and `use_tool_discovery` to false.
- **The token.** `tools/rootfs/jupydroid_mcp_bearer.py` (shipped as
  `usr/lib/python3.12/site-packages/jupydroid_mcp_bearer.py`) is a FastMCP middleware loaded through
  `mcp_middleware`. It refuses any message whose request lacks `Authorization: Bearer <the server's
  IdentityProvider token>`. That hook logs a factory that fails and goes on serving, so the image build
  runs `mcp_bearer_probe.py` to prove the refusal end to end.
- **The port.** `JupyterServerLauncher`
  (`app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt`, `mcpServerArgs`) asks
  `freeLoopbackPort` (`LoopbackPort.kt`) for a port once per start. It passes
  `--MCPExtensionApp.mcp_port`, `--MCPServer.host=127.0.0.1`, and a
  `--PersonaManager.builtin_mcp_servers` entry with the same URL and the bearer header. The entry is a
  `config=True` trait, so the agent gets the header with no change to the persona manager.
- **The start-up cost.** Importing the MCP stack inside `jupyter_server`'s extension load added 4.1 s to
  `server.starting → server.listening` (warm). `tools/rootfs/mcp_lazy_import_patch.py` defers
  `MCPServer`'s import into `start_extension`, and `mcp_lazy_import_probe.py` checks the deferral at
  build time.

Measured on SM-X216B:

- A request without a header, one with a wrong token, and one with the token but no `Bearer` scheme
  were each refused with a message naming the expected header. The server's token listed
  `['insert_cell']` and nothing else.
- Warm `server.starting → server.listening` took 3.685, 3.891 and 3.719 s with the deferred import,
  against 3.674, 3.527 and 3.412 s on the image before. The MCP server comes up 6 to 7 s after the
  server reports running, and the server logged `MCP servers loaded: 1 builtin, 0 from settings file.`
- Before the app answered, each command waited out the 2 s `ack_timeout` and the tool returned "No
  JupyterLab web client acknowledged the command". Once the app answered, `get-notebook-info` and
  `add-cell` were answered 30 to 35 ms apart.
- The demo, landscape with opencode signed in, started from `x = 1` typed in `untitled.ipynb`.
  `insert_cell` at index 0 put `print(42)` first and active, it printed `42`, and it was still first
  after a kill and relaunch. Asked to insert into `other.ipynb`, the tool returned
  `{"success":false,"error":"other.ipynb is not the open notebook (untitled.ipynb is)"}`, and both
  notebooks stayed as they were.

## Consequences

- A new notebook tool (`read_notebook`, `edit_cell`, `delete_cell`, `run_cell`, …) costs one command
  answered in `NotebookLabCommands` before it joins `mcp_tools`. **Do not turn on
  `use_tool_discovery` to "get the rest for free".** jupyterlab-commands-toolkit's own entry point
  would hand the agent every JupyterLab command, and the app answers two.
- **Do not drop `--no-deps` from jupyter-ai-tools.** Doing so ships JupyterLab to serve a labextension
  nothing loads.
- A notebook that is not the open one is refused rather than opened, since there is no
  `background`/`createWidget` for a native editor. `add-cell` puts its cell beside a lone empty cell
  rather than in its place, as the chat's Insert does. Replacing it would need a delete on
  `NotebookCells`.
- An agent's edit applies without review. Undo is the way back.
- A command is answered only while a chat's events socket is open, and the agent's turn always starts
  from one. A tool called with no chat open would end in `not_acknowledged`.
- A lost race for the port fails safe. `ExtensionManager.start_extension` (jupyter_server 2.20.0) logs
  the failed bind, and the server runs on with no tools. The token stops one Space's agent from
  reaching another Space's server.
- jupyter-server-mcp writes `jpserver-mcp-<pid>.json` beside the server's `jpserver-<pid>.json`.
  `JupyterRuntimeFile` therefore matches the server's file by its exact name
  (`JupyterRuntimeFileTest.read_ignoresTheMcpServersRuntimeFile`), and so does
  `scripts/space-server.sh`. Taking the first `jpserver-*.json` worked only because `2` sorts before `m`.
- The persona manager's own `localhost:3001` entry is replaced, not supplemented. If upstream ever reads
  the port from the runtime file, the launcher's entry can go.
