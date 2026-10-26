# ADR-0116 — The Chat's model, mode and commands come from the persona's events, and a pick rides the next question

Status: accepted
Date: 2026-10-26

## Context

The Chat sent a question and nothing else. `ChatFrames.ask`
(`extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/ChatFrames.kt`) stamped only
`metadata.to_persona`, and nothing read what the persona knows about its agent. So the user could not
see which model or mode opencode answered with, could not change either, and had to remember its slash
commands.

The image already ships the server half (jupyter_ai_persona_manager 0.2.2, jupyter_ai_acp_client 0.3.3,
jupyter_server 2.20.0, all under `usr/lib/python3.12/site-packages/` in the shipped rootfs):

- **State.** `jupyter_ai_persona_manager/persona_events.py` emits `persona_state/v1`
  `{chat_id, persona_id, model?, settings?, usage?, slash_commands?, processing?}`. Each event carries
  **only the attribute that changed** (`PersonaSessionState._emit`), so a reader has to merge them. The
  events go out on `/api/events/subscribe` (`jupyter_server/services/events/handlers.py`
  `SubscribeWebsocket`), one frame each, `{"schema_id": …, **data}`, and the socket carries every event
  the server emits.
- **Catch-up.** Events are fire-and-forget. `persona_manager.py` `_on_chat_event` has every persona
  publish its whole state when a client connects to the chat (jupyterlab_chat's `room/v1`
  `client_connected`). Only a subscriber that is already listening receives that state.
- **Preparation.** A persona has state only once its ACP session exists. `_on_persona_selected`
  prepares the persona on `persona_selected/v1 {chat_id, persona_id}`, which a client sends with
  `POST /api/events` (204).
- **Choosing.** `base_persona.py` `apply_specs_in_message` applies `metadata.model = {id, settings}`
  and `metadata.settings = {id: value}` before it answers. A null value keeps the current one. 0.3.3
  has no REST setter: `jupyter_ai_acp_client/routes.py` holds only permissions.
- **Mapping and persistence.** `jupyter_ai_acp_client/base_acp_persona.py` `_build_awareness_config`
  maps the agent's ACP config option of category `model` to the model and its `mode` to the general
  setting `__mode__`. Every other option becomes a general setting. `update_settings` routes
  `__mode__` to the mode option. Once the agent has switched, `_record_config_choice` writes the pick
  into the `.chat` file's `acp_config_options`, and `_create_session` reapplies it to a new session.
- **Commands.** `default_acp_client.py` turns the agent's `AvailableCommandsUpdate` into
  `slash_commands`. A command travels as the message body.

The agent is opencode v1.19.3 (anomalyco/opencode@e3c4eb24). `packages/opencode/src/acp/config-option.ts`
advertises three options:

- `model`: `<provider>/<model>` ids.
- `effort`: offered only for a model with variants.
- `mode`, "Session Mode": its primary agents, `build` by default.

In `acp/service.ts` `prompt`, opencode runs a command only when the prompt's **first word** is `/name`.
In base it lists `/customize-opencode`, `/init` and `/review`, and no `/compact`.

The references:

- jupyter-ai-persona-manager@d5ed04b (v0.2.2, the frontend the image ships):
  - `src/persona-events.ts`: merges the events.
  - `src/persona-controls.tsx`: builds a control per choice in the input's toolbar, shows the effective
    value's name, heads each list with "Default (<current>)" and filters a list as the user types.
  - `src/metadata.ts`: stamps the selection on every message.
  - `src/slash-commands.ts`: completes the word under the caret from the agent's commands.
- vscode@3f3380c:
  - `modelPickerActionItem.ts` and `modePickerActionItem.ts`: a model picker and a mode picker in the
    input's toolbar. Only the model picker can be searched.
  - `src/vs/sessions/contrib/providers/agentHost/browser/mobile/mobileChatPhoneInputPresenter.ts`: on a
    phone, one "Configure Session" sheet.
  - `editor/chatInputCompletions.ts`: completes slash commands.
- jupyterlab, vscode-jupyter, intellij-community, termux-app and winlator have no chat input with
  pickers.

2 of the 7 references have this, and the image already has the whole server half.

## Decision drivers

- Use the path the image already has. No code of ours goes into the image.
- Keep the ADR-0104 boundary: the extension sees only `:extension-api`.
- Name the agent's state before the first question, so the first question can already be asked with a
  pick.
- The chat must keep answering when the events socket fails.
- Fit a phone: the input must not grow.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Where state comes from | the persona's `persona_state` events (jupyter-ai); a model service of the host's own (VS Code) | the events. Only the persona knows what opencode offers |
| How a pick reaches the agent | message metadata (jupyter-ai); a setter call | metadata on the next question. 0.3.3 has no other route |
| What a chip says | the effective value's name (jupyter-ai); the model's name (VS Code) | the effective value's name: the pick if one is pending, else the persona's current value |
| A "Default (…)" row | yes (jupyter-ai); no (VS Code) | no. The persona applies and keeps a pick, so after one question "Default" would name the pick. The value in use is listed first and checked |
| Phone layout | one sheet for mode and model (VS Code); a control per choice (jupyter-ai) | a chip per choice in the toolbar row Send already sets. Two chips fit, and the row did not grow |
| Finding a model | a filter field (jupyter-ai, VS Code's model picker); scrolling | a search field the model list opens on, matching name or id. Mode and effort lists get none, as VS Code's mode picker has none |
| Slash commands | the word under the caret (jupyter-ai); the first word | the first word, because opencode runs a command only there |

## Decision

**The Chat reads the persona's state from Jupyter Events, shows the model, each general setting and the
commands from it, and sends a pick as metadata on the next question. The persona applies the pick and
keeps it with the chat.**

- `PersonaEventsSubscription` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/PersonaEventsSubscription.kt`)
  opens `/api/events/subscribe` and **waits until it is open before the chat's socket opens**, so the
  catch-up on `client_connected` reaches it. `PersonaStateFrames` (same folder) keeps `persona_state`
  frames, drops every other event, and builds the `persona_selected` body. `OkHttpAgentChat` posts that
  body once the chat is open. `TranscriptFold.stated` keeps only opencode's persona's states and merges
  each part of them by chat id, because the catch-up can arrive before the chat's own id is known. The
  transcript shows only this chat's part.
- `ChatFrames` stamps `metadata.model = {id, settings: {}}` when a model is picked, and
  `metadata.settings` when a setting is.
- The domain types live in `extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/`:
  - `AgentChoice`: the value in use, its name, and the listing for a query.
  - `AgentModels` and `AgentSetting`: the model and each general setting.
  - `AgentSelection`: kept per chat in `ChatPanel`. `listedIn` drops a setting the agent no longer lists,
    as opencode's `effort` disappears with a model that has no variants.
  - `AgentCommand.completing`: the commands the field could still become.
- `ui/AgentControls.kt` draws one chip per choice, left of Send. `ui/SlashCommands.kt` lists the
  matching commands above the field, each name over its description.

Measured on SM-X216B (240 dpi, portrait, one pane):

- **The input did not grow.** With the model and mode chips the input box is 173 px, field 76 px and
  toolbar row 97 px, exactly as with Send alone.
- **The model list stays short.** With opencode signed in to OpenRouter, the model chip offered 519
  models. The list now opens on "Search models" and stops at three fifths of the window, the 60vh
  jupyter-ai uses: 1164 px of 1920. Before that cap it ran to the foot of the screen.
- **The command list stays short.** The `/` list stops at two fifths of the window. Three commands
  took 336 px.
- **A pick is applied.** A question asked with `openrouter/cohere/north-mini-code:free` picked was
  answered by that model.
- **Plan mode held.** Asked to create a file in `plan`, opencode refused, and no edit completed.
- **A command ran.** `/review` picked and sent ran opencode's review subtask.
- **A pick survives a kill.** After a kill and relaunch, both chips named the pick, read back from the
  `acp_config_options` the persona wrote.

## Consequences

- **The persona owns the pick.** Once a pick has been applied, the persona reports it as its current
  value, so the panel holds a pick only until the next question confirms it. A pick survives a kill
  because the persona keeps it, not because the app stores anything.
- **The chips appear only once the session exists.** A new chat posts `persona_selected` to prepare
  the persona, so the chips can be empty for the moment the agent takes to start.
- **The events socket carries every server event.** It is the whole server's bus. The app parses each
  frame and drops what is not a `persona_state` for its own chat and persona.
- **A failed events socket is logged as `chat.events.failed`.** The chips keep what they last said, or
  never appear, and the chat still answers
  (`OkHttpAgentChatModelsTest.aChatWhoseServerRefusesTheEventBusStillAnswers`).
- **A command is completed only as the first word.** jupyter-ai completes one anywhere, but opencode
  would send a command in mid-sentence as plain text. Revisit this when an agent runs commands
  anywhere.
- **Not shown.** There are no usage, context or cost meters, which a phone has no room for and nobody
  asked for. There is no persona picker: the panel talks to one persona (ADR-0106).
- **Do not add a setter call or a host-side store for the pick.** 0.3.3 has no REST setter, and the
  persona already persists the pick in the `.chat` file. A second store would disagree with it the
  first time the agent refused a value.
