# JD-057 — The Chat opens in a tab as well as on the side panel

Milestone: `M7` · State: in-progress · Depends on: JD-053 · Closes: —

## Outcome

A chat opens in a main-area tab, either from the explorer or moved there from the side panel, and it
moves back. The tab and the panel show the same `.chat` file, with the same transcript, chips and
queue. Today an extension can contribute only a side panel (`sidePanels` in
`extension-api/src/main/java/com/jupydroid/extension/api/Extension.kt`). A `.chat` tapped in the
explorer opens as raw JSON in the file editor, because `openDocument`
(`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`) sends every suffix except `.ipynb` to
`FileEditor`.

## Demo

On SM-X216B, with opencode installed and signed in in base (ADR-0106), landscape (two panes):

1. Open Chat and pick a chat that has answers. Tap "Move to tab" in its header. A tab named after the
   chat opens with the transcript and both chips. The side panel goes back to the chat list.
2. In the tab, ask "Reply with one word: tab." The answer arrives in the tab.
3. Long-press the tab and pick "Move to side panel". The tab closes, and the side panel opens on that
   chat with the answer from step 2.
4. In Files, tap the `.chat` file. It opens as a chat tab, not as JSON. Tap it again: the same tab
   is revealed, not a second one.
5. Rotate to portrait (one pane): the chat tab is still open and still answers.
6. Kill and relaunch. What comes back is what the open decision below settles, and the screenshots
   show it.

The item is not `shipped` until this has been run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-chat` | FULL | jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts` (`CommandIDs.moveChat`, the `chat` file type, the widget factory `defaultFor: ['chat']`) | A `.chat` is a document whose default widget is a chat in the main area; a toolbar button on both sides runs `moveChat`, which opens the chat in the other area and then closes it where it was |
| `vscode` | FULL | vscode@3f3380c `src/vs/workbench/contrib/chat/browser/actions/chatMoveActions.ts` (`workbench.action.chat.openInEditor`, `openInSidebar`, `openInNewWindow`) | Title-bar menu actions: clear the view's widget and open the session in an editor carrying the input state, or close the editor and load the session into the view |
| `jupyterlab` | ABSENT | — | No chat (grep `moveChat`/`chat.openIn*`: none) |
| `vscode-jupyter` | ABSENT | — | Contributes tools to VS Code's chat; never moves it (grep `workbench.action.chat` in `src/`: none) |
| `intellij-community` | ABSENT | — | AI Assistant is closed source |
| `termux-app` | ABSENT | — | |
| `winlator` | ABSENT | — | |
| the shipped rootfs | FULL (server) | `jupyterlab_chat/chat_manager.py` `on_client_connect` | The server keys connections per client, so a chat open in two places is two clients of one room |

**Count:** 2 of 7, and both chat products have it. Both treat it as a **move, not a copy**: the
destination opens first, then the source closes.

## Implementation inventory

### jupyter-chat@2c224bc

| Concern | Files |
|---|---|
| Domain model | `packages/jupyterlab-chat-extension/src/index.ts`: `chatFileType` registered with `app.docRegistry.addFileType`; a chat is a document at a path |
| Opening by type | same file: the widget factory, `fileTypes: ['chat']`, `defaultFor: ['chat']`, so the file browser opens a `.chat` as a chat |
| Moving | same file, `commands.addCommand(CommandIDs.moveChat, …)`: sidebar → main `openChat{filepath}`, then `chatPanel.unsetLoadedModel(name)`; main → sidebar `openChat{filepath, inSidePanel: true}`, then `widget.dispose()` |
| UI entry point | the `moveChat` toolbar button, captions "Move the chat to the main area" / "Move the chat to the side panel"; `isEnabled` only when a chat is open on that side |
| Restoring | same file: the layout restorer's `name` is the model name in the main area and `'sidebar'` on the side |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Moving | `src/vs/workbench/contrib/chat/browser/actions/chatMoveActions.ts` `executeMoveToAction`, `moveToSidebar` |
| UI entry point | same file: `MenuId.ViewTitle` (view → editor), `MenuId.EditorTitle` (editor → side bar), and the command palette (`f1: true`) |
| Edge cases | same file: no live widget, or a session not open, opens a **new** chat rather than one whose state it cannot guarantee; the input state (typed text) moves with the chat |

**Where they disagree:** VS Code carries the half-typed question across the move; jupyter-chat does
not. VS Code also opens a new window, which Android has no use for.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Move or copy | both → move | A copy shows two views of one chat, and each asks for its own answers | Move: the destination opens, then the source closes |
| How an extension contributes a tab | jupyter-chat → a document factory per file type; VS Code → an editor input per session | `:app` owns the tab strip and its `NavKey`s, and an extension cannot add a key type | A `DocumentContribution` in `:extension-api` keyed by suffix (`.chat`) with a `@Composable` body; `:app` owns one generic key, `ContributedDocument(contributionId, path)`, and frames it as ADR-0105 frames a panel |
| Where "Move to tab" sits | jupyter-chat → toolbar button; VS Code → view title menu | A header has room for one icon | An icon in the open chat's header (`OpenChatHeader`, `extensions/ai/.../ui/ChatsPanel.kt`) |
| Where "Move to side panel" sits | jupyter-chat → toolbar button; VS Code → editor title menu | The tab's long-press menu already holds every tab action (ADR-0103) | A row in the tab's menu, given by the contribution |
| The half-typed question | VS Code → carried; jupyter-chat → dropped | Carrying it needs state outside the composition | Carried only if it costs no new state; otherwise dropped, as jupyter-chat does (T2) |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| A tab an extension draws | `Extension.sidePanels` only | jupyter-chat `addWidgetFactory` | absent |
| `.chat` opened from the explorer | `DocumentOpeners.kt` `openDocument` → `FileEditor` (JSON) | jupyter-chat `defaultFor: ['chat']` | present and wrong |
| Panel → tab | `ChatsPanel`, `OpenChatSelection` | jupyter-chat `moveChat` | absent |
| Tab → panel | none | VS Code `openInSidebar` | absent |
| A panel opening a file in a tab | `SidePanelHost.fileOpener` → `EditorWorkspaceFileOpener` → `openDocument` | — | present: the seam a move to the tab reuses once `.chat` is routed |

## Deviations

- No "Move into New Window": Android's multi-window is the system's, not the app's.
- The tab's chat has no list. The list stays on the side panel, as in jupyter-chat.

## Open decisions

- **What a kill restores.** Settled in T1: what a file tab does. After process death (`am kill` of the
  backgrounded app) both a `FileEditor` tab and a chat tab come back from the saved back stack, the
  chat read again from its file. After a force-stop neither does. `ContributedDocument` is serializable
  like `FileEditor`, so no rule of its own is needed.
- **The same chat in the panel and in a tab.** Settled in T1: both stay open. With both drawing
  `untitled`, a call and a question queued behind it in the tab were each asked once and answered once
  (three turns in the server's log, none a minute later), and both views showed the same transcript.
  A queue is the view's own, so only the tab showed "1 queued".
- **One pane.** At one pane the side panel is the modal rail (ADR-0109). Moving to the side panel
  from a tab opens it there.

## Surface

| Layer | Files |
|---|---|
| `extension-api` | `extension-api/src/main/java/com/jupydroid/extension/api/Extension.kt` (`documents`, `DocumentContribution`), `SidePanelHost.kt` (how a panel opens a tab and a tab opens a panel) |
| `:app` | `app/src/main/java/com/jupydroid/app/ContributedDocument.kt` (key + entry), `DocumentOpeners.kt`, `Navigation.kt`, `ui/shell/MainAreaTab.kt` (`tabIcon`, menu row) |
| extension | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/AiExtension.kt`, `ui/ChatsPanel.kt` |
| unit tests | the routing in `app/src/test/…` beside the existing `openDocument` tests; `extensions/ai/src/test/…` for the move |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ui/shell/ChatTabTest.kt` (the header icon and the tab menu row are there to press) |
| `.maestro/` flow + `scripts/` driver | `.maestro/chat-in-tab.yaml`, `scripts/chat-in-tab-driver.sh` (T1), then one per task |

No new port: what moves is the UI's, and the chat itself is still `AgentChat`.

## Tasks

- [x] T1 — An extension contributes a document by suffix; a `.chat` opened from the explorer opens
  as a chat tab, and opening it again reveals that tab. Settle the kill and the two-places decisions.
  Device evidence on SM-X216B
- [x] T2 — "Move to tab" in the open chat's header: the tab opens (or is revealed), the panel goes
  back to the list
- [ ] T3 — "Move to side panel" in the chat tab's menu: the panel opens on that chat, the tab closes;
  at one pane, the modal rail
- [ ] T4 — The demo above on SM-X216B; a defect found is fixed red first
- [ ] T5 — The ADR (a document contribution by suffix, framed by the host; a move closes the
  source); delete this PRD; update the backlog

## Out of scope

- A new window, split editors, and dragging a tab to the side panel.
- An agent other than opencode: `JD-050`.
