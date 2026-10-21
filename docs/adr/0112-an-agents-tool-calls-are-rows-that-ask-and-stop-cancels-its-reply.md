# ADR-0112 — An agent's tool calls are rows that ask, and Stop cancels its reply

Status: accepted
Date: 2026-10-21

## Context

[ADR-0106](0106-the-chat-panel-answers-from-an-acp-agent-the-user-installs-in-the-space.md) left the
Chat panel unable to show what an agent does. A message that held only tool calls had an empty body,
so it was not drawn. A question that needed a shell command waited forever on a permission request
nobody could see. A reply could not be stopped.

The server in the image already does all three. Every path below is under
`usr/lib/python3.12/site-packages/` in the shipped rootfs:

- jupyter_ai_acp_client 0.3.3 `tool_call_manager.py` puts consecutive calls in one message with
  `body=""`. On every change it replaces that message's `metadata` with
  `{"tool_calls": [ToolCallState…]}`, with snake_case keys (`tool_call_renderer.py`). A text chunk
  after the calls starts a new message.
- `default_acp_client.py` `request_permission` sets the call's `permission_options`,
  `permission_status: "pending"` and `session_id`, then waits. `routes.py` `PermissionHandler`
  (`POST ai/acp/permissions` with `{session_id, tool_call_id, option_id}`) resolves the wait, or
  returns 404 when nothing is pending.
- jupyter_ai_persona_manager 0.2.2 `handlers.py` `CancelHandler`
  (`POST api/ai/personas/cancel?chat_id=<id>`) cancels the personas that are processing in that
  chat. `stop_streaming` then marks unfinished calls `failed` and cancels any pending permission.
  `base_persona.py` sends a `writing` frame when a reply starts and another when it ends.

The references:

- jupyter-chat-components@992c6fb `src/components/grouped-tool-calls.tsx` is that server's own
  frontend. It draws one row per call: a mark (• running, ✓ completed, ✗ failed or rejected), the
  call's title (or its kind and the basename of its first location, or `Working...`), and, while a
  permission is pending, "Allow?" with a button for each of the agent's options. Once the permission
  is resolved, the title is followed by " - <chosen name>". jupyter-ai-acp-client@77016f6
  `src/request.ts` makes the POST.
- jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/buttons/stop-button.tsx` puts Stop
  at position 90, beside Send at 100, enabled while a bot is writing. Its click is a TODO and its
  registration is commented out.
- vscode@3f3380c draws tool invocation parts with a fixed Allow Once and Skip
  (`chatToolConfirmationSubPart.ts`). Its `CancelAction` (`chatExecuteActions.ts`) takes Send's place
  while a request is active.
- vscode-jupyter, intellij-community, termux-app and winlator have no agent chat.

3 of the 8 references have this in full and 1 in part. Every reference that has an agent chat shows
the calls, asks before running one, and offers a way to stop.

## Decision drivers

- What the server stores is the truth. A row drawn from anything else is not the agent's.
- Where jupyter-ai's own frontend has decided something, decide the same.
- A row must survive a relaunch without anything new being stored.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Where calls come from | the message's `metadata.tool_calls`; a client-side log of frames | the metadata as the server stores it, so the calls are in the `.chat` file and come back after a kill |
| How a row looks | jupyter-chat-components' mark and title; VS Code's part with an icon and a message | jupyter-chat-components', with its title fallback and marks |
| Approval buttons | the agent's options (jupyter-chat-components); a fixed pair (VS Code) | the agent's `permission_options`, labelled by `name`. ACP lets the agent name its options, and opencode sends three |
| Stop's place | beside Send (jupyter-chat); in Send's place (VS Code) | beside Send. Send keeps its own place for asking while a reply arrives |
| When Stop shows | while a request is active; while a bot writes | while a `writing` frame from a bot says one is writing, which is when `CancelHandler` has a persona to cancel |
| Stop's target | `api/ai/personas/cancel` | it, with the chat id from the socket's `connection` frame, which is what `chat.get_id()` returns over the JSON socket (jupyterlab_chat `websocket_model.py`) |

## Decision

**The Chat panel draws each tool call the server stores in a message's `metadata.tool_calls` as a
row with its status. A call that waits on the user offers the agent's own options, and the panel
POSTs the chosen one to `ai/acp/permissions`. While an agent writes, Stop sits beside Send and POSTs
to `api/ai/personas/cancel`.**

- `ToolCall`, `ToolCallStatus` and `ToolCallPermission`
  (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/`) model a call. A message is drawn
  when it has text or calls. A call the user rejected is shown as failed, whatever the agent reports
  after, as `ToolCallRow`'s `isRejected` marks it.
- `AgentChat` gains `choose(call, option)` and `stop()`, which `OkHttpAgentChat` makes as the two
  POSTs above and `FakeAgentChat` answers in memory. `ChatFrames` reads a `writing` frame from a
  bot into `ChatTranscript.agentWriting`.
- `ui/ToolCallRows.kt` draws a row: the mark, the title, and while a permission is pending,
  "Allow?" and the options. The panel scrolls to a call when it comes to ask.
- The panel follows the newest line until the reader drags away from the end, and judges that only
  when a drag ends (`ChatPanel.kt`, `FollowTheNewest`). On SM-X216B, once a chat held answered calls,
  the keyboard coming up for the next question moved the list back an item with no drag. Judged on
  every move, that ended the following, and the answer and Stop streamed out of sight.

## Consequences

- A relaunch shows every row with its last status, because the rows are read from the `.chat` file
  and nothing else is kept.
- A call's input, output and diff are not shown. jupyter-chat-components folds them into a
  `<details>`. Expanding a row is a later item.
- Do not fix the approval buttons to a pair. The agent names its options, and Always allow is one of
  opencode's.
- Do not move Stop into Send's place. Send keeps its place for queueing a question while a reply
  arrives.
- Do not judge the following on every move of the list. Layout moves it too, and only a drag is
  the reader leaving. A screen reader's scroll is not a drag, so it does not end the following
  either.
- Do not show Stop on anything but a bot's `writing` frame. `CancelHandler` cancels only personas
  that are processing, so a Stop shown at any other time does nothing.
- An answered permission request does not close the agent's connection. On SM-X216B, after two
  requests were answered, opencode still answered a question four minutes later.
