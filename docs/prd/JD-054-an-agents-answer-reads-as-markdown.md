# JD-054 — An agent's answer reads as markdown

Milestone: `M7` · State: in-progress · Depends on: JD-046 · Closes: —

## Outcome

An agent's answer in the Chat is drawn as a markdown cell is drawn: headings, lists, tables and code
blocks, not the raw `##`, `-` and fences. A code block in it can be copied, or inserted as a new code
cell below the notebook's active cell. Today `ChatPanel.kt` `MessageRow` draws the body as one `Text`.

## Demo

On SM-X216B, with opencode installed and signed in in base (ADR-0106):

1. Open Chat, New chat. Ask for an answer with a heading, a bulleted list and a fenced Python block.
   The heading is drawn as a heading, the list with bullets, and the code in a preformatted box. No
   `##`, `-` or fence is shown as text.
2. Tap Copy under the code block, open a notebook, and paste into a cell. The cell holds the code.
3. With that notebook open and a cell selected, ask again and tap Insert under the code block. A new
   code cell holding the code appears below the active cell.
4. Kill and relaunch, and open the chat. The answer read back is still drawn as markdown.

The item is not `shipped` until this has run on SM-X216B, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-chat` | FULL | jupyter-chat@2c224bc `packages/jupyter-chat/src/components/messages/message-renderer.tsx`, `src/components/code-blocks/code-toolbar.tsx`, `src/active-cell-manager.ts`, `packages/jupyterlab-chat-extension/src/index.ts` | Every text message goes through the host's `IRenderMimeRegistry` as `text/markdown`. A toolbar is put after each `<pre>`: insert above, insert below, replace, copy |
| `jupyter-chat-components` | ABSENT | jupyter-chat-components@992c6fb `src/components/` | Tool calls, diffs and the queue only; message bodies are jupyter-chat's |
| `jupyterlab` | FULL | jupyterlab@7f0e690 `packages/rendermime/src/renderers.ts` (`renderMarkdown`) | The rendermime jupyter-chat borrows: the chat draws markdown as a markdown cell does because it *is* the same renderer |
| `vscode` | FULL | vscode@3f3380c `src/vscode-dts/vscode.d.ts` (`ChatResponseStream.markdown`), `src/vs/workbench/contrib/chat/browser/actions/chatCodeblockActions.ts`, `.../actions/codeBlockOperations.ts` | The extension hands the workbench markdown, which renders it and owns the code-block actions: Copy, Insert At Cursor, Insert into New File, Insert into Terminal, Apply |
| `vscode-jupyter` | ABSENT | vscode-jupyter@f01d80ca0 `src/standalone/chat/` | Tools for Copilot; no chat of its own (ADR-0106) |
| `intellij-community` | PARTIAL | intellij-community@406a27a8 `platform/jewel/markdown/core/` | A Compose markdown library a plugin draws with; the model `RichBlock` already follows. AI Assistant itself is closed |
| `termux-app` | ABSENT | — | Writes markdown for bug reports; draws none |
| `winlator` | ABSENT | — | |

**Count:** 3 of 8 implement it in full. In both that have an extension contract, the **host renders**
and the chat extension is handed the renderer: jupyter-chat's plugin `requires: [IRenderMimeRegistry]`,
and a VS Code chat participant streams markdown the workbench draws.

## Implementation inventory

### jupyter-chat@2c224bc

| Concern | Files |
|---|---|
| Renderer received from the host | `packages/jupyterlab-chat-extension/src/index.ts` (`requires: [IRenderMimeRegistry, …]`, `rmRegistry` into the chat widget) |
| Message drawn as markdown | `packages/jupyter-chat/src/components/messages/message-renderer.tsx` (`DEFAULT_MIME_TYPE = 'text/markdown'`; a toolbar root inserted after every `<pre>`, its content `preBlock.textContent`) |
| Code-block actions | `packages/jupyter-chat/src/components/code-blocks/code-toolbar.tsx`, `copy-button.tsx` |
| Active cell | `packages/jupyter-chat/src/active-cell-manager.ts` (`available` needs an active cell *and* a visible notebook; `insertBelow`) |
| Empty / error states | `code-toolbar.tsx`: no toolbar when there is no active-cell manager; insert disabled with "(no active cell)" |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Markdown handed to the host | `src/vscode-dts/vscode.d.ts` `ChatResponseStream.markdown` |
| Code-block actions | `src/vs/workbench/contrib/chat/browser/actions/chatCodeblockActions.ts` |
| Insert into a notebook | `src/vs/workbench/contrib/chat/browser/actions/codeBlockOperations.ts` `handleNotebookEditor`: a code cell inserted below the focused cell; a notice when no editor is open; refused on a read-only notebook |

**Where they disagree:** jupyter-chat offers insert *above* and *below* and *replace*; VS Code offers
one insert, below. Both put a new cell below the active one for their main insert.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the renderer lives | jupyter-chat → the host's rendermime, required by the plugin; VS Code → the workbench; Jewel → a library a plugin links | Moving `markdown/`, `richtext/`, `html/` and `RichText` out of `:app` would drag the editor palette with it | The host's: `:extension-api` declares a `MarkdownRenderer`, `:app` implements it over `MarkdownDocument` and `RichText`, and hands it in `SidePanelHost`, as jupyter-chat is handed `IRenderMimeRegistry` |
| Which messages | jupyter-chat → every text message | — | The agent's answer; see Open decisions |
| Insert | jupyter-chat → above, below, replace; VS Code → below the focused cell | — | Below the active cell, the one both have |
| No active cell | jupyter-chat → disabled; VS Code → a notice | — | Disabled, as jupyter-chat, and named "(no active cell)" as its title is |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Markdown drawn as a cell draws it | `app/src/main/java/com/jupydroid/app/markdown/MarkdownDocument.kt`, `app/src/main/java/com/jupydroid/app/ui/editor/RichText.kt` | jupyterlab@7f0e690 `renderers.ts` | present but unreachable from `:extensions:ai` (ADR-0104) |
| The answer drawn | `extensions/ai/.../ui/ChatPanel.kt` `MessageRow`, plain `Text` | jupyter-chat@2c224bc `message-renderer.tsx` | absent |
| Copy | — | jupyter-chat@2c224bc `copy-button.tsx` | absent |
| Active cell reachable from a panel | — | jupyter-chat@2c224bc `active-cell-manager.ts` | absent |

## Deviations

- The actions sit under the code block as icon buttons, as jupyter-chat's toolbar does, but each
  names itself in a tooltip on a **long press** rather than on hover, which a touchscreen does not
  have. Copy shows a check for a second in place of jupyter-chat's "Copied!" tooltip.

## Open decisions

- **Is the user's own question drawn as markdown?** jupyter-chat renders every text message. The item
  names the answer; T1 draws the answer and leaves the question as typed, and the ADR records it.
- **Is Insert enabled while the Chat is the modal rail at one pane, over the notebook?** jupyter-chat
  requires the notebook to be visible (`_notebookVisible`). Decided in T3 on a measurement.

## Surface

| Layer | Files |
|---|---|
| `:extension-api` | `extension-api/src/main/java/com/jupydroid/extension/api/MarkdownRenderer.kt`, `SidePanelHost.kt` |
| `:app` host | `app/src/main/java/com/jupydroid/app/ui/shell/RichTextMarkdownRenderer.kt`, `app/src/main/java/com/jupydroid/app/ShellSidePane.kt` |
| `ui` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatPanel.kt`, `ChatsPanel.kt` |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ui/shell/ChatSidePanelTest.kt` |
| `.maestro/` flow + `scripts/` driver | `.maestro/chat-markdown-answer.yaml`, `scripts/chat-markdown-answer-driver.sh` |

## Tasks

- [x] T1 — An agent's answer is drawn by the host's markdown renderer, handed to the panel in
  `SidePanelHost`
- [x] T2 — Copy under a code block of an answer
- [ ] T3 — Insert under a code block puts a code cell below the active cell, through an active-cell
  API the host hands in `SidePanelHost`
- [ ] T4 — The demo end to end on SM-X216B; the ADR; delete this PRD; update the backlog

## Out of scope

- Insert above, replace the selection, Insert into Terminal or New File, Apply and diffs.
- Rendering a mime bundle message (jupyter-chat's `mime_model`): jupyter-ai's ACP persona sends text.
