# JD-048 — An agent in the Chat edits the open notebook

Milestone: `M7` · State: in-progress · Depends on: JD-055 · Closes: —

## Outcome

You ask the agent in the Chat to add a cell, and the cell appears in the notebook open on screen, at
the index the agent asked for, ready to run and saved like any other edit. Today the agent can only
offer code, and you insert it yourself with the code block's Insert action (ADR-0114). It has no tool that
reaches the notebook, because the image ships no notebook MCP server (ADR-0115: "`jupyter_server_mcp`'s
notebook tools are not in the image").

## Demo

On SM-X216B, with opencode installed and signed in in base (ADR-0106), landscape (two panes):

1. Cold start. Open `untitled.ipynb` from Files and type `x = 1` in its one cell.
2. Open Chat and start a new chat. Ask: "Use the insert_cell tool to insert a code cell containing
   `print(42)` at index 0 of untitled.ipynb."
3. A tool-call row for `insert_cell` appears and completes (ADR-0112). A new first cell holding `print(42)`
   appears in the notebook above `x = 1`, and it is the active cell.
4. Run the new cell: `42`.
5. Ask the agent to insert a cell into a notebook that is not open. The tool-call row ends with an error
   that names the path, and nothing changes on screen.
6. Kill and relaunch, then open `untitled.ipynb`: `print(42)` is still its first cell.

The item is not `shipped` until this has been run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai` (+ `jupyterlab`) | FULL | jupyter-ai@5b23f55d `jupyter_ai/__init__.py` (`DEFAULT_JUPYTER_SERVER_MCP_TOOLS`), `pyproject.toml` (`jupyter_server_mcp>=0.3.0,<0.4.0`, `jupyter_ai_tools>=0.7.0,<0.8.0`) | A server extension serves the notebook tools over MCP HTTP. Without RTC, each tool asks the browser to run a JupyterLab command and waits for the result |
| `vscode` | PARTIAL | vscode@3f3380c `src/vs/workbench/contrib/chat/browser/chatEditing/notebook/` (`chatEditingNotebookCellEntry.ts`, `chatEditingNewNotebookContentEdits.ts`) | The host applies an agent's notebook edits as a reviewable chat-editing session. The edit tool itself is contributed by an extension outside the tree |
| `vscode-jupyter` | PARTIAL | vscode-jupyter@f01d80c `src/standalone/chat/extension.node.ts`, `package.json` (`configure_notebook`, `notebook_install_packages`, `restart_notebook_kernel`, …) | Language-model tools for the kernel and packages. None adds or edits a cell |
| `intellij-community` | ABSENT | — | grep `mcp` in `*.kt` under notebook/jupyter paths: none. AI Assistant is closed source |
| `termux-app` | ABSENT | — | |
| `winlator` | ABSENT | — | |
| the shipped rootfs | PARTIAL | `jupyter_ai_persona_manager/persona_manager.py` (`builtin_mcp_servers`), `jupyter_ai_acp_client/default_acp_client.py` (`_get_mcp_servers`) | The agent side already hands any built-in MCP server to opencode. The server that has the tools is not installed |

**Count:** 1 of 7 lets an agent add a cell, plus VS Code's host half. The one that does is jupyter-ai, which
is the chain this app's Chat already runs (JD-046), so we adopt its server half
unchanged and answer its frontend half natively.

## Implementation inventory

The submodules of jupyter-ai@5b23f55d are not checked out under `reference/`. Their pinned SHAs come from
`git ls-tree HEAD submodules/` in that clone, and each file below was read at that SHA on GitHub.

### jupyter-ai-tools@4d1c823 (`jupyter-ai-contrib/jupyter-ai-tools`)

| Concern | Files |
|---|---|
| The tool | `jupyter_ai_tools/toolkits/notebook.py` `insert_cell(file_path, content, insert_index, cell_type)` |
| Which path it takes | `jupyter_ai_tools/utils.py` `rtc_available()`: True only if `jupyter_server_documents` or `jupyter_server_ydoc` is an enabled extension. Neither is in the image (`tools/rootfs/Dockerfile`, the jupyterlab_chat comment), so every call takes the RTC-free path |
| RTC-free path | `insert_cell` calls `run_lab_command("jupyterlab-ai-commands:get-notebook-info", {notebookPath})`, turns `insert_index` into a reference cell and `above`/`below`, then calls `run_lab_command("jupyterlab-ai-commands:add-cell", {notebookPath, referenceCellId, content, cellType, position})` |
| Registration | `jupyter-config/jupyter_server_config.d/jupyter_ai_tools.json` enables it as a server extension. It declares no `jupyter_server_mcp.tools` entry point; that entry point is the `jupyter_ai` metapackage's |
| Dependencies | `pyproject.toml`: `jupyter_server`, `pycrdt`, `jupyter_ydoc`, `jupyterlab-commands-toolkit`, `jupyterlab-ai-commands>=0.4.0` |

### jupyterlab-commands-toolkit@7c87c5d

| Concern | Files |
|---|---|
| Port / protocol | `jupyterlab_commands_toolkit/tools.py` `execute_command` → `emit_and_wait_for_result`: emits `https://events.jupyter.org/jupyterlab_command_toolkit/lab_command/v1` `{name, args, requestId, client_id?}` on the server's event bus |
| The answer | `events/jupyterlab-command-ack.yml` `{requestId, client_id}` within `CommandsToolkit.ack_timeout`, then `events/jupyterlab-command-result.yml` `{requestId, success, result?, error?}` within `command_timeout`. The listeners are in `__init__.py` `_load_jupyter_server_extension` |
| Frontend half | `src/index.ts`: subscribes to `events.stream`, skips a command whose `client_id` is another client's, acks, runs `app.commands.execute(name, args)`, and posts the result. The events service is the server's `/api/events/subscribe` WebSocket and `POST /api/events` |
| Empty / error states | `tools.py`: no subscriber → `error_code: "no_web_client"` "No JupyterLab web client is connected. Open JupyterLab in a web browser…"; no ack → `not_acknowledged`; then `timeout`, `invalid_command` |

### jupyterlab-ai-commands@2743d32 (the commands a web client must run)

| Concern | Files |
|---|---|
| `get-notebook-info` | `src/notebook-commands.ts` `registerGetNotebookInfoCommand`: `{notebookPath?}` → `{success, notebookName, notebookPath, cellCount, activeCellId, activeCellType, cells: [{cellId, cellType}], notebookMetadata, isDirty}` |
| `add-cell` | same file, `registerAddCellCommand`: `{notebookPath?, referenceCellId?, content, cellType = code, position = below}`. A notebook whose only cell is empty has that cell replaced; otherwise the cell goes above or below the reference. The new cell becomes active. Returns `{success, message, cellId, content, cellType, position}` |
| Edge cases it handles | an invalid `cellType`/`position` throws with the value and the allowed ones; a path that cannot be opened throws `Failed to open notebook at path: …` |

### jupyter-server-mcp@d1823a5

| Concern | Files |
|---|---|
| Server | `jupyter_server_mcp/extension.py` `MCPExtensionApp`: `mcp_port`, `mcp_tools` (a list of `module:function`), `use_tool_discovery` (entry points), `mcp_middleware`. It serves MCP over HTTP on its own port (`mcp_server.py`) |
| Dependency | `pyproject.toml`: `fastmcp>=3.4.4,<5` (pulls starlette, uvicorn, httpx, the `mcp` SDK) |

**Where they disagree:** with RTC, jupyter-ai-tools edits the server's Y document and every client sees it.
Without RTC, the tools edit nothing themselves and need a client to run the command. VS Code applies edits
host-side and asks you to accept them. The question for us is which half the app plays.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How an edit reaches the screen | jupyter-ai-tools → the server's Y document (RTC), or a web client running `jupyterlab-ai-commands` (RTC-free) | RTC means a Yjs client in the app and `jupyter_server_documents` in the image, which replaces the notebook's file model. RTC-free means one events socket and two commands | RTC-free: the app is a web client of the commands toolkit, as JupyterLab is, and the notebook stays the editor's (`NotebookCellEdits`) |
| Which tools the agent sees | jupyter-ai → all of `DEFAULT_JUPYTER_SERVER_MCP_TOOLS` via the `jupyter_ai` entry point | A tool the app cannot answer fails after `ack_timeout` and costs the agent a turn | `MCPExtensionApp.mcp_tools = [insert_cell]` with `use_tool_discovery = False`, so the list grows only as the app answers more commands |
| Where the web client lives | JupyterLab → a frontend plugin per extension | The AI extension already owns the event bus (`PersonaEventsSubscription.kt`) | The AI extension subscribes, and reaches the notebook through a widened `:extension-api` interface (ADR-0104 boundary) |
| A command for something we do not answer | JupyterLab → `app.commands.execute` throws, `success: false` | Silence would cost the agent the whole `command_timeout` | Ack, then `success: false` with an error naming the command or path |
| Review before apply | VS Code → accept/reject per edit; jupyter-ai → applied directly | Review needs a diff surface we lack | Applied directly, as jupyter-ai does. The agent's own permission request (ADR-0112) is the gate, and the cell joins undo |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| MCP server with notebook tools | none in `tools/rootfs/requirements.lock` | jupyter-server-mcp@d1823a5 | absent |
| Agent gets the built-in server | `builtin_mcp_servers` in the image's persona manager (ADR-0115) | same | present and unreachable: needs the package to import |
| Events socket | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/PersonaEventsSubscription.kt` (per chat, persona states only) | commands-toolkit `src/index.ts` | present and partial: no command frames, no ack/result |
| `POST /api/events` | `OkHttpAgentChat.kt` (persona selection) | same | present: the shape a result post reuses |
| Insert into the open notebook | `extension-api/.../NotebookActiveCell.kt`, `app/.../OpenNotebookActiveCell.kt`, `NotebookCellEdits.insertCodeBelow` | `add-cell` | present and partial: below-active code only, no path, cell ids, index or type |

## Deviations

- Only `insert_cell`. `read`, `edit`, `delete`, `select`, `run` and `create_notebook` are later items, and
  each is a command the app must answer before its tool joins `mcp_tools`.
- The web client is native, not a browser tab, so `background` and `createWidget` mean nothing; a path that
  is not the open notebook is refused rather than opened.

## Open decisions

- **Whether the image's persona manager (0.2.2) builds the built-in server's URL from
  `MCPExtensionApp.mcp_port`**, and with which headers. Read `jupyter_ai_persona_manager/persona_manager.py`
  in the built rootfs in T1, and pin the jupyter-server-mcp version that matches it.
- **fastmcp on musl/arm64**: its dependency tree must resolve to wheels or pure Python in the image. T1.
- **Start-up cost.** fastmcp is imported by every server start once the extension loads. Measure it on
  the tablet with `-X importtime` and `scripts/read_import_profile.py`. If it lands on the clock, defer it
  as `acp_lazy_import_patch.py` does. T1.
- **One events socket or one per chat.** Today the subscription is opened per chat. A command arrives
  while a chat is open, but a second subscriber would ack twice. T3 settles it with a test.

## Surface

| Layer | Files |
|---|---|
| rootfs | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.lock`, a `jupyter_server_config.d` JSON for `MCPExtensionApp`, a build-time probe beside `acp_lazy_import_probe.py` |
| `extension-api` | `extension-api/src/main/java/com/jupydroid/extension/api/` an open-notebook cells interface (path, cells, active id, insert at index) |
| `:app` | `app/src/main/java/com/jupydroid/app/OpenNotebookActiveCell.kt` (or its sibling), `ui/editor/NotebookCellEdits.kt` |
| extension | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/` a lab-command subscription and its frames |
| unit tests | `extensions/ai/src/test/…` against `ChatLikeTheTabletsServer.kt`; `app/src/test/…` for the insert |
| `.maestro/` flow + `scripts/` driver | one per task, then the demo |

No new port in `:app`'s domain: the commands travel over the server connection the AI extension already
holds.

## Tasks

- [ ] T1 — The image ships jupyter-server-mcp, jupyter-ai-tools, jupyterlab-commands-toolkit and
  jupyterlab-ai-commands, with `mcp_tools` set to `insert_cell` alone. On SM-X216B the agent's
  `insert_cell` call ends with the toolkit's "No JupyterLab web client is connected". Settle the three
  rootfs open decisions
- [ ] T2 — `:extension-api` exposes the open notebook's path, cells and active cell, and inserts a cell
  of a type at an index; `:app` implements it over `NotebookCellEdits`
- [ ] T3 — The AI extension answers `get-notebook-info` and `add-cell` from the events socket (ack, then
  result through `POST /api/events`) and refuses anything else. The agent's cell lands in the notebook on
  SM-X216B
- [ ] T4 — The demo above on SM-X216B; a defect found is fixed red first
- [ ] T5 — The ADR (the app is the commands toolkit's web client, and `mcp_tools` lists only the tools it
  answers); delete this PRD; update the backlog

## Out of scope

- The other notebook tools (`read_notebook`, `edit_cell`, `delete_cell`, `run_cell`, …): later items, one
  command each.
- RTC (`jupyter_server_documents`).
- An agent other than opencode: `JD-050`.
