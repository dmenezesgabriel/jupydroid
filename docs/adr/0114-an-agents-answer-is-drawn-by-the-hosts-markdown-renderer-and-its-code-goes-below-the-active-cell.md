# ADR-0114 — An agent's answer is drawn by the host's markdown renderer, and its code goes below the active cell

Status: accepted
Date: 2026-10-26

## Context

The Chat panel (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatPanel.kt`) drew every
message body as one `Text`. opencode answers in markdown, so a heading, a list and a fenced block
showed as raw `##`, `-` and fences. The code in a block could only be selected by hand.

The app already draws markdown, the way a markdown cell is drawn: `MarkdownDocument`
(`app/src/main/java/com/jupydroid/app/markdown/MarkdownDocument.kt`) parses CommonMark with GitHub's
tables, strikethrough and autolinks, and `RichText` (`app/src/main/java/com/jupydroid/app/ui/editor/RichText.kt`)
draws it. Both are in `:app`. Under
[ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md) the extension builds
against `:extension-api` and never against `:app`, so it cannot reach them. The notebook's cells live
in the editor's `EditorViewModel`, in the detail pane, and the Chat sits in the side pane.

The references:

- jupyter-chat@2c224bc `packages/jupyter-chat/src/components/messages/message-renderer.tsx` renders
  every text message through the host's `IRenderMimeRegistry` as `text/markdown`. Its plugin
  `requires` that registry (`packages/jupyterlab-chat-extension/src/index.ts`), so the chat draws
  markdown as a markdown cell does because it uses the same renderer. After every `<pre>` it puts a
  toolbar (`src/components/code-blocks/code-toolbar.tsx`) with insert above, insert below, replace and
  copy. Inserts are disabled, titled "(no active cell)", unless `src/active-cell-manager.ts` has an
  active cell in a visible notebook.
- vscode@3f3380c: a chat participant streams markdown (`src/vscode-dts/vscode.d.ts`,
  `ChatResponseStream.markdown`) that the workbench draws. Its code-block actions
  (`src/vs/workbench/contrib/chat/browser/actions/chatCodeblockActions.ts`) include Copy and Insert
  At Cursor. In a notebook, `codeBlockOperations.ts` `handleNotebookEditor` inserts a code cell below
  the focused one through `cellOperations.ts` `insertCell`, which focuses no cell editor.
- intellij-community@406a27a8 `platform/jewel/markdown/core/` is a Compose markdown library a plugin
  links itself. AI Assistant is closed.
- jupyter-chat-components, vscode-jupyter, termux-app and winlator draw no chat message bodies.

3 of the 8 references draw an answer as markdown in full. In both that have an extension contract,
the host renders it and the chat extension is handed the renderer.

## Decision drivers

- An answer must read as the notebook reads markdown: one renderer, so the two cannot drift.
- Keep the ADR-0104 boundary: the extension sees only `:extension-api`.
- Code in an answer must reach the notebook without being retyped.
- Where jupyter-chat has decided something, decide the same, unless a touchscreen rules it out.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Where the renderer lives | the host's, handed in (jupyter-chat, VS Code); a library the extension links (Jewel) | the host's. Moving `markdown/`, `richtext/` and `RichText` out of `:app` would drag the editor's palette with them |
| Which messages | every text message (jupyter-chat); the answer | the answer. The question stays as typed |
| Inserts | above, below and replace (jupyter-chat); below the focused cell (VS Code) | below the active cell, the one both have |
| With no active cell | disabled (jupyter-chat); a notice (VS Code) | disabled, and named "(no active cell)" as jupyter-chat titles it |
| The mode after an Insert | edit, the new cell's editor focused (JupyterLab's `insertBelow`); command, no editor focused (VS Code) | command. The user's hands are in the Chat, not the notebook |

## Decision

**The host hands a contributed panel its markdown renderer and the open notebook's active cell in
`SidePanelHost`. The Chat draws an agent's answer with that renderer, and under every code block puts
Insert, which adds a code cell below the active cell, and Copy.**

- `MarkdownRenderer` (`extension-api/src/main/java/com/jupydroid/extension/api/MarkdownRenderer.kt`)
  draws a CommonMark source. It takes the extension's own `codeBlockActions` and draws them under
  every code block, a nested one too, handing each the code as shown.
  `RichTextMarkdownRenderer` (`app/src/main/java/com/jupydroid/app/ui/shell/RichTextMarkdownRenderer.kt`)
  implements it as `MarkdownDocument.parse` into `RichText`, parsing once per source rather than per
  streamed frame.
- `NotebookActiveCell` (`extension-api/src/main/java/com/jupydroid/extension/api/NotebookActiveCell.kt`)
  is jupyter-chat's `IActiveCellManager` cut to the one insert: `available`, read as Compose state,
  and `insertBelow`. `OpenNotebookActiveCell` (`app/src/main/java/com/jupydroid/app/OpenNotebookActiveCell.kt`)
  is the seam `OpenDebugStack` already crosses. The editor publishes its insert through
  `InsertBelowActiveCellPublication` while a cell is selected, and clears it when it has none or its
  entry goes.
- The insert (`NotebookCellEdits.insertCodeBelow`) puts the code in a new code cell below the active
  one, which becomes active, as one undoable operation: JupyterLab's `insertBelow`
  (jupyterlab@7f0e690 `packages/notebook/src/actions.tsx`) followed by jupyter-chat's `setSource`.
  Before inserting, it drops the caret through the focus manager. That is how the notebook leaves edit
  mode everywhere in this app: the focus listener reports the mode, as `EditorScreen`'s Back handler
  relies on.
- `CodeBlockActions` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/CodeBlockActions.kt`)
  draws Insert, then Copy, as 48 dp icon buttons at the block's right edge, in jupyter-chat's order.
  A touchscreen has no hover, so each names itself in a tooltip on a long press. Copy puts the code on
  the system clipboard and shows a check for a second in place of jupyter-chat's "Copied!".
  Android's own confirmation starts at API 33, and the app runs from 32.
- `ChatPanel`'s `MessageRow` draws the user's own question as a `Text`, as typed.

## Consequences

- At one pane the Chat is reached only by leaving the notebook. The notebook's arrow closes its tab,
  so the editor clears what it published and Insert reads "(no active cell)". That is jupyter-chat's
  visible-notebook rule, with no code of its own.
- An answer read back from its `.chat` file after a kill is drawn the same way, because the markdown
  is what the server stored.
- Anything the notebook's markdown learns, such as a table or a link, the Chat gets too. Anything it
  mis-draws, the Chat mis-draws.
- Insert above, replace the selection, Insert into Terminal or New File, and Apply with diffs are not
  offered. A message carrying a mime bundle (jupyter-chat's `mime_model`) is not drawn, because
  jupyter-ai's ACP persona sends text.
- Do not set command mode in `insertCodeBelow`. A mode set in the state left the caret in the cell
  being edited, and on SM-X216B the next keystroke went into that cell while the new one was active
  (`InsertBelowActiveCellCaretTest`).
