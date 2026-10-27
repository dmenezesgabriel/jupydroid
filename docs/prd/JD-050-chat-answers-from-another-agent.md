# JD-050 — The Chat answers from an agent other than opencode

Milestone: `M7` · State: open · Depends on: JD-046 · Closes: —

## Outcome

With opencode and Claude both installed in base, the Chat offers both, a question goes to the one the
user picked, and each answer says which agent wrote it.

## Demo

On SM-X216B, from a cold start (Claude's adapter needs only base's terminal, but answering runs nothing
the emulator could prove: on x86_64 there is no rootfs, so no persona):

1. Fresh install of the APK; launch; enter base.
2. In base's terminal: install opencode as JD-046's demo did; `apk add nodejs npm`;
   `npm install -g @agentclientprotocol/claude-agent-acp` (the adapter's name since it left
   `@zed-industries`; it needs Node >= 22, and Alpine's is 22); a person signs Claude in, by hand:
   the adapter's `initialize` offers no `authMethods`.
3. Open the Chat. The agent chip offers **OpenCode** and **Claude**.
4. Pick Claude, ask "Which agent are you?". The answer arrives under the name **Claude**, and the model
   and mode controls are Claude's.
5. Pick OpenCode, ask again. The answer arrives under **OpenCode**; the controls are opencode's.
6. Kill and relaunch. The transcript reads back with each answer under its agent's name.

The item is not `shipped` until this has been run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/jd-050-claude-agent/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai` (`jupyter-ai-persona-manager@d5ed04b`, the 0.2.2 the image ships) | FULL | `src/persona-controls.tsx` (`reconcileSelection`, `DEFAULT_PERSONA_ID`), `src/metadata.ts` (`buildMessageMetadata`), `jupyter_ai_persona_manager/persona_manager.py` (`on_chat_message`, `prepare_persona`), `persona_events.py` | a persona picker in the chat input, fed by the `personas/v1` event; the pick rides each message as `metadata.to_persona` |
| `vscode@3f3380c` | FULL | `src/vs/workbench/contrib/chat/browser/widget/input/sessionTargetPickerActionItem.ts`, `src/vs/workbench/contrib/chat/browser/agentSessions/agentSessions.ts` (`AgentHostClaude`) | a session-target picker in the chat input chooses the agent type (local, Claude, Copilot CLI…) |
| `vscode-jupyter@f01d80c` | ABSENT | — | contributes tools to VS Code's chat; no chat or agent of its own (ADR-0106) |
| `intellij-community@406a27a` | PARTIAL | `plugins/ui.webview/demo/resources/webview/views/acp-chat/view.js` (built output only) | a webview *demo* of an ACP chat that holds a `selectedAgentId`; not a product feature |
| `termux-app` | ABSENT | — | no chat |
| `winlator` | ABSENT | — | no chat |
| the shipped rootfs | PARTIAL | `usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/` | personas for claude, codex, copilot, goose, kilo, kiro, mistral_vibe, opencode; each loads only when its CLI is on `PATH` |

**Count:** 2 of 7 implement it, and both put the choice of agent in the chat input. The server side
already exists in our image; what is missing is entirely the app's.

## Implementation inventory

### jupyter-ai-persona-manager@d5ed04b

| Concern | Files |
|---|---|
| Domain model | `jupyter_ai_persona_manager/awareness_models.py` (`PersonaOption`: `id`, `name`, `avatar_url`) |
| Port / protocol | `persona_events.py` (`PERSONAS_EVENT_SCHEMA_ID` `…/personas/v1`, `PERSONA_SELECTED_EVENT_SCHEMA_ID`); `persona_manager.py` (`TO_PERSONA_METADATA_KEY`, `on_chat_message`, `_publish_persona_list`, `_on_chat_event` re-publishing on `client_connected`) |
| UI entry point | `src/persona-controls.tsx` (the persona menu, `NO_ONE_LABEL`, `userPicked`) |
| Persisted state | none: the pick and each persona's settings cache live for the component's lifetime (`settingsCache`) |
| Empty / error / loading states | `showLoadingPlaceholder`; "No one" when the list is empty or a pick vanishes |
| Edge cases it handles | `reconcileSelection`: a sole persona is selected until the user picks; a pick of a persona no longer listed clears; the user's pick, including "No one", sticks |

### jupyter-ai-acp-client@77016f6 (newer than the 0.3.3 shipped)

| Concern | Files |
|---|---|
| Adapter | `jupyter_ai_acp_client/acp_personas/claude.py`: requires `claude-agent-acp` on `PATH` (`PersonaRequirementsUnmet` at import), spawns it, `_is_auth_error` |
| Persona id | `ClaudeAcpPersona` → `jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona`, name `Claude` (`BasePersona.id`, `base_persona.py`; read in the shipped 0.3.3 and persona-manager 0.2.2 on SM-X216B) |

### vscode@3f3380c

| Concern | Files |
|---|---|
| UI entry point | `src/vs/workbench/contrib/chat/browser/widget/input/sessionTargetPickerActionItem.ts` (`ISessionTypeItem`, grouped "Agent Types" / "Other") |

**Disagreement:** jupyter-ai offers "No one" and does not keep the pick; VS Code has no "no agent"
target. Decisions 1 and 2 below settle both.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the list comes from | jupyter-ai → `personas/v1` event; VS Code → registered session types | the event is what the server already sends, re-sent on connect | `personas/v1` on the bus `PersonaEventsSubscription` already reads |
| Default pick | jupyter-ai → sole persona, else page-config default, else none; VS Code → remembered while usable, else a computed default | the image's default (Jupyternaut) is not installed, so jupyter-ai lands on none with two personas; the list's order is entry-point load order, which ranks nothing | decision 2: the pick, else the newest question's persona, else the sole one, else none |
| "No one" | jupyter-ai → offered; VS Code → absent | a question sent to no one is never answered, and the panel waits on it | decision 1: not offered |
| The pick across a relaunch | jupyter-ai → not kept; VS Code → stored, used while usable | the server already saves each question's `metadata.to_persona` in the `.chat` file | decision 2: read back from the newest question, no store of ours |
| Whose answer it is | jupyter-chat → sender's name above each message | needs the `users` map the `connection` frame already carries | the agent's `display_name` above its answer |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Which persona a question goes to | `OPENCODE_PERSONA_ID`, `extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/ChatFrames.kt`; `OkHttpAgentChat(persona = …)` | `src/metadata.ts` | a constant |
| Whose state the controls show | `TranscriptFold(out, persona)` drops any other persona's `persona_state` | `persona-controls.tsx` follows `selectedId` | keyed by the constant |
| `persona_selected` | `PersonaStateFrames.personaSelected` exists | `persona-controls.tsx` emits on each pick | sent for opencode only |
| Persona list | absent | `personas/v1` | absent |
| Sender's name | `ChatMessage` has `fromMe` only (`domain/ChatTranscript.kt`) | jupyter-chat | absent |
| Claude in the Space | no Node in the image (`tools/rootfs/Dockerfile`) | — | the user's install, measured in T1: Node v22.23.4 from apk, the adapter 0.97.0 from npm in 22 s with a 277 MB native Claude Code 2.1.310 that runs under proot, and `initialize` answers protocolVersion 1 |

Ours is **present but unreachable**: the image loads a second persona as soon as its CLI is on `PATH`,
and nothing in the app can address it.

## Deviations

None.

## Decisions

1. **"No one" is not offered.** jupyter-ai offers it for a chat between people; here every chat is the
   user and agents. `on_chat_message` routes a message with no `to_persona` nowhere
   (jupyter-ai-persona-manager@d5ed04b `jupyter_ai_persona_manager/persona_manager.py`), and ADR-0113
   asks the next queued question only once the one before has started a reply, so a question to no one
   would hold the queue forever.
2. **The pick survives a relaunch with no store of ours.** jupyterlab_chat saves a sent message's
   `metadata` in the `.chat` file (jupyter-chat@2c224bc `python/jupyterlab-chat/jupyterlab_chat/websocket_handler.py`,
   `_handle_new_message`); measured, the `.chat` T2 pulled holds `to_persona` on the user's question.
   The persona a question goes to is, in order:
   1. the pick made in the panel, while it is listed;
   2. else the `to_persona` of the user's newest question, while it is listed: VS Code's rule, a
      remembered pick used while it is usable (vscode@3f3380c `src/vs/workbench/contrib/chat/common/constants.ts`,
      `getUsableRememberedSessionType`);
   3. else the sole persona listed (`reconcileSelection`);
   4. else none: the agent chip reads "Choose agent" and Send is off until the user picks. That is where
      jupyter-ai lands in this image, whose default persona (Jupyternaut) is not installed, without its
      trap of sending to no one. No persona is guessed from the list's order, which is entry-point load
      order and ranks nothing.

Both become an ADR amending ADR-0106's "Which chat, which persona" row (T4).

## Surface

| Layer | Files |
|---|---|
| `domain/` | `AgentSelection` gains the persona (`domain/AgentModels.kt`); a persona option; `ChatMessage` gains its sender, `ChatTranscript` the senders' names |
| `data/http` | a `personas/v1` parse beside `PersonaStateFrames.kt`; `TranscriptFold.kt` keyed by the picked persona; `ChatFrames.kt` stamps it and reads the `users` map; `OkHttpAgentChat.kt` loses its persona constant |
| `data/fake` | `FakeAgentChat.kt` lists two personas |
| `ui/` | `AgentControls.kt`: an agent chip, built from `AgentChoiceChip` / `ChoiceList`; `ChatPanel.kt`: the sender's name |
| unit tests | `PersonaStateFramesTest`, `TranscriptFoldTest`, `ChatFramesTest`, `OkHttpAgentChatModelsTest`, `FakeAgentChatTest` |
| instrumented tests | the sender's name is drawn above an answer; the agent chip is there to press, and lists both |
| `.maestro/` flow + `scripts/` driver | `jd-050-claude-acp-in-a-space`, then the pick and the demo |

## Tasks

- [x] T1 — Claude's ACP adapter installs and starts in base: a probe run in base's terminal installs Node
  from apk and `@zed-industries/claude-agent-acp` from npm, and `claude-agent-acp` answers ACP
  `initialize` over stdio under proot (as JD-046's opencode probe did). Records the persona's id in the
  shipped 0.3.3.
- [x] T2 — Each agent answer says which agent wrote it: the sender's name, from the chat's `users` map
  (`connection` and `users` frames), above an agent's message unless the one before is the same
  sender's, as jupyter-chat stacks them; read back after a relaunch (instrumented test + flow on
  SM-X216B, with opencode).
- [ ] T3 — The Chat asks the agent the user picked: `personas/v1` parsed, the selection carries the
  persona, the fold keeps each persona's state and shows the picked one's, and an agent chip in the
  Chat's controls lists the personas and sends `persona_selected` on a pick. Applies decisions 1 and 2
  (instrumented test + flow on SM-X216B).
- [ ] T4 — The demo on a fresh install on SM-X216B; the ADR; this PRD deleted; the backlog row `shipped`.

## Out of scope

- Installing an agent from the app: the agent is the user's, installed in the Space (ADR-0106).
- Agents other than Claude and opencode: each is the same persona path, with its own install.
- Jupyternaut and provider keys (ADR-0106).
