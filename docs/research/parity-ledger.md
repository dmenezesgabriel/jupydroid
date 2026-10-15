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
| Terminal emulator and view | `AUD-007` | examined — below |
| Rendering and formats | `AUD-008` | examined — below |
| Chrome and presentation | `AUD-009` | examined — below |
| Spaces and runtime | `AUD-010` | examined — below |
| Code health | `AUD-011` | examined — below |

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
| stdin `input_request` / `input_reply` | `allow_stdin: true` — `JupyterMessage.executeContent`; `KernelOutputs.inputRequestOf` reads the request, `KernelGateway.replyInput` → `JupyterMessage.inputReply` answers with the request's header as `parent_header` | present | as the reference sends it (`packages/services/src/kernel/default.ts:801`, `sendInputReply`); the prompt is drawn in the output area ([ADR-0082](../adr/0082-a-cells-input-is-answered-inline-and-kept-as-a-stdin-echo.md)) |

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
- **`allow_stdin` was the one place the wire was narrowed by a constant**, and it was why
  `GAP-014` was a one-line change in the request and a whole feature in the UI: the kernel cannot ask
  for input unless the client offers to answer. Both halves shipped together
  ([ADR-0082](../adr/0082-a-cells-input-is-answered-inline-and-kept-as-a-stdin-echo.md)); the gap is closed.
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
| `imageviewer:zoom-in`, `imageviewer:zoom-out`, `imageviewer:reset-image`, `imageviewer:rotate-clockwise`, `imageviewer:rotate-counterclockwise`, `imageviewer:flip-horizontal`, `imageviewer:flip-vertical`, `imageviewer:invert-colors` | `AUD-008` `T2` | The image document surface's eight transforms against what ours draws when a `.png` is tapped |
| `csv:go-to-line`, `tsv:go-to-line` | `AUD-008` `T2` | `csvviewer-extension` carries two families — the table viewer's go-to-line per delimiter |
| `mermaid:copy-source` | `AUD-008` `T2` | The diagram surface's one id — copy the mermaid source a rendered diagram was drawn from |
| `notebook:toggle-render-side-by-side-current`, `notebook:set-side-by-side-ratio`, `notebook:toggle-virtual-scrollbar` | `AUD-009` `T3` | Layout of the notebook pane. §8.3 makes VS Code authoritative for presentation, so the comparison is that slice's |
| `notebook:toggle-all-cell-line-numbers` | `AUD-009` `T3` | The gutter — `EditorSettings.lineNumbersShown`, drawn by `LineNumbers.kt`, and presentation rather than a cell operation |
| `notebook:create-new` | `AUD-009` `T3` | Added to the palette on its own. Creating a document is chrome, not a cell command |
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
| `recentmenu:open-recent`, `recentmenu:reopen-last` | `AUD-005` `T4` | The File menu's recents pair, declared in `mainmenu-extension` but already judged in `T4`'s recents read — `open-recent` adapted to `RecentsSection`, `reopen-last` absent with `GAP-066`. Ownership lands where the verdicts already live |
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
| `application:close` | `AUD-006` `T3` | `filemenu:close-and-cleanup`'s fallback — the semantic delegate executes this when no `*:close-and-shutdown` registrant is current — judged in the same row. Closes what the shell shows; declared in `application-extension` but this slice already examined it |
| `running:show-panel`, `running:show-modal` | `AUD-006` `T4` | Reaching the panel — the rail entry versus the searchable-sessions dialog (`SearchableSessions`) |
| `running:kernel-open-session`, `running:kernel-shut-down`, `running:kernel-shut-down-unused` | `AUD-006` `T4` | The sessions section's row acts — open, per-row shutdown, and the grouped-list's "Shut Down Unused" |
| `running:kernel-new-console`, `running:kernel-new-notebook` | `AUD-006` `T4` | Minting a document on a running kernel — the kernel row's two spawn acts (`kernels.tsx`) |
| `terminal:create-new`, `terminal:open`, `terminal:open-folder-in-terminal` | `AUD-007` `T2` | Reaching a terminal — `create-new` mints (the launcher's card), `open` reveals a running one by name, `open-folder-in-terminal` lands a folder's `cwd` on `TerminalGateway.open` |
| `terminal:copy`, `terminal:copy-link`, `terminal:paste` | `AUD-007` `T2` | The pane's clipboard — copying the selection `GridSelection` holds, a link under the cursor, and what a paste hands `send` |
| `terminal:increase-font`, `terminal:decrease-font`, `terminal:set-theme`, `terminal:refresh` | `AUD-007` `T2` | The pane's own presentation — the font pair (`TerminalActionsMenu` over `TerminalSettings`), the theme pick, and re-fit/re-send-size weighed against a layout that already resizes |
| `terminal:shut-down` | `AUD-007` `T2` | Ending one — `TerminalGateway.close`; the running panel's ✕ for it is `AUD-006` `T4`'s, verdicted above |
| `application:activate-next-tab`, `application:activate-next-tab-bar`, `application:activate-previous-tab`, `application:activate-previous-tab-bar`, `application:close-all`, `application:close-other-tabs`, `application:close-right-tabs`, `application:move-widget`, `application:reset-layout`, `application:reset-widget-zoom`, `application:set-activity-bar-position`, `application:set-mode`, `application:split-tab`, `application:toggle-context-menu`, `application:toggle-down-area`, `application:toggle-fullscreen-mode`, `application:toggle-header`, `application:toggle-left-area`, `application:toggle-mode`, `application:toggle-presentation-mode`, `application:toggle-right-area`, `application:toggle-side-tabbar`, `application:toggle-sidebar-widget`, `application:zoom-in-widget`, `application:zoom-out-widget` | `AUD-009` `T2` | The shell's own layout acts — area toggles, tab walks, splits, zooms, the presentation modes. Twenty are the typed declarations `T1` taught `COMMAND_ID` to see (`index.tsx:65-117`); `application:close` is `AUD-006` `T3`'s, judged already |
| `sidebar:switch`, `router:tree`, `property-inspector:show-panel` | `AUD-009` `T2` | Foreign families `application-extension` declares — the rail's tab switch, the URL-tree walk, the side panel's reveal — read with the shell that hosts them |
| `mainmenu:open-first`, `editmenu:open`, `filemenu:open`, `helpmenu:open`, `kernelmenu:open`, `runmenu:open`, `settingsmenu:open`, `tabsmenu:open`, `viewmenu:open` | `AUD-009` `T2` | The menu bar's openers — nine `*:open` acts plus the main menu's own, against a shell with no menu bar |
| `helpmenu:get-kernel`, `editmenu:clear-all`, `editmenu:clear-current`, `editmenu:go-to-line`, `editmenu:redo`, `editmenu:undo`, `runmenu:restart-and-run-all`, `runmenu:run`, `runmenu:run-above`, `runmenu:run-all`, `runmenu:run-below`, `tabsmenu:activate-by-id`, `tabsmenu:activate-previously-used-tab`, `viewmenu:line-numbering`, `viewmenu:match-brackets`, `viewmenu:show-minimap`, `viewmenu:word-wrap`, `filemenu:logout`, `filemenu:shutdown` | `AUD-009` `T2` | The menu delegates — `SemanticCommand`s forwarding to the current widget's registration and the View menu's settings rows; the acts they reach for are their registrants' slices' |
| `apputils:activate-command-palette`, `apputils:toggle-header`, `apputils:run-all-enabled`, `apputils:run-first-enabled`, `apputils:move-section-back`, `apputils:move-section-to`, `apputils:load-statedb`, `apputils:reset`, `apputils:reset-on-load` | `AUD-009` `T2` | The apputils shell acts — the palette's opener, the top-bar toggle, the enabled-command runners, the side-panel section movers, and the state-db restore/reset |
| `apputils:adaptive-theme`, `apputils:change-dark-theme`, `apputils:change-light-theme`, `apputils:change-theme`, `apputils:change-font`, `apputils:decr-font-size`, `apputils:incr-font-size`, `apputils:theme-scrollbars` | `AUD-009` `T3` | The presentation commands — the theme pickers, the font pair and family, scrollbar styling — against `theme/` and `ThemeSettings`/`CodeFontSize` |
| `launcher:create`, `settingeditor:open`, `settingeditor:open-json`, `settingeditor:save`, `settingeditor:revert`, `settingeditor:import`, `settingeditor:export` | `AUD-009` `T4` | The hosted surfaces' openers — the launcher's card and the settings editor's six, against `ui/launcher/` and `ui/settings/` |
| `shortcuts:add-keybinding`, `shortcuts:delete-keybinding`, `shortcuts:edit-keybinding`, `shortcuts:reset-all`, `shortcuts:toggle-selectors`, `apputils:display-shortcuts` | `AUD-009` `T4` | The keybinding editor's five and the shortcuts table the palette offers — against a shell with no user-editable bindings |
| `help:about`, `help:activate`, `help:close`, `help:hide`, `help:jupyter-forum`, `help:license-report`, `help:licenses`, `help:licenses-refresh`, `help:open`, `help:show` | `AUD-009` `T4` | The help surface — open/close/activate and the About/forum entries, plus the license-report trio it shares with `apputils`' |
| `workspace-ui:clone`, `workspace-ui:create-new`, `workspace-ui:delete`, `workspace-ui:export`, `workspace-ui:import`, `workspace-ui:open`, `workspace-ui:rename`, `workspace-ui:reset`, `workspace-ui:save`, `workspace-ui:save-as` | `AUD-009` `T4` | Workspaces — named layout snapshots against a shell whose one layout is fixed |
| `logconsole:add-checkpoint`, `logconsole:clear`, `logconsole:open`, `logconsole:set-level` | `AUD-009` `T4` | The log console — against logcat, which [ADR-0009](../adr/0009-logs-are-adbs.md) already makes the app's answer |
| `pluginmanager:open`, `pluginmanager:refresh`, `extensionmanager:show-panel`, `extensionmanager:toggle` | `AUD-009` `T4` | The plugin and extension manager panels — surfaces for managing plugins a Compose app does not load |
| `apputils:display-notifications`, `apputils:dismiss-notification`, `apputils:notify`, `apputils:update-notification`, `apputils:print`, `apputils:license-report`, `apputils:licenses`, `apputils:licenses-refresh` | `AUD-009` `T4` | The notification centre's four, print, and the license trio — chrome acts against a shell that shows state in place instead |

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
- **And one gap the walk records rather than repairs** — `GAP-023`, closed since by ADR-0089:
  `DiskNotebookRepository.saveNotebook` was a bare `File.writeText` where the reference stack's
  save is `atomic_writing`, so an interrupted write could leave half a notebook.

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
| `filebrowser:delete` — confirm → `manager.deleteFile` = `stopIfNeeded` + `contents.delete` + `deleteWidgets` on the file's contexts (`listing.ts:2537-2549`, `manager.ts:351-363`) | row menu → `DeleteFileDialog` → `delete` — permanent, refuses non-empty, unlinks a link; `Deleted` → the open document goes `gone`, and `DeletedDocumentSessions` shuts down the one session at the path | **adapted** | The ask, the permanence and the announced close are the reference's; `stopIfNeeded`'s rule is too, exactly one match and every refusal swallowed. The stop follows the delete rather than preceding it, heard on `WorkspaceRepository.changes`, so a delete the disk refuses costs no kernel — `DeletedDocumentSessions.kt`'s class doc states why. That was `GAP-058`, closed. |
| `filebrowser:shutdown` — shuts each session whose `session.path` sits among the *selected* rows (`listing.ts:654-674`, `index.ts:1797`) | — | absent | GAP-050 — selection-bound in the reference. The capability exists one surface over (a Running row names the file and owns `shutdownSession`), but no act on a file row reaches it. |
| `filebrowser:download` — `model.download` per selected non-directory, an anchor click on `getDownloadUrl` (`listing.ts:559`, `index.ts:526-543`) | — | absent | GAP-055. No served URL exists to hand a browser; `DocumentSharer` is the seam this ask would ride and only a notebook's export reaches it. |
| `filebrowser:copy-download-link` — `getDownloadUrl(selected)` → `Clipboard.copyToSystem`, hidden unless exactly one row is selected (`index.ts:544-566`) | — | absent | GAP-055. Same presupposed server URL; a link to a server nobody else can reach is not the shape sharing takes on Android. |
| `filebrowser:copy-path` — the workspace-relative path (or `serverRoot`-joined under `copyAbsolutePath`) → system clipboard (`index.ts:1761-1790`) | — | absent | GAP-055, whose text already names it. The path is a value the crumbs already show; the missing half is only the copy act on the row. |
| `filebrowser:open-url` — prompt → `fetch` → `model.upload` → `docmanager`'s `open` (`index.ts:1088-1140`) | — | absent | GAP-054. A file *in* — the URL's content is fetched then uploaded into the workspace — and nothing puts a file in. |
| `filebrowser:open-browser-tab` — per selected item, a `single-document` tree URL or `docmanager`'s `open-browser-tab`'s download URL → `window.open` (`index.ts:976-1010`) | — | absent | GAP-055. Both halves presuppose a server URL a browser can open; none exists. |
| `docmanager:new-untitled` — `contents.newUntitled({path,type,ext})` — the typed create every surface delegates to (`index.tsx:775-822`, `manager.ts:426-430`) | `NewMenu` → `create` → `createFile`/`createFolder`/`createNotebook` | adapted | The same create under the same server names. The notebook arm goes through `NotebookRepository` because an `.ipynb` is nbformat, not an empty file — `create`'s docstring states the split. |
| `docmanager:open` — `contents.get` then `openOrReveal` (`index.tsx:824-847`) | a tap → `openDocument` — `OpenDocuments.at` resolves the live path to the `openedAs` the key keeps — → `openNotebook`/`openFile` → `openInMainArea` | **defect** | found by the walk and fixed red-first: the reveal was key-equality only and a `NavKey` keeps the path it was opened under, so a document renamed beside its open editor re-opened as a *second* entry — two ViewModels, two autosaves on one file (`fbd21a99`, `NotebookBackStackTest.openingARenamedDocumentRevealsItInsteadOfOpeningItAgain`). The reference matches by the context's *current* path via `_contextsForPath` (`manager.ts:481-503`). |
| `docmanager:rename` — `dialogs.rename(context)`, bound on `document-title` context menus (`index.tsx:1428-1450`) | tab long press → the same `RenameFileDialog` → `rename`; the document adopts through `WorkspaceChange.Renamed` | present | `MainAreaTabStrip.kt:309` states the long press is the `document-title` menu collapsed to its one item. |
| `docmanager:delete`, `docmanager:delete-file` — ask → `deleteFile` = `stopIfNeeded` + `contents.delete` + `deleteWidgets` (`index.tsx:749-773,1473-1506`) | the ask+delete lives on the explorer row, never on the document's own tab; `Deleted` closes the document | absent | GAP-059 for the surface. The `stopIfNeeded` half is ours: a delete from the explorer row ends the session at the path (`DeletedDocumentSessions`), as the open document hears its file go (the `deleteWidgets` half). |
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
| `docmanager:save` — `context.save()` then `createCheckpoint()`, on Accel S, the File menu, the palette and the notebook toolbar (`index.tsx:1105-1235`); `renameUntitledFileOnSave` prompts on an untitled first save (`:1155-1190`) | `ActiveTabSave`, the tab strip's trailing icon, calls the top tab's `OpenDocument.saveNow`; live only while that document is dirty, greyed otherwise; no checkpoint, no key | **adapted** | [ADR-0093](../adr/0093-a-document-is-saved-on-demand-from-the-tab-strip.md): on the strip, the one chrome a notebook and a file share, beside the dirty dot; enabled on VS Code's dirty rule rather than writable. |
| `docmanager:save-all` — sweeps every writable context (`index.tsx:1237-1268`) | — | **absent** | `GAP-061`. Only the top tab composes, so a save refused beneath it has no screen to ask on. |
| `docmanager:save-as` — a path prompt, `_maybeOverWrite` on a taken one, then `fileChanged` closes the old context and opens the new (`context.ts:295-333`, `index.tsx:1270-1318`) | — | **absent** | `GAP-063`. `rename` moves a file; nothing writes the document's bytes under a second name. |
| `docmanager:toggle-autosave` — `isToggled` reads `docManager.autosave`, the act writes the setting (`index.tsx:1330-1349`), offered as a palette item | the Settings row → `EditorSettings.autosaveEnabled` (`SettingsScreen.kt:138-139`) | **adapted** | A settings row rather than a palette command — the phone has no palette, and the row's own checked state is `isToggled`. `SettingsScreen.kt:320` names the command the row is. |
| `docmanager:restore-checkpoint` — `listCheckpoints` → a picker → `restoreCheckpoint` (`index.tsx:1020-1084`); the points are made by `save()` itself and `_maybeCheckpoint` at first open (`context.ts:528-540,841-860`) | — | **absent** | `GAP-062`. The shipped `filecheckpoints.py` serves the API, but no port names it and nothing ever creates one — so the picker would have nothing to list. |
| `docmanager:reload` — `context.revert()` re-reads the file and reinitialises the model, asked first when dirty (`index.tsx:968-1018`, `context.ts:676`) | `ActiveTabReload`, the tab strip's Refresh icon before Save, calls the top tab's `OpenDocument.reloadFromDisk`; live on any document, a dirty one asked "Reload Notebook from Disk" or "Reload File from Disk" with a warn Reload | **adapted** | [ADR-0096](../adr/0096-an-open-document-is-reloaded-from-disk-on-demand-from-the-tab-strip.md): on the strip, the one chrome a notebook and a file share, rather than the File menu; the reference's enablement and words. |
| `docmanager:recents` — the `IStateDB` key and its machinery: `{opened, closed}` lists, `addRecent`'s dedup-then-unshift, Contents-API validation on load, a 500 ms `Debouncer` on the write (`recents.ts:17-288`) | `RecentDocumentsRepository` → `DiskRecentDocumentsRepository` — `recent-documents.json` in `stateDir`, dedup+cap in `withMostRecent`, the same validation at `loadValidated` and on explorer refresh (`ExplorerScreen.kt:162`) | **adapted** | Client state in a file outside the workspace rather than in `IStateDB`, and written eagerly rather than debounced — the repository's docstring states both choices. The `closed` list the schema stores is `GAP-066`. |
| `docmanager:clear-recents` — empties `opened` and `closed`, enabled only while either is non-empty (`docmanager-extension/src/recents.ts:66-81`) | the Recents section's overflow → `RecentsViewModel.clear` → `clear()`; the section itself is gone while the list is empty (`RecentsSection.kt:53`) | **present** | Same act, same label — `RecentsActionsMenu.kt:57` cites it — and the same reachability: two lists there, one here is `GAP-066`'s difference, not this command's. |
| `autosave` (`plugin.json:113-118`, default true — gates `DocumentManager.autosave`, `manager.ts:96-126`) | `EditorSettings.autosaveEnabled`, default true, gating both halves of `DocumentAutosave` | **present** | The flag, at the schema's own default, over the mechanism it names — the field's docstring names the setting and the command. |
| `autosaveInterval` (`plugin.json:119-124`, default 120, unbounded) | `EditorSettings.autosaveIntervalSeconds` — the same 120, clamped 5–600 by `AutosaveInterval` | **adapted** | The bound is written (`EditorSettings.kt:204-213`): a touch stepper needs edges, and under 5 s the poll is a chore the 500 ms debounce does better. The fixed interval also declines `SaveHandler`'s `10 * duration` growth and `isConnectedCallback` pause — `DocumentAutosave`'s docstring carries that reason. |
| `confirmClosingDocument` (`plugin.json:125-130`, default false — on asks for a clean close too, `widgetmanager.ts:478-487`) | not carried — a dirty close always asks, a clean one never does | **adapted** | Pinned at the schema's own default; the reason is written on `MainAreaCloseGate`'s docstring. |
| `defaultViewers` (`plugin.json:137-146` — a file-type → widget-factory override, `index.tsx:297-315`) | `DocumentOpeners`' suffix split — one factory per type | **adapted** | No second factory exists for any type, so an override would have nothing to point at — `DocumentOpeners`' docstring says so. |
| `lastModifiedCheckMargin` (`plugin.json:131-136`, default 500 ms — bounds `_maybeSave`'s disk-vs-client comparison, `context.ts:741-790`) | `File.writeIfUnchanged` — `saveNotebook` and `writeText` are handed the `FileStamp` of the read and refuse a file that is not the one it describes | **adapted** | No margin, so no knob: any difference refuses unless the file already holds the bytes being written, since on a local disk 500 ms would hide a cell's write landing just after a save ([ADR-0090](../adr/0090-a-save-is-refused-when-the-file-changed-since-it-was-read.md)). |
| `maxNumberRecents` (`plugin.json:153-158`, default 10 — `maximalRecentsLength` truncates both lists, `recents.ts:63-77`) | `MAX_RECENTS = 10`, a domain constant | **adapted** | The schema's own default, not carried as a setting — the constant's docstring states the decline. |
| `renameUntitledFileOnSave` (`plugin.json:147-152`, default true — the first-save rename prompt, `index.tsx:1155-1190`) | — | **adapted** | [ADR-0093](../adr/0093-a-document-is-saved-on-demand-from-the-tab-strip.md): a document is named when it is created, so a first save has nothing untitled to rename. |
| the save machinery — `SaveHandler`'s debounce, interval re-arm, `10 * duration` growth, `isConnectedCallback` pause (`savehandler.ts:130-137`, `index.tsx:216-221`) | `DocumentAutosave` — 500 ms debounce plus a dirt-gated poll at the configured interval | **adapted** | Debounce and poll are the same; the growth and the pause answer a write that crosses a network, and this one's never leaves the disk — the class docstring states it. |
| the `closed` list — `addRecent(path, 'closed')` on `close-request`, read by `recentmenu:reopen-last` and the running panel (`widgetmanager.ts:336-403,428-441`) | — | **absent** | `GAP-066`. Only opens are recorded (`RecordOpening.kt`); a closed document is still-opened or forgotten. |
| per-entry removal — `removeRecent` (`recents.ts:124-129`), Forget on the running panel's rows (`running-extension/src/recents.ts:71-75`) | clear-all only (`RecentsActionsMenu`) | **absent** | `GAP-067`. `RecentRow`'s own no-per-row-act rationale predates weighing Forget. |
| recents surface — `recentmenu:open-recent` items in the File menu and the running panel's two sections (`mainmenu-extension/src/recents.ts`, `running-extension/src/recents.ts`) | `RecentsSection` at the explorer root — directories first, tap to open or navigate | **adapted** | A pinned section rather than a menu — the phone has no File menu and the explorer root is where a next-open starts; `RecentsSection`'s docstring and `inRecentsOrder` carry the shape. |
| VS Code `workbench.action.files.save` (`fileCommands.ts:497-540`), on the tab's context menu while the editor is dirty | `ActiveTabSave` on the tab strip, live while the top document is dirty | **adapted** | [ADR-0093](../adr/0093-a-document-is-saved-on-demand-from-the-tab-strip.md): the same dirty rule, on a visible icon rather than a long press nobody is shown. |
| VS Code `saveAll`, `saveAs` (`fileCommands.ts:497-540`) | — | **absent** | `GAP-061` and `GAP-063` — the same holes the `docmanager:` family left. |
| VS Code `workbench.action.files.revert` (`fileCommands.ts:565`) | `ActiveTabReload` on the tab strip | **adapted** | [ADR-0096](../adr/0096-an-open-document-is-reloaded-from-disk-on-demand-from-the-tab-strip.md): live on a clean document too, JupyterLab's rule — VS Code greys it there because its watcher re-reads one; ours has that watcher ([ADR-0097](../adr/0097-an-open-document-with-no-unsaved-work-follows-its-file.md)) and keeps the icon live, since it is how a dirty document takes the disk's version. |
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
  open, the same rule. What another *process* does to the workspace reaches an open document
  only as a write: `changes` is in-process and non-replay, so a cell's `os.remove` is invisible to
  an open document until its own write is refused for it
  ([ADR-0090](../adr/0090-a-save-is-refused-when-the-file-changed-since-it-was-read.md)), while a
  write to its file re-reads a clean one by itself (ADR-0097) and the strip's reload icon re-reads
  one when asked (ADR-0096). The defects the walk owes the ledger, all fixed
  red-first: **`docmanager:open` revealed by key equality, and a key keeps the path it opened
  under** — rename a file beside its open document and the next tap pushed a second editor, two
  autosaves on one file — `fbd21a99`,
  `NotebookBackStackTest.openingARenamedDocumentRevealsItInsteadOfOpeningItAgain` (the row above
  is re-verdicted `**defect**`); **an act landing mid-navigation re-listed the stale path** —
  `relistAfter`/`move` read `uiState.path` where `refresh` already reads `browsing`, cancelling the
  pending `goTo` — `f631fea2`, `aRename…`/`aDeleteFinishedMidNavigationRelistsWhereWeAreGoing`;
  **a landing listing retracted the overwrite question** — `listingOf`'s fresh state dropped
  `nameTaken`, so a poll tick answered the ask nobody answered — `068d08c9`,
  `aPollDoesNotRetractTheOverwriteQuestion`. One more failure found and not repaired here: a rename or
  overwrite refused on the *tab strip* landed nowhere at all; it now draws the browser's
  "Rename Error" dialog (`MainAreaTabStrip.kt`). That was `GAP-069`, closed.
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
  And the coupling the reference has is ours too: `_onFileChanged` patches the session's own
  record — `sessionContext.session?.setPath(newPath)`, `context.ts:551` — and
  `EditorViewModel.adopt` asks `KernelSessions.setPath` for the same, so a Running row names the
  file where it lives now. That was `GAP-068`, closed.

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
| `running:kernel-open-session` — docmanager:open on the session's path, console:open when `type === 'console'`; label is the session name or path basename, icon by type (`kernels.tsx:248-285`) | `openRunningSession` — `openDocument(path)` for a document row, a console row reveals its open tab (`Navigation.kt:172-185`) | adapted | The document arm is the same act; the console arm is reveal-only because `entry<Console>` draws from the single `consoleSession` holder — a console with no tab cannot be synthesised, which both the caller and `RunningSidePanel`'s doc state. A notebook's row follows its rename through `KernelSessions.setPath`, which closed `GAP-068`. |
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
widget-view renderer; `GAP-014`'s `allow_stdin: false` as the missing `Stdin` (closed since by ADR-0082); `GAP-017` is what
`notebook:copy-to-clipboard` is measured against; `GAP-023`, the format half's un-atomic save, is closed since by ADR-0089.
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
lands in the same screen as a `.md`: `DiskWorkspaceRepository.readStampedText` decodes strictly, so
a binary file opens as a tab that says it is not UTF-8 text and offers no field
([ADR-0091](../adr/0091-a-file-that-is-not-utf-8-is-refused-by-the-text-read.md)) — where
the reference has a factory claim the file first.

No defect surfaced: the sharp edges the read found are absence-shaped, and the GAPs record them
with their repairs. Eight GAPs open — `GAP-093` through `GAP-100`, with `GAP-017` cited on the
one row it already owns. No `undocumented`: every difference the rows state has its reason in a
comment or ADR already.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `rendermime:handle-local-link` — the registry's `linkHandler` mounts a click listener on every rendered `<a>`: `contents.get(path)` → `openOrReveal` in the `defaultRendered` factory + `setFragment(id)`; `scope: 'kernel'` delegates to `debugger:open-source` (`index.ts:68-125`, `:137-166`) | a link is a `LinkAnnotation.Url` handed to `LocalUriHandler`: a `#fragment` lands on its heading's cell (`HeadingLinkUriHandler`, `cellOfHeadingAnchor`), a workspace path is resolved against the notebook's directory, checked in its listing, then opened or browsed (`WorkspaceLinkUriHandler`), and `http`/`https`/`mailto` leave through `ExternalLinkOpener` (`RenderedLinkUriHandler`) | adapted | the same route, with the differences each class states: a miss is said on a snackbar where JupyterLab says nothing; a heading's cell is the landing, as the outline's jump is per cell; a `.md` opens the file editor — `GAP-094`; no `scope: 'kernel'`, since no debugger source viewer exists to delegate to |
| `markdownviewer:open` — "Markdown Preview": delegates to the document manager's open with the rendered factory, which `defaultRendered: ['markdown']` also makes `handle-local-link`'s landing for a `.md` (`index.ts:152-181`; the file editor's own `defaultFor` outranks it for a plain tap) | `.md` → `FileEditorScreen`'s text field; `MarkdownDocument` renders only inside a notebook cell | **absent** | `GAP-094`. The capability is the *rendered* document — the file *is* shown, which is `markdownviewer:edit`'s row, not this one's |
| `markdownviewer:edit` — "Show Markdown Editor": re-opens the preview's path with the `'Editor'` factory split-right, visible only from a current preview (`index.ts:183-211`) | the editor is the only surface a `.md` ever lands in — the suffix split sends it there unconditionally (`DocumentOpeners.kt`) | adapted | the same destination by the only route that exists; a toggle presupposes the preview `GAP-094` records |
| `markdownviewer:trust` — `markTrusted` on the preview's node, arming the `jupyter:` command links rendered markdown can carry (`index.ts:213-230`; `data-trust-command` `:138`) | no rendered markdown document exists, and no command-link machinery anywhere: a `jupyter:` link's scheme is not one `RenderedLinkUriHandler` follows | **absent** | `GAP-094`'s other half — there is nothing for trust to mark. On a surface that *does* render, trust reads as `notebook:trust` below |
| `htmlviewer:trust-html` — toggles `widget.trusted` → the iframe sandbox's `allow-scripts`, `isToggled` reading `sandbox` (`index.tsx:180-208`); the whole `HTMLViewer` surface — `defaultFor: ['html']`, `UNTRUSTED_LINK_STYLE`'s painted warning, `trustByDefault` — stands behind it | `.html` → the same text field; no iframe, no sandbox, and `HtmlDocument`'s subset is what `text/html` *outputs* get, not documents | **absent** | `GAP-095`. The package's entire surface is missing, so the row records the viewer too: nothing renders the document, so there is nothing to trust — the `notebook:trust` row's by-construction answer |
| `mathjax:clipboard`, `mathjax:scale` — `contextMenuHitTest` on a `.MathJax` node: copy the TeX source it keeps, or rescale every typeset document (`index.ts:135-196`, mounted by `schema/plugin.json`'s context-menu contribution, not a settings schema) | nothing typesets: a `$…$` in markdown draws as literal text — `MarkdownDocument` has no math node to map | **absent** | `GAP-096`. Both acts presuppose a rendered equation; `text/latex` and the `ILatexTypesetter` plug are `T3`'s read and cite the same gap |
| `imageviewer:zoom-in`, `imageviewer:zoom-out`, `imageviewer:reset-image`, `imageviewer:rotate-clockwise`, `imageviewer:rotate-counterclockwise`, `imageviewer:flip-horizontal`, `imageviewer:flip-vertical`, `imageviewer:invert-colors` — `scale`/`rotate`/`flip`/`colorinversion` mutators on the tracker's current `ImageViewer`, `isEnabled` on it being foreground (`index.ts:203-304`) | `.png` → the file editor, whose read refuses it: `readStampedText` decodes the bytes strictly, so the tab says the file is not UTF-8 text and offers no field, and nothing is saved over it | **absent** | `GAP-097`. No image surface exists for the eight acts to mutate; the fallback is a refusal that leaves the file's bytes alone ([ADR-0091](../adr/0091-a-file-that-is-not-utf-8-is-refused-by-the-text-read.md)), which was the first half of the GAP's repair — the viewer is the half left |
| `csv:go-to-line`, `tsv:go-to-line` — `InputDialog.getNumber` → `CSVViewer.goToLine`, one registration per delimiter, plus `editMenu.goToLiners` (`index.ts:219-248`, `:383-412`); `searchprovider.ts` registers each grid for document search | `.csv`/`.tsv` → the text field; `reveal: MatchedLine` can *land* on a line — a search result uses it — but nothing asks for a line | **absent** | `GAP-098`. The landing machinery exists without the reach, and the grid viewer, its delimiter toolbar and the search registration do not — the last is `GAP-036`'s family, noted not re-gapped |
| `mermaid:copy-source` — context-menu on a rendered diagram copies the source its `MERMAID_CODE_CLASS` node keeps (`index.ts:80-102`) | no rendered mermaid anywhere — a ` ```mermaid ` fence in a markdown cell draws as a plain code block | **absent** | `GAP-099`. The diagram surface — mime factory, markdown fence, document widget — is `T3`'s read and cites the same gap; the copy reach itself would still need `GAP-017`'s selection story |
| `notebook:trust` — "Trust Notebook": warn dialog → `NotebookActions.trust` marks every cell `trusted` → save (`index.ts:3223-3244`; `actions.tsx:2504-2560`), and trust is what lets outputs render *un*sanitized | no `trusted` flag is read anywhere; `HtmlInlines`'s `IGNORED_TAGS` drops `script`/`style`/`head`/`title`/`meta`/`link` whatever the metadata says, and `PreservedJson` round-trips the flag unread | adapted | the subset parse is the sanitizer: nothing rendered can execute, so the flag gates nothing — the deviation the PRD names, resting on ADR-0039's no-WebView model and `HtmlInlines`'s stated rule |
| `notebook:export-to-format` — one submenu row per server-listed format minus `FORMAT_EXCLUDE` → save if dirty → `nbconvert.exportAs` → `window.open` download; HTML asks "Sanitize HTML output" where the server is new enough (`index.ts:694-747`, `:805-850`, `:375`) | the same act one surface over: overflow row → `pickerOpened` → server formats ∩ `OFFERED_EXPORT_FORMATS` (the measured four) → `exportAs` flushes the debounce → `NotebookExporter.export` → `writeBytes` beside the notebook → the report snackbar's Share → `DocumentSharer` | adapted | every departure written: the artifact lands in the workspace since Android has no download (`NotebookExport`), the four-row menu was measured on the tablet (`OfferedExportFormat`, ADR-0051), `python` stands in for `script`, and `sanitize_html` is never asked — the reference's default-`true` prompt has no reach here |
| `notebook:show-export-guidance` — "Enable notebook exports", the submenu's only row when the server lists no formats → the guidance dialog or the docs (`index.ts:775-790`, `:817-822`) | `NO_EXPORT_FORMATS` — a disabled "No export formats are available" row, quoting the reference's own wording for the same state (`index.ts:938`) | adapted | the same state surfaced in place rather than a guidance act — `NotebookActionsMenu`'s comment states it, and a docs link has no surface to open on |
| `notebook:copy-to-clipboard` — copies the output's rendered DOM element, context-menu on `.jp-OutputArea-child` (`index.ts:2078-2113`) | outputs are plain `Text`, unselectable — `GAP-017` measured on the tablet | **absent** | `GAP-017`. Already recorded as the selection story; this row measures the command against it — a copy reach has nothing to copy |
| `notebook:create-output-view` — `ClonedOutputArea`: a live second widget over one cell's output *model*, split-bottom sibling, disposed with the notebook (`index.ts:1751-1824`, `:5603-5630`) | outputs draw inline only; there is no output-model/surface split a second view could share | **absent** | `GAP-100`. The clone is another widget on the same model — ours has the model inside `CellOutput` with no second surface to mount |
| `markdownviewer`'s six (`fontFamily`, `fontSize`, `lineHeight`, `lineWidth`, `hideFrontMatter`, `renderTimeout`), `htmlviewer`'s `toolbar`/`trustByDefault`, `csvviewer`'s `toolbar` per delimiter (`schema/*.json`); `imageviewer`'s schema declares no `properties`; `mathjax`'s `plugin.json` is a menu contribution, not settings | rendered content takes `MaterialTheme` typography and `editorPalette`, not per-document config; `EditorSettings.codeFontSizeSp` sizes the *fields*, not the rendering | adapted | declined where each one's surface is — `GAP-094`/`GAP-095`/`GAP-098`; `trustByDefault`'s answer is `notebook:trust`'s row, and `renderTimeout` presupposes a render that can time out |

### The rendermime half — verdicts

`T3`'s entries, read down the registry, its eight standard factories, the five plugs it wires,
the extension-mounted factories, and the three models — plus the two presentation references the
PRD names. Theirs: `RenderMimeRegistry` (`registry.ts` — `preferredMimeType` walking factory
ranks, the sanitizer/resolver/linkHandler/latexTypesetter/markdownParser plugs); `factories.ts`'s
eight — html 50, image/* 90, latex 70, markdown 60, svg 80 (`safe: false`), stderr 110,
text/plain+stdout 120, javascript 110 (`safe: false`); `renderers.ts`'s render functions with
`ansiSpan`, `autolink`, `handleUrls`, `headerAnchors` and typeset; `mimemodel.ts`,
`outputmodel.ts`, `attachmentmodel.ts`; `latex.ts`'s delimiter extract/restore; `widgets.ts`'s
`RenderedCommon`/`RenderedHTMLCommon`; the plugs — `apputils`'s `ISanitizer` over sanitize-html,
`markedparser-extension` (marked lazy-loaded, `gfm: true`, `highlight`, `blockRenderers`,
`getHeadingTokens`), `mathjax-extension`'s `MathJaxTypesetter`, `attachments/`'s
`AttachmentsModel`/`AttachmentsResolver`; and the extension-mounted factories
`rendermime-extension` consumes — `json-extension` rank 0, `vega5-extension` 57, `pdf-extension`
100, `javascript-extension`'s `ExperimentalRenderedJavascript`, `mermaid`'s `text/vnd.mermaid` +
`MermaidMarkdown`, the audio/video media factories — `nbconvert-css` a style bundle, no renderer.
Ours: `RenderedOutput`/`renderRichest`/`RANKED_MIME_TYPES` (three mimes off `defaultRank`, the
present-but-unusable fallthrough stated in its own KDoc), `HtmlDocument`/`HtmlInlines`/`HtmlTable`
(the jsoup subset that *is* the sanitizer), `MarkdownDocument` (commonmark-java → `RichBlock`),
`RichBlock`/`RichInline`/`RichInlineString` (the shared model), `RichText`/`RichTable` (the draw),
`CellOutputsView` (the container), `KernelOutputs`/`CellOutput`/`MimeBundle` (the wire and the
models), `MarkdownCell` (rendered by default, tap to edit).

```sh
ls reference/research_repos/jupyterlab/packages/rendermime/src
grep -n 'defaultRank\|mimeTypes\|safe' reference/research_repos/jupyterlab/packages/rendermime/src/factories.ts
grep -n 'checkWeb\|checkPaths\|ansiSpan\|autolink' reference/research_repos/jupyterlab/packages/rendermime/src/renderers.ts
grep -n 'class AttachmentsResolver\|attachment:' reference/research_repos/jupyterlab/packages/attachments/src/model.ts
grep -rn 'mimeTypes\|rank' reference/research_repos/jupyterlab/packages/{json,vega5,pdf,javascript}-extension/src reference/research_repos/jupyterlab/packages/mermaid/src
ls app/src/main/java/com/jupydroid/app/{markdown,html,richtext}
```

These rows carry no `family:id` — the command surface is `T2`'s above — so the produced counts
do not move; each `absent` still names the `GAP-NNN` ADR-0067 obliges. One defect surfaced: a
stream or `text/plain` that coloured itself kept its escapes — the same `[31m`-on-screen
wrongness `withoutAnsiColours` was written to remove on tracebacks — fixed red-first in
`f1bd2e05` (`render_stripsTheAnsiColoursOutOfAStream`, `render_stripsTheAnsiColoursOutOfPlainText`).
Eleven GAPs open — `GAP-101` through `GAP-111`, `GAP-106` closed since by `SgrText` (ADR-0083); `GAP-093`, `GAP-096` and `GAP-099` are cited
where the read lands on their ground rather than re-gapped. Fold calls the PRD left open:
granularity is per factory or mime family where one read covers them — `image/*`'s four non-png
arms, vega's four mimes, the jsonl trio, the media trio — since each shares a repair class; and
ANSI tracebacks verdict `absent` rather than `adapted` — the colour *rendering* was never built,
and stripping is the stated fallback for its absence, not the capability itself.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| the registry — `RenderMimeRegistry`'s `preferredMimeType`/`_ranks`/`factories`, safe-vs-any preference, and the six plugs it carries (resolver, sanitizer, linkHandler, latexTypesetter, markdownParser, translator) (`registry.ts`) | `RenderedOutput.RANKED_MIME_TYPES` — a fixed list, first-present-and-usable wins | adapted | a registry holds the types it has renderers for — ours is the ones we draw; the plugs have nothing to plug but the link handler, which is `LocalUriHandler` (resolver → `LocalRichImageResolver`, which answers an image's source and nothing else, typesetter → `GAP-096`, sanitizer — the subset parse *is* one, parser — commonmark-java is wired in directly); the unusable-payload fallthrough is `renderRichest`'s own stated departure, recorded in its KDoc |
| `ISanitizer`/`sanitizer.ts` — sanitize-html whitelists (~100 `allowedTags` incl. `img`/`audio`/`video`/`input`/`details`, attribute and style whitelists, `rel="nofollow"` transform, `getAutolink`); untrusted → sanitize, trusted → raw (`apputils/src/sanitizer.ts`) | `HtmlDocument`/`HtmlInlines` — the jsoup subset: `IGNORED_TAGS` drops `script`/`style`/`head`/`title`/`meta`/`link`, unmapped elements flatten to children, an attribute subset only | adapted | the subset parse is the sanitizer, and it is unconditional — no trusted path exists to bypass it, which is `notebook:trust`'s `T2` verdict and ADR-0039's no-WebView model |
| `text/html` — `htmlRendererFactory` rank 50 `safe: true` → `renderHTML`: sanitize unless trusted, `handleUrls` resolving every `src`/`href` and binding linkHandler, `hardenAnchorLinks`, typeset (`renderers.ts`) | `text/html` → `HtmlDocument.parse` → `RenderedOutput.Document` → `RichText`/`RichTable` | adapted | the safe-side render lands — tables, headings, lists, code all draw as `RichBlock`s; a link is followed as a markdown one is (`rendermime:handle-local-link` above); an `<img>`'s `src` resolves through `LocalRichImageResolver` as a markdown image's does (ADR-0088) |
| `text/markdown` — `markdownRendererFactory` rank 60 → `renderMarkdown` → `IMarkdownParser.render` → sanitize + `headerAnchors` (`createHeaderId`/`data-jupyter-id`, `setFragment` scroll) + typeset | `RANKED_MIME_TYPES` ranks it between `text/html` and the images, as the factory's 60 does → `MarkdownDocument.parse` → `RenderedOutput.Document` → `RichText`; `outputHeadingsOf` lists its headings | adapted | it draws, in the notebook and the Console, and its headings join the table of contents as `CodeCell.getHeadings`'s do; a payload that parses to nothing falls to the next mime; a `#heading` link lands on the cell its heading is drawn in (`cellOfHeadingAnchor`); no typesetting — `GAP-096` |
| `text/latex` + `ILatexTypesetter` — `latexRendererFactory` rank 70 → `renderLatex`; `latex.ts` extracts `$…$`/`$$…$$`/`\(`/`\[`, delimiters before markdown parsing and restores them; `mathjax-extension`'s `MathJaxTypesetter` lazy-loads MathJax on first typeset | nothing typesets — `$…$` in a markdown cell or a `text/latex` output draws literally; no delimiter extraction exists | **absent** | `GAP-096`, opened at `T2` over `mathjax:clipboard`/`scale` — this row names the machinery half: the typesetter plug, the delimiter pass, and the mime itself |
| `image/svg+xml` — `svgRendererFactory` rank 80 `safe: false` → `renderSVG` inlines sanitized markup, or refuses: "Cannot display an untrusted SVG" | falls through to `text/plain`/the keys line | **absent** | `GAP-102`. The only unsafe-by-default standard renderer besides JavaScript — ours has no surface where its trust question could even be asked |
| `image/png` — the png arm of `imageRendererFactory` → `renderImage` `<img>`, metadata width/height/`needs_background`/`unconfined` | `IMAGE_MIME_TYPES` → strict-base64 `decodedBase64` → `RenderedOutput.Image` carrying `imageWidthOf` → `BitmapFactory` in the view, drawn at that width in dp, else its own pixels, at the column's start, capped at it | adapted | the size JupyterLab draws ([ADR-0086](../adr/0086-an-image-draws-at-its-metadata-width-in-dp-capped-at-the-column.md)); `height` is unread because `base.css`'s `height: auto` makes it inert there too; `unconfined` is `GAP-164`, `needs_background` `GAP-165`, `alt` `GAP-166`; a decode failure falls to the next mime — the usable-payload departure the registry row names |
| `image/{bmp,jpeg,gif,webp}` — the factory's other four arms (`factories.ts:22-30`) | the same arm: `IMAGE_MIME_TYPES` lists all five in the factory's order, one decoder and one drawing | adapted | they draw as PNG does; a GIF draws its first frame, where the `<img>` animates — `GAP-163` |
| output metadata — `MimeModel.metadata`, `getMetadata`, `ObservableJSON`; per-mime width/height/`needs_background`/`unconfined`/`fragment`/`expanded`/`root`, output-level `isolated`; `toJSON` round-trips it all and drops only `transient` (`outputmodel.ts:92-213`) | `KernelOutputs` reads `metadata` onto `DisplayData`/`ExecuteResult.metadata` and `transient.display_id` onto `displayId`; the file round-trips `metadata` and never writes `transient` | adapted | the carrier and the round trip match `toJSON`, `GAP-104` closed by it; the image width is the first knob spent (ADR-0086) |
| javascript — `javaScriptRendererFactory` rank 110 `safe: false` → `RenderedJavaScript`, the "JavaScript output is disabled" placeholder; `javascript-extension`'s `ExperimentalRenderedJavascript` mounts at rank 0 and evals a *trusted* bundle or shows a Run button | falls to `text/plain`/the keys line; nothing can execute | adapted | the stock surface is the placeholder — ours shows the same nothing; the opt-in executor is trust-gated extension surface the no-WebView model declines — `notebook:trust`'s answer. The read corrects the PRD: `ExperimentalRenderedJavascript` does `eval` — it is not a placeholder |
| `text/plain`/`vnd.jupyter.stdout`/`vnd.jupyter.stderr` — `textRendererFactory` rank 120 + `errorRendererFactory` rank 110 → `renderText`/`renderError` (`renderTextual`): `ansiSpan` colour, `autolink`, `<pre>` | `Stream`/`Error`/`text/plain` → `RenderedOutput.Text`; stderr and errors tint through `isError`; escapes colour through `SgrText` (ADR-0083) | adapted | the text draws; the colour and linkify halves are the next two rows; the stdout/stderr mimes normalize at `KernelOutputs`'s message mapping instead of reaching a renderer |
| `autolink` — `checkWeb` wrapping URLs in `<a>`, `checkPaths` wrapping `file:line` in `data-path` links (`renderers.ts:648-920`) | `plainTextLinks` (`richtext/PlainTextLink.kt`) laid over the coloured text in every text, stream and error output, the notebook's and the Console's; a tap goes where a markdown link's does | adapted | Lab's web pattern with the scheme narrowed to `http`, `https` and `www.`, what `isWebLinkDestination` follows; paths in errors and on stderr, where Python's own `File "…", line N` lands, not in errors alone; only `~/…` and `/home/jupydroid/…`, the kernel's `HOME`; drawn unchecked and checked on the tap, where Lab's `handlePathAnchor` asks `resolvePath` first; the file opens at its top, no line jump — `GAP-122` |
| `ansiSpan` — SGR → styled spans, the 16/256/true-colour forms, permitted through the sanitizer (`renderers.ts:852-864`) | `SgrText.parse` (`terminal/SgrText.kt`) reads each escape through the terminal's `GraphicRendition`; `RenderedOutput.Text` keeps the plain text and carries `SgrRun`s beside it, drawn on `TerminalPalette`'s VS Code ramps | adapted | ADR-0083: one SGR walk shared with the terminal instead of a second regex, every non-SGR CSI dropped as `ansiSpan` drops it, but bold is weight only and a coloured error is ruled rather than tinted — VS Code's renderer, which §8.3 makes authoritative for presentation; `GAP-106` closed by it |
| `MimeModel`/`OutputModel`/`AttachmentModel` — data+metadata+trusted, `convertBundle`, observable `streamText`, `executionCount`, raw fields, `toJSON` preserving everything but `transient` | `CellOutput` sealed type + `MimeBundle`/`KernelOutputs` — the bundle→model conversion is `KernelOutputs`'s own stated parity; metadata carried, trusted unread; each stream event appends a `CellOutput.Stream` in `EditorUiState.after` | adapted | the same fields land — `mimeBundle`, `execution_count`, traceback name/value; observable-stream and display-update accumulation is `T4`'s `OutputAreaModel` read, attachments' is the next row |
| `AttachmentsResolver`/`AttachmentsModel` — `attachment:name` answered from the cell's attachment bundle, held untrusted (`attachments/src/model.ts:328-390`) | `CellAttachmentImageResolver` answers `attachment:name` from the cell's `attachments`, still held verbatim in `PreservedJson` (`RichImageResolver.kt`) | adapted | the name is percent-decoded as VS Code decodes it, where Lab reads it raw; a missing or non-image attachment draws the image's alt text rather than a broken image |
| embedded images — `<img>` whitelisted and `handleUrls`/`getDownloadUrl` resolving workspace paths; markdown `![]()`; Jewel's `ImageRendererExtension`/`ImageSourceResolver`/Coil3 | `RichInline.Image`, parsed from `![]()` and `<img src alt width>`, drawn in its line through `LocalRichImageResolver`: the cell's attachments, then a workspace path beside the notebook, then an `https` address (`RichInlineText.kt`, `WorkspaceImageResolver.kt`, `RemoteImageResolver.kt`) | adapted | an inline in the paragraph, as Jewel draws one; its own width with a pixel to a dp, capped at the column (ADR-0086); a source that resolves to nothing draws its alt text where Lab draws a broken image; a web image is `https` only, capped at 5 MiB and fetched through a port with an HTTP cache, so an `http:` one draws its alt (ADR-0087); an HTML block's `align` is `GAP-170` |
| `IMarkdownParser` — `markedparser-extension` lazy-loads marked `gfm: true`: fence `highlight` through `ILanguageSession`, `blockRenderers` hooks (mermaid mounts one), `getHeadingTokens` (`markedparser-extension/src/index.ts`) | commonmark-java `Parser.builder()` with its table, strikethrough and autolink extensions, the AST walked to `RichBlock` | adapted | the AST-to-native-model strategy is `MarkdownDocument`'s stated design — no HTML is produced at all; `getHeadingTokens`' counterpart (`MarkdownDocument.heading`) verdicted under `AUD-004`; fence highlighting is `AUD-003`'s; the `blockRenderers` hook surface is what `GAP-099` records absent |
| GFM — marked's `gfm: true`: tables, strikethrough, autolink, tasklists; Jewel's `extensions/` ships the same four + images as processor/renderer module pairs (`jewel/markdown/extensions/`) | `commonmark-ext-gfm-tables`, `-gfm-strikethrough` and `-autolink` on `MarkdownDocument`'s parser: a pipe table → `RichBlock.Table` with each cell at its column's alignment (`MarkdownTable`), `~~…~~` struck, a bare URL a `RichInline.Link` | adapted | three of marked's four, in a markdown cell and a `text/markdown` output; no task-list checkbox — `GAP-167`; `> [!NOTE]` stays a plain blockquote, as in JupyterLab — alerts are Jewel's alone; a bare URL is followed as any link is |
| `application/json` + the jsonl trio — `RenderedJSON` rank 0, collapsible tree, `metadata.expanded`/`root`/`fragment` honoured (`json-extension/src/index.tsx:29-136`) | the `JsonObject` is held in `MimeBundle` and never read — the last resort prints keys | **absent** | `GAP-108`. Outranks `text/html` in the reference; the structure is kept and unseen |
| `application/pdf` + media documents — `pdf-extension` rank 100; audio/video document widgets; the whitelist's `<audio>`/`<video>` | bundle → keys; file → refused as not UTF-8 text, with no field; tags → flattened | **absent** | `GAP-109`. One repair class — a per-format surface decision — across the output, document and tag halves; the file half is the refusal `GAP-097`'s images get |
| vega/vega-lite — `vega5-extension` rank 57 over `application/vnd.vega.v5+json` + vegalite v3–v6, drawn by vega-embed (`vega5-extension/src/index.ts:175-224`) | keys, or a `VegaLiteChart(...)` placeholder line | **absent** | `GAP-110`. No in-idiom precedent exists — there is no Compose Vega |
| `text/vnd.mermaid` + `MermaidMarkdown` — `RenderedMermaid` for the mime, a markdown-fence block renderer, a `.mmd` document widget (`mermaid/src/mime.ts`, `tokens.ts:9`) | a ```` ```mermaid ```` fence draws as a plain code block — `info` kept, diagram never built | **absent** | `GAP-099`, opened at `T2` over `mermaid:copy-source` — this row names the machinery half: mime renderer, fence hook, document widget |
| `RenderedCommon`/`RenderedHTMLCommon`/`widgets.ts` — collapse/prompt DOM, `trusted` marks, `addMessageRenderer`'s mount point | `CellOutputsView` — the Compose container; no trusted marks (nothing to mark); inline expand/collapse and scroll | adapted | the container concerns are Compose's; collapse, scroll and output accumulation verdict with `T4`'s output-area surface |
| VS Code `backLayerWebView`/`cellRenderer` — outputs drawn by a second webview layer behind the cells, renderer scripts contributed per mime | outputs draw in-document in Compose — JupyterLab's own placement | adapted | presentation reference only: ours matches JupyterLab's in-document choice rather than the backlayer split, and ADR-0039's no-WebView model removes the bridge entirely |
| Jewel markdown — `MarkdownProcessor` → `MarkdownBlock`/`InlineMarkdown` → `MarkdownBlockRenderer`, `parseEmbeddedHtml` opt-in with `MarkdownHtmlConverterExtension`, `CodeHighlighter` no-op by default | `MarkdownDocument` → `RichBlock`/`RichInline` → `RichText` — the same two stages | adapted | the shape is deliberately shared; of the extension surface Jewel ships, autolink, gfm-tables and gfm-strikethrough are wired in, images draw as `RichInline.Image` (the embedded-images row above), and gfm-alerts is not built because JupyterLab draws none; `parseEmbeddedHtml` maps to ours: an HTML block converts through `HtmlDocument` and an inline `<img>` draws, while a paired inline tag drops (ADR-0088, `GAP-169`) |

### The output area and the format — verdicts

`T4`'s entries, read down the widget+model pair, the stdin and prompt machinery, the two
nbformat implementations and the server's file/nbconvert halves — then the import the VIEW
intent offers. Theirs: `OutputArea`/`OutputAreaModel` (`packages/outputarea/src/{widget,model}.ts`
— `add`/`insert`, `clear`/`clearNext`, `shouldCombine`, `processText`/`fixBackspace`/
`fixCarriageReturn`, `maxNumberOutputs`/`TrimmedOutputs`, `fromJSON`/`toJSON`, `execute` clearing
before a run and reading `tags` for `stop_on_error`); `OutputPrompt`/`InputPrompt`/
`PromptOverlay` and `IsolatedRenderer` (`tokens.ts`); `Stdin`/`onInputRequest` echoing a
`stream(name:'stdin')`; `display_id`/`update_display_data` registered under `transient` and
`set()` in place; `SimplifiedOutputArea` for a cloned output view; `execute_reply` `payload` routed to
the pager; `packages/nbformat/src/index.ts`'s open interfaces (`PartialJSONObject` everywhere,
`IUnrecognizedCell`/`IUnrecognizedOutput`, `MultilineString`, MINOR 4); rootfs `nbformat`
(`validate`, `split_lines`/`rejoin_lines`/`_rejoin_mimebundle`, `strip_transient` popping
`trusted`/`signature`/`orig_nbformat`, `nbformat_minor` 5); `jupyter_server`
(`contents/manager.py`'s `validate_notebook_model` on read *and* save, `fileio.py`'s
`atomic_writing` + `.invalid` recovery, `nbconvert/handlers.py`'s GET + POST + `api/nbconvert`
listing, `respond_zip`/CSP/`redirect_to_files`); `DocumentManager.open` reading bytes through
`contents.get`. Ours: `EditorUiState.outputs`/`after`/`afterOutput`/`startingRun` +
`clearsPending` (the model half), `CellOutputs`/`CellOutputsView`/`CollapsedCell`/
`executionLabel` (the draw), `KernelOutputs`/`CellOutput`/`MimeBundle` (the wire),
`NotebookFile`/`NotebookJsonWriter`/`NotebookOutput{Reader,Writer}`/`MimeBundleLines`/
`PreservedJson` (the format), `DiskNotebookRepository` (the file), `OkHttpNotebookExporter`/
`OfferedExportFormat`/`DocumentSharer` (the conversion), `ImportTitles` + `MainActivity`'s VIEW
intent (the import).

```sh
ls reference/research_repos/jupyterlab/packages/outputarea/src
grep -n 'clearNext\|shouldCombine\|processText\|maxNumberOutputs' reference/research_repos/jupyterlab/packages/outputarea/src/model.ts
grep -n 'OutputPrompt\|SimplifiedOutputArea\|isIsolated\|update_display_data\|display_id\|onInputRequest\|PromptOverlay\|raises-exception\|payload' reference/research_repos/jupyterlab/packages/outputarea/src/{widget,tokens}.ts
grep -n 'IUnrecognized\|PartialJSONObject\|nbformat_minor\|MultilineString' reference/research_repos/jupyterlab/packages/nbformat/src/index.ts
zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py | grep -n 'atomic\|invalid'
zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/contents/manager.py | grep -n 'validate'
zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/nbconvert/handlers.py | sed -n '1,90p'
zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst | tar -xOf - usr/lib/python3.12/site-packages/nbformat/v4/nbjson.py | grep -n 'strip_transient\|split_lines\|rejoin'
grep -rn 'ClearOutput\|clear_output\|allow_stdin\|stop_on_error' app/src/main/java/com/jupydroid/app
cat app/src/main/java/com/jupydroid/app/import/ImportTitles.kt; grep -n 'importTitle\|VIEW\|Intent' app/src/main/java/com/jupydroid/app/MainActivity.kt
```

These rows carry no `family:id` — the command surface is `T2`'s — so the produced counts do not
move; each `absent` still names the `GAP-NNN` ADR-0067 obliges. One defect surfaced and was
fixed red-first in `974ba25c`: `clear_output` was appended to the output list as an invisible
instruction — a `print; clear_output()` kept its output on screen, and the same entry serialized
to disk as `{}`, an output `nbformat.validate` refuses. `EditorUiStateTest`'s three
`aClearOutput*` cases and `KernelOutputsTest.outputOf_readsClearOutputWait` pinned it;
`afterOutput` now executes the instruction against the model — immediate clears drop the list,
`wait` sets `clearsPending` and the next output spends it (`model.ts:301-311`'s two cases) — so
it never lands in `outputs`, never renders, and never writes. Eight GAPs opened — `GAP-112` through `GAP-119`, `GAP-112` closed since by `withOutputFolded` (`domain/model/StreamFold.kt`); `GAP-014`,
`GAP-023` (closed since by ADR-0089) and `GAP-104` are cited where the read lands on their ground. Fold calls the PRD left
open: the stdin row cited `GAP-014` rather than re-gapping (its stream-name echo dropped on the
same lines; both closed since by ADR-0082), `isolated` verdicts `adapted` under ADR-0039's no-DOM answer rather than `absent`
(there is nothing rendered for an iframe to isolate, consistent with `notebook:trust` at `T2`),
and `update_display_data` verdicts `absent` — flattening to `DisplayData` produces a *duplicate*
frame, a visible wrongness no comment states — while the carrier it needs stays `GAP-104`'s. Both closed since by ADR-0085.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `OutputArea`/`OutputAreaModel` — the widget+model pair: `add`/`insert`, `changed`/`stateChanged` signals, `fromJSON`/`toJSON`, `execute` clearing outputs before a run (`packages/outputarea/src/{widget,model}.ts`) | `EditorUiState.outputs` + `after`/`afterOutput` projected over `CellOutput`; `startingRun`'s `outputs - cellId` on re-run | adapted | the same model duties run as a `Map` plus pure functions — `startingRun`'s clear is `execute()`'s `clear()`, commented on the function; each message's rule verdicts in its own row below |
| stream accumulation — `shouldCombine` folding adjacent same-name streams, `processText`/`fixBackspace`/`fixCarriageReturn` interpreting `\b` and `\r` (`model.ts:279-389`, `:545-659`) | `withOutputFolded` (`domain/model/StreamFold.kt`): a `stream` joins the last output when the names match and `\r`/`\b`/`\n` move a cursor carried across chunks; `afterOutput`, `ConsoleViewModel` and the disk read (`toDomainOutputs`, as `fromJSON` does) all add through it | present | the same merge rule and the same cursor, so a saved stream holds JupyterLab's text; `GAP-112` closed by it |
| `OutputPrompt` — `Out[n]` beside each `execute_result`, bound to the model's `executionCount` (`widget.ts:895-909`, `tokens.ts`) | `executionLabel`'s `[n]:` on the input side only; `CellOutput.ExecuteResult.executionCount` never drawn | **absent** | `GAP-114`. The PRD's "no `Out[n]` prompt" confirmed — the count arrives and round-trips; nothing draws it beside the output |
| `PromptOverlay` — the output-column overlay that toggles the `scrolled` metadata flag (`widget.ts`, `tokens.ts`) | `CellAction.ToggleOutputsScrolled` in the cell menu; `outputsScrolled` persisted the same way | adapted | same flag, different affordance — the hover/click overlay is a pointer's luxury the menu row answers on a tablet; the toggle itself verdicted at `AUD-002` |
| `Stdin`/`onInputRequest`/`allow_stdin: true` — a prompt widget on `input_request`, the reply echoed as `stream(name:'stdin')` (`widget.ts:547-629`) | `InputPromptField` (`ui/editor/CellInputPrompt.kt`) at the foot of `CellOutputs`, masked for `password`; submit appends `KernelInputRequest.echoOf` (`Name? Ada`, `Secret? ········`) as `StreamName.STDIN`, which the disk round-trips as `"stdin"` | adapted | the same field and the same echo; no line history (`inputHistoryScope`) and no placeholder hint, both a hardware keyboard's ([ADR-0082](../adr/0082-a-cells-input-is-answered-inline-and-kept-as-a-stdin-echo.md)) |
| `display_id`/`update_display_data` — outputs registered under `transient.display_id`; an update `set()`s the stored bundle in place, every view redraws (`widget.ts:804-850`) | `transient.display_id` rides on `CellOutput.DisplayData.displayId`; `update_display_data` arrives as `CellOutput.DisplayUpdate` and `withDisplayReplaced` replaces every output carrying the id, in the notebook and across the Console's entries; a repeated `display_data` updates, then appends | adapted | ADR-0085: the id on the output rather than a side map, and the scope vscode-jupyter's whole notebook rather than JupyterLab's request lifetime; `GAP-113` closed by it |
| `clear_output`/`clear(wait)` — `wait:false` clears the model now, `wait:true` sets `clearNext` and the next `add` clears first (`model.ts:301-311`) | was: appended as an invisible output and persisted as `{}` — now: `afterOutput` executes it against the model, `clearsPending` carries the deferred half; the Console does the same by entry (`ConsoleOutputArrival.afterOutput`, `ConsoleEntry.clearPending`) | **defect** | fixed red-first in `974ba25c` — `aClearOutputDropsWhatTheCellPrinted`, `aClearOutputWithWaitKeepsTheOutputsUntilTheNextOneArrives`, `aClearOutputWithWaitIsSpentByTheNextOutput`, `outputOf_readsClearOutputWait`; the Console's by `ConsoleOutputArrivalTest`'s `anImmediateClearEmptiesItsOwnEntryOnly`, `aWaitingClearKeepsTheEntryUntilItsNextOutput`, `aWaitingClearIsSpentByTheNextOutput`, `anUpdateLeavesAWaitingClearForTheNextOutput`; the `{}` write dies with it — the instruction never reaches `outputs`, so `toFileOutput`'s arm is unreachable |
| `maxNumberOutputs`/`TrimmedOutputs` — a cap (50 in a notebook, `notebook-extension/schema/tracker.json`) with a `Show more outputs` button (`{model,widget}.ts`) | `drawnOutputCountOf` draws the first `OUTPUT_COUNT_LIMIT` (50) behind `Show more outputs`, and `outputLineWindowOf` cuts a text past 30 lines to its head and tail behind an `N lines hidden` row; the model and the file keep every output and line | adapted | ADR-0084: both caps, since JupyterLab counts outputs and VS Code counts lines and each misses the other's runaway; expanded in place per output, and a search step onto a hidden hit draws it; `GAP-115` closed by it |
| `isIsolated`/`IsolatedRenderer` — `metadata.isolated` (per-mime key, then `metadata.isolated`) wraps the output in an iframe (`widget.ts:769-783`, `:1052-1064`) | `metadata.isolated` carried and unread, and no DOM to isolate — the subset parse is unconditional | adapted | the isolation question presumes rendered markup can act — under ADR-0039 nothing can, so there is nothing to fence; `notebook:trust`'s `T2` verdict covers the flag itself |
| `SimplifiedOutputArea` — the prompt-less output area `CodeCell.cloneOutputArea()` builds for a second view of a cell's outputs, `onInputRequest` a no-op so only the cell's own area prompts (`widget.ts:936-950`, `packages/cells/src/widget.ts:1590-1600`). The console is not it: its cells are ordinary `CodeCell`s (`packages/console/src/widget.ts:1382`) | no second view of a cell's outputs; the console's entries answer stdin through the same `CellOutputs` a notebook cell does | **absent** | `GAP-100` — the clone's own widget; with no second view there is no second prompt to suppress |
| `InputCollapser`/`OutputCollapser` — click strips over the prompt columns bound to `inputHidden`/`outputHidden` (`packages/cells/src/collapser.tsx`) | `CollapsedCell` for both directions, `outputsHidden`/`sourceHidden` metadata-driven | adapted | the collapse machinery verdicted at `AUD-002` over the toggle commands — this row is the widget shape: a strip with a preview rather than a prompt-column affordance |
| `execute_reply` `payload` — `source:'page'` to the pager, `set_next_input`, `ask_exit`, `edit` (`widget.ts:_onExecuteReply`) | `isReplyFor` reads the reply's identity only; `payload` never read | **absent** | `GAP-116`. `%page` and `?`-help vanish — the message arrives whole and its routing field is dropped |
| `raises-exception` cell tag — `OutputArea.execute` sends `stop_on_error: false` for a tagged cell (`widget.ts`; nbformat's tag convention) | `stop_on_error: true` unconditionally (`JupyterMessage.kt:107`); the tag is preserved but never read | **absent** | `GAP-117`. A test-suite notebook's expected failure halts our run-all where the reference's continues |
| `execute_input` → the prompt's execution count | `executionCountOf` → `KernelEvent.ExecutionCount` → `executionCounts` map → `[n]:` and `cell.executionCount` persisted | present | the same message feeds the same count — the input-side prompt draws it and the file keeps it |
| model `toJSON`/`fromJSON` — stored outputs round-trip through the model | `toDomainOutput`/`toFileOutput` over `outputsJson()` — preserved bytes when the domain list is unchanged, regenerated when it is not | adapted | stronger than a model round-trip on the untouched path (byte-for-byte); a regenerated list keeps each output's `metadata` and drops only `transient`, as `toJSON` does |
| `IUnrecognizedCell`/`IUnrecognizedOutput` — a cell type or output the schema does not name still opens, drawn unrecognized (`nbformat/src/index.ts`) | `cellKind` throws naming the fourth type; an unknown `output_type` drops at `toDomainOutput` ("Total by design") but survives in `PreservedJson` while untouched | adapted | refusal-with-a-name on the cell half — the comment names the value; preservation-not-modeling on the output half — the same `IUnrecognizedOutput` idea answered at the file layer instead of the widget |
| `nbformat`/`nbformat_minor` — TS `MINOR_VERSION` 4, Python `nbformat_minor` 5 | `nbformat = 4`, `nbformatMinor = 5` | present | matches the shipped rootfs's own writer — minor 5 because cell ids are a 4.5 requirement, stated on `NotebookFile` |
| `MultilineString`/`split_lines`/`rejoin_lines`/`_rejoin_mimebundle` — `text/*` and the two special mimes split on write, non-text JSON stays structured, lists rejoin on read (`nbformat/v4/nbjson.py`) | `MimeBundleLines` + `joinedText`/`toJsonLines` + `MultiLineSourceSerializer`; cell `source` emits the string form | present | the asymmetry is transcribed function-for-function, comments naming each; the string form is a shape `split_lines=False` also produces, and ours reads both |
| `nbformat.validate` — run on read and on save; `capture_validation_error` → `model["message"]` reports the failure; `.invalid` renames an unparseable file (`contents/{manager,filemanager}.py`, `nbformat/v4`) | nothing validates anywhere — a structurally broken notebook throws in decode or drops silently | **absent** | `GAP-118`. The reference's answer is *report*, not refuse — invalid structure opens with a warning; ours cannot say why a file opened degraded |
| `atomic_writing` + `.invalid` recovery — `.{name}~` temp, flush+fsync, rename, prior file restored on failure (`contents/fileio.py`) | `DiskNotebookRepository.writeFile` goes through `writeAtomically`: a `.~<name>` temp beside the file, synced, then moved over it in one rename, with the target's mode carried (`AtomicFileWrite.kt`) | adapted | ADR-0089. A rename never leaves a partial target, so there is no `.invalid` recovery to read for; the save replaces the inode, so a hard link keeps the old content |
| `strip_transient` — `nbformat`'s own I/O pops `cell.metadata.trusted`, `signature`, `orig_nbformat` on reads and writes (`nbformat/v4/nbjson.py`) | `PreservedJson` keeps every field byte-for-byte | adapted | the reference disagrees with itself here — JupyterLab's model carries `trusted` while the Python layer strips it at both ends; ours sides with file-faithful preservation (ADR-0021's open format) |
| open schema — every interface `extends PartialJSONObject`; `nbbase`/`toJSON` keep unknown keys (`nbformat/src/index.ts`) | `PreservedJson` + `ignoreUnknownKeys` + the overlay writer | present | same contract, one level stronger — untouched regions round-trip byte-for-byte rather than field-for-field |
| `api/nbconvert` + `NbconvertFileHandler` — format listing, GET `nbconvert/<fmt>/<path>?download`, POST variant, `respond_zip`, CSP sandbox header, `redirect_to_files` for non-notebooks (`nbconvert/handlers.py`) | `OkHttpNotebookExporter.formats()`/`export()` over the same two endpoints; `OfferedExportFormat` intersects; `DocumentSharer` hands bytes off | adapted | verdicted at `T2` — the GET pair is the half both clients call; POST, the zip branch and the redirect have no caller on either frontend, and the CSP header guards a browsing surface ADR-0039 never built |
| contents `get`/`save` — the file manager reading and writing notebook models (`contents/{filemanager,manager}.py`) | `DiskNotebookRepository.readFile`/`writeFile` | adapted | the same file-level duties; validation and atomicity split to their own rows above |
| `.ipynb` open — `DocumentManager.open` reads the file's bytes through `contents.get`; a document is its content (`docmanager/src/manager.ts`) | a VIEW intent's bytes are read through `SharedDocumentReader` and copied verbatim into the workspace root under the URI's display name, then opened: `importNotebook` (`DocumentOpeners.kt`) | adapted | the document is copied before it opens, since a `content://` URI is not a path the server or kernel can see; a taken name is counted past rather than asked about (`copyIn`'s KDoc) |

### The walk

Five paths end to end — an `.ipynb` open through save, a run's output through pixels, an
export through the share sheet, a `.md` tap, and the three flags the middle of the pipe
swallows. **An `.ipynb` open:** a tap in the explorer → `rememberDocumentOpener` →
`backStack.openDocument` — `openDocuments.at(path)?.openedAs` resolving the live path back to
the key's `openedAs`, the `openOrReveal` read `manager.ts`'s `_contextsForPath` makes — → the
`.ipynb` suffix → `openNotebook` → `openInMainArea`, an equal key revealed rather than pushed a
second time → `entry<Editor>` → `EditorScreen` → `EditorViewModel` →
`openDocuments.opened(document)` registered *before* the read, so nothing can replace the file
in between → `notebookRepository.readNotebook(id)` → `DiskNotebookRepository` — a missing file
or a failed decode each named in an `IllegalArgumentException` that lands as
`EditorUiState.error` — → `Json.readNotebook`'s double parse: the typed `NotebookFile` for the
modeled fields and the raw `JsonObject` riding beside them as `preserved` (ADR-0021) →
`toDomain` pairing each cell with its preserved twin by index, `cellKind` naming a fourth
`cell_type` rather than guessing → `toEditorUiState` seeding `outputs` and `executionCounts`
from the cells → `seedKernelPreference` → a typed keystroke → `withSource` setting `dirty` →
`save.schedule()` → `DocumentAutosave` — 500 ms of debounce, the periodic pass armed only once
an `editArrived` says there is work — → `persist()` under `writeMutex`, writes serialized in
the order asked so a debounced write and a run's checkpoint cannot put an older snapshot back
over a newer edit → `withResultsOnCells` folding `outputs` onto the cells **for the snapshot
only** — a run in flight keeps its output off the file until the checkpoint — →
`NotebookJsonWriter.toFileJson`, the overlay: `preserved` laid down first and the owned keys
over it, `nbformat_minor` forced to 5 because cell ids are written regardless,
`metadata.jupydroid` stripped, `kernelspec.name` updated while `display_name` keeps the file's
own, `outputsJson()` returning the preserved `JsonArray` itself when the domain list reads back
unchanged — byte-for-byte on the untouched path — and `toFileOutput` regenerating when it does
not → `writeAtomically`'s temp and rename (ADR-0089) → `adoptWritten` clearing `dirty`
only where the write still covers the live state → `onCleared`'s farewell `persist()` on
`applicationScope`, outliving the ViewModel and gated by `save.discarded`. **A run's output:**
`runCell` → `NotebookCellRuns.run` → `startingRun` — `outputs - cellId`, `execute()`'s pre-run
`clear()` — → `eventsFor(cell)` → `session.reopeningIfStale` → `kernelGateway.execute` →
`RealKernelGateway.execute`'s cold flow — `msg_id` and `ExecutionCompletion` minted per
collection, not per call — → `sessionSocket.events`, the 256-deep `MutableSharedFlow` fed by
`tryEmit` off OkHttp's reader thread, a full buffer logged `kernel.frame.dropped` rather than
backpressured → `onSubscription` — `awaitReady` holding the request until handshake *and*
`kernel_info` answer, then `send` — → `transformWhile { emitUntilComplete }`: an `Ended` frame
→ `KernelChannelException`; `executionCountOf` → `KernelEvent.ExecutionCount`; `outputOf` →
`KernelEvent.Output`; `completion.accept` wanting reply *and* idle in either order
(`future.ts:14-17`) → `KernelEvent.Idle` ends the flow → the collect folding each event into
`uiState.update { it.after(event) }` → `afterOutput` — `ClearOutput` executed against the
model: immediate drops the list, `wait` parks the cell in `clearsPending` and the next output
spends it, so the instruction never lands in `outputs`, never renders and never serializes
(`974ba25c`) — → `withSearchedOutputsRecomputed`, a no-op unless the search reads outputs →
`finishedRun` → `checkpoint` — `persist()` when autosave is on, `dirty = true` when not — → the
cell's `EditorContent` item → `CellOutputs` → `render()`/`renderRichest` — three mimes ranked
off `defaultRank`, an unusable payload falling through to the next, the bundle's keys the last
resort — → `OutputText`/`OutputImage`/`RichText` inside `OutputColumn`, `ScrolledOutputs`'s
24-line bound or `CollapsedCell`'s preview strip. **An export:** the overflow's row →
`pickerOpened` → `BaseSpaceNotebookExporter.live.get()` — asking for formats *is* asking for
the runtime, which is why the ask sits inside the picker and not beside the menu — →
`GET api/nbconvert` → `offeredExportFormats` intersecting the server's list with the four ours
has labels for → `exportAs("html")` → `converting` named on the row → `persist()` — the
save-first the reference does as `await context.save()`, and refused rather than silent since
`48041f2f` when the write never landed — → `exporter.export(path, "html")` →
`GET nbconvert/html/<path>?download=true` → `NbConvertResponse` — the server's own filename off
`Content-Disposition`, its media type off `Content-Type`, its own sentence off a failure — →
`writeBytes` beside the notebook, a same-name artifact replaced → `ExportReport.Written` → the
snackbar's Share → `IntentDocumentSharer` → `FileProvider`'s `content://` → `ACTION_SEND` under
a chooser, `FLAG_GRANT_READ_URI_PERMISSION` granted. **A `.md` tap:** the same `openDocument`
to the split's other arm → `openFile` → `entry<FileEditor>` → `FileEditorViewModel` →
`workspaceRepository.readStampedText` — strict decode, so a `.png` is refused and its tab says why
with no field (`GAP-097`) — → `syntaxForFile` → the same autosave-and-`persist` shape, `changes`-fed
`adopt`/`stopWriting`, the same farewell flush on `applicationScope`. **The three flags:**
`attachment:name` round-trips inside `PreservedJson` and, since, is resolved from it at draw time
(`CellAttachmentImageResolver`); `display_id`/`transient` are dropped at `KernelOutputs.mimeBundle`'s `data`-only
read, an `update_display_data` arriving as a second `DisplayData` beside its stale twin
(`GAP-113` riding `GAP-104`'s missing carrier, both closed since by ADR-0085: the id rides on `DisplayData.displayId` and the update replaces in place); `clear_output`'s `wait` survives as
`clearsPending`, spent by the next output — and surviving `startingRun` is the reference's own
shape, whose `clear()` never resets `clearNext` either (`model.ts:301-320`), the flag inert on
an already-empty list in both.

What the walk found, beyond the rows above:

- **Growth is paid per frame at the wire and per recomposition at the glass.** Each iopub
  frame is decoded four times — `executionCountOf`, `outputOf`, `isReplyFor` and `isIdleFor`
  each run `JupyterFrame.decode` on the same string rather than one decode fanning out. The
  buffer behind them is honest: 256 frames of headroom, `tryEmit` never blocking OkHttp's
  reader thread, a refusal logged `kernel.frame.dropped` rather than queued without bound.
  Inside the model `afterOutput`'s `carried + output` copies the cell's list per message,
  bounded only by the output count — which the model leaves unbounded on purpose, since
  ADR-0084 caps what is drawn and never what is kept; a progress loop is no longer one output per repaint, since `withOutputFolded`
  folds it. And `CellOutputs`'s
  `mapNotNull { it.render() }` re-derives every rendered output on each recomposition of the
  item: `OutputImage` memoizes only the bitmap decode (`remember(output)`), `MarkdownCell`'s
  blocks are `remember`ed by source — the html/base64/ANSI work is re-paid on every state bump
  of a running cell, which is once per frame. On the file side `outputsJson()` pays O(outputs)
  per save per code cell to prove byte-identity — the rarer direction. None of it is wrong; it
  is the cost ledger a `JD-NNN` would spend on memoizing `render()` or decoding the frame once.
- **Branching splits at the factory door, on `msg_type`, and on who may boot the runtime.**
  `openDocument`'s `openedAs`-then-suffix is the whole factory dispatch — one conditional where
  the reference's document manager holds a factory map, sized by there being exactly one
  factory per type to dispatch (`DocumentOpeners.kt`). `KernelOutputs.outputFrom`'s `when` is
  the wire dispatch — a `stdin`-named stream dropping at `toStream` (a kernel never sends one: the
  echo is the front end's, `domain/model/CellOutput.kt`),
  `update_display_data` becoming `DisplayUpdate`, an instruction like `clear_output` (ADR-0085), `clear_output` becoming an
  instruction rather than an output. `renderRichest`'s fall-through is the renderer selection —
  rank → usable-payload → keys — already verdicted. And the boot split: `pickerOpened`,
  `exportAs` and a cell's run all reach `live.get()`, so every Space-backed ask may boot the
  runtime; only the disk paths — open, save, the artifact write, the share — never do.
- **Failure lands at four distinct surfaces, and the walk found the two places it did not.**
  An open failure is `EditorUiState.error` with the file's name in it. A save failure is
  `saveFailure` — a state rather than an event, because "your work is not reaching disk" goes
  on being true until it stops — and is now also the export's refusal where it used to convert
  the last-good file beside the banner: `48041f2f`, red first
  (`exportAs_refusesToConvertAFileTheSaveNeverWrote`). A wire failure is a `CellOutput.Error`
  the cell earned — `KernelChannelException` on an `Ended` socket, a refused connect — except
  `KernelPickRequiredException`, rethrown past the failure lane to arm the picker rather than
  stain a cell that never ran. An export failure is `ExportReport.Refused` carrying the
  server's own sentence (`NbConvertResponse`). The second defect is the file half's twin of the
  wire's totality contract: `toDomainOutput` promised "dropped rather than throwing" and read
  every member through `jsonPrimitive`, which throws on a non-primitive — one malformed member
  anywhere in `outputs` failed the whole open until `4304a05f` made the reads `as?`-tolerant at
  member granularity (red first: the four `toDomain_*` cases in `NotebookOutputsTest`). What
  the walk confirmed rather than repaired: `toStream`'s stdin refusal (`domain/model/CellOutput.kt`),
  `stopWriting`/`notebookIsGone` gating the farewell write so a deleted file is not
  resurrected, and `getUriForFile`'s refusal staying unreachable behind a `writeBytes` that
  just succeeded — `DiskWorkspaceRepository.writeText`/`writeBytes` sharing `GAP-023`'s
  non-atomic wound was worth naming while here; ADR-0089 closed both since.
- **Coupling is one type spoken by two translators, and one junction where wire-state meets
  file-state.** `CellOutput` is the wire's model *and* the file's — `KernelOutputs.outputFrom`
  produces it from frames, `NotebookOutputReader.toDomainOutput`/`NotebookOutputWriter`'s
  `toFileOutput` translate the stored shapes, and `MimeBundleLines` mediates the line-split
  convention the wire never has. `PreservedJson` threads raw `JsonObject` through the domain
  model — lawful because kotlinx's types are not Android's (ADR-0021) — which is what makes the
  byte-for-byte overlay possible. `outputs` live on `EditorUiState`, not on `Cell` — in-flight
  output is not document content — and `withResultsOnCells` is the single junction where the
  run's map meets the persisted list, `toEditorUiState` its inverse on open; `persist` injected
  into `NotebookExport` is the same seam a level up, and its `Boolean` is now what keeps the
  export honest about which bytes nbconvert will read. `DocumentSharer` is the one Android
  boundary the path ends at: the artifact lands in the workspace precisely so the explorer
  already lists it and a cell can `open()` it — the download the reference hands to a browser,
  answered by a share sheet.

## Chrome and presentation — `AUD-009`

**The surface is eleven chrome packages whole, `mainmenu-extension`'s remainder, five `notebook:`
ids earlier sections reserved, and the VS Code workbench parts the comparison reads against —
127 ids this slice owns of the 435 the instrument now counts across 34 packages.** `COMMAND_ID`
learned the typed declaration at `T1` (`export const name: string = 'id'`, wrapped by the
formatter or not), and the count moved 412 → 435 unique across 33 → 34 packages because twenty
of `application-extension`'s 29 ids and all three of `hub-extension`'s are written that way —
the annotation hid the chrome's own area toggles behind it (`index.tsx:65-117`).
`docmanager:clear-recents` is declared in both `docmanager-extension` and `mainmenu-extension`,
which is why package declarations total 436 where unique ids total 435. Two real commands sit in
module-level `const`s the pattern will not honestly reach — statusbar:toggle
(`statusbar-extension/src/index.ts:49`) and workspace-indicator:toggle
(`workspaces-extension/src/index.ts:76`) — named like `console:prompt-to-top` before them rather
than counted, because broadening to bare `const` would sweep every `const cmd = 'console:create'`
an `execute` call names in with them. `hub-extension`'s three — hub:control-panel, hub:logout,
hub:restart — entered the measured surface on the same fix but are JupyterHub's own, named not
claimed, as is the completer pair `notebook-extension` declares under the `completer:` prefix
(completer:invoke-notebook, completer:select-notebook) that the `notebook:` family filter drops.
Produced, never stored:

```sh
python3 scripts/read_reference_commands.py --all                                     # 435 / 34
python3 scripts/read_reference_commands.py --jupyterlab-package application-extension  # 29
python3 scripts/read_reference_commands.py --jupyterlab-package mainmenu-extension     # 41
for p in apputils-extension launcher-extension settingeditor-extension help-extension \
         shortcuts-extension workspaces-extension logconsole-extension \
         pluginmanager-extension extensionmanager-extension; do
  echo "== $p"; python3 scripts/read_reference_commands.py --jupyterlab-package "$p"
done
python3 scripts/read_ledger_partition.py --unassigned   # 0 / 0
python3 scripts/read_ledger_verdicts.py --missing       # 0
```

**This slice meets two kinds of reference.** JupyterLab's chrome:
`application/src/{shell,layoutrestorer,router}.ts` and `application-extension/src/{index,topbar}`
(the `application:` ids plus `sidebar:switch`, `router:tree`, `property-inspector:show-panel`),
`apputils/src/{thememanager,commandpalette,menufactory,semanticCommand,notification,printing,licenses}`
beside `apputils-extension`'s plugin files, `mainmenu/src/{mainmenu,file,edit,view,kernel,run,tabs,settings,help}.ts`
— the `SemanticCommand` delegates — `launcher/src`, `statusbar/src` +
`statusbar-extension/src/index.ts`, `settingeditor/src` + `settingeditor-extension`,
`help-extension`, `shortcuts-extension`, `workspaces/src` + `workspaces-extension`,
`logconsole/src` + `logconsole-extension`, `pluginmanager/`, `extensionmanager/`, and the three
shipped theme packages — `theme-light-extension`, `theme-dark-extension`,
`theme-dark-high-contrast-extension` (`theme-scrollbars` is a setting, not a package). VS Code's
is the workbench: `src/vs/workbench/browser/{layout,workbench}.ts` and `browser/parts/` —
activitybar, auxiliarybar, banner, editor, notifications, panel, sidebar, statusbar, titlebar,
views — plus `contrib/notebook/browser/view/cellParts/` (`cellToolbars.ts`,
`cellFocusIndicator.ts`, `cellExecution.ts`, `cellStatusPart.ts`, `foldedCellHint.ts`,
`collapsedCellInput.ts`, `collapsedCellOutput.ts`, `cellDnd.ts`) for the cell chrome §8.3 makes
VS Code authoritative over.

```sh
ls reference/research_repos/vscode/src/vs/workbench/browser/parts
ls reference/research_repos/vscode/src/vs/workbench/contrib/notebook/browser/view/cellParts
```

**Ours** is `Navigation.kt` (the rail, the panes, the main-area stack) with `PaneDirective`,
`SidePanel.kt`/`SidePanelBar.kt`/`SidePanelRail.kt`, `MainAreaTabs.kt`/`MainAreaTabStrip.kt`/
`MainAreaCloseGate.kt`, `NotebookBackStack`/`OpenDocuments`/`DocumentOpeners`, `MainActivity`,
`theme/` — `ChromePalettes`/`EditorPalettes`/`Typography`/`CodeText` over `ThemeSettings`/
`ThemeOverride` and `EditorSettings.codeFontSizeSp` — `ui/launcher/` + `LauncherItems`,
`ui/settings/` over the `*Settings` models and `Disk*` repositories, and the status items on
`EditorTopBar*`/`KernelIndicator`/`DirtyMarker` — Compose chrome by ADR-0039, with no menu bar,
no command palette, no status-bar part for the toggles to land on.

**Carried in, recorded not rejudged.** Three ids arrive already judged: `application:close` sits
in `AUD-006`'s `filemenu:close-and-cleanup` row and the `recentmenu:` pair in `AUD-005`'s recents
read — ownership followed the verdicts rather than this slice reclaiming them.
`docmanager:clear-recents`, `editmenu:find`, the `filemenu:` pair and the seven `kernelmenu:`
delegates keep their earlier owners while `mainmenu-extension` enters the partition whole — its
eleven already-owned ids declared by the package read now rather than carried. The partition
rows are in the shared table under `AUD-002`'s heading; this slice's own split puts 65 ids on
`T2` (shell and menus), 13 on `T3` (the presentation commands and the five `notebook:` carries),
49 on `T4` (the hosted surfaces).

**The hole, named:** `fileeditor-extension`'s remaining 28, `codemirror-extension`'s 12,
`debugger`'s 17, `completer-extension`'s 4 plus the notebook pair, `tooltip-extension`'s 4,
`inspector-extension`'s 3 — **70 ids no audit has examined**, left outside this slice on purpose
and recorded so the next slice knows exactly what is unclaimed. No verdicts land at `T1`: the
section opens with the measured surface and the ownership, and the verdict tables grow in with
the tasks that produce them.

### The shell and the menus — verdicts

`T2`'s entries, read down every `addCommand` in `application-extension`'s `index.tsx`, the
`addSemanticCommand` delegates `mainmenu-extension` wires through `application/src/utils.ts`, and
`apputils-extension`'s palette, statedb and section-mover registrations. Theirs: `LabShell`'s
areas — `main`, `header`, `top`, `menu`, `left`, `right`, `bottom`, `down` — with tab walks that
cross bars (`shell.ts:919-1004`), dock splits (`:2008`), area toggles that collapse to an activity
bar (`:2383-2394`), the mode/presentation/fullscreen trio and `resetLayout`'s composite
(`index.tsx:375-830`), the menu bar's nine openers and its semantic delegates
(`index.ts:195-1012`), and the apputils shell acts (`index.ts:344-770`, `palette.ts:163-175`,
`movesectionsplugin.ts:44-118`). Ours: a fixed-area shell — one side area collapsible to its rail
(`SidePanel.chosen`, which cites `shell.ts:2383-2394` itself), a bottom bar in place of the rail
below two panes (`SidePanelBar`, VS Code's `ActivityBarPosition.BOTTOM` the cited precedent), one
main-area stack whose strip draws opening order (`MainAreaTabs`, `MainAreaTabStrip`), the shared
dirty-close gate (`MainAreaCloseGate`), and the menus folded into contextual bars —
`NotebookBar`, `KernelMenu`, `NotebookActionsMenu`, the Running panel — with no menu bar
(`RecentsSection.kt`) and no command registry for a palette to search.

Two findings the read surfaced, neither a defect: `runmenu:run-above` and `runmenu:run-below` are
**declared but never registered** — no `addCommand` or `addSemanticCommand` mounts them anywhere
(`index.ts:117-119` are bare declarations); the Run menu mounts `notebook:run-all-above`/
`notebook:run-all-below` directly (`tracker.json:201-205`), so their row verdicts the acts the
menu actually reaches. And `application:toggle-header`'s operand — the top header — exists only
in `single-document` mode, which `TabbedDocumentPane`'s doc records as read and rejected here.

No defect surfaced: the sharp edges are absence-shaped, and the GAPs record them with their
repairs. Five GAPs open — `GAP-120` through `GAP-124` — with `GAP-018`, `GAP-059`, `GAP-060`,
`GAP-098` and `GAP-119` cited on rows they already own. No `undocumented`: every `adapted` row
rests on a comment or ADR already written, and ADR-0054 carries the one-side-area ones.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `application:activate-next-tab`, `application:activate-previous-tab` — `labShell.activateNextTab()`/`activatePreviousTab()` move `currentWidget` inside a tab bar, wrapping into the neighbouring bar at the ends (`index.tsx:432-483`, `shell.ts:919-985`) | the strip activates by direct tap — `activateMainAreaTab` promotes the entry to the top the scene draws, and the row scrolls to reach it; Back reveals the previously-used one through the MRU stack | adapted | relative cycling is a keybinding's answer to many tabs; a touch strip's answer is the direct pick — the wrap's other half has no second bar to walk into, `GAP-060`'s ground |
| `application:activate-next-tab-bar`, `application:activate-previous-tab-bar` — focus walks between the multiple tab bars a split main area creates (`index.tsx:458-483`, `shell.ts:986-1004`) | one strip exists — `TabbedDocumentPane` draws the only bar | **absent** | `GAP-060`. The walk's operand is a second bar, which presupposes the split view the gap records |
| `application:split-tab` — direction arg or context-menu tab → `labShell.moveTab`, splitting the dock (`index.tsx:840-870`, `shell.ts:2008`) | — | **absent** | `GAP-060`. Same ground: one strip, no dock splitting, a document viewed once |
| `application:close-other-tabs`, `application:close-right-tabs` — tab context-menu acts closing the current tab's siblings (`index.tsx:329-373`) | the strip's long-press carries rename only; no sibling bulk close | **absent** | `GAP-059`. The repair the gap records — a real tab context menu — is where these rows would live |
| `application:close-all` — `labShell.closeAll()` closes every main-area widget (`index.tsx:484-496`, `shell.ts:1206`); the Running panel files the same bulk (`opentabs.ts:82-86`) | the Running panel's Open Tabs "Close All" — a confirm, then `openTabs.forEach { onCloseTab(key) }`, each ask queued by `MainAreaCloseGate` | adapted | the act exists on the running surface the reference also files it under; a shell-level row would have only a menu to hang on (`RecentsSection.kt`'s no-menu-bar record) |
| `application:toggle-left-area` — collapse/expand the left side; in `'side'` mode only deselects the active tab, leaving the activity bar (`index.tsx:514-534`, `shell.ts:2383-2394`) | `SidePanel.chosen` — re-picking the showing panel collapses the area to the rail, its doc naming this command's `collapse()` as the model | adapted | the reference's own toggle, reached by pick rather than a separate act |
| `application:toggle-right-area`, `application:toggle-down-area`, `sidebar:switch` — the right and down areas' toggles, and the context-menu act moving a panel left↔right while saving user layout (`index.tsx:536-578`, `:2199-2230`) | one side area — ADR-0054's recorded shape; `SidePanel`'s enum is the closed roll | adapted | no right or down area exists for a toggle or a switch to move into — the difference is the recorded decision, not a missing act |
| `application:toggle-sidebar-widget` — activate the `{side, index}` widget, or collapse if it is already current (`index.tsx:580-639`) | the same `SidePanel.chosen` rule — the pick identifies the panel rather than a side-plus-index pair | adapted | same act, one operand fewer, on the one side that exists |
| `application:toggle-side-tabbar`, `application:set-activity-bar-position` — hide/show a side's activity-bar strip; place the side activity bars side/top/bottom (`index.tsx:641-674`, `:994-1030`) | the bar's presence and position are measured, not set: `SidePanelRail` beside a two-pane window, `SidePanelBar` on the bottom at one — its own doc cites VS Code's `ActivityBarPosition.BOTTOM` as the relocation precedent | adapted | the acts presuppose a bar with states to pick; this one relocates by width class, which is the reference's own third position arriving by measurement |
| `application:set-mode`, `application:toggle-mode` — `labShell.mode` between `'single-document'` and `'multiple-document'` (`index.tsx:716-768`) | `TabbedDocumentPane`'s doc records the read-and-reject: the reference draws the strip at every width, so no mode exists — `paneScaffoldDirective`'s width class is the layout's only shape switch | adapted | the mode's question — how much fits — is answered by the measured window rather than a persisted flag |
| `application:toggle-header` — the shell's top header, `isVisible` only in `single-document` mode (`index.tsx:497-512`) | no `top`/`header`/`menu` areas exist — the shell is always what this toggle would leave it | adapted | nothing to show or hide: the header's content — logo and menu bar — is the recorded menu fold |
| `application:toggle-presentation-mode` — `labShell.presentationMode`, withdrawing side areas, activity bar and menus (`index.tsx:676-690`) | — | **absent** | `GAP-121`. The rail collapses to icons at most; no reach hides the chrome wholesale |
| `application:toggle-fullscreen-mode` — DOM fullscreen request/exit on the shell (`index.tsx:691-715`) | an activity is the display — `enableEdgeToEdge` in `MainActivity` | adapted | the toggled-to state is the only one the window has; the toggle answers a desktop window's question |
| `application:toggle-context-menu` — toggles the context-menu plugin's `disabled` setting — the escape hatch back to the browser's native menu (`index.tsx:2177-2198`) | no Lumino context menu exists to disable; context surfaces are per-widget long-presses (`MainAreaTab`'s rename, `GAP-059`'s family) | adapted | the conflict the toggle escapes cannot arise here |
| `application:move-widget` — move a widget between `main`/`left`/`right`/`down`, context-menu act (`index.tsx:1879-1930`) | areas are fixed kinds: `SidePanel`'s closed enum for panels, `isDocument()`'s roll for the stack — a document cannot become a panel | adapted | the areas' fixedness is ADR-0054's decision; the act's operand is a movable surface the shape refuses |
| `application:reset-layout` — composite: presentation off, fullscreen off, header on, side tabbars on (`index.tsx:769-822`) | none of those states exist — the layout carries no user-movable state to reset | adapted | no operand; a reset over a fixed layout is a no-op by construction |
| `application:zoom-in-widget`, `application:zoom-out-widget`, `application:reset-widget-zoom` — `zoomActiveWidget` CSS-scales the current widget ±10% (`index.tsx:375-430`) | content size is the font steppers' answer: `TerminalActionsMenu`'s A+/A− per terminal, `EditorSettings.codeFontSizeSp` on the Settings screen | adapted | a text-size reach rather than a widget transform — the same magnify ask, the unit a touch surface can act on |
| `router:tree` — the `treePattern` route resolves `file-browser-path` + `treePath` into the browser and file the URL names (`index.tsx:1511-1550`) | the VIEW intent is the deep link: `sharedNotebookFrom` → `importNotebook` → `openDocument`; `browseRequest` is the browser-path analog (`Navigation.kt`) | adapted | no URL router because no server page |
| `property-inspector:show-panel` — `activateById` on `jp-property-inspector`, the side-area widget providers draw properties into (`index.tsx:1758-1770`) | — | **absent** | `GAP-123`. No inspector widget exists; nothing draws a current widget's properties |
| `mainmenu:open-first`, `editmenu:open`, `filemenu:open`, `helpmenu:open`, `kernelmenu:open`, `runmenu:open`, `settingsmenu:open`, `tabsmenu:open`, `viewmenu:open` — each sets `menu.activeMenu` and `openActiveMenu()`: keyboard reach into the menu bar (`index.ts:195-290`) | no menu bar exists — menus fold into contextual bars and action menus (`NotebookBar`, `KernelMenu`, `NotebookActionsMenu`; `RecentsSection.kt`'s record); every act the menus carry sits on its owner's surface or on its own row | adapted | the openers' operand is the menu bar the fold records as absent-by-decision; the acts they reach are verdicted where they live |
| `editmenu:undo`, `editmenu:redo` — delegates → the current widget's undoers registration (`index.ts:332-368`) | — | **absent** | `GAP-018`. The menu is exactly the touch reach the gap names — the history exists, nothing surfaces it |
| `editmenu:clear-current`, `editmenu:clear-all` — delegates → `clearers` (`index.ts:370-405`; notebook's `clear-cell-output`/`clear-all-cell-outputs`, the console's clear) | `CellAction.ClearOutputs`, `KernelMenu`'s "Clear Outputs of All Cells", `ConsoleViewModel.clear` — the registrants' own verdicts are present | adapted | the acts live on their owners' surfaces rather than behind a delegate |
| `editmenu:go-to-line` — delegate → `goToLiners` (`index.ts:407-440`; fileeditor's go-to-line, csv/tsv's `*:go-to-line`) | `reveal: MatchedLine` can land on a line — a search result uses it — but nothing asks for one | **absent** | `GAP-122`. The landing machinery has no reach; the csv/tsv registrant half is `GAP-098`'s already |
| `runmenu:run`, `runmenu:run-all`, `runmenu:restart-and-run-all` — delegates → `codeRunners` (`index.ts:839-908`: `run-cell-and-select-next`, `run-all-cells`, restart+`runAll` for a notebook) | the running surfaces' own bars: `NotebookBar`'s run-all and fast-forward, `KernelMenu`'s ranged runs, the console's input row — registrants verdicted on their rows (`notebook:run-cell` is `GAP-005`'s, `run-cell-and-select-next` `GAP-020`'s, the rest present/adapted) | adapted | the dispatch is the registry's machinery; the acts it would reach are on their owners' surfaces with their own verdicts |
| `runmenu:run-above`, `runmenu:run-below` — **declared, never registered**: no `addCommand` or `addSemanticCommand` mounts them (`index.ts:117-119`); the Run menu mounts the notebook's own run-all-above/run-all-below ids directly (`tracker.json:201-205`) | `KernelMenu`'s "Run All Above Selected Cell" and "Run Selected Cell and All Below" — the acts the menu mounts, verdicted `present` in `AUD-002` | adapted | dead consts upstream; the mounted acts exist on the kernel menu |
| `tabsmenu:activate-by-id`, `tabsmenu:activate-previously-used-tab` — `activateById` rows the Tabs menu populates per main widget; `previousId` tracked off `currentChanged.oldValue` (`index.ts:918-987`) | the strip's tap and the Running panel's Open Tabs rows activate by key (`activateMainAreaTab`); the MRU back stack keeps the previously-used one a Back away | adapted | activate-by-id is the strip's whole job; "previously used" is the stack's own order |
| `viewmenu:line-numbering`, `viewmenu:word-wrap` — delegates → `editorViewers.toggleLineNumbers`/`toggleWordWrap` on the current widget (`index.ts:751-765`, `:808-828`; notebook's `toggle-all-cell-line-numbers`, fileeditor's `editorConfig` writes) | `EditorSettings.lineNumbersShown`/`codeWrapEnabled` — rows on the Settings screen, global rather than per-widget | adapted | the toggles exist as persisted settings; `SettingsScreen` itself names `lineNumbersShown` as `notebook:toggle-all-cell-line-numbers`, whose own verdict is `T3`'s carried row |
| `viewmenu:match-brackets` — delegate → `toggleMatchBrackets` (`index.ts:790-807`; an `editorConfig` toggle on the editors) | `bracketPairAround`, fixed on in `CodeCellField` — `AUD-003` verdicted the extension `adapted` with its CM-divergence record | adapted | the capability is always on rather than toggled; the divergence record stands on the registrant's row |
| `viewmenu:show-minimap` — delegate → `toggleMinimap` (`index.ts:771-788`), whose only registrant is the notebook's virtual-scrollbar toggle | — | **absent** | `GAP-124`. No miniature exists anywhere; the carried registrant's `T3` row cites the same gap |
| `filemenu:logout`, `filemenu:shutdown` — `router.navigate('/logout', hard)`, and warn-dialog → `api/shutdown` after sessions+terminals shutdown (`index.ts:484-585`) | the server is the process: `JupyterServerLauncher` binds `127.0.0.1` on an ephemeral port behind a per-process token; the session-shutdown half exists as the Running panel's "Shut Down All" | adapted | no served session to log out of and no browser page to navigate — ending the server is the activity's own end |
| `helpmenu:get-kernel` — delegate → `getKernel` providers (`index.ts:1012-1030`) | `KernelSession.kernelName` drawn by `KernelIndicator` — `AUD-006` verdicted the notebook registrant `adapted`, the console one `absent`/`GAP-074` | adapted | the question exists as resolved state where a surface carries it; the console half's verdict stands on its own row |
| `apputils:activate-command-palette` — `modalPalette.activate()` or `activateById` on the rank-300 sidebar palette (`palette.ts:163-175`); searches every registered command | — | **absent** | `GAP-120`. No registry exists for a palette to search; acts are a closed set wired to their reach points |
| `apputils:toggle-header` — `MainAreaWidget.contentHeader.setHidden` on the current widget (`index.ts:344-370`) | `editorTopBarVisible` withdraws the editor's app bar when a short window plus edit mode needs the room — automatic, not a toggle | adapted | the space ask is answered by the window's measure; a manual hide would guard a surface the auto-rule already yields |
| `apputils:run-first-enabled`, `apputils:run-all-enabled` — execute the first / every enabled command from an arg list (`index.ts:707-770`) | — | **absent** | `GAP-120`. Dispatch machinery over the same absent registry — not a capability in itself |
| `apputils:move-section-to`, `apputils:move-section-back` — drag sections between registered sidebars, persisted in `section-mover:layout` (`movesectionsplugin.ts:44-118`) | one panel at a time — `SidePanel`'s `_onCurrentChanged` cite — and no panel sections exist to move | adapted | the operands — multiple stacked sidebars — are ADR-0054's recorded shape |
| `apputils:load-statedb`, `apputils:reset`, `apputils:reset-on-load` — the workspace statedb's load/reset/reset-on-load router commands; `reset` is `db.clear()` + save + reload (`index.ts:481-610`) | `rememberSaveable`/`rememberNavBackStack` plus `Disk*` repositories is the persistence story; no workspace layer exists for a statedb to serve | adapted | the restore question is answered by the persisted model; a reset's operand — a workspace — is `T4`'s `workspace-ui` read |

### The presentation — verdicts

`T3`'s entries, read down every `addCommand` in `apputils-extension`'s `themesplugins.ts`
(`:119-366`, the `matchMedia` listener at `:368-386`, the Settings/palette mounts at
`:402-528`), the fifteen presentation keys `codemirror/src/extension.ts`'s `editorConfig`
registry declares (`:646-1031`), `notebook-extension`'s five carried ids with their launcher
and palette mounts (`index.ts:2509-2545,2589-2609,4405-4441,2268-2293,4877-4910,5209-5233,
5289-5293`), and the VS Code half the intro names — `browser/parts/`, `cellParts/`,
`colorRegistry.ts` — read for the look's census, never counted. Theirs: `ThemeManager`'s
registered-`ITheme` registry and `setCSSOverride` (`thememanager.ts:270-429`), the `themes.json`
keys the commands write, `NotebookActions`'s `toggleAllLineNumbers`/`renderSideBySide`/
`renderDefault` (`actions.tsx:1792-1812,2043-2053`), the persisted `sideBySideOutputRatio`, and
`content.scrollbar` — the flag the "Show Minimap" label actually flips. Ours: `ThemeOverride`'s
tri-state resolved through `isSystemInDarkTheme()` at `MainActivity` into `JupyterDroidTheme`'s
two palettes (`EditorPalettes`/`ChromePalettes`/`ColorRamp`, the pair `resolve_vscode_theme.py`
verifies them against), `ThemeSettings`, the fourteen-field `EditorSettings` with `CodeFontSize`,
the three cell fields plus `FileEditorScreen`'s `CodeCellField`, the
`LineNumbers`/`GutterOverlay` gutter, and `CellFrame`/`CellStripe`/`CollapsedCell`/
`HiddenCellsRow`/`CellHeader`/`ExecutionLabel` — the chrome `cellParts/` maps onto.

Two findings and a census before the rows. `apputils:change-font` is **registered but never
mounted** — `themesplugins.ts:277-304` adds the command and no `execute`, `palette.addItem` or
menu item reaches it anywhere upstream; the `cssOverrides` keys it writes are the settings
editor's own surface, a `T4` read. Same shape as `T2`'s `runmenu:run-above` dead consts: a hole
in the reference's surface, not a local defect. Second: `lineNumbersShown` reaches the three
cell fields and `ConsoleInputRow` but not `FileEditorScreen`, whose `CodeCellField` takes the
parameter's own always-on default — recorded on the `lineNumbers` row rather than defected,
because the flag's documented scope is the notebook's (`EditorSettings.lineNumbersShown`). And
the `cellParts/` census maps one-for-one — `cellFocusIndicator` → `CellStripe`,
`cellExecution`/`cellStatusPart` → `ExecutionLabel`, `collapsedCellInput`/`collapsedCellOutput`/
`foldedCellHint` → `CollapsedCell`/`HiddenCellsRow`, `cellToolbars` → `CellHeader` — while
`cellDnd`'s drag gesture answers an act we carry as `CellAction.Move`'s menu rows
(`notebook:move-cell-up` verdicted `present` at `AUD-002`): a reach difference, recorded, that
no gap needs because the act exists.

Eight absences open new gaps — `GAP-125` (side-by-side layout), `GAP-126` (in-cell folding),
`GAP-127` (the invisible marks), `GAP-128` (line-level field chrome), `GAP-129` (caret
affordances), `GAP-130` (font family), `GAP-131` (localisation), `GAP-132` (styled scrollbars)
— and the virtual-scrollbar row cites `GAP-124`, which already names this carried id.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `apputils:change-theme` — `manager.setTheme(theme)` across a registered-`ITheme` registry: three shipped `*-extension` theme packages plus whatever plugins add; Settings-submenu and palette mounts carry one item per theme (`themesplugins.ts:119-160,427-449`; `thememanager.ts:280-309`) | `ThemeOverrideRow`'s Light/Dark segments → `ThemeSettings.override` over `DiskThemeSettingsRepository`; `JupyterDroidTheme` swaps the `EditorPalettes`/`ChromePalettes` pair | adapted | the pick exists on a closed set — `ThemeOverride`'s own docstring folds the open registry into two palettes because a third name would have nothing to name |
| `apputils:change-light-theme`, `apputils:change-dark-theme` — `setPreferred{Light,Dark}Theme` writes the theme `preferredTheme` falls back to while adaptive is on (`themesplugins.ts:162-238`; `thememanager.ts:68-92`) | the same two segments — `SYSTEM` resolves to whichever palette the system reports | adapted | the preferred pair is the palette pair; `ThemeOverride`'s docstring folds the two strings into the tri-state on the same reasoning |
| `apputils:adaptive-theme` — `toggleAdaptiveTheme` plus the `matchMedia('(prefers-color-scheme: dark)')` listener swapping preferred themes live (`themesplugins.ts:240-263,368-386`; `thememanager.ts:356-374`) | `ThemeOverride.SYSTEM` → `resolvesToDark(isSystemInDarkTheme())` at `MainActivity`; recomposition reacts the way the listener does | adapted | the same track-the-system state as one enum rather than a boolean beside two strings — `resolvesToDark` cites `thememanager.ts:68-92` itself, and `ThemeSettings`'s docstring records why SYSTEM defaults on where `adaptive-theme` ships `false` |
| `apputils:theme-scrollbars` — `toggleThemeScrollbars` flips `themes.json`'s `theme-scrollbars`, setting `data-jp-theme-scrollbars` and mounting `scrollbar.raw.css` while a theme asks (`themesplugins.ts:265-275`; `thememanager.ts:329-354`) | — | **absent** | `GAP-132`. No styled scrollbar exists for the attribute to colour — Compose scrollables draw the platform's transient indicators, and the terminal's missing scroll chrome is `GAP-090`'s own record |
| `apputils:change-font` — `setCSSOverride(key, font)` over the `*-font-family` `cssOverrides` keys — registered at `themesplugins.ts:277-304` and **never mounted**: no `execute`, palette item or menu reaches it (`thememanager.ts:270-278,394-429`) | `FontFamily.Monospace` in `codeTextStyle`, `JupydroidTypography` for the rest | **absent** | `GAP-130`. Dead surface upstream — the finding above — and no font choice exists here either; the `cssOverrides` keys it would write are the `settingeditor`'s own surface, a `T4` read |
| `apputils:incr-font-size`, `apputils:decr-font-size` — `_incrFontSize` steps `code-font-size`/`content-font-size1`/`ui-font-size1` by ±1px, the palette mounting all three keys (`themesplugins.ts:306-366,480-522`; `thememanager.ts:311-322,380-389`) | `SettingsStepperRow` steps `codeFontSizeSp` 8–32sp by `CodeFontSize.STEP`; `TerminalActionsMenu`'s ± owns the terminal's own size | adapted | the code scope's stepper exists and `CodeFontSize.STEP`'s docstring cites this mechanism as its source; the other two keys answer per-scope sizes nothing here names |
| `notebook:create-new` — the `docmanager` `new-untitled`+`open` pair per kernelspec's `kernelName`, launcher-mounted once per kernel (default at rank 0, its icon) and palette-mounted (`index.ts:2509-2545,2589-2609,5289-5293`) | `LauncherCommand.NEW_NOTEBOOK` — one card under "Notebook" → `createNotebook(untitledNotebookIn(cwd), DEFAULT_KERNEL_ID)` → `LaunchedDocument.CreatedNotebook` → a tab on the strip | adapted | one kernelspec collapses a card-per-kernel launcher to the single card `LAUNCHER_ITEMS` declares; the palette mount is a surface the closed act set does not have — the mint is the same |
| `notebook:toggle-all-cell-line-numbers` — `NotebookActions.toggleAllLineNumbers` flips `editorConfig.code`/`markdown`/`raw.lineNumbers` on the tracked notebook (`index.ts:4405-4441`; `actions.tsx:1792-1812`) | `EditorSettings.lineNumbersShown` — app-wide, persisted, on by default — gating the zero-is-off gutter on all three cell fields and `ConsoleInputRow` | adapted | a per-notebook triple-flip becomes one app-wide flag whose docstring names this command; `FileEditorScreen` takes the always-on default — the finding above's recorded half |
| `notebook:toggle-render-side-by-side-current`, `notebook:set-side-by-side-ratio` — `renderingLayout = 'side-by-side'` sets `jp-mod-sideBySide` and renders a markdown cell's source beside its output; the ratio command's dialog persists `sideBySideOutputRatio` (`index.ts:4877-4910,2268-2293`; `widget.ts:157-170`; `tracker.json:854-860`) | — | **absent** | `GAP-125`. A markdown cell renders or edits, never beside itself — no second layout exists for a ratio to divide |
| `notebook:toggle-virtual-scrollbar` — `content.scrollbar`, labelled "Show Minimap": the document miniature is the scrollbar (`index.ts:5209-5233`) | — | **absent** | `GAP-124`, opened at `T2` and already naming this carried id as its own cite |
| `lineNumbers` (`lineNumbers()` extension, default `true`, `extension.ts`) | `lineNumbersShown` → `Modifier.lineNumbers`/`GutterOverlay` on code, markdown and raw fields — a gutter that also carries breakpoints, diagnostics and the stopped line | adapted | the key is what the command row above flips, app-wide rather than per-notebook; the gutter does more than the extension's numbers alone, `LineNumbers.kt`'s own docstring's shape |
| `lineWrap` (`EditorView.lineWrapping`, `true` by default — `false` in `codeCellConfig`, `tracker.json:699-706`) | `codeWrapEnabled` governs code cells and `FileEditorScreen`; markdown and raw fields wrap unconditionally | adapted | the reference hardwires markdown/raw to wrap (`widget.ts:1335-1352`), so the toggle's honest scope is code — `codeWrapEnabled`'s docstring records that plus why it defaults `true` where the schema ships `false` |
| `theme` (`themes.getTheme(value)` — a CM editor theme from the registered set, default `'jupyter'`, `extension.ts:1037-1042`) | `EditorPalettes` dark/light — resolved VS Code themes, `resolve_vscode_theme.py`'s own check | adapted | fixed palettes rather than a registry — `change-theme`'s row's same collapse, one level down in the editor itself; ADR-0041's resolved-not-read rule |
| `customStyles` (`customTheme({fontFamily, fontSize, lineHeight})`, all `null` by default, `extension.ts:1000-1010`) | `codeFontSizeSp` covers `fontSize`; `CodeText.lineHeightFor` derives the height; `FontFamily.Monospace` is `fontFamily`, fixed | adapted | one of three sub-keys is a reach where all default null; `fontFamily`'s absent half is `GAP-130`'s own |
| `tooltips` (`tooltips()` as `createImmutableExtension` — machinery, not a toggle, `extension.ts:975-986`) | `HoverPopup`, `CompletionPopup`, `SignatureHelpPopup`, `DiagnosticDetailPopup` — Compose popups anchored at `caretRect` | adapted | the tooltip space exists as per-kind popups rather than CM's one shared layer — `caretRect`'s own anchor math |
| `codeFolding` (`foldGutter()` — fold regions inside one editor, `false` by default, `extension.ts:646-652`) | — | **absent** | `GAP-126`. Folds here are cell-level (`CellFolds`, `CollapsedCell`, `HiddenCellsRow`; `GAP-016`'s all-cells pair sits at the same level); nothing folds a region inside one field's source |
| `cursorBlinkRate` (`drawSelection({cursorBlinkRate})`, 1200 ms default, `extension.ts:655-659`) | the platform caret `BasicTextField` draws | **absent** | `GAP-129`. No slot exists for the knob — a Compose field's caret blinks at the platform's rate |
| `dropCursor` (`dropCursor()` — the marker a text-drag's drop position draws, `true` by default, `extension.ts:798-800`) | — | **absent** | `GAP-129`. No drag-move of selected text exists for a drop marker to answer; cut/paste through the platform menu is the only move |
| `scrollPastEnd` (`scrollPastEnd()` — `null` for inline/cell editors, `false` default, `extension.ts:912-915`) | cell fields grow to content; `FileEditorScreen`'s scroll ends at the last line | **absent** | `GAP-128`. The reference itself refuses it for cells (`options.inline`), so the file editor is where the real absence lives |
| `rulers` (`rulers(number[])` — column guides, `[]` by default meaning none, `extension.ts:848-856`) | — | **absent** | `GAP-128`. No column guide is drawn on any field |
| `highlightActiveLine` (`highlightActiveLine()` — a wash over the caret's line, `false` by default, `extension.ts:670-676`) | — | **absent** | `GAP-128`. The active *cell*'s marker is `CellStripe`'s; the caret's own line draws nothing |
| `highlightWhitespace`, `highlightTrailingWhitespace`, `highlightSpecialCharacters` (`highlightWhitespace()`/`highlightTrailingWhitespace()`/`highlightSpecialChars()` — invisible-character marks, the last `true` by default, `extension.ts:679-704`) | — | **absent** | `GAP-127`. No invisible is drawn anywhere — `HighlightTransformation` styles syntax and the platform font renders whatever control characters arrive, where the reference shows marks |
| `translation` (`EditorState.phrases` — CM's own string table a locale overrides, `extension.ts:1055-1071`) | English literals throughout `ui/` and `theme/` | **absent** | `GAP-131`. `trans.__()` and this key have no surface; nothing in the app is localised |

### The hosted surfaces — verdicts

`T4`'s entries, read down every `addCommand` in `launcher-extension`'s `index.ts:62-157` (the
`Launcher` widget and the `maybeCreate` fill on an empty dock), `settingeditor-extension`'s
`index.ts:235-505` (the `settingEditorType` dispatch, the raw `JsonSettingEditor`, the staged
`save`/`revert` gated on `canSaveRaw`/`canRevertRaw`, and the file-borne `import`/`export`),
`shortcuts-extension`'s `index.ts:114-215` (five commands emitting `ActionRequest` signals into
the keybindings editor, read off `contextMenuHitTest`'s `data-shortcut` nodes),
`help-extension`'s `index.tsx:85-590`, `workspaces-extension`'s `commands.ts:126-560` plus its
indicator plugin at `index.ts:76-150`, `logconsole-extension`'s `index.tsx:213-300`,
`pluginmanager-extension`'s `index.ts:119-170`, `extensionmanager-extension`'s
`index.ts:158-200`, and `apputils-extension`'s `notificationplugin.tsx:401-780`,
`licensesplugin.ts:142-210` and `index.ts:303-330,797-825`. Theirs: a `Launcher` mounted into
`main` and re-minted when the dock empties; a `SettingEditor` whose UI form and JSON form are
tracked widgets over a staged raw buffer; a keybindings editor plus the `displayShortcuts`
dialog tabulating the current widget's bindings; the Help plugin's About dialog, forum
`window.open` and `help:open`'s URL-in-tab; ten `workspace-ui:` acts over `IWorkspacesModel`'s
named, URL-keyed layout snapshots; the `LogConsolePanel` window with `ILogger` checkpoints and
level; the plugin list and extension-manager panels; and the notification machinery —
`Notification.manager` toasts plus the centre anchored on the status bell — beside
`licensesUrl`'s widget and report and `Printing.getPrintFunction`. Ours: `SidePanelRail`'s `+`
and `LauncherNavigation`'s empty-main fill over `LAUNCHER_ITEMS`; `SettingsScreen`'s flat row
list writing through the `*Settings` models to the `Disk*` repositories' JSON under `filesDir`;
the status items drawn on their owners (`KernelIndicator` on the document bar, `DirtyMarker`
on the tab); per-owner transient reports (`ExportReportSnackbar`, `SaveFailureBanner`,
`SearchReplaceReport`); `SymbolRow` as the edit-mode key surface; and kernel/server logging in
`run/jupyter_server.log` and logcat, which is ADR-0009's decision.

One finding the read surfaced, not a defect: `help:activate`, `help:close`, `help:show` and
`help:hide` are declared (`index.tsx:40-46`) and never registered or referenced anywhere — no
`addCommand` mounts them, tree-wide; the same dead-const shape as `T2`'s `runmenu:run-above`
pair and `T3`'s `apputils:change-font`, so their row follows the `editmenu:find` precedent.
Two more entries lean the other way: `help:licenses`, `help:license-report` and
`help:licenses-refresh` are deprecated delegates to the `apputils:` ids (`index.tsx:85-129`), so
both trios verdict together. And `apputils:licenses` would be inert even upstream on this
stack — its `execute` bails unless `licensesUrl` is set (`licensesplugin.ts:152`), a
page-config option the Lab server extension writes and bare `jupyter_server` does not.

Two real commands ride the surface the measure cannot see: statusbar:toggle
(`statusbar-extension/src/index.ts:49-95`) hides the status bar and persists `visible`, and
workspace-indicator:toggle (`workspaces-extension/src/index.ts:76`) hides the workspace
selector the topbar carries. Named, un-backticked — the `console:prompt-to-top` precedent — and
read here rather than verdicted in the table: the first presupposes a status-bar part, and what
one would carry is drawn on the owners instead (`KernelIndicator`, `DirtyMarker`, the running
panel's count) — the shape `application:toggle-header`'s row verdicted `adapted`, an absent
part the shell never has; the second presupposes the workspace layer the `workspace-ui`
row records `absent`, so it shares that ground without standing a row of its own.

Eight absences open new gaps — `GAP-133` (settings' raw and portable form), `GAP-134` (a
keymap to edit), `GAP-135` (a help/about surface), `GAP-136` (licenses), `GAP-137` (a
notification store and centre), `GAP-138` (print), `GAP-139` (named workspaces) and `GAP-140`
(an in-app log window). Four ids land `undocumented` — the plugin and extension managers —
with [ADR-0071](../adr/0071-nothing-extends-the-app.md) written by this slice, the verdict
[ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md) keeps for a
reason in nobody's writing.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `launcher:create` — `new Launcher` into `main` per call, and `maybeCreate` refilling an empty dock on every `layoutModified` (`index.ts:62-157`) | `SidePanelRail`'s `+` → `openLauncher`, and the same fill when the last document closes — `LauncherNavigation` cites `maybeCreate` itself; a card's `openDocument` then removes the launcher entry, which is the reference's own `ref:`-replace | adapted | the surface and both reaches exist — a rail button whose docstring names `addButtonEnabled`, and the auto-fill; the palette mount is `GAP-120`'s closed-set story |
| `settingeditor:open` — the `SettingsEditor` UI form, one panel per plugin schema, dispatched by `settingEditorType` (`index.ts:235-330`) | `SettingsScreen` — a flat list of the app's own persisted keys, stacked over the document by `openSettings` | adapted | the reach is the same — a settings surface in the main area — minus the per-plugin registry; the `*Settings` models are the closed equivalent of `schema/*.json` |
| `settingeditor:open-json` — the `JsonSettingEditor`: the user-settings JSON edited raw (`index.ts:344-410`) | the `Disk*` repositories' JSON files under `filesDir` are the raw form (`DiskEditorSettingsRepository`'s `editor-settings.json`), with no in-app surface that shows or edits them | **absent** | `GAP-133`. |
| `settingeditor:save`, `settingeditor:revert` — `content.save()`/`revert()` on the raw editor's staged buffer, `isEnabled` on `canSaveRaw`/`canRevertRaw` (`index.ts:436-463`) | writes land on the tap itself — `update` → the `Disk*` repository write; no staging exists for a save or revert to act on | adapted | the acts' operand is a staged buffer the live-apply list never makes — `SettingsScreen`'s rows persist per tap, so the question these commands answer is never asked |
| `settingeditor:import`, `settingeditor:export` — a file input writes an uploaded overrides object into the registry; `collectUserSettings` downloads `overrides.json` (`index.ts:464-505`) | — | **absent** | `GAP-133`. The raw form is the same repair's other half — an `overrides.json` is only meaningful to a surface that can read one |
| `shortcuts:add-keybinding`, `shortcuts:delete-keybinding`, `shortcuts:edit-keybinding`, `shortcuts:reset-all`, `shortcuts:toggle-selectors` — `actionRequested` signals into the keybindings editor, read off `contextMenuHitTest`'s `data-shortcut` nodes (`index.ts:114-215`) | `SymbolRow` — the fixed edit-mode key surface a soft keyboard can reach, its docstring carrying the selector-by-selector map | **absent** | `GAP-134`. No keymap exists for the CRUD to edit; the analog answers the keys' absence rather than exposing bindings |
| `apputils:display-shortcuts` — `displayShortcuts` dialog tabulating the current widget's keybindings (`index.ts:797-825`) | — | **absent** | `GAP-134`. A table of bindings presupposes the same absent keymap |
| `help:about`, `help:jupyter-forum`, `help:open` — the About dialog with version and wordmark, the forum's `window.open`, and open-a-URL-in-a-tab (`index.tsx:155-590`) | — | **absent** | `GAP-135`. No help or about surface exists; `help:open` is also the banner machinery's link runner |
| `help:activate`, `help:close`, `help:show`, `help:hide` — **declared, never registered**: no `addCommand`, `execute` or reference reaches them anywhere in the tree (`index.tsx:40-46`) | — | present | The `editmenu:find` precedent — dead consts upstream, so the id cannot fire there either; ours has no help widget for them to name in any case (the finding above) |
| `help:licenses`, `help:license-report`, `help:licenses-refresh`, `apputils:licenses`, `apputils:license-report`, `apputils:licenses-refresh` — the help trio deprecated delegates to the `apputils` ids (`index.tsx:85-129`); those open a Licenses widget over `licensesUrl`, re-init its model and download the report (`licensesplugin.ts:142-210`) | — | **absent** | `GAP-136`. Inert even upstream here — `licensesUrl` is a Lab-server page-config the Space's bare `jupyter_server` never writes |
| `workspace-ui:clone`, `workspace-ui:create-new`, `workspace-ui:delete`, `workspace-ui:export`, `workspace-ui:import`, `workspace-ui:open`, `workspace-ui:rename`, `workspace-ui:reset`, `workspace-ui:save`, `workspace-ui:save-as` — named, URL-keyed layout snapshots over `IWorkspacesModel`/`IWindowResolver` (`commands.ts:126-560`) | one unnamed workspace — the persisted `OpenDocuments`/`NotebookBackStack` model is the whole layout memory, and `architecture.md` §1 already lists `workspaces` out of scope | **absent** | `GAP-139`. Ten acts on one capability: a workspace that can be named, switched and carried — the folded row answers the open decision by verdict-sharing unit |
| `logconsole:add-checkpoint`, `logconsole:clear`, `logconsole:open`, `logconsole:set-level` — the `LogConsolePanel` window's toggle, and the `ILogger` ring's checkpoint/clear/level (`index.tsx:213-300`; `logconsole/src/logger.ts`) | kernel and server logging land in `run/jupyter_server.log` and logcat — ADR-0009's recorded answer; §1 names `logconsole` out of scope | **absent** | `GAP-140`. The logs' home is recorded; what is missing is the in-app window, its ring and its level filter |
| `pluginmanager:open`, `pluginmanager:refresh`, `extensionmanager:show-panel`, `extensionmanager:toggle` — the plugin list panel with its refresh, and the extension-manager view plus its enable toggle (`pluginmanager-extension/src/index.ts:119-170`, `extensionmanager-extension/src/index.ts:158-200`) | — | **undocumented** | [ADR-0071](../adr/0071-nothing-extends-the-app.md), written by this slice: the app is not extensible by construction, so a manager's operand — a plugin registry — does not exist; machinery-n/a, not a missing capability |
| `apputils:notify` — `Notification.manager` toast plus a centre entry, typed with `autoClose` and action buttons (`notificationplugin.tsx:401-500`) | the owner-drawn transient report — `ExportReportSnackbar`, `SaveFailureBanner`, `SearchReplaceReport` — shown where the event lived | adapted | the message exists in place, which is also why the next row's machinery has no operand: a report here is an event on its owner, not an entry in a store |
| `apputils:update-notification`, `apputils:dismiss-notification`, `apputils:display-notifications` — mutate a store entry by id, and open the `NotificationCenter` popup anchored on the status bell (`notificationplugin.tsx:500-780`) | — | **absent** | `GAP-137`. No notification store exists to update or dismiss, and no centre to open — the transient reports above never join a list |
| `apputils:print` — `Printing.getPrintFunction(currentWidget)` — a per-widget print the browser owns (`index.ts:303-330`) | — | **absent** | `GAP-138`. The adjacent surface is `NotebookExport`'s convert-and-share; printing the view itself is Android's `PrintManager`, unreached |

### The walk

Four paths end to end — a document through the shell's tab machinery, a settings write through
its disk store, a theme override through the palettes, a launcher card through its destination.
**A document:** launch builds the one process-scoped `AppContainer` in `JupydroidApplication` —
each `Disk*` store's `loadValidated` paying its read on the main thread inside it, `GAP-070` —
→ `MainActivity` → `JupyterDroidTheme` → `MainNavigation`, where the shell's whole state is
`remember`ed beside the saveable `rememberNavBackStack` — `sidePanel`, `openTabOrder`,
`openDocumentPaths`, `liveTabTitles`, `openDocuments`, `consoleSession`, `browseRequest`,
`revealRequest` — → `NavDisplay` under `ListDetailSceneStrategy` measuring its own panes → a
rail tap setting `sidePanel` → the list pane's `entry<Explorer>` → `ExplorerScreen`,
`KeepTheListingFresh` re-reading the directory on the reference's own 10 s interval → a row tap
→ `viewModel.open` → `onOpenFile` → `rememberDocumentOpener` — the asked path recorded in
Recents on `applicationScope` — → `openDocument`'s `openOrReveal`: `openDocuments.at(path)`
matching by *live* path, `?.openedAs` resolving back to the key the entry carries — → the
`.ipynb` suffix split → `openNotebook`/`openFile` → `openInMainArea`'s `remove`+`add`, the
equal-key reveal that keeps two entries off one `contentKey` — → `entry<Editor>` inside
`tabbedPane` → `TabbedDocumentPane` → `MainAreaTabStrip` drawing `orderedTabs` — opening order,
not the stack's promotion order — → `EditorViewModel` registering `openDocuments.opened`
*before* its read → the screen's `LaunchedEffect(state.notebookId)` reporting the live path up
→ `openDocumentPaths[key]` → the strip's title and `openDocumentPath`'s explorer marking → a ✕
→ `rememberMainAreaCloseGate` — a dirty document queues a `SaveBeforeCloseDialog` whose Save is
`saveNow`'s continuation, a refused save never closing — → `closeMainAreaTab` re-selecting the
sibling by opening order → the entry leaves, its `ViewModelStore` clears, `onCleared` runs
`openDocuments.closed` and the farewell `persist()` → `reconcileOpenTabs` dropping the dead key
from the order and both maps — `46164a51`, because before it a renamed-then-closed key kept
answering `liveDocumentPath`, and a reopen of the old name whose open failed marked the renamed
file's row for a document that never arrived (`aClosedTabsLivePathStopsAnswering`) — → an empty
main area → `LauncherNavigation`'s `maybeCreate` fill. **A settings write:** `SettingsScreen`
collects each `observeSettings()` live → a row's tap → the `*Settings` model's callback →
`applicationScope.launch { repository.update }` → `withContext(ioDispatcher)` →
`writeMutex.withLock` → `mkdirs` → `writeText` → *then* the `MutableStateFlow` publishes —
`e31f319b`, since before that order a refused `writeText` broadcast a value the next launch
never kept, and two writes racing on the scope could leave file and flow disagreeing about
which update was last — → the same flow collected by the editor, file editor, terminal and
settings screens themselves → applied live, no restart — → a restart reconstructs the
repositories in `AppContainer` and `loadValidated` re-reads what the write left. **A theme
override:** the settings row → the same store → `DiskThemeSettingsRepository` →
`MainActivity`'s `collectAsStateWithLifecycle` on `observeSettings()` →
`ThemeOverride.resolvesToDark` → `JupyterDroidTheme` re-providing `LocalEditorPalette` and
`LocalChromePalette` — *static* locals, so the swap is one deliberate whole-tree recomposition
rather than per-node notification — → the same `loadValidated` read restoring it after restart.
**A launcher card:** `SidePanelRail`'s `+` or the empty-dock `maybeCreate` fill → `openLauncher`
— `remove`+`add` promoting rather than stacking a twin `data object` — → `entry<Launcher>` →
`LauncherViewModel.launch` → a `LaunchedDocument` → `rememberLaunchHandler`: a created notebook
or file `openDocument`s into the first path; an opened terminal takes `terminalGateway.open`'s
minted name to `openTerminal`; a console mints its `NotebookKernelSession` and publishes it into
`consoleSession` *before* `openStandaloneConsole` pushes — the single-holder rule — → the
launcher entry disposed of on a card's success, the reference's own `add(panel, 'main', {ref})`
→ `launcher.dispose()` — → and a refused card lands on `LauncherViewModel.error`, the one
failure surface this path owns.

What the walk found, beyond the rows above:

- **Growth is paid per recomposition on the tab path, once at launch on the persisted one.**
  `orderedAsOpened`'s reconcile is a filter+concat per stack change, and the strip redraws on
  any write to the three per-tab holders; `openDocumentPath` and `runningTabTitle` resolve per
  pass through `documentIn`-then-map. The theme override is the deliberate inverse: static
  composition locals make a palette swap a whole-tree recomposition in a single pass, which is
  why the swap lives in `MainActivity` and nowhere smaller. The asymmetric cost is launch —
  `GAP-070`, every `Disk*` file `loadValidated` on the main thread before the first frame —
  while steady-state is all flow replays, and the explorer's 10 s poll is the reference's own
  `DEFAULT_REFRESH_INTERVAL` rather than a tax this app invented.
- **Branching forks on the suffix, the dirty bit, the pane count and the kind.**
  `openDocument`'s `at`-then-`.ipynb` is the whole factory dispatch — one conditional where the
  reference holds a factory map, sized by there being one factory per type to dispatch.
  `openInMainArea`'s remove+add is the reveal. The close gate forks a dirty document into a
  queued dialog — the reference's one-dialog-per-dirty-widget flattened to one modal surface —
  and a clean one into a straight remove. `TabbedDocumentPane` leads with Up below two panes
  and nothing beside a browser. `openRunningSession`'s console arm can only *reveal* — a
  console row without a live tab cannot be synthesised, because `entry<Console>` draws from the
  one `consoleSession` — while the document arm re-enters the open path. The launcher
  dispatches on `LaunchedDocument`'s four kinds, and `resolvesToDark` is the tri-state's own
  fork.
- **Failure has an honest arm on the write path, and the walk named where it does not.** A
  refused settings write publishes nothing (`e31f319b`, red first in all six stores); a failed
  document open lands `EditorUiState.error` with the file's name in it; a refused card mint
  surfaces on `LauncherViewModel.error`; a dead key stopped answering at `46164a51`. The
  recorded silences: `importTitle`'s `createNotebook` and the explorer's `openNewTerminal` ride
  a bare `runCatching` — a user act that lands nowhere, unsaid — `GAP-144`. A `Console` key
  restored after a process death without its session composes a blank pane under a live tab —
  `GAP-142`. The same stale-key family's other wound, a tab renamed off `a.ipynb` still being
  `Editor("a.ipynb")` so that a file recreated at that name could never open, was `GAP-141`, closed
  by a key identity rather than a walk-time patch
  ([ADR-0095](../adr/0095-a-tab-is-keyed-by-its-opening-not-its-path.md)).
- **Coupling is five holders hoisted to `MainNavigation`'s scope and the `WorkspaceChange`
  nerve between them.** Nav entries own separate `ViewModelStore`s, so what crosses between
  them — the document registry, the live-path map, the strip's order, the OSC titles, the
  console's session — lives one level up and is handed down. `WorkspaceChange.Renamed` is what
  keeps the two "where is it" records coherent: the repository emits, the ViewModels adopt the
  moved `path`, the screens report it back into `openDocumentPaths`, and the strip, the close
  gate and the row marker read through `documentIn`-then-map. That is also why `46164a51` was a
  reconcile fix and not a marking fix — the map can only lie where no document remains to
  correct it. The five live in three `ViewModel`s of the activity where `browsedDirectory` is
  `rememberSaveable`: a rotation keeps the stack and, beside it, the strip's order, the live
  titles and every background tab's `documentIn`, the close gate's dirty read included
  ([ADR-0092](../adr/0092-open-documents-survive-the-activitys-relaunch-in-its-retained-scope.md)).

## Spaces and runtime — `AUD-010`

**The surface is not a JupyterLab package — it is the reference app's whole model, and the
partition claims none of it.** Every slice before this one counted `x:y` command ids because the
area was a JupyterLab extension; a Space's lifecycle is winlator's: a user browses a list of
installed *containers*, each a `xuser-<id>` directory inside the one rootfs the app extracts into
`files/rootfs` — so the containers live under `files/rootfs/home/` beside the image they share —
with a `.container` JSON inside, the whole tree behind a `.winlator/.rfs_version` stamp. Produced: **9** `ContainerManager` verbs (`getContainers`,
`activateContainer`, `createContainerAsync`, `duplicateContainerAsync`, `removeContainerAsync`,
`loadShortcuts`, `loadFiles`, `getNextContainerId`, `getContainerById`), **18** config keys the
create/edit form persists, a per-row Run button plus a five-item popup (File Manager, Edit,
Duplicate, Remove, Storage Info), a one-item Add toolbar, seven
`GeneralComponents.Type` parts — five `isVersioned` and downloadable-or-builtin, SOUNDFONT and
ADRENOTOOLS_DRIVER installable from file only — against fourteen `DefaultVersion` pins, two
version constants (`LATEST_VERSION = 19`, `UPDATE_WINEPREFIX_VERSION = 16`), and eight
`xenvironment/components/` classes the run side composes. All produced, never stored:

```sh
grep -n 'public.*(' reference/research_repos/winlator/app/app/src/main/java/com/winlator/container/ContainerManager.java
grep -o 'data\.put("[a-zA-Z_0-9]*"' reference/research_repos/winlator/app/app/src/main/java/com/winlator/ContainerDetailFragment.java | sort -u
cat reference/research_repos/winlator/app/app/src/main/res/menu/container_popup_menu.xml \
    reference/research_repos/winlator/app/app/src/main/res/menu/containers_menu.xml \
    reference/research_repos/winlator/app/app/src/main/res/menu/main_menu.xml
ls reference/research_repos/winlator/installable_components/ \
   reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/components/
grep -n 'LATEST_VERSION\|UPDATE_WINEPREFIX_VERSION\|getRFSVersionFile\|getVersion\|isValid\|clearRootDir\|resetContainerRFSVersions' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/RootFS.java \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/RootFSInstaller.java
```

**The lifecycle, end to end.** Identity is the directory name: `loadContainers()` scans
`files/rootfs/home` for `xuser-*` dirs **once, at each `ContainerManager`'s construction** — a
fresh one per fragment/launch — derives
`maxContainerId` from what it finds (`getNextContainerId()` is max+1), and the in-memory list is
then maintained incrementally — `containers.add`/`remove`, never re-read. Config lives inside the
dir: `loadData` is a per-key switch, so an absent key leaves the field's initializer — a partial
file loads field-by-field — and a parallel `putExtra`/`getExtra` extras bag on `Container` holds
state like `rfsVersion` and `wineprefixNeedsUpdate` that the form never shows. Acts:
`createContainerAsync` (which also extracts `container_pattern.tzst` into the new dir),
`duplicateContainerAsync` (`FileUtils.copy` under a fresh id), `saveData` in place,
`removeContainerAsync` (`FileUtils.delete` the dir — nothing at this SHA stops a session already
running it), and `activateContainer` — not a user act but the run path's mark, which repoints the
`home/xuser` symlink at `xuser-<id>` so the guest's `/home/xuser` lands on the launched container;
`XServerDisplayActivity` calls it itself (`XServerDisplayActivity.java:169`). Install:
`installIfNeeded` fires when `!isValid || version < LATEST_VERSION`;
`install` is `clearRootDir` — delete everything under `files/rootfs` except `home` and `opt`
(`home` keeps the containers and their wineprefixes, `opt` keeps `installed-wine`) — then extract `rootfs.tzst` in place, then on success write the
stamp and `resetContainerRFSVersions`, which clears every container's `rfsVersion` extra and
flags `wineprefixNeedsUpdate=t` where the old stamp was ≤ 16: a rootfs bump cascades into
per-container migration. The surfaces are the app itself — `ContainersFragment` is the drawer's
default, `ContainerDetailFragment` the form over all eighteen keys, `ContainerFileManagerFragment`
browses the dir, `ShortcutsFragment` lists per-container `.desktop` entries with
`ShortcutSettingsDialog`'s per-entry overrides, `SettingsFragment` picks component versions. The
run side composes `EnvironmentComponent`s in `XServerDisplayActivity`
(`startEnvironmentComponents` clears `files/tmp` first) and `GuestProgramLauncherComponent`
execs `box64 <argv>` through `ProcessHelper` with a container-built env; `onDestroy` stops them all. Termux is the same shape
with exactly one environment and the stronger install — extract to `usr-staging`, then
`renameTo(files/usr)` — and IntelliJ's `python/src/com/jetbrains/python/sdk/` is a registry of
external interpreters, never a shipped image. JupyterLab and VS Code own nothing here; like
AUD-007's Termux half, this area is reference-shaped prose and reproduction greps, not partition
ids.

**Ours** is the same shape in two bands that do not meet. The *unwired* band is the lifecycle
the port declares: `domain/model/` `Space`/`SpaceConfig` (name, `pythonVersion`, `createdAt`;
`BASE_SPACE`, `space-<n>` ids from `nextSpaceId` over the scan, `spaceWithId` field-by-field
defaults, `requireDeletableSpace`), `domain/port/SpaceRepository`'s four ops
(spaces/create/rename/delete) behind `DiskSpaceRepository` (`config.json` inside the dir, id
from the name, refuses to delete `base`) and `FakeSpaceRepository`, with
`SpaceRepositoryContract` pinning both — and zero production callers: `AppContainer` never
constructs one, the UI never names it, the port-surface counts are `WorkspaceRepository`'s
same-named ops colliding. The *live* band is the base Space's own chain: `tools/rootfs/build.sh`
→ `assets/spaces/base/rootfs-arm64-v8a.tzst` (arm64 only; `jniLibs/{arm64-v8a,x86_64}` both ship
`libbusybox`/`libloader`/`libpython3`/`libshebang` — the ABI gate is `SpaceAbi`/`AndroidBaseSpace`,
not the package) → `BaseSpaceInstaller` (extract in place, no clear — `NotebookWorkspace`'s KDoc
stakes the workspace on it: `home/jupydroid` ships empty in the archive and the never-wipe write
is what makes a bump safe; `.jupydroid/rootfs-version` stamped last; loader+interpreter+busybox+
console-script links and `SpaceKernelSpec` rewritten every launch) → `BaseSpaceServer` (one lazy
shared server, mutex, generations) →
`JupyterServerLauncher` (ephemeral port, `jpserver-<pid>.json` via `JupyterRuntimeFile`, token,
UDS kernels, contents rooted at `NotebookWorkspace`) over `SpaceRuntime`'s ProcessBuilder+env
(`JUPYDROID_SPACE_ROOT`, `PYTHONUSERBASE`, `PYTHONHOME`, `LD_LIBRARY_PATH`) →
`IdleServerStopper`; the `BaseSpace*` adapters (`KernelGateway`, `TerminalGateway`,
`KernelSessions`, `KernelSpecs`, `KernelDebugger`, `NotebookExporter`, `StdioLanguageServer`)
serve the wire ports off it, and `AppContainer` defaults to the fakes when `SpaceAbi` says no.
Outside `data/space/` the literal `spaces/` appears only in `NotebookWorkspaceMigration`,
`domain/model/Space`/`SpaceConfig`, and `domain/port/SpaceRepository` — `DiskSpaceRepository`
reaches the dir through `SpacesDirectory`, never the string. Produced:

```sh
python3 scripts/read_port_surface.py            # SpaceRepository 4 ops, ServerLauncher 3
python3 scripts/read_port_surface.py --unused
ls app/src/main/java/com/jupydroid/app/data/space/ app/src/main/java/com/jupydroid/app/data/disk/ \
   app/src/main/java/com/jupydroid/app/data/fake/
find tools/rootfs app/src/main/assets/spaces app/src/main/jniLibs -type f | sort
grep -rln 'spaces/' app/src/main/java/com/jupydroid/app/ --include='*.kt'
```

**Carried in, recorded not rejudged.** The Space's contents are other slices': the workspace
and its disk (`AUD-005`, `JD-003`, `JD-007`, `GAP-010`; `NotebookWorkspace` is the served root,
not this slice's), the kernel it spawns and the `KERNEL_ID` spec (`AUD-001`, `AUD-006`,
`GAP-007`), the shell terminado hands out (`GAP-003`), the packages inside (`JD-004`, `JD-005`).
The exec model is already decided ground: `ADR-0002` + `CLOSED-001` (jniLibs + launch-time
relink because `nativeLibraryDir` moves on updates), `ADR-0003` (musl, arm64-only rootfs —
where winlator and termux simply hold `targetSdkVersion 28`), `ADR-0004`/`0005`/`0006`/`0008`
(SIGSYS, getaddrinfo-over-resolv.conf — `DnsServerSource` is its arm — busybox suid-out, the two
cpython hazards), `ADR-0010`/`0011`/`0012`/`0013`/`0014` (Space-as-directory, site-packages
inside it, zstd, bytecode invalidation, no RFC-3987 tooling), `ADR-0022` (kernels start through
sessions; one tree is the contents root — `NotebookWorkspace` is that tree), `ADR-0024` (the
app stops its own server; `IdleServerStopper` is the arm), `CLOSED-009` (a pty works
in-process). The `rootfs-version` stamp is no ADR — it lives where it is implemented, in
`BaseSpaceInstaller`'s KDoc. Four KDocs already cite the
winlator shapes they adapt — `SpaceConfig`'s two `ContainerManager.java` line refs,
`DiskSpaceRepository`'s `ContainerManager` scan ref, `BaseSpaceInstaller`'s `.rfs_version`
trick, `TarZstdExtractor`'s "same archive shape through the same two libraries". The Jupyter wire
`BaseSpaceServer` serves was `AUD-001`/`003`/`006`/`007`/`008`'s; the Areas row stays
`unexamined` and no partition rows move — the verdicts land in `T2`–`T5`'s subsections below.

### The model and its lifecycle acts — verdicts

`T2`'s entries, read down both sides' own files. Theirs: `container/Container.java` (the
`.container` fields, `saveData`'s 19 `data.put`s against `loadData`'s no-`id`-case switch,
`checkObsoleteOrMissingProperties`' on-read merge, `extraData`),
`container/ContainerManager.java` (the once-at-construction scan, the five acts, `loadShortcuts`
/`loadFiles`), `container/Shortcut.java` (a `.desktop` entry plus its `[Extra Data]` override
bag), `ContainerDetailFragment.java` (the 18-key create/edit form), `ContainersFragment.java`
(the Run button, the five-verb popup, the gated Add), and `XServerDisplayActivity.java:164-229`
(where `activateContainer`, `wineprefixNeedsUpdate`, `wineVersion` and the shortcut overrides are
consumed). Ours: `domain/model/Space.kt` + `SpaceConfig.kt`, `domain/port/SpaceRepository.kt`,
`data/disk/DiskSpaceRepository.kt`, `data/fake/FakeSpaceRepository.kt`,
`data/space/SpacesDirectory.kt`, and the two contract suites that pin them.

```sh
grep -n 'public.*(' reference/research_repos/winlator/app/app/src/main/java/com/winlator/container/ContainerManager.java
grep -n 'case "' reference/research_repos/winlator/app/app/src/main/java/com/winlator/container/Container.java
grep -n 'putExtra\|getExtra\|\[Extra Data\]' reference/research_repos/winlator/app/app/src/main/java/com/winlator/container/Shortcut.java
grep -rn 'activateContainer\|wineprefixNeedsUpdate\|setWinePath' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/
python3 scripts/read_port_surface.py --unused    # 0 — and wrong: see the by-hand read
grep -rn 'SpaceRepository' app/src/main --include='*.kt' | grep -v WorkspaceRepository   # only its own files
grep -rn 'BUNDLED_PYTHON_VERSION\|pythonVersion' app/src/main --include='*.kt'
```

**Three of the surface paragraph's claims were wrong at this SHA and are corrected in place
above**: the containers are `xuser-<id>` dirs inside `files/rootfs/home/` — inside the shared
image, which is also what `clearRootDir`'s `home` exemption preserves — not under
`files/containers/`; `loadContainers()` runs once per screen's manager, not per call; and there is no
`onRemoveContainer`/`XrActivity`/`createDir`/`.sys` — `activateContainer` repoints the `home/xuser`
symlink and `XServerDisplayActivity` calls it itself. The same cadence read corrects ADR-0010's
"that is the reference's shape" — ours' per-op scan is stronger than the reference's
per-screen one, and the ADR now says so.

**The unwired read is by hand, and ADR-0067 says to record it that way.** `read_port_surface
--unused` reports zero because `rename`/`delete` mentions are `WorkspaceRepository`'s same-named
ops colliding; grep says `AppContainer` never constructs a `SpaceRepository` and no screen names
one — so the port's four ops are `present` as machinery and the surface over them is the
`absent` row. First slice where the backlog already owns repairs: an `absent` verdict here cites
the `JD-NNN` that schedules it (a picked-up gap is a JD, not a second record) and a `GAP-NNN`
only where nobody owns it. No defect surfaced — the sharp edges are absence-shaped or
deliberate, and the recorded-not-repaired findings sit under the table.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| Identity — `xuser-<id>` dir name is the id; ids derived from the scan, `getNextContainerId()` max+1 (`ContainerManager.java:43-64,227-229`) | `files/spaces/<id>` is the id; `DiskSpaceRepository.scan()` + `nextSpaceId` mints `space-<n>` over the listing | present | ADR-0010 takes the model wholesale; `SpaceConfigTest` pins gap-never-filled and deleting-the-highest-hands-it-back as the reference's own behaviour |
| Placement — containers live *inside* the shared image at `files/rootfs/home/xuser-<id>/`; the `xuser` symlink names the active one | Spaces are siblings under `files/spaces/`, `base` beside them | adapted | a container is a home dir inside the one image every container mounts; a Space is its own rootfs tree — `SpaceConfig`'s "the one every other Space shares a rootfs with" records the intent, and `JD-004` holds the open server-half |
| Scan cadence — once per screen's `ContainerManager`; the list is then `containers.add`/`remove`, never re-read for the manager's life (`ContainerManager.java:32,119,164` — a fresh one per fragment/launch) | `scan()` runs inside every op; nothing is cached | adapted | ADR-0010's recorded consequence, stronger than the reference's — no ours-op sees a stale list where a reference screen can for its own lifetime |
| What counts — only `xuser-*` directories are containers (`ContainerManager.java:52`) | any non-dot directory under `spaces/` is a Space (`DiskSpaceRepository.scan`) | adapted | `aDirectoryWithNoConfigIsNamedAfterItself` pins it — a Space made by hand in a terminal still lists; the dot rule is the workspace listing's own |
| The config file — `.container` JSON inside the dir (`Container.java:238-240`) | `config.json` inside the dir (`SPACE_CONFIG_FILE_NAME`) | present | same placement; ours is readable by eye (ISO `created`) and writes no `id` — the next row |
| Field tolerance — `loadData` is a per-key switch; an absent key leaves the field's initializer; a malformed config throws `JSONException` that `loadContainers`' one catch turns into an *empty* list (`Container.java:314-390`, `ContainerManager.java:64`) | `StoredSpaceConfig` is all-optional over `defaultSpaceConfig`; a malformed or unreadable file falls back per-Space (`DiskSpaceRepository.read`) | present | the same partial-file rule — `aConfigStatingOnlySomeFieldsKeepsTheOnesItStates` pins it and ADR-0010 names it — with ours' blast radius per directory where the reference's single catch drops the whole list |
| `id` in the file — `saveData` writes `data.put("id", id)` and `loadData`'s switch has no `case "id"` (`Container.java:288`, `:314-388`) | never written | adapted | ADR-0010 — the stored copy is the reference's own two-truth risk, removed rather than ignored |
| The extras bag — `extraData` round-tripped verbatim; `putExtra`/`getExtra` carry `rfsVersion`, `wineprefixNeedsUpdate`, `appVersion` (`Container.java:202-228,306,370-373`) | `extra` JsonObject round-tripped verbatim | present | `unknownFieldsUnderExtraSurviveARename` pins it; `StoredSpaceConfig`'s KDoc cites `Container.java` for the same nested object |
| Read-side migration — `checkObsoleteOrMissingProperties` merges newly-added default keys *into* stored `envVars`/`wincomponents` values on load (`Container.java:392-426`) | none — absent fields take `defaultSpaceConfig`'s value | adapted | the merge answers "new keys appear inside a stored value"; no ours-field is a composite bag, so field-default covers what exists today — the first composite field reopens it |
| `createContainerAsync` — mkdir → `extractContainerPatternFile` (`container_pattern.tzst` + `common_dlls` copies) → `saveData`, on a background executor (`ContainerManager.java:98-125`) | `create` — `nextSpaceId` mint → `mkdirs` → write `config.json`, `suspend` on `Dispatchers.IO` | adapted | the bookkeeping half is identical; a created Space carries no env to provision because its runtime is base's shared rootfs — `JD-001`'s steering ground — and ours refuses loudly with the dir named where `createContainer`'s `return null` says nothing |
| Edit — `saveData` rewrites `.container` in place from the form's 18 keys (`Container.java:285-312`, `ContainerDetailFragment.java:204-229`) | `rename` writes the one mutable field | adapted | ours' config has one user-mutable field where the form has eighteen; the rest is the run-config rows below |
| `duplicateContainerAsync` — `FileUtils.copy` the tree under a fresh id, name suffixed "(copy)" (`ContainerManager.java:127-161`) | — | **absent** | `GAP-145` — the copy act exists on neither port nor disk impl |
| `removeContainerAsync` — `FileUtils.delete` the dir (`ContainerManager.java:163-165`) | `delete` — `deleteRecursively`, and `requireDeletableSpace` refuses `base` before lookup | adapted | the same arm plus the refusal ADR-0010 records — the reference can delete anything, including the container a session is running |
| `activateContainer` — repoint `home/xuser` at `xuser-<id>`; the launch reads through the symlink (`ContainerManager.java:67-72`, `XServerDisplayActivity.java:169`) | no active Space; `BASE_SPACE` is the compile-time answer | **absent** | `JD-005` owns the switching capability — the active Space rooting the explorer and binding new notebooks |
| `getContainerById` — linear scan, null on miss (`ContainerManager.java:231-234`) | `spaceWithId` throws `IllegalArgumentException` naming the asked id and the roll | adapted | the shared refusal `SpaceConfig.spaceWithId`'s KDoc states — a miss is a programming error both impls refuse identically, which `SpaceRepositoryContract` holds them to |
| `loadShortcuts` + `Shortcut` — per-container `.desktop` entries with icons, and each entry's `[Extra Data]` overrides the container's run config at launch (`ContainerManager.java:167-200`, `Shortcut.java`, `XServerDisplayActivity.java:205-218`) | — | **absent** | `GAP-146` — no saved per-entry launch exists; the document-open path is the only entry into a Space and `JD-005`'s binding is the nearest shape |
| `loadFiles` / `ContainerFileManagerFragment` — browse the container dir (`C:`, drives, Documents, Favorites) (`ContainerManager.java:202-225`) | the explorer browses `NotebookWorkspace` — `home/jupydroid` *inside* the base Space's rootfs | adapted | the browse act exists, aimed at the served tree rather than the env dir — nobody browses `usr/` — and a user Space's browse is `JD-005`'s rooting |
| `StorageInfoDialog` — `FileUtils.getSizeAsync` over the container's `drive_c`, its cache and the global cache (`contentdialog/StorageInfoDialog.java`) | — | **absent** | `GAP-147` — no surface measures a Space dir, and `base`'s 268 MiB makes it the size most worth answering |
| Per-container env — `envVars` is a `.container` key the form edits (`Container.java:20,78-80`) | `SpaceRuntime`'s env is fixed for the one Space | **absent** | `JD-001` owns it — per-Space `PYTHONUSERBASE`/`HOME`/`JUPYTER_RUNTIME_DIR` generalising what `base` already ships |
| The run-tuning keys — screenSize, graphicsDriver(+config), dxwrapper(+config), audioDriver(+config), wincomponents, drives, hudMode, startupSelection, cpuList×2, desktopTheme, box64Preset (`Container.java:33-49`) | none — a Python env has no display, driver or wineprefix to tune | adapted | the keys' operand is the wine/display stack; the ours-analog run config is the kernelspec binding — `JD-005`'s, not a key bag's |
| Interpreter pick — `wineVersion` chooses `container-pattern-<ver>.tzst` at create and `rootFS.setWinePath` at launch (`ContainerManager.java:110-111,247-269`, `XServerDisplayActivity.java:188-191`) | `pythonVersion` is written into `config.json` and round-tripped, but nothing consults it — `INTERPRETER_PATH` is `usr/bin/python$BUNDLED_PYTHON_VERSION` | **absent** | `GAP-148` — the selection machinery is the absence: the field is honest today (one Python exists) and inert by construction, and a hand-edit would make it lie because the runtime never reads it |
| Migration flag — `wineprefixNeedsUpdate` in `extraData`, set by `resetContainerRFSVersions`, read at launch, cleared by `wineboot -u` (`RootFSInstaller.java:33-45`, `XServerDisplayActivity.java:171-183`) | `extra` bag exists; no flag is written and no migration act reads one | adapted | the carrier is present and round-trips — `unknownFieldsUnderExtraSurviveARename` — and no schema move has ever needed flagging; the first one writes into the same bag |
| `*Async` + `PreloaderDialog` — acts run on per-call single-thread executors behind a progress dialog (`ContainerManager.java:74-96`) | `suspend` ops on `Dispatchers.IO`; no surface calls them | adapted | the machinery half is the suspending port — and both sides race identically, a per-call executor being no more serialized than no mutex — while the progress surface is `JD-002`'s |
| Confirm — `ContentDialog.confirm` gates duplicate and remove (`ContainersFragment.java:156,165`) | `requireDeletableSpace` refuses `base`; nothing asks | adapted | the ask is a surface act — `JD-002`'s "delete asking first"; ours' only confirm-shaped rule today is the refusal itself |
| The lifecycle surface — `ContainersFragment` (Run + five-verb popup + gated Add), `ContainerDetailFragment` over 18 keys, `ShortcutsFragment`, `SettingsFragment` (`main_menu.xml` is the app) | nothing — `AppContainer` never constructs a `SpaceRepository` and no screen names one | **absent** | `JD-002` owns the surface — list, create, rename and delete, async with progress; `FakeSpaceRepository`'s KDoc called itself "`AppContainer`'s default" before the container ever built one — corrected in this slice |

What the read found, beyond the rows:

- **`FakeSpaceRepository` tells the time by the wall, `DiskSpaceRepository` by an injected
  `Clock`.** The fake stamps `created` with `Instant.now()` where the disk impl takes the
  constructor's clock — so a ViewModel test that ever orders or asserts on `created` races the
  real clock. Not a defect today: the contract pins no `created` behaviour and nothing orders by
  it; recorded because the first test that needs it has no injection point.
- **`inSpaceOrder`'s doc says "by name", the code sorts by `it.id`.** For an unnumbered dir the
  two agree until the dir is renamed — `rename("scratch", "Zebra")` still sorts by `scratch`.
  The id is the stronger tie-break (unique, rename-stable where a name may duplicate), so the
  KDoc's word was the lie — corrected to "by id", not the sort.
- **`space--<n>` counts toward the mint.** `nextSpaceId`'s `toIntOrNull` accepts `-5`, so a
  hand-made `space--5` makes the next mint `space--4` — weird but lawful: every minted id is
  still `space-`-prefixed and unique, and the counter walks up toward zero. No guard added;
  `aDirectoryWithNoNumberIsIgnoredByTheCounter` pins the intent for unnumbered dirs.
- **A mistyped field costs the file's parse.** `{"name": 5}` is valid JSON that fails
  `StoredSpaceConfig`'s decode, so the Space reads as default — name and all — where a missing
  field keeps the rest. The reference's arm is strictly worse (the one catch empties the list),
  so this is a recorded edge, not a defect: tolerance is promised for *partial* files, not
  *wrongly-typed* ones, and the fix would be per-field `as?` decoding the contract never asked
  for.
- **Neither side validates a name, and only ours writes config atomically.** `create("")` and
  `etName`'s straight-through to `data.put` both mint a blank-named entry. Parity — noted so a
  later hardening does not mistake it for a regression. `FileUtils.writeString` can leave a torn
  file that reads as default; ours is written beside the file and renamed over it, so it cannot
  (ADR-0089).
- **A `rename` on `base` writes `config.json` into the extracted rootfs.** Lawful — the port
  refuses only `delete` — and the file survives a `rootfs-version` bump because the installer
  never clears (`T3`'s arm); the only hazard is a future archive shipping a top-level
  `config.json`, which the image does not carry today.

### The image and the install — verdicts

`T3`'s entries, read down both sides' own files. Theirs: `xenvironment/RootFS.java` (`isValid`,
`getVersion`, the `imagefs`→`rootfs` rename, `.winlator/.rfs_version`), `xenvironment/
RootFSInstaller.java` (`LATEST_VERSION`, `UPDATE_WINEPREFIX_VERSION`, `clearRootDir`, `install`,
`resetContainerRFSVersions`, the progress dialog), `core/TarCompressorUtils.java` (the extract
loop), `core/GeneralComponents.java` (`Type`, `installed_components`, `extractFile`,
`getInstallMode`/`isVersioned`), `core/DefaultVersion.java`, the top-level archives
(`rootfs.tzst`, `rootfs_patches.tzst`, `pulseaudio.tzst`, `container_pattern.tzst`) and the asset
component folders, `installable_components/` with its `index.txt` manifests,
`XServerDisplayActivity.java:428-445,1059-1070` (`setupWineSystemFiles` → `applyGeneralPatches`),
`MainActivity.java:82,102` (where install is kicked), and termux's `TermuxInstaller.java` (the
staging arm). Ours: `data/space/BaseSpaceInstaller.kt`, `TarZstdExtractor.kt`,
`SpaceKernelSpec.kt`, `BusyBoxApplets.kt`, `ConsoleScripts.kt`, `SpaceAbi.kt`,
`AndroidBaseSpace.kt`, `NotebookWorkspace.kt`, `data/disk/NotebookWorkspaceMigration.kt`,
`BaseSpaceServer.kt` + `StdioLanguageServer.kt` (the two places an install waits),
`tools/rootfs/` (`Dockerfile`, `build.sh`, `stage-app-artifacts.sh`, `busybox.config`,
`getaddrinfo_shim.c`, `shebang_exec.c`), the shipped `assets/spaces/base/rootfs-arm64-v8a.tzst`
read as a tar listing and two extracted members, `jniLibs/`, and the five test suites that pin
them.

```sh
grep -n 'isValid\|getVersion\|createRFSVersionFile\|imagefs' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/RootFS.java
grep -n 'LATEST_VERSION\|UPDATE_WINEPREFIX_VERSION\|clearRootDir\|resetContainerRFSVersions\|getContentLength' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/RootFSInstaller.java
sed -n '148,183p' reference/research_repos/winlator/app/app/src/main/java/com/winlator/core/TarCompressorUtils.java
grep -n 'enum Type\|isVersioned\|getInstallMode\|installed_components\|index\.txt\|getDestination' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/core/GeneralComponents.java
grep -n 'setupWineSystemFiles\|applyGeneralPatches\|rootfs_patches\|pulseaudio' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/XServerDisplayActivity.java
grep -n 'STAGING\|renameTo\|SYMLINKS\|chmod' \
  reference/research_repos/termux-app/app/src/main/java/com/termux/app/TermuxInstaller.java

zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst | tar -tv > /tmp/rootfs-tv.txt
grep -c ' -> /bin/busybox' /tmp/rootfs-tv.txt                                    # 304 applet links
grep -nE 'resolv\.conf|usr/local/bin/|home/jupydroid|kernels/python3/kernel\.json|^.* run/|tmp/' \
  /tmp/rootfs-tv.txt
zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst \
  | tar -xO usr/share/jupyter/kernels/python3/kernel.json                        # the stock spec
grep -n 'installedVersion\|bundledVersion\|stampVersion\|linkLoader\|linkInterpreter\|relink' \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceInstaller.kt
git log -G 'BUNDLED_ROOTFS_VERSION' --format='%h' -- \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceInstaller.kt | wc -l   # 9: add + 8 bumps
```

**One of the surface paragraph's claims was wrong at this SHA and is corrected in place above**:
`GeneralComponents.Type` has seven members but only five are `isVersioned` and downloadable —
SOUNDFONT and ADRENOTOOLS_DRIVER install from file alone — and `DefaultVersion` pins fourteen
names, not one per type (ZINK, VIRGL and friends ride the same class). The install description
checked out (`clearRootDir` minus `home`/`opt`, stamp on success, `resetContainerRFSVersions`);
what T1 did not mention is the launch side of that cascade — `setupWineSystemFiles` compares the
`appVersion`/`rfsVersion` extras and answers a mismatch with `applyGeneralPatches`: a fresh
`rootfs_patches.tzst` + `pulseaudio.tzst` extract and `WineUtils` tweaks before the
`wineprefixNeedsUpdate` → `wineboot -u` arm. It is ours' relink, not ours' install, that shares
that shape.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| The archive — `rootfs.tzst`: zstd over tar through commons-compress + zstd-jni, a top-level APK asset (`TarCompressorUtils.Type.ZSTD`, `RootFSInstaller.FILENAME`) | `rootfs-arm64-v8a.tzst`: the same format through the same two libraries, an ABI-named asset (`TarZstdExtractor`) | present | `TarZstdExtractor`'s KDoc already cites winlator for "the same archive shape through the same two libraries"; ADR-0012 picked zstd on the measured 16.5 s → 12.3 s |
| Image provenance — the tzst files are binary assets; no build recipe in the clone | `tools/rootfs/Dockerfile` on digest-pinned `alpine:3.21@sha256` + `requirements.lock` + `busybox.config`, with build-time probes (the trampoline dispatches, the kernel module asserts, `pylsp` starts, the shims interpose, the busybox fingerprint has no suid) | adapted | ours is reproducible from source where theirs is a blob — and the probes fail the image build rather than the device |
| ABI shape — one `rootfs.tzst` for every device; termux ships a `bootstrap-<abi>.zip` per ABI | `rootfs-arm64-v8a.tzst` alone; `jniLibs` ships both ABIs' ELFs but `SpaceAbi` lists arm64 only and `stage-app-artifacts.sh` deletes the x86_64 asset | adapted | ADR-0003 — x86_64's seccomp filter kills musl startup, so 33 MB nothing could execute is not shipped; the ELFs ride along so the APK installs for UI work |
| Install-needed — `!isValid \|\| getVersion() < LATEST_VERSION`; `isValid` = dir + stamp file, `getVersion` `Integer.parseInt`s the stamp's first line (garbage or empty throws; absent → 0) (`RootFS.java:37-44`, `RootFSInstaller.java:80-83`) | `installedVersion() != bundledVersion`; a missing or unparsable stamp reads as not-installed (`installedVersion`'s `toIntOrNull`) | adapted | stamp-only trust on both sides — but ours' `!=` re-flashes on a *downgrade* where `<` honours the newer tree, and a corrupt stamp self-heals where `parseInt`/`get(0)` crashes the check itself |
| Clear-before-extract — `clearRootDir` deletes everything under `files/rootfs` except `home` (the `xuser-<id>` containers) and `opt` (itself cleared minus `installed-wine`), then extracts in place (`RootFSInstaller.java:85-112`) | `TarZstdExtractor.extract` writes over the live tree and never clears | adapted | `home/jupydroid` ships as a bare dir and `pkgs/` is absent from the archive, so user trees survive *by absence* — `NotebookWorkspace`'s KDoc stakes the workspace on the never-wipe; the inverse — entries a new image *dropped* are never removed — is the accretion arm in the findings |
| Staging — termux extracts to `usr-staging`, builds `SYMLINKS.txt` links, then `renameTo(files/usr)`; a broken staging can never masquerade (`TermuxInstaller.java:154-217`) | none — the extract writes the destination directly | adapted | the stronger variant is termux's; both in-place installers lean on the stamp instead — a mid-write death leaves a mixed or cleared tree *without* the new stamp, so the next check re-extracts either way |
| Failure arm — extract answers `success`; on `false` a toast, a cleared tree with no stamp, and `installIfNeeded` retries next launch; termux shows an abort/try-again dialog that deletes and re-runs (`RootFSInstaller.java:70-74`, `TermuxInstaller.java:242-264`) | the extract throws → `BaseSpaceServer.startSpace` logs `failed` and rethrows; the old stamp stays → the next start re-extracts | adapted | all three self-heal through the unwritten stamp; ours' residue is a mixed new-over-old tree where theirs' is a cleared one, and logcat stands where they put a dialog — the surface half is the row below |
| The stamp — `.winlator/.rfs_version` under `.winlator/`, written only on extract `success`; an `IOException` there is `printStackTrace`d and silently retried next launch (`RootFS.java:50-59`, `RootFSInstaller.java:70-73`) | `.jupydroid/rootfs-version`, written only when the extract returned (`stampVersion`, after `entriesWritten`) | present | the same dot-dir-inside-the-tree placement and the same stamp-last ordering — a stamp-write failure means "not installed" on both sides, theirs by swallow, ours by rethrow |
| The version cascade — a rootfs bump clears every container's `rfsVersion` and flags `wineprefixNeedsUpdate` under ≤ `UPDATE_WINEPREFIX_VERSION`; the next launch's `setupWineSystemFiles` re-extracts the patch layer and `wineboot -u` migrates the prefix (`RootFSInstaller.java:33-45`, `XServerDisplayActivity.java:429-441`) | none — `BUNDLED_ROOTFS_VERSION` is one stamp, bumped eight times since `fc6d171b`, and there are no envs to flag | adapted | the extras-bag carrier exists (T2's row) but there is nothing to cascade into today; `pkgs/lib/python3.12` *would* strand on an interpreter bump — the findings carry that arm |
| Versioned parts — seven `GeneralComponents.Type`s (five `isVersioned`), fourteen `DefaultVersion` pins, builtin asset folders + `installed_components/<type>/<name>-<ver>.tzst` + `index.txt` manifests, per-type destinations (rootfs / `drive_c/windows` / cache), spinners with a remove-confirm, `generateCompactContainerPattern` repacking the per-container pattern | one blob, one version | **absent** | `GAP-149` — there is no per-part upgrade path: a busybox rebuild or a `jupyter_server` bump costs a whole-image rebuild, a `BUNDLED_ROOTFS_VERSION` bump and a full re-extract |
| The launch repair pass — `activateContainer` repoints `home/xuser`; `setupWineSystemFiles` compares `appVersion`/`rfsVersion` extras → `applyGeneralPatches` re-extracts `rootfs_patches.tzst` + `pulseaudio.tzst`, applies `WineUtils` tweaks, resets extras (`XServerDisplayActivity.java:169,429-441,1059-1070`) | every launch repoints the loader, the interpreter, `bin/busybox` + its 304 applets and `bin/shebang-exec` + its `usr/local/bin` links, and rewrites `kernel.json` | adapted | the same "the tree is repaired at launch because its pointers moved" shape — ours' mover is `nativeLibraryDir` on every app update (ADR-0002, CLOSED-001), theirs' is the version cascade; the exec model itself is the forced deviation, no parity target |
| `kernel.json` — the image carries ipykernel's stock spec (`python -m ipykernel_launcher`, `supported_encryption`, `kernel_protocol_version`) and nothing rewrites it — the container's wine path is `setWinePath`'s job at launch | `SpaceKernelSpec.install` rewrites the shipped spec on every launch: argv[0] → the `nativeLibraryDir` interpreter, `-m jupydroid_kernel`, `--log-level=DEBUG`, `metadata.debugger` | adapted | a kernelspec is JSON we own, and argv[0] is the one path execve permits (ADR-0002); the rewrite is total — the two keys it drops ride the findings |
| Extraction details — every entry `chmod 0771`, `FileUtils.symlink` deletes before linking, hardlinks fall through to plain copies, and `new File(destination, entry.getName())` has no `../` guard (`TarCompressorUtils.java:148-183`, `FileUtils.java:102-108`) | no mode bits at all, a lexical `resolveInside` guard, hardlinks materialised through `copyFrom`, character/block devices and FIFOs skipped | adapted | theirs needs the bits because `filesDir` execve worked at targetSdk 28; ours writes none by design — nothing under `filesDir` can run (ADR-0002) — and guards a crafted archive theirs does not |
| `etc/resolv.conf` — nothing in the app layer reads or writes one | the image ships it **empty** (0 bytes, mode 0755); DNS is answered by `DnsServerSource` reading `ConnectivityManager` per launch → `JUPYDROID_NAMESERVERS` → `getaddrinfo_shim.c` | adapted | ADR-0005 — musl resolves the *device's* `/etc/resolv.conf`, which Android has never had; the shipped file is residue the findings record |
| Legacy-dir migration — `RootFS.find` renames `files/imagefs` → `files/rootfs` (`RootFS.java:26-31`) | `NotebookWorkspaceMigration` moves the pre-Space notebooks tree into `home/jupydroid`, stamped under `.jupydroid/` beside the version stamp | adapted | the same "the path moved once; carry the old one forward" act, aimed at the workspace rather than the rootfs |
| When install runs — `MainActivity` at startup, after permissions, behind `keepScreenOn` and the progress dialog (`MainActivity.java:82,102`, `RootFSInstaller.java:47-77`) | lazily, inside the first `BaseSpaceServer.start` or the first `StdioLanguageServer` spawn | adapted | `baseSpaceLanguageServerPort`'s KDoc states the trade: an app that only browses notebooks never pays for a runtime it did not ask for |
| Install surface — `DownloadProgressDialog` fed by `getContentLength` (a full decompress pass to count, then a second pass to extract), a toast on failure, termux's abort/retry dialog (`RootFSInstaller.java:54-77`, `TarCompressorUtils.java:185-192`) | `entriesWritten` is counted in the same pass and read by nothing; the kernel indicator's "starting" is the only on-screen account of the install (`KernelStatus`' KDoc names it part of that state) | **absent** | `JD-002` — the Spaces surface this audit already found absent owns the first-install wait, and the count it would display is already produced |

What the read found, beyond the rows:

- **The `ash builtin.md` sentence was mangled by `e421345c`'s citation-drop** — "an ash builtin.
  Measured in `evidence/…`" became "an ash builtin.md`, which is also where the…" mid-sentence.
  Repaired in place under T2's KDoc precedent: the naming constraint now cites ADR-0045, which is
  the decision that records it.
- **The shipped `etc/resolv.conf` is empty, executable and owned by nothing.** Alpine residue —
  musl resolves the device's `/etc/resolv.conf`, which Android lacks, so the file is dead weight
  either way; `JUPYDROID_NAMESERVERS` is the live arm. Recorded because a reader who finds it in
  the image will ask who maintains it: nobody does, and it costs nothing.
- **The `kernel.json` rewrite is total.** The image's stock spec carries
  `supported_encryption: curve` and `kernel_protocol_version: 5.5`; `SpaceKernelSpec` keeps
  neither. Harmless today — `jupyter_client` defaults both — but any field a future ipykernel
  adds to the shipped spec is silently lost at install.
- **`pkgs/` strands on an interpreter bump.** `PYTHONUSERBASE=pkgs` grounds user site-packages
  under `pkgs/lib/python3.12/`; a rootfs bump shipping 3.13 leaves that tree installed but
  invisible — `pkgs/lib/python3.13/` starts empty and nothing flags the old one. That is exactly
  the arm `wineprefixNeedsUpdate` exists for, and all eight bumps so far kept python3.12, so it
  has never fired — recorded so the first interpreter bump prices it instead of discovering it.
- **`run/`, `tmp/`, `tmp/spaces/` are image-provided scaffolding.** `JupyterServerLauncher`'s
  `runtimeDir` and `SpaceRuntime`'s `TMPDIR` exist on device because the archive carries them;
  nothing at install creates them if a future image drops them.
- **Two install arms, two mutexes — benign by construction.** `BaseSpaceServer.startLock` and
  `StdioLanguageServer.ServerPool.startup` each serialize their own `installIfNeeded`, but a
  first kernel and a first LSP can race into the same `files/spaces/base` tree. Both writers lay
  the same bytes through delete-then-write, so interleaved writes agree; the worst arm is a
  `FileAlreadyExistsException` on a shared symlink path, which fails the install and self-heals
  on the next start.
- **Winlator's extractor has no `../` guard.** `new File(destination, entry.getName())` writes
  wherever the name resolves; `resolveInside` is ours' stronger arm, cheap because the archive is
  ours too — recorded so a later reader does not mistake the guard for parity.
- **`getContentLength` pays a whole second decompress for the progress number.** Ours counts
  `entriesWritten` inside the one pass — the measurement half of the absent surface already
  exists and is read by nothing.

### The runtime and the lifecycle — verdicts

`T4`'s entries, read down both sides' own files. Theirs: `xenvironment/XEnvironment.java` (the
ordered component list, `files/tmp` cleared at every start, the start/stop/pause/resume
broadcast), `xenvironment/EnvironmentComponent.java` (the four-method contract),
`xenvironment/components/` — `GuestProgramLauncherComponent.java` (the static `pid`/`lock`,
`start→stop`, `extractBox64File`/`copyDefaultBox64RCFile`, `execGuestProgram`'s env) and
`PulseAudioComponent.java` (the same static kill-first) as the process kind, `XServerComponent`/
`SysVSharedMemoryComponent`/`ALSAServerComponent`/`VirGLRendererComponent`/
`VortekRendererComponent` as the `XConnectorEpoll` kind (`start` a no-op once up), and
`NetworkInfoUpdateComponent.java` (`etc/hosts` + `tmp/ifaddrs` rewritten on every connectivity
broadcast) — `core/ProcessHelper.java` (`exec`, `splitCommand`, the pid read by reflection, the
`waitFor` thread, `suspendProcess`/`resumeProcess`, the `/proc` scan in `getChildProcesses`),
`core/EnvVars.java` (the space-split bag), `core/WineInstaller.java:23-79` (the
generate-wineprefix mode), `xconnector/UnixSocketConfig.java` (the `tmp/.…` socket paths), and
`XServerDisplayActivity.java` (`activateContainer`, `wineprefixNeedsUpdate`, the single-thread
executor that runs `setupWineSystemFiles` → `setupXEnvironment`, `terminationCallback` →
`exit()` → `restartApplication`, `onPause`/`onResume`/`onDestroy`, `singleTask` in the
manifest). Ours: `data/space/SpaceRuntime.kt`, `JupyterServerLauncher.kt` +
`jupyterServerProcess`/`jupyterServerArgs`, `JupyterRuntimeFile.kt`, `BaseSpaceServer.kt`,
`PerRunningServer.kt`, `IdleServerStopper.kt`, `AndroidBaseSpace.kt`, `SpaceAbi.kt`,
`SpaceLifecycleLog.kt`, `StdioLanguageServer.kt`'s `ServerPool`/`stop`,
`data/fake/FakeServerLauncher.kt`, `domain/port/ServerLauncher.kt`, `di/AppContainer.kt` (the
stopper wiring and `stopServer`), `JupydroidApplication.kt` (the `ProcessLifecycleOwner`
observer and the one-launcher construction), and the suites — `BaseSpaceServerTest`,
`IdleServerStopperTest`, `JupyterRuntimeFileTest`, `SpaceRuntimeTest`, `SpaceAbiTest`,
`JupyterServerWorkingDirTest`, `SpaceLogLineTest`. `PythonRunner` is carried in as the
short-lived arm of `SpaceRuntime` — androidTest probes only, no production caller.

```sh
grep -n 'public\|static\|synchronized' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/XEnvironment.java \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/components/GuestProgramLauncherComponent.java \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/components/PulseAudioComponent.java \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/components/NetworkInfoUpdateComponent.java
ls reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/components/
grep -n 'exec\|ProcessBuilder\|killProcess\|sendSignal\|waitFor\|splitCommand\|getChildProcesses' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/core/ProcessHelper.java
grep -n 'setupXEnvironment\|addComponent\|envVars.put\|terminationCallback\|stopEnvironmentComponents\|restartApplication\|activateContainer\|wineprefixNeedsUpdate\|singleTask' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/XServerDisplayActivity.java \
  reference/research_repos/winlator/app/app/src/main/AndroidManifest.xml
grep -rn 'proot' reference/research_repos/winlator/          # empty — the T1 correction below
grep -n 'fun \|val ' \
  app/src/main/java/com/jupydroid/app/data/space/SpaceRuntime.kt \
  app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt \
  app/src/main/java/com/jupydroid/app/data/space/JupyterRuntimeFile.kt \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceServer.kt \
  app/src/main/java/com/jupydroid/app/data/space/PerRunningServer.kt \
  app/src/main/java/com/jupydroid/app/data/space/IdleServerStopper.kt \
  app/src/main/java/com/jupydroid/app/data/space/AndroidBaseSpace.kt \
  app/src/main/java/com/jupydroid/app/data/space/SpaceAbi.kt \
  app/src/main/java/com/jupydroid/app/data/space/SpaceLifecycleLog.kt
grep -rn 'exitCode()' app/src --include='*.kt'               # the only hit is the definition
grep -rn 'serverLauncher\|idleServerStopper\|onAppStopped\|onAppStarted\|stopServer' \
  app/src/main/java/com/jupydroid/app/di/AppContainer.kt \
  app/src/main/java/com/jupydroid/app/JupydroidApplication.kt
```

**Two of the surface paragraph's claims were wrong at this SHA and are corrected in place
above**: there is no `proot` anywhere in the clone — the guest is `box64 <argv>` exec'd
straight off `files/` paths, which only `targetSdk 28` permits — and the component list it
named (`XorgComponent`/`XrComponent`/`SteamComponent`/`MidiManagerComponent`) does not exist
either; the eight classes at this pin are the two kinds the table's first row splits.
`XServerDisplayActivity` is `singleTask`, which is what makes the components' *static* pids
work: one instance exists at a time, and the static is what lets a recreated activity's fresh
component still kill the last instance's processes.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| The environment object — `XEnvironment` is an ordered `ArrayList<EnvironmentComponent>`: `startEnvironmentComponents` clears `files/tmp` then `start()`s each in add-order; stop/pause/resume broadcast the same way (`XEnvironment.java:55-70`) | no component list — the env is one `jupyter_server` process plus the kernels it spawns and one `pylsp`; `SpaceBackedPorts` is the composition (`JupydroidApplication.kt:140-170`) | adapted | the component shape answers services a Wine env needs — X, audio, shm, graphics — that a Python env simply has none of; ours' only component-shaped thing is `DnsServerSource`, and the kernels are the server's own over `--KernelManager.transport=ipc` — the same "local sockets, no TCP" instinct as their `tmp/.X11-unix/X0` (`UnixSocketConfig.java:8-13`, `jupyterServerArgs:158`) |
| Process components kill-first — `GuestProgramLauncherComponent` and `PulseAudioComponent` hold a `static int pid` + `static final Object lock`; `start()` calls `stop()` inside it (`GuestProgramLauncherComponent.java:36-41`, `PulseAudioComponent.java:28-33`) | `JupyterServerLauncher.start` calls `stop()` first (`JupyterServerLauncher.kt:51-52`) | present | the identical ordering for the identical reason — a second start may not leave two processes; the kill reaches further on theirs (the static pid kills across activity instances), which is the next row |
| One guest at a time — `singleTask` means one display activity, and the static pid is the belt under it: a second component's `start()` kills the previous instance's process by number | `BaseSpaceServer.startLock` plus the composition rule — one launcher built once per process (`JupydroidApplication.kt:83-91`); `JupyterServerLauncher.start(space)` *ignores* `space` — its `runtime`/`runtimeDir`/`rootDir`/`shell` are bound to `base` at construction (`AndroidBaseSpace.kt:56-61`) | adapted | the same one-env-at-a-time invariant, theirs enforced inside the mechanism across recreations, ours' by one-owner construction — and ours' port advertises a per-Space operand the impl cannot honour, the findings' first edge |
| Concurrent start — the same `synchronized(lock)` covers `start` and `stop`, so no stop is ever eaten by an in-flight start | `startLock` covers `connection()` end to end (`connection_startsOneServerEvenWhenTwoCallersRaceForIt`), but the launcher's own methods are unserialized — `idleServerStopper`'s `stop()` can land inside a start (the findings' race) | adapted | ours' airtight arm exists one class over — `StdioLanguageServer.ServerPool.startup` makes a racing `remove` queue and land — the launcher's lack of it is recorded rather than verdicted away |
| The scratch dir — `startEnvironmentComponents` clears `files/tmp`, and `setupXEnvironment` clears `rootFS.tmp` — the component sockets (`tmp/.X11-unix/X0`, `.sound/`, `.virgl/`, `.vortek/`) and `shm/` land there, so a dead env's sockets never collide with the next start (`XEnvironment.java:56`, `XServerDisplayActivity.java:490`) | `JupyterRuntimeFile.clear` removes `jpserver-*.json` alone; `spaces/base/tmp` (the Space's `TMPDIR` — `sigsys.log` appends there, kernel `ipykernel_*` scratch lands there) and `run/`'s `kernel-*.json` connection files are never cleared | **absent** | `GAP-150` — the whole-scratch-dir arm is the reference's; ours clears the one prefix that must not be stale and lets the rest accrete |
| The stop signal — `Process.killProcess(pid)` is SIGKILL to the component's own child, the pid read by reflection off `java.lang.Process` (`ProcessHelper.java:75-78`, `GuestProgramLauncherComponent.java:48`) | the `Process` handle is held and `destroy()` is SIGTERM — jupyter_server's own handler shuts its kernels down; `StdioLanguageServer.stop` goes one further: `shutdown`/`exit` → `waitFor` inside the grace → `destroy` (`JupyterServerLauncher.kt:62-65`, `StdioLanguageServer.kt:287-299`) | adapted | ours' is the gentler arm and needs to be — an ungraceful server kill orphans kernels mid-execution where theirs' game loop expects a hard stop; a SIGKILLed parent orphans children on *both* sides (theirs scans `/proc` for wine/`.exe` to suspend, never to reap) |
| Process death — `exec` runs a `waitFor` thread whose callback is `exit()`, which restarts the whole *application* (`ProcessHelper.java:85,108-115`, `XServerDisplayActivity.java:415-427`) | `isRunning()` is asked at the next `connection()`; a dead server is replaced in place and `generation`++ lets each `PerRunningServer` rebuild its clients (`BaseSpaceServer.kt:60-64`, `PerRunningServer.kt:31-39`) | adapted | theirs cannot half-recover — the env *is* the screen, so the app reboots; ours' is a lazy in-place restart `connection_startsAFreshServerWhenThePreviousOneIsNoLongerRunning` pins |
| Readiness — `preloaderDialog` closes when the first X window maps; a guest whose `exec` throws leaves it up forever (the catch swallows, `pid` stays `-1`, no callback ever fires) (`XServerDisplayActivity.java:243-249`, `ProcessHelper.java:86-88`) | `awaitConnection` polls `jpserver-*.json` for up to 60 s — or until the process dies — then `failStart` throws `IllegalStateException` carrying the server's own log tail (`JupyterServerLauncher.kt:75-93`) | adapted | a probe with a timeout and the log excerpt, vs a painted window as the readiness signal |
| The connection handshake — none: the guest needs no port and no auth; it execs and draws | `JupyterRuntimeFile` — `clear` before each start so a stale file never advertises a dead port, a truncated read means not-yet, a port-less file throws; the token is minted once per *launcher* so a restart keeps it (`JupyterRuntimeFile.kt`, `JupyterServerLauncher.kt:47-49`) | adapted | ours-only machinery — the server *is* the env's product so it reports where it bound; `JupyterRuntimeFileTest` pins all three arms |
| Env construction — `EnvVars` layered: activity base (`WINEPREFIX`, `WINEDEBUG`, MESA) + `container.getEnvVars()` + `shortcut.getExtra("envVars")`, `environment.put`ed **over** the inherited Android env (`XServerDisplayActivity.java:481-503`, `ProcessHelper.java:71-72`) | `SpaceRuntime.environment()` — one fixed map + per-call `extraEnv`, and `processBuilder` **clears** the inherited env first (`SpaceRuntime.kt:29-71,123-128`) | adapted | the per-container layer is T2's `JD-001` row; the replace-not-merge is recorded in place — "Android's PATH and linker variables would otherwise leak into the Space" — and `EnvVars(String)` splitting on space means theirs' bag cannot carry a value with a space in it |
| The command — `guestExecutable` is built by `+` interpolation and re-split by `splitCommand`'s hand-rolled quoting (`XServerDisplayActivity.java:498`, `ProcessHelper.java:136-181`) | `command()`/`jupyterServerArgs` are `List<String>` end to end; the one string-typed setting (`terminado_settings`) is passed a JSON list for the same reason (`jupyterServerArgs:149-174`) | adapted | ours' argv is never re-parsed; theirs' round-trips through a quoting rule one escape away from a torn arg |
| The cwd — `ProcessHelper.exec(command, envVars, rootDir)` runs the guest with cwd = the rootfs root (`GuestProgramLauncherComponent.java:115`) | `.directory(notebookWorkspace)` — `home/jupydroid`, the served tree (`jupyterServerProcess:131-134`) | adapted | `JupyterServerWorkingDirTest` pins it: `root_dir` defaults to cwd upstream, so the server's root and the kernel's HOME are pinned to one dir — a terminal opens inside the workspace, not at Android's read-only `/` |
| Pause/resume — `onPause` SIGSTOPs every wine/`.exe` child the `/proc` scan finds, `onResume` SIGCONTs them (`GuestProgramLauncherComponent.java:175-203`, `ProcessHelper.java:45-51,219-284`) | `ProcessLifecycleOwner` ON_STOP → `IdleServerStopper` waits for zero activity → 30 s grace → `serverLauncher.stop()` + `languageServer.stop(BASE_SPACE)`; ON_START cancels (`JupydroidApplication.kt:74-80`, `AppContainer.kt:178-189`) | adapted | freeze is a game's requirement — the same frame resumes; a notebook's is a backgrounded cell *finishing*, which `stoppedWhileACellRuns_waitsForTheCellBeforeStopping` pins and ADR-0024 records |
| Teardown — `onDestroy` and the drawer's `exit()` stop every component, then `exit()` restarts the app clean (`XServerDisplayActivity.java:329-333,415-427`) | nothing per-activity — the container is per-process, process death reaps the children, and the next `start` clears `run/` residue | adapted | the per-process container is the measured answer to per-activity being a server-per-rotation (`JupydroidApplication`'s KDoc cites the two-server sighting) |
| The per-launch repair pass — `extractBox64File` re-extracts the picked box64 whenever `box64_version != current_box64_version`, and `copyDefaultBox64RCFile` rewrites `etc/config.box64rc`, on every `start()` (`GuestProgramLauncherComponent.java:118-134`) | the relink pass — loader, interpreter, `busybox` + applets, `shebang-exec` links, `kernel.json` — every launch | adapted | T3's launch-repair row: the same "the tree is repaired at launch because its pointers moved" — theirs' mover is a component version, ours' is `nativeLibraryDir` on every app update (ADR-0002) |
| Network facts into the env — `NetworkInfoUpdateComponent` rewrites `etc/hosts` and `tmp/ifaddrs` inside the live rootfs on every `CONNECTIVITY_ACTION` (`NetworkInfoUpdateComponent.java:19-42`) | `DnsServerSource` reads `ConnectivityManager` at each process spawn → `JUPYDROID_NAMESERVERS` → the getaddrinfo shim (`AndroidBaseSpace.kt:40-44`, `SpaceRuntime.kt:112-116`) | adapted | ADR-0005; theirs keeps a *running* env updated, ours' is a spawn-time snapshot — a mid-run network switch is the findings' edge |
| An env nobody uses — none; the components live exactly as long as the activity | three timeouts: `cull_idle_timeout=300` for kernels, `shutdown_no_activity_timeout=300` for the server, `IdleServerStopper`'s 30 s for the backgrounded app (`jupyterServerArgs:175-184`, `AppContainer.kt:191-199`) | adapted | ADR-0024's arm — the app stops its own server; the reference's env IS the activity, so the question never comes up |
| An env that cannot run — none needed; one `rootfs.tzst` on every device because `files/` exec works at `targetSdk 28` | `SpaceAbi.of(Build.SUPPORTED_ABIS.first())` throws on x86_64 → `baseSpaceServer`/`baseSpaceLanguageServerPort` return `null` → `SpaceBackedPorts` fakes (`SpaceAbi.kt`, `AndroidBaseSpace.kt:74-81,93-105`) | adapted | ADR-0003's arm — the refusal is a port-level null the composition reads, not a crash; the fakes are the reason the UI still composes on an emulator |
| Deleting the env under its feet — `removeContainerAsync` `FileUtils.delete`s the dir while a session can still be running it; nothing in the run path checks | `requireDeletableSpace` refuses `base` — the only Space that can run — so the arm is unreachable today (`SpaceConfig.kt`, `DiskSpaceRepository.kt`) | adapted | T2's delete row already verdicted the act; from the runtime side the refusal closes the hole by construction, and it reopens the day a second Space can run — `JD-005`'s ground |
| Observability — `ProcessHelper` debugCallbacks feed the `LogView` pane; `save_logs_to_file` and per-pid `BOX64_TRACE_FILE` dirs under it (`ProcessHelper.java:91-134`, `GuestProgramLauncherComponent.java:147-167`) | stdout+stderr merged into `run/jupyter_server.log`, truncated at every start; `SpaceLifecycleLog` writes structured JSON to logcat under `JupydroidSpace`; `exitCode()` decodes `128+signal` (`JupyterServerLauncher.kt:73,135-136`, `SpaceLifecycleLog.kt`) | adapted | ADR-0009's recorded answer — the in-app window over the same stream is the gap `GAP-140` already names; `exitCode` is dead API, in the findings |
| The provisioning mode — `generateWineprefix` runs wine's own installer *inside* the env and snapshots the resulting prefix into `container-pattern-<ver>.tzst` (`WineInstaller.java:23-79`, `XServerDisplayActivity.java:546-549`) | the image is provisioned ahead of time — `tools/rootfs/build.sh` in Docker — and nothing is provisioned by running the env | adapted | same question — where a part comes from — answered at build time vs run time; theirs' is also what makes per-container `wineVersion` possible at all (T2's interpreter-pick row, `GAP-148`) |
| The second process — none: everything guest-side is a child of the one launcher pid | `pylsp` over stdio, one `RunningServer` per `space.id` under `ServerPool`'s `startup` mutex — multi-Space-shaped where the launcher is not (`StdioLanguageServer.kt:308-358`) | adapted | the asymmetry is the audit's spine in miniature: the pool is already keyed for N Spaces and the backstop's `languageServer.stop(BASE_SPACE)` names the one — `JD-001`'s ground from the other side |

What the read found, beyond the rows:

- **`JupyterServerLauncher.start(space)` ignores its operand.** `runtime`, `runtimeDir`,
  `rootDir` and `shell` are all bound to `base` at construction and the `Space` passed in is
  never read — `BaseSpaceServer` passes `BASE_SPACE`, which is the only thing that makes the
  call honest today. `start(space-1)` would `stop()` base's server and start base's again,
  silently. The general impl is `JD-001`'s; recorded so the port's shape is not mistaken for
  already-per-Space machinery.
- **The eaten stop.** The launcher serializes nothing of its own: `idleServerStopper`'s
  `stop()` can land between `jupyterServerProcess(...).start()` and `process = started`, and is
  then consumed — the new process runs, is tracked, and the stopper's job is already done, so a
  server stays up past ON_STOP until `shutdown_no_activity_timeout` (which never fires while a
  kernel exists). `StdioLanguageServer.ServerPool` shows ours' own airtight arm — a racing
  `remove` queues on `startup` and finds the registered server — and theirs'
  `synchronized(lock)` covers start and stop equally, so the hole is the launcher's alone. It
  is recorded rather than verdicted `defect` because the window is one suspension boundary wide
  and self-limiting, and because the reference carries the *wider* same-class hole: an
  `onDestroy` arriving before the setup executor reaches `environment = new XEnvironment(...)`
  leaves every component the executor then starts running forever
  (`XServerDisplayActivity.java:268-275,329-333`). The repair — serializing the launcher's
  start/stop the way `ServerPool` already does — is a `JD-NNN`, not this slice's fix.
- **`exitCode()` is dead public API.** Nothing calls it and no test pins it — a debugging
  leftover whose KDoc is the only account of how a seccomp kill (159 = 128+SIGSYS) reads. A
  `JD-NNN` either removes it or the first caller earns it; recorded so it is not mistaken for a
  port op.
- **`FakeServerLauncher` models a flag, not a process.** Its `isRunning` reports a boolean
  `start`/`stop` set — the real impl asks `process.isAlive` — and its `start` skips the
  kill-first the port's only real impl performs. No `ServerLauncherContract` exists, so the
  fake diverges from the load-bearing semantic `BaseSpaceServer`'s one-owner KDoc depends on
  and nothing fails. Bounded — the fake exists for UI composition and `BaseSpaceServerTest`
  uses its own `RecordingServerLauncher` — recorded because the contract is where kill-first
  belongs if it is port semantics.
- **`stopServer` names `BASE_SPACE`.** `languageServer.stop(BASE_SPACE)` is the backstop's
  only LSP arm while `ServerPool` is keyed by `space.id` — a second Space's `pylsp` would
  never be stopped. The mirror of the launcher's blind operand, from the caller's side.
- **DNS is a spawn-time snapshot.** Theirs rewrites `etc/hosts` on every connectivity
  broadcast; ours reads `ConnectivityManager` when a process spawns. A Wi-Fi→cellular switch
  mid-server leaves every Space process resolving through the old nameservers until the next
  start — the price of the env-var channel, bounded by the shim's own fallthrough.
- **Theirs' env bag cannot carry a space.** `EnvVars(String)` splits on `" "` and `.container`'s
  `envVars` value round-trips through it — an env var with a space in its value is
  unrepresentable (`EnvVars.java:32-35`). Ours' `extraEnv` is a real `Map`; noted as a format
  constraint, not a defect.
- **A SIGKILLed server can orphan its kernels.** `destroy()` is SIGTERM, which jupyter_server's
  handler answers by shutting its kernels — the covered arm. A hard kill (seccomp, OOM) leaves
  them reparented and dead-ended, their `run/kernel-*.json` files the residue `GAP-150`'s row
  names; theirs' `killProcess` orphans wine children the same way. Neither side reaps.

### The walk

Six transitions of the one lifecycle — an install, a create, a run, an idle, a second Space, a
reinstall — traced down ours' own call chain, the reference's matching act named where the
shapes fork.

```sh
grep -n 'startLock\|connection()\|startSpace\|installSpace\|isRunning\|generation\|StartedServer' \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceServer.kt
grep -n 'installIfNeeded\|installedVersion\|stampVersion\|BUNDLED_ROOTFS_VERSION\|relink' \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceInstaller.kt
grep -rn 'liveOrNull\|live\.get' \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceTerminalGateway.kt \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceKernelSpecs.kt \
  app/src/main/java/com/jupydroid/app/data/space/BaseSpaceKernelSessions.kt
grep -n 'fun \|mkdirs\|delete\|scan\|nextSpaceId\|requireDeletableSpace\|inSpaceOrder' \
  app/src/main/java/com/jupydroid/app/data/space/DiskSpaceRepository.kt
grep -rn 'SpaceRepository' app/src/main/java/com/jupydroid/app/di/    # empty — unwired
grep -n 'ProcessLifecycleOwner\|onStop\|onStart' \
  app/src/main/java/com/jupydroid/app/JupydroidApplication.kt
grep -n 'combine\|stopServer\|delay\|onAppStopped\|onAppStarted\|BASE_SPACE' \
  app/src/main/java/com/jupydroid/app/data/space/IdleServerStopper.kt \
  app/src/main/java/com/jupydroid/app/di/AppContainer.kt
zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst | tar -t | \
  grep -E '^(home|run|tmp|pkgs|\.jupydroid)'        # pkgs/.jupydroid absent — runtime writes
grep -rn 'activateContainer\|createContainerAsync\|restartApplication\|setupEnvironment' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/XServerDisplayActivity.java \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/container/ContainerManager.java
```

**An install** is paid by the first ask rather than the launch, which is why none of it sits on
`JupydroidApplication`'s critical path. `buildContainer` does three Space-touching things and
none extracts a byte: `baseSpaceServerLauncher` binds `spaces/base`'s runtime, runtime dir,
root dir and shell into the one `JupyterServerLauncher`; `SpaceBackedPorts` runs the `deviceAbi`
gate — null on x86_64, so the four wire ports become fakes — and `migrateNotebooksOnce` writes
`home/jupydroid` and the `.jupydroid/notebooks-migrated` stamp *inside* `spaces/base`, which is
what creates the `base` directory at all. The first ask that wants a kernel — a cell's
`execute`, counted in `ActiveCellRuns.whileRunning` from the tap so the backstop cannot kill the
boot it is protecting — reaches `BaseSpaceKernelGateway.connect` → `IssuedSessions.currentGateway`
→ `PerRunningServer.get` → `BaseSpaceServer.connection()` under `startLock` → `startSpace` →
`installSpace` → `BaseSpaceInstaller.installIfNeeded`: `installedVersion()` reads
`.jupydroid/rootfs-version`, and a miss or a mismatch pays `TarZstdExtractor.extract`
(`resolveInside`, delete-then-write, no mode bits, devices skipped); then the launch repair pass
runs *whether or not anything extracted* — the loader link, `usr/bin/python3.12` →
`libpython3.so`, `bin/busybox` → `libbusybox.so` with its 304 applets repointed relatively,
`usr/local/bin` → `bin/shebang-exec`, and `kernel.json` rewritten to the same
`nativeLibraryDir` interpreter — and only then `stampVersion`. A thrown extract or relink leaves
the old stamp, so the next ask pays the whole pass again; winlator's answer to the same failure
is a cleared tree and a toast. The language server holds a parallel arm: `ServerPool.startup`
calls the same `installSpace` on the first `pylsp` ask under its own mutex — two install arms
writing the same bytes, the stamp making the second one's pass extract-free.

**A create** is the band nothing calls. `DiskSpaceRepository.create` re-scans, mints
`space-<max+1>` off `nextSpaceId`, `mkdirs`, and writes `config.json` — name, `created`, a
`pythonVersion` nothing reads — and that is the whole act: a created Space is a config file in
an otherwise empty directory, with nothing to provision because its runtime is `base`'s shared
rootfs. The reference's `createContainerAsync` extracts `container_pattern.tzst` into the new
dir; ours' nearest shape is `JD-001`'s ground, where the runtime learns to want one.
`AppContainer` never constructs a `SpaceRepository`, so the only way a second directory appears
under `spaces/` today is a terminal or `run-as` — and the scan would list it all the same,
because the scan is the truth and a config-less dir reads as `defaultSpaceConfig`.

**A run** is the cached half of `connection()` — `cached` plus `launcher.isRunning()` — which is
the steady state: `gatewayKnowing` checks the session id against `IssuedSessions`' set,
`socketFor` reuses the per-session socket, and the frame goes down. The verbs that may boot the
runtime are the *act* verbs — `connect` and every kernel op through `gatewayKnowing`,
`TerminalGateway`'s `open`/`send`/`resize`/`attach`, `NotebookExporter`'s two, every
`LanguageServerPort` ask through `pool.serverFor` — and the verbs that may not are the *report*
and *undo* ones, passive through `liveOrNull`: `running()`, `close`, `disconnect`, `shutdown`,
`endingIfStillLive`, `KernelSpecs.specs` (which answers `base`'s declared catalogue while the
server is down, so the picker never boots a runtime to draw its rows). A first-of-generation
run pays the install paragraph minus the extract when the stamp matches: the relink pass is the
per-start cost, and `attach` lands a refused boot as `TerminalEvent.Ended` rather than a thrown
collect.

**An idle** is three clocks and a backstop. In-server, `cull_idle_timeout=300` reaps a kernel
with no websocket and `shutdown_no_activity_timeout=300` stops the server once no kernels
exist — a closed notebook's kernel survives at most ~6 min of one-minute cull polls. Out-of-app,
`ProcessLifecycleOwner`'s ON_STOP — registered in `JupydroidApplication.onCreate`, the process
lifecycle that lets a rotation pass but not a cover — drives `IdleServerStopper.onAppStopped` →
`combine(cellsInFlight, terminalsOpen)` → wait for zero → 30 s of *continued* zero
(`stoppedWhileACellRuns_waitsForTheCellBeforeStopping` pins the grace) → `stopServer`:
`serverLauncher.stop()`'s SIGTERM, which jupyter_server's own handler answers by shutting its
kernels down, plus `languageServer.stop(BASE_SPACE)`'s `shutdown`/`exit` → `waitFor` inside the
grace → `destroy`. ON_START cancels the countdown. What the stop leaves is `GAP-150`'s arm:
`run/`'s `kernel-*.json` and a `jpserver-*.json` the next start's `JupyterRuntimeFile.clear`
removes, plus `tmp/`'s `sigsys.log` and kernel scratch that nothing removes. The next ask
replays the run paragraph: `isRunning()` false → `startSpace` → generation++ → `IssuedSessions`
clears its set and `registry.endAll()` lands every held session on the truth, so a stale
session's next op throws `StaleKernelSessionException` — the UI's No Kernel — where winlator's
answer to the same event is `exit()` + `restartApplication`, the whole app relaunched.

**A second Space** exists, and nothing notices. `scan()` lists `space-1` after `base`
(`inSpaceOrder`: base, then numbered by number, then unnumbered by id), `spaceWithId` finds it,
`rename` writes its `config.json` in place, `delete` `deleteRecursively`s the dir —
`requireDeletableSpace` refusing `base` alone — and the runtime reads none of it. Every
Space-keyed operand is bound at composition rather than consulted at use: `JUPYDROID_SPACE_ROOT`,
`PYTHONUSERBASE`, `HOME`, `JUPYTER_RUNTIME_DIR`, `root_dir`, `spaceShellIn` and the kernelspec
argv are `spaces/base` paths the launcher was built with, so `connect(space-1)` would mint a
session on `base`'s server and merely tag it `spaceId`, and `languageServer.*(space-1)` would
spawn `base`'s `pylsp` keyed `space-1` — a process `stopServer`'s `BASE_SPACE` never stops. The
audit-spine question answers *by construction*: a second Space's **existence** is inert —
`base` ignores siblings because nothing but the repository's scan ever reads `spaces/` — while
its **use** is served by `base` wearing the asked id. The coupling leak is not live; it is the
port shape `JD-001` inherits.

**A reinstall** is the bump's next start. `BUNDLED_ROOTFS_VERSION` is compiled into the APK, so a
mismatched stamp can only exist across an app update — which kills the process and its server
with it. `installedVersion() != bundledVersion` against a *live* server is therefore
unreachable rather than locked out: the stamp this process wrote is the one every
`installIfNeeded` in it reads. The next first-ask extracts over the live tree:
`home/jupydroid` and `pkgs/` survive by absence from the archive, `.jupydroid/`'s two stamps
survive beside them, `run/` and `tmp/` residue stays (`GAP-150`), and entries the new image
*dropped* stay too — the accretion `clearRootDir` exists to prevent, the other half of T3's
never-clear verdict. The relink pass then re-points every `nativeLibraryDir` reference — the
archive's own `lib/ld-musl-aarch64.so.1` and `usr/bin/python3.12` files are replaced by links —
and `stampVersion` lands last, so a mid-write death leaves a mixed tree that reads as
not-installed.

What the walk found, beyond the rows above:

- **Growth is a stub per Space beside one shared image — the asymmetry the layout already
  prices.** `spaces()` rescans on every op, and `create`/`rename`/`delete` each pay a scan before
  their own write; `nextSpaceId` mints from the same listing — O(dirs) per call, the shape that
  costs ours nothing where the reference's once-per-screen list can be stale for its own
  lifetime. A created Space costs a `mkdirs` and a small `config.json`; a second *runnable*
  Space is the case that does not exist — `JD-004` prices the server half, and the rootfs half
  is `files/spaces/<id>`'s one-tree-per-Space layout: N copies of a ~270 MiB image or N views,
  nobody's yet. A generation bump rebuilds one `KernelSockets` per client and re-opens
  per-session sockets lazily on next ask, not eagerly.
- **Branching is three-armed everywhere the lifecycle forks.** `connection()` is
  cached-and-alive / dead-replaced / never-started; `JupyterRuntimeFile.read` is absent → poll,
  truncated → poll, port-less → throw; `installIfNeeded` is stamp-match → relink-only / mismatch
  → extract-then-relink; `deviceAbi` is arm64 → real / anything-else → null → fakes;
  `requireDeletableSpace` refuses `base` before the lookup it protects; the config `read` falls
  back per-field where the reference's one catch drops the whole list. The act-vs-passive split
  is the walk's fourth arm: verbs that *act* may pay a runtime, verbs that *report* or *undo*
  never do.
- **Failure is a stamp not written — and the walk found two arms where a failure still rides a
  live handle.** An extract or relink throw leaves the old stamp and the next ask repays
  everything; a launch timeout or a dead process throws `failStart` carrying the server's own
  log tail. The port-less runtime file's `IllegalArgumentException` escapes `awaitConnection`
  the same way but leaves `process` alive — reaped by the *next* `start`'s kill-first, a
  self-heal the retry pays for. The eaten stop (T4's finding) has a second boundary the finding
  did not name: a `stop()` landing inside `awaitConnection` is not consumed — it makes the
  start *throw*, the exception reaching the asker where the consumed arm reaches nobody. And
  `IssuedSessions`' bookkeeping is itself unsynchronized: `minted` lands after the whole
  session-mint REST call while `currentGateway`'s generation turnover `clear`s the set from
  another coroutine, so a session minted across a restart can register under the new
  generation, escape `StaleKernelSessionException`, and fail at the REST layer instead — the
  404 the registry exists to pre-empt, surfacing without its name. Same call T4 made for the
  eaten stop — recorded, a `JD-NNN`, not this slice's fix: the repair is one lock over the
  mint-check-mark span, or a mint that names its generation and is refused when it has moved.
- **Coupling is one composition, two stamps, three blind operands and a stub.** `SpaceBackedPorts`
  builds one `BaseSpaceServer` for four wire ports, `KernelSockets` is shared by gateway and
  debugger, and `IssuedSessions` ties session validity to the socket generation — a second
  `jupyter_server` cannot be started, only killed-and-replaced. `.jupydroid/` holds both the
  installer's `rootfs-version` and the migration's `notebooks-migrated` — and the ordering that
  produces: `migrateNotebooksOnce` *creates* `spaces/base` at composition, so the scan lists a
  stub `base` from the first launch on, and *installed* is a fact only `rootfs-version` knows —
  `SpaceConfig` exposes no installedness, which is `JD-002`'s list semantics priced early. The
  blind operands generalize the T4 finding: `connect(space)` tags rather than routes,
  `specs(space)` answers `base`'s catalogue for any ask, and `languageServer.*(space)` is the
  one place already keyed for N while `stopServer` names the one. And the ABI gate lives at the
  two spawn sites, not the mechanism — `baseSpaceServerLauncher` returns a real
  `JupyterServerLauncher` on x86_64 too, armed but unreachable because only the gated callers
  ever `start()` it (the stopper's `stop()` is a no-op on a never-started process; a second
  start-caller would learn at process-death speed what `SpaceAbi` already knew).
- **And one doc debt the walk corrected in place** — `AppContainer`'s `idleServerStopper` doc
  said "`MainActivity` drives it"; `JupydroidApplication` has driven it since the composition
  root moved to the process, and the doc now says so.

## Code health — `AUD-011`

**The surface is the repo's own health machinery — there is no reference command surface here, so
the partition claims none of it.** Every earlier slice examined a JupyterLab package or a
reference app's model; this one examines what the audit itself runs on: every rule the Working
Agreement and `docs/architecture/building-blocks.md` §5.1–5.6 state, what keeps each one true, and
whether each gate of ours is sound — does it fail closed, does it carry its own tests, and is it
wired anywhere a commit or a step actually runs (the AUD-007 Termux / AUD-010 Winlator precedent:
reference-shaped prose and reproduction greps, not partition ids). The two repairs the backlog
row's order was load-bearing for are already landed, so this snapshot describes the harness as it
ships: `GAP-006`'s three dependency rules are gated arms of `ArchitectureGuardTest` with `glob/`
inside `documentAndLanguageRoots`, and `GAP-008`'s source set compiles — `compileDebugAndroidTestKotlin`
rides the `unit-tests` hook's one Gradle call and the documented local command. Produced: **16**
pre-commit hook ids; **13** gates — `check-file-line-length.sh` plus twelve `check_*.py`, of which
ten are hook-wired and two are driver-bound because a device frame is their input
(`check_chrome_parts` inside `scripts/p7a-chrome-parts-driver.sh`, `check_node_fill` inside the
`p7a-1b-*` drivers); **12** measures (`read_*` and `resolve_vscode_theme`); **3** comment scanners
(`kotlin_source`, `script_source`, `xml_source`); and **34** `*_test.py` suites the
`maestro-gate-tests` hook runs `always_run`. The flows the evidence gates watch are produced too —
**206** `*-driver.sh` over **238** `.maestro/*.yaml`. All produced, never stored:

```sh
grep -c "id:" .pre-commit-config.yaml                                # 16 hook ids
ls scripts/check_*.py | grep -cv _test                               # 12, + check-file-line-length.sh
ls scripts/read_*.py scripts/resolve_*.py | grep -cv _test           # 12 measures
ls scripts/*_source.py                                               # 3 scanners
ls scripts/*_test.py | wc -l                                         # 34 suites
ls scripts/*-driver.sh | wc -l && ls .maestro/*.yaml | wc -l         # 206 / 238
grep -ln "check_chrome_parts\|check_node_fill" scripts/*-driver.sh   # the two driver-bound
grep -n "id:\|entry:" .pre-commit-config.yaml                        # what each hook runs
```

**What the references gate for themselves — real machinery this time**, read at the pins in
`docs/README.md`:

| Reference | Gates itself? | The health machinery it owns |
|---|---|---|
| `vscode` @ `3f3380c668d07649b953eb8278354b2f263ea6ff` | full | `build/checker/layersChecker.ts` — the `src/vs/` layer split as a TS-API checker over `tsconfig.<layer>.json` boundaries plus a `RULES` table of disallowed types — `build/hygiene.ts` (copyright header, indentation and unicode filters, the `.eslint-allowed-javascript-files` allowlist, `checkCopilotEnginesVersion`), `build/lib/checkCyclicDependencies.ts`; `build/lib/test/` tests the gates themselves (`layersChecker.test.ts`, `hygiene.test.ts`, `checkCyclicDependencies.test.ts`) |
| `jupyterlab` @ `7f0e690680a763d3c3db5dc0231803b0ba0e1e12` | full | `lint` = prettier + eslint (a second *typed* config) + stylelint + shellcheck; 30 workflow files — license-header, check-i18n, check-external-links, spell-check, zizmor, codeql-analysis, galata-flakiness-report, check-release, reject-staging-checks |
| `intellij-community` @ `406a27a8da071c59b052719fe6f1208c613e96b5` | full | Qodana (`.github/workflows/code_quality.yml`), `jewel-checks.yml`; the JPS `.iml`/Bazel module graph *is* the dependency gate — a module references only what it declares |
| `termux-app` @ `3df69d1da197dd9bd71a3bafd902dffd720576b4` | partial | six workflows — `run_tests.yml` (`./gradlew test`), `debug_build.yml`, `gradle-wrapper-validation.yml` |
| `vscode-jupyter` @ `f01d80ca0aa0dfdfa1d2e10a08943e12b776d155` | partial | `checkDependencies` gulp task run by `build-test.yml`; `codeql-analysis.yml` |
| `jediterm` @ `377b76e682a5f86bcbb18a318386f530dbebf5c1` | partial | `.github/workflows/test.yml` |
| `winlator` @ `fb66541b93a4eb3ee585a433b4c7b20544d58e40` | absent | `.github/` holds `FUNDING.yml` and an issue template — no workflows |
| the shipped rootfs | n/a | the artifact the gates watch, not a watcher |

That is **3 of 7** shipping the full shape, and the pattern they share is the one this repo already
follows: the layer/dependency gate exists *because the codebase is too big for review to hold it*
(VS Code's checker, IntelliJ's module model), and every gate is cheap enough to run per commit —
hygiene is a filter stream, not a build. Ours is the same shape at one module's scale: a Kotlin
guard test instead of a TS-API checker, Python gates instead of gulp filters, pre-commit hooks
instead of a pipeline.

```sh
ls reference/research_repos/vscode/build/checker/ reference/research_repos/vscode/build/lib/test/
grep -o '"lint": "[^"]*"' reference/research_repos/jupyterlab/package.json
ls reference/research_repos/jupyterlab/.github/workflows/ | wc -l
ls reference/research_repos/intellij-community/.github/workflows/ | grep "quality\|jewel"
ls reference/research_repos/termux-app/.github/workflows/
grep -rn "checkDependencies" reference/research_repos/vscode-jupyter/package.json \
  reference/research_repos/vscode-jupyter/.github/workflows/
find reference/research_repos/winlator/.github -type f
ls reference/research_repos/jediterm/.github/workflows/
```

**Ours — the claims map, re-run after the two repairs.** Every rule the Working Agreement and §5
state, classified **enforced** (a gate fails without it), **produced** (a command answers it and
nothing gates the answer), or **convention** (nothing keeps it true):

| Rule | Where stated | Enforced by | State |
|---|---|---|---|
| `domain` imports no `android.*` | §5.4 | `ArchitectureGuardTest.domainLayer_hasSources_withZeroAndroidImports` | enforced |
| `domain` imports no `data`/`ui`/`di` | §5.4 | `ArchitectureGuardTest.domainLayer_doesNotImportOtherLayers` | enforced |
| doc/language packages import no `android.*` — all six §5.2 names | §5.2, AGENTS.md | `ArchitectureGuardTest.documentAndLanguageModules_*` | enforced — `glob/` joined the roots with `GAP-006`'s repair |
| `ui` names no real adapter (`data.disk`/`space`/`intent`; `data.fake` is the DI default) | §5.4 | `ArchitectureGuardTest.uiLayer_namesNoRealAdapterPackages` | enforced — the rule as restated; the written one was already false |
| `data` names no `ui`/`di` | §5.4 | `ArchitectureGuardTest.dataLayer_namesNoUiOrDi` | enforced |
| real adapters named only inside `data/` and at the composition root (`di/`, `JupydroidApplication.kt`) | §5.4 | `ArchitectureGuardTest.realAdapters_areNamedOnlyInsideDataOrTheCompositionRoot` | enforced — a qualified-reference scan over code lines, so an inline `com.jupydroid.app.data.disk.*(` trips it and a KDoc `[…]` citation does not |
| `androidTest` compiles in the local gate | `GAP-008` | the `unit-tests` hook's one Gradle call + the documented command | enforced — `GAP-008`'s repair |
| files ≤ 500, functions 4–20, line ≤ 120, naming | AGENTS.md r.6 | `check-file-line-length.sh` + detekt `LargeClass`/`LongMethod`/`CyclomaticComplexMethod` + ktlint | enforced |
| formatters and lint wired into the build | AGENTS.md r.5 | the Gradle plugin wiring + the ktlint/detekt/lint hooks | enforced |
| hooks gate every commit | AGENTS.md r.7 | `.pre-commit-config.yaml` | enforced where installed — `pre-commit install` is opt-in and there is no CI backstop; a design fact, recorded |
| every `check_*`/`read_*`/scanner carries `_test.py` | AGENTS.md tooling table | `maestro-gate-tests`, `always_run` | enforced — 34 suites, milliseconds |
| comments cite what resolves | `docs/README.md` | `check_doc_citations.py` + `check_retired_doc_citations.py` | enforced |
| evidence names the app, not the house | AGENTS.md r.2 | `check_device_secrets.py` + `.gitignore`'s `evidence/**/*logcat*.txt` | enforced |
| flows observe kernel output | AGENTS.md | `check_maestro_output_assertions.py` | enforced |
| a gap has a name / one monospace home / palette entries draw / flows start seeded / seccomp / shebang-exec / chrome parts / node fill | AGENTS.md tooling table | the eight `check_*` | enforced — six hook-wired; `check_chrome_parts`/`check_node_fill` are driver-bound and live inside the flows whose frames they read |
| a `Fake…` per port, 1:1 | §5.3 | the `diff` below | produced, ungated — empty today |
| android-free per-package counts | §5.2 | the `for` loop below | produced, ungated — the *rule* is now gated for all six; what stays produced is the denominator |
| "max 2 levels of indentation", "no `any`", "early returns", "exception messages name the value" | AGENTS.md code style | nothing gates prose-level style — detekt's `NestedBlockDepth` is unset and its default allows 4 | convention |
| the gates' inputs stay honest (flow/driver/evidence naming) | ADR-0001 | `check_retired_doc_citations.py` covers the doc names; the step-code lockstep is convention | mixed |

Two of the table's mechanisms deserve their own words. **`ArchitectureGuardTest`'s arms prove red
by a planted violation recorded in a commit body**, not by a suite — the `layersChecker.test.ts`
idea at repo scale: a synthetic source planted, watched fail, reverted. The `check_*` gates
instead carry `_test.py` files that feed them fixtures, so "the gate fails closed" is proven
differently on the two sides — a `T4` soundness question, recorded here as the fact it is. And
**the driver-bound arms are wired by the flows themselves**, which is why `grep
"scripts/check_" .pre-commit-config.yaml` finds ten of the twelve `check_*.py` — no commit hook
could hold a gate whose input is a device frame.

```sh
# the documented local gate, and the source set it now compiles
grep -n "gradlew" AGENTS.md
grep -rn "compileDebugAndroidTestKotlin" AGENTS.md .pre-commit-config.yaml docs/architecture.md
# the dependency rules as they hold
grep -rn "^import com.jupydroid.app.data" app/src/main/java/com/jupydroid/app/ui/
grep -rln "com\.jupydroid\.app\.data\.\(disk\|space\|intent\)" app/src/main/java/com/jupydroid/app/
grep -rn "^import com.jupydroid.app.\(ui\|di\)" app/src/main/java/com/jupydroid/app/data/
# the produced counts §5.2 and §5.3 keep un-stored
for p in glob html import markdown syntax terminal theme richtext; do
  d=app/src/main/java/com/jupydroid/app/$p
  printf '%-10s %s of %s import android.*/androidx.*\n' "$p" \
    "$(grep -rlE '^import (android|androidx)\.' $d | wc -l)" "$(ls $d/*.kt | wc -l)"
done
diff <(grep -lE '^(interface|fun interface) ' app/src/main/java/com/jupydroid/app/domain/port/*.kt \
         | xargs -n1 basename | sed 's/\.kt$//' | sort) \
     <(ls app/src/main/java/com/jupydroid/app/data/fake/Fake*.kt \
         | xargs -n1 basename | sed 's/^Fake//;s/\.kt$//' | sort)
```

**Carried in.** The *behaviour* the gates protect is every earlier slice's. The gates' *inputs*
are those slices' ground: the `.maestro` flows the driver-bound arms read, the rootfs artifacts
the seccomp and shebang gates watch (`AUD-010`), `evidence/`'s content rules. What a comment may
cite is `ADR-0001`/`ADR-0066` policy, verdicted here as a *gate* — does it catch, is it tested —
never re-litigated as a rule.

### The claims — verdicts

`T4`'s first half, read against the table above. The verdict here judges the *keeping*, not the
rule's truth — an enforced rule and a convention can both be `present` if nothing in the
references would keep them truer. The two-tier shape named below is the whole read for every
staged-scope gate: the hook's `files:` pattern is a fast path with a friendly message, and the
whole-tree run inside the gate's own `_test.py` under `always_run` `maestro-gate-tests` is what
refuses — so a violation in a file the trigger misses still fails the commit, under the suite's
name instead of the gate's.

| Claim | Verdict | Why / id |
|---|---|---|
| `domain` imports no `android.*`, `domain` imports no `data`/`ui`/`di`, doc/language packages android-free (all six) | present | the layer gate is the references' own shape — `build/checker/layersChecker.ts`'s per-layer `tsconfig` split in the vscode clone, the `.iml`/Bazel module graph in the intellij clone — at one module's package granularity |
| `ui` names no real adapter, `data` names no `ui`/`di`, real adapters named only in `data/`/`di/`/`JupydroidApplication.kt` | present | the same arms, in the corrected shape `GAP-006`'s repair wrote — `data.fake` is the DI default §5.6 prescribes, and the scan reads qualified references so a spelled-out name trips it |
| `androidTest` compiles in the local gate | present | `GAP-008`'s repair — every reference's pipeline compiles its tests; the venue is a commit hook rather than a runner |
| files ≤ 500 / functions 4–20 / line ≤ 120 / naming | present | the lint-composite rules — `LargeClass`/`LongMethod`/`CyclomaticComplexMethod`/ktlint are the prettier+eslint analogue; the `.kt`-only reach is a finding below |
| formatters and lint wired into the build | present | the same acts as jupyterlab's `lint` composite — `shellcheck`, which that composite also carries, is `absent` below |
| hooks gate every commit | adapted | the venue is local pre-commit, `pre-commit install` opt-in — there is no pipeline behind it, and that absence is `GAP-161`, not a footnote on this row |
| every `check_*`/`read_*`/scanner carries `_test.py` | present | `build/lib/test/` in the vscode clone is the same shape — gates testing their gates — and the one file it does not cover (`check-file-line-length.sh`) is a finding below |
| comments cite what resolves, evidence names the app, flows observe kernel output | adapted | ours-only machinery — no reference commits device evidence or drives a real kernel — each gate's own docstring is the record |
| a named gap / one monospace home / palette entries draw / flows start seeded / seccomp / shebang-exec / chrome parts / node fill | adapted | ours-only machinery for the same reason — bespoke gates answering incidents the references never had |
| a `Fake…` per port, 1:1 | **absent** | `GAP-151` — §5.3's rule is answered by a produced `diff` and nothing refuses; `GAP-006`'s own class, one table earlier |
| android-free per-package counts | adapted | the *rule* is gated for all six packages; the produced half is the denominators, and produced-not-stored counts are `ADR-0067`'s design |
| prose-level style — "max 2 levels", "no `any`", "early returns", "exception messages name the value" | adapted | held by review on both sides: no reference gates them either — jupyterlab's `'@typescript-eslint/no-explicit-any': 'error'` is a TS-shaped rule with no Kotlin spelling — and detekt `NestedBlockDepth` sits unset at its default 4 against the stated 2, a finding below |
| the gates' inputs — retired doc names | present | `check_retired_doc_citations` plus the `git ls-files` sweep in its suite |
| the gates' inputs — driver↔flow↔evidence name lockstep | **absent** | `GAP-152` — `ADR-0001`'s index is carried by naming alone, and the one gate that reads drivers never checks the three stems agree |

### The gates — soundness

`T4`'s second half, per gate: does it fail closed, is it tested, and is it wired anywhere a
commit or a run actually executes. Read down the table, the design is one shape repeated — a
staged-file fast path where the trigger's message names the rule, backed by an always-run suite
that sweeps the real tree anyway, so *nothing* in the harness fails open. The staged half is
where the friendly message lives, not where the coverage lives. **No defect.**

| Gate | Fails closed by | Tested by | Wired by | Read |
|---|---|---|---|---|
| `check-file-line-length.sh` | a non-blank count over the limit exits 1 | **nothing** — the only gate with no `_test.py` and no planted-violation commit on record | `file-line-length` hook on staged `.kt` | detekt `LargeClass` (500) is the whole-tree Kotlin arm it pairs with; the gate counts *non-blank* lines, so a 608-line file passes at 486 — and the stated rule says "files" where the mechanism sees only `.kt` |
| `check_maestro_output_assertions.py` | `_test.py` fixtures | its suite — deliberately no real-tree run: a test over live flows "would change meaning every time a flow is edited" (its own docstring) | `maestro-output-assertions` hook on `.maestro/*.{yaml,ipynb}` → scans all flows | the trigger covers the whole scanned surface |
| `check_ui_dimensions.py` | `_test.py` incl. `ScopeTest` over the real tree | its suite | `ui-dimensions` hook on `app/ui/**.kt` staged | the trigger covers a third of the `app/` tree the gate scans — `ScopeTest` under `always_run` is the arm that actually refuses |
| `check_busybox_seccomp.py` | `_test.py` incl. `main(REPO)` over the real `jniLibs/` | its suite | `busybox-seccomp` hook on `tools/rootfs/{busybox.config,Dockerfile,stage-app-artifacts.sh}` | the trigger names the recipe while the operand is the committed `libbusybox.so` — the suite's `main(REPO)` is the arm that refuses a binary |
| `check_shebang_exec.py` | `_test.py` incl. `main(REPO)` | its suite | `shebang-exec` hook on `tools/rootfs/{shebang_exec.c,Dockerfile,stage-app-artifacts.sh}` | same shape |
| `check_driver_isolation.py` | `_test.py` incl. `test_the_real_drivers_are_clean` | its suite | `driver-isolation` hook on `scripts/*-driver.sh` | four arms — reset-before-seed, `maestro` targeted, `$ADB` not bare `adb`, width-marked evidence — and the trigger matches the scanned set |
| `check_monospace_home.py` | `_test.py` incl. real-tree run | its suite | `monospace-home` hook `always_run` | scans the whole `app/` tree per commit |
| `check_device_secrets.py` | `_test.py` incl. the `git ls-files` sweep | its suite | `device-secrets` hook on every staged file | staged-file plus whole-tree: the two-tier shape in its clearest form |
| `check_palette_consumers.py` | `_test.py` incl. real-tree run | its suite | `palette-consumers` hook `always_run` | — |
| `check_doc_citations.py` | `_test.py`; the hook run itself is the sweep | its suite | `doc-citations` hook `always_run` | reads every file under `app/src`+`tools` and *fails* on a type with no scanner — the one gate whose absence of a scanner is itself an offence; `scripts/` is deliberately outside it (the quoting question, `backlog.md` "not scheduled") |
| `check_retired_doc_citations.py` | `_test.py` incl. the `git ls-files` sweep | its suite | `retired-doc-citations` hook on staged `types: [text]` | the suite is the whole-tree arm |
| `check_chrome_parts.py` | `_test.py` over this repo's committed frames | its suite | `p7a-chrome-parts-driver.sh` | driver-bound: a device frame is its input, so no commit hook could hold it — the wire is the flow itself |
| `check_node_fill.py` | `_test.py` over a committed frame | its suite | the `p7a-1b-*` drivers | same |
| `ArchitectureGuardTest` | a planted violation, watched fail, reverted — `f18e2dc1`'s commit body | it *is* the suite | `unit-tests` hook (`testDebugUnitTest`) | asserts the real tree rather than fixtures, so its red-proof lives in git log — the `layersChecker.test.ts` idea at repo scale |
| ktlint, detekt, `lintDebug` | the build | — | `ktlint-check`, `detekt`, `android-lint` hooks `always_run`, tasks named per `ADR-0016` | detekt `NestedBlockDepth` unset — default allows 4 against the stated 2 |
| `maestro-gate-tests` | — | it is the runner | `always_run` | the meta-arm: what makes every `_test.py` sweep a gate rather than a document |
| `unit-tests` | — | runs `testDebugUnitTest` + `compileDebugAndroidTestKotlin` | `always_run` | the second Gradle call a commit pays |

What the read found, beyond the rows:

- **`check-file-line-length.sh` is the one gate that cannot demonstrate it fails.** Every other
  gate proves red — a `_test.py` fixture for the Python gates, a planted-violation commit body
  for `ArchitectureGuardTest` — and this one has neither. The convention's own wording covers
  `check_*.py`, so it slips the stated rule by name-shape while being every bit a gate. The
  repair is a `check_file_line_length_test.py` invoking the script over fixtures, not a new
  mechanism — recorded rather than built because nothing in the audit's mandate makes a
  convention worded around it into a defect.
- **Its count is not `wc -l`.** `awk 'NF'` skips blanks, so a file can read over the limit by eye
  and pass — `NotebookBackStackTest.kt` is 608 lines and 486 non-blank. The limit the gate
  enforces is a different number than the one a reader counts; detekt's `LargeClass` is the
  arm that reads every `.kt` every run.
- **"Files ≤ 500" is wider than its mechanism.** Kotlin is covered twice — the staged shell
  gate and detekt across every source set — and `*.kts`, the 213 `*.sh` and the `*.py` under
  `scripts/` have no arm at all. Same class as the `ui ↛ data` mis-statement `GAP-006` corrected:
  the rule's prose outruns the thing that keeps it.
- **`check_doc_citations` reads `app/src`+`tools` and nothing else.** Its own hook comment is
  the reason — the whole-tree run exists because a four-extension glob once left `Dockerfile`,
  the grammars and the manifest unread while reporting them clean — and `scripts/` stays outside
  until the gate learns to quote its own test data (`backlog.md`, "not scheduled", with the
  counting already done: 131 citations across 41 files).
- **`pre-commit install` is the only enrolment.** Nothing refuses a commit from a clone that
  never installed the hooks, or a `git commit --no-verify` — the whole tier above is the gate
  *where it runs*, and where it does not run is `GAP-161`'s entry.

### The references' gates — verdicts

Read down both sides' own files at the pins `docs/README.md` names. Ours-side rows carry the
same `Verdict` column with the direction reversed — an ours-only gate is `adapted`, the same
verdict `AUD-010` gave machinery no reference has.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| Layer split — `build/checker/layersChecker.ts` + `tsconfig.<layer>.json` per-layer type-checks and a `RULES` table of disallowed types (vscode @ `3f3380c`) | `ArchitectureGuardTest`'s six arms over the `app/` package tree | present | the same mechanism at one module's scale — a rules table over real sources; the reference's own `layersChecker.test.ts` plants violations and ours plants them in commit bodies |
| Module graph — a JPS `.iml`/Bazel module references only what it declares (intellij @ `406a27a`) | the same arms over packages inside the one `:app` module | adapted | the build model *is* the gate there; ours is a guard test because there is one module and packages are the granularity it has |
| Lint composite — `prettier` + `eslint` + a second *typed* eslint + `stylelint` + `shellcheck` (jupyterlab @ `7f0e690`) | ktlint + detekt + `lintDebug` | adapted | three of the four acts have our analogue; `shellcheck` does not — `GAP-153` |
| Copyright header — `build/hygiene.ts` refuses a file without it; `license-header.yml` writes headers back onto PR branches | — | **absent** | `GAP-154` |
| Cyclic dependencies — `build/lib/checkCyclicDependencies.ts` walks the module graph | — | **absent** | `GAP-155` — one module makes the reference's shape impossible; intra-package cycles are ungated |
| i18n — `check-i18n.yml` keeps the translatable surface extractable | one `<string>` in `values/strings.xml`; the UI text is literals | **absent** | `GAP-156` — the machinery and the check are absent together, and the single-locale posture is unrecorded |
| External links — `check-external-links.yml` runs maintainer-tools `check-links` | 16 URLs in `docs/`+`AGENTS.md`, never resolved by a gate | **absent** | `GAP-157` |
| Spell-check — `spell-check.yml` over docs prose | — | **absent** | `GAP-158` |
| Workflow security lint — `zizmor.yml` | no workflows exist to lint | adapted | the substrate's absence is `GAP-161`, not a second row |
| Flakiness tracking — `galata-flakiness-report.yml` counts the UI suite's flakes | `clear_system_anr.py` + `system_anr_dialog.py` — a per-run rescue | **absent** | `GAP-159` — clearing a stuck dialog is not counting a flake |
| Wrapper validation — `gradle-wrapper-validation.yml` checksums the committed jar (termux @ `3df69d1`) | `gradlew` + `scripts/gradlew-jbr` run the jar unverified | **absent** | `GAP-160` |
| Deep static analysis — Qodana (`code_quality.yml`), `codeql-analysis.yml` | detekt + `lintDebug` per commit | adapted | same category at lint's depth and the local venue; the scheduled-analysis venue is `GAP-161` |
| Build + test pipeline — `run_tests.yml`, `debug_build.yml` (termux), `test.yml` (jediterm), `build-test.yml` + `checkDependencies` (vscode-jupyter) | `unit-tests` hook + `assembleDebug` evidence gate | adapted | the same acts at the commit/step tier; `checkDependencies`' consistency half is Gradle resolution, and its native-artifact half is the seccomp/shebang gates' |
| A pipeline at all — the venue every reference but winlator runs its gates in | — | **absent** | `GAP-161` — the tiering is AGENTS.md's design, and the mechanism that would refuse an uninstalled hook is still absent |
| Doc-citation, device-secret, kernel-output, driver-isolation, dimension/monospace/palette, seccomp/shebang and file-length gates | — | adapted | ours-only machinery — no reference ships device evidence, a real kernel or a rootfs, so the gates that watch ours have no analogue to compare |

`T4`'s verdicts landed with **no `undocumented` and no `defect`** — the row counts are produced by
`read_ledger_verdicts.py --counts`, not stored. The absents opened `GAP-151`–`GAP-161` in
`architecture.md` §11 — two on our side (a produced-not-gated §5.3 rule, the
driver↔flow↔evidence lockstep) and nine on the references' (shellcheck, licence headers, cyclic
dependencies, i18n, external links, spell-check, flakiness tracking, wrapper validation, and the
pipeline itself). The Areas row stays `unexamined` until `T5`'s walk reads a commit end to end.

### The walk

One commit traced end to end — hook, gate, suite, then the step's documented command — the three
tiers the harness runs at and what each can and cannot hold. Reproduces from a clone:

```sh
grep -n "id:\|files:\|types:\|always_run\|pass_filenames\|entry:" .pre-commit-config.yaml
pre-commit run --all-files                                   # the whole walk, live
ls .git/hooks/pre-commit                                     # the enrolment edge — opt-in
python3 -m unittest discover -s scripts -p '*_test.py'       # the 34 suites every commit pays
ls scripts/*_test.py | wc -l                                 # 34
grep -n "gradlew\|unittest" AGENTS.md                        # the documented step gate
```

**A commit** starts at `.git/hooks/pre-commit` — the framework's generated trampoline, present on
this clone because someone ran `pre-commit install` and free to be absent on the next: the
enrolment edge `GAP-161` records, where the tier refuses nothing it never runs and
`git commit --no-verify` walks around it whole. Inside it, the yaml's 16 ids run in three trigger
shapes. **Three pass the staged names to the gate as its input** — `file-line-length` on staged
`.kt` (its `"$@"` is the operand, `awk 'NF'` counting non-blank — staged-only by design, detekt
`LargeClass` its every-file pair, and the one gate with no fails-closed proof of its own), plus
`device-secrets` on everything staged and `retired-doc-citations` on staged text, each backed by a
whole-tree sweep inside its suite. **Five take the staged name as a trigger, not an input** —
`pass_filenames: false`, the gate re-scans its whole domain — and for four of them the suite's
real-tree run (`ScopeTest`, `main(REPO)`, `test_the_real_drivers_are_clean`) is what refuses a
violation in a file the trigger's `files:` pattern missed; `check_maestro_output_assertions`
needs no sweep because its trigger *is* its domain — every `.maestro` flow matches
`\.(yaml|ipynb)$` — so its suite keeps fixtures instead. **Eight are `always_run`** — a commit
touching one `.kt` and a commit touching only `docs/` pay identically: the 34-suite `unittest
discover`, the three whole-tree sweeps (`monospace-home`, `palette-consumers`, `doc-citations`),
and the four Gradle calls in the yaml's order — ktlint's named source sets, detekt, `lintDebug`,
`testDebugUnitTest` + `compileDebugAndroidTestKotlin`. The order is cheap first: every Python hook
can refuse before a Gradle daemon is asked, and `gradlew-jbr` carries the `JAVA_HOME` fact once
for all four (the documented `<jdk>` is the same fact a human pays by hand).

**The hooks' tests** are the second tier inside the same commit. `maestro-gate-tests` —
`always_run` — runs `unittest discover -s scripts -p '*_test.py'`: 34 suites, milliseconds, and
the reason the five narrow triggers can stay narrow. `ArchitectureGuardTest` is the same act on
the Gradle side, inside `testDebugUnitTest`, asserting the real tree — its red proven not by a
fixture suite but by a planted violation watched failing and reverted, recorded in `f18e2dc1`'s
commit body: the two red-proof styles the audit found, kept as the fact they are. `unittest
discover`'s `*_test.py` pattern is the wiring — a new gate's suite joins the run by filename
alone, no registration — which is also the edge the one `.sh` gate sits outside: a
`check_file_line_length_test.py` wrapper is how a non-Python gate would re-enter the tier.

**The step** is what the documented command adds to a commit: the same Gradle surface plus
`assembleDebug` — evidence gate 3, the APK itself — and the same `unittest discover`, each ktlint
source set and `compileDebugAndroidTestKotlin` named for the `ADR-0016`/`GAP-008` reasons. Beyond
any command is the rest of the step — `connectedDebugAndroidTest` wants a device or emulator, the
Maestro flow wants the device rule 2 names (arm64 when a cell runs, the emulator otherwise), and
the evidence wants committing. The device tier exists because a commit cannot carry a device
frame — the same constraint that put `check_chrome_parts` and `check_node_fill` inside their
driver scripts rather than in the yaml, wired by the flows they read.

What the walk found, beyond the tiers:

- **Growth is one file, one suite, one stanza.** A new gate is `check_x.py`, `check_x_test.py`
  (joining `unittest discover` by its filename — nothing registers it) and a yaml stanza choosing
  its trigger shape. `check-file-line-length.sh` shows the edge: it predates the convention, its
  `check_*` name slips the stated rule by shape, and a `_test.py` wrapper is the whole repair the
  finding prescribes. The `34` is produced, not gated — a deleted suite shrinks coverage silently,
  the deliberate posture `ADR-0067` set for counts.
- **Branching is three trigger shapes and one refusal arm.** Filename-passing where the staged
  set *is* the domain; trigger-not-input where the domain is a subtree and the suite sweeps what
  the trigger misses; `always_run` where nothing may depend on what was touched. pre-commit's own
  fail-fast ordering puts the free checks before the four Gradle-paying hooks.
- **Failure is closed at the wiring, open at the enrolment.** `language: system` makes a missing
  entry fail the hook rather than skip it, and every gate's error is a non-zero exit, never a
  pass. The open arm is the one `GAP-161` already names — opt-in install, `--no-verify`, no
  pipeline behind the local tier — and inside the tier the one gate that cannot demonstrate it
  fails stays the file-length `.sh`. Both recorded; neither a defect.
- **Coupling is three shared substrates and one honest split.** `gradlew-jbr` holds `JAVA_HOME`
  for the four Gradle hooks; the three scanners are the citation gates' shared readers; `unittest
  discover` couples every `*_test.py` to every commit. The driver-bound pair stay coupled to their
  flows — a device frame is their input, the one coupling the design refuses to pretend away.
