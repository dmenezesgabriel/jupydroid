# ADR-0119 — The Chat asks the agent the user picked, from the personas the server lists

Status: accepted
Date: 2026-10-27

## Context

The Chat addressed every question to opencode's persona, a constant
([ADR-0106](0106-the-chat-panel-answers-from-an-acp-agent-the-user-installs-in-the-space.md), "Which
chat, which persona"). The image already ships a persona for each of eight ACP agents (claude, codex,
copilot, goose, kilo, kiro, mistral_vibe, opencode, in
`usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/` of the shipped rootfs), and each
loads as soon as its CLI is on `PATH`. So a second agent installed in a Space was loaded by the server
and could not be reached from the app.

Claude is that second agent, and installing it needs nothing from the image. Measured on SM-X216B from
base's own terminal: Node v22.23.4 comes from Alpine's repositories (`apk add nodejs npm`), and
`@agentclientprotocol/claude-agent-acp` 0.97.0 comes from npm in 22 s. The adapter was renamed from
`@zed-industries/claude-agent-acp`, which is still the name the persona's message gives, and it needs
Node >= 22. Its native Claude Code 2.1.310, 277 MB, runs under proot. `claude-agent-acp` answers ACP
`initialize` with protocolVersion 1 and `authMethods: []`. The person therefore signs Claude in by hand
with the bundled `claude auth login`, approved from a browser opened as a pop-up over the app, because a
full-screen browser freezes the app and the sign-in fails (GAP-179). The persona's id is
`jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona` and its name is `Claude`
(`BasePersona.id`, `base_persona.py`, in jupyter_ai_acp_client 0.3.3 and jupyter_ai_persona_manager 0.2.2
as shipped).

Of the seven references, two let the user choose the agent, and both put the choice in the chat input.
jupyter-ai has a persona menu fed by the `personas/v1` event, and the pick rides each message as
`metadata.to_persona` (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`, `src/metadata.ts`,
`jupyter_ai_persona_manager/persona_manager.py`). VS Code has a session-target picker
(vscode@3f3380c `src/vs/workbench/contrib/chat/browser/widget/input/sessionTargetPickerActionItem.ts`).
intellij-community@406a27a has only a webview demo of an ACP chat, and vscode-jupyter, termux-app and
winlator have no chat. The two that choose disagree on two points. jupyter-ai offers "No one" and
forgets the pick when the component goes away. VS Code has no "no agent" target and remembers the
pick while it can still be used.

## Decision drivers

- The server already lists the personas and routes on `to_persona`, so the app adds a picker and no
  protocol.
- A question must always reach an agent. The Chat is the user talking to agents, not people talking to
  each other.
- No store of the app's own when the `.chat` file already holds the answer.
- Guess nothing. The list's order is entry-point load order, which ranks nothing.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Where the list comes from | `personas/v1` (jupyter-ai); registered session types (VS Code) | `personas/v1`, on the events socket `PersonaEventsSubscription` already reads. The server re-publishes it on `client_connected` (`_on_chat_event`) |
| "No one" | offered (jupyter-ai); absent (VS Code) | absent. `on_chat_message` routes a message with no `to_persona` nowhere, and [ADR-0113](0113-a-question-sent-while-the-agent-answers-waits-in-a-queue-kept-per-chat.md) asks the next queued question only once the one before has started a reply, so a question to no one would hold the queue forever |
| The pick across a relaunch | not kept (jupyter-ai); stored, used while usable (VS Code) | VS Code's rule with no store. jupyterlab_chat saves each sent message's `metadata` in the `.chat` file (jupyter-chat@2c224bc `python/jupyterlab-chat/jupyterlab_chat/websocket_handler.py`, `_handle_new_message`), so the newest question's `to_persona` is the remembered pick |
| The default with several agents | the sole persona, else page config's default, else none (jupyter-ai); a computed default (VS Code) | none. The image's default, Jupyternaut, is not installed, so jupyter-ai lands on none here too |
| Whose answer it is | the sender's name above a message (jupyter-chat); nothing | the sender's `display_name` from the chat's `users` map, which the `connection` and `users` frames carry. It is drawn above an agent's message unless the message before it has the same sender, as jupyter-chat stacks them |

## Decision

**The Chat lists the personas the server publishes in an agent chip in its input. A question goes to
the agent the chip names, as `metadata.to_persona`, and each answer is drawn under its agent's name.**

The agent a question goes to is, in order (`AgentPersonas`,
`extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/AgentPersonas.kt`):

1. the pick made in the panel, while that persona is listed;
2. else the `to_persona` of the user's newest question, while it is listed;
3. else the sole persona listed;
4. else none: the chip reads "Choose agent", and Send is off until the user picks.

A pick sends `persona_selected`, so the persona starts its session and publishes its state. The fold
keeps each persona's `persona_state` and shows the chosen one's, so the model, mode and effort chips
([ADR-0116](0116-the-chats-model-mode-and-commands-come-from-the-personas-events-and-ride-the-next-question.md))
are always the chosen agent's.

## Consequences

- An agent is added in the Space, not in the app. Any persona the image ships appears in the chip once
  its CLI is on `PATH` when the Space's server starts. Codex, Goose and the rest go the same way, each with its
  own install.
- After a relaunch the chip names the newest question's agent, but its model and mode chips stay empty
  until that persona publishes its state again: a persona's state is per session, and a new server has
  none yet.
- An agent that is uninstalled drops out of the list, so neither step 1 nor step 2 can choose it, and the
  chip falls back to the sole agent left, or to "Choose agent".
- A sender the `users` map does not name is drawn under its username, which for an agent is its persona
  id (`ChatTranscript.senderName`).
- Four chips (agent, model, mode, effort) share the toolbar row with Send at one pane, and the model
  chip ellipsizes. The row was kept at its height (ADR-0116). How a long name is shown is an open
  question of its own.
