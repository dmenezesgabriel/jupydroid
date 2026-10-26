# JD-056 — The Chat picks the agent's model and mode, and offers its slash commands

Milestone: `M7` · State: in-progress · Depends on: JD-046 · Closes: —

## Outcome

Under the Chat's input, the user sees which model and which mode opencode answers with, changes either
for the questions they send next, and typing `/` lists the commands opencode offers. Today the panel
sends a question and nothing else: `ChatFrames.ask`
(`extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/ChatFrames.kt`) stamps only
`metadata.to_persona`, and nothing reads the persona's state.

## Demo

On SM-X216B, with opencode installed and signed in in base (ADR-0106):

1. Open Chat, New chat. Before any question, a model chip under the input names opencode's current
   model, and a mode chip names its mode (`build`).
2. Tap the model chip: a list of the models opencode offers, the current one marked. Pick another.
   Ask "Which model are you? One line." The answer comes from the model picked.
3. Tap the mode chip, pick `plan`. Ask opencode to create a file; it does not (plan mode does not edit).
4. Type `/`: a list of opencode's commands with their descriptions. Pick one (e.g. `/compact`); it is
   put in the input as `/compact `, sent, and runs.
5. Kill and relaunch, open the same chat: the chips still name the model and mode picked.

The item is not `shipped` until this has been run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai` | FULL | jupyter-ai-persona-manager@d5ed04b (v0.2.2, the frontend the image ships) `src/persona-controls.tsx`, `src/persona-events.ts`, `src/metadata.ts`, `src/slash-commands.ts`; server: the shipped rootfs's `jupyter_ai_persona_manager/persona_events.py`, `base_persona.py`, `jupyter_ai_acp_client/base_acp_persona.py` | Controls in the chat input's toolbar built from the persona's `persona_state` event; the choice is stamped on the next message's metadata; `/` completes from the persona's `slash_commands` |
| `vscode` | FULL | vscode@3f3380c `src/vs/workbench/contrib/chat/browser/widget/input/modelPicker/modelPickerActionItem.ts`, `modePickerActionItem.ts`, `chatPhoneInputPresenter.ts`, `src/vs/sessions/contrib/providers/agentHost/browser/mobile/mobileChatPhoneInputPresenter.ts`, `editor/chatInputCompletions.ts` | A model and a mode picker in the input's toolbar; **on a phone** one bottom sheet, "Configure Session", with an *Agent Mode* section and a *Model* section; `/` completes slash commands |
| `jupyterlab` | ABSENT | — | No chat |
| `vscode-jupyter` | ABSENT | — | No chat input; it contributes tools to VS Code's |
| `intellij-community` | ABSENT | — | AI Assistant is closed source |
| `termux-app` | ABSENT | — | |
| `winlator` | ABSENT | — | |
| the shipped rootfs | FULL (server) | `usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_events.py` | The server half below, already in the image |

The agent: opencode v1.19.3 (anomalyco/opencode@e3c4eb24 `packages/opencode/src/acp/config-option.ts`)
advertises three config options: `model` (category `model`, `<provider>/<model>` ids), `effort`
(category `thought_level`, only for a model with variants) and `mode` "Session Mode" (category `mode`,
its primary agents, default `build`). `acp/service.ts` `prompt`: a prompt whose first word is `/name`
matching an advertised command runs `session.command`; `/compact` summarizes; its commands are its own
plus its skills, sorted.

**Count:** 2 of 7, plus the whole server half in the image. Both put the pickers in the input's own
toolbar, and both complete `/` from the agent's list.

## Implementation inventory

### The shipped rootfs (jupyter_ai_persona_manager 0.2.2, jupyter_ai_acp_client 0.3.3, jupyter_server 2.20.0)

| Concern | Files |
|---|---|
| Domain model | `jupyter_ai_persona_manager/awareness_models.py`: `ModelConfiguration{current, options[ModelOption{id,name,description}], settings[SettingConfiguration]}`, `SettingConfiguration{id,current,name,description,options[SettingOption]}`, `CommandOption{name "/x", description}`, `ModelSpec{id, settings}` |
| ACP → that shape | `jupyter_ai_acp_client/base_acp_persona.py` `_build_awareness_config`: category `model` → the model; category `mode`, else `session/set_mode` state → the general setting `__mode__` (`MODE_CONTROL_ID`); `model_config` → model settings; every other option → a general setting (so opencode's `effort` is one) |
| Port / protocol: reading | `persona_events.py`: `persona_state/v1` `{chat_id, persona_id, model?, settings?, usage?, slash_commands?, processing?}`, **only the changed attribute** per event; a reader merges. Read on `jupyter_server/services/events/handlers.py` `SubscribeWebsocket` (`/api/events/subscribe`), each frame `{"schema_id": …, **data}` |
| Catch-up | `persona_manager.py` `_on_chat_event`: on jupyterlab_chat's `room/v1` `client_connected` (`jupyterlab_chat/chat_manager.py` `on_client_connect`, the JSON socket included) every persona `publish()`es its full state. Events are fire-and-forget, so the events socket must be open **before** the chat socket |
| Eager preparation | A persona has state only once its ACP session exists. `persona_manager.py` `_on_persona_selected`: `persona_selected/v1 {chat_id, persona_id}` prepares it; clients emit it with `POST /api/events {schema_id, version: "1", data}` (`EventHandler.post`, 204) |
| Port / protocol: choosing | `base_persona.py` `apply_specs_in_message`: `metadata.model = {id, settings}` and `metadata.settings = {id: value}` on the next message; null means "keep". No REST setter in 0.3.3 (`jupyter_ai_acp_client/routes.py` holds only permissions) |
| Applying | `base_acp_persona.py` `update_model` → `set_acp_config_option`; `update_settings` routes `__mode__` to the mode option or `set_acp_mode` |
| Persisted state | `base_acp_persona.py` `_record_config_choice` / `_record_mode_choice` write `acp_config_options` / `acp_modes` into the `.chat` file's metadata; `_create_session` reapplies them to a new session, so a choice survives a kill |
| Slash commands | `default_acp_client.py`: `AvailableCommandsUpdate` → `report_slash_commands`; the command itself travels as the message body |
| Error state | A failed reapply is logged and the agent keeps its default; an unknown option id is the agent's to refuse |

### jupyter-ai-persona-manager@d5ed04b (v0.2.2)

| Concern | Files |
|---|---|
| Reading state | `src/persona-events.ts` `PersonaSessionState.withUpdate`: absent attributes carried forward |
| Controls | `src/persona-controls.tsx` `buildControls`: Model first (only when it has options), then model settings, then general settings; `currentControlLabel` shows the effective value's name; `defaultChoiceLabel` "Default (<current>)" heads every list; a menu filters by typing |
| Choice | `src/metadata.ts` `buildMessageMetadata`: the selection per persona, null = the persona's current; stamped on every message |
| Eager preparation | `src/persona-controls.tsx`: emits `persona_selected` when a persona is selected, "so its controls appear before the first message" |
| Slash commands | `src/slash-commands.ts` `SlashCommandProvider`: only when the current word starts with `/`, prefix-matched, `spaceOnAccept` |
| Loading | `LoadingPlaceholder` skeleton while the persona list is in flight |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Model picker | `widget/input/modelPicker/modelPickerActionItem.ts`, `modelPickerWidget.ts` |
| Mode picker | `widget/input/modePickerActionItem.ts` |
| Phone | `widget/input/chatPhoneInputPresenter.ts` (the contract), `vs/sessions/.../mobile/mobileChatPhoneInputPresenter.ts` `showCombinedModeAndModelSheet`: one sheet, "Configure Session", sections *Agent Mode* and *Model*, kept open until Done |
| Slash commands | `widget/input/editor/chatInputCompletions.ts`, `chatSlashCommands.ts` |

**Where they disagree:** jupyter-ai heads each list with "Default (<current>)" and keeps a per-user
selection that rides every message; VS Code selects a value outright. On a phone VS Code collapses the
pickers into one sheet; jupyter-ai has no phone layout.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where state comes from | jupyter-ai → `persona_state` events; VS Code → its own model service | Only the persona knows opencode's options | jupyter-ai's events, on a second socket opened before the chat's |
| How a choice reaches the agent | jupyter-ai → message metadata, applied before the message | No other route exists in the image | Message metadata: the next question carries it |
| What the chip says | jupyter-ai → effective value's name; VS Code → the model's name | — | The effective value's name: the pick if one is pending, else the persona's current |
| A "Default" row | jupyter-ai → yes; VS Code → no | The persona applies and persists a pick, so after one question "Default" *is* the pick | No Default row: the current value is marked in the list |
| Phone layout | VS Code → one bottom sheet for mode and model; jupyter-ai → a dropdown per control | A sheet takes the width a phone has; two chips cost one row | A chip per control in the input's toolbar row, left of Send, each opening a list; revisit as one sheet only if the row overflows on a phone (measured in T2) |
| Slash commands | both → `/` at the first word completes from the agent's list | — | The same: a list above the input while the first word starts with `/` |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The agent's model, mode and commands | the shipped rootfs publishes them | jupyter-ai-persona-manager@d5ed04b `src/persona-events.ts` | absent: nothing subscribes |
| A choice reaching the agent | `ChatFrames.ask` sends `to_persona` only | `src/metadata.ts` | absent |
| Controls in the input | `ui/ChatPanel.kt` `QuestionField`: Stop and Send | `src/persona-controls.tsx` | absent |
| `/` completion | none | `src/slash-commands.ts` | absent |

## Deviations

- No "Default (…)" row (see the trade-off). The persona persists a pick, so it would name the pick.
- Usage and context meters are not shown: not asked for, and a phone has no room for them.
- No persona picker: the panel talks to one persona (ADR-0106); another agent is `JD-050`.

## Open decisions

- **A failed events socket.** The chat must still open and answer; the chips are then absent. Settled
  in T1 with a test.

## Surface

| Layer | Files |
|---|---|
| `domain` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/AgentModels.kt` (T1), `AgentSettings`/`AgentCommands` (T2, T3), `ChatTranscript.kt`, `AgentChat.kt` |
| `data/http` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/PersonaStateFrames.kt`, `OkHttpAgentChat.kt`, `ChatFrames.kt` |
| `data/fake` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/fake/FakeAgentChat.kt` |
| `ui` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/AgentControls.kt`, `ChatPanel.kt` |
| unit tests | `extensions/ai/src/test/java/com/jupydroid/extensions/ai/data/http/PersonaStateFramesTest.kt`, `OkHttpAgentChatModelsTest.kt`, `domain/AgentChatContract.kt` |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ui/shell/ChatAgentControlsTest.kt` |
| `.maestro/` flow + `scripts/` driver | `.maestro/chat-model-picker.yaml`, `scripts/chat-model-picker-driver.sh` (T1), then one per task |

## Tasks

- [x] T1 — The model chip: the chat reads the persona's `persona_state` over `/api/events/subscribe`
  (opened before the chat socket, merged, scoped to the chat's id and opencode's persona; a failed
  events socket leaves the chat working), emits `persona_selected` once the chat is open, and the
  next question carries `metadata.model.id`. A chip left of Send names the model and opens the list.
  Device evidence on SM-X216B
- [x] T2 — The mode and effort chips: general settings beside it, riding as `metadata.settings`;
  the toolbar row measured at one pane on a phone width
- [ ] T3 — `/` lists the persona's commands with their descriptions; picking one puts `/name ` in the
  input
- [ ] T4 — The demo above on SM-X216B; a defect found is fixed red first
- [ ] T5 — The ADR (state from Jupyter Events, the choice on the next message, persisted by the
  persona); delete this PRD; update the backlog

## Out of scope

- Usage, context and cost meters.
- A persona picker, or an agent other than opencode: `JD-050`.
- Opening the chat in a tab: `JD-057`.
