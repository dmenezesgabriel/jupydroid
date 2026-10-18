# JD-046 — A Chat panel answers from an ACP agent installed in the Space

Milestone: `M7` · State: partial · Depends on: JD-045 · Closes: —

**Reopened.** T1–T4e shipped a question and its answer, and the demo passed. A person then used
the panel and found it barely usable: "the chat's socket failed: null" keeps replacing the
chat, and the input and Send sit flush against each other. T5 and T6 are what was missing.

## Outcome

In a Space where the user has installed opencode and signed it in to a provider, a **Chat** panel on
the side rail sends a question to opencode and shows its answer. The chat is jupyter-ai's own: the
`opencode-acp` persona of jupyter-ai's ACP client, which talks to `opencode acp` over the Agent Client
Protocol, reached through jupyterlab-chat's WebSocket in the Space's `jupyter_server`.

## Demo

On the arm64 tablet (`SM-X216B`). The agent runs in a Space, and only arm64 ships one.

1. Fresh install, launch, run and enter `base`.
2. In the Terminal, install opencode into `/usr/local/bin`, which is on the server's `PATH` (the npm
   tarball `opencode-linux-arm64-musl`, as T1's probe did), then `opencode auth login` with the user's own key. The key never appears in evidence.
3. Tap **Chat** on the rail and ask a question that uses no tool. opencode's answer appears beneath it,
   growing as it streams, and the panel follows it to its last line.
4. Kill and relaunch, run and enter `base`, open Chat: the question and the answer are still there,
   read back from the `.chat` file.
5. Leave the panel open and idle for longer than the Space's idle setting, then ask again: the
   chat is still open and answers. If the socket drops, the panel says so and opens it again
   without a relaunch.
6. The input is one bordered box with Send inside it, padded and aligned the way jupyter-chat's is
   (T6's numbers, before and after).

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai` | FULL | `pyproject.toml` (the metapackage's dependencies); `docs/source/roadmap/completed/decouple-rtc.md`; `docs/source/releases/v3.2.0.md`; the packages below, read from their PyPI wheels because the repo's submodules hold only their docs | a chat file served by jupyterlab-chat, a router that hands each new message to the persona its metadata names, and ACP personas that spawn the agent's CLI and relay its stream back as message updates |
| `vscode` | FULL | `src/vs/workbench/contrib/chat/browser/chatParticipant.contribution.ts` (chat view container in `ViewContainerLocation.AuxiliaryBar`); `src/vscode-dts/vscode.d.ts` (`registerLanguageModelChatProvider`) | the workbench owns a chat view in the secondary side bar, and extensions contribute participants and model providers to it |
| `vscode-jupyter` | PARTIAL | `package.json` (`languageModelTools`: `configure_notebook`, `notebook_list_packages`, `notebook_install_packages`) | contributes tools to VS Code's chat, and has no chat of its own |
| `intellij-community` | ABSENT | `platform/ml-api`, `platform/ml-impl` are completion ranking | the AI Assistant is a separate, closed plugin |
| `termux-app` | ABSENT | — | an agent is a package the user installs in the shell |
| `winlator` | ABSENT | — | — |
| the shipped rootfs | ABSENT | `tools/rootfs/requirements.lock` has no `jupyterlab-chat` or `jupyter-ai-*` | — |

Pinned SHAs: jupyter-ai@5b23f55d, vscode@3f3380c, vscode-jupyter@f01d80ca, intellij-community@406a27a8da.

**Count:** 2 of 7 have a chat (3 counting vscode-jupyter's tools). Both put it in a side panel fed by
extensions. The editor-shaped references all treat the model or agent as something plugged in, never
built in, and that is the shape this item takes.

## Implementation inventory

The jupyter-ai packages live in `jupyter-ai-contrib` repositories, and the metapackage's submodules
check out their `docs/` only. Their code was read from the wheels the metapackage resolves, at these
versions: `jupyterlab_chat` 0.25.2, `jupyter_ai_router` 0.1.1, `jupyter_ai_persona_manager` 0.2.2,
`jupyter_ai_acp_client` 0.3.3, `agent-client-protocol` 0.11.1.

### jupyterlab_chat 0.25.2

| Concern | Files |
|---|---|
| Wire protocol | `ws_messages.py`: client frames `{type:"client", action:"send"\|"edit", id, body, metadata, …}`; server frames `{type:"server", action}`, the action one of `connection` (client id, chat id, user, full history, users), `message` (new or updated, keyed by `id`), `users`, `metadata`, `writing` |
| Endpoint | `__init__.py`: `GET /api/chat/ws/(.+)`, registered only when no RTC provider is enabled (`rtc_lib.py` `get_rtc_provider`) |
| Handler | `websocket_handler.py` (`WSChatHandler`): the path must stay inside the server root or the socket closes `1008`; the sender is the server's authenticated user |
| Persisted state | `websocket_model.py`: the `.chat` file is JSON `{messages, users, attachments, metadata}`; a missing file loads empty and is written on the first save |
| Lifecycle | `chat_manager.py` (`ws_open`, `ws_client_gone`): a chat is freed when its last client leaves, unless a persona holds it open with `keep_alive()` |

### jupyter_ai_router 0.1.1 and jupyter_ai_persona_manager 0.2.2

| Concern | Files |
|---|---|
| Routing | `router.py` (`connect_chat`, `_route_message`); `persona_manager.py` (`on_chat_message`): a message goes to the persona `metadata["to_persona"]` names, and to none if it names none |
| Persona ids | `persona_manager.py` (entry point group `jupyter_ai.personas`, and the id format the default shows: `jupyter-ai-personas::<package>::<Class>`) |
| Enable | `jupyter_server_config.d/*.json` inside each wheel |

### jupyter_ai_acp_client 0.3.3

| Concern | Files |
|---|---|
| The persona | `acp_personas/opencode.py`: refuses at import unless `opencode` is on `PATH` at a version `>=1,<2`; spawns `opencode acp`; sets `OPENCODE_CONFIG` to its bundled `opencode.json` (`edit` and `bash`: `ask`) unless the user has their own |
| Not signed in | `opencode.py` (`handle_message_no_auth`): answers "OpenCode isn't configured yet… run `opencode auth`" |
| Stream to chat | `base_acp_persona.py`, `default_acp_client.py`: the agent's chunks become updates to one message |
| Permissions | `permission_manager.py`, `routes.py` (`PermissionHandler`): a tool the agent wants approved waits for a POST |
| Other agents | `acp_personas/`: claude (needs `claude-agent-acp`, a Node package), codex, copilot, goose, kilo, kiro, mistral_vibe |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Container | `src/vs/workbench/contrib/chat/browser/chatParticipant.contribution.ts` |
| Providers | `src/vscode-dts/vscode.d.ts` (`registerLanguageModelChatProvider`) |

**Where they disagree:** VS Code puts chat in the secondary side bar, on the side opposite the
explorer. jupyter-ai puts it in JupyterLab's side area. Our shell has one side area, so the panel goes
there (see Deviations).

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What answers | jupyter-ai → Jupyternaut over litellm, or ACP personas over agent CLIs; vscode → participants and model providers | Jupyternaut needs a provider key in the app and 143 wheels (litellm, langchain, boto3, tokenizers); ACP needs an agent binary in the Space | **ACP**, the user's choice: the agent owns its provider, sign-in and tools, and the app never holds a key |
| Python in the image | the full metapackage: 210 wheels, JupyterLab 4.6 among them; the ACP chain alone: 73, of which 11 are new to the lock | the rest of the metapackage (MCP, tools, commands toolkit) drives a JupyterLab frontend we do not have | **the ACP chain alone**, resolved for `aarch64-unknown-linux-musl` on 3.12 with binaries only |
| Chat transport | jupyterlab-chat → a Yjs document over RTC, or a JSON WebSocket when no RTC provider is enabled | a CRDT client in Kotlin against a plain JSON stream | **the JSON WebSocket**; the image installs no RTC provider, so it is the one the server registers |
| Where the agent comes from | jupyter-ai → the user installs it on the host; termux → a package in the shell | baking `opencode-linux-arm64-musl` adds 195 MB unpacked per agent, and the next agents need Node | **the user installs it in the Space**, which is their own system (M1); decided by T1's measurement |
| Panel position | jupyter-ai → JupyterLab's side area; vscode → the secondary side bar | — | a `SidePanelContribution`, the one side area our shell has |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| A contributed side panel | `extension-api/src/main/java/com/jupydroid/extension/api/Extension.kt`, `SidePanelHost.kt`; `extensions/git/` as the worked example | `chatParticipant.contribution.ts` | complete, to reuse |
| A WebSocket to the running server | `app/src/main/java/com/jupydroid/app/data/space/OkHttpKernelSocketOpener.kt`, `OkHttpTerminalSocketOpener.kt` | `websocket_handler.py` | present in `:app`; an extension opens its own with OkHttp against `SpaceServerEndpoint` |
| The chat's server half | `tools/rootfs/requirements.lock` | the ACP chain above | absent |
| An agent in the Space | — | `acp_personas/opencode.py` | absent, and unmeasured: see Open decisions |

## Deviations

- One side area, so the panel shares it with Files, Search and Source Control instead of sitting
  opposite them as VS Code's does.
- No tool approval in this item. opencode's bundled config asks before every edit and shell command,
  and the panel cannot answer yet, so a request that needs one waits. The demo asks a question that
  needs no tool. Approval is JD-049.
- The user signs the agent in from the Terminal (`opencode auth login`). The app has no secrets UI.

## Open decisions

- ~~Whether opencode runs in a Space at all.~~ Settled by T1: on SM-X216B's 39-bit kernel,
  `opencode` 1.19.3 installed to `/usr/local/bin` from the Space's own terminal answers `--version`
  and an ACP `initialize`, and logcat shows no seccomp refusal.
- Whether the persona needs a server restart after the install. T2's server log shows the persona
  manager checking each persona's requirement when a chat opens, not at server start. So a new chat
  should load opencode without a restart, and T3a confirmed it on the device.
- ~~Persona ids for `to_persona`.~~ `BasePersona.id` is `jupyter-ai-personas::<top-level
  module>::<class>` (jupyter_ai_persona_manager 0.2.2 `base_persona.py`), so opencode's is
  `jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona`. A message that names no persona
  goes to none: the default is Jupyternaut, which the image does not ship (`persona_manager.py`).
- Which chat the panel opens: `untitled.chat` at the workspace root. jupyterlab-chat looks for chats
  in `defaultDirectory`, `""` by default (`schemas/jupyterlab-chat-extension/factory.json`), and names
  a new one with `contents.newUntitled({ext: ".chat"})`, which is `untitled.chat` for the first.
- ~~Whether to cut the import.~~ Settled by T4b: warm server boot grew by about 1.7 s with the chain
  (median 2.92 s → 4.66 s on SM-X216B), and the on-device profile puts 98 to 99% of
  `jupyter_ai_acp_client`'s import (median 1.22 s) behind one line: `routes.py` imports
  `BaseAcpPersona` at module scope for an `isinstance` test in its POST handler, which loads `acp` and
  pydantic on every server start. The personas that need `acp` load when a chat opens, so `acp`'s
  subtree (median 0.85 s) can wait for one. 0.4.0a0 has the same line. **Cut** by T4c, as a patch
  in the image (`tools/rootfs/acp_lazy_import_patch.py`) that fails the build once upstream changes
  the file. The upstream change is the user's to open. Warm boot went from 4.66 s to 4.00 s.
- Claude, Devin, pi and Antigravity are later items. jupyter_ai_acp_client 0.3.3 has a persona for
  Claude only, and Antigravity has been reported to abort on 39-bit arm64 kernels
  (https://discuss.ai.google.dev/t/bug-agy-crashes-on-arm64/145425).

## Surface

| Layer | Files |
|---|---|
| image | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.lock`, `BaseSpaceInstaller.kt` (`BUNDLED_ROOTFS_VERSION`) |
| `:extensions:ai` domain/port | `extensions/ai/…/domain/` (a chat message, the transcript, the `AgentChat` port) |
| `:extensions:ai` adapter + fake | `extensions/ai/…/data/http/OkHttp…` (the frames), `extensions/ai/…/data/fake/FakeAgentChat` |
| `:extensions:ai` UI | `extensions/ai/…/ui/` (the Chat panel) |
| `:app` host | `settings.gradle.kts`, `app/build.gradle.kts`, `di/AppContainer.kt` |
| gates | `AGENTS.md`'s command, `.pre-commit-config.yaml`, `scripts/check_doc_citations.py` |
| unit tests | frame parsing, the adapter against `MockWebServer`, the transcript's update-by-id |
| instrumented tests | the rail shows Chat, and its input sends |
| `.maestro/` flow + `scripts/` driver | `jd-046-ai-chat-*` |

## The socket failure, measured (T5)

On SM-X216B, 2026-10-18, after the demo, `run/jupyter_server.log` in base read, in order: the chat
socket connected at 13:15:57; the pre-started kernel was culled at 13:21:53 ("Culling 'starting'
kernel … with 0 connections due to 356 seconds of inactivity"); and at 13:22:50 "No kernels for
413 seconds; shutting down". The server exiting drops the socket without a close frame. OkHttp
reports that as an `EOFException`, whose message is null, which is the "failed: null" the panel
shows. `ChatPanel` treats any failure as final, so the chat stays gone until a relaunch.

Why an open chat does not keep the server up (jupyter_server 2.20.0, shipped rootfs):

- `ServerApp.shutdown_no_activity` (`serverapp.py`) stops the server once no kernel exists, no
  extension app's `current_activity()` is true, and `web_app.last_activity()` is older than
  `shutdown_no_activity_timeout`. `JupyterServerLauncher` sets that timeout to the Space's idle
  setting.
- `last_activity` moves only on authenticated API requests (`APIHandler.update_api_activity`,
  `base/handlers.py`). A chat socket's frames are not one, and none of jupyterlab_chat 0.25.2,
  jupyter_ai_router, jupyter_ai_persona_manager or jupyter_ai_acp_client overrides
  `current_activity()` (`jupyter_server/extension/application.py` returns False).
- A browser JupyterLab never meets this, because its frontend polls the REST API on a timer. This
  app holds one socket and polls nothing.

It may not be the only cause. T5a makes every failure say what it was before T5b and T5c remove
the causes.

**How an open Chat keeps the server up (T5b, settled):** a heartbeat from the app, the way
JupyterLab stays alive. While the chat's socket is open, `OkHttpAgentChat` sends an authenticated
`GET api/me` every 30 s, and it stops once the transcript is no longer collected.

- jupyter_server's own hook for this is an ExtensionApp's `current_activity()`, which
  `TerminalsExtensionApp` uses to return its open terminals (jupyter_server_terminals 0.5.4
  `app.py`). jupyterlab_chat 0.25.2 is a module extension, not an ExtensionApp, so
  `ExtensionManager.any_activity` never asks it. The alternative was an ExtensionApp of our own in
  the image, and it was turned down: it means new Python in the rootfs and a rebuild.
- `api/me` is `IdentityHandler`, which counts as activity. `APIStatusHandler` and
  `PathResolverHandler` set `_track_activity = False` (jupyter_server 2.20.0
  `services/api/handlers.py`, `base/handlers.py` `update_api_activity`).
- JupyterLab@7f0e690 polls `api/kernels` and `api/terminals` every 10 s and `api/me` every 61 s,
  each with `standby: 'when-hidden'` (`packages/services/src/kernel/manager.ts`,
  `terminal/manager.ts`, `user/manager.ts`).
- 30 s is half the shortest idle setting, 1 minute (`IDLE_SHUTDOWN_MINUTES_RANGE`). The server
  checks once every 60 s, so whatever the check's phase, the last activity it sees is never older than 30 s.

## The input, against jupyter-chat (T6)

jupyter-chat@2c224bcc `packages/jupyter-chat/src/components/input/chat-input.tsx`: one box with a
1 px `--jp-border-color1` border and an 8 px radius (`borderRadius: 2` at MUI's 4 px unit),
holding a multiline field (`maxRows={10}`, 12 px padding, no underline), then a toolbar row under a
1 px top border, its buttons right-aligned with a 16 px gap and 12 px padding. Send is an icon
button (`ArrowUpwardIcon`, `input/buttons/send-button.tsx`). The sidebar variant uses 12 px of
horizontal padding (`area === 'sidebar' ? 1.5 : 2`). Ours is a bare `OutlinedTextField` with a
`Button("Send")` beside it, bottom-aligned and with no gap between them
(`extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatPanel.kt`, `QuestionField`).

## What the references offer beyond this item

Recorded so the items after this one start from evidence. The PRD of each is written when it is
picked up.

| Capability | jupyter-ai / jupyter-chat | VS Code (vscode@3f3380c) | Backlog |
|---|---|---|---|
| Several chats: list, new (named), switch, delete | `.chat` files at the root in the side panel, **+ New chat** asks for a name, delete is deleting the file (jupyter-ai@5b23f55d `docs/source/users/index.md`) | `workbench.action.chat.newChat`, agent sessions (`chat/browser/actions/chatActions.ts`) | JD-053 |
| Tool calls and approval | a row per call with its status, and "Allow?" with the agent's own options, POSTed as `{session_id, tool_call_id, option_id}` to `ai/acp/permissions` (jupyter-chat-components@992c6fb9 `src/components/grouped-tool-calls.tsx`; shipped `jupyter_ai_acp_client/routes.py`) | Allow Once / Skip / Allow All (`toolInvocationParts/chatToolConfirmationSubPart.ts`) | JD-049 |
| Stop a reply | stop button (`input/buttons/stop-button.tsx`); `POST /api/ai/personas/cancel` (shipped `jupyter_ai_persona_manager/extension.py`) | cancel | JD-049 |
| Markdown answers, code toolbar | markdown through rendermime; copy, insert above/below, replace the active cell | markdown; copy, insert into a new file | JD-054 |
| MCP servers | `.jupyter/mcp_settings.json`, stdio and http, read when a session starts (shipped `default_acp_client.py`, `_get_mcp_servers`); no UI, a restart applies it | a server list with add, start, restart and show output, and `mcp.json` (`contrib/mcp/browser/mcpCommands.ts`) | JD-055 |
| Model, mode, slash commands | pickers fed by `persona_state` over Jupyter Events, which works without RTC (shipped `jupyter_ai_persona_manager/base_persona.py`, `state`) | model picker | JD-056 |

intellij-community@406a27a8da has no agent chat: the AI Assistant is closed. What it ships is
the IDE as an MCP *server* (`plugins/mcp-server`).

## Tasks

- [x] T1 — opencode runs in the `base` Space on the tablet: `opencode --version`, and `opencode acp`
  answers an ACP `initialize` over stdio; the install path that lands it on `PATH` is recorded
- [x] T2 — the image ships the ACP chain and the server answers the `connection` frame on
  `/api/chat/ws/<name>.chat` on the tablet; image size and warm boot measured before and after
- [x] T3a — `:extensions:ai` and its Chat panel: a question goes to the opencode persona, and with
  opencode installed but signed in to nothing, the persona's reply appears beneath it; every gate
  names the new module (signed out, opencode answers from a model of its own rather than "not
  configured")
- [x] T3b — signed in, opencode's answer streams back into one message as it grows
- [x] T4a — the Chat panel keeps the newest message's end in view while the reader is at the bottom,
  and draws no message with an empty body
- [x] T4b — an on-device `python3 -X importtime` profile of the `jupyter_ai_acp_client` import as the
  server makes it, self time summed by top-level package; the verdict on cutting it settled in Open
  decisions
- [x] T4c — the cut the profile justifies, if it justifies one
- [x] T4d — after a relaunch, the question asked before it is still drawn as the user's: every
  socket is a new anonymous user, so "mine" is any sender the chat's users mark as no bot
- [x] T4e — the demo on a fresh install, opencode signed in by a person; ADR, ledger rows, ship
- [x] T5a — a failed socket says what failed: the throwable's class when its message is null, in
  the panel and in a structured log line
- [ ] T5b — an open Chat keeps the Space's server from stopping itself; how is settled in this PRD
  from jupyter_server's own hooks before any code
- [ ] T5c — a dropped socket opens again: the panel shows it reconnecting and reads the chat back,
  with no relaunch
- [ ] T6 — the input as jupyter-chat draws it, measured before and after on SM-X216B
- [ ] T7 — the demo again with a person, steps 1–6

## Out of scope

- Tool approval and stop (JD-049), several chats (JD-053), markdown (JD-054), MCP (JD-055),
  pickers (JD-056), and every agent other than opencode (JD-050): items of their own.
- jupyter-ai's MCP server and its notebook tools: JD-048, after JD-047.
- Jupyternaut and litellm: the user chose ACP agents.
- Attachments, mentions, several chats, and editing a sent message.
