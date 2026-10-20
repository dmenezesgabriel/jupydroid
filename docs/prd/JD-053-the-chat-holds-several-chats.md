# JD-053 — The Chat holds several chats

Milestone: `M7` · State: open · Depends on: JD-046 · Closes: —

## Outcome

The Chat panel lists the workspace's `.chat` files. A user can open any of them, start a new
untitled one, rename one and delete one. Today the panel talks only to `untitled.chat`.

## Demo

On SM-X216B, from a fresh install, with opencode installed and signed in in the Space (ADR-0106):

1. Open Chat. The list is empty apart from **New chat**.
2. Tap New chat. `untitled` opens. Ask a question; the agent answers it.
3. Go back to the list and tap New chat again. `untitled1` opens, empty.
4. Go back to the list and open `untitled`. Its question and answer are shown.
5. Rename `untitled1` to `notes`. The list shows `notes` and no `untitled1`.
6. Delete `notes` and confirm. It is gone from the list and from the explorer.
7. Open `untitled`, then rotate the tablet. The same chat is still open.
8. Kill and relaunch. The panel shows the list, and opening `untitled` shows the earlier answer.

The item is not `shipped` until this has run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-chat` (0.25.2, the version the image ships) | FULL, except delete | jupyter-chat@2c224bc `packages/jupyter-chat/src/widgets/multichat-panel.tsx`, `packages/jupyterlab-chat-extension/src/index.ts` | Lists the `*.chat` files in one directory through the Contents API. New is `newUntitled`, Rename is `contents.rename`, and "close" unloads a chat without deleting it |
| `vscode` | FULL | vscode@3f3380c `src/vs/workbench/contrib/chat/browser/agentSessions/agentSessionsActions.ts`, `actions/chatActions.ts` (`ACTION_ID_NEW_CHAT`) | A sessions list with New chat, `Rename...` (an input box) and `Delete...` (a confirm that says it cannot be undone) |
| `jupyterlab` | ABSENT | — | No chat of its own; jupyter-chat is its extension (ADR-0106) |
| `vscode-jupyter` | ABSENT | — | Contributes tools to VS Code's chat (ADR-0106) |
| `intellij-community` | ABSENT | — | AI Assistant is a separate, closed plugin (ADR-0106) |
| `termux-app` | ABSENT | — | |
| `winlator` | ABSENT | — | |
| the shipped rootfs | server half | `jupyterlab_chat` 0.25.2 | Serves each `.chat` file over `/api/chat/ws/<path>`; jupyter_server's Contents API creates, lists, renames and deletes the files |

**Count:** 2 of 7 implement this. Both list, create and rename chats. Only VS Code deletes one from
the chat surface itself.

## Implementation inventory

### jupyter-chat@2c224bc

| Concern | Files |
|---|---|
| Domain model | a chat is a file. `getChatNames` maps a name without `.chat` to its path (`packages/jupyterlab-chat-extension/src/index.ts`) |
| Port / protocol | jupyter_server's Contents API: `contents.get(defaultDirectory)`, `newUntitled({type:'file', ext:'.chat'})`, `rename`, all in `createChat` / `renameChat` (`packages/jupyterlab-chat-extension/src/index.ts`) |
| UI entry point | `MultiChatPanel` holds the New chat toolbar button and the chat selector (`packages/jupyter-chat/src/widgets/multichat-panel.tsx`). `placeholder.tsx` is the list shown when no chat is open, sorted by `localeCompare`, with New chat |
| Persisted state | `restorer.add(chatPanel, 'jupyter-chat')` restores the panel and not which chat was open in it (`packages/jupyterlab-chat-extension/src/index.ts`) |
| Close vs delete | the selector's ✕ is "Close and dispose this chat" (`packages/jupyter-chat/src/widgets/chat-selector-popup.tsx`). Deleting a file goes through the file browser |
| Server: an empty file | `WsChatModel.load_from_file` reads a missing or empty file as an empty chat, because it catches `JSONDecodeError` (`python/jupyterlab-chat/jupyterlab_chat/websocket_model.py`) |
| Server: rename | a rename through the ContentsManager moves the live model's path (the `rename` event handler in `websocket_model.py`) |
| Server: delete | `ChatManager` reuses a live model by path. It frees one when its last client leaves (`ws_client_gone`) or when the 60 s scan finds its file gone (`_scan_freeable_chats` in `chat_manager.py`) |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Rename | `RenameAgentSessionAction` uses a `quickInputService.input` prefilled with the current title (`src/vs/workbench/contrib/chat/browser/agentSessions/agentSessionsActions.ts`) |
| Delete | `DeleteAgentSessionAction`: confirm ("This action cannot be undone."), clear the open widget, then remove the session (same file) |

**Disagreement:** jupyter-chat leaves deleting to the file browser, and VS Code deletes from the chat
surface. We take VS Code's Delete... with its confirm. A phone has the explorer one tap away, but
the panel is where the chats are listed.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where chats live | jupyter-chat: files in `defaultDirectory`; VS Code: workspace storage | a file is visible in the explorer and survives a reinstall (ADR-0107) | `.chat` files at the workspace root, jupyter-chat's default `defaultDirectory`, which is where `untitled.chat` already is |
| Naming a new chat | jupyter-chat `newUntitled`, then rename; VS Code untitled, then rename | asking for a name first costs a dialog on every New | untitled at once, plus Rename... (both references) |
| Deleting | jupyter-chat: file browser; VS Code: Delete... + confirm | see Disagreement | Delete... + confirm |
| What the panel opens on | jupyter-chat: placeholder list in a fresh page; the open chat while the page lives | — | the last chat across rotation and panel switches (saved state); the list in a fresh process |
| Transport for files | Contents REST API; the app's `WorkspaceFiles` port | an extension never builds against `:app` (ADR-0104) | the Contents API, as jupyter-chat does, from `:extensions:ai` |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Opening a chat by path | `OkHttpAgentChat(chatPath = "untitled.chat")` in `extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/OkHttpAgentChat.kt` | jupyter-chat@2c224bc `createChatModel` | present: the path is a parameter nobody varies |
| Listing chats | — | `getChatNames` | absent |
| New / Rename / Delete | — | `createChat`, `renameChat`; VS Code `DeleteAgentSessionAction` | absent |
| A REST client in an extension | `OkHttpGitStatusReader` in `extensions/git/src/main/java/com/jupydroid/extensions/git/data/http/OkHttpGitStatusReader.kt` | — | the pattern to copy: token header, `ioDispatcher` |

Overall, the feature is absent.

## Deviations

- Deleting from the panel follows VS Code, not jupyter-chat (see Disagreement).
- **Delete leaves the chat before it deletes the file.** jupyterlab_chat keeps a live model per path
  until its last client leaves. If the file were deleted under an open socket, a later save would
  write it back, and a new chat under the same name would reuse the stale model (`chat_manager.py`).

## Open decisions

- T3 checks on the device that a rename through `PATCH api/contents` moves the live model and that
  the persona still answers under the new path. `websocket_model.py` says it does; the check
  confirms this on the shipped server.

## Surface

| Layer | Files |
|---|---|
| domain | `extensions/ai/.../domain/ChatFiles.kt` (the port and `ChatFile`) |
| `data/http` | `extensions/ai/.../data/http/OkHttpChatFiles.kt` |
| `data/fake` | `extensions/ai/.../data/fake/FakeChatFiles.kt` |
| ui | `extensions/ai/.../ui/ChatPanel.kt`, a new `ui/ChatList.kt` |
| composition root | `extensions/ai/.../AiExtension.kt` (`chatFor(server, path)`, `chatFilesFor(server)`) |
| unit tests | `ChatFilesContract`, `FakeChatFilesTest`, `OkHttpChatFilesTest` |
| instrumented tests | `extensions/ai/src/androidTest/.../ChatListTest.kt` |
| `.maestro/` flow + `scripts/` driver | `chat-several-*.yaml`, `scripts/chat-several-driver.sh` |

## Tasks

- [ ] T1 — The panel lists the workspace's chats and opens the one tapped. Covers `ChatFiles.list`,
  the adapter, the fake, the list, and the saved open path. On the tablet, two seeded `.chat` files
  each open with their own messages.
- [ ] T2 — New chat creates `untitled*.chat` through `POST api/contents` and opens it; a question
  there is answered.
- [ ] T3 — Rename... through `PATCH api/contents/<path>`, prefilled with the current name.
- [ ] T4 — Delete... with a confirm. It leaves the chat, then sends `DELETE api/contents/<path>`, then
  shows the list.
- [ ] T5 — The demo from a fresh install. Then ADR-0111 (a chat is a `.chat` file at the workspace
  root, managed through the Contents API; it supersedes ADR-0106's "one chat" row), the backlog, and
  this PRD deleted.

## Out of scope

- Chats in other directories, and a setting for the directory. jupyter-chat's `defaultDirectory` is
  the root by default.
- Searching the list. jupyter-chat's selector filters; a phone list of a few chats does not need it.
- A chat in a main-area tab (`JD-057`).
