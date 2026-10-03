# Parity ledger

**What of each reference we have, what we did differently, and which differences were decided.**
One row per reference entry, carrying a verdict and an id — never a count, a total or a percentage
([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)). Numbers are
**produced** by the two measurements below, which print the rule that produced them.

Measured research, so this file may cite `reference/`, which no durable document may
([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)). It is filled one
area at a time by the `AUD-NNN` items in [`../backlog.md`](../backlog.md); an area nobody has
examined says `unexamined` rather than nothing.

## Re-measuring

```sh
python3 scripts/read_reference_commands.py --all              # the references' surface, per package
python3 scripts/read_reference_commands.py --notebook-actions # NotebookActions' members
python3 scripts/read_reference_commands.py --lsp-methods      # the LSP surface — Method's four direction enums
python3 scripts/read_port_surface.py                          # our ports, and what names each operation
python3 scripts/read_port_surface.py --unused                 # operations nothing names
python3 scripts/read_ledger_partition.py --unassigned         # command ids this file owns nowhere
python3 scripts/read_ledger_verdicts.py --missing             # ids an examined task left unjudged
python3 scripts/read_ledger_verdicts.py --counts              # how many of each verdict
python3 -m unittest discover -s scripts -p 'read_*_test.py'   # the counts §1 states, against the clones
```

The shipped rootfs is authoritative for what the kernel on *this device* does
([`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) §8.3), and is read without
unpacking the image:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/ipykernel/kernelbase.py > /tmp/kb.py
python3 scripts/read_reference_commands.py --kernelbase /tmp/kb.py
```

## Verdicts

Five, each obliging something different. The full definition is in
[ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md).

| Verdict | Means | Obliges |
|---|---|---|
| `present` | we do what the reference does | nothing |
| `adapted` | we differ, and an ADR or code comment states why | nothing — the citation is the record |
| `undocumented` | we differ and the reason is in nobody's writing | an ADR, in the slice that examines the area |
| `absent` | the reference has it, we do not, no reason recorded | a `GAP-NNN`, and a `JD-NNN` if it is a capability |
| `defect` | ours behaves wrongly against its own intent | a failing test, then a fix, in that slice |

## Areas

| Area | Item | State |
|---|---|---|
| Kernel wire and execution | `AUD-001` | examined — below |
| Cell commands and selection | `AUD-002` | examined — below |
| Editor language features | `AUD-003` | examined — below |
| Search, replace, table of contents | `AUD-004` | examined — below |
| Files, workspace, recents | `AUD-005` | examined — below |
| Console, sessions, running panel | `AUD-006` | examined — below |
| Terminal emulator and view | `AUD-007` | partially verdicted — all but the walk below |
| Rendering and formats | `AUD-008` | partially verdicted — the command surface below |
| Chrome and presentation | `AUD-009` | unexamined |
| Spaces and runtime | `AUD-010` | unexamined |

---

## Kernel wire and execution — `AUD-001`

**The surface.** The shipped `ipykernel` dispatches 10 message types on the shell channel and 5 more
on control only, declared as its own `msg_types` and `control_msg_types`
(`usr/lib/python3.12/site-packages/ipykernel/kernelbase.py` in the rootfs). Those 15 are the whole
of what a client may ask this kernel. The behaviour reference is
`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`, whose `packages/services/src/kernel/` wraps
each one and whose extensions are where a *caller* is found — "the service layer declares it" and "a
feature uses it" are different facts, and the rows below distinguish them.

**Ours sends five**, all built by `data/space/JupyterMessage.kt`: four on shell and one on control.
The rest of what we do to a kernel goes over the server's REST API, which is also where the
reference goes.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `execute_request` | `JupyterMessage.executeRequest` | present | — |
| `kernel_info_request` | `JupyterMessage.kernelInfoRequest` | present | sent first on every socket, as the reference does ([ADR-0023](../adr/0023-an-unanswered-greeting-replaces-the-kernel-once.md)) |
| `complete_request` | `JupyterMessage.completeRequest` | present | `cursor_pos` counted in characters |
| `history_request` | `JupyterMessage.historyRequest` | present | `tail`/`raw` transcribed from `packages/notebook/src/history.ts` ([ADR-0052](../adr/0052-kernel-history-is-the-kernels-and-a-walk-belongs-to-its-cell.md)) |
| `debug_request` (control) | `JupyterMessage.debugRequest` | present | DAP in the Jupyter envelope ([ADR-0050](../adr/0050-reads-hit-the-repository-server-side-algorithms-do-not.md) on what may not be re-implemented) |
| `interrupt_request` | `POST api/kernels/<id>/interrupt` — `KernelLifecycle.interrupt` | adapted | the reference also goes over REST; the server sends the message or signals, per the kernelspec's `interrupt_mode` |
| `shutdown_request` | `DELETE api/sessions/<id>` — `KernelLifecycle.shutdownSession` | adapted | a shutdown is accounted for by the *session*, which is what owns the kernel ([ADR-0022](../adr/0022-kernels-start-through-sessions-and-one-tree-is-the-root.md)) |
| `connect_request` | — | adapted | deprecated in the protocol, and the reference has no caller for it either: zero hits outside its own message types |
| `usage_request` (control) | — | adapted | same absence in the reference — no hit anywhere under `packages/` |
| `create_subshell_request`, `delete_subshell_request`, `list_subshell_request` (control) | — | adapted | declared in the reference's service layer, called by no feature in it |
| `inspect_request` | LSP `textDocument/hover` — `ui/editor/NotebookHover.kt` | **undocumented** | the file noted the substitution and gave no reason; [ADR-0069](../adr/0069-a-cells-hover-asks-the-source-not-the-live-object.md), written by `AUD-003` T2, says it — the kernel answers the *live* object, the server answers the *source*, and the substitution is lossy in that direction. The reference's four callers stand: `packages/inspector/src/kernelconnector.ts:47`, `packages/completer/src/default/kernelprovider.ts:113`, `packages/tooltip-extension/src/index.ts:355`, and the console |
| `is_complete_request` | — | **absent** | `GAP-012`. Two callers in the reference: `packages/console/src/widget.ts:979` decides whether a submitted block is complete, and `packages/notebook-extension/src/index.ts:1962` |
| `comm_info_request`, and the comm channel | — | **absent** | `GAP-013`. The protocol half ipywidgets rides on |
| stdin `input_request` / `input_reply` | `allow_stdin: false` — `JupyterMessage.kt:106` | **absent** | `GAP-014`. The reference sends `allow_stdin: true` (`packages/services/src/kernel/default.ts:801`) and draws the prompt in the output area (`packages/outputarea/src/widget.ts`) |

**Our ports, measured against their use.** 19 ports, 84 operations, 4 that no production file names
(`read_port_surface.py --unused`). Three belong to this area's neighbours rather than to the wire,
and are carried to the slice that owns them:

| Operation | Verdict | Why / id |
|---|---|---|
| `NotebookRepository.renameNotebook` | `defect`-adjacent, deferred | named nowhere; renaming goes through `WorkspaceRepository`, which owns the tree. `AUD-005` decides whether the port keeps it |
| `NotebookRepository.overwriteNotebook` | deferred | as above |
| `NotebookRepository.deleteNotebook` | deferred | as above |
| `LanguageServerPort.closeDocument` | `defect` → fixed | a rename re-keyed the virtual document's uri and orphaned the server's copy — `NotebookLanguageDocument.sync` now sends `didClose` for the replaced uri (`aRename_closesTheDocumentTheServerKnew`). `AUD-003` T2 |
| `LanguageServerPort.capabilities` | kept | **read by hand, not by the tool** — the `val capabilities` field masked the dead name. Decided by `AUD-003` T2: the `initialize` answer itself, kept as the port's expose of the negotiated surface; the reason is in the op's KDoc |

**The walk.** `EditorViewModel` → `NotebookKernel` → `KernelGateway` (port) →
`BaseSpaceKernelGateway` → `RealKernelGateway` → `KernelSockets` → one JSON text frame per message.
What the walk found, beyond the rows above:

- **Growth is in the kernel's output, not in our dispatch.** Every request is O(1) in the number of
  cells; `KernelOutputs` maps one wire frame to one `CellOutput`, and a cell's outputs accumulate in
  a list appended to. Nothing on this path is quadratic in cells or in output size.
- **`allow_stdin: false` is the one place the wire is narrowed by a constant**, and it is the reason
  `GAP-014` is a one-line change in the request and a whole feature in the UI: the kernel cannot ask
  for input unless the client offers to answer.
- **The protocol version we send is `5.3`** (`JupyterMessage.PROTOCOL_VERSION`), which is what the
  shipped kernel speaks.

---

## Cell commands and selection — `AUD-002`

**The surface.** `notebook-extension` declares 102 command ids, 99 of them `notebook:*`, and those
99 are the whole of what JupyterLab can be asked to do to a notebook or its cells. Produced, never
stored:

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package notebook-extension | grep '^notebook:'
```

**The area's bounds are the reference's own** — it sorts its commands into two palette categories,
*Notebook Operations* and *Notebook Cell Operations*
(`packages/notebook-extension/src/index.ts:5255-5360`). Everything outside this slice is carried to
the slice that owns it rather than left unassigned, because an id assigned to no slice is how an
area silently stays unexamined.

**And the palette does not reach the whole surface.** The two categories hold 27 and 58 ids, plus
`notebook:create-new` added on its own with `args: { isPalette: true }` — 86 of 99. **Thirteen are
in neither**, and four of those are operations this app ships: `notebook:duplicate-below`,
`notebook:undo`, `notebook:redo` and `notebook:toggle-output-scrolling`. A reading bounded by the
palette alone would have dropped them silently, so ownership is decided by three sources in order:
the palette category; failing that the command's own registration, read for what its `label` and
`execute` say it does; and failing that
[`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) §8.3, which names the
reference authoritative for the question and therefore the slice that owns the comparison.

### Who owns which id

One row per command **family**, and a family that spans two slices is split into one row per owner —
`scripts/read_ledger_partition.py` refuses a double claim, and `PartitionedSurfaceTest` refuses an
id nobody claims. The partition says who examines an entry; it says nothing about how that
examination came out, which is what a verdict is for.

| Reference ids | Owner | Why it is that slice's |
|---|---|---|
| `notebook:insert-cell-above`, `notebook:insert-cell-below` | `AUD-002` `T2b` | *Cell Operations*. `CellAction.Insert`/`InsertAbove` |
| `notebook:insert-heading-above`, `notebook:insert-heading-below` | `AUD-002` `T2b` | *Cell Operations*. `CellAction.InsertHeading`, whose "below" is the end of the section rather than the next cell |
| `notebook:change-cell-to-code`, `notebook:change-cell-to-markdown`, `notebook:change-cell-to-raw` | `AUD-002` `T2b` | *Cell Operations*. `CellAction.ChangeKind` over `CellRestructure.withCellsKindChanged` |
| `notebook:change-cell-to-heading-1`, `notebook:change-cell-to-heading-2`, `notebook:change-cell-to-heading-3`, `notebook:change-cell-to-heading-4`, `notebook:change-cell-to-heading-5`, `notebook:change-cell-to-heading-6` | `AUD-002` `T2b` | *Cell Operations*. `CellAction.ChangeToHeading`; the six are one parameter here and one row plus a picker in the menu |
| `notebook:merge-cells`, `notebook:merge-cell-above`, `notebook:merge-cell-below` | `AUD-002` `T2b` | *Cell Operations*. `CellRestructure.withCellsMerged` |
| `notebook:split-cell-at-cursor` | `AUD-002` `T2b` | *Cell Operations*. `CellSplit`, merge's inverse |
| `notebook:move-cell-up`, `notebook:move-cell-down` | `AUD-002` `T2b` | *Cell Operations*. `CellEdits.withCellsMoved` |
| `notebook:duplicate-below` | `AUD-002` `T2b` | Palette-less. Registered at `index.ts:3654` and reached from the cell menu there; `CellEdits.withCellsDuplicated` here |
| `notebook:delete-cell` | `AUD-002` `T2b` | *Cell Operations*. `CellEdits.withCellsDeleted` |
| `notebook:copy-cell`, `notebook:cut-cell`, `notebook:paste-cell-below`, `notebook:paste-cell-above`, `notebook:paste-and-replace-cell` | `AUD-002` `T2b` | *Cell Operations*. The **cell** clipboard — `CellRestructure.withCellsCopied`/`withCellsCut`/`withClipboardPasted` over `EditorUiState.clipboard` |
| `notebook:copy-selected-text`, `notebook:cut-selected-text`, `notebook:paste-text`, `notebook:replace-selection` | `AUD-002` `T2b` | *Cell Operations*. The **text** clipboard inside one cell — *"Copy Selected Text"* (`:3786`), *"Replace Selection in Notebook Cell"* (`:5055`). Distinct from the row above, and the distinction is the one `EditorUiState.clipboard` being `List<Cell>` turns on |
| `notebook:hide-cell-code`, `notebook:show-cell-code`, `notebook:hide-cell-outputs`, `notebook:show-cell-outputs`, `notebook:toggle-cell-outputs` | `AUD-002` `T2b` | *Cell Operations*. `CellFolds.withSourceHiddenSet`/`withOutputsHiddenSet` |
| `notebook:hide-all-cell-code`, `notebook:show-all-cell-code`, `notebook:hide-all-cell-outputs`, `notebook:show-all-cell-outputs` | `AUD-002` `T2b` | *Cell Operations*. The same folds with the notebook as operand rather than the selection |
| `notebook:enable-output-scrolling`, `notebook:disable-output-scrolling`, `notebook:toggle-output-scrolling` | `AUD-002` `T2b` | The pair is *Cell Operations*; the toggle is palette-less (`:4963`). `CellFolds.withOutputsScrolledSet` |
| `notebook:toggle-heading-collapse`, `notebook:collapse-all-headings`, `notebook:expand-all-headings` | `AUD-002` `T2b` | *Notebook Operations*, but a fold rather than a navigation: `CellHeadings` here, and `NotebookActionsMenu`'s two rows |
| `notebook:render-all-markdown` | `AUD-002` `T2b` | *Notebook Operations*. Sets every markdown cell's rendered state, which is a cell state like the folds beside it — not how markdown is rendered, which is `AUD-008`'s |
| `notebook:clear-cell-output`, `notebook:clear-all-cell-outputs` | `AUD-002` `T2b` | *Cell Operations* and *Notebook Operations*. `CellEdits.withOutputsCleared`/`withAllOutputsCleared`, which discard where the folds beside them only hide |
| `notebook:undo-cell-action`, `notebook:redo-cell-action` | `AUD-002` `T2b` | *Cell Operations*. `CellUndo` |
| `notebook:undo`, `notebook:redo` | `AUD-002` `T2b` | Palette-less (`:4530`, `:4510`). The **text** history, which `CellUndo`'s own docstring names to say the two are separate scopes — so the comparison belongs beside the pair above it, not in another slice |
| `notebook:select-all`, `notebook:deselect-all` | `AUD-002` `T3` | *Notebook Operations*. `CellRangeSelection`, `NotebookCellSelection` |
| `notebook:extend-marked-cells-above`, `notebook:extend-marked-cells-below`, `notebook:extend-marked-cells-top`, `notebook:extend-marked-cells-bottom` | `AUD-002` `T3` | *Cell Operations*. `SelectionReach`, the four as one parameter |
| `notebook:move-cursor-up`, `notebook:move-cursor-down` | `AUD-002` `T3` | *Cell Operations*. Moving the head in command mode — `GAP-005` itself |
| `notebook:move-cursor-heading-above-or-collapse`, `notebook:move-cursor-heading-below-or-expand` | `AUD-002` `T3` | *Cell Operations*. The only command-mode way to walk headings, and invisible to this measurement until `T1` fixed `COMMAND_ID` |
| `notebook:enter-command-mode`, `notebook:enter-edit-mode` | `AUD-002` `T3` | *Notebook Operations*. The mode the head-relative commands are aimed from |
| `notebook:select-last-modified-cell`, `notebook:select-next-modified-cell`, `notebook:select-last-run-cell` | `AUD-002` `T3` | The first two are *Cell Operations*; the third is palette-less, *"Select current running or last run cell"* (`:5000`) |
| `notebook:run-cell`, `notebook:run-cell-and-select-next`, `notebook:run-cell-and-insert-below`, `notebook:run-all-cells`, `notebook:run-all-above`, `notebook:run-all-below` | `AUD-002` `T3` | The `RunRange` ranges. Which cells a run means is a selection question; what the kernel then does with them is `AUD-001`'s, already examined |
| `notebook:restart-and-run-to-selected` | `AUD-002` `T3` | `RunRange.UP_TO_SELECTED` — head-relative, which is why it is here and not with the restarts. Its restart half is `AUD-006`'s |
| `notebook:interrupt-kernel`, `notebook:restart-kernel`, `notebook:restart-clear-output`, `notebook:restart-run-all`, `notebook:shutdown-kernel`, `notebook:change-kernel`, `notebook:reconnect-to-kernel`, `notebook:get-kernel`, `notebook:close-and-shutdown` | `AUD-006` `T3` | Kernel lifecycle. `shutdown-kernel` and `get-kernel` are palette-less (`:3171`, `:4567`); the rest are *Notebook Operations* |
| `notebook:create-console`, `notebook:create-subshell-console`, `notebook:run-in-console` | `AUD-006` `T2` | The console, which is that slice's own area |
| `notebook:access-previous-history-entry`, `notebook:access-next-history-entry` | `AUD-006` `T3` | *Notebook Operations*. The history walk — the `history_request` behind it is `present` in `AUD-001` above, so what is left is the walk ([ADR-0052](../adr/0052-kernel-history-is-the-kernels-and-a-walk-belongs-to-its-cell.md)) |
| `notebook:export-to-format`, `notebook:show-export-guidance` | `AUD-008` `T2` | Formats. *"Enable notebook exports"* (`:775`) is the guidance dialog for a deployment that can convert nothing |
| `notebook:trust` | `AUD-008` `T2` | *Notebook Operations*. Trust decides what a notebook's outputs may render |
| `notebook:copy-to-clipboard`, `notebook:create-output-view` | `AUD-008` `T2` | Palette-less, and both are *output* operations by their own labels: *"Copy Output to Clipboard"* (`:2078`) reads `cell.outputArea`, *"Create New View for Cell Output"* (`:1751`) |
| `rendermime:handle-local-link` | `AUD-008` `T2` | The registry's one command — a link inside rendered output activating. `RichInlineString`'s `RICH_LINK_TAG` is ours |
| `markdownviewer:open`, `markdownviewer:edit`, `markdownviewer:trust` | `AUD-008` `T2` | The markdown *document* surface — open rendered, edit the source, trust the file — against a `.md` tap landing in `FileEditorScreen` |
| `htmlviewer:trust-html` | `AUD-008` `T2` | The HTML document surface's trust act — declared in `index.tsx`, `htmlviewer-extension`'s only source file |
| `mathjax:clipboard`, `mathjax:scale` | `AUD-008` `T2` | The rendered-equation context menu — `schema/plugin.json` mounts both on a `.MathJax` node only a rendered equation creates |
| `imageviewer:zoom-in`, `imageviewer:zoom-out`, `imageviewer:reset-image`, `imageviewer:rotate-clockwise`, `imageviewer:rotate-counterclockwise`, `imageviewer:flip-horizontal`, `imageviewer:flip-vertical`, `imageviewer:invert-colors` | `AUD-008` `T2` | The image document surface's eight transforms against what ours draws when a `.png` opens |
| `csv:go-to-line`, `tsv:go-to-line` | `AUD-008` `T2` | `csvviewer-extension` carries two families — the table viewer's go-to-line per delimiter |
| `mermaid:copy-source` | `AUD-008` `T2` | The diagram surface's one id — copy the mermaid source a rendered diagram was drawn from |
| `notebook:toggle-render-side-by-side-current`, `notebook:set-side-by-side-ratio`, `notebook:toggle-virtual-scrollbar` | `AUD-009` | Layout of the notebook pane. §8.3 makes VS Code authoritative for presentation, so the comparison is that slice's |
| `notebook:toggle-all-cell-line-numbers` | `AUD-009` | The gutter — `EditorSettings.lineNumbersShown`, drawn by `LineNumbers.kt`, and presentation rather than a cell operation |
| `notebook:create-new` | `AUD-009` | Added to the palette on its own. Creating a document is chrome, not a cell command |
| `notebook:toggle-autoclosing-brackets` | `AUD-003` | Palette-less (`:2223`). A typing rule — `PythonTypingRules`, which is `GAP-004`'s own subject — carried app-wide by `EditorSettingsRepository` |
| `documentsearch:start`, `documentsearch:startWithReplace` | `AUD-004` `T2` | The find bar opening — `EditorUiState.withSearchOpened`/`withReplaceShown`, reached by the top bar's Find |
| `documentsearch:highlightNext`, `documentsearch:highlightPrevious` | `AUD-004` `T2` | The bar's next/previous — `withSearchStepped` over `SearchHit`s |
| `documentsearch:end` | `AUD-004` `T2` | The bar closing — `withSearchClosed`, and Back |
| `documentsearch:toggleSearchInSelection` | `AUD-004` `T2` | The `selection` filter — `SearchState.searchSelection` |
| `fileeditor:find`, `editmenu:find` | `AUD-004` `T2` | Carried ids from packages later slices own: the file editor's find and the Edit menu's. `editmenu:find-and-replace` sits beside it only in `plugin.json`'s schema, never declared; `fileeditor:replace-selection` reads like this slice's and is the completer's write-over-selection (`fileeditor-extension/src/commands.ts:733`) |
| `toc:display-numbering`, `toc:display-h1-numbering`, `toc:display-outputs-numbering` | `AUD-004` `T4` | The schema's numbering settings as commands — `EditorSettings.toc*`/`TocNumbering` |
| `toc:show-panel` | `AUD-004` `T4` | The outline's entry point — `TableOfContentsSidePanel` at two panes, `TableOfContentsPanel` where there is no side area |
| `toc:toggle-collapse` | `AUD-004` `T4` | The ToC *list's* own fold — `TocFolds`. The document's section fold (`jp-MarkdownHeadingCollapsed`, `CellHeadings`) is a cell operation, `AUD-002`'s |
| `toc:run-cells` | `AUD-004` `T4` | A ToC-row context-menu command — *"Select and Run Cell(s) for this Heading"* — declared by `notebook-extension` under the `toc:` prefix (`index.ts:351`, mounted at `toc-extension/schema/registry.json:18-24`). The package-whole read of `toc-extension` never sees it, and `notebook-extension`'s own `notebook:` filter drops it — carried for exactly that reason |
| `filebrowser:go-up`, `filebrowser:go-to-path`, `filebrowser:edit-path` | `AUD-005` `T2` | Navigation — `go-up` is one directory (`ExplorerViewModel.goTo`), `go-to-path` a jump to an argument, `edit-path` the breadcrumb's edit mode (`filebrowser-extension/src/index.ts:2020`) |
| `filebrowser:open-path`, `filebrowser:open-directory`, `filebrowser:refresh` | `AUD-005` `T2` | Reaching a path in the browser — `open-path` resolves then `goToPath`s (`:1476`), `open-directory` activates the widget holding it (`:720`) — and relisting it |
| `filebrowser:open` | `AUD-005` `T2` | The listing row's own act — a directory `cd`s, a file delegates to `docmanager:open` (`:1557`); ours is the row tap in `ExplorerScreen`. Its `docmanager:open` twin is `T3`'s below |
| `filebrowser:activate`, `filebrowser:show-panel`, `filebrowser:hide-main`, `filebrowser:toggle-main`, `filebrowser:share-main` | `AUD-005` `T2` | The browser widget's own chrome — reveal, hide, and `share-main` = `copyShareableLink` on the main widget (`:130`), an act on the panel rather than on a file |
| `filebrowser:select-all` | `AUD-005` `T2` | The listing's selection — whether ours has anything to select-all over |
| `filebrowser:toggle-date-created`, `filebrowser:toggle-file-checkboxes`, `filebrowser:toggle-file-filter`, `filebrowser:toggle-file-size`, `filebrowser:toggle-hidden-files`, `filebrowser:toggle-last-modified`, `filebrowser:toggle-navigate-to-current-directory`, `filebrowser:toggle-show-full-path`, `filebrowser:toggle-single-click-navigation`, `filebrowser:toggle-sort-notebooks-first` | `AUD-005` `T2` | `browser.json`'s settings as commands — columns, hidden files, the filter, checkboxes, full path, single-click, sort order — against `ExplorerSettings` |
| `filebrowser:create-new-directory`, `filebrowser:create-new-file`, `filebrowser:create-new-markdown-file`, `fileeditor:create-new`, `fileeditor:create-new-markdown-file` | `AUD-005` `T3` | Creating — `ExplorerBar`'s NewMenu → `WorkspaceRepository.createFile`/`createFolder`. The `fileeditor:` pair is carried: it creates files rather than editing one (`commands.ts:60`, `:62`), while the rest of its package waits for its own slices |
| `filebrowser:rename`, `filebrowser:duplicate`, `filebrowser:copy`, `filebrowser:cut`, `filebrowser:paste` | `AUD-005` `T3` | The acts on the selected rows — ours' rename goes `WorkspaceRepository.rename` through the 409 → ask → overwrite split; whether a file clipboard exists is the verdict's question |
| `filebrowser:delete`, `filebrowser:shutdown` | `AUD-005` `T3` | Permanent delete, no trash — `WorkspaceRepository.delete` refuses a non-empty folder — and shutting down what a file's sessions run |
| `filebrowser:download`, `filebrowser:copy-download-link`, `filebrowser:copy-path`, `filebrowser:open-url`, `filebrowser:open-browser-tab` | `AUD-005` `T3` | Leaving the browser — `DocumentSharer` is weighed against `download` here though export's format question is `AUD-008`'s |
| `docmanager:new-untitled`, `docmanager:open`, `docmanager:rename`, `docmanager:delete`, `docmanager:delete-file`, `docmanager:duplicate`, `docmanager:clone`, `docmanager:download`, `docmanager:open-browser-tab`, `docmanager:show-in-file-browser` | `AUD-005` `T3` | The document manager's own file acts — the commands every other surface calls (`docmanager-extension/src/index.tsx`, no palette category) |
| `docmanager:save`, `docmanager:save-all`, `docmanager:save-as`, `docmanager:toggle-autosave`, `docmanager:restore-checkpoint`, `docmanager:reload` | `AUD-005` `T4` | Persistence — `DocumentAutosave`'s debounce over `EditorSettings.autosave`/`autosaveInterval`, and what checkpoints/save-as/reload mean where there is no document manager |
| `docmanager:recents`, `docmanager:clear-recents` | `AUD-005` `T4` | Recents — `RecentDocumentsRepository` → `DiskRecentDocumentsRepository`, `RecordOpening`, `RecentsSection`. `docmanager:recents` is the one id the `docmanager` package itself declares |
| `console:run-forced`, `console:run-unforced`, `console:linebreak` | `AUD-006` `T2` | The prompt's run acts — `Shift Enter` forced in both modes, `Enter` unforced and `Accel Enter` the linebreak in terminal mode (`schema/tracker.json`), and notebook-mode Enter's run-or-newline is `requestIsComplete`'s call (`widget.ts:979`); `submit()` and Enter's newline are ours |
| `console:replace-selection`, `console:undo`, `console:redo` | `AUD-006` `T2` | The prompt's text acts — the completer's write-over-selection and the console's own history |
| `console:clear` | `AUD-006` `T2` | The transcript emptied — `ConsoleViewModel.clear`, unconditional as `CodeConsole.clear()` is |
| `console:interrupt-kernel`, `console:restart-kernel`, `console:shutdown`, `console:close-and-shutdown` | `AUD-006` `T2` | The console's kernel ends — Interrupt sits in `ConsoleActionsMenu`; Restart's absence is `GAP-009` itself |
| `console:change-kernel`, `console:get-kernel` | `AUD-006` `T2` | The console's kernel asks — `selectKernel` and the invisible semantic delegate |
| `console:create`, `console:open`, `console:inject`, `filemenu:create-console` | `AUD-006` `T2` | Reaching a console — `create` mints (the launcher's card, and `notebook:create-console`'s `split-bottom` "Linked Console"), `open` reveals by path, `inject` runs code in a console found by session path (`index.ts:1294-1337`); the File menu's entry is a semantic delegate to `console:create` |
| `console:rename`, `console:interaction-mode`, `console:toggle-autoclosing-brackets` | `AUD-006` `T2` | The console's own shape — its title, the terminal-vs-notebook `interactionMode`, and the universal bracket toggle's reach |
| `completer:invoke-console`, `completer:select-console` | `AUD-006` `T2` | The console's own completer pair, declared inside `console-extension` — completion on the prompt field |
| `kernelmenu:interrupt`, `kernelmenu:reconnect-to-kernel`, `kernelmenu:restart`, `kernelmenu:restart-and-clear`, `kernelmenu:change`, `kernelmenu:shutdown`, `kernelmenu:shutdownAll` | `AUD-006` `T3` | The Kernel menu's semantic delegates (`mainmenu/src/kernel.ts` `SemanticCommand`) — each executes whatever the current widget registered for the act, and both registrants (`notebook:*` above, `console:*` above) are this slice's |
| `filemenu:close-and-cleanup` | `AUD-006` `T3` | The File menu's close-and-shutdown — a semantic delegate to `*:close-and-shutdown` (`mainmenu-extension/src/index.ts`), which this slice already owns |
| `running:show-panel`, `running:show-modal` | `AUD-006` `T4` | Reaching the panel — the rail entry versus the searchable-sessions dialog (`SearchableSessions`) |
| `running:kernel-open-session`, `running:kernel-shut-down`, `running:kernel-shut-down-unused` | `AUD-006` `T4` | The sessions section's row acts — open, per-row shutdown, and the grouped-list's "Shut Down Unused" |
| `running:kernel-new-console`, `running:kernel-new-notebook` | `AUD-006` `T4` | Minting a document on a running kernel — the kernel row's two spawn acts (`kernels.tsx`) |
| `terminal:create-new`, `terminal:open`, `terminal:open-folder-in-terminal` | `AUD-007` `T2` | Reaching a terminal — `create-new` mints (the launcher's card), `open` reveals a running one by name, `open-folder-in-terminal` lands a folder's `cwd` on `TerminalGateway.open` |
| `terminal:copy`, `terminal:copy-link`, `terminal:paste` | `AUD-007` `T2` | The pane's clipboard — copying the selection `GridSelection` holds, a link under the cursor, and what a paste hands `send` |
| `terminal:increase-font`, `terminal:decrease-font`, `terminal:set-theme`, `terminal:refresh` | `AUD-007` `T2` | The pane's own presentation — the font pair (`TerminalActionsMenu` over `TerminalSettings`), the theme pick, and re-fit/re-send-size weighed against a layout that already resizes |
| `terminal:shut-down` | `AUD-007` `T2` | Ending one — `TerminalGateway.close`; the running panel's ✕ for it is `AUD-006` `T4`'s, verdicted above |

### The editing half — verdicts

`T2b`'s ids, one row per family as above, and every verdict read against the code down the dispatch
— `CellActionItems` (the row) → `NotebookCellEdits.applyTo` → `applyingFrom` / `applying` /
`transformed` in `ui/editor/CellOperation.kt` → the transform → `recording` — rather than against a
grep, which `docs/prd/AUD-002-*.md` showed is wrong in both directions here. The text half was
**measured on the tablet** as well as read, because what it rests on is the platform's and not ours:
`scripts/aud-002-text-history-driver.sh`. A verdict is what the examination found; the last column
says what discharged its obligation.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `notebook:insert-cell-above`, `notebook:insert-cell-below` | `CellAction.InsertAbove` / `Insert` → `withCellInsertedAbove` / `withCellInserted` | present | the head only, as `insertAbove`/`insertBelow` act on the active cell (`actions.tsx:545`, `:582`) |
| `notebook:insert-heading-above`, `notebook:insert-heading-below` | `CellAction.InsertHeading` → `withHeadingInserted` | adapted | same placement, end of the section; the *label* reads "after section" where the reference says "below", and `CellActionItems.kt`'s `addingItems` says why |
| `notebook:change-cell-to-code`, `notebook:change-cell-to-markdown`, `notebook:change-cell-to-raw` | `CellAction.ChangeKind` → `withCellsKindChanged` | present | id kept, outputs dropped, a new markdown cell left unrendered — `changeCellType`'s three rules; the menu offers the two types the head is not |
| `notebook:change-cell-to-heading-1`, `notebook:change-cell-to-heading-2`, `notebook:change-cell-to-heading-3`, `notebook:change-cell-to-heading-4`, `notebook:change-cell-to-heading-5`, `notebook:change-cell-to-heading-6` | `CellAction.ChangeToHeading` → `withCellsHeadingLevel` | adapted | six palette entries become one row and a picker, and the flat six on the accessibility surface — `CellActionItems.kt`'s `headingLevelItems` and `CHANGE_TO_HEADING` |
| `notebook:merge-cells`, `notebook:merge-cell-above`, `notebook:merge-cell-below` | `CellAction.Merge` → `withCellsMerged` | present | two rows for one cell, one for a run, because `mergeAbove` is read only when one cell is marked (`actions.tsx:390-425`) |
| `notebook:split-cell-at-cursor` | `CellAction.Split` → `withCellSplit` | adapted | one selection, so two or three fragments, never more — a `TextFieldState` carries one; `CellSplit.kt` states the narrowing |
| `notebook:move-cell-up`, `notebook:move-cell-down` | `CellAction.Move` → `withCellsMoved` | present | the run moves as a block and stays marked, as `move` has no `deselectAll()` |
| `notebook:duplicate-below` | `CellAction.Duplicate` → `withCellsDuplicated` | present | `'belowSelected'`, outputs and count kept, a collapsed heading taking its section (`CellOperation.kt`'s `operandOf`) |
| `notebook:delete-cell` | `CellAction.Delete` → `withCellsDeleted` | present | the last cell's deletion leaves an empty code cell, inside the same undo entry |
| `notebook:copy-cell`, `notebook:cut-cell` | `CellAction.Copy` / `Cut` → `withCellsCopied` / `withCellsCut` | adapted | the clipboard is per notebook where the reference's is a singleton — `EditorUiState.clipboard`'s docstring says why |
| `notebook:paste-cell-below` | `CellAction.Paste` → `withClipboardPasted` | **defect** | two, found by the walk below and fixed red-first: the pasted cell's outputs and count never reached the maps beside `cells`, so a pasted copy showed blank and the next save wrote it so (`CellRestructureTest.pastingACopiedCellBringsItsOutputsAndCountUnderTheNewId`, `pastingACutCellBringsItsOutputsAndCountBack`, and the `aud-002-paste-outputs` flow on the tablet); and `clipboardWasCut` never cleared, so a second paste reused the cut id where `recordCellClipboardInteraction('paste')` flips the reference's read of it (`actions.tsx:1683`) — `onlyTheFirstPasteAfterACutKeepsTheCellsId`. The per-notebook scope above still applies |
| `notebook:paste-cell-above`, `notebook:paste-and-replace-cell` | — | **absent** | `GAP-015`. The reference's Edit menu; its cell context menu, like ours, has below alone |
| `notebook:copy-selected-text` | the platform text toolbar, in the source only | **absent** | `GAP-017`. Copying from a cell's source is the field's ([ADR-0068](../adr/0068-text-inside-a-cell-is-its-fields.md)); the command's other half — copying from an output — has nothing to select. Measured: a long press on an output opens no toolbar |
| `notebook:cut-selected-text`, `notebook:paste-text` | the platform text toolbar, over the system clipboard | **undocumented** | no reason was written anywhere; [ADR-0068](../adr/0068-text-inside-a-cell-is-its-fields.md), written by this slice. Measured: Cut, Copy, Paste and Select all, and a cell copy never reaching the text clipboard |
| `notebook:replace-selection` | `FocusedCellInput.type` | **defect** | measured: the symbol row's `#` over a selected line gave `#kept = 1` with the line still selected, against `type`'s own promise of keyboard parity. Fixed red-first — `FocusedCellInputTest.typingOverASelectionReplacesItJustAsTheKeyboardsDoes`, and a flow on the tablet. No command id, for [ADR-0068](../adr/0068-text-inside-a-cell-is-its-fields.md)'s reason: nothing calls one |
| `notebook:hide-cell-code`, `notebook:show-cell-code`, `notebook:hide-cell-outputs`, `notebook:show-cell-outputs`, `notebook:toggle-cell-outputs` | `CellAction.ToggleSourceHidden` / `ToggleOutputsHidden` → `withSourceHiddenSet` / `withOutputsHiddenSet` | adapted | each pair is one row that sets the state opposite to the head's, because the row's label is written from the head — `CellFolds.kt`. That differs from `toggleOutput`, which is **any-of** (`actions.tsx:1999-2015`): on a mixed run whose head is collapsed the reference hides and ours shows. Read by hand |
| `notebook:hide-all-cell-code`, `notebook:show-all-cell-code`, `notebook:hide-all-cell-outputs`, `notebook:show-all-cell-outputs` | — | **absent** | `GAP-016`. The reference's View menu; the transforms already take a list |
| `notebook:enable-output-scrolling`, `notebook:disable-output-scrolling`, `notebook:toggle-output-scrolling` | `CellAction.ToggleOutputsScrolled` → `withOutputsScrolledSet` | adapted | one row with the reference's own fixed menu label, toggling by its all-of rule — `CellActionItems.kt`'s `foldingItems`, `CellFolds.allMarkedCodeCellsScrolled` |
| `notebook:toggle-heading-collapse` | `CellAction.ToggleHeadingCollapse` → `withHeadingCollapsed` | present | the head alone, as `toggleCurrentHeadingCollapse` reads `activeCell` |
| `notebook:collapse-all-headings`, `notebook:expand-all-headings` | `NotebookCellEdits.setAllHeadingsCollapsed` | adapted | menu rows where the reference has palette entries and shortcuts — `NotebookActionsMenu.kt` says why |
| `notebook:render-all-markdown` | — | **absent** | `GAP-019`. Only Run All renders markdown notebook-wide here, and it runs the code cells too |
| `notebook:clear-cell-output`, `notebook:clear-all-cell-outputs` | `CellAction.ClearOutputs` → `withOutputsCleared`; `NotebookCellEdits.clearAllOutputs` in `KernelMenu` | present | outputs, count and run record together, and the fold undone, as `clearExecution` does |
| `notebook:undo-cell-action`, `notebook:redo-cell-action` | `NotebookCellEdits.undo` / `redo` → `CellUndo`, on the editor bar | present | structural only; a cell operation never carries text back (`CellUndo.kt`) |
| `notebook:undo`, `notebook:redo` | `TextFieldState`'s own history, under `Ctrl+Z` / `Ctrl+Shift+Z` | **absent** | `GAP-018`. The history is present and measured — one entry per run of typing; what is absent is the reference's Edit → Undo, any touch way to reach it |

### The selection and navigation half — verdicts

`T3`'s ids, read the same way: down the dispatch from the control that offers each one, through
`NotebookCellSelection`, `cellFocusChanged` or `NotebookCellRuns`, to the transform in
`CellRangeSelection.kt`, `CellSelection.kt` or `CellRunRange.kt`. Where the reference's surface is
a key binding, it is `packages/notebook-extension/schema/tracker.json` and is cited by line.
Nothing on screen changed, so there was no flow to run. `GAP-005` is decided here: it is the
**select-without-editing** half that four of the ids below are missing, restated in
[`../architecture.md`](../architecture.md) §11.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `notebook:select-all` | `SelectionTopBar`'s Select all → `NotebookCellSelection.selectAll` → `withAllCellsSelected` | adapted | the head goes on the last cell where `selectAll` leaves `activeCellIndex` alone. The docstring says why: the next extend would otherwise reach `getContiguousSelection()`'s throw (`widget.ts:2344-2348`). The reference's `Accel A` (`tracker.json:635`) and Edit-menu row (`:75`) are one bar button here, and it is reachable only while a run is marked |
| `notebook:deselect-all` | the selection bar's close, and Back → `withSelectionCollapsed` | present | anchor dropped, head kept, as `Notebook.deselectAll()` does |
| `notebook:extend-marked-cells-above`, `notebook:extend-marked-cells-below`, `notebook:extend-marked-cells-top`, `notebook:extend-marked-cells-bottom` | `SelectionTopBar`'s overflow → `NotebookCellSelection.extend` → `withSelectionExtended(SelectionReach)` | present | no wrap-around, and extending onto the anchor ends the selection, both read from `actions.tsx:1314-1356`. Entering the mode first is the PRD's known deviation (`CellAction.StartSelecting`) |
| `notebook:move-cursor-up`, `notebook:move-cursor-down` | — | **absent** | `GAP-005`. `ArrowUp`/`K` and `ArrowDown`/`J` in command mode (`tracker.json:523-540`) → `selectAbove`/`selectBelow` (`actions.tsx:1099`, `:1142`), which skip hidden cells and keep the mode. No editor composable here reads a key, and no touch control moves the head without the caret |
| `notebook:move-cursor-heading-above-or-collapse`, `notebook:move-cursor-heading-below-or-expand` | the collapse half only: `CellAction.ToggleHeadingCollapse` | **absent** | `GAP-005`. `ArrowLeft`/`ArrowRight` (`tracker.json:543-550`): collapse or expand the head if it is a heading, otherwise walk to the parent or next heading (`actions.tsx:1244-1310`). The fold exists here, but the walk does not |
| `notebook:enter-command-mode` | Back → `focusManager.clearFocus()` → `cellFocusChanged` → `withMode(COMMAND)` | adapted | `Escape` and `Ctrl M` (`tracker.json:443-450`) become Back. `EditorScreen.kt` says why: it is the touch `Escape`, and dropping focus keeps one path out of edit mode |
| `notebook:enter-edit-mode` | a tap into the cell's field → `cellFocusChanged` → `withCellSelected` then `withMode(EDIT)` | adapted | `Enter` on a focused cell (`tracker.json:463`) becomes the tap. Focus *is* the mode here, and the `cellFocusChanged` KDoc gives the reason and the order |
| `notebook:run-cell` | the selection bar's Run selected cells → `run(RunRange.MARKED)`; the cell header's Run → `runCell(cell)` | **absent** | `GAP-005`. The marked run matches `runSelected` (deselect, head on the last cell run). The header's Run runs its cell **and leaves the head where it was**, but both references move the head first: JupyterLab because the click activated the cell, VS Code by `focusNotebookCell(context.cell, 'container')` (`vscode@3f3380c668d07649b953eb8278354b2f263ea6ff` `src/vs/workbench/contrib/notebook/browser/controller/executeActions.ts:295-297`). Read by hand |
| `notebook:run-cell-and-select-next`, `notebook:run-cell-and-insert-below` | — | **absent** | `GAP-020`. `Shift Enter` and `Alt Enter` (`tracker.json:605-622`), and the notebook toolbar's own Run button is `run-cell-and-select-next` (`schema/panel.json:17-19`). Nothing here advances after a run |
| `notebook:run-all-cells` | `NotebookBar` → `run(RunRange.ALL)` | adapted | runs with no head where `runAll` bails (`actions.tsx:946`). `cellsToRun` says why: the button is on the notebook's own bar and reachable before any cell is touched |
| `notebook:run-all-above`, `notebook:run-all-below` | `KernelMenu` → `run(RunRange.ABOVE / BELOW)`, greyed by `canRunAllAbove` / `canRunSelectedAndBelow` | present | above excludes the head, below includes it, and the head-placement rule is `withRunStarted`'s, all from `actions.tsx:1005-1090` |
| `notebook:restart-and-run-to-selected` | `KernelMenu` → `NotebookKernel.restartAndRunTo` → `RunRange.UP_TO_SELECTED` through `runCells` | present | `slice(0, activeCellIndex + 1)` with the selection untouched (`index.ts:3273`). Its restart half is `AUD-006`'s |
| `notebook:select-last-run-cell` | — | **absent** | `GAP-021`. *"Select current running or last run cell"* is reached by clicking the execution indicator (`packages/notebook/src/executionindicator.tsx:93-99`) |
| `notebook:select-last-modified-cell`, `notebook:select-next-modified-cell` | — | **absent** | `GAP-022`. A back/forward stack of edited cells (`widget.ts:2538-2616`), on the table of contents' toolbar (`packages/notebook/src/toc.ts:846-868`) |

### The walk

`EditorCell`'s `act` → `EditorViewModel.cellAction` (`EditorCellActions.kt`, the one door the
menu, the sheet and the accessibility context menu all take — with `GoToDefinition`,
`FormatCell` and `RenameTo` peeled off as *asks* first) → `NotebookCellEdits.applyTo` →
`applyingFrom` → `transformed` (`CellOperation.kt`) → `CellEdits` / `CellRestructure` /
`CellFolds` → `recording` (`CellUndo.kt`) → `withSelectionClamped` → `DocumentAutosave.schedule`
→ `EditorViewModel.persist` → `NotebookRepository.saveNotebook` (`DiskNotebookRepository`).
What it found, beyond the rows above:

- **Growth is a `copy()` per operation, never one per keystroke.** `applyTo` rewrites
  `EditorUiState` through `MutableStateFlow.update`, so a transform always reads the state as it
  stands — never a snapshot taken before a write was suspended. The run's results live in maps
  beside `cells` (`outputs`, `executionCounts`, `executions`) and fold back into the cell
  objects only at the state's two edges — `toEditorUiState` on the way in,
  `withResultsOnCells` on the way to disk. Undo records share the immutable spines, so an
  operation costs list structure, not cell copies — `NotebookRevision`'s own arithmetic.
- **Branching is one `when`** — `transformed` over the sealed `CellAction`, with `folded`
  split out on detekt's `LongMethod` — and the rules that are not transforms are decided before
  it runs: `applying` answers the actions nothing can apply (`Paste` on an empty clipboard),
  `operandOf` collapses a marked run onto the head the menu was opened from.
- **Failure is a state, not an event.** A refused write is `saveFailure`, drawn beside the
  notebook until a later one succeeds; `dirty` is cleared by a landing write, and only while
  that write still covers the state as it stands. A failed write is retried by the poll at the
  next interval only while dirt remains, and otherwise waits for the next edit —
  `SaveHandler`'s re-arm narrowed to dirt, the narrowing `DocumentAutosave`'s docstring states.
- **Coupling was where the defects were — two in the save half, both fixed red-first.**
  `pending` used to carry the *whole* debounce job — delay and write in one coroutine — so the
  next `schedule()` cancelled a write already suspended on the disk, and `persist`'s
  `runCatching` reported the `CancellationException` as a `saveFailure` that the next success
  then hid. And on success `persist` put its pre-suspension snapshot back over `uiState.cells`
  and `notebook`, so whatever had changed mid-write was reverted on screen — reachable through
  a run's `checkpoint`, which calls `persist` directly and never touches `pending`, making an
  older write landing second able to take a run's outputs back off the document *and* the file.
  Writes now run beside the debounce rather than inside it (`DocumentAutosave.launchWrite`),
  serialize on `writeMutex` so landing order is ask order, and `adoptWritten` rebuilds the
  screen's cells from the live state — `Context._save`'s shape
  (`packages/docregistry/src/context.ts:627-640`), tightened: the reference serializes nothing
  and clears `dirty` unconditionally, which it can afford because a JupyterLab save patches the
  model where ours writes the whole document. `NotebookSaveInFlightTest` pins all five cases;
  `DocumentAutosaveTest.aWriteInFlightIsNotCancelledByTheNextSchedule` is the machinery-level
  pin.
- **And one gap the walk records rather than repairs** — `GAP-023`:
  `DiskNotebookRepository.saveNotebook` is a bare `File.writeText` where the reference stack's
  save is `atomic_writing`, so an interrupted write can leave half a notebook.

---

## Editor language features — `AUD-003`

**The surface is three enumerations, not one.** `AUD-002` had 99 command ids; this area has
exactly one — `notebook:toggle-autoclosing-brackets` (the partition row above) — because almost
everything a cell editor does for a language is asked by typing and caret position, not invoked.
So the surface is what a language server may be asked, what a kernel may be asked without
executing, and what the editor's settings do to a keystroke. Produced, never stored:

```sh
python3 scripts/read_reference_commands.py --lsp-methods
grep -rln "requestInspect\|requestComplete\|requestIsComplete\|requestHistory" \
  reference/research_repos/jupyterlab/packages --include="*.ts" | grep -v "services\|test\|mock"
grep -rln "requestInspect\|requestComplete\|do_inspect" \
  reference/research_repos/vscode-jupyter/src --include="*.ts" | grep -v test
```

The first: `packages/lsp/src/tokens.ts`'s `Method` namespace holds **36 wire strings in four
direction enums** — 22 client requests, 6 client notifications, 4 server requests, 4 server
notifications — which is the whole of what a connection here can route, jupyterlab-lsp's plugin
contract included. The second: **eight files** send the kernel a non-execute ask —
`completer/default/kernelprovider.ts` (`complete_request`, one completer provider),
`completer/default/inlinehistoryprovider.ts`, `inspector/kernelconnector.ts` (`inspect_request`,
the Inspector panel), `tooltip-extension` (`inspect_request` on hover), `console/widget.ts`
(`is_complete_request`, the block-completeness check), `console/history.ts` and
`notebook/src/history.ts` (`history_request`, `present` in `AUD-001`), and
`notebook-extension/src/index.ts:1962` (`is_complete_request` inside `run-in-console` — both
`is_complete_request` callers are the console's, so `GAP-012`'s repair is `AUD-006`'s and this
slice records it). The third grep is vscode-jupyter's half of the same question: the intellisense
callers are `standalone/intellisense/kernelCompletionProvider.ts` and `resolveCompletionItem.ts`
— a provider that races an LSP-shaped answer against the kernel and can `do_inspect` to resolve
documentation — and the rest of the hits are the service layer and unrelated features.

**The editor's keys split three ways.** `packages/codemirror/src/extension.ts`'s
`getDefaultExtensions` declares **27 `editorConfig` entries**. Eleven are what a keystroke does
to the source or the selection and are this slice's: `autoClosingBrackets`, `indentUnit`,
`smartIndent`, `matchBrackets`, `tabSize`, `tabFocusable`, `keymap`, `readOnly`,
`extendSelection`, `rectangularSelection`, `allowMultipleSelections`. `searchWithCM` — the panel
keystroke search rides — is `AUD-004`'s. The other 15 are presentation and carry to `AUD-009`:
`codeFolding`, `cursorBlinkRate`, `highlightActiveLine`, `highlightSpecialCharacters`,
`highlightTrailingWhitespace`, `highlightWhitespace`, `lineNumbers`, `lineWrap`, `dropCursor`,
`rulers`, `scrollPastEnd`, `tooltips`, `customStyles`, `theme`, `translation`.

**Ours is a 12-operation port**, `LanguageServerPort`, and ten operations are reached through
it: `changeDocument` (`NotebookLanguageDocument` — where `didOpen` also happens, the adapter's
`_sendChange` opening a never-opened uri), `closeDocument` (the same class, for the uri a rename
replaces — the `defect` row below), `completion`, `hover`, `signatureHelp`, `definition`,
`rangeFormatting`, `rename` (one ask file each under `ui/editor/`, all behind
`NotebookVirtualDocument`'s one-`file:///`-document-per-notebook mapping), `diagnostics`
(`NotebookDiagnostics` collects the push into gutter marks), and `stop` (`AppContainer`'s
shutdown). **Two are never called through the port**, both hidden behind the name collisions
`AUD-001` warned the tool cannot see: `read_port_surface.py` reports `openDocument`
used because navigation's document-opening functions share the identifier, and `capabilities`
used because `StdioLanguageServer` holds a field of that name and checks it five times — the port
operations have no caller. `closeDocument` was the third, named nowhere until T2's fix. Measured:

```sh
python3 scripts/read_port_surface.py --unused
grep -rn "languageServer\." app/src/main/java
```

**And the server ours speaks to was probed, not assumed.** `LspSurfaceProbeTest` drove a raw
`JsonRpcChannel` against the shipped `pylsp 1.15.0` on the arm64 tablet
(`evidence/p5e-0-lsp-surface/`): it advertises completion, `completionItem/resolve`, hover,
signatureHelp (triggers `(`, `,`, `=`), definition, references, documentHighlight,
documentSymbol, foldingRange, formatting, rename and codeAction; it answers `codeAction` with
`[]` for pyflakes diagnostics; and it takes ~620ms for a completion and ~540–630ms from
`didChange` to `publishDiagnostics` — the felt debounce budget every typing-triggered feature
inherits. Two probe decisions are already load-bearing in the code: `snippetSupport` stays
undeclared because pylsp would emit `${1:}` placeholders our insert would splice literally, and
`codeAction` sits off this surface because the measured reply was empty.

### The LSP half — verdicts

`T2`'s rows. Every entry was read twice: once against what the shipped `pylsp 1.15.0` advertises
and answers (the probe above; where the probe's questions end, the rootfs's own source —
`python_lsp.py`, `workspace.py`, `plugins/pylint_lint.py`), and once for whether a surface here
asks it. Two shapes the enum does not know are in the table anyway because we send them —
`shutdown`/`exit` and `didClose`: a connection in the reference is server-managed, never stopped
and never closed, where ours owns a subprocess per Space. `foldingRange` is the inverse: pylsp
advertises it and the reference's `Method` does not route it — the 36 members above are the whole
contract, and the decline ours made lives in `backlog.md`'s not-scheduled table.

**Client → server — the 22 requests, then the 6 notifications.**

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `initialize` | `LspWire.initializeParams`, at spawn | present | utf-16 declared; `initializationOptions`/`rootUri` both `null` — the edge GAP-030 names |
| `textDocument/completion` | `completion` → `NotebookCompletion` | present | LSP first, kernel `complete_request` as fallback — the merge question is T3's own row |
| `completionItem/resolve` | — | **absent** | `GAP-024`. Advertised (`resolveProvider: true`) and probe-measured carrying `detail` + markdown `documentation`; a row's docs are never fetched |
| `textDocument/definition` | `definition` → `NotebookDefinition` | present | locations outside the virtual document dropped — `LandingOnDefinition` |
| `textDocument/diagnostic` | — | adapted | pull diagnostics (LSP 3.17); `diagnosticProvider` never advertised — the push is ours |
| `textDocument/documentColor` | — | adapted | never advertised (a CSS-shaped feature anyway) |
| `textDocument/documentHighlight` | — | **absent** | `GAP-026`. Advertised; probe-measured `[{range, kind}]`, kind splitting reads from writes |
| `textDocument/documentSymbol` | — | **absent** | `GAP-027`. Advertised; probe-measured flat `SymbolInformation[]` — the outline no pane here draws |
| `textDocument/hover` | `hover` → `NotebookHover` | present | the `inspect_request` substitution — [ADR-0069](../adr/0069-a-cells-hover-asks-the-source-not-the-live-object.md) |
| `textDocument/implementation` | — | adapted | never advertised |
| `textDocument/references` | — | **absent** | `GAP-025`. Advertised; pylsp `-32602`s without `context.includeDeclaration`, probe-measured |
| `textDocument/rename` | `rename` → `NotebookRename` | present | `WorkspaceEdit` sliced and verified back into cells — `cellSourcesAfterEdits` |
| `textDocument/signatureHelp` | `signatureHelp` → `NotebookSignatureHelp` | present | triggered on `(`, `,`, `=` — the probe's own triggerCharacters |
| `textDocument/typeDefinition` | — | **absent** | `GAP-028`. Advertised `true`; ours asks `definition` alone |
| `textDocument/linkedEditingRange`, `textDocument/inlineValue`, `textDocument/inlayHint` | — | adapted | never advertised — one reason, read by hand off the probe's initialize reply |
| `workspace/symbol`, `workspaceSymbol/resolve` | — | adapted | never advertised — and no workspace surface here would ask it |
| `textDocument/formatting` | — | **absent** | `GAP-029`. Advertised — black answers it whole-document; only the range form is ever asked, and the FileEditor owns no asks at all |
| `textDocument/rangeFormatting` | `rangeFormatting` → `NotebookFormat` | present | the cell's block as the range — `rangeOf`'s non-canonical end is pylsp's own `pylsp_format_range` accommodation |
| `textDocument/codeAction` | — | adapted | advertised and probe-measured `[]` for pyflakes diagnostics — off the surface by measurement, not by guess |
| `shutdown`, `exit` (unrouted by the reference) | `stop` | adapted | the reference never stops a server; ours owns a subprocess that must not outlive its Space — the port docstring's own rule |
| `initialized` | sent post-handshake | present | — |
| `textDocument/didOpen` | `changeDocument`'s auto-open | present | `_sendChange`'s rule — a sync is never a document's first frame |
| `textDocument/didChange` | `changeDocument`, whole text | present | `sendFullTextChange`'s shape; pylsp advertises kind 2, whole is the cheaper bound it accepts |
| `textDocument/didSave` | — | adapted | pylsp re-lints `is_saved` on it (`python_lsp.py:697`), and the one save-gated plugin ships disabled and unreachable — `pylint_lint.py`'s own `"enabled": False`, GAP-030's edge. Every enabled lint already fires on `didChange`/`didOpen` |
| `workspace/didChangeConfiguration` | — | **absent** | `GAP-030`. The `pylsp.plugins.*` route — `m_workspace__did_change_configuration` merges `settings["pylsp"]` and re-lints (`python_lsp.py:832`) — and nothing reaches it |
| `$/setTrace` | — | adapted | a wire-trace toggle; the server's own log drains on stderr (`drainStderr`), which is where a trace's detail belongs here |
| `textDocument/didClose` (unrouted by the reference) | `closeDocument` | **defect** → fixed | a rename re-keyed the uri and orphaned the server's copy — `sync` closes the replaced uri now (`NotebookLanguageDocumentTest.aRename_closesTheDocumentTheServerKnew`, `de92aa58`) |
| `textDocument/foldingRange` (unrouted by the reference) | — | adapted | the enum holds 36 members and this is not one; pylsp advertises it, and `backlog.md`'s not-scheduled table holds the code-folding decline |

**Server → client — the 4 requests, then the 4 notifications.**

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `workspace/configuration` | `result: null` — `answerServerRequest` | adapted | declined per spec and moot for pylsp, which never pulls config — its own server→client asks are `workspace/applyEdit` and `window/workDoneProgress/create`, measured off `workspace.py` and declined the same way. The push half is GAP-030 |
| `client/registerCapability`, `client/unregisterCapability`, `window/showMessageRequest` | `result: null` — `answerServerRequest` | adapted | LSP lets a client decline what it does not implement — `JsonRpcChannel`'s own comment carries the reason |
| `textDocument/publishDiagnostics` | `diagnostics` → `NotebookDiagnostics` → gutter marks | present | pushed ranges map back through the last synced mirror, never a rebuild |
| `window/showMessage`, `window/logMessage`, `$/logTrace` | on the notifications flow, dropped at `publishDiagnosticsFrom` | adapted | a server's log may never occupy the protocol stream — it drains on stderr instead (`drainStderr`'s own comment) |

**The port ops, decided.** `closeDocument` is the `defect` row above — kept, because its fix is
its caller. The other two stay uncalled by design, and the reason is now in each op's KDoc
rather than here alone:

| Operation | Decision | Why |
|---|---|---|
| `capabilities` | kept | the `initialize` answer itself — the adapter gates asks on the capability field it stored at handshake, which is why no production file names the port op; it stays the negotiated surface's one expose, and `LanguageServerPortContract` reads it |
| `openDocument` | kept | `changeDocument`'s auto-open is the only open a sync needs; the port carries the lifecycle whole rather than the used half, and the contract test opens through it |

### The kernel introspection — verdicts

`T3`'s first half. The wire verdicts are `AUD-001`'s already (`complete_request` and
`history_request` `present`, `inspect_request` `undocumented` → ADR-0069, `is_complete_request`
`absent` → GAP-012); what is judged here is the *callers* — the surfaces the references build on
those asks, and whether anything stands in their place. `vscode-jupyter` contributes a third
completion shape beside the merged list and the fallback: its provider **races** the kernel
against the other sources, taking the kernel answer if it lands first
(`kernelCompletionProvider.ts:90-132`'s `raceTimeout`), and resolves a row's documentation with
`requestInspect` or a `do_inspect` executed on a background thread
(`resolveCompletionItem.ts:136-146`, `:329`).

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `complete_request` — one ranked provider in a merged list (`manager.json`'s `availableProviders`, kernel 550 / context 500; `ProviderReconciliator` merges under a 1000ms per-provider timeout) | `NotebookCompletion` — `pylsp` first, `kernelGateway.complete` only when the server cannot answer | adapted | the docstring states the narrowing: a fallback, not a second source. The trigger is likewise ours — `completionQueryAt` wants a two-letter identifier at its end on a 250ms settle, where the reference invokes on Tab (`autoCompletion` defaults `false`) or continuous-hints per provider; `EditorSettings.completionEnabled` collapses `availableProviders` and says so. And the reply's `_jupyter_types_experimental` — the per-match `type`/`text` `kernelprovider.ts:62-75` keeps — is dropped by `KernelCompletions`, which returns bare strings |
| `CompletionProvider:context` — same-document token completion, the one provider that is always applicable | — | **absent** | `GAP-031`. The only source that answers with no kernel and no server; both of ours wait on `session.opened` |
| `kernelprovider.resolve` → `requestInspect`, and vscode-jupyter's `resolveCompletionItem` → `do_inspect` — a completion row's documentation, lazy-fetched | — | **absent** | `GAP-024`, whose own text names this kernel twin of the unasked `completionItem/resolve` |
| `@jupyterlab/inline-completer:history` — ghost-text completions ranked by `history_request` frequency | — | **absent** | `GAP-032`. The channel is absent whole: no inline provider, and no field that draws grey text ahead of the caret |
| `inspector:open` — the contextual-help pane: `inspect_request` at `detail_level: 1`, refiring on each text change while visible (`inspector/src/handler.ts:114-172`), rendered through the rendermime registry | `CellAction.SymbolInfo` → `textDocument/hover` → `HoverPopup` | adapted | a pane a compact surface has no room for, and the live-object answer is ADR-0069's substitution — `CellAction.SymbolInfo`'s docstring |
| `tooltip:launch-notebook` — the same ask as a transient widget at `detail_level: 0`, bound to `Shift Tab` in edit mode (`tooltip-extension/schema/notebooks.json`) | the same row and popup | adapted | the ask is the same; the trigger is a key a soft keyboard does not have — `CellAction.SymbolInfo`'s docstring, whose earlier "mouse hover" wording named only VS Code's gesture |
| `NotebookCellBangInstallDiagnosticsProvider` — vscode-jupyter's static `!pip`/`!conda`/`!matplotlib` diagnostics with a `%pip` quick fix | — | **absent** | `GAP-034`. No kernel ask at all — a lint ours never runs and `pylsp` never produces |

`is_complete_request` is restated rather than rejudged: `absent` → `GAP-012` in `AUD-001`'s
table, and both callers are the console's own — `console/widget.ts:979`'s block check and
`notebook-extension/src/index.ts:1962` inside `run-in-console` — so the repair is `AUD-006`'s
and this slice records only. `history_request`'s remaining callers split the same way:
`console/history.ts` is `AUD-006`'s, and `notebook/src/history.ts`'s walk is the one ours does
carry, behind the same opt-in flag the reference ships (`EditorSettings.accessKernelHistory`,
`SymbolRow`'s two recall keys — the `notebook:access-previous-history-entry` /
`notebook:access-next-history-entry` ids themselves are `AUD-006`'s partition rows).

### The typing rules and highlighting — verdicts

`T3`'s second half: the eleven language-side `editorConfig` keys, this slice's one command id,
and the highlighting row. Every key was read against the class that does the work — which is how
`matchBrackets` came out `adapted` rather than `present`: the highlight is ours, but the key
also bundles `closeBracketsKeymap` (`extension.ts:807-819`), and nothing here deletes a pair on
Backspace.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `notebook:toggle-autoclosing-brackets` | the Settings row writing `EditorSettings.autoClosingBrackets` | adapted | the command flips three per-cell-type configs in one write (`notebook-extension/src/index.ts:2223-2246`); a single app-wide flag is the `fileeditor:toggle-autoclosing-brackets-universal` sibling's shape, and `EditorSettings`'s docstring says so |
| `autoClosingBrackets` (`closeBrackets()`) | `PythonTypingRules.closePair` / `stepOverPartner` / `opensAPair` | adapted | on by default against the schema's `false`, because a soft keyboard's `(` is two taps deep (`EditorSettings`); a non-empty selection is left alone rather than wrapped — GAP-004's decline half; and `'''` is deliberately not closed — `opensAPair`'s own comment |
| `indentUnit` | `EditorSettings.indentUnit` → `IndentUnit` | present | the schema's own five-value enum and `'4'` default; feeds `continueIndent`, `SymbolRow`'s Tab, and `IndentUnit.formattingOptions` |
| `smartIndent` (`indentOnInput()`) | — | **absent** | `GAP-004`. The line being typed is never re-indented: `@codemirror/lang-python@6.2.1`'s regexp dedents `else:`/`elif`/`except`/`finally:`/`case` and reindents on `)`/`]`/`}` |
| `matchBrackets` (`bracketMatching()` + `closeBracketsKeymap`) | `bracketPairAround`, run by `HighlightTransformation` | adapted | the highlight answers with the lexer's string/comment mask — `print("(")` lights nothing, the same question CM's syntax tree answers; the bundled Backspace-pair-delete is not carried, and `PythonTypingRules`'s docstring states why |
| `tabSize` | — | adapted | deliberately unmodelled — `IndentUnit`'s docstring: the display width of a `\t` is a slot a Compose `TextFieldState` does not have; its default still answers `formattingOptions` |
| `tabFocusable` | the platform's own focus traversal; hardware Tab indents through `SymbolRow` | adapted | `tabIndex` management is a DOM problem a Compose field does not have — `EditorSettings`'s docstring |
| `keymap` | `SymbolRow` + the cell menu | adapted | "what edit-mode keys mean on a phone" — `SymbolRow`'s docstring; the list's `Enter` (newline-and-indent) is `continueIndent`'s text-level version, GAP-004's other miss |
| `readOnly` | — | adapted | no surface locks a field; a rendered markdown cell is a different widget, not an editor held still — `EditorSettings`'s docstring |
| `extendSelection`, `rectangularSelection`, `allowMultipleSelections` | — | **absent** | `GAP-033`. All three create what `TextFieldState` cannot hold: more than one selection |
| syntax highlighting (`syntaxHighlighting` over the Lezer parse tree) | `syntax/`'s ANTLR lexers → `SyntaxHighlighter` spans → `HighlightTransformation` styles | adapted | a generated pure-JVM lexer instead of a parse tree — ADR-0038 — over a deliberately short kind list, `SyntaxHighlighter`'s "a cell is not an IDE" |

### The walk

`CodeCellField` asks on its own typing: `rememberCellCompletions` (`snapshotFlow` over text and
selection, `distinctUntilChanged`, `collectLatest` — a keystroke cancels the debounce and the ask
in flight — on a 250 ms settle behind `completionQueryAt`'s two-letter tail) and
`rememberCellSignatureHelp` (armed-or-trigger-char behind `insideUnclosedParen`, 120 ms — VS
Code's `parameterHintsModel.DEFAULT_DELAY`) both reach `NotebookAsks`, wired
`viewModel.kernel.asks::*` at `EditorScreen.kt:412-414`. The menu asks enter lower down:
`EditorCellActions.cellAction` peels `GoToDefinition`/`FormatCell`/`RenameTo` out of the `when`
before `NotebookCellEdits.applyTo` sees them, and `SymbolInfo` never leaves `EditorCell` —
`symbolAsk++` fires `hoverAt` directly. From there one shape six times: the `session.opened` gate
→ `document.sync(cells.withLiveSource(cellId, code))` → the caret's virtual position →
`languageServer.<op>` → `NotebookLanguageDocument.sync` (`syncLock`; a rename's orphaned uri is
closed before the new one auto-opens) → `StdioLanguageServer.ServerPool` (the `startup` mutex:
lazy spawn, `initialize`/`initialized`, a dead process respawned on the next call) →
`JsonRpcChannel` (one write mutex, a `pending` map, `$/cancelRequest` owed on timeout-or-cancel)
→ `pylsp` over `LspFraming`'s stdio frames. Back: `channel.notifications` → `diagnosticsPump` →
`LspNotifications.publishDiagnosticsFrom` → `diagnostics` → `NotebookDiagnostics.relayPublishes`
(uri-checked against `synced`) → `toCellLines` → `diagnosticsByCellId` → the gutter marks — while
`keepSynced` feeds the document that mirror mirrors, a `didChange` per distinct
`(cells, kernelConnected)` state. What it found:

- **Growth is the whole document, twice a burst.** Every ask syncs before it asks, and every
  distinct cells-state syncs anyway; each builds a fresh `NotebookVirtualDocument` — all code
  cells joined, the block map rebuilt — even when the adapter's `synced.text == text` then skips
  the send. When the text did move the frame is the whole notebook (`sendFullTextChange`'s own
  choice), and `pylsp` pays ~540–630 ms of re-analysis per `didChange` → `publishDiagnostics`
  (probe-measured). Felt latency is the debounce plus that analysis: a completion lands ≈
  250 + 620 ms after the keystroke that earned it. Nothing sends deltas, and nothing measured
  says one should yet.
- **Branching is three gates in a row, each biased to quiet.** The query shapes decide whether a
  keystroke is worth a round trip; `session.opened` decides whether a runtime was ever paid for
  (typing boots nothing — the Space install is seconds and ~143 MB); the capability checks decide
  whether the server was offered the method at all. Answers map back through the synced mirror —
  `positionOf`, `cellLineAt`, `cellOffsetOf(Clamped)` — a markdown cell, a separator line, an
  off-document uri each read as null rather than a guess. Below that, the channel's `route` is
  the last `when`: reply, notification, or a server→client request declined `result: null`. One
  hole named rather than fixed: completion has an off-switch (`EditorSettings.completionEnabled`)
  and signature help has none — VS Code's `editor.parameterHints.enabled` has no counterpart here.
- **Failure is designed silence, and the catches are where the design lives.** Every ask's broad
  `catch (failed: Exception)` keeps a dead `pylsp` or a dying channel out of a `LaunchedEffect`;
  `CancellationException` rethrows so `collectLatest`'s next-keystroke cancel lands, and the
  channel pays the `$/cancelRequest` courtesy on the way. The kernel fallback's
  `catch (IllegalStateException)` is load-bearing — `StaleKernelSessionException` is one *by
  declaration*, so a session the idle backstop stopped is a quiet `NONE`, not a crash.
  `keepSynced`'s `runCatching` leaves the last mirror standing; a dead server respawns inside
  `serverFor`'s lock — its `documents` map dies with the process, so the next sync's auto-
  `didOpen` is also the recovery — and `close()` fails every pending request rather than leave
  one hung. The honest lag: a push maps against `synced`, the last text *sent*, which can sit
  ahead of the version `pylsp` analyzed — a ~600 ms window of possibly-misplaced marks, corrected
  by the next push, the same approximation the reference accepts.
- **Coupling is the walk's real yield — three knots.** `BASE_SPACE` is hardcoded at every ask
  site, at the document's sync and at `AppContainer`'s idle stop: the language surface answers
  only for the base Space, `JD-001`'s question arriving from a second direction. `NotebookKernel`
  owns `asks`, `history`, the virtual document and the diagnostics watch because it owns the
  `session` each gates on — one owner keeps the gate singular, at the price of a class named
  "kernel" holding the language surface. And the watch borrows the session for a runtime that is
  not it: `keepSynced` keys on `kernelConnected`, but `pylsp` is the app's own subprocess whose
  lifetime is the Space's — spawned by any sync, stopped only by the idle stopper's
  `stopServer`. A session ended elsewhere (`NotebookKernelSession.endIfMatches`, the Running
  panel's shutdown) stops the syncs while the server and its document live on, and the marks
  freeze on whatever lines they last mapped; the other way, typing against a session the idle
  stopper made stale respawns ~60 MB of `pylsp` while `jupyter_server` is still down. Recorded
  as `GAP-035`.

---

## Search, replace and the table of contents — `AUD-004`

**The surface is two packages' command ids plus everything a command never names.**
`documentsearch-extension` declares **6 ids** and `toc-extension` **5** — produced, never stored:

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package documentsearch-extension
python3 scripts/read_reference_commands.py --jupyterlab-package toc-extension
```

Two carried ids join them: `fileeditor:find` (*"Find…"* on the current editor,
`fileeditor-extension/src/commands.ts:624`) and `editmenu:find` — where
`editmenu:find-and-replace` sits beside it only in `plugin.json`'s schema, never declared, and
`fileeditor:replace-selection` reads like this slice's and is the completer's
write-over-selection (`commands.ts:733`). And `searchWithCM`, the `editorConfig` key the
keystroke search rides (`packages/codemirror/src/extension.ts:879`), carried from `AUD-003`.

**The rest of the surface is registries, filters and settings.** `ISearchProviderRegistry` takes
one provider per widget factory and `ITableOfContentsRegistry` a factory per document kind — the
notebook, the LaTeX/Markdown/Python file editors, the markdown viewer:

```sh
grep -rn "searchRegistry.add" reference/research_repos/jupyterlab/packages --include="*.ts"
# jp-notebookSearchProvider, jp-fileeditorSearchProvider, csv, tsv, terminal
grep -rn "tocRegistry.add" reference/research_repos/jupyterlab/packages --include="*.ts"
# notebook + LaTeX/Markdown/Python file factories + markdownviewer
```

The notebook provider's own surface is its filters — `output` (off by default) and `selection`
(`packages/notebook/src/searchprovider.ts:229-256`) — over one `CellSearchProvider` per cell
editor plus a `GenericSearchProvider` per output widget
(`packages/cells/src/searchprovider.ts:63-100`). The ToC's is `toc-extension/schema/registry.json`:
`numberHeaders`, `numberingH1`, `maximalDepth`, `includeOutput`, `syncCollapseState`. **And the
workspace half is VS Code's alone** — JupyterLab registers no workspace search, so the panel's
authoritative reading is `contrib/search/browser/` with `notebookSearch/` (`webviewResults`,
`isMultilineRegexSource` → `IPatternInfo.isMultiline`), `services/search/common/`, and
`contrib/outline/browser/` for the document-structure half.

**Ours** is the find bar over `SearchState`/`searchHits`/`SearchHit.outputAt` — source and
rendered outputs — the workspace walk behind the one-operation `WorkspaceSearcher` port
(`DiskWorkspaceSearcher` + the transcribed `glob/`), the replace-all through `OpenDocuments`, and
the outline as `DocumentOutline`/`tableOfContentsHeadings` drawn by `TableOfContentsSidePanel` at
two panes or `TableOfContentsPanel` at one. The two recorded holes are this slice's to decide:
`GAP-001` (the walk never reaches an output) and `GAP-002` (a result row *is* a line). Produced:

```sh
python3 scripts/read_port_surface.py           # WorkspaceSearcher: search, one operation
python3 scripts/read_port_surface.py --unused  # nothing of this slice's is dead
```

**Carried neighbours, restated not rejudged.** The document's own section fold
(`CellHeadings.withHeadingCollapsed`) and the ToC toolbar's two
`notebook:select-*-modified-cell` ids are `AUD-002`'s verdicts (`GAP-022`); the rest of
`fileeditor-extension` and `mainmenu-extension` waits for the slices that own them. The
partition rows are in the shared table under `AUD-002`'s heading — `documentsearch:*` and the
carried ids to `T2`, `toc:*` to `T4`.

### The in-document half — verdicts

`T2`'s entries, read down the dispatch — `EditorTopBars`/`FindTopBar` → `NotebookSearch` →
`SearchState`/`searchHits`/`SearchHit` → `CellReveal`/`CellSearchHighlight`, and the replace half
through `NotebookReplace` — and for the carried ids, down the file editor's dispatch, where it
simply stops. The walk is what found the defect below: the reference's query is live data
(`cells.changed` and `outputLengthChanged` each restart it,
`packages/notebook/src/searchprovider.ts:559-590`, `packages/cells/src/searchprovider.ts:76-81`),
and ours re-ran only on typed edits and replaces, so a find bar left open across a structural
edit counted a notebook that was no longer there.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `documentsearch:start`, `documentsearch:startWithReplace` | the top bar's Find → `withSearchOpened` / `withReplaceShown`, a contextual bar rather than the floating overlay | adapted | the bar opens empty where `getInitialQuery` reads `window.getSelection()` — `withSearchOpened`'s docstring; and the two settings the overlay would read are declined where they would matter — `withSearchQuery`, `withSearchInSelection` |
| `documentsearch:highlightNext`, `documentsearch:highlightPrevious` | `withSearchStepped` over one hit list → `withMatchRevealed` | present | wrap, reveal and the head moving onto the match, read from `searchprovider.ts:389-401`, `:604-706`; `NotebookSearchTest`'s step tests |
| `documentsearch:end` | `withSearchClosed`, and Back | adapted | `Escape` is Back on touch — `EditorScreen`'s `BackHandler`; `endQuery` disposes the model there, `search = null` does it here |
| `documentsearch:toggleSearchInSelection` | the bar's row → `withSearchInSelection` → `onlySelected` over `selectedCellIds` | **absent** | `GAP-037`. The marked-run half is `isSelectedOrActive` exactly (`searchprovider.ts:369-374`); the missing half is `getSelectionState`'s text mode — selected *lines* inside one edit-mode cell (`:166-174`, `:264-276`) — which `EditorUiState` has no shape for (ADR-0068) |
| `fileeditor:find` | — | **absent** | `GAP-036`. `editor.execCommand(findNext)` over a `searchWithCM` panel (`commands.ts:624-641`), with `jp-fileeditorSearchProvider` as the overlay's own surface (`index.ts:288`); `FileEditorScreen` has neither — it only *lands* workspace results, via `reveal` |
| `editmenu:find` | — | present | declared in `mainmenu-extension`'s constants and bound to an empty-keys schema entry (`plugin.json:269`), never `addCommand`-ed — the id cannot fire there either, so there is nothing to carry |
| `searchWithCM` | the notebook's own bar stands in; nothing in the file editor | **absent** | `GAP-036`. The key mounts CM's own search keymap — `Mod-f`/`F3`/`Mod-g`/`Escape` (`packages/codemirror/src/extension.ts:879`) — which the file editor factory turns on (`factory.ts:28`) and the notebook's overlay replaces. Ours declines the panel where the reference does too, and the decline is stated on `FileEditorScreen`; the file-editor half is the gap |
| the provider registry's breadth | the notebook's alone | **absent** | `GAP-036`. `ISearchProviderRegistry` gets five registrations — notebook, file editor, csv, tsv, terminal. csv/tsv open through the file editor here, and the terminal is a real screen with the same hole |
| one provider per cell, plus per-output providers | one hit list in document order, `SearchHit.outputAt` marking the output half | adapted | the split the arithmetic exists for is the same; held apart it is summed, held together it is already the sum — `SearchHit.kt`'s own accounting |
| a query stays live under change | `withSearchRecomputed` / `withSearchedOutputsRecomputed`, now called from every seam that moves what a search reads | **defect** | found by the walk and fixed red-first: `editCell` and the two replace paths re-ran the query, but a cell delete, duplicate, move, undo, rename, format or a stream's output left the counter counting stale hits — `cells.changed`/`outputLengthChanged` restart it there. `NotebookSearchRestartTest`: `deletingACellUnderAnOpenSearchDropsItsHits`, `duplicatingACellUnderAnOpenSearchCountsItsCopy`, `undoingACellOperationUnderAnOpenSearchFindsAgain`, `aRenameUnderAnOpenSearchFindsTheNewText`, `anArrivingOutputIsCountedWhileOutputsAreSearched`, `aRerunDropsTheHitsItsEarlierOutputsHad` |
| a pattern mid-parse keeps the last settled answer — `_updateSearch`'s catch sets `_parsingError` and returns before `endQuery` is reached, so the provider goes on highlighting what the last good query found (`searchmodel.ts:339-369`); `!isInputValid` early-returns the same way on the workspace side | `withSearchQuery` on `Invalid` writes only `parseError`, `hits`/`current` standing | **defect** | found by the walk and fixed red-first: a `(` typed mid-pattern emptied every highlight it would restore a keystroke later, and the workspace arm was already verdicted `present` on the same rule — `NotebookSearchTest.anUnparseablePatternKeepsTheLastQuerysHits` |
| the `output` filter | `searchOutputs` → `outputsSearched` → `outputHitsOf` | adapted | only what `RenderedOutput.Text` draws is searched, where the reference searches its rendered DOM — `outputHitsOf`'s docstring and ADR-0043; `supportReplace: false` is `outputsSearched`'s rule |
| `searchDebounceTime`, `autoSearchInSelection` (`documentsearch-extension/schema/plugin.json:72-86`) | no settings carried | adapted | a re-find is one synchronous pass over state, so per-keystroke is already honest; the selection filter arms only by hand — the reasons live on `withSearchQuery` and `withSearchInSelection` |

### The workspace half — verdicts

`T3`'s entries, read down both dispatches. Ours: `SearchSidePanel` → `WorkspaceSearchViewModel`'s
debounced `ask` → the one-operation `WorkspaceSearcher.search` → `DiskWorkspaceSearcher`'s walk
(`SearchFilter`/`PathGlob` on each path, `matchedLinesIn`/`matchedCellsIn` on each text) →
`SearchProgress` → `SearchResults`, and the replace half through `WorkspaceReplaceAll` →
`WorkspaceReplace` → `OpenDocuments` or the two repositories → `ReplaceUndo`. Theirs:
`searchWidget`/`searchView` → `queryBuilder.text` → `ISearchService.textSearch`, which merges open
editor *models* (`getOpenEditorResults`, `searchService.ts:120-145,497-575`) with the disk providers
and with `notebookSearchService`/`extHostNotebook`'s notebook paths → `textSearchHeading.replaceAll`
→ `replaceService` → `bulkTextEdits` → `undoRedoService`. JupyterLab registers no workspace search —
this half is VS Code's alone, and `searchEditor`'s *results as a document*, the AI-search branch and
`searchActionsFind`'s folder-scoped entry are other surfaces (none is a `contrib/search` command id
this slice owns). The port stands at one operation, `search`, and nothing in this slice is unused —
`read_port_surface.py` above.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `ISearchService.textSearch` — `ISearchProgressItem`s streamed, resolving to `ISearchComplete.limitHit` | `Flow<SearchProgress>` — `Found` per matching file, then one `Complete(limitHit)` | present | one flow is the stream *and* its end (`search.ts:595-607`, `search.ts:599-604`); `WorkspaceSearcher`'s docstring and `WorkspaceSearcherContract` pin both halves for the fake and the disk walk |
| `search.maxResults` (20 000, `null` = none) spent per result, `limitHit` drawn as a warning | `MAX_SEARCH_RESULTS` spent as the walk goes; `RESULTS_ARE_A_SUBSET` is the reference's own words | present | `emitMatchingFiles`'s budget stops *reading* at the cap, and `limitHit` is the walk's report not a caller's count (`searchView.ts:1670`, `:1897-1899`; `search.contribution.ts:202-205`) |
| `files.exclude` + `search.exclude` folded into every query — eight registered defaults | `DEFAULT_EXCLUDED_GLOBS`, the same eight | present | `commonQuery` folds the boxes and both settings (`queryBuilder.ts:140-160`); `SearchFilter` prunes a matching *directory*'s subtree — ripgrep's own behaviour, which is what makes `**/node_modules` (no trailing globstar) mean what it means |
| `.gitignore`/`.ignore` honoured by default — `useIgnoreFiles` (true) plus global and parent variants | nothing is read but the globs | **absent** | `GAP-038`. The toggle feeds `disregardIgnoreFiles` into the query (`searchView.ts:1666-1671`, `patternInputWidget.ts:294-306`); a `build/`d directory the user ignored is walked here |
| the include box's scope toggles — `onlySearchInOpenEditors`, `onlySearchInChangedFiles` | no scopes past the two glob boxes | **absent** | `GAP-039`. `patternInputWidget.ts:210-227` mounts both; `commonQueryFromFileList` turns the open editors into a query (`queryBuilder.ts:258-263`). `SearchScope` is include+exclude alone |
| open documents searched *as they are* — `getOpenEditorResults` runs `model.findMatches` per open model and its hits beat the disk walk's for the same file; an open notebook answers through `widget.find` | the disk alone is walked, whatever is open | **absent** | `GAP-041`. `searchService.ts:127-145` merges model hits under "don't override open editor results", `notebookSearchService.ts:230-268` asks the widget; here `DocumentAutosave` narrows the window to a debounce, and `editsAgainst` refuses what is left |
| results stay live after the walk — `bindModel` re-matches on every model edit, `onFilesChanged` drops deleted files | a snapshot; staleness is answered at the tap and the replace instead | **absent** | `GAP-041`. `fileMatch.ts:135-175` re-finds on `onDidChangeContent` at a 250 ms schedule; `searchView.ts:2354-2370` removes deleted resources. Ours re-resolves one match on open (`MatchedLine.rangeIn`, `hitFor`) and refuses a stale replace — the row that moved is never redrawn |
| a `.ipynb` searched as cells — `contentResults` *and* `webviewResults` | `matchedCellsIn` reads cell *sources* only | **absent** | `GAP-001`. A closed notebook is deserialized and its outputs searched (`extHostNotebook.ts:513-544`); an open one searches markup input+preview and code input+output through the widget (`notebookSearchService.ts:241-268`) |
| the four `notebookInfo` filters — markup input, markup preview, code input, code output — surfaced on the box while a notebook is open | no region choice; every cell source is read | **absent** | `GAP-001` again — a filter over regions presupposes reaching them, and the rendered halves (preview, output) are exactly what that gap names. `NotebookFindFilters` defaults all four on (`searchWidget.ts:203-217`) |
| a pattern that spans lines — `isMultilineRegexSource` or a literal `\n` sets `IPatternInfo.isMultiline` | `matchedLinesIn`, line by line; a `\n` in the pattern finds nothing | **absent** | `GAP-002`. `queryBuilder.ts:339-352` decides from the pattern; ours is bounded because a result row *is* a line — the §11 entry names the row-spans-lines presentation work a fix needs first |
| the glob boxes' language — `parseSearchPaths`, `expandGlobalGlob` | `globsFrom`/`PathGlob`, transcribed (ADR-0060) | adapted | the `searchPaths` half is not modelled: a segment that is absolute or `./`-/`../`-led becomes a search *root* there (`queryBuilder.ts:377-380`), and every path here is workspace-relative — `SearchGlobs.kt` says so |
| `search.followSymlinks` (default true) | a directory reached through a link is not entered | adapted | `isADirectoryLink`'s stated reading: a cell can `os.symlink` a loop and nothing here detects one, where ripgrep would — ADR-0060; `DiskWorkspaceSearcherTest.aDirectorySymlinkIsNotFollowed` |
| binary vs text, delegated to ripgrep (`maxFileSize` 16 GB) | the NUL rule, checked on the bytes | adapted | `rawSearchService.ts:85-87` states no rule and hands the question down; ours is ripgrep's own, quoted in ADR-0060 — a PNG is declined before it is decoded |
| a notebook that cannot be read ends nobody else's walk | `matchedCellsAt` returns null on the same condition | present | `catch (e) { return; }` per file (`extHostNotebook.ts:555-558`); `DiskWorkspaceSearcher` swallows a serializer failure the same way |
| the query cadence — typing waits `searchOnTypeDebouncePeriod` (300 ms), a toggle asks at once, a glob keystroke waits the typing delay | `debounce { it.waitMillis }` into `collectLatest` — 300 / 0 / 300 | present | `triggerQueryChange`'s `{ delay }` is the caller's (`searchView.ts:584`, `:582-588`, `:1590`); a superseded walk is cancelled rather than landed |
| a blank box, or a pattern that does not compile | blank clears the tree; invalid keeps the last settled answer | present | `searchView.ts:1631-1632`, `:1612-1614` — `walk`'s two early returns are the same two branches |
| what the finished walk says — count, `limitHit` warning, scoped "No results found in…" variants | `resultCountMessage`, `RESULTS_ARE_A_SUBSET`, `NO_RESULTS_FOUND` | adapted | `buildResultCountMessage`'s plurals are whole (`searchView.ts:2184-2195`); the second sentence's *"check your gitignore files"* and its *Open Settings* button are dropped on the stated ground — no gitignore is read and no settings screen exists (`NO_RESULTS_FOUND`) |
| the tree's shape — folder grouping under `defaultViewMode`, `collapseResults`, per-file fold, `CountBadge` | two levels (file, match), a manual fold per file, the count beside the name | adapted | `alwaysExpand` is the registered default (`search.contribution.ts:207-214`) and the folder level's width is the match's at 360 dp — `SearchResults`' docstring |
| one result row — `MatchImpl.preview`, `MAX_PREVIEW_CHARS`, `lcut`, `showLineNumbers` off | `previewOf`/`lcut` transcribed; the line number is spoken, not drawn | present | `searchTreeModel/match.ts:22,65-80`; `lcut` keeps the *last* boundary that fits — `matchRowLabel`/`fileMatchLabel` carry the spoken half |
| the tree takes actions — per-element Dismiss, per-match/file/folder Replace, Refresh, Clear, Cancel, collapse/expand-all, copy actions, query history | a row can be opened and a file folded — nothing else | **absent** | `GAP-040`. `searchActionsRemoveReplace.ts:54-243`, `searchActionsTopBar.ts:42-157`, `searchActionsCopy.ts`; a dismissed row is also how the reference *narrows what a replace writes*, which the all-or-nothing confirm here has no answer to |
| `useReplacePreview` — a match tapped while replace is armed opens a diff editor of the file rewritten | a tap always opens the document | **absent** | `GAP-042`. `onFocus` → `openReplacePreview` (`searchView.ts:2229-2237`, `replaceService.ts:140-167`); default true, and ours has no second editor shape |
| replace goes through models — `IReplaceService` → `IBulkEditService` resolves one per resource, edits it, then saves | `WorkspaceReplace` — `OpenDocuments` where open, `NotebookRepository` for a cell, `WorkspaceRepository` for a file | adapted | the same "the document is what is edited" rule where resolving a model is a repository read — ADR-0061; a cell is its own resource either way (`replaceService.ts:206-214`) |
| `_validateBeforePrepare` — a model's `versionId` per edit, the whole bulk edit thrown on the first mismatch | `editsAgainst` compares the resource's *characters*, all-or-none | adapted | no version counter exists for a file on disk, and the workspace is shared with a kernel — so the text is compared, which is stronger, and the refusal names *every* stale path where the reference throws on the first (`bulkTextEdits.ts:213-216`; `ReplaceOutcome.Refused`) |
| what is replaced is **the matches on screen**, never a re-run query | `replaceAll(files, …)` takes the `MatchedFile`s the tree holds | present | `searchResult.matches()` (`textSearchHeading.ts:296`); `WorkspaceReplace.replaceAll`'s first parameter is the same thing |
| the counted question, then the counted report | `ReplaceAllRequest` → `ReplaceAllDialog` → `replaceAllReport`, counted before the write | present | `searchView.ts:812-860`; the counts cannot disagree with the question the reader answered |
| the tree emptied on success and standing on refusal | `afterReplace` — same rule | present | `textSearchHeading.ts:296-303`; a refusal wrote to nothing, so nothing it described is stale |
| one undo across every touched model — `MultiModelEditStackElement`; `_checkWorkspaceUndo` refuses a resource that moved since | `ReplaceUndo` — the same edits inverted, refused resource by resource | adapted | the results come back with the files, which the reference's editor-side undo cannot show (ADR-0061); no redo, because nothing asks for one (`undoRedoService.ts:853-869`) |
| `preserveCase` on the replace input — substitute under regex, then take the match's case | `preserveCase` flag → `replacementFor` → `inTheCaseOf` | present | the two-step in the reference's own order (`packages/codemirror/src/searchprovider.ts:344-352`); the three arms including sentence-case pinning `Cat`→`Dog` |
| `truncateValue` — a long replacement clamped in the confirm sentence | declined | adapted | the replace box is `singleLine`, so the clamp cannot fire — stated on `replaceAllConfirmation` (`searchView.ts:889-899`) |
| a match tapped re-resolves against the opened document — `updateMatchesForEditorWidget`, same index or the last | `hitFor`/`landedOn` and `FileEditorScreen`'s `rangeIn` — land on what still verifies, or nowhere | adapted | both re-resolve rather than trust the walk's offsets (`searchView.ts:2283-2295`); the reference picks the neighbouring match when the index slid, ours lands only where the match still *is* — "null rather than a guess", `MatchedFile.kt`'s stated rule |
| the settings that would be read — `sortOrder`, `smartCase`, `seedWithNearestWord`, `seedOnFocus`, `decorations.*`, `actionsPosition`, `search.mode`, `collapseResults`, `quickOpen.*` | none carried; the fixed behaviour is each one's registered default | adapted | there is no settings screen for them to land on, and each default is what this app already does — the decline is written on `WorkspaceSearchUiState` |

The two `absent` rows a second look would merge — `GAP-041`'s live-data half and `GAP-001`'s output
half — are kept apart on the same rule T2 used: a verdict names *one* thing, and the two repair
paths share no code even where they share a file.

### The table of contents — verdicts

`T4`'s entries, read down both dispatches. Ours: `NotebookActionsMenu`'s "Show table of contents" →
`sidePanel`/`TableOfContentsPanel` → `state.tableOfContentsHeadings(tocIncludeOutput)` →
`tocRows`/`tocPrefixes` → `TableOfContentsRows`, `TocRowDisclosure`, `CollapseAllEntries` →
`TocFolds`; `jumpTo` = `revealCell` + `withCellRevealed` + `animateScrollToItem`; the numbers
written into the document itself by `headingPrefixes` → `withHeadingNumbered` on `MarkdownCell`;
`OpenDocumentOutline.publish`/`clear` carries the list to a side panel that cannot reach the
editor's `NavEntry`. Theirs: `toc:show-panel` → `activateById` → `tocRegistry.getModel(widget)` on
`labShell.currentChanged` (`index.ts:331-353`) → the model's `getHeadings` per document kind over
`filterHeadings` (`maximalDepth`, `skip`, `getPrefix` in `utils/common.ts`) → `toggleCollapse` →
`activeHeadingChanged` scrolling to the *heading element*, `NotebookActions`' execution signals
feeding `data-running`, `getToolbarItems` mounting two `notebook:select-*` ids — `GAP-022`'s,
`AUD-002`'s verdicts, restated not rejudged — and `addPrefix`/`clearNumbering` writing the numbers
into the rendered document. The schema mounts a sixth `toc:` id that `toc-extension` never
declares: `toc:run-cells` lives in `notebook-extension` (`index.ts:351`, `:5124`) for the ToC row's
context menu — carried into the partition for it.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `toc:show-panel` — `activateById`, a View-menu row, `Accel Shift K` | the rail's item at two panes; `NotebookActionsMenu`'s "Show table of contents" → `TableOfContentsPanel` where there is no side area | adapted | a rail destination for a sidebar tab — `SidePanel.TABLE_OF_CONTENTS` keeps the reference's own rank 400 last; `Accel Shift K` has no soft-keyboard shape, and the one-pane bar carries only destinations that survive being the window (`onePaneDestinations`) |
| `toc:toggle-collapse` — `someExpanded` picks the half, `toggleCollapse` writes every heading | `CollapseAllEntries` → `TocFolds.toggleAll`; `TocRowDisclosure` → `toggle` per entry | adapted | the operand is narrowed to section owners — a marked leaf hides nothing and would leave `tocSomeExpanded` true for ever (`TocRow.kt`'s stated reason); no fold controls on the overlay, whose state would die with it (`TableOfContentsRows`); the control is absent where nothing nests rather than drawn dead on `isEnabled: model !== null` (`index.ts:198`) |
| `toc:display-numbering` — `numberHeaders`, a panel-toolbar toggle | `EditorSettings.tocNumberHeaders` → `tocPrefixes` on the rows **and** `headingPrefix`/`withHeadingNumbered` on the rendered heading | adapted | both halves drawn — the command's own label is "in the document" (`index.ts:115`; `HeadingNumbering.kt`'s doc); the toggle is a settings row because the phone's ToC surface has no toolbar for it (`EditorSettings`'s doc). An output heading is *counted* but its prefix is list-only — `headingPrefixes` is keyed by `cellId` and read by `MarkdownCell` alone, where the reference writes it into the output node (`toc.ts:729-740`) |
| `toc:display-h1-numbering` — `numberingH1`, an ellipses-menu item | `EditorSettings.tocNumberingH1`, nested under the numbering toggle | adapted | `drop(1)`/`""` is `levels.slice(1)` plus the nothing-open case (`common.ts:184-194`); the setting is inert without `numberHeaders` there too — `getPrefix` returns early — so nesting the row under the toggle is the same dependency drawn (`SettingsScreen.kt:162`) |
| `toc:display-outputs-numbering` — `includeOutput`, an ellipses-menu item | `EditorSettings.tocIncludeOutput` → `tableOfContentsHeadings(includeOutput)` → `outputHeadingsOf` | adapted | reads `RenderedOutput.Document`'s Heading blocks — the mime types this app draws — rather than the rendered DOM's `h1`–`h6` (`outputHeadingsOf`'s doc); the `syncCollapseState` inhibition of it (`toc.ts:259-263`) declines with that setting's own decline below |
| `toc:run-cells` — *"Select and Run Cell(s) for this Heading"* on `.jp-tocItem`'s context menu (`registry.json:18-24`) | — | **absent** | `GAP-044`. A ToC row here takes a tap and nothing else — no context menu, no run-section. VS Code mounts the same menu on an outline entry: `notebook.section.runSingleCell`/`runCells` and `foldSection`/`expandSection` (`sectionActions.ts:30-248`) |
| `maximalDepth` — schema default 4, `filterHeadings` drops `level > maximalDepth` (`common.ts:50`) | every level listed | **absent** | `GAP-043` — and with it the author's opt-out marks `jp-toc-ignore`/`tocSkip` (`common.ts:104-106`, `markdown.ts:351-352`): which headings the list keeps is unfiltered |
| `baseNumbering` — schema default 1, `toc/base_numbering` in metadata | fixed at the schema's 1 | adapted | declined where `TocNumbering`'s docstring states it — `level === highestLevel ? baseNumbering : 1` is the same number either way (`common.ts:172`) |
| `syncCollapseState` — the ToC fold and `jp-MarkdownHeadingCollapsed` synced both ways (`toc.ts:770-802`), schema default false | `TocFolds` — the list's own folds, never the document's | adapted | declined on the reference's own default, and its cost is the schema's own note — *"inhibits the cell output headings"* (`registry.json:57-62`, read at `toc.ts:259-263`) — a coupling `TocFolds` never takes |
| a notebook carrying its own ToC config — `toc-autonumbering`, `toc/number_sections`, `toc/skip_h1_title`, `toc/base_numbering` (`configMetadataMap`, `toc.ts:565-571`) | notebook metadata is never read for the outline | **absent** | `GAP-047`. `loadConfigurationFromMetadata`/`onMetadataChanged` re-applies it per document (`toc.ts:347-377`, `:467-469`); a notebook that carries `toc/skip_h1_title` loses its numbering here |
| `ITableOfContentsRegistry`'s breadth — five factories: the notebook, the LaTeX/Markdown/Python file editors (`fileeditor-extension/src/index.ts:585-587`), the markdown viewer (`markdownviewer-extension/src/index.ts:233`) | the notebook alone — `FileEditorScreen` publishes no `DocumentOutline` | **absent** | `GAP-045`. A `.md` or `.py` opened in the file editor has headings the registry would outline — `PythonTableOfContentsModel` is a regex over `class`/`def`/`import` lines needing no server (`python.ts:21-111`); VS Code's `OutlineModel` is `DocumentSymbol`-driven for any provider (`outlineModel.ts:196-255`), with `followCursor`/sort/filter view state ours also lacks (`outlineViewState.ts:12-53`) |
| `activeHeading` — `onActiveCellChanged` → `setActiveHeading`, drawn `selected` on the row (`tocitem.tsx:60-63`); and `isRunning`/`data-running` — per-heading execution markers aggregated upward over a fold (`toc.ts:397-556`), VS Code's `exeState` bubble the same | no row marks where the document is or what it is doing | **absent** | `GAP-046`. The outline never follows the active cell and never shows a running section — both references carry both halves |
| a markdown cell's *every* heading listed (`toc.ts:283-294`, `getCellHeadings`) | the winning heading only — `MarkdownDocument.heading`'s reduce | adapted | the narrowing `tableOfContentsHeadings`'s docstring states: the fold rule already collapsed the cell to one heading, and `TocHeading.key` still tells a cell's output headings apart |
| heading-granular landing — `headingToElement`/`getIdForHeading`, an output heading scrolls to its output node (`toc.ts:630-753`) | `jumpTo` per `cellId`, after `revealCell`/`withCellRevealed` | adapted | cell-granular is the reference's own fallback when the element is not found (`toc.ts:664-674`), and the reveal is `expandParent`'s — wired to every active-cell change there (`index.ts:2871-2875`) |
| `isActive`/`isAlwaysActive` gating + the 200 ms `ActivityMonitor` (`model.ts:108-120`, `factory.ts:72-81`) | a synchronous rederivation per state change, always | adapted | `isAlwaysActive` is already true on the two reference models that write numbering into the document (`toc.ts:142-144`, `markdownviewer/src/toc.ts:57-59`); a pure derivation over state needs no activity monitor |
| `title` = `PathExt.basename` (`factory.ts:83-90`), and the two-line placeholder (`panel.ts:31-36`, `treeview.tsx:31-40`) | `state.title` → `DocumentOutline.title`; `NoHeadingsPlaceholder` carries the same two lines | present | one placeholder answers "nothing is open" and "nothing has headings" in both |
| settings pushed live to every open model — `settings.changed` → `updateSettings` (`index.ts:219-258`) | `editorSettings` republishes the outline — `LaunchedEffect(…, editorSettings)` | present | the same broadcast; `per-widget` vs per-document is a difference that cannot fire while the outline reads the open document alone |

### The walk

Four dispatches on the one `SearchQuery`. **The find bar:** `EditorTopBars` mounts `FindTopBar`
while `search != null` — find beats marking beats the document (`EditorTopBars.kt`) — →
`NotebookSearch.queryChanged` → `withSearchQuery`, where `compile`'s three arms decide: `Matching`
re-finds over `searchHits` (+ `outputHitsOf` while `outputsSearched`), `Empty` clears,
`Invalid` now stands the last hits and writes only `parseError` → `firstHitFrom` lands beside the
selected cell → the counter, and per cell `searchHighlightOf`/`outputSearchHighlightsOf` →
`CellSearchHighlight.within` clamping ranges to the field's text, which trails the model by up to
one `reportCellEdits` debounce. The arrows → `withSearchStepped` → `withMatchRevealed` —
`withCellRevealed` plus whichever of the three folds `foldsOver` finds in the way — → select,
anchor cleared → `NotebookSearch.step` schedules a write only when `cells` moved identity. The
recompute is wired to every seam that moves what a search reads: `cellAction`, `editCell`,
undo/redo, rename, format, `applyEdits`, and output events only while `outputsSearched`.
**The workspace:** `SearchBox`/`SearchOptionToggles`/`SearchScopeBoxes` →
`queryChanged`/`optionChanged`/`scopeChanged` → `ask` — state and `parseError` at once, the walk
in `waitMillis` — → `asked` (`SharedFlow`, `DROP_OLDEST`) → `debounce { it.waitMillis }` →
`collectLatest`, which is the cancellation half: a superseded walk's `Found`s never land under
another query's text → `walk`'s two early returns — blank clears, invalid keeps — →
`DiskWorkspaceSearcher.search` on IO → `pathsUnder`'s lazy sequence — a directory symlink
declined, an excluded directory's subtree pruned, a file filtered — → `matchedFileAt` (`.ipynb` →
`matchedCellsAt`, an unreadable notebook swallowed per file; else `matchedTextAt`, the NUL rule on
bytes) → `matchedLinesIn` → `cappedAt` spending `search.maxResults` → `Found` per file then one
`Complete(limitHit)` → `after` folding each into the panel's state → `searchResults` → a row's tap
→ `onRevealMatch` + `openDocument` — the match rides beside the path, because the panel may not
pick which editor a path opens — → `LandingOnAMatch`, once per request and keyed on `loaded`
rather than cells, since the reveal *changes* cells and a cells-keyed effect cancels its own
scroll → `landOnMatch` → `landedOn` → `hitFor` re-resolving the cell by **index** and `rangeIn`
re-verifying the characters → `withMatchRevealed` → scroll; the file editor's arm lands the same
match through `rangeIn(state.text)` → a `TextRange` selection → `scrollTo`. **Replace:**
`requested` → `replaceAllQuestion` counted once, so the report can never disagree with the
question → `ReplaceAllDialog` → `confirmed` reads *the screen's* `results`, never a re-query →
`WorkspaceReplace.replaceAll` → `resources()` grouping the file's matches by cell → `plan`:
`sourceOf` asks the open document first and **refuses on null rather than falling back to the
file** → `editsAgainst` comparing characters, all-or-none → `write` three routes chosen by the
resource's own shape — an open document's `applyEdits`, a closed file's `writeText`, a closed
notebook's read-copy-`saveNotebook` — → `Replaced(…, asUndo())` or `Refused(paths)` →
`afterReplace` emptying the tree on success and standing it on refusal → `undo` re-checking every
resource against the text the replace *produced* → `afterUndo` returning the cleared results. The
notebook's own replace is the same discipline at document scale: `withCurrentMatchReplaced` /
`withAllMatchesReplaced` → `recording` → `withSearchRefoundFrom`/`withSearchRecomputed` → the same
`cells`-moved save gate. **The table of contents:**
`tableOfContentsHeadings(tocIncludeOutput)` per recomposition → `LaunchedEffect(outline, title,
tocHeadings, editorSettings)` → `outline.publish` — and `DisposableEffect` → `clear` when the
entry goes — → `TableOfContentsSidePanel` (`remember(outline?.title) { TocFolds() }`, `tocRows`)
at two panes or the fold-less `TableOfContentsPanel` overlay at one → a row's tap → `jumpTo` →
`edits.revealCell` — `withCellRevealed`, returning `this` when nothing hid so a jump past no folds
writes nothing — → the index asked of the *about-to-be* document's `cellsOnScreen` →
`animateScrollToItem`. What it found, beyond the rows above:

- **Growth is a re-find per keystroke and a re-walk per debounce, both paid in full and both small
  by construction.** The find bar's answer is one synchronous pass over state the next frame reads
  anyway — no debounce, the stated reason being that the reference debounces an async DOM walk
  this app does not have. The drawn half re-filters the whole `hits` list per cell per frame
  (`searchHighlightOf`) — O(cells × hits), where the heading numbers beside it are sliced once for
  the list; bounded and deliberate, and the docstring is the argument. The workspace walk is a
  lazy `Sequence` under `collectLatest`, so a superseded query stops mid-file, and the cap is
  *learnt* one file late when the budget lands exactly — the next file is read and capped to
  nothing before `limitHit` reports true, which is how the cap is known to have cost something
  rather than merely counted. Two costs the walk measured rather than excused, both recorded:
  `editsAgainst` re-splits the resource's text **once per match** — O(matches × lines) where the
  reference's `versionId` compare is O(matches) — `GAP-048`; and the disk read is a whole-file
  `readBytes()` with nothing like `search.maxFileSize` standing between a dataset-sized file and
  the heap — `GAP-049`.
- **Branching lives on `CompiledQuery`'s three arms, and after the fix both surfaces read them
  alike.** `Empty` clears (the reference's `endQuery`), `Invalid` keeps the last settled answer
  and says so, `Matching` re-finds — `walk`'s two early returns are the same two branches on the
  panel's side. Below that the branches are each path's own: `matchedFileAt` on the `.ipynb`
  suffix, `WorkspaceReplace`'s three routes off `openDocuments.at` + `cell != null` — the match's
  shape picks its writer, no suffix rule — `foldsOver`'s three flags deciding which of a section,
  a source area or a rendered prose a match sits behind, `SearchFilter`'s exclude-before-include
  with directory pruning, `tocRows`' single `hidingBelow` level standing in for the fold stack.
- **Failure is null-rather-than-guess at every seam a shared workspace can move under.** `hitFor`
  re-resolves the cell by index and `rangeIn` re-verifies the line still says what the walk read;
  `stillAt` adds the character compare so a line still present and still long enough now saying
  something else refuses; an open document that cannot be read refuses rather than falling back
  to the file (`anOpenDocumentThatCannotBeReadRefusesRatherThanFallingBackToTheFile`); one stale
  match refuses the whole replace and names *every* stale path where the reference throws on the
  first; undo re-checks each resource against the produced text the same way. A tapped match the
  notebook no longer holds lands nowhere and is still consumed — `landOnMatch` returns null and
  `onHandled` still runs — so the request is spent and the document opens at the top.
  `matchedCellsAt` swallows an unreadable notebook per file — `extHostNotebook`'s
  `catch (e) { return; }` exactly — and a dangling symlink is passed over, where a directory link
  is deliberately declined (`isADirectoryLink`, since a cell can `os.symlink` a loop). And the
  defect the walk owes the ledger: **a pattern mid-parse emptied the find bar's hits** where both
  references keep the last settled answer — `_updateSearch`'s catch never reaches `endQuery`, the
  workspace panel was already verdicted `present` on the same rule — fixed red-first,
  `NotebookSearchTest.anUnparseablePatternKeepsTheLastQuerysHits`.
- **Coupling is the walk's real yield — one direction rule and three seams.** `data/` may not
  import `ui/editor`'s `RenderedOutput.render`, which is `GAP-001`'s own blocker: which outputs
  have searchable text is a *draw* rule and the disk walk sits a layer under it, so
  `matchedCellsIn` reads cell sources and no more. A match crosses from the panel to the editor as
  a cell **index**, not an id — `CellFile.toDomain` mints a UUID for an id-less cell, so the only
  thing a disk walk and an editor provably share is position; `hitFor` converts at landing and
  `OpenDocuments.sourceOf(cell)` takes the same index, so a replace addresses a cell identically
  to the tap that found it. And everything between panel and document is composition-scoped, never
  `AppContainer`: `OpenDocuments`, `OpenDocumentOutline`, `revealRequest` and the `WorkspaceReplace`
  built around them are `remember`ed by `MainNavigation`/`ShellSidePane`, because a document's
  `path` must be read live across a rename and `jumpTo` closes over a `LazyListState` that dies
  with its composition; `TocFolds` keyed on `outline?.title` is the same lifetime answer one level
  down, dead keys inert across same-named documents. The one rule four collaborators share —
  `step`, `replacing`, `revealCell`, `landOnMatch` — is the `cells !==` identity check: *did a
  persisted thing move*, which is what makes reading through a notebook's matches free and
  reaching a hidden one a write.

---

## Files, workspace and recents — `AUD-005`

**The surface is three packages' command ids plus everything a command never names.**
`filebrowser-extension` declares **38 ids**, `docmanager-extension` **17**, and `docmanager` —
the document manager's own package, not its extension — **1** (`docmanager:recents`). Produced,
never stored:

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package filebrowser-extension
python3 scripts/read_reference_commands.py --jupyterlab-package docmanager-extension
python3 scripts/read_reference_commands.py --jupyterlab-package docmanager
```

Two carried ids join them out of `fileeditor-extension`'s 31: `fileeditor:create-new` and
`fileeditor:create-new-markdown-file` (`src/commands.ts:60`, `:62`), which create files rather
than edit one; the rest of that package waits for the slices that own the file editor and the
chrome.

**The rest of the surface is settings and the machinery under the ids.** Eighteen settings under
`filebrowser-extension/schema/browser.json` — sorting (`sortNotebooksFirst`,
`sortFileNamesNaturally`), the three columns (`showLastModifiedColumn`, `showFileSizeColumn`,
`showDateCreatedColumn`), `showHiddenFiles`, the filter's four (`showFileFilter`,
`useFuzzyFilter`, `filterDirectories`, `clearFilterOnNavigation`), `breadcrumbs`, `showFullPath`,
`singleClickNavigation`, `navigateToCurrentDirectory`, `showFileCheckboxes`, and the uploader's
three (`allowFileUploads`, `autoOpenUploads`, `maxAutoOpenSizeMB`) — several surfaced by the
`filebrowser:toggle-*` ids above. Seven under `docmanager-extension/schema/plugin.json`:
`autosave`, `autosaveInterval`, `confirmClosingDocument`, `defaultViewers`,
`lastModifiedCheckMargin`, `maxNumberRecents`, `renameUntitledFileOnSave`. Produced:

```sh
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/filebrowser-extension/schema/browser.json'))['properties']))"
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/docmanager-extension/schema/plugin.json'))['properties']))"
```

The machinery the commands ride on: `Contents.fileChanged` and the open document following its
path (`docregistry/src/context.ts`), `SaveHandler` (`docmanager/src/savehandler.ts`), the
overwrite dialog on a 409 (`docmanager/src/dialogs.ts`), `RecentsManager`'s validation
(`docmanager/src/recents.ts`), the uploader (`filebrowser/src/upload.ts`), the breadcrumbs
(`filebrowser/src/crumbs.ts`). **And the presentation half is VS Code's** — a tree, not a
replaced listing: `contrib/files/browser/` (`fileCommands`, `fileActions`, `explorerService`,
`views/`), `workbench.action.openRecent` (`browser/actions/windowActions.ts`):

```sh
ls reference/research_repos/vscode/src/vs/workbench/contrib/files/browser/
grep -n "openRecent" reference/research_repos/vscode/src/vs/workbench/browser/actions/windowActions.ts
```

Under it all, the behaviour ours mirrors is the shipped rootfs's —
`jupyter_server/services/contents/filemanager.py` and `manager.py`: `newUntitled`'s names, the
409 on rename, the refused non-empty directory, `allow_hidden`.

**Ours** is `ui/explorer/` over `WorkspaceRepository` — `ExplorerViewModel`/`ExplorerScreen`/
`ExplorerBar`, `DiskWorkspaceRepository` and `WorkspaceListing` (directories first, natural
order, hidden left out) — the 409 → ask → overwrite split on rename, a permanent delete refusing
a non-empty folder, `WorkspaceChange`s an open document follows; plus recents
(`RecentDocumentsRepository` → `DiskRecentDocumentsRepository`, `RecordOpening`, `MAX_RECENTS`,
the section's fold on `ExplorerSettings`), `DocumentAutosave` over `EditorSettings`, and the
`DocumentSharer` an export leaves through. Carried from `AUD-001`, this slice's to decide: the
three `NotebookRepository` operations no production file names — `renameNotebook`,
`overwriteNotebook`, `deleteNotebook`. Produced:

```sh
python3 scripts/read_port_surface.py            # WorkspaceRepository 11, RecentDocumentsRepository 4,
                                                # ExplorerSettingsRepository 2, NotebookRepository 6,
                                                # DocumentSharer 1
python3 scripts/read_port_surface.py --unused   # the three carried operations
```

**Carried neighbours, restated not rejudged.** The running panel and `running-extension` are
`AUD-006`'s; `filemenu:*`, the tab strip and the rest of the chrome are `AUD-009`'s; export's
format question is `AUD-008`'s, though the `DocumentSharer` it leaves through is weighed here
against `filebrowser:download`. The partition rows are in the shared table under `AUD-002`'s
heading — the tree's navigation and view ids to `T2`, the file operations to `T3`, persistence
and recents to `T4`.

### The tree — verdicts

`T2`'s entries, read down both dispatches. Theirs: a `filebrowser:*` id →
`commands.execute` → `tracker.currentWidget` — a `FileBrowser` holding
`BreadCrumbs`, a `FilenameSearcher`, and a sortable-header `DirListing` — →
`FileBrowserModel.cd`/`refresh` over `services.contents`, its `Poll` ticking at
10 s `standby: 'when-hidden'` (`model.ts:26,97`), `model.restore`/
`listing.restore` holding cwd, sort, and column state in the state DB
(`model.ts:369`, `listing.ts:573`). Ours: `FilesBar` (back, New, Settings) over `Breadcrumb`
targets and a `BackHandler`, `ExplorerViewModel.goTo`/`open` →
`WorkspaceRepository` → `DiskWorkspaceRepository`/`WorkspaceListing` (hidden
filtered, `inWorkspaceOrder`) → `ExplorerUiState` → a `LazyColumn`; around it
`KeepTheListingFresh` polls at the same 10 s keyed `RESUMED`, `relistAfter`
re-lists after each own act, and `browseRequest`/`openDocumentPath` carry a
document's crumb-aim and the open-document mark in from `MainNavigation`. VS
Code's explorer is the presentation comparison — an `AsyncDataTree`
(`explorerViewer.ts`), not the flat listing JupyterLab's model also assumes.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `filebrowser:go-up` — `listing.goUp()` → `model.cd('..')`, a no-op at the root (`index.ts:1457`); `browser.json` binds Backspace twice | `BackHandler(enabled = state.canGoUp)` → `goTo(path.parentDirectory())`, and each crumb a target to an arbitrary ancestor | adapted | The climb is the same; the key is not — Backspace has no soft-keyboard shape, so Back ascends a directory before it leaves the screen (the `documentsearch:end` answer, AUD-004), and a crumb is go-up to any ancestor, not one. |
| `filebrowser:go-to-path` — `navigateToPath` cds to the directory, or to the file's parent + `selectItemByName`, then `open-directory` unless `dontShowBrowser` (`index.ts:1407-1456`) | `browseRequest` → `goToFolder`: `goTo`, then `sidePanel = FILES` at two panes or the `Explorer` entry forward at one; a file's row marked via `openDocumentPath` | adapted | Aim-the-browser-at-a-path exists only as the programmatic request — the open document's crumb is its sole caller (`Navigation.kt`); `selectItemByName`'s counterpart is the mark of the open document's own row. |
| `filebrowser:open-directory`, `filebrowser:activate` — `getBrowserForPath` dispatches by `driveName`, `activateById` reveals + activates the widget holding it (`index.ts:720`); `activate` is `activateBrowser`'s legacy alias kept for compatibility | the same `browseRequest` wiring | adapted | "The browser for this path" is always the one browser — `Explorer`'s NavKey docstring states it (descending is a replaced listing, not a back-stack push); reveal + activate is the rail destination or the forward-entry. |
| `filebrowser:edit-path` — `crumbs.enterEditMode()` swaps the trail for a `PathNavigator` text input (`index.ts:2020`, `crumbs.ts:847`) | — | absent | GAP-051. The crumbs are targets only; nothing types a path. |
| `filebrowser:open-path` — an `InputDialog` "Open Path" → `contents.get` → `goToPath`; a file is handed to the document manager's `open`, a miss errors "Could not find path" (`index.ts:1476-1530`); `jp-mainmenu-file`'s Open from Path… | — | absent | GAP-051. Nothing else accepts a typed path either — a deep directory is only reachable by tapping through every level. |
| `filebrowser:open` — each selected item: a directory `cd`s, a file delegates to the document manager's `open`, the `factory` arg carrying the Open-With choice (`index.ts:1557`) | the row tap — `open(entry)`: a directory `goTo`s, a file rides `onOpenFile` to the navigation that picks its editor | adapted | The delegation is identical — the browser never chooses the editor, and `factory` collapses to one answer per suffix (`ExplorerViewModel.open`'s docstring). The tap is `singleClickNavigation`'s on-state; the row below. |
| `filebrowser:refresh` — `model.refresh()` forces the poller's tick (`index.ts:1710`, `model.ts:245`); a toolbar button and the palette | the poll, and every own act re-listing; no on-demand control | absent | GAP-056. Both references ship the manual act beside the same poll — `RefreshExplorerAction` mounts in VS Code's explorer. A change nobody polled yet waits up to the tick and cannot be pulled forward. |
| `filebrowser:show-panel`, `filebrowser:toggle-main` — `labShell.activateById(browser.id)` (`index.ts:687`); `toggle-main` is the pre-rename alias, a label rather than a toggle (`index.ts:174,704`) | the rail's Files item at two panes, `onePaneDestinations` forward at one, `goToFolder`'s `sidePanel = FILES` | adapted | A rail destination answers a sidebar tab — the same verdict `toc:show-panel` took in AUD-004. |
| `filebrowser:hide-main` — `collapseLeft` when the browser's widget is current (`index.ts:763-777`) | re-tapping the selected rail item — `chosen` returns null and the side area collapses (`directive.forcedToOnePane`) | adapted | The collapse is the same act on the same rail item's second tap. |
| `filebrowser:share-main` — `copyShareableLink` copies `PageConfig.getUrl({treePath, toShare: true})` for the one selected item onto the clipboard (`index.ts:848-880`) | — | absent | GAP-055. A shareable *link* presupposes a server the recipient can reach — JupyterHub's shape. `DocumentSharer` hands the open *document* to other apps; a file row cannot reach the seam. |
| `filebrowser:select-all` — `listing.selectAll()` marks every row's path (`index.ts:1389`, `listing.ts:543`); Accel+A | — | absent | GAP-050. There is no selection to fill: a row answers a tap and a long-press of itself alone. The checkbox column, the context menu's multi-row copy/cut/delete, and `share-main`'s `selectedItems().length === 1` all presuppose it. |
| `filebrowser:toggle-last-modified` — `showLastModifiedColumn`, the one extra column default-on, sorted-on too | `relativeTime(entry.updatedAt)` under every row's name, always | adapted | The reference's own default-on column, stacked where a ~360 dp row has room rather than in a second column — `EntryRow`'s cite of the reference's own compact mode (`listing.ts:1061-1073`). |
| `filebrowser:toggle-date-created`, `filebrowser:toggle-file-size` — `showDateCreatedColumn`, `showFileSizeColumn`, both default-off `ViewFilesColumns` members | — | absent | GAP-053. `WorkspaceEntry` reads neither `size` nor `created`, so neither can be drawn or sorted on even if the columns surface existed. |
| `filebrowser:toggle-sort-notebooks-first` — `sortNotebooksFirst`, default false; on hoists notebooks under directories | directories first, names after; notebooks not hoisted (`inWorkspaceOrder`) | adapted | Fixed at the schema's own default — `inWorkspaceOrder`'s docstring states the order. |
| the sortable header — `DirListing.sortState`, a header click picking key + direction (`listing.ts:322,1585`), persisted by `listing.restore`; VS Code's `explorer.sortOrder` six modes + `sortOrderLexicographicOptions` + `sortOrderReverse` (`files.contribution.ts:520-562`) | one fixed order | absent | GAP-053. No id owns it; the GAP names the whole surface — modified/type/files-first/reverse — that a fixed directories-first order cannot express. |
| `filebrowser:toggle-single-click-navigation` — `singleClickNavigation`, default off (`browser.json`): a click selects, a double-click opens | a tap opens | adapted | The on-state worn permanently: a touch screen has no click-that-selects to spend, and the row's other acts sit on the long press. `ExplorerViewModel.open` now states it. |
| `filebrowser:toggle-hidden-files` — `showHiddenFiles`, and the command itself is `isVisible: allow_hidden_files` (`index.ts:1971`) — the setting cannot show what the server will not list | dotfiles never listed — `workspaceEntriesUnder` drops names starting with `.` — and `isValidFileName` refuses to create one | adapted | The only state the shipped backend serves: `allow_hidden` stays false in the Dockerfile and nothing else sets it — `WorkspaceListing`'s docstring cites it (`:25-27`). On this server the reference's toggle is itself invisible. |
| `filebrowser:toggle-file-checkboxes` — `showFileCheckboxes`, the `is_selected` column (`listing.ts:113-123`) | — | absent | GAP-050. The column is the selection made visible. |
| `filebrowser:toggle-file-filter` — `showFileFilter` mounts a `FilenameSearcher` (`ui-components/…/search.tsx:273`) narrowing the listing by substring, or fuzzy when `useFuzzyFilter`; `filterDirectories` and `clearFilterOnNavigation` scope it | — | absent | GAP-052, which takes the three filter settings with it. Ours' workspace search is a different act — it reads file *contents*; this narrows the names already listed. |
| `filebrowser:toggle-show-full-path`, `breadcrumbs` — `fullPath` draws the whole path on the home crumb; the ellipsis policy `minimumLeftItems:0`/`minimumRightItems:2`, the home/ellipsis toggles (`browser.json`) | labelled, separated segments that scroll sideways | adapted | All five knobs sit at their schema defaults; the scroll-instead-of-elide is `Breadcrumb`'s stated choice. |
| `sortFileNamesNaturally` — `doNaturalSort`, `Intl.Collator{numeric:true}` (`listing.ts:4037-4038`) | `compareNaturally` — digit runs as numbers, then `Collator` SECONDARY | adapted | Implemented, fixed at the schema's default true — `WorkspaceEntry.kt`'s docstring transcribes the collator. |
| `navigateToCurrentDirectory`, `filebrowser:toggle-navigate-to-current-directory` — on `currentChanged`, `cd` to the active document's folder when set (`index.ts:814-830`); the marking half runs regardless — `_onActivateRequested` selects the row only when it sits in the browsed directory (`listing.ts:2761`) | `openDocumentPath` marks the row only when the browsed directory holds it; the follow exists as the document's own crumb aiming the browser on tap | adapted | The mark is the reference's own (its cite sits on `ExplorerScreen`). The auto-follow is the schema's default **off**, while VS Code's `explorer.autoReveal` defaults **on** (`files.contribution.ts:447`) — ours splits the disagreement: mark like JupyterLab, follow on request, and the mark never scrolls the row into view where VS Code's reveal would. |
| `allowFileUploads`, `autoOpenUploads`, `maxAutoOpenSizeMB` — the trio gating `upload.ts` and the listing's drag-drop | — | absent | GAP-054. Nothing puts a file in: the VIEW intent hands only a display *name* to `createNotebook` — the content is never copied — and no picker exists. SAF/`OpenDocumentTree` is the repair's shape. |
| `model.restore`/`listing.restore` — cwd, sort state, and column sizes persist in the state DB across sessions (`model.ts:369`, `listing.ts:573`); the tracker restores the browser widget itself | every launch opens the root (`ExplorerViewModel.init`); `ExplorerSettings` persists only the recents fold | adapted | The resume question is already answered by Recents; a restored cwd would re-ask it. `ExplorerViewModel`'s docstring now says so. |
| `model.cd`'s miss — a failed `cd` walks back to the root (`model.ts:316-328`) | the failure leaves the view where it was and reports it; the banner persists while the directory stays missing | adapted | `goTo`'s docstring states the stay-put choice; the reference retreats to root instead. |
| VS Code's explorer tree — expandable, compressible (`compactFolders`), `fileNesting`, per-file decorations, collapse-all (`explorerViewer.ts`, `fileActions.ts`) | one directory at a time, contents replaced | adapted | Stated in `ExplorerScreen`'s and `goTo`'s docstrings: a tree's indentation spends the width the names need; collapse-all and nesting are tree-shaped and go with it. |
| VS Code refresh-on-visible — `onDidChangeBodyVisibility` → `explorerService.refresh()` (#126817, `explorerView.ts:390-392`) | `repeatOnLifecycle(RESUMED)` restarts the poll on return | present | The same compensation for missed file events — `KeepTheListingFresh`'s docstring names the reference's sibling mechanism. |

GAP-050 and GAP-055 are shared absences: T3's copy/cut/download rows and
`docmanager`'s download land on the same two holes, and their verdicts cite
these GAPs rather than naming new ones.

### File operations — verdicts

`T3`'s entries, read down both dispatches. Theirs: a `filebrowser:*` or
`docmanager:*` id → `commands.execute` → `tracker.currentWidget` /
`contextMenuWidget` → `FileBrowser`/`DirListing` acts (`_clipboard`,
`selectedItems`, `model.manager`) or `IDocumentManager` (`newUntitled`,
`openOrReveal`, `deleteFile` — `stopIfNeeded` first — `overwrite`,
`cloneWidget`) → `services.contents`; a 409 on rename reaches the shared
`IDocumentManagerDialogs.rename` → `shouldOverwrite`. Winlator:
`BaseFileManagerFragment`'s `Clipboard{files, cutMode}` plus a paste FAB,
`FileUtils.copy/delete` under an `isSymlink` guard, `clearReservedChars` on
rename. VS Code: `fileActions.ts` handlers on the explorer's `AsyncDataTree`
and — unlike both — the *system* clipboard.

Ours: `NewMenu` → `ExplorerViewModel.create` → `createFile`/`createFolder`/
`createNotebook`; a row's long press opens its own menu (`ExplorerScreen.kt:387`)
whose rename/delete reach `ExplorerViewModel.rename`/`delete` through the shared
`RenameFileDialog`/`DeleteFileDialog`; a `FileNameTakenException` becomes
`nameTaken` → `answerOverwrite` → `WorkspaceRepository.overwrite`; every act
announces on `WorkspaceChange` for open documents; the tab strip's long press
is `docmanager:rename`'s home (`MainAreaTabStrip.kt:309`); a Running row owns a
session's shutdown (`RunningSidePanel.kt:175`); `DocumentSharer` — the
leaving-seam — is reachable only from a notebook's export.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `filebrowser:create-new-directory`, `filebrowser:create-new-file` — `_createNew` → `newUntitled` → `selectItemByName(name, true)` → `rename()` on the landed row (`browser.ts:529-541`); `create-new-file` carries an `ext` per file type on the New submenu (`index.ts:604-607,1653-1690`) | `NewMenu` → `create` → `createFile`/`createFolder` → `untitled{ext}`/`Untitled Folder` counted past, the row brought into view | adapted | The server's names and the lands-then-visible are the same; the rename the reference then opens is not chained — `NewMenu`'s docstring states the choice, one way to name a thing, and the per-type submenu collapses to the one non-notebook type this app creates. |
| `filebrowser:create-new-markdown-file`, `fileeditor:create-new-markdown-file` — `createNewFile({ext:'md'})` (`index.ts:1692-1697`); the carried command creates *and opens* it (`commands.ts:1033-1040,1382-1400`) | — | absent | GAP-057. `createFile` takes any extension and `DocumentOpeners` opens a `.md` in the file editor, but no surface can make one. |
| `fileeditor:create-new` — `new-untitled` + `open` + `isUntitled` (`commands.ts:969-990,1382-1400`) | `NewItem.PYTHON_FILE` → `createFile(directory, "py")`; the landed row waits for a tap | adapted | The create half is `docmanager:new-untitled`'s own; the chained *open* is the row's tap here — the same lands-then-name-later the NewMenu docstring states. |
| `filebrowser:rename` — the row's inline edit node (`_doRename`, `listing.ts:2556`), `isVisible` only for a single selection (`index.ts:1737-1748`); a taken name is 409 → `shouldOverwrite` → `manager.overwrite` | row menu → `RenameFileDialog` → `rename`; `FileNameTakenException` → `nameTaken` → `OverwriteFileDialog` → `overwrite` | adapted | The bare-name rule and the ask-then-overwrite split are identical (`move`/`overwrite`). The ask is the shared `RenameFileDialog` rather than the listing's edit node — one dialog for the row, the tab, and Recents, as its docstring states. |
| `filebrowser:duplicate` — `manager.duplicate` per selected non-directory: `contents.copy(path, dirname)` (`listing.ts:519-527`, `manager.ts:372-375`) | — | absent | GAP-050. Iterates `selectedItems()` — and no `copy` op exists for a single row to ride even without selection. |
| `filebrowser:copy`, `filebrowser:cut`, `filebrowser:paste` — `_clipboard` of selected paths (`listing.ts:2528-2533`); cut-paste is `manager.rename` per item, copy-paste `manager.copy`, clipboard cleared after (`listing.ts:406-431`) | — | absent | GAP-050. There is no selection to put on a clipboard; the reference's own note on the seam — an in-app list, not the system clipboard — is what Winlator replicates with a different gesture. |
| `filebrowser:delete` — confirm → `manager.deleteFile` = `stopIfNeeded` + `contents.delete` + `deleteWidgets` on the file's contexts (`listing.ts:2537-2549`, `manager.ts:351-363`) | row menu → `DeleteFileDialog` → `delete` — permanent, refuses non-empty, unlinks a link; `Deleted` → the open document goes `gone` | absent | GAP-058. The file half is present in full — the ask, the permanence, the announced close. Absent is the first step: the file's sessions are never stopped. |
| `filebrowser:shutdown` — shuts each session whose `session.path` sits among the *selected* rows (`listing.ts:654-674`, `index.ts:1797`) | — | absent | GAP-050 — selection-bound in the reference. The capability exists one surface over (a Running row names the file and owns `shutdownSession`), but no act on a file row reaches it. |
| `filebrowser:download` — `model.download` per selected non-directory, an anchor click on `getDownloadUrl` (`listing.ts:559`, `index.ts:526-543`) | — | absent | GAP-055. No served URL exists to hand a browser; `DocumentSharer` is the seam this ask would ride and only a notebook's export reaches it. |
| `filebrowser:copy-download-link` — `getDownloadUrl(selected)` → `Clipboard.copyToSystem`, hidden unless exactly one row is selected (`index.ts:544-566`) | — | absent | GAP-055. Same presupposed server URL; a link to a server nobody else can reach is not the shape sharing takes on Android. |
| `filebrowser:copy-path` — the workspace-relative path (or `serverRoot`-joined under `copyAbsolutePath`) → system clipboard (`index.ts:1761-1790`) | — | absent | GAP-055, whose text already names it. The path is a value the crumbs already show; the missing half is only the copy act on the row. |
| `filebrowser:open-url` — prompt → `fetch` → `model.upload` → `docmanager`'s `open` (`index.ts:1088-1140`) | — | absent | GAP-054. A file *in* — the URL's content is fetched then uploaded into the workspace — and nothing puts a file in. |
| `filebrowser:open-browser-tab` — per selected item, a `single-document` tree URL or `docmanager`'s `open-browser-tab`'s download URL → `window.open` (`index.ts:976-1010`) | — | absent | GAP-055. Both halves presuppose a server URL a browser can open; none exists. |
| `docmanager:new-untitled` — `contents.newUntitled({path,type,ext})` — the typed create every surface delegates to (`index.tsx:775-822`, `manager.ts:426-430`) | `NewMenu` → `create` → `createFile`/`createFolder`/`createNotebook` | adapted | The same create under the same server names. The notebook arm goes through `NotebookRepository` because an `.ipynb` is nbformat, not an empty file — `create`'s docstring states the split. |
| `docmanager:open` — `contents.get` then `openOrReveal` (`index.tsx:824-847`) | a tap → `openDocument` — `OpenDocuments.at` resolves the live path to the `openedAs` the key keeps — → `openNotebook`/`openFile` → `openInMainArea` | **defect** | found by the walk and fixed red-first: the reveal was key-equality only and a `NavKey` keeps the path it was opened under, so a document renamed beside its open editor re-opened as a *second* entry — two ViewModels, two autosaves on one file (`fbd21a99`, `NotebookBackStackTest.openingARenamedDocumentRevealsItInsteadOfOpeningItAgain`). The reference matches by the context's *current* path via `_contextsForPath` (`manager.ts:481-503`). |
| `docmanager:rename` — `dialogs.rename(context)`, bound on `document-title` context menus (`index.tsx:1428-1450`) | tab long press → the same `RenameFileDialog` → `rename`; the document adopts through `WorkspaceChange.Renamed` | present | `MainAreaTabStrip.kt:309` states the long press is the `document-title` menu collapsed to its one item. |
| `docmanager:delete`, `docmanager:delete-file` — ask → `deleteFile` = `stopIfNeeded` + `contents.delete` + `deleteWidgets` (`index.tsx:749-773,1473-1506`) | the ask+delete lives on the explorer row, never on the document's own tab; `Deleted` closes the document | absent | GAP-059 for the surface, GAP-058 for `stopIfNeeded`: the open document hears its file go (the `deleteWidgets` half) while its session survives. |
| `docmanager:duplicate` — `manager.duplicate` on the context's path (`index.tsx:1452-1471`) | — | absent | GAP-059 — no copy act on the document's surface, and GAP-050 — no `copy` op for one to delegate to. |
| `docmanager:clone` — `cloneWidget` + `widgetOpener.open`, `split-right` the default (`index.tsx:1397-1425`) | — | absent | GAP-060. `openInMainArea`'s one-entry-per-path rule is stated, so the second view is refused by the stack rather than merely unoffered. |
| `docmanager:download` — `context.download()` on the current document (`index.tsx:504-527`) | — | absent | GAP-055. The open notebook's *export* does leave through `DocumentSharer` — a conversion to another format, not this file's own bytes. |
| `docmanager:open-browser-tab` — `getDownloadUrl(path)` → `window.open` (`index.tsx:563-600`) | — | absent | GAP-055. Same presupposed server URL. |
| `docmanager:show-in-file-browser` — `open-directory` + `go-to-path` (`index.tsx:1508-1527`) | the document's crumb → `onGoToFolder` → `browseRequest` → `goTo` + the row's mark | adapted | T2's verdict on the same machinery; the command exists as the crumb's aim rather than a menu item — `EditorScreen.onGoToFolder`'s docstring carries the wiring. |
| Winlator's file clipboard — `Clipboard{files, cutMode}` + a paste FAB; `pasteFiles` checks every target first and *toasts* on a taken name — no overwrite ask (`BaseFileManagerFragment:154-178`) | — | absent | GAP-050 — the same hole as JupyterLab's clipboard, plus a second answer for the collision worth recording: refuse with a toast where the reference asks to overwrite. |
| Winlator's delete — `FileUtils.delete` recurses a directory but checks `isSymlink` first (`FileUtils:114-119`) | `delete` refuses a non-empty folder and unlinks a link rather than following it | adapted | The refuse is documented on the port: Winlator's file manager deletes a tree recursively and the server sharing this workspace does not. The link half is shared. |
| Winlator's rename — `clearReservedChars` strips the bad characters | `isValidFileName` refuses them | adapted | Strip-vs-refuse is stated in `WorkspaceRepositoryContract.anInvalidNameIsRefused`: a stripped name hands back a file the user never named. |
| VS Code `explorer.newFile`/`explorer.newFolder` — names the file **inline in the tree** at create (`fileActions.ts`) | server-named `untitled`/`Untitled Folder`, landed and scrolled to, renamed later from the row's menu | adapted | A third option JupyterLab also declines — and `NewMenu`'s docstring states the lands-then-rename-later choice. |
| VS Code copy/cut/paste — the **system** clipboard, so a file copied in another app can paste *in* (`fileActions.ts`, `explorerService.ts`) | — | absent | GAP-050 for the clipboard; the system half doubles as an import channel, GAP-054's hole from the other side. |
| VS Code `explorer.download`, `explorer.upload` — remote-aware file in/out (`fileActions.ts`) | — | absent | GAP-055 and GAP-054 respectively. |
| VS Code `copyFilePath`/`copyRelativeFilePath`/`copyPathOfActiveFile` | — | absent | GAP-055. |
| VS Code `revealInExplorer`/`revealResourceInExplorer` — the tree reveals and selects the file (`fileActions.ts`) | the document's crumb aims the browser (`browseRequest` → `goTo` + the mark) | adapted | Same verdict as `docmanager:show-in-file-browser` — the aim exists as the crumb's request. |
| VS Code `openToSide` — a second editor group over one file (`fileActions.ts`) | — | absent | GAP-060. |
| VS Code `moveFileToTrash`/`deleteFile` — platform trash unless `files.simpleDialog.enable`/Shift or `useTrash:false` (`fileActions.ts`) | permanent delete; a non-empty folder is refused | adapted | Android grants an app no trash over its own files — the port's `delete` docstring states it, and the reference's own trashless branch is what a server-side `delete` already is. |

**The walk's own findings.** Two came out of tracing rather than comparing.
`DiskWorkspaceRepository.overwrite` used to emit only its `Renamed` — while the
reference's `DocumentManager.overwrite` deletes the target through `deleteFile`
(`manager.ts:514-528`), whose `deleteWidgets` is what a widget on the target
hears — so a document open on the *overwritten* path was never told its file
was gone and its next save wrote over the winner. **defect → fixed** in
`b492da53`, red-first through
`WorkspaceRepositoryContract.overwritingTellsTheTargetIsGoneBeforeItMoves` and
`FileEditorViewModelTest.aFileOverwrittenUnderTheEditorIsNotWrittenBack`;
`overwrite` now emits `Deleted(moved)` before `Renamed`, matching the disk's
own delete-then-move order.

And the three carried `NotebookRepository` file ops are removed rather than
kept — no caller, no `WorkspaceChange` to announce on, and a `renameNotebook`
that rewrote content where the reference moves the file. ADR-0070;
`read_port_surface.py --unused` now reports nothing.

### Persistence and recents — verdicts

`T4`'s entries, read down both dispatches. Theirs: a `docmanager:*` id →
`commands.execute` → the current document's `context` — `save`, `saveAs`,
`revert`, `listCheckpoints`/`restoreCheckpoint` — or the `settingRegistry`
(`toggle-autosave` is a settings write wearing a command's shape); every save
runs `_maybeSave`'s hash-or-`last_modified` check first and ends in
`createCheckpoint`. Recents are `RecentsManager` over `IStateDB` under the
`docmanager:recents` key — one entry recorded per activation, one per close —
shown by `recentmenu:*` and the running panel's Recently Closed. VS Code:
`fileCommands.ts` handlers for save/saveAll/saveAs/revert, `files.autoSave`'s
four-valued enum, and `workbench.action.openRecent`'s quick pick.

Ours: `DocumentAutosave` — the debounce and the interval poll over
`EditorSettings.autosaveEnabled`/`autosaveIntervalSeconds` — plus the close
gates (`SaveBeforeCloseGate`, `MainAreaCloseGate`) whose `saveNow` is the only
on-demand write in the app; `RecordOpening` → `RecentDocumentsRepository` →
`DiskRecentDocumentsRepository` (`stateDir`, `recent-documents.json`), rendered
by `RecentsSection` at the explorer root with `RecentsActionsMenu`'s clear.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `docmanager:save`, `docmanager:save-all` — `context.save()` then `createCheckpoint()`, on Accel S, the File menu, the palette and the notebook toolbar (`index.tsx:1105-1235`); `save-all` sweeps every writable context (`:1237-1268`); `renameUntitledFileOnSave` prompts on an untitled first save (`:1155-1190`) | `DocumentAutosave`'s debounce and poll write on their own timers; the only on-demand write is the close gate's flush (`SaveBeforeCloseGate` → `saveNow`) | **absent** | `GAP-061`. The write exists — `OpenDocuments.saveNow` — and the act does not; with `autosaveEnabled` off a dirty document writes only when it closes. |
| `docmanager:save-as` — a path prompt, `_maybeOverWrite` on a taken one, then `fileChanged` closes the old context and opens the new (`context.ts:295-333`, `index.tsx:1270-1318`) | — | **absent** | `GAP-063`. `rename` moves a file; nothing writes the document's bytes under a second name. |
| `docmanager:toggle-autosave` — `isToggled` reads `docManager.autosave`, the act writes the setting (`index.tsx:1330-1349`), offered as a palette item | the Settings row → `EditorSettings.autosaveEnabled` (`SettingsScreen.kt:138-139`) | **adapted** | A settings row rather than a palette command — the phone has no palette, and the row's own checked state is `isToggled`. `SettingsScreen.kt:320` names the command the row is. |
| `docmanager:restore-checkpoint` — `listCheckpoints` → a picker → `restoreCheckpoint` (`index.tsx:1020-1084`); the points are made by `save()` itself and `_maybeCheckpoint` at first open (`context.ts:528-540,841-860`) | — | **absent** | `GAP-062`. The shipped `filecheckpoints.py` serves the API, but no port names it and nothing ever creates one — so the picker would have nothing to list. |
| `docmanager:reload` — `context.revert()` re-reads the file and reinitialises the model, asked first when dirty (`index.tsx:968-1018`, `context.ts:676`) | — | **absent** | `GAP-064`. A file is read at open and never again; `changes` announces only what this app itself moved. |
| `docmanager:recents` — the `IStateDB` key and its machinery: `{opened, closed}` lists, `addRecent`'s dedup-then-unshift, Contents-API validation on load, a 500 ms `Debouncer` on the write (`recents.ts:17-288`) | `RecentDocumentsRepository` → `DiskRecentDocumentsRepository` — `recent-documents.json` in `stateDir`, dedup+cap in `withMostRecent`, the same validation at `loadValidated` and on explorer refresh (`ExplorerScreen.kt:162`) | **adapted** | Client state in a file outside the workspace rather than in `IStateDB`, and written eagerly rather than debounced — the repository's docstring states both choices. The `closed` list the schema stores is `GAP-066`. |
| `docmanager:clear-recents` — empties `opened` and `closed`, enabled only while either is non-empty (`docmanager-extension/src/recents.ts:66-81`) | the Recents section's overflow → `RecentsViewModel.clear` → `clear()`; the section itself is gone while the list is empty (`RecentsSection.kt:53`) | **present** | Same act, same label — `RecentsActionsMenu.kt:57` cites it — and the same reachability: two lists there, one here is `GAP-066`'s difference, not this command's. |
| `autosave` (`plugin.json:113-118`, default true — gates `DocumentManager.autosave`, `manager.ts:96-126`) | `EditorSettings.autosaveEnabled`, default true, gating both halves of `DocumentAutosave` | **present** | The flag, at the schema's own default, over the mechanism it names — the field's docstring names the setting and the command. |
| `autosaveInterval` (`plugin.json:119-124`, default 120, unbounded) | `EditorSettings.autosaveIntervalSeconds` — the same 120, clamped 5–600 by `AutosaveInterval` | **adapted** | The bound is written (`EditorSettings.kt:204-213`): a touch stepper needs edges, and under 5 s the poll is a chore the 500 ms debounce does better. The fixed interval also declines `SaveHandler`'s `10 * duration` growth and `isConnectedCallback` pause — `DocumentAutosave`'s docstring carries that reason. |
| `confirmClosingDocument` (`plugin.json:125-130`, default false — on asks for a clean close too, `widgetmanager.ts:478-487`) | not carried — a dirty close always asks, a clean one never does | **adapted** | Pinned at the schema's own default; the reason is written on `MainAreaCloseGate`'s docstring. |
| `defaultViewers` (`plugin.json:137-146` — a file-type → widget-factory override, `index.tsx:297-315`) | `DocumentOpeners`' suffix split — one factory per type | **adapted** | No second factory exists for any type, so an override would have nothing to point at — `DocumentOpeners`' docstring says so. |
| `lastModifiedCheckMargin` (`plugin.json:131-136`, default 500 ms — bounds `_maybeSave`'s disk-vs-client comparison, `context.ts:741-790`) | — | **absent** | `GAP-065`. The knob parameterises a check that does not exist: `writeText`/`saveNotebook` write blind over whatever the kernel or another client left. |
| `maxNumberRecents` (`plugin.json:153-158`, default 10 — `maximalRecentsLength` truncates both lists, `recents.ts:63-77`) | `MAX_RECENTS = 10`, a domain constant | **adapted** | The schema's own default, not carried as a setting — the constant's docstring states the decline. |
| `renameUntitledFileOnSave` (`plugin.json:147-152`, default true — the first-save rename prompt, `index.tsx:1155-1190`) | — | **absent** | `GAP-061`. Its premise — a manual save — is the command row above's hole. |
| the save machinery — `SaveHandler`'s debounce, interval re-arm, `10 * duration` growth, `isConnectedCallback` pause (`savehandler.ts:130-137`, `index.tsx:216-221`) | `DocumentAutosave` — 500 ms debounce plus a dirt-gated poll at the configured interval | **adapted** | Debounce and poll are the same; the growth and the pause answer a write that crosses a network, and this one's never leaves the disk — the class docstring states it. |
| the `closed` list — `addRecent(path, 'closed')` on `close-request`, read by `recentmenu:reopen-last` and the running panel (`widgetmanager.ts:336-403,428-441`) | — | **absent** | `GAP-066`. Only opens are recorded (`RecordOpening.kt`); a closed document is still-opened or forgotten. |
| per-entry removal — `removeRecent` (`recents.ts:124-129`), Forget on the running panel's rows (`running-extension/src/recents.ts:71-75`) | clear-all only (`RecentsActionsMenu`) | **absent** | `GAP-067`. `RecentRow`'s own no-per-row-act rationale predates weighing Forget. |
| recents surface — `recentmenu:open-recent` items in the File menu and the running panel's two sections (`mainmenu-extension/src/recents.ts`, `running-extension/src/recents.ts`) | `RecentsSection` at the explorer root — directories first, tap to open or navigate | **adapted** | A pinned section rather than a menu — the phone has no File menu and the explorer root is where a next-open starts; `RecentsSection`'s docstring and `inRecentsOrder` carry the shape. |
| VS Code `workbench.action.files.save`, `saveAll`, `saveAs` (`fileCommands.ts:497-540`) | — | **absent** | `GAP-061` and `GAP-063` — the same holes the `docmanager:` family left. |
| VS Code `workbench.action.files.revert` (`fileCommands.ts:565`) | — | **absent** | `GAP-064`. |
| VS Code `workbench.action.openRecent` — a quick pick over MRU'd workspaces and files, a remove button per row (`windowActions.ts:48-56,290-310`) | `RecentsSection` — always-on-screen at the explorer root | **adapted** | The pick's job — reopen something recent without hunting — is a root-level section; the per-row remove button the pick mounts is `GAP-067`. |
| VS Code `files.autoSave` — `off`/`afterDelay`/`onFocusChange`/`onWindowChange` (`files.contribution.ts:261-268`) | `autosaveEnabled` + `autosaveIntervalSeconds` — an on/off and a delay | **adapted** | The four-valued trigger collapses: `afterDelay` is this app everywhere (a phone's focus is one window's), and `onFocusChange`/`onWindowChange` have no surface to hang from — `EditorSettings`' docstring carries the JupyterLab half of the same collapse. |

### The walk

Four paths through the one workspace. **A tap to an open document re-keyed:** `EntryRow`'s tap →
`ExplorerViewModel.open` — a directory `goTo`s, a file is handed up `onOpenFile` →
`rememberDocumentOpener` → `MutableList<NavKey>.openDocument` — `OpenDocuments.at` resolving the
asked path to the `openedAs` the stack still knows, the live-path reveal `openOrReveal` gets from
`_contextsForPath` — then the suffix split → `openNotebook`/`openFile` → `openInMainArea`'s
reveal-or-push → the new entry's ViewModel `init`: `openDocuments.opened` *before* the read, then
the `workspaceRepository.changes` collector. The change side: `DiskWorkspaceRepository`'s
`MutableSharedFlow` — no replay, no buffer, `emit` suspending until every collector has taken the
event. A `Renamed` → `pathAfterRename` — exact match or whole-segment prefix, so a renamed folder
moves every open descendant — → `adopt` → the live `notebookId`/`path` var → `LaunchedEffect` →
`onNotebookMoved`/`onFileMoved` → `openDocumentPaths[key]` → `mainAreaTabTitle` renames the tab and
`openDocumentPath` re-marks the row. A `Deleted` matching exactly → `gone`/`stopWriting` → `onBack`
*outside* the save gate — a deleted file has nothing a save could put back.
**A rename to 409 to overwrite:** the row's menu → `RenameFileDialog` — invalid or unchanged
refused before the port is asked — → `rename` → `move` (`renamingPath` armed) →
`DiskWorkspaceRepository.rename`: `idBeside` joins the name onto the parent, same-name no-ops,
`isValidFileName` refuses, a missing source refuses, a taken target throws
`FileNameTakenException` before `renameTo` has touched anything → `_changes.emit(Renamed)`.
`move.onFailure` splits: taken → `nameTaken` → `OverwriteFileDialog` → `answerOverwrite` →
`overwrite` — the source moved aside to a UUID sibling, the target deleted, the aside moved in,
the aside *restored* when the delete failed — → `Deleted(moved)` then `Renamed(from→moved)` in
the disk's own order → the same adoption path a plain rename takes; every other failure lands on
the `error` line. **An open to recents to revalidate:** `recordOpening` on `applicationScope` —
the write must outlive the tap that caused it — → `recentsForOpening`: the document and its
parent directory, the root excluded → `record` per entry, a path the workspace cannot address
refused → `withMostRecent` dedup-unshift-cap-`MAX_RECENTS` → a whole-JSON write per record →
`MutableStateFlow` → `RecentsViewModel` → `shownWhileBrowsing` — the section exists only at the
root — → `inRecentsOrder` directories-first → `RecentsSection`; a recent's tap re-enters the tap
path or `goTo`. `loadValidated` at construction — parse-or-empty, then an `lstat` per stored row
— and `revalidate` on every `KeepTheListingFresh` tick, the same filter again, writing only when
a row dropped. **An edit to debounce to poll to write:** `editCell`/`edit` →
`DocumentAutosave.schedule` — refused while `autosaveEnabled` is off or the document is
`discarded` — → `editArrived = true` + the 500 ms `pending` job restarted → `launchWrite` as a
*separate* job, so a re-schedule cannot cancel a write mid-flight → `attemptWrite` swallowing
everything but cancellation → `persist`: `gone`/`loaded` guards, then the notebook arm —
`writeMutex`, a snapshot of `written`, `saveNotebook`, `adoptWritten` clearing `dirty` only while
the snapshot still covers, `saveFailure` on failure — or the file arm — `writeText`, `dirty`
cleared, `error` on failure. The poll: `editorSettings.collectLatest` → `editArrived.first{it}` —
a clean document parks the poll rather than spinning — → `delay(interval)` → `isDirty()` → write.
The way out: `SaveBeforeCloseGate`/`MainAreaCloseGate` → `saveNow` → `flush` → `onSaved` only
when nothing is left dirty — a rejected save never closes, the reference's own rule —
`discardChanges` dropping the owed work, `onCleared` flushing what is owed on `applicationScope`.
What it found, beyond the rows above:

- **Growth is a relist per act and per tick, and a revalidation per refresh — all bounded by
  construction.** Every create/rename/delete ends in a `goTo(browsing)` — one `listFiles`, a stat
  per entry, `inWorkspaceOrder` — serialized by `listing?.cancel()` so at most one listing is in
  flight; `KeepTheListingFresh` adds one per `RESUMED` spell. That is the reference's own shape —
  `_refreshOrRepopulate` after every mutation plus the 10 s poll — and the same re-read
  `relistAfter`'s docstring argues for over patching rows. Recents cost ≤`MAX_RECENTS` `lstat`s a
  tick and write only when a row dropped; `record` writes the whole JSON per entry — two writes
  for a document opened from a folder — bounded at ten rows, the debounce declined where the
  repository's docstring states it. `emit`'s suspend-until-collected makes a rename's cost the
  move plus every open document's synchronous adoption — paid by the renamer, and the ordering
  the tab strip leans on. The one growth the walk did not excuse: `loadValidated` and the four
  `Disk*SettingsRepository` twins read their state files inside `AppContainer` construction, on
  the main thread, before the first frame — `GAP-070`.
- **Branching is small and each arm belongs to the layer that owns it.** `open` splits directory
  from file, `openDocument` splits the suffix, `move.onFailure` splits the one answerable refusal
  (`FileNameTakenException` → the ask) from every other (→ the banner). `rename`'s own refusals
  run in order — same name, invalid name, missing source, taken target — before `renameTo` runs;
  `overwrite` carries a restore-aside arm so a failed delete puts the source back rather than
  leaving the name's owner aside. `pathAfterRename` is exact-match-or-whole-segment-prefix — a
  folder takes its open descendants with it — while `Deleted` is exact-match only, sufficient
  *because* the port refuses a non-empty delete: the refusal is load-bearing for the listener, a
  coupling worth naming. `DocumentAutosave` splits debounce/poll/flush, and `persist` guards
  `gone` and `loaded` before it writes, so a mid-write delete cannot resurrect a file.
- **Failure is answered at the surface it belongs to — and the walk found three places it was
  not.** A refused rename asks (taken) or banners (other); a refused non-empty delete and a failed
  create banner; a failed listing leaves the browser where it stood; a cancelled listing says
  nothing — both pinned. A failed write leaves `dirty` set with `saveFailure`/`error` carrying the
  cause, and the close gate keeps the document open — `context.save()` rejecting keeps the widget
  open, the same rule. What another *process* does to the workspace arrives nowhere: `changes` is
  in-process and non-replay, so a cell's `os.remove` or a second client is invisible to an open
  document until its own write trips on it — `GAP-064`'s missing reload and `GAP-065`'s blind
  save are where the ledger already charges that. The defects the walk owes the ledger, all fixed
  red-first: **`docmanager:open` revealed by key equality, and a key keeps the path it opened
  under** — rename a file beside its open document and the next tap pushed a second editor, two
  autosaves on one file — `fbd21a99`,
  `NotebookBackStackTest.openingARenamedDocumentRevealsItInsteadOfOpeningItAgain` (the row above
  is re-verdicted `**defect**`); **an act landing mid-navigation re-listed the stale path** —
  `relistAfter`/`move` read `uiState.path` where `refresh` already reads `browsing`, cancelling the
  pending `goTo` — `f631fea2`, `aRename…`/`aDeleteFinishedMidNavigationRelistsWhereWeAreGoing`;
  **a landing listing retracted the overwrite question** — `listingOf`'s fresh state dropped
  `nameTaken`, so a poll tick answered the ask nobody answered — `068d08c9`,
  `aPollDoesNotRetractTheOverwriteQuestion`. One more failure found and not repaired: a rename or
  overwrite refused on the *tab strip* lands nowhere at all — `GAP-069`.
- **Coupling is the walk's real yield — one channel, one direction, and who never hears.**
  `WorkspaceRepository.changes` is the only channel every tree act announces on, and
  `emit`-suspends-until-collected is the ordering `TabbedDocumentPane`'s `openDocumentPaths` write
  leans on — the rename returns only after the document adopted it. Documents adopt through
  `pathAfterRename` and report their live path sideways into `openDocumentPaths`; the key keeps
  `openedAs` and `documentIn`/`openedUnder` find a document by it even mid-composition-gap — the
  two-key scheme `MainAreaTabs` documents, joined on the open side by `openDocument`'s `at`
  lookup. Recents deliberately do not follow a move — the reference's rule
  (`RecentsManager.validateRecents` drops what no longer resolves) and ours is the same
  `stillThere` filter at `loadValidated`/`revalidate`. The explorer itself never subscribes — the
  poll is its freshness, `GAP-056`'s shape. `changes` carries `Renamed`/`Deleted` and no `new` or
  `save` type because no listener needs one: the only consumer is a document hearing about itself.
  And the coupling the reference has that ours does not: `_onFileChanged` patches the session's
  own record — `sessionContext.session?.setPath(newPath)`, `context.ts:551` — while
  `KernelSessions` has no path op, so a Running row keeps the dead path and opens it — `GAP-068`.

---

## Console, sessions and the running panel — `AUD-006`

**The surface is two packages' command ids, nine menu delegates, and everything a command never
names.** `console-extension` declares **21 ids** — nineteen `console:*` plus the console's own
completer pair (`completer:invoke-console`, `completer:select-console`, declared there rather
than in `completer`'s own package) — and `running-extension` **7**. Nine are carried out of
`mainmenu-extension`'s 41: the seven `kernelmenu:*` semantic delegates — each a `SemanticCommand`
executing whatever the current widget registered for the act
(`packages/mainmenu/src/kernel.ts`), so they are this slice's acts wearing the menu — plus
`filemenu:create-console` and `filemenu:close-and-cleanup`, the File menu's entries into console
creation and close-and-shutdown. `kernelmenu:open` stays with the chrome slice: it opens the menu,
not the act. Fourteen `notebook:*` ids were already this slice's: nine kernel-lifecycle, three
console-creation, two history-walk. Produced, never stored:

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package console-extension
python3 scripts/read_reference_commands.py --jupyterlab-package running-extension
python3 scripts/read_reference_commands.py --jupyterlab-package mainmenu-extension
```

**The rest of the surface is settings and the machinery under the ids.** Nine settings under
`console-extension/schema/tracker.json` — `clearCellsOnExecute`, `clearCodeContentOnExecute`,
`hideCodeInput`, `interactionMode`, `promptCellConfig`, `promptCellPosition`,
`showAllKernelActivity`, `showBanner`, `toolbar` — and its four shortcuts: `Shift Enter` runs
forced in both modes, `Enter` runs unforced and `Accel Enter` inserts the linebreak in terminal
mode, while notebook-mode Enter asks `requestIsComplete` whether to run or break — the split our
prompt's Run button and Enter-as-newline stand in for. The
kernel-lifecycle settings live in `apputils-extension/schema/`: `sessionDialogs.json`'s
`skipKernelRestartDialog` — the "don't ask again" half of the restart confirm — and
`kernels-settings.json`'s `commsOverSubshells`, `kernelInfoTimeout`, `showStatusBarItem`.
Produced:

```sh
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/console-extension/schema/tracker.json'))['properties']))"
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/apputils-extension/schema/sessionDialogs.json'))['properties']))"
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/apputils-extension/schema/kernels-settings.json'))['properties']))"
```

The machinery the commands ride on: `CodeConsole`/`ConsolePanel` (`console/src/widget.ts`,
`panel.ts`), `ConsoleHistory` (`console/src/history.ts` — what `NotebookHistory` was transcribed
from, so this slice reads the class ours returns to its own surface), the `ForeignHandler` and
`ConsoleCellExecutor` that put a notebook's run inside a console's transcript
(`console/src/foreign.ts`, `cellexecutor.ts` and their `console-extension` twins), the running
panel's `Section`/`RunningSessions`/`SearchableSessions` and its per-manager `refreshRunning()`
(`running/src/index.tsx`), the three contributed sections in
`running-extension/src/{kernels,opentabs,recents}` — `kernels.tsx` the kernel-grouped list, its
`Throttler` pacing the kernels and sessions managers — `SessionContext`
(`apputils/src/sessioncontext.tsx` — the object every document's kernel goes through, select and
restart dialogs included), `apputils/src/{kernelstatuses,runningSessions}.tsx` (the statusbar's
"N Terminals, M Kernel sessions"), and `services/src/{session,kernel,terminal}/` — the managers
the panel's `running()` reads. **The console's second home is vscode-jupyter's interactive window** —
`src/interactive-window/` (`InteractiveWindowController.ts`, `commands/`, `editor-integration/`)
behind `jupyter.createnewinteractive`, `jupyter.execSelectionInteractive`,
`jupyter.runFileInteractive`, the `jupyter.interactive.*` family and the `jupyter.*kernel*`
commands, which keep Restart on the toolbar — the `GAP-009` reference row:

```sh
grep -n '"command": "jupyter\.' reference/research_repos/vscode-jupyter/package.json
ls reference/research_repos/vscode-jupyter/src/interactive-window/
```

Under it all, the behaviour ours mirrors is the shipped rootfs's —
`jupyter_server/services/sessions/{handlers,sessionmanager}.py`,
`services/kernels/{handlers,kernelmanager}.py`, `services/kernelspecs/handlers.py`,
`jupyter_server_terminals/{handlers,terminalmanager}.py` and `terminado/websocket.py`:
`api/sessions` POST/PATCH/GET/DELETE, interrupt and restart on `api/kernels`, `api/terminals`,
`api/kernelspecs`.

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/sessions/sessionmanager.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/sessions/handlers.py
```

**Ours** is `ui/console/` — `ConsoleViewModel` (submit-disabled-while-running, `clear`,
`interrupt`, the `NotebookHistory` walk on buttons for `edgeRequested`), `ConsoleScreen`,
`ConsoleInputRow`, `ConsoleTranscriptView`, `ConsoleActionsMenu` — over a `NotebookKernelSession`
`NotebookKernel.attachConsole` pre-binds to the notebook's own, or `openStandaloneConsole`/
`LauncherViewModel.mintConsole` mints one standalone;
the session object `NotebookKernelSession` (`SessionContext`'s shape: preference, pending name,
`disposed`/`kernelChanged` landings as `endIfMatches`/`rebindIfMatches`, `reopeningIfStale`'s
reconnect the reference has no equivalent for); the REST half `KernelLifecycle` +
`RealKernelGateway`/`KernelSockets` (`AUD-001`'s wire); the lifecycle UI `KernelMenu`,
`RestartDialog`, `NotebookKernelPicker`, `kernelStatus`/`EditorUiState.kernelRestarting`; and the
panel `RunningSidePanel`/`RunningPanelViewModel` over `KernelSessions` (`running`, `shutdown`,
`ended`, `rebound`) and `TerminalGateway` (`running`, `close`). Produced:

```sh
python3 scripts/read_port_surface.py            # KernelGateway 9, KernelSessions 4, KernelSpecs 1,
                                                # TerminalGateway 6, KernelDebugger 10
python3 scripts/read_port_surface.py --unused   # empty since AUD-005's ADR-0070
```

**Carried in, recorded not rejudged.** `is_complete_request`'s only two callers are the console's
(`console/src/widget.ts:979` decides whether a submitted block is complete,
`notebook-extension/src/index.ts:1962` inside `run-in-console`), so `GAP-012` — recorded `absent`
on the wire in `AUD-001` — is this slice's to weigh on the surface that would send it. The three
gaps the backlog row names are verdicted here, not repaired: `GAP-009` (no Restart on the
Console, cross-holder staleness), `GAP-010` (no server-generation signal reaches the editor —
`BaseSpaceServer` counts it for `PerRunningServer`'s cache and no port carries it further),
`GAP-011` (the running panel's kernel half, written
before `KernelSessions.running()` and the panel's Sessions section existed). The terminal pane's
own commands (`terminal:*`) wait for `AUD-007` — the panel's Terminals section is
`running-extension`'s own code, and the pane's `copy`/`paste`/`set-theme`/font pair are the
terminal *view's*; the menu shells and the rest of the chrome are `AUD-009`'s; the completer's
machinery is `AUD-003`'s, examined — the two `completer:*-console` ids are read here only as far
as the console's surface offers them. The partition rows are in the shared table under `AUD-002`'s
heading — the console's ids to `T2`, sessions and kernel lifecycle to `T3`, the running panel to
`T4`, and the walk is `T5`.

### The console — verdicts

`T2`'s entries, read down both dispatches. Theirs: a `console:*` or `completer:*-console` id →
`commands.execute` → `getCurrent`/`contextMenuConsole` on `IConsoleTracker` → `CodeConsole`/
`ConsolePanel` (`execute(force)`, `insertLinebreak`, `replaceSelection`, `clear`, `inject`,
`setConfig`, the prompt editor's own undo/redo) or its `sessionContext` (`shutdown`,
`sessionDialogs.restart`/`selectKernel`, `setName`, `session?.kernel`); the three
`notebook:*-console` ids mint or feed one through `sessionContextDialogs`/`ForeignHandler`; and
`filemenu:create-console` is `console:create` wearing the File menu. VS Code's second home is the
Interactive Window, which keeps Restart on the primary toolbar — `GAP-009`'s own row.

Ours: `ConsoleViewModel` (`submit`, `clear`, `interrupt`, the `NotebookHistory` walk) under
`ConsoleActionsMenu` and `ConsoleInputRow`'s Run button, over a `NotebookKernelSession` either
`attachConsole`ed onto the notebook's own or `mintConsole`ed standalone, reached through
`KernelMenu`'s "New Console for Notebook", the launcher's card, or `openRunningSession`'s
reveal — all under the one `consoleSession` holder `NotebookBackStack`'s two openers sweep for.

Five real commands ride the surface that the measure cannot see: console:prompt-to-top, -bottom,
-left, -right (`index.ts:1360-1427`) and console:toggle-show-all-kernel-activity (`foreign.ts`)
are literal `addCommand` registrations with no `CommandIDs` export for `COMMAND_ID` to count, so
no slice owns them and they are named here — un-backticked, so the missing-check does not report
them owned — rather than verdicted: the four move the prompt between the console's edges, and the
fifth gates whether *every* kernel's activity lands in the transcript, which presupposes the
foreign-execution surface `GAP-073` records missing; ours' prompt is docked bottom with nowhere
second to go.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `console:run-forced` — `console.execute(true)`, Shift Enter in both modes (`index.ts:1058-1080`) | `ConsoleViewModel.submit` → `run` → `kernelGateway.execute`, on `ConsoleInputRow`'s Run button, disabled while the last entry runs | **defect** | The forced run is what a tap on Run is; `submit`'s docstring records the dedicated-action-in-place-of-Shift+Enter call. The walk found `run` awaiting `reopeningIfStale` outside the flow's `catch` — a refused `connect` escaped the launch and the entry span RUNNING for ever; fixed red-first in `dc73b380` (`submit_resolvesToStoppedErrorWhenConnectingIsRefused`). |
| `console:run-unforced` — `console.execute()`; notebook-mode Enter runs only when `requestIsComplete` says the block is whole, else inserts a newline (`widget.ts:960-1000`) | the same `submit()` — no completeness ask precedes it | **defect** | Executing is the same `execute`; the unforced half's check — `is_complete_request` — is `GAP-012`'s recorded absence, so a run here is always the forced arm's. The same `run` boundary defect, judged once with `console:run-forced`. |
| `console:linebreak` — `console.insertLinebreak()`, Accel Enter in terminal mode (`index.ts:1081-1102`) | Enter inside `CodeCellField` inserts a newline | adapted | `ConsoleInputRow`'s docstring states it: Enter keeps the field's own newline and the dedicated action runs. |
| `console:replace-selection` — `console.replaceSelection(text)`, the write-back a completer pick rides (`index.ts:1104-1130`) | — | absent | GAP-072. |
| `console:undo`, `console:redo` — `editor.undo()`/`editor.redo()` on the prompt cell, enabled on `sharedModel.canUndo()`/`canRedo()` (`index.ts:910-985`) | — | absent | GAP-071 — the prompt's `TextFieldState` keeps no edit history to walk. |
| `console:clear` — `console.clear()`, unconditional (`index.ts:997-1024`) | `ConsoleActionsMenu`'s "Clear Console Cells" → `ConsoleViewModel.clear` | present | Same act, same label, same unconditionality — `clear()`'s docstring cites both. |
| `console:interrupt-kernel` — `sessionContext.interrupt()`, reachable only from a menu or the palette in either reference (`index.ts:1132-1150`) | `ConsoleActionsMenu`'s "Interrupt Kernel" → `interrupt` → `kernelGateway.interrupt`, enabled only while a run is live | **defect** | `ConsoleActionsMenu.kt:23-27` states the menu-only placement both references share. The walk found the stale arm unguarded — interrupt on a session whose server was replaced threw `StaleKernelSessionException` out of the launch; fixed red-first in `0d2146b8` (`interrupt_onASessionWhoseServerWasReplaced_forgetsIt`). |
| `console:restart-kernel` — `sessionDialogs.restart(sessionContext)`, on the console toolbar's contributed slots and the Interactive Window's primary toolbar | — | absent | GAP-009 — recorded by the backlog before this audit, verdicted not decided here. |
| `console:shutdown`, `console:close-and-shutdown` — `sessionContext.shutdown()` leaving the console open; the close variant asks, shuts, disposes (`index.ts:1186-1255`) | — | absent | GAP-075 — the console surface has no end act, and closing its tab frees neither socket nor session. |
| `console:change-kernel`, `console:get-kernel` — `sessionDialogs.selectKernel`, and the invisible `session?.kernel` the kernel-name/status widgets ask (`index.ts:1338-1380`) | — | absent | GAP-074 — `NotebookKernelSession.changeKernel` exists but the console carries no kernel surface that reaches it. |
| `console:create` — mints `console-<count>-<uuid>` under cwd titled `Console <count>`, from launcher tile, palette, or menu (`index.ts:716-770`, `panel.ts:58-70`) | the launcher's console card → `LauncherViewModel.mintConsole` → `openStandaloneConsole` | adapted | The mint and the naming are the same (`Console.kt:7-15` cites both); one card instead of per-kernelspec tiles, and the sweep is the single-`consoleSession`-holder rule. |
| `console:open` — reveals the console whose session path matches, minting one when a running session has none (`index.ts:674-710`) | `openRunningSession`'s console arm — reveal only | **defect** | Found by the read and fixed red-first: every session POST derived `type` from the path and `console-<count>-<uuid>` is extensionless, so the row always read `file` and the tap fell to `openDocument` on a path that does not exist (`0af14e7b`; `OkHttpKernelLifecycleTest`, `LauncherViewModelTest`, `EditorViewModelTest`, `RunningPanelViewModelTest`). Still reveal-only past the fix — `Navigation.kt:172-185` states why a console with no tab cannot be synthesised. |
| `console:inject` — find-or-create a console by session path, run `code` in it (`index.ts:1294-1337`) | — | absent | GAP-073. `attachConsole`'s docstring already names the find-the-tracked-session half as its own precedent; the act that would use it does not exist. |
| `filemenu:create-console` — the File menu's entry, a semantic delegate to console:create (`mainmenu-extension/src/index.ts`) | the launcher card is the only create entry | adapted | The same mint under one entry point; the File menu shell itself is the chrome slice's (`AUD-009`). |
| `console:rename` — `InputDialog.getText` → `session.setName` = `PATCH api/sessions` `name` (`index.ts:1257-1292`) | — | absent | GAP-076 — `KernelSessions` has no name op, and a `Console` key's `title` is minted once. |
| `console:interaction-mode` — writes `interactionMode` to notebook or terminal, choosing which key runs (`index.ts:1482-1511`) | — | adapted | Both modes exist to pick which key runs; the touch prompt decides once — Enter only newlines, the button only runs — so a toggle would have no second mode to select (`ConsoleViewModel.submit`/`ConsoleInputRow`'s docstrings record the shape). |
| `console:toggle-autoclosing-brackets` — writes `promptCellConfig.autoClosingBrackets` (`index.ts:630-646`) | `EditorSettings.autoClosingBrackets`, the universal pair the prompt field reads | adapted | One setting rather than a per-widget `promptCellConfig` — `ConsoleScreen.kt:40-48` states the universal toggle's own reach. |
| `completer:invoke-console`, `completer:select-console` — `manager.invoke` on the current console and the pick's write-back, registered inside `console-extension` (`index.ts:1519-1560`) | — | absent | GAP-072 — `ConsoleInputRow` leaves `CodeCellField`'s `completionsAt` at `NO_COMPLETIONS`; the machinery exists unwired. |
| `notebook:create-console` — a console sharing the notebook's `sessionContext`, `split-bottom`, filed under the File menu's `consoleCreators` (`index.ts:1841`, `mainmenu-extension`'s group) | `KernelMenu`'s "New Console for Notebook" → `attachConsole()` — a second `NotebookKernelSession` pre-bound by `attachedTo` — → `openConsole`'s sweep-and-push | adapted | The share is the same session and the label is the reference's own (`KernelMenu.kt:265`); the placement is a stacked entry rather than a split pane, under the single-holder sweep `NotebookBackStack.kt:54-84` states. |
| `notebook:create-subshell-console` — a console whose session talks on a kernel-spawned subshell (`index.ts:5461-5473`), gated by `commsOverSubshells` | — | absent | GAP-077 — nothing on the wire or the port names a subshell. |
| `notebook:run-in-console` — the selection executes in the notebook's console through `ForeignHandler`, landing in its transcript (`notebook-extension/src/index.ts:1900-1990`) | — | absent | GAP-073 — no foreign execution exists to land. |

**The read's own findings.** One defect came out of tracing rather than comparing —
`openRunningSession`'s console arm could never fire, since every session POST derived `type` from
the path and a minted `console-<count>-<uuid>` is extensionless — **defect → fixed** in `0af14e7b`,
red-first through `OkHttpKernelLifecycleTest`, `LauncherViewModelTest`, `EditorViewModelTest` and
`RunningPanelViewModelTest`'s new cases, the fix threading a declared `SessionType` from both
console constructors down to `SessionStartRequest` the way `ConsolePanel`'s `type: 'console'`
declares it. And one observation shaped a gap rather than a fix: closing a Console tab releases
neither socket nor session while `SessionContext.dispose` releases the client connection —
recorded as `GAP-075` rather than repaired, because the naive disconnect would close an attached
console's *shared* socket out from under its notebook.
### Sessions and kernel lifecycle — verdicts

`T3`'s entries, read through `SessionContext` rather than the command ids alone: every notebook
id here is a call on `context.sessionContext` — `sessionDialogs.restart`/`selectKernel` for the
two that ask, `sessionContext.interrupt`/`shutdown`/`reconnect` for the ends, `session?.kernel`
for the invisible get — and the seven `kernelmenu:*` ids are `SemanticCommand`s executing
whatever the current widget registered for the act (`mainmenu/src/kernel.ts`), so a menu row and
the command it wears are one read. `filemenu:close-and-cleanup` delegates to
`*:close-and-shutdown` with `application:close` as the fallback.

Ours is the same shape at one remove: `NotebookKernel` (`stopRunningCell`, `restart(then)`,
`restartAndRunAll`, `shutdown(then)`, `picker`) over a `NotebookKernelSession` that lands
`SessionContext`'s two signals — `disposed` as `kernelSessions.ended` → `endIfMatches`,
`kernelChanged` as `rebound` → `rebindIfMatches` (`NotebookKernelSession.kt:180-220`) — with
`reopeningIfStale` the landing the reference has no equivalent for. The compound restarts keep
the reference's own composition: an awaited restart whose continuation runs after the kernel has
been replaced (`index.ts:3245-3345`, cited at `NotebookKernel.restart`), and each asks first
through the one `RestartDialog` reading the reference's own sentence — *"All variables will be
lost"* (`sessioncontext.tsx:1505-1520`).

The settings read as machinery rather than commands: `kernelInfoTimeout` is
`KERNEL_INFO_TIMEOUT`'s 3 s — the reference's own default as a constant (`KernelSockets.kt`);
`commsOverSubshells` is `GAP-077`'s absent wire; `showStatusBarItem` toggles a statusbar item
this shell has no surface for — the running panel's Kernel Sessions section is the same list;
and `skipKernelRestartDialog` is the one hole the read found in the dialogs — `GAP-078`.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `notebook:interrupt-kernel` — `sessionContext.interrupt()` → `POST api/kernels/<id>/interrupt`; the toolbar's stop square, enabled while running (`createInterruptButton`, `apputils/src/toolbar/widget.tsx:50`) | `NotebookBar`'s stop square, enabled `busy` → `stopRunningCell` → `kernelGateway.interrupt` → the same POST | **defect** | Same endpoint, same icon, same enable-while-running — `NotebookBar.kt`'s doc cites both halves. The walk found the stale arm unguarded — interrupt on a session whose server was replaced threw `StaleKernelSessionException` out of the launch; fixed red-first in `0d2146b8` (`aStopWhoseServerWasReplaced_landsOnNoKernel`). |
| `notebook:restart-kernel` — `sessionDialogs.restart` → `restartKernel` → `POST api/kernels/<id>/restart` | `NotebookBar`'s refresh button → `RestartDialog(RESTART)` → `NotebookKernel.restart` → `kernelGateway.restart` | **defect** | The same confirm asking the reference's own sentence, then the same POST. The dialog's "Do not ask me again" half is the settings read below, not the command's. The walk found the stale arm unguarded — restart beside `reopeningIfStale` threw out of the launch with `kernelRestarting` latched; fixed red-first in `0d2146b8` (`aRestartWhoseServerWasReplaced_reconnectsAndRestartsTheFreshKernel`). |
| `notebook:restart-clear-output` — restart, then `NotebookActions.clearAllOutputs` (`index.ts:3245-3260`) | `KernelMenu`'s `RESTART_AND_CLEAR` row → `RestartDialog` → `kernel.restart(then = edits.clearAllOutputs)` | present | The same awaited-restart-then-clear; `restart`'s `then` parameter exists for exactly these three commands (`NotebookKernel.kt`). |
| `notebook:restart-run-all` — restart, then the public `NotebookActions.runCells` over the whole notebook (`index.ts:3315`) | `NotebookBar`'s fast-forward → `restartAndRunAll` → `restart(then = runs.runCells(cells))` | present | Same continuation, same toolbar-button surface; `restartAndRunAll`'s doc states why it composes out of `runCells` rather than `runAll`, exactly as the reference does. |
| `notebook:shutdown-kernel` — `sessionContext.shutdown()` → `DELETE api/kernels/<id>`, unguarded | `KernelMenu`'s "Shut Down Kernel" → `kernel.shutdown()` → `kernelGateway.shutdown` → DELETE + `ended` | present | Unguarded in both — the close variant is the one that asks (`index.ts:3189-3213`), which `shutdown`'s doc states. |
| `notebook:change-kernel` — `sessionDialogs.selectKernel` over specs, running kernels and "No Kernel" | `KernelMenu`'s "Change Kernel…" and the `KernelIndicator` tap → `NotebookKernelPicker` | **defect** | The same three groups, transcribed from `kernelOptions`; the picker's `autoStartDefault`/"always start the preferred kernel" checkbox is declined on purpose — `KernelChoice.kt` states it. The walk found the stale PATCH escaping `pick`'s launch with `kernelChanging` latched; fixed red-first in `0d2146b8` (`changeKernel_connectsTheChoiceWhenTheOpenSessionProvesStale`). |
| `notebook:get-kernel` — the invisible `session?.kernel` the name and status widgets query (`index.ts:4591-4607`) | `KernelSession.kernelName`/`preferredKernel`, drawn by `KernelIndicator` | adapted | The question exists as resolved state rather than a command result — `KernelIndicator.kt`'s doc states why the name is the *live* kernel's, not the file's. |
| `notebook:reconnect-to-kernel` — `kernel.reconnect()`: close the websocket, open a fresh one on the same kernel (`index.ts:4591-4607`) | `KernelSockets.socketFor` evicts a socket that ended, so the next ask opens the fresh one | **defect** | Found by the read and fixed red-first: a socket that reported `Ended` between asks stayed cached in `sessionSockets`, the next `execute` reused it — OkHttp's `send` returns false there and no frame ever arrives — so the run waited for ever. `RealKernelGatewayTest.execute_reopensASocketThatEndedBetweenRuns`; the act is now the reference's own, arriving on the next ask rather than from a menu. |
| `notebook:close-and-shutdown` — asks "Shut down the notebook?", awaits `sessionContext.shutdown()`, disposes (`index.ts:3189-3213`) | `KernelMenu`'s "Close and Shut Down Notebook…" → `CloseAndShutDownDialog` → `kernel.shutdown(then = onBack)` | present | Same question in the reference's own words, same awaited ordering — `CloseAndShutDownDialog` cites `index.ts:3196-3205`, and `shutdown`'s `then` runs after the session has ended, as the reference's `await` does. |
| `notebook:access-previous-history-entry`, `notebook:access-next-history-entry` — ArrowUp/ArrowDown at the prompt cell's edge walking `NotebookHistory`, behind `accessKernelHistory` (`index.ts:5179+`) | `SymbolRow`'s `Hist ↑`/`Hist ↓` keys behind `EditorSettings.accessKernelHistory` → `recallPrevious`/`recallNext` | adapted | The walk is the same object, the setting keeps the reference's name and off-by-default; the keys are the touch surface for the cell-edge arrows — `SymbolRow.kt`'s `RECALL_*_ENTRY` labels cite the reference's own. |
| `kernelmenu:interrupt` — the Kernel menu's Interrupt row, executing the current widget's `interruptKernel` | `NotebookBar`'s stop square | **defect** | The same stale-session escape, judged once with `notebook:interrupt-kernel` — the act is what was unguarded; the bar-over-menu placement stands as the `adapted` half. |
| `kernelmenu:reconnect-to-kernel` — the menu's Reconnect row | — | **defect** | The same dead-socket hang, judged once with `notebook:reconnect-to-kernel`: the menu's own act is what `socketFor`'s eviction now performs on the next ask. |
| `kernelmenu:restart` — the menu's "Restart Kernel…" row | `NotebookBar`'s refresh button | **defect** | The same stale-session escape, judged once with `notebook:restart-kernel` — the act is what was unguarded; the bar-over-menu placement stands as the `adapted` half. |
| `kernelmenu:restart-and-clear` — the menu row executing `restartKernel` + `clearWidget` (`mainmenu-extension/src/index.ts:653`) | `KernelMenu`'s `RESTART_AND_CLEAR` row | present | Same label, same composition, filed in the Kernel menu the reference files it under. |
| `kernelmenu:change` — the menu's "Change Kernel…" row | `KernelMenu`'s "Change Kernel…" row | **defect** | The same stale PATCH escape, judged once with `notebook:change-kernel` — the row and label stand; the act behind them was what was unguarded. |
| `kernelmenu:shutdown` — the menu's "Shut Down Kernel" row | `KernelMenu`'s "Shut Down Kernel" row | present | Same label, same unguarded act. |
| `kernelmenu:shutdownAll` — "Shut Down All Kernels…" → counted confirm → `sessions.shutdownAll()` (`mainmenu-extension/src/index.ts:708-735`); also the running panel's own bulk button (`running/src/index.tsx:584-620`) | the Kernel Sessions section's "Shut Down All" → `kernelShutdownAllText`'s counted confirm → `shutdownAllSessions` | **defect** | The act lives on the running surface, which is the reference's own second home for it; `RunningPanelViewModel.kt`'s doc states why ours deletes sessions — the level the reference's own `sessions.shutdownAll()` works at — where its dialog counts kernels. The walk found one refused DELETE escaping the launch and skipping the rows behind it; fixed red-first in `7b5e670e` (`aRefusedShutdownDoesNotSkipTheRestOfShutdownAll`). |
| `filemenu:close-and-cleanup` — semantic delegate to `*:close-and-shutdown`, `application:close` as the fallback | `KernelMenu`'s "Close and Shut Down Notebook…" row | adapted | The same close-and-shutdown act under the one entry point; the File menu shell itself is `AUD-009`'s chrome. |
| `sessionDialogs.json`'s `skipKernelRestartDialog` — the "Do not ask me again." checkbox writing `kernelPreference.skipKernelRestartDialog`, and the setting that skips the confirm outright (`sessioncontext.tsx:1493-1540`) | — | absent | GAP-078. |
| `kernels-settings.json`'s `commsOverSubshells` — how comms route over kernel-spawned subshells | — | absent | GAP-077 — no subshell exists to route over. |
| `kernels-settings.json`'s `kernelInfoTimeout` — 3000 ms default | `KERNEL_INFO_TIMEOUT` = 3 s, a constant | adapted | The reference's own default, fixed rather than tunable — `KernelSockets.kt` states it; there is no settings surface to hang a tunable on. |
| `kernels-settings.json`'s `showStatusBarItem` — toggles the statusbar's running-kernels item | — | adapted | The shell has no statusbar; the running panel's Kernel Sessions section is the same list the item counts, always shown rather than toggled. |

**The read's own findings.** One defect came out of tracing the `Ended` path rather than comparing
menus: a socket that reported `Ended` between asks stayed the session's socket, and OkHttp's own
contract — `send` returns false on a terminated socket, no frames arrive — turned the next run
into a wait for ever. Fixed red-first as `execute_reopensASocketThatEndedBetweenRuns`, which also
made the scripted fake honest: it now returns false from `send` after `Ended`, as the real one
does. The carried gaps were measured again rather than re-opened: `GAP-009` stands — the console
still has no Restart, and the `ended`/`rebound` pair every holder already collects is the shape a
shared end/restart signal would take — and `GAP-010` stands — `BaseSpaceServer` still counts the
generation no port carries to the editor. `GAP-011` is `T4`'s.

### The running panel — verdicts

`T4`'s entries, read down both dispatches. Theirs: `running:show-panel` activates the sidebar
widget and `running:show-modal` launches a `SearchableSessions` inside a `Dialog`
(`running-extension/src/index.ts:123-209`); the five `kernel-*` ids are acts on the Kernels
section's rows — a context-menu hit or a toolbar button resolving `args.id` off
`dataset.context` (`kernels.tsx`) — and the sections themselves are `IManager`s registered into
`IRunningSessionManagers`: Open Tabs reads `labShell.widgets('main')` (`opentabs.ts`), Kernels
groups `kernels.running()` by spec with each kernel's sessions as children
(`kernels.tsx:143-199`), Recently Closed reads `recentsManager.recentlyClosed` (`recents.ts`),
and Terminals is `terminal-extension`'s own contribution, not this package's
(`terminal-extension/src/index.ts:420-462`). `Section` wraps each manager in an accordion title
with a per-section toolbar — the counted shutdown-all confirm, the list/tree switch,
collapse-expand — while `RunningSessions` is the `SidePanel` itself, added left at rank 200,
carrying the refresh button that calls every manager's `refreshRunning()`, the
`listViewSections` StateDB entry and the movable-sections registration
(`running/src/index.tsx:786-955`). The data re-arms on each `services` manager's
`Poll{interval: 10s, standby: 'when-hidden'}` (`session/manager.ts:32-41`), whose
`runningChanged` the kernels section funnels through a 100 ms `Throttler` (`kernels.tsx:51-66`),
with a second 10 s `Throttler` re-checking whether any kernel has `connections < 1` to re-enable
`kernel-shut-down-unused` (`:56-84`).

Ours is the same panel one level flatter: `RunningSidePanel` draws three collapsible
`RunningSection`s — Open Tabs off the shell's own back stack, Kernel Sessions and Terminals off
`RunningPanelViewModel`'s two server lists — with the refresh icon on the panel's bar, a bulk
confirm per section while open, a tap to open and a direct ✕ to end (`RunningSidePanel.kt`).
The `LaunchedEffect` 10 s poll is the `Poll`'s cadence with *composed* standing in for
`standby: 'when-hidden'`, and `kernelSessions.ended` collected from `init` is `runningChanged`'s
kernel half — a row the panel itself ends leaves at once because `BaseSpaceKernelSessions`'s
`shutdown` emits `ended` whether or not the server was still there to DELETE; the terminal port
carries no such signal, so its rows re-list after their own acts instead
(`RunningPanelViewModel.kt:30-43`, `BaseSpaceKernelSessions.kt:46-50`).

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `running:show-panel` — `app.shell.activateById(running.id)`, the palette/menu reach that puts Sessions and Tabs on screen (`running-extension/src/index.ts:123-134`) | the rail's `SidePanel.RUNNING` item → `onChoosePanel` (`SidePanelRail.kt:50`), the bottom bar's same item at one pane (`onePaneDestinations`, `SidePanel.kt:114`) | adapted | The activate act is a rail/bar item rather than a command — the panel is a destination of the shell, not a palette reach, which is `SidePanel.chosen`/`showingAtOnePane`'s own shape. |
| `running:show-modal` — a `Dialog` over `SearchableSessions`: a `FilterBox` flattening every section, arrow-key walk, `getValue` clicking the active row (`index.ts:190-209`, `running/src/index.tsx:999-1146`) | — | **absent** | `GAP-079`. |
| `running:kernel-open-session` — docmanager:open on the session's path, console:open when `type === 'console'`; label is the session name or path basename, icon by type (`kernels.tsx:248-285`) | `openRunningSession` — `openDocument(path)` for a document row, a console row reveals its open tab (`Navigation.kt:172-185`) | adapted | The document arm is the same act; the console arm is reveal-only because `entry<Console>` draws from the single `consoleSession` holder — a console with no tab cannot be synthesised, which both the caller and `RunningSidePanel`'s doc state. A row whose path went stale opens the dead path — `GAP-068`'s own recorded caveat. |
| `running:kernel-shut-down` — `kernels.shutdown(id)` → `DELETE api/kernels/<id>` on the row's ✕, unguarded (`kernels.tsx:286-307`; `Item.shutdown` asks nothing, `index.tsx:218-225`) | the row's ✕ → `shutdownSession` → `DELETE api/sessions/<id>`, unguarded (`RunningPanelViewModel.kt:54-57`) | **defect** | Session-level rather than kernel-level — `delete_session` shuts the kernel behind the session (`sessionmanager.py:522-527`), the level `kernelmenu:shutdownAll` was already verdicted at. The row keeps the reference's own `Shut Down Kernel` label. The walk found a refused DELETE escaping the launch; fixed red-first in `7b5e670e` (`aRefusedShutdownLeavesTheSessionListed`). |
| `running:kernel-shut-down-unused` — every kernel with `connections < 1`, a confirm listing them, `KernelAPI.shutdownKernel` each, then both managers refreshed; a `CommandToolbarButton` on the section toolbar, `isEnabled` re-asked on a 10 s `Throttler` (`kernels.tsx:54-140`) | — | adapted | Declined where the section is drawn: the "unused" question is asked of `GET api/kernels`' `connections`, which the contract does not carry — the port's rows are sessions, and `RunningSidePanel.kt`'s class doc states the decline. |
| `running:kernel-new-console` — console:create with `kernelPreference: {id}` — the minted console's `SessionContext` joins the *running* kernel (`kernels.tsx:204-225`) | — | **absent** | `GAP-080`. |
| `running:kernel-new-notebook` — notebook:create-new with `kernelId` — the same join for a notebook (`kernels.tsx:226-247`) | — | **absent** | `GAP-080`. |
| `Section` + `Item` — accordion title, per-section toolbar (counted shutdown-all confirm, list/tree switch, collapse-expand), rows carrying icon/label/`labelTitle`/detail with tap-to-open and an unguarded ✕ (`index.tsx:192-746`) | `RunningSection` + `RunningRow` — caret fold, the bulk `TextButton` drawn only while open and disabled on an empty section, the `(kernelIdPrefix)` detail list-mode carries | adapted | Folds survive a rotation in `rememberSaveable` rather than a StateDB, and the two tree-only controls are declined with the grouping itself — `RunningSidePanel.kt`'s doc states both, and `_updateButtons`' disabled-when-empty is `bulkEnabled`'s own read. |
| `RunningSessions` — the `SidePanel` at left rank 200, its refresh button calling every `refreshRunning()`, `restorer.add` for layout restore, `IMovableSectionRegistry` for drag-reorder (`index.tsx:786-955`, `running-extension/src/index.ts:83-146`) | `RunningSidePanel` under `SidePanel.RUNNING`, refresh icon → `viewModel.refresh()` (`ShellSidePane.kt:122-131`) | **defect** | A fixed trio in the registration order rather than a movable registry — the rail offers no drag to reorder with, so there is nothing for a registry to persist. The walk found `relist` propagating a refused `running()` uncaught — the class's own "keeps the last good list" contract; fixed red-first in `7b5e670e` (`aRefusedRelistKeepsTheLastGoodSessions`). |
| Open Tabs manager — `labShell.widgets('main')`, `activateById` on tap, `widget.close()` on ✕, "Close"/"Close All" with the counted confirm; `runningChanged` driven by `layoutModified` plus each widget's `title.changed` (`opentabs.ts:19-127`) | `openTabs` handed down from the back stack, `onActivateTab` → `activateMainAreaTab`, `onCloseTab` through the shared close gate, "Close All" with the same question | present | Same section, same acts, same labels; recomposition on the live `openTabs` list is `OpenTabsSignaler`'s job done by the state itself, and the dirty gate is the reference's own close-request answer. |
| Recently Closed manager — `recentsManager.recentlyClosed`, reopen validates then docmanager:open and forgets the row, per-row Forget, "Forget All" (`recents.ts:33-109`) | — | **absent** | `GAP-066` — the `closed` list it would read was verdicted in AUD-005; this section is the surface half that gap already names. |
| Terminals section — `terminal-extension`'s manager: `terminals.running()`, terminal:open on tap, `manager.shutdown`/`shutdownAll` under "Shut Down"/"Shut Down All" (`terminal-extension/src/index.ts:420-462`) | `TerminalGateway.running()`/`close()`, `onOpenTerminal` reattaches to the held shell, the same confirm sentence verbatim (`RunningSidePanel.kt:181-205`) | **defect** | The section's machinery verdicts here as `running-extension`'s surface; the pane's own `terminal:*` ids stay `AUD-007`'s. The walk found a refused close escaping the launch instead of relisting; fixed red-first in `7b5e670e` (`aRefusedTerminalCloseLeavesTheTerminalListed`). |
| `runningSessions.tsx` statusbar item — "N Terminals, M Kernel sessions" counting off `sessions.runningChanged`/`terminals.runningChanged`, click activating the sidebar (`apputils/src/runningSessions.tsx:111-213`) | — | adapted | The shell has no statusbar to hold it; the same two lists the item counts are drawn always-on in the panel — `showStatusBarItem`'s own read, one section up. |
| kernels-by-spec tree and the connected-sessions submenu — `KernelSpecItem` → `RunningKernel` → session children, the switch-view that flattens it, the `contextMenu.opened` submenu listing a kernel's sessions (`kernels.tsx:143-199,309-517`) | flat session rows under "Kernel Sessions" | adapted | The grouping is the same `GET api/kernels` decline the shut-down-unused row rests on — `RunningSidePanel.kt`'s doc names the section "Kernel Sessions" because its rows *are* sessions; a phone's rail has no context-menu slot for the submenu. |

**The read's own findings.** `GAP-011` retires rather than keeps: it was written before the
panel existed, and its premise — the kernel half needs `GET api/sessions`, which the lifecycle
does not expose — is now `KernelSessions.running()`/`shutdown()` with the Kernel Sessions
section standing on them. What the reference's Kernels section still has that ours declines —
the spec-grouped tree, Shut Down Unused, kernel-level shutdown — is each recorded where the
decline lives, which is `adapted` already stated rather than an open gap. Two new gaps open:
`GAP-079` — the panel lists the four lists but nothing filters or quick-opens them — and
`GAP-080` — a new document cannot join a running kernel, though `KernelChoice.Running` is the
wire half already plumbed and the kernel picker's change path already reaches it. No defect came
out of the panel's own read: `shutdown` emits `ended` through the registry whether or not the
server was still there, so a row ends under the tap rather than at the next poll, and a dead
server's `running()` already answers empty with `endAll` told. `GAP-011`'s row in
`decisions-triage.md` is updated to the retirement; `GAP-009` and `GAP-010` stand from `T3`.

### The walk

Six paths through the one registry. **A submission to a transcript line:** `ConsoleInputRow`'s
Run button → `ConsoleViewModel.submit` — blank input refused, a still-RUNNING last entry refused
— → a minted `newCellId`, a RUNNING `ConsoleEntry` appended, the field cleared →
`viewModelScope.launch { run }` → `flow { emitAll(session.reopeningIfStale { open ->
kernelGateway.execute(open, id, source) }) }`: `open()` hands back `opened` or `connect`s under
the resolved preference (`Private.getDefaultKernel`'s resolution shape) →
`BaseSpaceKernelGateway.gatewayKnowing` → `PerRunningServer.get` → `RealKernelGateway.execute` →
`KernelSockets.socketFor`, an `Ended` socket evicted on the way — → the streamed `KernelEvent`s
folded per event into the entry (`after`: `Output` appends, `ExecutionCount` records, `Idle`
does nothing) → the flow's completion → `resolved`: a set `interrupting` says CANCELLED, an
error output says STOPPED_ERROR, else STOPPED_OK. **An interrupt:** the last entry RUNNING →
`interrupting` marked → `kernelGateway.interrupt(open)` → `POST api/kernels/<id>/interrupt` →
the kernel's own `KeyboardInterrupt` output — the flag, not the stream, is what says cancelled,
exactly as the notebook's `stopRunningCell` does it; a `StaleKernelSessionException` off a dead
server is caught and the dead session forgotten through `endIfMatches` instead of escaping the
launch. **A session ended elsewhere landing on both holders:** the panel's row ✕ →
`kernelSessions.shutdown(serverSessionId)` → `BaseSpaceKernelSessions` DELETEs only when a live
server answers (`liveOrNull` — ending must never boot a runtime) and `registry.end`es regardless
→ `ended`, a buffered no-replay `MutableSharedFlow<String>` — one emission per serverSessionId,
ever — → every holder's collector: `NotebookKernel`'s `init` → `endIfMatches` → `opened = null`
→ `onConnectedChanged(false)` → `kernelConnected = false` and the indicator's "No Kernel"; the
console's `NotebookKernelSession` — a second holder over the same serverSessionId, minted by
`attachConsole`'s `attachedTo` — its own `endIfMatches` collector landing identically; the
panel's own collector → `refresh()` — the row leaves because the event, not the DELETE's return,
removed it. **A rebound:** a `changeKernel` → `PATCH api/sessions` keeps `serverSessionId` and
swaps `kernelId` → `registry.rebind` → `rebound` → every holder's `rebindIfMatches` — `opened`
takes the rebound session or the holder would address a kernel process the server already shut
down, and `onBound` is what writes the kernelspec back. **The stale reopen against a replaced
server:** `reopeningIfStale { use(open()) }` → `use` throws `StaleKernelSessionException` —
`IssuedSessions`' id set cleared when `PerRunningServer` saw the new generation — → `opened =
null`, the indicator honestly dropping in between — → `use(open())` once more: a fresh session
on the server that replaced it. One retry; a second refusal is a real failure and belongs to the
caller. **The list re-arming:** `ended` → `refresh()`; the refresh icon → `refresh()`; a
terminal's close → `relist()` directly, since the terminal port carries no end signal; the 10 s
`LaunchedEffect` poll — the `Poll`'s cadence, *composed* standing in for `standby: 'when-hidden'`.
Each `relist` is two passive asks — `liveOrNull` never boots a runtime to answer — and each list
keeps its last good answer when its own ask is refused.
What the walk found, beyond the rows above:

- **Growth is a flow per submitted line and a relist per act — bounded by construction.** One
  `launch` and one cold `Flow` per submission, one `MutableStateFlow` update per `KernelEvent`,
  one buffered `ended` emission per end and one `rebound` per PATCH, `extraBufferCapacity` set
  once in the registry. `shutdown` answers locally through `ended` even when the DELETE had no
  server to reach, and a dead server's `running()` answers `emptyList()` plus `endAll` off
  `liveOrNull`'s `isRunning` check — ending and listing never boot a runtime. The growth the
  walk names rather than opens: a server's disappearance lands as `endAll`'s N emissions → N
  `refresh()`es → N `relist()`s, where the reference funnels the same burst through a 100 ms
  `Throttler` on `runningChanged` (`kernels.tsx:51-66`) — bounded because a dead server's relist
  never reaches the wire, and `endAll`'s second pass emits nothing once the registry is empty.
- **Branching splits on who may ask, and each arm belongs to the layer that owns it.** `submit`
  refuses blank and busy before minting the entry; `open()` splits held-session from connect and
  a resolved preference from a picker-owed one — `KernelPickRequiredException` is a cue, not an
  error, so the notebook's run lane rethrows it for the picker while the console folds it as an
  error output (a console holds no picker to cue). `reopeningIfStale` splits stale-once
  (reconnect and re-ask) from stale-twice (propagate). `restart` splits nothing-to-replace (the
  continuation still runs) from stale (reconnect first) from dead-twice or pick-owed (No Kernel
  or the cue, the continuation skipped — an awaited restart that never happened has no
  continuation). `endIfMatches`/`rebindIfMatches` are exact-match on `serverSessionId` — an
  emission naming somebody else's session is ignored. `interrupt` splits nothing-running (a
  no-op) from stale (forget), and carries `interrupting` so the kernel's own `KeyboardInterrupt`
  resolves the run CANCELLED rather than ERROR.
- **Failure is answered at the surface it belongs to — and the walk found three boundaries where
  it was not, all repaired red-first.** `interrupt`, `restart` and `changeKernel` reached the
  gateway beside `reopeningIfStale`, so a session minted by a server the idle backstop had
  stopped threw `StaleKernelSessionException` out of the `scope.launch` — an uncaught coroutine
  failure with `kernelRestarting`/`kernelChanging` latched and a continuation that silently
  never ran; `0d2146b8`, red through
  `aRestartWhoseServerWasReplaced_reconnectsAndRestartsTheFreshKernel`,
  `aStopWhoseServerWasReplaced_landsOnNoKernel`,
  `changeKernel_connectsTheChoiceWhenTheOpenSessionProvesStale` and
  `interrupt_onASessionWhoseServerWasReplaced_forgetsIt`. The console's `run` awaited
  `reopeningIfStale` before the flow existed, so a refused `connect` escaped past the `catch`
  and left the entry RUNNING for ever; `dc73b380`,
  `submit_resolvesToStoppedErrorWhenConnectingIsRefused`. And every launch in
  `RunningPanelViewModel` propagated a refused ask, contradicting its own "keeps the last good
  list" contract; `7b5e670e`, the four `aRefused*` cases. The failure shapes the walk confirmed
  rather than repaired: `ended` fires for a dead server's sessions even when nothing was left to
  DELETE, `running()`'s `dropped` reconciliation ends rows the server forgot without asking, and
  an already-gone shutdown is a no-op by the registry's once-per-id emission.
- **Coupling is the walk's real yield — one registry, one direction, and who hears an end.**
  `KernelSessionRegistry` is the single channel every session act announces on:
  `BaseSpaceKernelGateway` registers on `connect` and rebinds on PATCH, `BaseSpaceKernelSessions`
  emits `ended` for `shutdown`, `dropped` rows and `endAll` on a dead server, and
  `PerRunningServer` is the one place a client learns its server was replaced — `build` per
  generation, `IssuedSessions` cleared — which is `GAP-010`'s point that the editor proper never
  hears it. `ended`/`rebound` are collected by exactly three holders — the notebook's session,
  the console's own `NotebookKernelSession` copy (same `serverSessionId`, so one `ended` lands
  on both), and the panel — each inside `init`, because `ended` is buffered but not replayed and
  a late subscriber would miss it. The direction is deliberately one-way: the panel never calls
  into a holder — the holder hears the registry — which is `SessionContext`'s
  `disposed`/`kernelChanged` by another name. Who does *not* hear: `NotebookHistory` and the
  asks layer (`completion`, `hover`, `signatureHelp`, `definition`, `format`, `rename`) address
  `session.opened` fresh per ask, so an end reaches them only as the next ask's refusal — folded
  to the caller's own lane, which is all they need.

---

## Terminal emulator and view — `AUD-007`

**The surface is one package's eleven ids, twelve settings, and two emulators' worth of state
machine.** `terminal-extension` declares **11 ids**, all `terminal:*`, and nothing else — the
first package in the audit whose whole surface is one slice's with nothing carried (verified: no
other package under `packages/` declares a `terminal:` id). Twelve settings sit in
`schema/plugin.json` — `fontFamily`, `fontSize`, `lineHeight`, `theme`, `screenReaderMode`,
`scrollback`, `shutdownOnClose`, `closeOnExit`, `pasteWithCtrlV`, `macOptionIsMeta`,
`cursorBlink`, `showStatusBarItem`. Produced, never stored:

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package terminal-extension
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/terminal-extension/schema/plugin.json'))['properties']))"
```

**This slice meets two kinds of reference.** The behaviour spec is JupyterLab's command surface
plus the shipped server's wire; the state-machine spec is the two emulators ours transcribes —
Termux's `terminal-emulator/` (`TerminalEmulator.java`'s 24-state dispatch over `TerminalBuffer`,
`TerminalRow`, `TextStyle`, `TerminalColors`, `WcWidth`, `KeyHandler`,
`TerminalSession`/`TerminalSessionClient`) and JediTerm `core/` (`Terminal.java`,
`emulator/JediEmulator`, `model/`'s `TerminalTextBuffer`/`LinesBuffer`/`TerminalSelection`/
`Tabulator`, plus `typeahead/`, `model/hyperlinks/` and `mouse/` — surfaces Termux does not have).
The view is Termux's `terminal-view/` — `TerminalView`'s IME contract, hardware-key handling,
`textselection/` handles, `GestureAndScaleRecognizer`'s scroll/fling/scale, `TerminalRenderer` —
plus the app layer's `io/` extras (`TermuxTerminalExtraKeys`, `KeyboardShortcut`,
`TermuxTerminalViewClient`, `TermuxSessionsListViewController`). The machinery the commands ride
on is `packages/terminal/src/widget.ts`'s `Terminal` over vendored xterm.js and its lazy addons
(`addon-fit`, `addon-search`, `addon-web-links`, `addon-webgl`), `tokens.ts`'s
`ITerminal`/`IOptions`, `terminal-extension/src/searchprovider.ts`'s document-search
registration — `GAP-036`'s terminal entry — and `services/src/terminal/`'s connection,
`default.ts`'s `_pendingMessages`-until-`setup` being the shape ours mirrors.

```sh
grep -c 'case ' reference/research_repos/termux-app/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java
ls reference/research_repos/termux-app/terminal-emulator/src/main/java/com/termux/terminal/
ls reference/research_repos/termux-app/terminal-view/src/main/java/com/termux/view/
ls reference/research_repos/jediterm/core/src/com/jediterm/terminal/ reference/research_repos/jediterm/core/src/com/jediterm/terminal/{emulator,model}
```

Under it all, the behaviour ours mirrors is the shipped rootfs's —
`jupyter_server_terminals/{api_handlers,handlers,terminalmanager}.py` and
`terminado/{websocket,management}.py`: `api/terminals` POST/GET/DELETE, and
`terminals/websocket/<name>` carrying `setup`/`stdin`/`stdout`/`set_size`/`disconnect`.

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/terminado/websocket.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/terminado/management.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server_terminals/api_handlers.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server_terminals/terminalmanager.py
```

**Ours** is `terminal/` — `TerminalEmulator` over `EscapeParser`/`EscapeDispatch`/
`EscapeParameters`, `TerminalScreen`/`TerminalGrid`/`TerminalRow`/`RowRing`, `ScreenModes`,
`Utf8Decoder`, `TerminalReporting`/`TerminalReplies`/`TerminalNotices`, `CharacterWidth`,
`GraphicRendition`, `TerminalPalette`, `GridSelection`, transcribed from Termux and JediTerm
where the files cite them — `ui/terminal/` (`TerminalPaneScreen`/`TerminalPaneViewModel`,
`TerminalCanvas`/`GlyphStyle`/`TerminalCellMetrics`/`TerminalGridSize`, `TerminalActionsMenu`,
`TerminalExtraKeysRow`/`TerminalModifierLatch`, `TerminalKeyEncoding`) — and the wire:
`TerminalGateway`'s six ops (open/running/attach/send/resize/close) behind `data/space/`'s
`RealTerminalGateway`, `TerminalFrame`, `TerminalLifecycle`/`OkHttpTerminalLifecycle`,
`OkHttpTerminalSocketOpener`, `BaseSpaceTerminalGateway`, `ActivityTrackedTerminalGateway`,
`FakeTerminalGateway`, with the one setting `TerminalSettings.fontSizeSp` over
`TerminalSettingsRepository`. Produced:

```sh
python3 scripts/read_port_surface.py            # TerminalGateway 6, TerminalSettingsRepository 2
python3 scripts/read_port_surface.py --unused
```

**Carried in, recorded not rejudged.** `GAP-036`'s terminal half — the provider registry's fifth
registration, `searchprovider.ts` — is verdicted here, not repaired. The running panel's
Terminals section was verdicted in `AUD-006` `T4` as `running-extension`'s surface; what stays is
the pane's own ids. Three closed questions are this area's settled ground: `CLOSED-006` (`stty
size` reads a fixed default, not the pty — `vi`'s reflow is the oracle), `CLOSED-007` (a re-attach
replays the whole session, so the pane clears first), `CLOSED-009` (a pty works in the app
process — probed before the terminal was designed). Two ADRs already carry this area's decisions:
`ADR-0020` (the colour ramp lives in `terminal/`) and `ADR-0026` (a terminal is ready when `setup`
arrives). VS Code's `workbench.action.terminal.*` ids are **not** partitioned — the command
surface is JupyterLab's by §8.3 and VS Code is read for presentation only — and IntelliJ's
`plugins/terminal/` is the *integration* it names, the emulator it drives being JediTerm's. What
runs inside the pty is `AUD-010`'s; this slice owns the wire to it and the screen it draws on. The
partition rows are in the shared table under `AUD-002`'s heading — all eleven ids to `T2` (the
pane surface and the wire); `T3` verdicts the emulator, `T4` the view, `T5` the walk.

### The pane surface and the wire — verdicts

`T2`'s entries, read down both dispatches. Ours: the bar's "+" / the launcher's Terminal card →
`rememberNewTerminal` → `openNewTerminal` → `TerminalGateway.open` → `openTerminal`; the pane's
`TerminalActionsMenu` → `TerminalPaneViewModel.changeFontSize` → `TerminalSettings`; the running
panel's tap → `openTerminal(name)` and its ✕ → `close` (AUD-006 `T4` verdicted that surface's own
rows); `KeepTheShellAttached` → `attach()` → `BaseSpaceTerminalGateway.attach` →
`RealTerminalGateway` over `OkHttpTerminalSocketOpener`/`OkHttpTerminalLifecycle`/`TerminalFrame`,
answers going back through `TerminalGateway.send`/`resize`. Theirs: `index.ts`'s eleven
`addCommand`s and the `plugin.json` settings, `widget.ts`'s `Terminal` over xterm.js,
`default.ts`'s `TerminalConnection` (`_pendingMessages` until `setup`, `_reconnect` backoff,
`_isDisposed` drop), `manager.ts`/`restapi.ts`, `searchprovider.ts`'s registry add - and under it
all the shipped server's `api/terminals` handlers and `TermSocket`/`NamedTermManager`.

The read is what found the three defects below: two refused asks escaping a launch (the mint, the
post-`Ended` write) and one refused attach escaping the flow - the same class AUD-006 fixed twice,
fixed red-first here and verdicted with the rows they sit under.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `terminal:create-new` — Launcher tab, File menu, palette; args `name`, `cwd` (`index.ts:482-550`) | the bar's "+" at one pane and the launcher's Terminal card at two → `rememberNewTerminal` → `openNewTerminal` → `open(cwd)` → `openTerminal` | adapted | the mint happens with the browsed directory carried (`Navigation.kt`'s `newTerminal(browsedDirectory)` is `openFolderInTerminal`'s `cwd` half already); the `name` arg is uncarried - a name is the server's own `%d` mint (`terminalmanager.py`), never the client's |
| `terminal:open` — activate a widget by session name, delegating to `create-new` on a miss (`index.ts:552-580`) | the running panel's tap → `openTerminal(name)`, `openInMainArea`'s reveal-or-push | adapted | reveal-by-name is the same act; a miss lands `Ended` carrying the 404 `TermSocket.get` raises rather than minting under a client-chosen name - `BaseSpaceTerminalGateway`'s stated "truthful answer", pinned by `attach_toATerminalTheServerDoesNotHaveEndsWithTheReason` |
| `terminal:copy`, `terminal:paste` - `getSelection()`/`hasSelection()`, the context-menu Copy, clipboard → `widget.paste` (`index.ts:623-746`); the `pasteWithCtrlV` keybind is the same reach | `GridSelection`/`TerminalGrid.selectedText` is span→text machinery nothing can arm - no selection state exists - and `TerminalEmulator.paste`/`PasteCommand` (sanitizing, bracketed) sits uncalled | **absent** | `GAP-081`. The gap is the anchor and the reach, not the wiring: no gesture creates a selection, no surface offers Copy or Paste |
| `terminal:copy-link` — `contextMenuLink` reads the OSC-8 link under the context-menu pointer (`index.ts:693-716`; `addon-web-links`) | no link is ever parsed - OSC 8 has no dispatch entry, so there is nothing under a pointer to name | **absent** | `GAP-082`. Deeper than an unwired menu: the emulator discards the sequence and stores no link |
| `terminal:refresh` — `session.reconnect()` then `term.clear()` (`widget.ts:265-270`), the connection's own `_reconnect` backoff on a dropped socket (`default.ts:178-210`) | `KeepTheShellAttached`'s cancel → re-`attach`, which clears the emulator first while `read_buffer` replays the whole session | adapted | reconnect+clear+replay is exactly what every STARTED→attach cycle does (CLOSED-007), so no manual reach exists; what is *not* carried is the mid-foreground auto-retry - a socket that dies while visible ends the pane (`Ended`) instead of a silent re-attach, and a leave-and-return is the re-try |
| `terminal:increase-font`, `terminal:decrease-font` | `TerminalActionsMenu`'s two rows → `changeFontSize` → `TerminalSettings.fontSizeSp`, persisted | present | the rows disable at `TerminalFontSize`'s bounds - the reference's `isEnabled` clamps (`index.ts:770`, `:790`) reread as menu state; Termux's own 4/2/256 (`TerminalFontSize.kt`) rather than the command's ±1 |
| `terminal:set-theme` — writes the `theme` setting through a four-item submenu (`index.ts:813-845`) | `isSystemInDarkTheme()` picks `TerminalPalette.VS_CODE_DARK`/`_LIGHT` | adapted | the palette follows the system theme - the composition-root pick `TerminalPalette.kt`'s KDoc states; a per-terminal theme menu has no reach where the app itself has no theme switcher |
| `terminal:shut-down` — `session.shutdown()` on the current widget (`index.ts:747-765`) | the running panel's ✕ → `TerminalGateway.close` → `DELETE api/terminals/<name>` | adapted | the same act one surface over - AUD-006 `T4` verdicted the panel's rows; the pane's own menu carries no shutdown, and `OkHttpTerminalLifecycle`'s 404-tolerant DELETE is the server's own `kill` semantics for an already-dead name |
| `terminal:open-folder-in-terminal` — a directory's context menu → `create-new` with `cwd` (`index.ts:128-163`) | `open(cwd)` carries a directory on the wire and the bar's "+" already passes `browsedDirectory`; a directory row's long-press menu offers Rename and Delete only | **absent** | `GAP-083`. The wire half exists - what nothing mounts is the row's own reach |
| `terminal:`-registered search - `searchRegistry.add('terminal', TerminalSearchProvider)` (`searchprovider.ts`) | no registration | **absent** | `GAP-036`'s fifth row, verdicted here not repaired: the pane's grid is no searchable surface and the workspace search never reaches it |
| `fontSize` (`plugin.json`, default 13) | `TerminalSettings.fontSizeSp` → `codeTextStyle(state.fontSizeSp.sp)` | present | stored through `TerminalSettingsRepository`, observed into `uiState`, drawn by `TerminalCanvas` - `changeFontSize`'s tests |
| `theme` | folded into `terminal:set-theme`'s row - the system is the switcher | adapted | the reference's `inherit` default is also theme-follow; ours follows the system rather than the app's Light/Dark |
| `scrollback` (default 1000) | `TerminalEmulator.DEFAULT_TRANSCRIPT_ROWS = 1000`, fixed | adapted | fixed at the schema's own default - and at Termux's, whose `MAX_TRANSCRIPT_ROWS` the constant cites - so the setting's only value anyone runs is already the one chosen |
| `shutdownOnClose` (default false) | a closed tab detaches the socket; the server's pty keeps running | adapted | fixed at the schema's own default - `NamedTermManager`'s list still holds the shell for the running panel, and the idle backstop is the lifecycle's answer to a truly dead one |
| `closeOnExit` (default true - the tab closes when the pty exits) | `Ended` sets `endedReason`; the pane stands on the shell's last screen | **absent** | `GAP-084`. The write is real - `endingTheTerminalSurfacesItsReason` pins it - but nothing reads it: no banner, no auto-close, a dead shell indistinguishable from a quiet one |
| `fontFamily`, `lineHeight`, `cursorBlink`, `screenReaderMode`, `macOptionIsMeta`, `showStatusBarItem`, `pasteWithCtrlV` | none carried | adapted | declined where each one's machinery is: the face is the app's code font (`CodeText`), the cell height is measured (`terminalCellMetrics`), the cursor is a static block (the blink's own read is the cursor's - T4's), the canvas is drawn text not a DOM (the a11y read is likewise T4's), no Option key exists, no status bar exists, and `pasteWithCtrlV` declines with the paste it would bind - `GAP-081` |
| widget readiness - stdout buffered until xterm is ready and `setup` lands, `_isDisposed` dropping a send, `onTitleChange` → `title.label` (`widget.ts:135-270`, `:486-488`) | the emulator exists before the socket (`TerminalPaneViewModel`'s `val emulator`), so output needs no buffer; `send`/`resize` suspend until attach-readiness (`send_waitsForSetupRatherThanForTheHandshake`); `endedReason` is the `_isDisposed` gate; `state.title` → `onTitleChanged` → the tab | **defect** | the gate's absence was found by the read, not a sighting - a write after `Ended` escaped `viewModelScope.launch`, the port's refusal being a throw. Fixed red-first: `aKeystrokeOnAnEndedShellReachesNoSocket`, `typingOnAClosedShellDoesNotThrow` |
| connection machinery - `_pendingMessages` until `setup`, `connectionFailed` as a stream state, `manager.ts`'s `isReady`/`refreshRunning`/`shutdownAll` | `TerminalGateway`'s six ops over `RealTerminalGateway`/`TerminalFrame`; `running()`/`close` answer the panel's poll (AUD-006 `T4`'s verdicts) | adapted | the queue-until-setup is `send`'s own suspend; a refused `live.get()` now lands `Ended` like the 404 (the third defect - `attach_endsWithTheReasonWhenTheServerCannotStart`); `refreshRunning`/`shutdownAll` are the panel's, already verdicted |
| the wire - `POST/GET/DELETE api/terminals`, `terminals/websocket/<name>` carrying `setup`/`stdin`/`stdout`/`set_size`/`disconnect`, `read_buffer` replay, `NamedTermManager`'s `%d`, 404-on-unknown-name | `OkHttpTerminalLifecycle` → the three REST verbs; `OkHttpTerminalSocketOpener` → the websocket; `TerminalFrame` maps each frame and `FakeTerminalGateway` mints the same `%d` | present | `TerminalFrameTest`, `OkHttpTerminalLifecycleTest`, `RealTerminalGatewayTest` pin every frame, the replay, and the 404-tolerant delete; a refused mint escaping `rememberNewTerminal`'s launch was the second defect - `aRefusedTerminalOpenLeavesTheStackAlone` |

### The emulator — verdicts

`T3`'s entries, read down three dispatches and the model under them. Theirs:
`TerminalEmulator.java`'s `processCodePoint` - 24 `ESC_*` states of which 23 are reachable
(`ESC_PERCENT` is declared and no arm ever enters it) - into `doEsc`/`doEscPound`, `doCsi`,
`doCsiQuestionMark`, `doCsiBiggerThan`, `doOsc`/`doOscSetTextParameters` and `doDeviceControl`,
over `TerminalBuffer`/`TerminalRow`/`TextStyle`/`TerminalColors`/`WcWidth` and `KeyHandler`'s
keycode table; and JediTerm `core/`'s `JediEmulator.processControlSequence`/
`processTwoCharSequence` over `JediTerminal` on `TerminalTextBuffer`/`LinesBuffer`/
`CyclicBufferLinesStorage`, `Tabulator`, `StoredCursor`, `StyleState` and `TerminalSelection`,
with `typeahead/`, `model/hyperlinks/` and `emulator/mouse/` as what that reference adds. Ours:
`EscapeParser`'s twenty `EscapeState`s routing into `EscapeDispatch` and the appliers
(`GraphicRendition`, `ScrollMargins`, `TerminalReporting`, `CursorAddressing`, `ModeSwitching`,
`ScreenEditing`, `SelectiveErasing`, `RectangularAreas`, `RectangularAttributes`), the model
`TerminalScreen`/`TerminalGrid`/`TerminalRow`/`RowRing`/`ScreenBuffers`/`SavedCursor`/`TabStops`/
`TerminalCursor`, `Utf8Decoder` → `CharacterPlacement`/`CharacterWidth`/`LineDrawingCharset`,
`OperatingSystemCommand`/`ColorCommands`/`ClipboardCommand`/`WindowTitle`, `DeviceControlString`,
`PasteCommand`, `TerminalReplies`/`TerminalNotices` - and `ui/terminal/TerminalKeyEncoding` for
the key-table half, whose *reach* (IME and hardware delivery) stays `T4`'s.

```sh
grep -c 'case ' reference/research_repos/termux-app/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java   # 272 arms
grep -n 'ESC_\w* = ' reference/research_repos/termux-app/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java
grep -n "case '.\?'" reference/research_repos/jediterm/core/src/com/jediterm/terminal/emulator/JediEmulator.java
grep -n '^  [A-Z][A-Za-z]*' app/src/main/java/com/jupydroid/app/terminal/EscapeParser.kt   # the 20 EscapeStates
ls app/src/main/java/com/jupydroid/app/terminal/
```

These rows carry no `terminal:*` id - all eleven are `T2`'s above - so the produced counts do
not move; each `absent` still names the `GAP-NNN` ADR-0067 obliges. No defect surfaced in the
read: where ours and the reference differ it is a commented narrowing, not a wrongness.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| the state machine - `processCodePoint` over 23 reachable states, `startEscapeSequence`/`continueSequence`/`finishSequence`, CAN/SUB abandoning a sequence as DEL, C1 bytes discarded mid-sequence | twenty `EscapeState`s covering the same routing; `ESC_CSI_EXCLAMATION` and `ESC_CSI_ARGS_SPACE` are folded into `UnsupportedIntermediateByte` | present | every state the reference can enter has an ours-equivalent - the two folds land on the swallow the next two rows verdict, not on text; the JediTerm seam the split is taken on is `EscapeParser`'s own KDoc |
| CSI final bytes - `doCsi`'s arm set: `@` `A` `B` `C`+`a` `D` `E` `F` `G`+`` ` `` `H`+`f` `I` `J` `K` `L` `M` `P` `S` `T` `X` `Z` `b` `c` `d` `e` `g` `h` `l` `m` `n` `p` `r` `s` `t` `u`, the `?`/`>`/`<`/`=` private bytes, the `'`/`"`/`$`/`*` intermediates | `applyFinalByte` fans out to `GraphicRendition`/`ScrollMargins`/`TerminalReporting`/`CursorAddressing`/`ModeSwitching`/`ScreenEditing`; what none claims is swallowed with its parameters | present | arm for arm - and past JediTerm, whose 27-final `processControlSequence` drops REP `b`, HPR `a`, VPR `e`, CHT `I`, CBT `Z`, DECSLRM/ANSI.SYS `s`/`u` and every intermediate ours keeps |
| DECSTR - `CSI ! p`, soft terminal reset (`TerminalEmulator.java:669-675` - `reset()`; JediEmulator's `'p'` arm does `reset(false)`) | routed to `UnsupportedIntermediateByte` - the sequence and its `p` are consumed and nothing happens | **absent** | `GAP-085`. `TerminalScreen.reset` exists and `ESC c` reaches it; the `!` entry is the declared never-implemented sub-step `EscapeParser`'s comment names - terminfo's `rs2`, which `reset`/`tput reset` emits, is this sequence |
| DECSCUSR - `CSI Ps SP q`, cursor shape block/underline/bar (`TerminalEmulator.java:871-893` - `mCursorStyle`; JediEmulator's `'q'` → `cursorShape` → `CursorShape`) | swallowed the same way; no cursor-shape state exists anywhere for a view to read | **absent** | `GAP-086`. The static block is *drawn* correctly - the gap is the sequence that would change it, which neovim's `guicursor` and readline's vi-mode emit per mode |
| ESC finals - `doEsc`'s arms (IND, NEL, HTS, RI, DECBI, DECFI, DECSC, DECRC, DECKPAM/PNM, lower-left, RIS, `(`/`)` designation) and `doEscPound`'s DECALN, including the empty SS2/SS3 arms written `case '0'` where SS3 is `'O'` | `EscapeDispatch.introduce`/`applyCommand`/`applyPound`/`designateG0`/`designateG1` | present | every arm lands; the `'0'`/`'O'` quirk and the SS2/SS3 dead arms are documented in the dispatch's KDoc, and an unclaimed byte is consumed silently exactly as `unknownSequence` ends the sequence after logging |
| graphic sets - Termux's two designate arms + SI/SO invocation + the DEC Special table; JediTerm's ISO-2022 whole: G0-G3 under seven designators (`(` `)` `*` `+` `-` `.` `/`), LS2/LS3/LS1R/LS2R/LS3R invocations, SS2/SS3 single shifts, ~20 national sets in `CharacterSets` | `LineDrawingCharset` - two designate bits and an invoke flag | adapted | Termux's own two-set model, whose KDoc states it ("that is the whole of what `ESC ( C` and `ESC ) C` can designate here, as it is upstream"); `LineDrawingCharsetTest` pins the three cells where the two references' DEC-Special tables disagree against `vttest` |
| DECSET/DECRST - the 12-entry `mapDecSetBitToInternalBit` (1,5,6,7,25,66,69,1000,1002,1004,1006,2004) plus the number-keyed side-effects: DECCOLM's margins+erase+home+DECLRMM-off, DECOM's re-home, DECLRMM's margin clear, 1048 save/restore, the one-arm 47/1047/1049 alt-screen with its stale-resize override, mark-records-no-value, mouse-mode exclusivity | `DecMode`/`ScreenModes`/`ModeSwitching` - external number and internal bit folded into one enum | present | "no second table to drift"; DECCOLM's four effects kept though the width never changes (`reset` leans on them); `1048`/`47`/`1047`/`1049` carry no bit exactly as `-1` reads upstream; the alt-screen's raw-saved-coordinate override on a stale leave is transcribed verbatim |
| mode numbers only JediTerm has - 1034 eight-bit-input, 1039 `AltSendsEscape`, ANSI 2/12 (KAM/SRM), 9001 win32-input (suppressed upstream anyway); 1001/1003/1005/1015 mouse modes+formats | not carried; an unmapped number is ignored as `mapDecSetBitToInternalBit`'s `-1` does | adapted | 1039's behavior is what our alt latch does unconditionally; 1034 has no eighth bit to set without a Meta key that sets one; KAM/SRM are local-echo-era no-ops in both references' spirit; the mouse numbers fold into `GAP-087`'s row |
| margins, tab stops, origin - DECSTBM, `CSI s` as DECSLRM-under-DECLRMM else save-cursor, DECIC/DECDC under `'`, the every-8 `mTabStop` array with HTS/TBC, `nextTabStop`/CBT bounded by margins, origin-mode clamping | `ScrollMargins`/`CursorAddressing`/`TabStops`/`TerminalCursor` | present | the `s` ambiguity is kept on the reference's own condition (DECLRMM decides); `next`/`previous` scan `columns` and clamp at the margin as `nextTabStop` does |
| erase and edit - ED/EL/ECH/ICH/DCH/IL/DL/SU/SD/REP with the current-style erase and the wrap-flag asymmetries; DECSED/DECSEL/DECSCA; the rectangle set DECCRA/DECFRA/DECERA/DECSERA/DECCARA/DECRARA and DECSACE's internal bit | `ScreenEditing`/`SelectiveErasing`/`RectangularAreas`/`RectangularAttributes` | present | `TerminalScreen.rectangularAttributeChange` is the same mechanism as `DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE` at a different home - a screen field rather than a DecSet bit, since nothing external can read it |
| SGR - `processAttribute`'s set: reset, bright, faint, italic, underline, blink, fast-blink, inverse, invisible, strikethrough, 21-29 resets, 30-37/90-97 fg, 40-47/100-107 bg, 39/49, the `38`/`48`/`58` extended forms incl. `:` sub-parameters, `4:` underline styles | `GraphicRendition.applied` | present | every code incl. the kitty `4:` sub-parameter form and aixterm brights; `CellStyle`'s packing is `TextStyle.encode` verbatim |
| reports - DA1/DA2 (`CSI c`, `CSI > c`), DSR `5n`/CPR `6n`, DECXCPR `?6n` with page=1, DECRQM `? Ps $ p` incl. the alt-screen bit answered from `mScreen == mAltBuffer`, window ops `11`-`21 t`, title push/pop `22`/`23 t` with the 20-deep stack | `TerminalReporting`/`WindowTitle`/`TerminalReplies` | present | same reply strings, same 20-cap drop-oldest; a reply is text not bytes - the one `byte[]` reply upstream is four ASCII literals, and encoding is the transport's |
| OSC numbers - `doOscSetTextParameters`'s set {0,1,2 title; 4,10-12 colours incl. `?` queries on the dynamics; 52 selection; 104/110-112/119 resets}, BEL-or-ST terminated, abandoned past the length cap | `OperatingSystemCommand`/`ColorCommands`/`ClipboardCommand`/`WindowTitle` - the same set | present | the documented departures are deliberate: `ColorCommands` declines upstream's crash on a malformed `104`, and the measured `OSC 10;?;?` double-answer (the second carrying *background*) is kept with its comment |
| OSC 8 links - JediTerm's `case 8` → `HyperlinkStyle` on the cells + `TextProcessing`'s filters auto-detecting printed URLs | no `8` arm; a printed URL is text | **absent** | `GAP-082`, opened at `T2` over `terminal:copy-link` - this row names the emulator half the gap already records: no link span is ever stored |
| DCS - `doDeviceControl`: DECRQSS `$q` answering DECSCL `"p` only, `+q` termcap replies through `KeyHandler.getCodeFromTermcap`'s ~40-entry table | `DeviceControlString`: DECRQSS-DECSCL, and `+q` for five capabilities (`Co`, `colors`, `TN`, `name`, `kB`) | adapted | the five-cap table's constraint is stated in the KDoc - a reply must name an Android keycode, which needs the `GAP-088` machinery that does not exist yet; JediTerm drops DCS whole (`deviceControlString` returns `false`), so ours sits between the references |
| C0 and UTF-8 - BEL (bell outside, terminator inside a string), BS stepping back over a wrap, HT to the next stop, LF/VT/FF new-line, CR to the left margin, SO/SI charset invoke, CAN/SUB abandon; decode with overlong/surrogate/unassigned rejection and partials held across socket frames | `TerminalEmulator`'s byte loop + `Utf8Decoder` + `TerminalScreen` | present | push-driven decode so a split multibyte survives a WebSocket boundary; malformed input yields U+FFFD, a C1 byte inside a sequence is discarded, and `ESC` inside a string restarts - all as upstream |
| widths and placement - `WcWidth`'s Unicode-15 tables, combining marks appended to the last cell (capped), DWC-at-edge, the pending-wrap flag, insert-mode's blockCopy shift | `CharacterWidth`/`CharacterWidthRanges` + `CharacterPlacement` | present | the width ranges are packed arithmetic checked at corners and seams; `CharacterPlacement`'s screen-edge guard is a documented departure that keeps a narrowed-margin wide char from throwing where upstream relies on the unchecked array landing in bounds |
| the buffer model - `TerminalBuffer`'s one ring (`mTotalRows` = screen+history, external coords `-history..rows-1`), `resize`'s rows-only fast path and its `setChar`/`scrollDownOneLine`/`setLineWrap` slow-path replay, alt buffer at `rows,rows` with no transcript | `TerminalGrid`/`RowRing`/`GridReflow`/`ScreenBuffers`/`PackedText`/`RowStyles` | present | JediTerm's split model (`TerminalTextBuffer` screen + `CyclicBufferLinesStorage` history at 5000) is the disagreement; ours takes Termux's ring at JupyterLab's `scrollback: 1000`, the `DEFAULT_TRANSCRIPT_ROWS` comment recording both |
| paste and clipboard machinery - `paste()` sanitizing (ESC and C1 stripped, LF/CRLF to CR) and `\033[200~`/`201~` under DECSET 2004; `OSC 52`'s base64 → `onCopyTextToClipboard` | `PasteCommand` + `ClipboardCommand` → `TerminalNotices.onClipboardCopy` | present | machinery verbatim, malformed payloads swallowed; *reach* is the gap already open - `GAP-081` covers that nothing calls `emulator.paste` and nothing creates a selection to copy |
| mouse reporting - `sendMouseEvent`'s X10-style and SGR encoders behind `isMouseTrackingActive`, the `MOUSE_*` constants; JediTerm's `emulator/mouse/` - a 267-line encoder over modes 1000-1003 and formats XTERM/XTERM_EXT/URXVT/SGR (1005/1006/1015) | the mode bits store (1000/1002/1004/1006, exclusive as upstream) and nothing else - no encoder, no consumer of the bits, no `send` path | **absent** | `GAP-087`. A program enabling mouse reporting sets flags nobody reads; the *gesture* half (which pointer event becomes a mouse report) is `T4`'s read, but the protocol half - an encoder over the modes and a route to `send` - is this emulator's absence |
| key encoding - `KeyHandler.getCode`'s table (F1-F12, Insert/ForwardDel, SysRq/Break, numpad with app-keypad variants, DPAD_CENTER) + `transformForModifiers`'s `CSI 1;Psm` + `getCodeFromTermcap`'s reverse map; JediTerm's `TerminalKeyEncoder`/`TerminalKeyEventProcessor` the same breadth plus Shift+Tab → `ESC [ Z` and macOS variants | `terminalKeystroke`/`applyModifierLatches`: arrows/Home/End in both DECCKM states, PgUp/PgDn, Tab/Escape, Ctrl+A-Z, the ctrl/alt latches | **absent** | `GAP-088`. The covered subset is byte-exact (`KEYCODE_MOVE_HOME`/`_END`, the `\033[5~`/`6~` pair, Tab-is-`\011`); the rest of the table - F-keys, Insert, Delete, modified keys, numpad, Ctrl+Space's NUL, the termcap answer set - has no encoding |
| synchronized output - JediTerm's `2026` → `SynchronizedOutput`, buffering until the `?2026l` end-marker then flushing (contour's BSU/ESU spec) | no `2026` arm; output paints as it arrives | **absent** | `GAP-089`. Graceful by degradation - a TUI's batch-redraw hint lands as an ignored mode - but the mode is a real probe target for modern full-screen apps |
| typeahead - `typeahead/TerminalTypeAheadManager`'s predict/debounce/reconcile over `TypeAheadTerminalModel` + `TerminalTypeAheadSettings` | none | adapted | declined: the machinery exists to hide a *remote* session's latency (`adjustLatency` measures the prediction's round-trip); this app's shell is a process-local websocket, so there is no RTT to predict over and no speculative repaint a prediction could safely ride |
| the session boundary - Termux's `TerminalSession`/`TerminalOutput` (pty + `ByteQueue` + title/clipboard/colour callbacks); JediTerm's `TtyConnector`/`TerminalDataStream`/`TerminalStarter` | `TerminalNotices` + `TerminalReplies` + the `T2` wire | adapted | the reference's one class carrying both directions is split by where each binds - replies to `TerminalGateway.send`, notices to the ViewModel; the pty/`ByteQueue`/`TtyConnector` substrate itself is the websocket, a stated deviation the wire rows verdict |
| selection - `TerminalBuffer.getSelectedText` over the ring; JediTerm's `TerminalSelection`/`SelectionUtil` as model state | `GridSelection` is `getSelectedText` verbatim, and nothing creates a span | **absent** | `GAP-081`, opened at `T2` - this row names the machinery half: the emulator holds text-of-a-rectangle faithfully, and no gesture ever hands it one |

The reads below are where the references disagree with *each other*, settled in `T3`'s favour of
the named one: scrollback is one ring (Termux) not two stores (JediTerm), at JupyterLab's 1000;
DCS is Termux's narrow table, JediTerm offering none; REP/HPR/VPR/CHT/CBT/DECSLRM are kept with
Termux though JediTerm drops them; charsets are Termux's two-set model, not JediTerm's ISO-2022;
DECSCUSR and DECSTR are *ours* absent while both references carry them - the only cells in the
table where both references out-ship us.

### The view — verdicts

`T4`'s entries, read down the screen-side of both references. Theirs: `TerminalView`'s 1,500
lines - `onCreateInputConnection`'s `BaseInputConnection` and `commitText`'s code-point loop,
`onKeyPreIme`/`onKeyDown`/`inputCodePoint`, `GestureAndScaleRecognizer`'s scroll/fling/scale,
`textselection/`'s cursor controller and drag handles with its ACTION_MENU, `onScreenUpdated`'s
snap and `isSelectingText`/`isAutoScrollDisabled` carve-outs, `onCheckIsTextEditor` and the
transcript content description, the `TerminalViewClient` seam - `TerminalRenderer`'s
`measureText("X")`/`getFontSpacing` metrics, `drawTextRun` batching, `CURSOR_STYLE_*` shapes
with `invertCursorTextColor`, the `canvas.scale` width correction and `mInvertedColors`; the
app layer's `io/` extras - `TermuxTerminalExtraKeys`'s property-driven `ExtraKeysView` rows,
`KeyboardShortcut`'s ctrl/volume chords, `TerminalToolbarViewPager`, `FullScreenWorkAround`,
`TermuxTerminalViewClient`'s scale/paste/bell/logging answers and
`TermuxSessionsListViewController`'s drawer. JediTerm `ui/` is the second opinion:
`TerminalPanel` over `JediTermWidget` with a real `JScrollBar`, `BlinkingTextTracker`, word and
line drag-selection, middle-click paste, mouse wheel, clickable hyperlinks and `input/`'s
key-event processor. Ours: `TerminalPaneScreen`'s invisible `BasicTextField` and
`onPreviewKeyEvent`, `TerminalCanvas`/`GlyphStyle`'s batched runs and block cursor,
`terminalCellMetrics`/`terminalGridSize`, `TerminalExtraKeysRow`/`TerminalModifierLatch`,
`detectTerminalScrollDrag` -> `TerminalPaneViewModel.scrollBy`, `TerminalActionsMenu`'s font
stepper, and the transparent `Text` per row that is this canvas's answer to TalkBack.

```sh
wc -l reference/research_repos/termux-app/terminal-view/src/main/java/com/termux/view/{TerminalView,TerminalRenderer,GestureAndScaleRecognizer}.java
ls reference/research_repos/termux-app/terminal-view/src/main/java/com/termux/view/textselection
ls reference/research_repos/termux-app/app/src/main/java/com/termux/app/terminal/io
ls reference/research_repos/jediterm/ui/src/com/jediterm/terminal/ui
ls app/src/main/java/com/jupydroid/app/ui/terminal
```

These rows carry no `terminal:*` id - all eleven stayed `T2`'s - so the produced counts do not
move here either; each `absent` still names its `GAP-NNN`. Two `defect`s surfaced in the read,
both in what was drawn rather than in what was stored: the block cursor painted over its own
glyph where `invertCursorTextColor` keeps it legible, and a soft-keyboard Enter landed `\n`
where `commitText` rewrites every committed `'\n'` to `'\r'` - "a terminal expects \r". Both
were fixed red-first in this slice and are verdicted with the rows they sit under.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| the IME contract - `onCreateInputConnection`'s `VISIBLE_PASSWORD` + `NO_SUGGESTIONS` for the Samsung IMEs (their #686), `commitText`'s per-code-point loop (surrogate pairs, `'\n' -> '\r'`, `codePoint <= 31` routed to `sendKeyEvent`), `deleteSurroundingText` re-emitted as DEL key events, selection/editable callbacks stubbed | the invisible `BasicTextField` laid over the whole canvas (`KeyboardType.Password`, `autoCorrectEnabled = false`), `snapshotFlow` -> `sendTerminalDiff`: delete to the shared prefix as `\u007F`s, then type the tail; the field clears when its text ends in Enter | **defect** | the `\n` -> `\r` rewrite was absent - a soft Enter sent `\n` where the reference and a real Return key send `\r`, and a raw-mode program that binds `C-m` and `C-j` apart (Emacs) reads them as different keystrokes. Fixed red-first: `aTrailingNewlineIsSentAsCarriageReturnAndClearsTheField`, `aNewlineMidTextIsSentAsCarriageReturn`. The rest is the same contract at Compose's boundary: the field IS the input connection, password-type is the same Samsung guard, and the diff's delete-then-retype is `deleteSurroundingText`+`commitText` read as a buffer delta |
| hardware keys - `onKeyPreIme`/`onKeyDown` -> `inputCodePoint`/`KeyHandler.getCodeForKey` with the modifier transform, volume keys routed, BACK folded to Escape | `onPreviewKeyEvent` -> `terminalKeystroke` -> `onEncodedKey` -> `send`; an unhandled key returns `false` and falls through to the field | adapted | delivery is the same reach - encoded bytes to the one socket write - while the table's breadth is `GAP-088`'s, verdicted at `T3`. BACK->Escape stays with the activity's own dispatcher rather than folding: the pane is one tab among several, and Esc exists on the extra-keys row |
| the latches - `SpecialButtonState`/`readSpecialButton` over CTRL/ALT/SHIFT/FN: tap toggles active (clearing lock), long-press locks, unlocked consumes once | `TerminalModifierLatch`/`TerminalModifierLatches` verbatim as an immutable value, read by `applyModifierLatches` on the typed-text path | present | `TerminalModifierLatchTest` pins each transition against `SpecialButton.java`'s own lines; the stated narrowing - CTRL/ALT only, Shift/Fn being the reference's optional extras - is the enum's KDoc |
| extra keys - the `extra-keys` property -> `ExtraKeysInfo`/`ExtraKeysView` (configurable rows, long-press popups, two-row layouts) under `TermuxTerminalExtraKeys`, `TerminalToolbarViewPager`'s page 0 | `TerminalExtraKeysRow` - a fixed row: Esc, Tab, Ctrl, Alt, the arrows, Home, End, PgUp, PgDn | adapted | the fixed set is Termux's own default row's content; what is not carried - property configurability, popup keys, the toolbar's second page (a plain text field for long input the IME itself answers here) - is a narrower row, not a different contract |
| `KeyboardShortcut` - ctrl+key and volume-down-as-ctrl chords to actions, the client flags `shouldBackButtonBeMappedToEscape`/`shouldEnforceCharBasedInput`/`shouldUseCtrlSpaceWorkaround` | the Ctrl latch covers soft-keyboard chords; no volume-chord or flag layer exists | adapted | the chords' own need - a Ctrl a touchscreen can apply - is the latch's; Ctrl+Space's NUL is `GAP-088`'s named table gap; the workaround flags exist for IME quirks the password-type field was itself chosen to dodge |
| selection and its handles - long-press arms `TextSelectionCursorController`, `TextSelectionHandleView` cursors drag over the transcript with edge auto-scroll, ACTION_MENU's copy/paste | `GridSelection` spans exist in the emulator and no gesture can create one; the canvas carries no long-press handling at all | **absent** | `GAP-081`, opened at `T2` - this row names the gesture half its machinery verdict lacked: nothing ever hands `GridSelection` a span, so neither handle nor menu has anything to act on. JediTerm's word/line drag-select and middle-click paste are the same gap's extra surface |
| scroll - `GestureAndScaleRecognizer.onScroll` -> `doScroll(distanceY)` with `mScrollRemainder` carrying sub-row pixels; the alt screen redirected to Up/Down arrow keystrokes | `detectTerminalScrollDrag` (Initial-pass consume, measured pre-empting the field's Main-pass) -> `scrollBy(delta, cellHeightPx)` - the remainder carried, the alt screen forwarded as `terminalKeystroke` arrows | present | `doScroll` verbatim including the remainder and the arrow redirection, pinned by `scrollByRemembersTheRemainderAcrossCalls`/`scrollByForwardsUpArrowsWhileTheAlternateScreenIsLive`; the Initial-pass consume is the measured Compose answer to "the same view handles both" |
| fling, wheel, key scroll, scrollbar - `onFling` into `Scroller.fling`/`computeScroll` decay, `onGenericMotionEvent` wheel x3-rows, Shift+PageUp/PageDown full-screen `doScroll`s, `awakenScrollBars`' thumb; JediTerm's `JScrollBar` and wheel are the same chrome | none - a drag moves whole rows and release stops dead | **absent** | `GAP-090`. Four flavours of the same unbuilt chrome; `scrollBy`'s own return contract already reserves the fling driver ("so a caller driving this from a fling... can stop its own deceleration cleanly at a clamped boundary") |
| pinch - `GestureAndScaleRecognizer.onScale` -> `setTextSize` clamped to `MIN_FONT_SIZE`/`MAX_FONT_SIZE`, tracked through `onScaleBegin`/`End` | `TerminalActionsMenu`'s A+/A- stepper -> `TerminalFontSize` (the same 4-256 at a 2sp step) persisted through `TerminalSettings` | adapted | the same act through a menu rather than a gesture - `T2` verdicted the rows themselves; a pinch carries no semantics a TalkBack user or a Maestro flow can name, the stepper does |
| output while scrolled - `onScreenUpdated` snaps `mTopRow` to 0 unless `isSelectingText` or `isAutoScrollDisabled` holds the view back (with selection cursors riding the scroll counter) | any `Output` event snaps `topRow` to 0 | adapted | `TerminalPaneViewModel`'s comment names it a simplification: neither carve-out has machinery behind it - no selection to preserve, no scroll-lock toggle to honour - so live output wins every time; the lock lands with `GAP-081`'s own task |
| cell metrics - `mTextPaint.measureText("X")` for the width, `Paint.getFontSpacing()` for the height, `mFontLineSpacingAndAscent` for the baseline | `terminalCellMetrics` - the same "X" measured through Compose's `TextMeasurer`, shared `codeTextStyle` with the glyphs | present | the file's own KDoc names the reference's two calls it mirrors; one style feeding measurement and drawing alike keeps the two from drifting |
| sizing - `onSizeChanged` -> `updateSize()` -> `TerminalSession.updateSize` -> pty `TIOCSWINSZ`, IME-shrunken height counted | `BoxWithConstraints` -> `terminalGridSize` -> `resize()` -> `emulator.resize` + wire `set_size` | present | the container is the source of truth exactly as `TerminalView.onSizeChanged` is; the doubled-IME-inset detour is recorded in `TerminalCanvas`'s own KDoc, measured wrong on device and removed |
| the draw - `drawTextRun`'s run batching at style-change boundaries, indexed/256/truecolour resolution, bold-brightens/inverse/dim/underline/italic/strikethrough/invisible, non-default backgrounds only | `GlyphStyle`'s `resolvedRunColors`/`styleFor` under `TerminalCanvas`'s `drawRun` - the same batching, resolutions and background rule | present | `TerminalRenderer` is the cited model and `TerminalCanvasTest` pins the observable half: palette resolution, repaint-over, scrollback rows, INVISIBLE drawing nothing, the cursor's visibility rules |
| proportional-font correction - `drawTextRun` measures the run and `canvas.scale`s it into `mFontWidth * columns` when the font lied about being monospace | none - every run draws at the grid's cell pitch unscaled | **absent** | `GAP-092`. A font whose glyphs do not match the measured "X" (ligature-prone or mis-declared monospace) drifts ink and cells apart; `CodeText` is consistent today, so this is a dormant rendering risk rather than a visible wrongness |
| `CSI ? 5 h/l` - Termux's `isReverseVideo` -> `render`'s `reverseVideoHere` XOR on every run plus a foreground-coloured canvas fill; JediTerm's `TerminalMode.ReverseVideo` is *set* by `JediEmulator` and `TerminalPanel` never reads it | `DecMode.REVERSE_VIDEO` stores the bit; `TerminalCanvas` never reads it | **absent** | `GAP-091` - the stored-never-read class `GAP-087` already names, and one JediTerm shares: the emulator answers `? 5 h` truthfully and the paint step has no branch for it, so the sequence repaints nothing. Termux is the only reference that draws it |
| the cursor - `CURSOR_STYLE_BLOCK/UNDERLINE/BAR` drawn under the run, `invertCursorTextColor` repainting the run's text inverted over a block; `cursorBlink`'s timer and `onTerminalCursorStateChange` | the block only - shapes are `GAP-086`'s, blink stays declined - and until this read it was an opaque rect that erased the glyph beneath it | **defect** | `invertCursorTextColor` was missing: `drawCursor` painted the block *over* the runs. Fixed red-first - it now repaints the cell's glyph in the run's resolved back colour over the block, wide-char second halves and INVISIBLE honoured (`theBlockCursorShowsTheGlyphUnderIt`). JediTerm's `BlinkingTextTracker` blink stays folded into `cursorBlink`'s declined setting |
| accessibility - `onCheckIsTextEditor` true, `setContentDescription(getText())` republishing the whole transcript on every update | a transparent `Text` per row over the same grid - row-granular nodes a screen reader walks line by line | adapted | one utterance per row rather than one blob per screen, the `TerminalCanvas` KDoc's own stated reason; an IME-reachable node exists in both, the invisible field being ours |
| bell - `TerminalViewClient.onBell` -> `TermuxTerminalViewClient`'s notification/`Bell` | `TerminalNotices.onBell` reaches the ViewModel and is a `Unit` | adapted | the notice is carried and declined: JupyterLab's `widget.ts` has no bell handler at all, so this is a mobile-form's affordance, not a parity command - stated at the handler itself |
| dead session - `TermuxService`'s `onSessionFinished` notification/toast and the session list dropping it | `endedReason` stores; the pane stands on the shell's last screen | **absent** | `GAP-084`, verdicted at `T2` over `closeOnExit` - this row names the presentation half: a dead shell is indistinguishable from a quiet one |
| session list - `TermuxSessionsListViewController`'s drawer (list, add, rename via long-press) | the running panel's Terminals section | adapted | verdicted at `AUD-006` `T4` as `running-extension`'s surface: the acts are the same, the container differs |
| mouse/pen reach - `onTouchEvent`'s `SOURCE_MOUSE` routing into `sendMouseEvent`, stylus/mouse capture, `isMouseTrackingActive` gates on scroll/select | the drag detector is the only pointer path; no event ever consults the stored mouse-mode bits | **absent** | `GAP-087` names the protocol half; this row names the pointer half - even an encoder landing tomorrow has no gesture feeding it today |

As at `T3`, the disagreements stay named rather than averaged: scrollbar-and-wheel chrome is
JediTerm's answer where Termux uses `Scroller`-fling - both are `GAP-090`; blink is JediTerm's
`BlinkingTextTracker` where Termux ties it to `cursorBlink` - both stay declined; selection is a
gap on every front (`GAP-081`); the `\r` rewrite and `invertCursorTextColor` are *not*
Termux-only - JediTerm encodes `VK_ENTER` to `Ascii.CR` in
`TerminalKeyEncoder` and its `drawCursor` repaints the cell's char under `getInversedStyle` - so
both defects were ours alone.

### The walk

Eight paths through the one port - a mint, an attach, a keystroke, a resize, an end, a covered
tab, a close, and the refused asks. **A mint:** the bar's "+" at one pane and the launcher's
Terminal card at two → `rememberNewTerminal`'s `scope.launch` → `openNewTerminal` →
`runCatching { terminalGateway.open(cwd.ifEmpty { null }) }` (the launcher card's own
`runCatching` lands on `LauncherViewModel.error` instead) → `ActivityTrackedTerminalGateway.open`
→ `BaseSpaceTerminalGateway.open` → `live.get()` - asking for a terminal is asking for the
runtime, so a stopped Space boots here - → `RealTerminalGateway.open` →
`OkHttpTerminalLifecycle.create` → `POST api/terminals` with `{}` or `{"cwd": dir}`, never
`{"cwd": null}` - → `TerminalRootHandler.post` → `NamedTermManager.new` minting `"%d"` and
spawning the pty → `200 {"name": "1"}` → `TerminalSession("1")` → `activeTerminals.minted("1")` →
`openTerminal("1")` → `openInMainArea` pushing `Terminal("1")` - a fresh name never matches an
old tab, so a second mint is always a second tab - → `entry<Terminal>` → `TerminalPaneScreen`.
**An attach, and what flows back:** `KeepTheShellAttached` →
`repeatOnLifecycle(Lifecycle.State.STARTED)` → `viewModel.attach()` →
`ActivityTrackedTerminalGateway.attach` - the `onEach` the fix below added - →
`BaseSpaceTerminalGateway.attach` → `live.get()` → `RealTerminalGateway.attach`, an
`AttachedTerminal` registered in the `attached` map as the flow is collected → `connect()` →
`OkHttpTerminalSocketOpener` upgrading `terminals/websocket/<name>` → `TermSocket.open` appending
the client, sending `["setup", …]` and replaying `read_buffer` → `TerminalFrame.read` per
message - `setup` → `Ready` (completing `ready`), `stdout` → `Output`, `disconnect` →
`Ended("the shell exited")`, anything unrecognised ignored - → `tryEmit` into the 256-deep
buffered `MutableSharedFlow`, on OkHttp's reader thread, a refusal logged as
`terminal.frame.dropped` → `upToEnd` stopping the flow on `Ended` → the ViewModel folding each
event: `Ready` → `uiState.ready`; `Output` → `emulator.append` + `topRow = 0` + `bumpRedraw` →
`key(state.redraw)` rebuilding `TerminalCanvas`, whose `drawRun`s repaint batched runs; `Ended` →
`endedReason`. What the emulator answers on its own - `TerminalReplies.reply`, the DA1/DSR
replies a program can ask for - goes back through `sendToShell` exactly as a keystroke does. The
`finally` removes the registration *by identity* and closes the socket, so an old attach's
cancel cannot unregister a newer re-attach. **A keystroke:** two routes into `sendToShell` -
soft-keyboard edits land in the invisible `BasicTextField` (`KeyboardType.Password`,
`autoCorrectEnabled = false`, focus requested at composition) → `snapshotFlow` →
`reportTerminalKeystroke` → `sendTerminalDiff` deleting back to the shared prefix as `\u007F`s
and typing the tail with `\n` rewritten to `\r`, the field cleared once its text ends in a
newline → `typeCharacters` → `applyModifierLatches` → `sendToShell`; a hardware key or an
extra-keys tap → `onPreviewKeyEvent`/`onKey` → `terminalKeystroke` → `type` - pre-encoded bytes,
no latch pass, which would corrupt an escape sequence one character at a time - → `sendToShell` →
the `endedReason` gate → `writingToTheShell { send }` → `write` waiting up to `ATTACH_TIMEOUT`
(10 s) for `attached[name]` to appear and `awaitReady` to complete, then `["stdin", text]` on the
socket → `TermSocket.on_message` → `ptyproc.write`. **A resize:** `TerminalCanvas`'s
`onGridSizeChanged` - `terminalGridSize` over `terminalCellMetrics`'s measured "X" - →
`viewModel.resize(grid, w, h)` → a `lastGridSize` dedup → `emulator.resize` reflowing the grid
plus `bumpRedraw` - and this half stays right on a dead shell, whose last screen still draws
correctly at a new size - → the `endedReason` gate → `writingToTheShell { resize }` →
`["set_size", rows, cols]` rows first, `self.size = command[1:3]` on the wire's own order - →
`resize_to_smallest` → `TIOCSWINSZ`. **An end:** `on_pty_died` sends `["disconnect", 1]` →
`Ended("the shell exited")`; a socket ending for any other reason - a network drop, the 404
`TermSocket.get` raises on a name a replaced server never minted - → `Ended(reason)`; either way
`upToEnd` completes, `endedReason` stores, and the pane stands on the shell's last screen
(`GAP-084`'s verdict) with input and wire-resizes gated off - while a `ready` that never
completed is completed *exceptionally*, so a writer that raced a pre-`setup` end fails rather
than suspending forever. **A covered tab:** the entry leaving the top of the scene caps its
lifecycle at `CREATED` → `repeatOnLifecycle` cancels the collect → the `finally` closes only the
client socket - `TermSocket.on_close` drops the client while `NamedTermManager` keeps the shell -
→ returning to the tab re-`STARTED`s and calls `attach()` again → `hasAttachedBefore` sends the
emulator `ESC[H ESC[2J ESC[3J` first - home the cursor, blank the screen, forget the scrollback,
because `read_buffer` replays the whole session rather than the missed delta (`CLOSED-007`) - → a
fresh `AttachedTerminal`, `setup`, the replay, and the pane redraws the session it left. The
active count is unmoved by the whole cycle: the shell lived throughout, which is exactly what the
count is for. **A close:** the running panel's ✕ → `closeTerminal` →
`ignoringFailure { terminalGateway.close(session) }` → `liveOrNull()?.close()` - ending a
terminal must never boot a runtime to do it - → `lifecycle.terminate` →
`DELETE api/terminals/<name>` → 204 and 404 both success, the second being "somebody got there
first" → `activeTerminals.ended(name)` → `relist()`. A tab ✕ is only navigation: the entry's
ViewModel clears, the attach's `finally` drops the socket, and the pty runs on -
`shutdownOnClose`'s verdicted adaptation, the panel's ✕ being the kill reach. **The refused
asks:** a refused `open` dies in `openNewTerminal`'s `runCatching` - no terminal opened, not a
crash (`aRefusedTerminalOpenLeavesTheStackAlone`) - or lands on the launcher card's `error`
surface; a refused attach is `Ended` carrying the reason - a boot refusal
(`attach_endsWithTheReasonWhenTheServerCannotStart`) or the 404
(`attach_toATerminalTheServerDoesNotHaveEndsWithTheReason`); a write to a terminal nobody
attached suspends up to `ATTACH_TIMEOUT` then throws `TerminalChannelException`, as does a send
on a socket already ending - both landing in `writingToTheShell`'s swallow
(`aKeystrokeOnAnEndedShellReachesNoSocket`, `typingOnAClosedShellDoesNotThrow`).

What the walk found, beyond the rows above:

- **Growth is one redraw per frame and a ring that overwrites - bounded by construction.** Each
  `stdout` frame is one `Output` → one `emulator.append` → one `redraw` bump → one `key()`
  rebuild of the canvas; the canvas's own run batching is per-paint, so output volume changes
  repaint frequency, never per-repaint cost. Scrollback is `RowRing` capped at
  `DEFAULT_TRANSCRIPT_ROWS = 1000` - overwrite, not allocation - and a resize reflows by replay
  (`GridReflow`), deduplicated by `lastGridSize` so a redraw that changes nothing writes the
  wire never. The one place output *can* be lost is named on `publish` itself: `tryEmit` into a
  256-frame buffer means a collector slower than the socket drops frames - logged as
  `terminal.frame.dropped` rather than backpressured, because the producer is OkHttp's reader
  thread and must never block. `ActiveTerminals` is now a set of names, so a name a later server
  generation re-mints counts once. And the panel's `relist` is two passive asks behind
  `keepingLastGood` - `running()` answers through `liveOrNull`, so the poll never boots what it
  lists.
- **Branching splits on who may boot the runtime, and on which end an `Ended` was.**
  `open`/`send`/`resize`/`attach` go through `live.get()` - asking is asking for the runtime;
  `running`/`close` go through `liveOrNull()` - listing and ending never boot one.
  `BaseSpaceTerminalGateway.attach` splits a refused boot (`Ended` carrying it) from the
  delegate's own refusals (the 404) from readiness (`setup` → `Ready`). `write` splits
  attachable-in-time from `TerminalChannelException` twice over - no attach within 10 s, and a
  socket that will not take a frame. The ViewModel splits `endedReason` the reference's own way
  (`_isDisposed`): wire writes drop, while the emulator half of `resize` keeps running because a
  dead shell's last screen is still the right thing to draw at a new size. The scroll gesture
  splits on `isAlternateLive` - arrows to the program where there is no scrollback to move.
- **Failure is answered at the surface it belongs to - and the walk found the one boundary
  where it was not.** `ActiveTerminals` released a hold only on `close()`: a shell that exited -
  `disconnect`, `on_eof` popping it from `NamedTermManager`'s map - or a name lost when the
  server was replaced kept the count positive forever, and `IdleServerStopper`'s
  `combine { both zero }` cannot fire while it stays positive: a dead shell held a live server
  open. `Ended` could not decrement on its own - `TermSocket.on_close` means the pty may live -
  so `b176c260` made the belief a set of names reconciled to `running()`'s answer: asked once on
  every attach's `Ended`, re-aligned by every `running()` call, removed idempotently by `close`.
  Red first: `anEndedAttachReconcilesAwayTheDeadShell`, `aSocketEndOnALiveShellKeepsItsHold`,
  `aShellEndedElsewhereDropsOnTheNextList`, `anEndedObservedAfterCloseStillCountsOnce`, with
  `FakeTerminalGateway.detach` modelling the socket-ended-pty-lived half and
  `ActiveTerminalsTest` pinning the set semantics. It is the audit's sixth terminal defect after
  `T2`'s three and `T4`'s two. The failure shapes the walk confirmed rather than repaired:
  `TerminalFrame.read` swallows malformed and unrecognised frames; `writingToTheShell` lands a
  refused write the reference's own `_isDisposed` way; `ready.completeExceptionally` turns a
  pre-`setup` end into an error rather than a forever-suspend.
- **Coupling is the walk's real yield - who owns the truth about whether a shell is alive.**
  `running()` is now doubly authoritative: it draws the panel's rows *and* re-aligns the active
  count, so one ask keeps both honest and every panel refresh is also a reconcile.
  `ActivityTrackedTerminalGateway` is the count's only mover and `IdleServerStopper` only reads
  `combine(cells, terminals)` - the wrapper sitting outside `BaseSpaceTerminalGateway`
  deliberately, since that class swaps its delegate per `PerRunningServer` generation and a
  counter inside it would reset on every replacement. A replaced server's minted names reconcile
  away at the next list rather than at a signal the port does not carry - which is the same
  answer `BaseSpaceTerminalGateway`'s KDoc gives for carrying no session registry: the wire
  already reports the truth, and `running()` is where it is asked. The residual the fix accepts:
  a shell that exits while *nothing* is attached and nobody lists stays counted until the next
  `running()` - bounded, failing safe (a server kept, never a live shell dropped), and the same
  question the reference's `current_activity()` answers, only asked client-side. `endedReason`
  stays the pane's one error channel where JupyterLab has `connectionFailed`, `_isDisposed` and
  `closeOnExit`; the presentation half is `GAP-084`'s, unchanged by the fix.

## Rendering and formats — `AUD-008`

**The surface is seven packages' whole command surface plus the five `notebook:` ids this slice
already reserved — and a machinery reach no earlier slice has had: the registry every renderer
plugs into, the document viewers, the output area and the format itself.**
`rendermime-extension` declares `rendermime:handle-local-link`; `markdownviewer-extension`
`open`/`edit`/`trust`; `htmlviewer-extension` `trust-html` — in `index.tsx`, the package's only
source file, which is why the produced grep reads `*.tsx` beside `*.ts`; `mathjax-extension`
`clipboard`/`scale`, mounted by a `schema/plugin.json` that is a `.MathJax` context-menu
contribution rather than a settings schema; `imageviewer-extension` the eight transform acts;
`csvviewer-extension` two families at once (`csv:`/`tsv:go-to-line`); `mermaid-extension`
`copy-source`. Verified: no other package under `packages/` declares an id in any of these
families. Produced, never stored:

```sh
for p in rendermime-extension markdownviewer-extension htmlviewer-extension mathjax-extension \
         imageviewer-extension csvviewer-extension mermaid-extension; do
  echo "== $p"; python3 scripts/read_reference_commands.py --jupyterlab-package "$p"
done
grep -rhoE "'(rendermime|markdownviewer|htmlviewer|mathjax|imageviewer|csv|tsv|mermaid):[A-Za-z0-9-]+'" \
  reference/research_repos/jupyterlab/packages/*/src --include='*.ts' --include='*.tsx' | sort -u
for f in reference/research_repos/jupyterlab/packages/{markdownviewer-extension,htmlviewer-extension,mathjax-extension,imageviewer-extension,csvviewer-extension}/schema/*.json; do
  echo "== $f"; python3 -c "import json;print(sorted(json.load(open('$f')).get('properties',{})))"
done
```

**This slice meets two kinds of reference.** The behaviour spec is JupyterLab's command surface
plus `packages/rendermime/` — `RenderMimeRegistry` (`preferredMimeType` walking `defaultRank`,
`createRenderer` wiring the five plugs: `resolver`, `sanitizer`, `linkHandler`,
`latexTypesetter`, `markdownParser`), `standardRendererFactories` (html, the five images, latex,
markdown, svg, console text and a JavaScript **placeholder**), `mimemodel`/`outputmodel`/
`attachmentmodel` — plus `outputarea` (`OutputArea`, `SimplifiedOutputArea`, `OutputPrompt`,
`Stdin`, `display_id` routing and `isolated` metadata in `widget.ts`; the collapser is
`cells/src/collapser.tsx`) and `nbformat` itself. The format spec is met twice —
`packages/nbformat/`'s TS types (`MAJOR_VERSION` 4, `MINOR_VERSION` 4) and the shipped rootfs's
`nbformat` 5.10.4 (`validator.py`, `reader.py`, `v4/{nbjson,nbbase,convert,rwbase}.py`) with
`nbconvert` 7.17.1's exporters behind `jupyter_server/nbconvert/handlers.py`'s
`api/nbconvert/<format>/<path>?download=true`, the wire ours already mirrors. The document
surfaces ours falls back to plain text for are `markdownviewer` (six settings — `fontFamily`,
`fontSize`, `lineHeight`, `lineWidth`, `hideFrontMatter`, `renderTimeout`), `htmlviewer` (an
explicitly sandboxed iframe; `toolbar`, `trustByDefault`), `csvviewer` (`toolbar` per schema and
`searchprovider.ts`'s document-search registration), `imageviewer` and `mermaid`
(`mermaid/src/{mime,markdown}.ts` render inside outputs and markdown, not just documents).
IntelliJ's `platform/jewel/markdown` is the Compose-native precedent for AST → blocks →
composables — the one reference shaped like ours — and VS Code's `cellRenderer`/
`backLayerWebView` is read for how a non-JupyterLab client renders outputs: behind a second
process boundary JupyterLab does not have.

```sh
ls reference/research_repos/jupyterlab/packages/{rendermime,outputarea,nbformat}/src
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/nbconvert/handlers.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/nbformat/validator.py
```

**Ours** is `markdown/` — `MarkdownDocument` over commonmark-java's AST into `RichBlock`/
`RichInline`, producing no HTML anywhere — `html/` — `HtmlDocument` over jsoup into the same
model — `richtext/` with `ui/editor/{RichText,RichTable}` the drawing, `ui/editor/RenderedOutput`
picking `text/html` → `image/png` → `text/plain` off each `CellOutput` and
listing bundle keys for anything else, `data/disk/` — `NotebookFile`/`NotebookJsonWriter`/
`NotebookOutputReader`/`NotebookOutputWriter`/`MimeBundleLines` plus `PreservedJson`, nbformat 4
read/write with unknowns carried through — and the export half `domain/port/NotebookExporter` +
`data/space/{BaseSpace,OkHttp}NotebookExporter`/`NbConvertResponse` → `ui/editor/NotebookExport`
→ `DocumentSharer`. Produced:

```sh
python3 scripts/read_port_surface.py            # NotebookExporter 2, DocumentSharer 1
python3 scripts/read_port_surface.py --unused
```

**Carried in, recorded not rejudged.** `GAP-013`'s comm-channel absence lands here as the missing
widget-view renderer; `GAP-014`'s `allow_stdin: false` as the missing `Stdin`; `GAP-017` is what
`notebook:copy-to-clipboard` is measured against; `GAP-023` is the format half's open wound.
`GAP-019` was verdicted `absent` under `AUD-002` `T2b` — `render-all-markdown` sets cell state,
not rendering — and is recorded, not rejudged. Five ADRs already carry this area's decisions:
`ADR-0009` (kernel logging out of output), `ADR-0021` (`PreservedJson`), `ADR-0035` (unrendered
≠ asked-for), `ADR-0039` — the load-bearing one, Compose rather than a WebView, the deviation
every renderer verdict rests on — and `ADR-0051` (the server names the export). The partition
rows are in the shared table under `AUD-002`'s heading — all 23 ids to `T2` (the command
surface); `T3` verdicts the rendermime half, `T4` the output area and the format, `T5` the walk.

### The command surface — verdicts

`T2`'s entries, read down every `addCommand` in the seven packages and the five `notebook:` ids
this slice carried. Theirs: `rendermime-extension`'s link dispatch (`index.ts:68-125` —
`contents.get` → `defaultRenderedWidgetFactory` → `openOrReveal` + `setFragment`),
`markdownviewer-extension`'s trio on the preview factory (`:122-230`), `htmlviewer-extension`'s
trust toggle on a sandboxed iframe that `defaultFor: ['html']` makes the `.html` tap's landing
(`index.tsx:113-208`), `mathjax-extension`'s pair behind a `.MathJax` context-menu node
(`index.ts:135-196`, `schema/plugin.json`), `imageviewer-extension`'s eight transform mutators
(`index.ts:203-297`), `csvviewer-extension`'s delimiter-twinned go-to-line (`index.ts:219-248`,
`:383-412`) beside `searchprovider.ts`, `mermaid-extension`'s source-copy off a rendered node
(`index.ts:80-102`) — and `notebook-extension`'s export trio (`exportToFormat` `:694-747`, the
submenu's guidance fallback `:809-833`, `FORMAT_EXCLUDE` `:375`), its `ClonedOutputArea` sibling
(`:1751-1824`, `:5603-5630`), its DOM-element copy (`:2078-2113`) and its trust dialog
(`:3223-3244` → `actions.tsx:2504-2560`). Ours: `DocumentOpeners`'s suffix split — `.ipynb` to
the notebook editor, everything else to `FileEditorScreen`'s one text field (`reveal:
MatchedLine` its only line-landing machinery) — `RenderedOutput`/`CellOutputsView` (three mimes
ranked off `defaultRank`, plain `Text`, nothing selectable), `RichInlineString`'s
`RICH_LINK_TAG` written by `appendLink` and read only by `RichInlineStringTest`,
`NotebookExport`/`OfferedExportFormat`/`ExportStatus` into `DocumentSharer`, and
`HtmlInlines`'s `IGNORED_TAGS` — the standing answer every trust row rests on. A `.png` tap
lands in the same field as a `.md`: `DiskWorkspaceRepository.readText` decodes leniently, so
binary opens as editable mojibake — the reference's text editor shares that failure mode, but
there a factory claims the file first.

No defect surfaced: the sharp edges the read found are absence-shaped, and the GAPs record them
with their repairs. Eight GAPs open — `GAP-093` through `GAP-100`, with `GAP-017` cited on the
one row it already owns. No `undocumented`: every difference the rows state has its reason in a
comment or ADR already.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `rendermime:handle-local-link` — the registry's `linkHandler` mounts a click listener on every rendered `<a>`: `contents.get(path)` → `openOrReveal` in the `defaultRendered` factory + `setFragment(id)`; `scope: 'kernel'` delegates to `debugger:open-source` (`index.ts:68-125`, `:137-166`) | `RichInline.Link` carries `destination` and `appendLink` stores it under `RICH_LINK_TAG`; `RichInlineStringTest` is the annotation's only reader — a tap on a rendered link reaches nothing | **absent** | `GAP-093`. Machinery without reach, `GAP-081`'s class: the destination is kept and nothing follows it — no workspace open, not even a browser hand-off |
| `markdownviewer:open` — "Markdown Preview": delegates to the document manager's open with the rendered factory, which `defaultRendered: ['markdown']` also makes `handle-local-link`'s landing for a `.md` (`index.ts:152-181`; the file editor's own `defaultFor` outranks it for a plain tap) | `.md` → `FileEditorScreen`'s text field; `MarkdownDocument` renders only inside a notebook cell | **absent** | `GAP-094`. The capability is the *rendered* document — the file *is* shown, which is `markdownviewer:edit`'s row, not this one's |
| `markdownviewer:edit` — "Show Markdown Editor": re-opens the preview's path with the `'Editor'` factory split-right, visible only from a current preview (`index.ts:183-211`) | the editor is the only surface a `.md` ever lands in — the suffix split sends it there unconditionally (`DocumentOpeners.kt`) | adapted | the same destination by the only route that exists; a toggle presupposes the preview `GAP-094` records |
| `markdownviewer:trust` — `markTrusted` on the preview's node, arming the `jupyter:` command links rendered markdown can carry (`index.ts:213-230`; `data-trust-command` `:138`) | no rendered markdown document exists, and no command-link machinery anywhere — `GAP-093` | **absent** | `GAP-094`'s other half — there is nothing for trust to mark. On a surface that *does* render, trust reads as `notebook:trust` below |
| `htmlviewer:trust-html` — toggles `widget.trusted` → the iframe sandbox's `allow-scripts`, `isToggled` reading `sandbox` (`index.tsx:180-208`); the whole `HTMLViewer` surface — `defaultFor: ['html']`, `UNTRUSTED_LINK_STYLE`'s painted warning, `trustByDefault` — stands behind it | `.html` → the same text field; no iframe, no sandbox, and `HtmlDocument`'s subset is what `text/html` *outputs* get, not documents | **absent** | `GAP-095`. The package's entire surface is missing, so the row records the viewer too: nothing renders the document, so there is nothing to trust — the `notebook:trust` row's by-construction answer |
| `mathjax:clipboard`, `mathjax:scale` — `contextMenuHitTest` on a `.MathJax` node: copy the TeX source it keeps, or rescale every typeset document (`index.ts:135-196`, mounted by `schema/plugin.json`'s context-menu contribution, not a settings schema) | nothing typesets: a `$…$` in markdown draws as literal text — `MarkdownDocument` has no math node to map | **absent** | `GAP-096`. Both acts presuppose a rendered equation; `text/latex` and the `ILatexTypesetter` plug are `T3`'s read and cite the same gap |
| `imageviewer:zoom-in`, `imageviewer:zoom-out`, `imageviewer:reset-image`, `imageviewer:rotate-clockwise`, `imageviewer:rotate-counterclockwise`, `imageviewer:flip-horizontal`, `imageviewer:flip-vertical`, `imageviewer:invert-colors` — `scale`/`rotate`/`flip`/`colorinversion` mutators on the tracker's current `ImageViewer`, `isEnabled` on it being foreground (`index.ts:203-304`) | `.png` → the text field: `readText` decodes the bytes leniently, so the file shows as mojibake in an *editable* field — and a save writes the lossy decoding back | **absent** | `GAP-097`. No image surface exists for the eight acts to mutate; the fallback is worse than empty, since an edit corrupts the file — the GAP's repair names the refuse/read-only half as well as the viewer |
| `csv:go-to-line`, `tsv:go-to-line` — `InputDialog.getNumber` → `CSVViewer.goToLine`, one registration per delimiter, plus `editMenu.goToLiners` (`index.ts:219-248`, `:383-412`); `searchprovider.ts` registers each grid for document search | `.csv`/`.tsv` → the text field; `reveal: MatchedLine` can *land* on a line — a search result uses it — but nothing asks for a line | **absent** | `GAP-098`. The landing machinery exists without the reach, and the grid viewer, its delimiter toolbar and the search registration do not — the last is `GAP-036`'s family, noted not re-gapped |
| `mermaid:copy-source` — context-menu on a rendered diagram copies the source its `MERMAID_CODE_CLASS` node keeps (`index.ts:80-102`) | no rendered mermaid anywhere — a ` ```mermaid ` fence in a markdown cell draws as a plain code block | **absent** | `GAP-099`. The diagram surface — mime factory, markdown fence, document widget — is `T3`'s read and cites the same gap; the copy reach itself would still need `GAP-017`'s selection story |
| `notebook:trust` — "Trust Notebook": warn dialog → `NotebookActions.trust` marks every cell `trusted` → save (`index.ts:3223-3244`; `actions.tsx:2504-2560`), and trust is what lets outputs render *un*sanitized | no `trusted` flag is read anywhere; `HtmlInlines`'s `IGNORED_TAGS` drops `script`/`style`/`head`/`title`/`meta`/`link` whatever the metadata says, and `PreservedJson` round-trips the flag unread | adapted | the subset parse is the sanitizer: nothing rendered can execute, so the flag gates nothing — the deviation the PRD names, resting on ADR-0039's no-WebView model and `HtmlInlines`'s stated rule |
| `notebook:export-to-format` — one submenu row per server-listed format minus `FORMAT_EXCLUDE` → save if dirty → `nbconvert.exportAs` → `window.open` download; HTML asks "Sanitize HTML output" where the server is new enough (`index.ts:694-747`, `:805-850`, `:375`) | the same act one surface over: overflow row → `pickerOpened` → server formats ∩ `OFFERED_EXPORT_FORMATS` (the measured four) → `exportAs` flushes the debounce → `NotebookExporter.export` → `writeBytes` beside the notebook → the report snackbar's Share → `DocumentSharer` | adapted | every departure written: the artifact lands in the workspace since Android has no download (`NotebookExport`), the four-row menu was measured on the tablet (`OfferedExportFormat`, ADR-0051), `python` stands in for `script`, and `sanitize_html` is never asked — the reference's default-`true` prompt has no reach here |
| `notebook:show-export-guidance` — "Enable notebook exports", the submenu's only row when the server lists no formats → the guidance dialog or the docs (`index.ts:775-790`, `:817-822`) | `NO_EXPORT_FORMATS` — a disabled "No export formats are available" row, quoting the reference's own wording for the same state (`index.ts:938`) | adapted | the same state surfaced in place rather than a guidance act — `NotebookActionsMenu`'s comment states it, and a docs link has no surface to open on |
| `notebook:copy-to-clipboard` — copies the output's rendered DOM element, context-menu on `.jp-OutputArea-child` (`index.ts:2078-2113`) | outputs are plain `Text`, unselectable — `GAP-017` measured on the tablet | **absent** | `GAP-017`. Already recorded as the selection story; this row measures the command against it — a copy reach has nothing to copy |
| `notebook:create-output-view` — `ClonedOutputArea`: a live second widget over one cell's output *model*, split-bottom sibling, disposed with the notebook (`index.ts:1751-1824`, `:5603-5630`) | outputs draw inline only; there is no output-model/surface split a second view could share | **absent** | `GAP-100`. The clone is another widget on the same model — ours has the model inside `CellOutput` with no second surface to mount |
| `markdownviewer`'s six (`fontFamily`, `fontSize`, `lineHeight`, `lineWidth`, `hideFrontMatter`, `renderTimeout`), `htmlviewer`'s `toolbar`/`trustByDefault`, `csvviewer`'s `toolbar` per delimiter (`schema/*.json`); `imageviewer`'s schema declares no `properties`; `mathjax`'s `plugin.json` is a menu contribution, not settings | rendered content takes `MaterialTheme` typography and `editorPalette`, not per-document config; `EditorSettings.codeFontSizeSp` sizes the *fields*, not the rendering | adapted | declined where each one's surface is — `GAP-094`/`GAP-095`/`GAP-098`; `trustByDefault`'s answer is `notebook:trust`'s row, and `renderTimeout` presupposes a render that can time out |
