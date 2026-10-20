# JD-049 — The Chat shows an agent's tool calls, asks before one runs, and stops a reply

Milestone: `M7` · State: in-progress · Depends on: JD-046, JD-053 · Closes: —

## Outcome

In the Chat panel a user sees each tool call the agent makes, as a row with its status. When a call
needs approval they pick one of the agent's own options (Allow once, Always allow, Reject), and they
can stop a reply that is still arriving. Today the panel draws none of these: a message that holds
only tool calls is hidden, and a question that needs a shell command waits forever on a permission
request nobody can see. ADR-0106's consequences record this.

## Demo

On SM-X216B, from a fresh install, with opencode installed and signed in in base (ADR-0106):

1. Open Chat, New chat. Ask opencode to read `probe.txt` with its read tool and answer with the word
   in it. A row `✓ … probe.txt` appears between the question and the answer.
2. Ask it to run `echo permission-probe`. A row appears with **Allow?** and the agent's own options.
   Tap **Allow once**. The row shows the chosen option and then ✓, and the answer holds
   `permission-probe`.
3. Ask it to run `echo second-probe`, and tap **Reject**. The row shows ✗, and nothing ran.
4. Ask for a long answer (count to 300, one number a line). While it arrives a **Stop** button
   shows beside Send. Tap it. The text stops growing and Stop goes away.
5. Kill and relaunch, then open the chat. The rows are still there with their last status, because
   they are in the `.chat` file.

The item is not `shipped` until this has run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai` (the server in the image) | FULL | jupyter_ai_acp_client 0.3.3 `tool_call_manager.py`, `tool_call_renderer.py`, `default_acp_client.py`, `permission_manager.py`, `routes.py`, `extension_app.py`; jupyter_ai_persona_manager 0.2.2 `handlers.py` `CancelHandler`, `base_persona.py` (all under `usr/lib/python3.12/site-packages/` in the shipped rootfs) | Calls go in a message's `metadata.tool_calls`. A permission is answered by `POST ai/acp/permissions` and a reply is stopped by `POST api/ai/personas/cancel` |
| jupyter-chat-components (that server's frontend rows) | FULL | jupyter-chat-components@992c6fb `src/components/grouped-tool-calls.tsx`; jupyter-ai-acp-client@77016f6 `src/request.ts` | One row per call: a mark, a title, and "Allow?" followed by a button for each of the agent's options |
| `jupyter-chat` | PARTIAL | jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/buttons/stop-button.tsx`, `.../input/toolbar-registry.tsx` | A Stop button at position 90 beside Send (100), enabled while a bot is writing. Its click is a TODO, and the registration is commented out |
| `vscode` | FULL | vscode@3f3380c `src/vs/workbench/contrib/chat/browser/widget/chatContentParts/toolInvocationParts/chatToolInvocationPart.ts`, `.../chatToolConfirmationSubPart.ts`; `src/vs/workbench/contrib/chat/browser/actions/chatExecuteActions.ts` `CancelAction` | Tool invocation parts with Allow Once and Skip. Cancel (`Codicon.stopCircle`) takes Send's place in the execute menu while a request is active |
| `vscode-jupyter` | ABSENT | — | Contributes tools to VS Code's chat and has no chat of its own (ADR-0106) |
| `intellij-community` | ABSENT | — | AI Assistant is a closed, separate plugin |
| `termux-app` | ABSENT | — | |
| `winlator` | ABSENT | — | |

**Count:** 3 of 8 have it in full and 1 in part. Every reference that has a chat with agents shows tool
calls, asks before running one, and offers a way to stop.

## Implementation inventory

### jupyter_ai_acp_client 0.3.3 (the shipped rootfs, the wire we read)

| Concern | Files |
|---|---|
| Domain model | `tool_call_renderer.py` `ToolCallState`: `tool_call_id`, `title`, `kind`, `status`, `raw_input`, `raw_output`, `locations`, `permission_options`, `permission_status` (`pending`/`resolved`), `selected_option_id`, `session_id`, `diffs` |
| Port / protocol | `acp/schema.py`: `ToolCallStatus` = `pending`/`in_progress`/`completed`/`failed`; `PermissionOptionKind` = `allow_once`/`allow_always`/`reject_once`/`reject_always`; `PermissionOption` `option_id`, `name`, `kind` |
| Adapter | `tool_call_manager.py`. Consecutive calls share one message with `body=""`, whose `metadata` is replaced by `{"tool_calls": [state.model_dump(exclude_none=True)…]}` on every change, so the keys are snake_case. A text chunk after them starts a new message |
| Approval | `default_acp_client.py` `request_permission` sets `permission_options`, `permission_status="pending"` and `session_id`, flushes, and awaits. `routes.py` `PermissionHandler` (`POST ai/acp/permissions`, `{session_id, tool_call_id, option_id}`) resolves it, or returns 404 when nothing is pending |
| Stop | persona_manager `handlers.py` `CancelHandler`: `POST api/ai/personas/cancel?chat_id=<id>`, keyed by `chat.get_id()`, which over the JSON socket is the `connection` frame's `id` (jupyterlab_chat `websocket_model.py`). It cancels only personas that are `processing`. `default_acp_client.py` `stop_streaming` then marks unfinished calls `failed` and cancels any pending permission |
| Writing state | jupyterlab_chat `ws_messages.py` `ServerWritingMessage` (`action: "writing"`, `user`, `state`), sent by `base_persona.py`'s `broadcast_writing_status` and `clear_status` |
| Real sample | `evidence/jd-046-ai-chat/t4a/tool-call/untitled.chat`: a `read` call, `completed`, with `locations` and `title` `home/jupydroid/probe.txt` |

### jupyter-chat-components@992c6fb

| Concern | Files |
|---|---|
| UI entry point | `src/components/grouped-tool-calls.tsx` `ToolCallRow`, `PermissionButtons`, `PermissionLabel` |
| Title | `getToolCallDisplayTitle`: the title if one is set, otherwise `TOOL_KIND_LABELS[kind]` and the basename of the first location, otherwise `Working...` |
| Status mark | • for `in_progress`, `pending` or a pending permission; ✓ for `completed`; ✗ for `failed`, or when the chosen option's kind contains `reject` |
| Approval | "Allow?" and one button per `permissionOptions`, labelled by `name`, all disabled while one is being submitted. Once resolved, " - <chosen name>" follows the title |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Confirmation | `chatToolConfirmationSubPart.ts`: Allow Once, or Skip ("Proceed without running this tool") |
| Stop | `chatExecuteActions.ts` `CancelAction`, `workbench.action.chat.cancel`, shown when `ChatContextKeys.hasActiveRequest` |

**Disagreements.** VS Code fixes the approval buttons itself, where jupyter-chat-components draws
whatever options the agent sends. VS Code puts Cancel in Send's place, where jupyter-chat puts Stop
beside Send.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where calls come from | `metadata.tool_calls` (jupyter-ai) | — | the message's metadata as the server stores it. The calls then persist in the `.chat` file, and a relaunch shows them without anything new |
| How a row looks | jupyter-chat-components: mark + title; VS Code: a part with an icon and a message | the first is what the server's own frontend draws | jupyter-chat-components' row, with its title fallback and marks |
| Approval buttons | the agent's options (jupyter-chat-components); a fixed pair (VS Code) | ACP lets the agent name its options, and opencode sends three | the agent's `permission_options`, labelled by `name` |
| Stop's place | beside Send (jupyter-chat); in Send's place (VS Code) | replacing Send takes away what JD-060 gives Send during a reply | beside Send, shown only while a bot is writing (`writing` frames), as jupyter-chat enables it |
| Stop's target | `api/ai/personas/cancel` | — | the cancel route, with the chat id from the `connection` frame |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Tool-call message | hidden: `ChatTranscript.messagesWithText` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/ChatTranscript.kt`) | `grouped-tool-calls.tsx` | absent |
| `metadata` on the wire | dropped: `WireMessage` reads only `id`, `body`, `sender` (`data/http/ChatFrames.kt`) | `tool_call_manager.py` | absent |
| Approval | no route call | `routes.py`, `src/request.ts` | absent |
| `writing` frame | `ServerFrame.Other` | `ws_messages.py` | absent |
| Stop | no route call | `handlers.py` `CancelHandler` | absent |

## Deviations

- No call's input, output or diff is expanded. jupyter-chat-components folds them into a
  `<details>`. A row here is the mark, the title and, while a permission is pending, the options.
  Expanding a row is a later item.

## Open decisions

- None. The "Connection closed" that JD-046 saw about two minutes after an unanswered permission
  request does not recur once the request is answered: T2's run answered two, and the agent still
  answered four minutes later with none in the server log (`evidence/chat-tool-calls-approval/`).

## Surface

| Layer | Files |
|---|---|
| domain | `extensions/ai/.../domain/ChatTranscript.kt` (`ToolCall`), `domain/AgentChat.kt` (T2 `choose`, T3 `stop`) |
| `data/http` | `ChatFrames.kt`, `OkHttpAgentChat.kt` |
| `data/fake` | `FakeAgentChat.kt` |
| ui | `ui/ChatPanel.kt`, and `ui/ToolCallRows.kt` if a separate file is needed |
| unit tests | `ChatFramesTest`, `ChatTranscriptTest`, `OkHttpAgentChatTest`, `FakeAgentChatTest`, `AgentChatContract` |
| instrumented tests | a row is visible for a call, its options can be pressed, and Stop is visible while an agent writes |
| `.maestro/` flow + `scripts/` driver | `chat-tool-calls-*.yaml`, `scripts/chat-tool-calls-*-driver.sh` |

## Tasks

- [x] T1 — Each tool call is a row with its status. `ChatMessage` gains its tool calls (id, title,
  status: running, completed or failed). `ChatFrames` reads `metadata.tool_calls`, and a message is
  shown when it has text or calls. The panel draws a mark and a title for each call. Tablet: the
  read-probe question shows a ✓ row for `probe.txt` between the question and the answer.
- [x] T2 — A call awaiting approval offers the agent's options. The permission is pending (session id
  and options) or resolved (the chosen name). `AgentChat` gets an operation that chooses an option,
  which `OkHttpAgentChat` POSTs to `ai/acp/permissions`. Tablet: Allow once runs `echo`, and Reject
  shows ✗.
- [ ] T3 — Stop cancels the reply in progress. `writing` frames say an agent is writing, and
  `AgentChat` gets an operation that stops it, a POST to `api/ai/personas/cancel?chat_id=<id>`. Stop
  shows beside Send while an agent writes. Tablet: a long answer stops growing.
- [ ] T4 — The demo from a fresh install. Then an ADR for tool calls, approval and stop, an
  amendment to ADR-0106's consequence about permission requests, and this PRD deleted.

## Out of scope

- Expanding a call's input, output or diff (a later item).
- Sending a question while a reply arrives (`JD-060`).
- Markdown in answers (`JD-054`).
