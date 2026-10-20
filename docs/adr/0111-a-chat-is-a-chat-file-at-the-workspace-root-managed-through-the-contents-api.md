# ADR-0111 — A chat is a `.chat` file at the workspace root, managed through the Contents API

Status: accepted
Date: 2026-10-20

## Context

[ADR-0106](0106-the-chat-panel-answers-from-an-acp-agent-the-user-installs-in-the-space.md) gave the
Chat panel one chat, `untitled.chat` at the workspace root. A user who wants a second conversation,
or wants to keep one under a name, had nowhere to put it.

Two of the seven references hold several chats. jupyter-chat@2c224bc keeps each as a `*.chat` file in
one directory, the root by default (`defaultDirectory`). It lists them from the Contents API
(`getChatNames` in `packages/jupyterlab-chat-extension/src/index.ts`), makes a new one with
`newUntitled({type: 'file', ext: '.chat'})` (`createChat`), and renames one with `contents.rename`
(`renameChat`). Its selector's ✕ only closes a chat, so deleting one is left to the file browser
(`packages/jupyter-chat/src/widgets/chat-selector-popup.tsx`). vscode@3f3380c lists its chat sessions
with New chat, `Rename...` (an input prefilled with the title) and `Delete...` (a confirm that says it
cannot be undone), in `src/vs/workbench/contrib/chat/browser/agentSessions/agentSessionsActions.ts`.
jupyterlab, vscode-jupyter, intellij-community, termux-app and winlator have no chat list of their own.

The shipped rootfs serves a chat by its path. jupyterlab_chat 0.25.2 `chat_manager.py` holds one live
model per path, reuses it while it is held, and frees it when its last client leaves or when its 60 s
scan finds the file gone. `websocket_model.py` moves a live model's path on a rename through the
ContentsManager, and reads a missing or empty file as an empty chat. On SM-X216B, a chat renamed
through `PATCH api/contents` kept its answer and was answered again under its new path. A chat
deleted after its socket closed came back neither as a file nor as a model: a new chat under the same
name opened empty.

## Decision drivers

- A chat must be something the user owns: visible in the explorer, and kept by the backup that keeps
  the workspace ([ADR-0107](0107-what-the-user-made-survives-a-reinstall-through-a-filtered-auto-backup.md)).
- An extension never builds against `:app`
  ([ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)).
- Where the references agree, act as they do. Where they disagree, choose for a phone.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Where chats live | `.chat` files in a directory (jupyter-chat); the app's own storage (VS Code) | files at the workspace root, jupyter-chat's default, where `untitled.chat` already was |
| How a new chat is named | ask for a name first; untitled at once, renamed later (both references) | untitled at once, plus Rename... |
| Where a chat is deleted | the file browser (jupyter-chat); the chat list, after a confirm (VS Code) | the chat list, after the explorer's own confirm: the list is where a user looks for their chats |
| How the files are reached | the app's `WorkspaceFiles` port; jupyter_server's Contents API | the Contents API, as jupyter-chat calls it, from `:extensions:ai` |
| What the panel opens on | the open chat, saved; the list | the open chat for the life of the process, through rotation and visits to other panels; the list in a new process, as jupyter-chat's restored panel opens |

## Decision

**A chat is a `.chat` file at the workspace root. The Chat panel lists, creates, renames and deletes
those files through jupyter_server's Contents API, and it deletes a chat only after leaving it.**

- `ChatFiles` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/ChatFiles.kt`) is the
  port. `OkHttpChatFiles` makes `GET`, `POST`, `PATCH` and `DELETE` requests on `api/contents`, and
  `FakeChatFiles` is its fake.
- The list sorts by name, ignoring case, as jupyter-chat's placeholder sorts by `localeCompare`. A
  long press on a row offers Rename and Delete, through `RenameFileDialog` and `DeleteFileDialog`,
  which `:extension-api` lends an extension and which the explorer uses too.
- Delete is offered only on the list, so no socket from this panel is open on the chat when the
  `DELETE` goes out. Otherwise the live model `chat_manager.py` holds would write the file back on its
  next save, and a new chat under that name would get the deleted chat's messages.
- `OpenChatSelection` holds which chat is open. The extension keeps it, not the panel's composition,
  so it outlives the panel being dropped when another panel is picked. The extension is built once per
  process, so a new process opens on the list.

## Consequences

- The chats appear in the explorer, survive a reinstall with the workspace, and can be opened or
  deleted from either place.
- A file deleted through the server is gone for good. The server runs with
  `--FileContentsManager.delete_to_trash=False`, as the explorer deletes, so the confirm's
  "permanently" is true.
- Chats outside the root are not listed, and there is no setting for the directory. jupyter-chat's
  default is the root, and a chat moved elsewhere is still a file the explorer opens.
- The open chat is not restored after a kill. Restoring it may look like a fix, but jupyter-chat's
  restored panel does not restore its chat either, and a process that was killed has lost its socket
  anyway.
