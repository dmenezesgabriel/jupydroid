# AUD-002 — Cell commands and selection

Milestone: `M0` · State: in-progress · Depends on: `AUD-001` · Closes: `GAP-005`

> An **audit** item, so this is not `_TEMPLATE.md` filled in. What an `AUD-NNN` ships is a
> *measurement* rather than a screen
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), so the
> template's Outcome, Demo, Maestro flow and domain→port→adapter Surface have nothing to say here
> and are replaced by what is examined and the command that reproduces each number. The template's
> two load-bearing sections stay, because they are the whole point: a row per reference, and a gap
> analysis against our own code.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, and the code comments — nothing else
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **cell commands and selection** — taken through four questions at once, never four
sweeps: what the reference offers, what we built, where we differ and *why*, and whether the code on
that path is sound in growth, branching, failure handling and coupling.

The area's bounds are the reference's own, not ours. `notebook-extension` sorts its commands into
two palette categories, *Notebook Operations* and *Notebook Cell Operations*
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12 packages/notebook-extension/src/index.ts:5255-5360`),
and the second of those plus the selection half of the first is this slice. Everything else is
carried to the slice that owns it, named in the ledger — an id assigned to no slice is how an area
silently stays unexamined.

**And that bound does not reach the whole surface, which `T2a` measured.** The two categories hold
27 and 58 ids, plus `notebook:create-new` added on its own with `args: { isPalette: true }` — 86 of
the 99. **Thirteen are in neither**, and four of those are operations this app ships:
`notebook:duplicate-below`, `notebook:undo`, `notebook:redo` and `notebook:toggle-output-scrolling`.
A reading bounded by the palette alone would have dropped them in silence, which is this slice's own
warning turned on itself. So ownership is decided by three sources in order — the palette category,
then the command's own registration read for what its `label` and `execute` say it does, then
`cross-cutting.md` §8.3 — and `scripts/read_ledger_partition.py` plus `PartitionedSurfaceTest` make
an unowned id fail rather than go unnoticed.

## The surface, produced

Never stored. ADR-0067's rule, and the commands are these:

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package notebook-extension   # 102 ids, 99 notebook:*
python3 scripts/read_reference_commands.py --notebook-actions                        # 69 NotebookActions members
```

**Those were 99 and 96 when this PRD was written, and T1 is why they are not.** `COMMAND_ID`
required the id on the `export const` line, so it could not read a declaration the reference's own
formatter had wrapped — and two of the three it missed in this very package are
`notebook:move-cursor-heading-above-or-collapse` and `-below-or-expand`, the only command-mode way
to walk headings. The measurement that should have found this area's gap was blind to it. Fixed
red-first in T1; see the ledger's first row.

VS Code's half has no producer, because its core commands are registered in code rather than
contributed by a manifest — `read_reference_commands.py`'s `vscode_jupyter_command_ids` reads a
`package.json` and there is none to read. The pattern travels with the number instead:

```sh
grep -rhoE "'notebook\.cell\.[a-zA-Z.]+'" reference/research_repos/vscode/src/vs/workbench/contrib/notebook/ \
  | tr -d "'" | sort -u | wc -l        # 73
```

That is a literal-string grep and therefore an **upper bound on ids and a lower bound on commands**:
a command assembled from a constant is missed, and a string that is not a registration is counted.
It is enough to answer "does VS Code have this one", which is all this slice asks of it.

## Our side is read by hand, and why

There is no third script. `read_port_surface.py` reads `domain/port/`, and none of this area is a
port: `CellAction`, `CellEdits`, `CellRestructure`, `CellSelection` and `CellRangeSelection` are all
`ui/editor/`. A first pass exists and **must not be trusted as an answer** —

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package notebook-extension \
  | grep '^notebook:' \
  | while read -r id; do grep -rqF "$id" app/src/ || echo "unnamed $id"; done
```

— which reports 40 named and 59 unnamed, and both numbers are wrong in both directions. This is
`read_port_surface.py`'s own lesson in a new place, and all three failures are confirmed, not
feared:

- **Unnamed does not mean absent.** Our comments cite a family rather than a member, in four
  notations: the glob `notebook:extend-marked-cells-*` (`CellAction.kt`), the range
  `notebook:change-cell-to-heading-1..6` (`CellAction.kt`), the brace
  `notebook:change-cell-to-{code,markdown,raw}` and the stem `merge-cell` (`CellActionItems.kt`).
- **Unnamed does not mean unimplemented.** `notebook:move-cell-up`, `delete-cell`, `copy-cell` and
  `cut-cell` appear nowhere in `app/src/`, while `CellEdits.withCellsMoved`,
  `CellEdits.withCellsDeleted`, `CellRestructure.withCellsCopied` and `withCellsCut` all exist and
  cite `actions.tsx` line ranges instead. A missing id is a citation fact, not a parity one.
- **Named does not mean ours.** A hit may be a KDoc in a neighbour, a test name or a settings
  screen — `notebook:toggle-all-cell-line-numbers` has eleven hits across three source sets.

So every verdict is read against the code, and the ledger says so, exactly as `AUD-001`'s
`LanguageServerPort.capabilities` row does. A verdict with a stated method is worth more than a
number with none.

## Who else implements this

Both references that §8.3 makes authoritative here have the area in full, which is what makes an
absence on our side a gap rather than a platform difference.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL — the behaviour reference | `packages/notebook-extension/src/index.ts`, `packages/notebook/src/actions.tsx`, `packages/notebook/src/widget.ts` | A command id per operation over `NotebookActions`, every one scoped by `isSelectedOrActive` |
| `vscode` | FULL — the presentation reference | `src/vs/workbench/contrib/notebook/browser/controller/` (`cellOperations.ts`, `foldingController.ts`, `editActions.ts`) | The same operations as menu/toolbar actions on a cell, keyboard-first |
| `vscode-jupyter` | ABSENT | — | §8.3: notebook chrome is core VS Code, not this repo — checked and empty |
| `intellij-community` | PARTIAL | `notebooks/visualization/` | Notebook *layout*; §8.3 does not make it authoritative for the command surface |
| `termux-app` | ABSENT | — | A terminal emulator has no cells |
| `winlator` | ABSENT | — | Container lifecycle has no cells |
| the shipped rootfs | ABSENT | — | A cell command never reaches the kernel; the wire is `AUD-001`'s area |

**Count:** 2 of 7, and they are the two that matter — JupyterLab for behaviour, VS Code for
presentation, which is §8.3's deliberate split. An operation both have and we do not is `absent`
with no platform excuse available.

## Gap analysis — ours today

Read before any verdict was written. Stated plainly: **present and partial.** The per-cell half is
complete and unusually well cited; the notebook-wide half is where the holes are.

| Concern | Ours today (file) | State |
|---|---|---|
| The action vocabulary | `CellAction.kt` | complete — one `sealed interface`, every variant carrying its reference line |
| The menu that offers it | `CellActionItems.kt`, `CellActions.kt` | complete — three surfaces from one list |
| Insert / change type / heading level | `CellRestructure.kt` | complete |
| Merge / split | `CellRestructure.kt`, `CellSplit.kt` | complete, split narrowed to one selection and the narrowing stated |
| Move / duplicate / delete / clear outputs | `CellEdits.kt` | complete |
| Folds, per cell | `CellFolds.kt` | complete |
| Folds, notebook-wide | — | **absent** for code and outputs; headings have `CellHeadings.kt` and a menu row |
| Clipboard, cells | `CellRestructure.kt`, `EditorUiState.clipboard` | partial — one paste placement of three |
| Clipboard, system | — | **absent** — `EditorUiState.clipboard` is `List<Cell>`, in-process |
| Undo / redo of an operation | `CellUndo.kt` | complete, and the two-histories seam is stated |
| One-cell selection | `CellSelection.kt` | complete |
| A run of cells | `CellRangeSelection.kt`, `NotebookCellSelection.kt`, `SelectionTopBar.kt` | complete |
| Moving the head in command mode | `EditorViewModel.cellFocusChanged` | **absent** — `GAP-005`, restated by `T3` as the missing select-without-editing primitive |
| Dispatch | `EditorCellActions.kt`, `NotebookCellEdits.kt` | `T4` walks it |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **Two commands become one toggle row** wherever the reference has a `hide`/`show` pair. A menu row
  reads the head cell's state and names the opposite; a palette has no state to read.
- **Six palette entries become one row and a picker** for the heading levels
  (`CellActions.HeadingLevelSurface`), with the flat six kept for the accessibility context menu.
- **A long press is not available**, so entering multi-select is a visible control
  (`CellAction.StartSelecting`) where the reference needs no entry at all — `Shift`+click extends
  and there is nothing to enter.

## Tasks

- [x] T1 — **The `defect` the slice found in its own instrument**, repaired red-first: `COMMAND_ID`
      now reads a wrapped declaration, which moves the whole surface from 403 to 412 ids and this
      package from 99 to 102. §1's prose, `PinnedSurfaceTest` and ADR-0067's worked example all
      corrected to the measured numbers. Nothing can be mapped against an enumeration that is short.
- [x] T2a — **Partition the 99 `notebook:*` ids** by the slice that owns them, one ledger row per
      command family, with the rule that decided each. Split out of `T2` because the partition is
      what makes `T3`, `AUD-006`, `AUD-008` and `AUD-009` pickable at all, and because it is the
      half nothing was checking: `read_ledger_partition.py` and `PartitionedSurfaceTest` now refuse
      an id owned by nobody or claimed by two. A partition carries no verdict.
- [x] T2b — Map this slice's **editing** half: insert, change type, the six heading levels, merge,
      split, move, duplicate, delete, the clipboard, the folds, clear outputs, undo/redo. One ledger
      row per entry with a verdict; a `GAP-NNN` per `absent` — `GAP-015`..`GAP-019`, ADR-0068 for
      the text half nobody had written down, and one `defect` fixed red-first (`FocusedCellInput.type`
      inserting in front of a selection). The text half was measured on the tablet, not only read.
      `read_ledger_verdicts.py` and `VerdictedSurfaceTest` now refuse an examined task that left an
      id unjudged — `python3 scripts/read_ledger_verdicts.py --missing`.
- [x] T3 — Map the **selection and navigation** half: `select-all`, `deselect-all`, the four
      `extend-marked-cells-*`, `move-cursor-up`/`-down`, the two
      `move-cursor-heading-{above-or-collapse,below-or-expand}` walks T1 made visible,
      `enter-command-mode`/`enter-edit-mode`, `select-last-{modified,run}-cell`,
      `select-next-modified-cell`, and the five `RunRange` ranges. `GAP-005` is decided here,
      because it *is* this. Decided: nothing selects a cell without editing it, which is
      what the two cursor moves, the two heading walks and the header Run's head are missing.
      Three more gaps for the rest: `GAP-020` run-and-advance, `GAP-021` last-run, `GAP-022`
      modified-cell back/forward.
- [ ] T4 — Walk our own call stack: `EditorCell` → `cellAction` → `NotebookCellEdits.applyTo` →
      `CellEdits`/`CellRestructure` → `DocumentAutosave`, for growth, branching, failure handling
      and coupling. `AUD-001`'s walk is the shape.

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair it
  finds becomes a `JD-NNN` with a demo of its own.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's one named exception. What
  stands in for the device is the command that reproduces each measurement, in the commit body.
- **The neighbours' ids.** Kernel lifecycle (`AUD-001`, `AUD-006`), the console
  (`AUD-006`), export, trust and rendering (`AUD-008`), side-by-side and the virtual scrollbar
  (`AUD-009`). T1 assigns them; it does not examine them.
