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
| Search, replace, table of contents | `AUD-004` | measured — the surface below, verdicts next |
| Files, workspace, recents | `AUD-005` | unexamined |
| Console, sessions, running panel | `AUD-006` | unexamined |
| Terminal emulator and view | `AUD-007` | unexamined |
| Rendering and formats | `AUD-008` | unexamined |
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
| `notebook:interrupt-kernel`, `notebook:restart-kernel`, `notebook:restart-clear-output`, `notebook:restart-run-all`, `notebook:shutdown-kernel`, `notebook:change-kernel`, `notebook:reconnect-to-kernel`, `notebook:get-kernel`, `notebook:close-and-shutdown` | `AUD-006` | Kernel lifecycle. `shutdown-kernel` and `get-kernel` are palette-less (`:3171`, `:4567`); the rest are *Notebook Operations* |
| `notebook:create-console`, `notebook:create-subshell-console`, `notebook:run-in-console` | `AUD-006` | The console, which is that slice's own area |
| `notebook:access-previous-history-entry`, `notebook:access-next-history-entry` | `AUD-006` | *Notebook Operations*. The history walk — the `history_request` behind it is `present` in `AUD-001` above, so what is left is the walk ([ADR-0052](../adr/0052-kernel-history-is-the-kernels-and-a-walk-belongs-to-its-cell.md)) |
| `notebook:export-to-format`, `notebook:show-export-guidance` | `AUD-008` | Formats. *"Enable notebook exports"* (`:775`) is the guidance dialog for a deployment that can convert nothing |
| `notebook:trust` | `AUD-008` | *Notebook Operations*. Trust decides what a notebook's outputs may render |
| `notebook:copy-to-clipboard`, `notebook:create-output-view` | `AUD-008` | Palette-less, and both are *output* operations by their own labels: *"Copy Output to Clipboard"* (`:2078`) reads `cell.outputArea`, *"Create New View for Cell Output"* (`:1751`) |
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
| the `output` filter | `searchOutputs` → `outputsSearched` → `outputHitsOf` | adapted | only what `RenderedOutput.Text` draws is searched, where the reference searches its rendered DOM — `outputHitsOf`'s docstring and ADR-0043; `supportReplace: false` is `outputsSearched`'s rule |
| `searchDebounceTime`, `autoSearchInSelection` (`documentsearch-extension/schema/plugin.json:72-86`) | no settings carried | adapted | a re-find is one synchronous pass over state, so per-keystroke is already honest; the selection filter arms only by hand — the reasons live on `withSearchQuery` and `withSearchInSelection` |
