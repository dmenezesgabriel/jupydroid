# JD-046 — A Chat panel answers from an ACP agent installed in the Space

Milestone: `M7` · State: in-progress · Depends on: JD-045 · Closes: —

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
  needs no tool. Approval is the next item.
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
- Warm server boot grew by about 1.7 s with the chain (median 2.92 s → 4.66 s on SM-X216B), 1.29 s
  of it importing `jupyter_ai_acp_client`. Whether to cut it, and how, is decided on a profile of
  that import (`python3 -X importtime` on the device), not before.
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
- [ ] T4b — an on-device `python3 -X importtime` profile of the `jupyter_ai_acp_client` import; the
  demo on a fresh install; ADR, ledger rows, ship

## Out of scope

- Tool approval, and every agent other than opencode: items of their own once this ships.
- jupyter-ai's MCP server and its notebook tools: JD-048, after JD-047.
- Jupyternaut and litellm: the user chose ACP agents.
- Attachments, mentions, several chats, and editing a sent message.
