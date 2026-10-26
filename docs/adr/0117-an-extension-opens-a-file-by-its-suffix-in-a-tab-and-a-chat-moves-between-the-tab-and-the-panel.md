# ADR-0117 — An extension opens a file by its suffix in a tab the host frames, and a chat moves between the tab and the panel

Status: accepted
Date: 2026-10-26

## Context

An extension could contribute only side panels (`Extension.sidePanels`,
`extension-api/src/main/java/com/jupydroid/extension/api/Extension.kt`). The Chat lived only on the side
panel, and a `.chat` tapped in the explorer opened as raw JSON, because `openDocument`
(`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`) sent every suffix except `.ipynb` to
`FileEditor`.

The references:

- **jupyter-chat@2c224bc** (`packages/jupyterlab-chat-extension/src/index.ts`) registers the `chat` file
  type with a widget factory `defaultFor: ['chat']`, so the file browser opens a `.chat` as a chat in the
  main area. Its `moveChat` toolbar button, shown on both sides, opens the chat in the other area and
  then closes it where it was: `chatPanel.unsetLoadedModel(name)` from the side, `widget.dispose()` from
  the main area. It does not carry a half-typed question.
- **vscode@3f3380c** (`src/vs/workbench/contrib/chat/browser/actions/chatMoveActions.ts`) has
  `workbench.action.chat.openInEditor` in the view's title menu and `openInSidebar` in the editor's
  title menu. Each clears or closes the source and opens the session in the other place, and it carries
  the input state. It also offers `openInNewWindow`.
- jupyterlab, vscode-jupyter, termux-app and winlator have no such move (grep `moveChat`,
  `workbench.action.chat`: none). intellij-community's AI Assistant is closed source.
- The shipped rootfs's `jupyterlab_chat/chat_manager.py` `on_client_connect` keys connections per
  client, so a chat open in two places is two clients of one room.

2 of the 7 references have it, both chat products, and both make it a **move, not a copy**: the
destination opens first, then the source closes.

## Decision drivers

- Keep the ADR-0104 boundary: `:app` owns the tab strip and its `NavKey`s, and an extension sees only
  `:extension-api`.
- Open a file the way the explorer already does, so a `.chat` opened from the panel, from Files or
  again lands in the same tab.
- Put each action where that side's actions already are: the chat's header on the panel, and the tab's
  long-press menu (ADR-0103) in the main area.
- Add no state outside the composition for the move alone.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| How an extension contributes a tab | a document factory per file type (jupyter-chat); an editor input per session (VS Code) | a contribution keyed by suffix, like jupyter-chat's. A `.chat` is a file, and the explorer opens files |
| How `:app` keys the tab | a key type per extension; one generic key | one generic key. An extension cannot add a key type, and saved state has to name it |
| Move or copy | move (both references); copy | move: the destination opens, then the source closes |
| Where "Move to tab" sits | toolbar button (jupyter-chat); view title menu (VS Code) | an icon in the open chat's header, which has room for one |
| Where "Move to side panel" sits | toolbar button (jupyter-chat); editor title menu (VS Code) | a row in the tab's long-press menu, which already holds every tab action |
| The half-typed question | carried (VS Code); dropped (jupyter-chat) | dropped. Carrying it needs state outside the composition |
| A new window | yes (VS Code); no (jupyter-chat) | no. Android's multi-window belongs to the system, not the app |

## Decision

**An extension claims a file suffix with a `DocumentContribution`. `:app` opens every file with that
suffix in one generic tab and frames the contribution's body as it frames a side panel. A chat moves
between that tab and the Chat panel: the destination opens first, then the source closes.**

- `DocumentContribution(id, suffix, icon, movesTo, content)` and `Extension.documents`, which is empty
  unless the extension gives one, are in `extension-api/src/main/java/com/jupydroid/extension/api/Extension.kt`.
  `content` is a `@Composable (path, SidePanelHost)`, for the reason ADR-0105 gives a panel one.
- `ContributedDocument(contributionId, path)` (`app/src/main/java/com/jupydroid/app/ContributedDocument.kt`)
  is the one `@Serializable` key for every contribution, so it round-trips through saved state as
  `FileEditor` does. `openDocument` gives a path to it, before the editor split, when a contribution
  claims the path's suffix in any case (`claiming`). Opening an equal key reveals its tab. The tab is
  titled with the file name and shows the contribution's icon (`tabIcon`,
  `app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTab.kt`). It has no rename or save rows.
- `ContributedDocumentPane` (`app/src/main/java/com/jupydroid/app/ui/shell/ContributedDocumentPane.kt`)
  frames the body and pads it for the navigation bars and the keyboard. The pane reaches the window's
  bottom edge, which a side panel at two panes does not. `rememberContributionHost`
  (`app/src/main/java/com/jupydroid/app/ContributionHost.kt`) builds the host once for the panel and the
  tab.
- **Panel → tab.** The open chat's header (`OpenChatHeader`,
  `extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatsPanel.kt`) ends with "Move to tab",
  using `OpenInNew`, the launch glyph jupyter-chat's button uses. It opens the file through the host's
  `WorkspaceFileOpener`, as the explorer would, and then the panel releases the chat and shows its list.
- **Tab → panel.** `SidePanelMove(panelId, open)`, given as `movesTo`, adds a "Move to side panel" row
  after the tab menu's close group. `moveToSidePanel` tells the panel the path, shows the panel, then
  closes the tab through the close gate. At one pane the panel is the modal rail (ADR-0109), drawn only
  while the browser's entry is on top, so that entry comes forward.
- The AI extension (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/AiExtension.kt`) contributes
  `.chat`: a `ChatPanel` for that path, keyed on the server and the Space like the panel. It has no list
  and no header, because the tab already names the file, and it moves to the Chat panel.

Measured on SM-X216B (240 dpi):

- **The tab's input clears the taskbar.** Before the padding, the input box ran from y=1010 to y=1180,
  and its chips were under the taskbar, which starts at about y=1128. With the padding it runs from
  y=937 to y=1108, the same bottom edge as the panel's box.
- **Each question is asked and answered once.** The demo moved one chat to a tab and back, opened it
  from Files, turned to one pane and killed the app. 3 questions were asked once and answered once, in
  6 messages. The server loaded the session four times and wrote no message at any load.
- **Opening it again reveals it.** Tapping the `.chat` in Files a second time left one tab.
- **Process death restores the tab.** After `am kill` the chat tab came back with its transcript and
  chips. After a force-stop it did not, and neither does a file tab.

## Consequences

- **A kill follows the file tab.** Both come back from the saved back stack after process death, the
  chat read again from its file, and neither comes back after a force-stop. `ContributedDocument` needs
  no rule of its own.
- **One chat can be open in the panel and in a tab at once**, as two clients of one room. A question
  queued behind a call in one was asked once and answered once, and both showed the same transcript. A
  queue belongs to its view (ADR-0113), so only the view that queued shows it.
- **A reload used to write a replayed answer.** Moving a chat empties and refills its room, so the
  persona loads the session again, and jupyter_ai_acp_client 0.3.3 could write a replayed chunk that
  landed after the load as a new message. `tools/rootfs/acp_replayed_updates_patch.py` drops the
  replayed updates by count instead (Fixes: ce48b3942f67 ("fix(rootfs): a chat loaded again writes none
  of the history its agent replays")).
- **A contribution that no extension gives any more draws an empty tab**, which the strip can still
  close.
- **A move drops the half-typed question.**
- Tests: `ContributedDocumentOpeningTest` and `ContributedDocumentMoveTest` (routing and the move),
  `AiExtensionTest` (the `.chat` contribution), `ContributedDocumentTabTest` and `ChatTabTest` (the
  header icon and the tab's menu row are there to press).
- **Do not add a key type per extension, or a second copy of a chat on a move.** The first would need
  `:app` to know each extension. The second would leave two views, each asking for its own answers.
