# JD-079 — A notebook ends in "Code" and "Markdown" buttons that add a cell

Milestone: `M11` · State: in-progress · Depends on: — · Closes: `GAP-197`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

After the last cell a reader sees two buttons, "Code" and "Markdown", and one tap appends an empty cell of
that kind, makes it the active cell and brings it into view, without opening a cell's `⋮` menu.

## Demo

Adding a cell runs no cell, so the emulator is allowed (AGENTS.md rule 2); the tablet half needs the arm64
tablet `SM-X216B` only because the item names a tablet, not because a kernel is involved.

1. Cold start, open a notebook of three cells; scroll to the end. Both buttons are on screen with no menu open.
2. Tap "Code": a fourth, empty code cell exists, is the active one and is fully on screen.
3. Tap "Markdown": a fifth cell, markdown, the same way.
4. Undo once: the fifth cell is gone, the fourth stays.
5. Repeat on a phone and a tablet at font scale 1.0 and 2.0: every label whole, no overlap.
6. Kill and relaunch: both cells were saved.

Not `shipped` until this has run on a phone-sized and a tablet-sized device, frames looked at, dumps kept
under the local `evidence/` ([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)).

## Who else implements this

Read in `docs/architecture.md` §GAP-197, which holds the paths and the pins; not re-read here.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode@3f3380c668d0` | FULL | `src/vs/workbench/contrib/notebook/browser/controller/insertCellActions.ts:236-350`, `notebook.contribution.ts:1049-1062` | "Code" and "Markdown" in a between-cells toolbar (hover) and a top toolbar; `both` by default |
| `jupyterlab@7f0e690680a7` | FULL | `packages/notebook/src/notebookfooter.ts:25,47-53`, `widget.ts:1808`, `notebook-extension/schema/panel.json` | a footer after the last cell, "Click to add a cell.", plus a toolbar `insert` button |
| `vscode-jupyter@f01d80ca0aa0` | PARTIAL | `package.json:155-170` | `jupyter.insertCellBelow` as key chords for `# %%` cells; no button |
| `colab-vscode@96e23580c6a6` | PARTIAL | `src/colab/commands/notebook.ts:65-88` | one prefilled code cell after the selection, from a command |
| `intellij-community` | ABSENT | — | no notebook UI in the community tree |
| `termux-app`, `winlator` | ABSENT | — | not notebook editors |
| the shipped rootfs | ABSENT | — | no UI |

**Count:** 4 of 7 implement it, the two notebook editors with a control always on screen. A touch screen
has no hover, so VS Code's between-cells half does not carry over (the reason ADR-0036 declined
`resize: vertical`).

## Implementation inventory

| Concern | Files |
|---|---|
| the insert itself | `ui/editor/NotebookCellEdits.kt` `insertCellAt` (active cell, one undo, schedules a save) |
| the list the footer joins | `ui/editor/EditorContent.kt` (`LazyColumn`, `itemsIndexed`), wired from `EditorScreen.kt` |
| the existing rows it must not replace | `ui/editor/CellActionItems.kt` `addingItems` |
| scrolling to a cell | `ui/editor/CellRevealScroll.kt`, `CellScrollTarget.kt` (`isFullyVisible`) |
| existing tests to extend | `app/src/test/.../ui/editor/CellInsertedAtTest.kt`, `app/src/androidTest/.../ui/editor/CellHeaderTest.kt` (shape of a visible-to-press test) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Choice |
|---|---|---|---|
| where | footer after the last cell (JupyterLab) · top toolbar (VS Code) · between cells (VS Code) | between cells needs hover, or costs vertical space on a phone; the footer is where a reader scrolls to append | the footer; between cells stays open until the footer is on a phone and the cost is measured |
| kinds | one "add" button · "Code" and "Markdown" | one button needs a second tap to choose | two buttons, VS Code's pair |
| the menu rows | remove · keep | the references keep the commands too | keep |

## Gap analysis

Absent. The only insert controls are the cell menu's five rows; `NotebookBar.kt` has none.
The backlog row's two further conditions do not hold in this code, and are dropped from the acceptance:

- **"Alone in an empty notebook":** no notebook is empty. `CellEdits.kt` `withCellsDeleted` replaces a deleted
  last cell with an empty code cell, and `EditorUiState.kt` gives a file with no cells one.
- **"Absent read-only":** there is no read-only notebook. `domain/model/EditorSettings.kt` records that
  `readOnly` "locks an editor no surface locks". A read-only mode would be an item of its own.

## Deviations

No hover toolbar (no hover on touch). No "Click to add a cell." sentence: two labelled buttons say the
same and are what VS Code names them.

## Open decisions

- **Is a footer-added cell in view?** Answered: no, and measured. On a 320x640dp window the list spans
  y 136-536; after "Code" the new cell's editor was cut at 536 and the footer was off screen.
  `CellRevealScroll` scrolls only in `EDIT` mode and `insertCellAt` leaves `COMMAND`, so it never ran.
  The fix is `AppendedCellReveal.kt` `appendCellInView`, which scrolls to the new cell by its list key
  when it is not whole (cell 422-470, footer 494-514 afterwards). Waiting for an item *count* does not
  work: the footer is an item, so the count is already past the new row's index, and
  `AppendedCellRevealTest` pins that. A cell inside a collapsed trailing section has no row and scrolls
  nothing.
- **A control between cells or at the top**, after the footer is measured on a phone (GAP-197 slice 2).

## Surface

| Layer | Files |
|---|---|
| `ui/editor` | `NotebookCellEdits.kt` (the append), a new `AddCellFooter.kt`, `EditorContent.kt`, `EditorScreen.kt`, `EditorViewModel.kt` (the one call through) |
| unit tests | `CellInsertedAtTest.kt` or a sibling for the append |
| instrumented tests | an `AddCellFooterTest` beside `CellHeaderTest`, at both font scales |
| `.maestro/` flow + `scripts/` driver | one flow and its driver, written in T4 |

No port and no adapter, so no `Fake…`.

## Tasks

- [x] T1 — appending a cell of a kind at the end: last, empty, active, one undo
- [x] T2 — `AddCellFooter` ("Code", "Markdown") as the list's last item, each tap reporting its kind
- [x] T3 — the new cell is brought fully into view
- [ ] T4 — phone and tablet at 1.0 and 2.0, labels whole; the flow and driver; the ADR; delete this file

## Out of scope

A control between cells or at the top (GAP-197 slice 2). A read-only notebook. Removing the cell menu's rows.
