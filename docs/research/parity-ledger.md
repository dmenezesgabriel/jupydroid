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
python3 scripts/read_port_surface.py                          # our ports, and what names each operation
python3 scripts/read_port_surface.py --unused                 # operations nothing names
python3 scripts/read_ledger_partition.py --unassigned         # command ids this file owns nowhere
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
| Cell commands and selection | `AUD-002` | partitioned — every id owned, verdicts for the editing half next |
| Editor language features | `AUD-003` | unexamined |
| Search, replace, table of contents | `AUD-004` | unexamined |
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
| `inspect_request` | LSP `textDocument/hover` — `ui/editor/NotebookHover.kt` | **undocumented** | the file notes the substitution and gives no reason. The reference has four callers: `packages/inspector/src/kernelconnector.ts:47`, `packages/completer/src/default/kernelprovider.ts:113`, `packages/tooltip-extension/src/index.ts:355`, and the console. `AUD-003` owes the ADR |
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
| `LanguageServerPort.closeDocument` | deferred | named nowhere. `AUD-003` |
| `LanguageServerPort.capabilities` | deferred | **read by hand, not by the tool**: it reports the name as used because `StdioLanguageServer` holds an unrelated `val capabilities` field and checks it six times. The port operation has no caller. `AUD-003` |

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
