# ADR-0113 — A question sent while the agent answers waits in a queue kept per chat

Status: accepted
Date: 2026-10-24

## Context

[ADR-0112](0112-an-agents-tool-calls-are-rows-that-ask-and-stop-cancels-its-reply.md) put Stop
beside Send and left Send in its place, enabled while a reply arrives. Each tap of Send asked at
once. The server in the image does not hold a second question for the first to finish. Every path
below is under `usr/lib/python3.12/site-packages/` in the shipped rootfs:

- jupyter_ai_acp_client 0.3.3 `default_acp_client.py` `prompt_and_reply` serialises prompts on a
  per-session `asyncio.Lock`. Before it waits on that lock it sets `_cancel_requested` and calls
  `_cancel_pending_work`. That drops the rest of the reply in flight from the chat, marks its unfinished
  tool calls failed and rejects its pending permission requests. It sends no `session/cancel`, so the
  second question waits for the agent to finish a turn the chat no longer shows.
- jupyter_ai_persona_manager 0.2.2 `base_persona.py` sends a `writing` frame once the persona holds
  the lock, and clears it in `prompt_and_reply`'s `finally`. The echo of a question arrives before
  that frame, while nothing writes.

The references:

- vscode@3f3380c `src/vs/workbench/contrib/chat/browser/actions/chatQueueActions.ts` offers Add to
  Queue and Steer with Message while a request is in progress. `chatServiceImpl.ts`
  `processNextPendingRequest` takes the oldest pending request off the queue and sends it when the
  turn completes. `chatModel.ts` saves `pendingRequests` with the session. Its pending rows offer
  Edit, Remove, Send Immediately and Remove All Queued, and are drawn in the list after a divider.
- jupyter-chat-components@992c6fb `src/components/message-queue.tsx`, that server's own frontend,
  draws an "N queued" box above the input with a row per message, edit, remove and drag to reorder.
  The queue itself is held by the client that renders it (jupyterlite/ai#302), which keeps it in
  memory.
- jupyter-chat@2c224bc disables Send only for an empty input (`send-button.tsx`).
- vscode-jupyter, intellij-community, termux-app and winlator have no agent chat.

1 of the 8 references has this in full and 1 in part. Neither has a server that queues. Both hold
the queue in the client.

## Decision drivers

- A question must never cut a reply short. That is the defect.
- Where jupyter-ai's own frontend has decided something, decide the same.
- A question the user sent is theirs. A kill must not lose it, and must not ask it twice.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Who holds the queue | the server; the client | the client, as both references do. No server in the image queues |
| Where pending rows are drawn | in the list after a divider (VS Code); in a box above the input (jupyter-chat-components) | the box. It stays in view on a phone while the reply scrolls |
| Actions on a row | Edit, Remove, Send Immediately, Steer, Remove All (VS Code); edit, remove, reorder (jupyter-chat-components) | edit and remove. Send Immediately and Steer cut the reply, which is the defect |
| When the next is asked | when the turn completes (VS Code) | when no agent writes **and** the question asked before has been seen to start a reply |
| How long the queue lasts | with the session (VS Code); the client's memory (jupyter-chat-components) | the app's own storage, one file per Space, by chat path |
| Where on disk | a sidecar in the workspace; the app's own storage | the app's own storage. jupyter_server refuses hidden files, so a sidecar would show in the explorer, and per-Space bookkeeping (recents) already lives in `filesDir` |

## Decision

**Every Send puts the question in the chat's queue. One is asked only when no agent writes and the
question asked before it has started its reply. Waiting questions are drawn in an "N queued" box
above the input, each with a pencil to edit it in place and a × to remove it, and they are kept per
chat in the app's own storage until they are asked.**

- `QuestionQueue` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/QuestionQueue.kt`)
  holds the pending questions, oldest first. `due` answers null while an agent writes, while the
  question asked before has not yet seen a `writing: true` frame (`awaitingReply`), and while the
  oldest is being edited (`held`). With nothing writing and nothing in flight, a question is asked at
  once, as VS Code's Add to Queue sends at once when no request is in progress.
- `awaitingReply` exists because the echo of an asked question arrives while `writing` is still
  false. Without it, that echo would release the next question, and the server would cancel the first
  one's turn.
- Remove and edit find a question by its text, not its place. The oldest can be asked between two
  frames, so a row's place can shift under a tap.
- `QueuedQuestionsStore` (`.../domain/QueuedQuestionsStore.kt`) keeps them. `DiskQueuedQuestionsStore`
  writes `filesDir/chat-queues/<spaceId>.json`, chat path to questions, through `AtomicFile`.
  `FakeQueuedQuestionsStore` keeps them in memory. `SidePanelHost.spaceId` gives the extension the
  Space, and `JupydroidApplication` passes `AiExtension` the store per Space.
- `ChatPanel` reads what was kept when a chat opens and holds Send and the queue until that read
  lands, so an empty start cannot overwrite it. Every change to the queue is written. A due question
  is taken off what is kept **before** it is asked, as `processNextPendingRequest` dequeues before
  sending: a kill between the two loses it rather than asking it twice.
- A rename from the Chat list moves the chat's questions to its new path (`QueuedQuestionsStore.move`,
  one file replace), once the file has moved. A delete from the list forgets them, once the file is
  deleted. A refused rename moves nothing.

## Consequences

- Reopened after a kill, a chat's kept questions go through the same rule. The new server has no reply
  in flight, so they are asked at once.
- In a Space whose agent never writes, the queue waits forever. It stays visible, and each row can be
  removed.
- A rename or delete made outside the Chat list (the explorer, JupyterLab) leaves the old path's entry.
  A later chat given that name gets its questions.
- An unreadable store file costs the questions in it, not the chat (`readOrNone`). The next write
  replaces it.
- The box takes its height from the message list, so a long queue leaves less of the reply in view.
  While the panel follows the newest line, it scrolls back to the end whenever the list's height
  changes (`FollowTheNewest` in `ChatPanel.kt`). Without that, the box's arrival left a call's Allow?
  under it on SM-X216B.
- Do not add Steer or Send Immediately. Both cut the reply in flight.
- Do not release the next question on `writing: false` alone. The echo arrives while nothing writes.
- Do not move the queue into the `.chat` file. It is the server's, and a question written there is
  one the server has been asked.
