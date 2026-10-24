# JD-060 — A question asked while the agent answers waits its turn

Milestone: `M7` · State: in-progress · Depends on: JD-049 · Closes: GAP-177

## Outcome

While an agent's reply is still arriving, a user can send their next question without cutting the reply
short. The question waits as a pending row they can edit or remove, it is sent when the reply ends, and
it is still there after the app is killed. Today Send asks at once, and jupyter-ai's ACP client drops the
rest of the reply in flight ([GAP-177](../architecture.md)).

## Demo

On SM-X216B, with opencode installed and signed in in base (ADR-0106):

1. Open Chat, New chat. Ask for a count from 1 to 1000, one number a line.
2. While it arrives, type "What is 2+2?" and tap Send. A box above the input says **1 queued** and
   holds the question. The count keeps arriving and Stop stays beside Send.
3. Queue two more. Edit one in place, and remove the other with its ×.
4. The count ends at 1000. The oldest pending question is asked, and its answer arrives under the
   count. Then the edited one is asked, and the box is gone.
5. Ask for the count again, queue a question, kill the app, then relaunch and open the chat. The
   question is still pending, and it is asked once no reply is arriving.

The item is not `shipped` until this has run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai` (the server in the image) | ABSENT | jupyter_ai_acp_client 0.3.3 `default_acp_client.py` `prompt_and_reply`; jupyter_ai_persona_manager 0.2.2 `base_persona.py` `set_status`/`clear_status` (under `usr/lib/python3.12/site-packages/` in the shipped rootfs) | Serialises prompts on a per-session lock, but cancels the turn in flight before waiting on it |
| jupyter-chat-components (that server's frontend) | PARTIAL | jupyter-chat-components@992c6fb `src/components/message-queue.tsx`, `src/token.ts` (`IMessageQueueMetadata`, `IQueueMessageCallbacks`), `src/factory.tsx` | An "N queued" box with a row per message, edit, remove and drag to reorder. The queue itself is held by the client that renders it (jupyterlite/ai#302) |
| `jupyter-chat` | ABSENT | jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/buttons/send-button.tsx` | Send is disabled only by an empty input |
| `vscode` | FULL | vscode@3f3380c `src/vs/workbench/contrib/chat/browser/actions/chatQueueActions.ts`; `.../common/chatService/chatServiceImpl.ts` `processNextPendingRequest`; `.../common/model/chatModel.ts` `_pendingRequests`, `pendingRequests` in the serialised session | Add to Queue or Steer while a request is in progress. Pending requests are drawn as rows, sent one at a time when the turn completes, and saved with the session |
| `vscode-jupyter` | ABSENT | — | Has no chat of its own (ADR-0106) |
| `intellij-community` | ABSENT | — | AI Assistant is a closed, separate plugin |
| `termux-app` | ABSENT | — | |
| `winlator` | ABSENT | — | |

**Count:** 1 of 8 implement this in full and 1 in part. Neither has a server that queues; both hold
the queue in the client. That is where it goes here.

## Implementation inventory

### vscode@3f3380c

| Concern | Files |
|---|---|
| Domain model | `src/vs/workbench/contrib/chat/common/model/chatModel.ts` (`IChatPendingRequest`, `addPendingRequest`, `dequeuePendingRequest`, `removePendingRequest`) |
| Port / protocol | `src/vs/workbench/contrib/chat/common/chatService/chatService.ts` (`ChatRequestQueueKind`, `processPendingRequests`) |
| Adapter | `src/vs/workbench/contrib/chat/common/chatService/chatServiceImpl.ts` (`sendRequest` with `options.queue`, `processNextPendingRequest` on completion) |
| UI entry point | `src/vs/workbench/contrib/chat/browser/actions/chatQueueActions.ts`; `src/vs/workbench/contrib/chat/browser/widget/chatListRenderer.ts` (pending rows after a divider) |
| Persisted state | `chatModel.ts` `ISerializableChatData.pendingRequests`, `_deserializePendingRequests` |
| Edge cases it handles | Steering requests merged into one; Add to Queue when no request is in progress sends at once |

### jupyter-chat-components@992c6fb

| Concern | Files |
|---|---|
| Domain model | `src/token.ts` `IQueuedMessage`, `IMessageQueueMetadata` |
| UI entry point | `src/components/message-queue.tsx` (header "%1 queued", collapse toggle, rows, edit textarea, remove) |
| Edge cases it handles | Renders nothing when the queue is empty; Enter saves an edit, Escape cancels it |

**Where they disagree:** VS Code draws pending rows in the message list, after the response.
jupyter-chat-components draws them in a box of their own above the input. VS Code persists them;
jupyter-chat-components leaves that to its client.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where pending rows are drawn | VS Code → in the list after a divider; jupyter-chat-components → a box above the input | Above the input stays in view on a phone while the reply scrolls | jupyter-chat-components': it is jupyter-ai's own frontend, which is what ADR-0112 follows |
| When a pending question is sent | VS Code → one at a time, when the turn completes | — | The same. The turn's end is the persona's `writing` frame going false (`clear_status()` in `prompt_and_reply`'s `finally`) |
| Actions on a row | VS Code → Edit, Remove, Send Immediately, Remove All, Steer; jupyter-chat-components → edit, remove, reorder | Send Immediately and Steer cut the reply, which is the defect | Edit and remove |
| How long the queue lasts | VS Code → saved with the session; jupyter-chat-components → its client's memory | Saving needs a store | Saved to disk, per chat (the user's decision) |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Knowing a reply is arriving | `ChatTranscript.agentWriting`, folded from `writing` frames by `OkHttpAgentChat` | vscode@3f3380c `requestInProgress` | complete |
| Holding a question | — | vscode@3f3380c `chatModel.ts` `_pendingRequests` | absent |
| Drawing pending rows | — | jupyter-chat-components@992c6fb `message-queue.tsx` | absent |
| Sending when the reply ends | — | vscode@3f3380c `processNextPendingRequest` | absent |
| Persisting | — | vscode@3f3380c `chatModel.ts` serialisation | absent |

## Deviations

- **One question in flight.** Once a question is asked, the next is held until a `writing: true` frame
  has been seen. The persona only says it writes once it holds the lock, so the echo of the first
  question arrives while `writing` is still false. Without this hold, that echo would release the
  second question, and the server would cancel the first question's turn. The cost: in a Space whose
  agent never writes, the queue waits. It stays visible and removable.
- Every Send goes through the queue. When nothing is writing or in flight, it is asked at once, as VS
  Code's Add to Queue sends at once when no request is in progress.

## Open decisions

- **Where the queue is stored — settled (T4a).** In the app's own storage, one JSON file per Space,
  `filesDir/chat-queues/<spaceId>.json`, chat path to questions, through `QueuedQuestionsStore`. VS Code
  saves pending requests with the session in its workspace storage (vscode@3f3380c `chatModel.ts`
  `pendingRequests`, `chatSessionStore.ts:73`). No jupyter reference persists a queue. The repo already
  keeps per-Space bookkeeping (recents) in `filesDir`, out of the workspace. And jupyter_server refuses
  hidden files, so a sidecar would show in the explorer. Not settled yet: the ADR (T5), and
  following a rename and going with a delete (T4b).

## Surface

| Layer | Files |
|---|---|
| `domain` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/QuestionQueue.kt`, `.../domain/QueuedQuestionsStore.kt` |
| `data/<mechanism>` | `extensions/ai/.../data/disk/DiskQueuedQuestionsStore.kt` |
| `data/fake` | `extensions/ai/.../data/fake/FakeQueuedQuestionsStore.kt` |
| `ui` | `extensions/ai/.../ui/ChatPanel.kt`, `extensions/ai/.../ui/QueuedQuestions.kt` |
| `AiExtension` | `queuesFor(spaceId)`; `SidePanelHost.spaceId` in `:extension-api` |
| unit tests | `extensions/ai/src/test/.../domain/QuestionQueueTest.kt` |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ui/shell/ChatSidePanelTest.kt` |
| `.maestro/` flow + `scripts/` driver | `.maestro/chat-queued-questions.yaml`, `scripts/chat-queued-questions-driver.sh`, a check in `scripts/chat_tool_call_reads.py` |

## Tasks

- [x] T1 — Send while the agent writes queues the question as a pending row in a box above the input,
  and the oldest is asked when the reply ends, one in flight at a time
- [x] T2 — Remove a pending row with its ×
- [x] T3 — Edit a pending row in place
- [x] T4a — The queue persists per chat (the store decided, with its port, `Fake…` and adapter), and is
  sent on reopening when no reply is arriving
- [ ] T4b — A chat's queue follows its rename and goes with its delete
- [ ] T5 — The demo end to end on SM-X216B; the ADR; delete this PRD; update the backlog

## Out of scope

- Steer with Message and Send Immediately. Both cut the reply in flight, which is what this item exists
  to stop.
- Reordering pending rows and Remove All Queued.
- A server-side queue: no reference has one.
