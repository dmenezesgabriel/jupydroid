# JD-104 — Jupyter's keyboard shortcuts run the notebook

Milestone: `M13` · State: open · Depends on: — · Closes: `GAP-005`, `GAP-020`

## Outcome

Any keyboard that can emit the keys drives the notebook the way Jupyter's modal editor reads
them: `Ctrl+Enter` runs the cell in place, `Shift+Enter` runs and moves the head on (making a
cell at the end), `Alt+Enter` runs and inserts below, `Enter` begins editing the selected cell
and `Escape` leaves editing for command mode, and command mode's letters — `A`, `B`, `D D`,
`M`, `Y`, `R`, `1`–`6`, `C`, `X`, `V`, `Z`, `J`, `K` and the rest of JupyterLab's shipped
table — do what a Jupyter user expects them to do. The same bindings answer a hardware, USB or
Bluetooth keyboard and a soft keyboard that can send the chords, because both arrive as the
same `KeyEvent` through `onPreviewKeyEvent`.

## Demo

Who: the agent, on **`SM-X216B`** — cells run, so rule 2 requires the arm64 tablet.

The two keyboard halves are driven honestly, and the difference is stated in every commit
body: the **soft-keyboard half** is a real IME that can send chords — Unexpected Keyboard
(installed for the demo: it carries `Ctrl`, `Shift`, `Esc`, `Tab` and arrows and sends them as
real `KeyEvent`s) — and the **external half** is `adb shell input keyevent` /
`input keycombination`, which injects through Android's virtual keyboard device into the same
`dispatchKeyEvent` → `onPreviewKeyEvent` pipeline a Bluetooth or USB keyboard uses. Command-mode
letters have no soft-keyboard shape at all: with no editable field focused the IME is hidden
and cannot send, which is why the acts also sit on the bars and menus. `Demo run by the agent,
not by a human` goes in each commit body.

1. Cold start: install, launch, open the seeded notebook (three code cells, one markdown).
2. Tap into the middle cell. With Unexpected Keyboard: `Ctrl`+`Enter` runs it in place — the
   count ticks, the caret stays. `Shift`+`Enter` on the next cell runs it and selects the cell
   below; `Shift`+`Enter` on the last cell runs it, makes a new code cell and lands the caret
   in it. `Esc` drops the keyboard — command mode.
3. `adb input keyevent`/`keycombination` for the command-mode set: `J`/`K` and the arrows step
   the head (screenshot each — the head cell stays on screen), `Shift`+`J` marks a run, `A`
   inserts above, `B` below, `D` `D` deletes, `Z` brings it back, `M` turns a code cell
   markdown, `Y` back, `2` makes the markdown cell an H2, `C`/`X`/`V` clipboard a cell,
   `Shift`+`M` merges, `Ctrl`+`Shift`+`↑` moves the head cell up, `Ctrl`+`A` marks all, `Shift`+`L`
   toggles line numbers, `Enter` puts the caret back in the selected cell.
4. `Ctrl`+`Shift`+`-` in edit mode splits the cell at the caret; `Ctrl`+`S` writes the document.
5. Kill and relaunch. The document reads back with every structural edit the keys made.

The flow and driver that reproduce this ship as `jupyter-keys-*` (`.maestro/` +
`scripts/jupyter-keys-driver.sh`); the screenshots, dumps and `maestro.log` stay local
([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)) and are
looked at, not just asserted.

## Who else implements this

Read from code at the SHAs in [`../README.md`](../README.md). JupyterLab is authoritative for
behaviour and the command surface ([`../architecture/cross-cutting.md`](../architecture/cross-cutting.md)
§8.3); the bindings themselves are its shipped settings, which is exactly what this item ports.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/notebook-extension/schema/tracker.json`, `packages/notebook/src/actions.tsx`, `packages/notebook/src/widget.ts` | A shipped keybinding table scoped by `.jp-mod-commandMode`/`.jp-mod-editMode` selectors ending `:focus`; the cell node holds DOM focus in command mode |
| `vscode` | PARTIAL | `vscode@3f3380c src/vs/workbench/contrib/notebook/browser/controller/executeActions.ts`, `insertCellActions.ts`, `editActions.ts` | The run chords on `NOTEBOOK_CELL_LIST_FOCUSED` — the **list**, not a cell, holds focus; no bare-letter set exists (kinds are `CtrlCmd+K,M`-style chords) |
| `vscode-jupyter` | ABSENT | — | Notebook chrome is core VS Code; the extension adds no notebook keymap |
| `intellij-community` | PARTIAL | `intellij-community@406a27a notebooks/notebook-ui/src/com/intellij/notebooks/ui/editor/actions/command/mode/NotebookEditorMode.kt`, `NotebookCellCaretTracker.kt`, `notebooks/visualization/resources/intellij.notebooks.visualization.xml` | The same two-mode model (`EDIT`/`COMMAND` on a message bus); keys are ordinary IDE actions bound by the user keymap, not a fixed letter table |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/src/main/java/com/termux/app/terminal/io/extrakeys/SpecialButtonState.java` | No notebook keys — but the latching-modifier answer for keys a soft keyboard lacks, already adapted as `TerminalExtraKeysRow` |
| `winlator` | ABSENT | — | No text editing surface |
| the shipped rootfs | ABSENT | — | Server-side; no UI |

**Count:** 1 of 7 ships the modal letter set (JupyterLab); IntelliJ shares the modal model but
leaves keys to the keymap; VS Code binds only the Enter chords. The set is Jupyter's own, and
ours already has the two modes it scopes — what is missing is the key layer, not the model.

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| The binding table | `packages/notebook-extension/schema/tracker.json` — every `notebook:` binding with its `keys` and `selector`; `viewmenu:line-numbering` is bound here too |
| Command semantics | `packages/notebook/src/actions.tsx` — `runAndAdvance` (:814-856), `runAndInsert` (:864-903), `selectAbove`/`selectBelow` (:1099, :1142), `insertAbove`/`insertBelow`, `deleteCells`, `changeCellType`, `extendSelection*`, `mergeSelected`/`mergeAbove`/`mergeBelow`, `move` |
| Mode and focus | `packages/notebook/src/widget.ts` — `setMode` (:1925-1929 "Edit mode deselects all cells"), `_evtFocusIn`/`_evtFocusOut` (:3541-3599), `.jp-Cell:focus` in command mode, `_ensureFocus` (:2885-2905), `handleState`/`scrollToItem` keeping the head on screen |
| The `D D` form | `tracker.json` — `['D', 'D']` is a two-key sequence: `D` alone matches nothing and waits for the next keystroke |
| Save | `packages/docmanager-extension/schema/plugin.json:103` — `docmanager:save` = `Accel S` (app-level, not notebook-scoped) |

### vscode@3f3380c668d07649b953eb8278354b2f263ea6ff

| Concern | Files |
|---|---|
| Run chords | `src/vs/workbench/contrib/notebook/browser/controller/executeActions.ts` — `Ctrl+Enter` run-in-place (:266-270), `Shift+Enter` `executeAndSelectBelow` (:509-514), `Alt+Enter` `executeAndInsertBelow` (:576-578), all `when: NOTEBOOK_CELL_LIST_FOCUSED` |
| Insert chords | `controller/insertCellActions.ts` — `CtrlCmd+Enter`-family rows (:77-79, :114-116) |
| Kind chords | `controller/editActions.ts` — `CtrlCmd+K,M` markdown (:402); the letters themselves are not bound |
| Focus model | `notebookEditorWidget.ts` — `NOTEBOOK_CELL_LIST_FOCUSED`: the cell **list** is the focus owner; a per-cell frame focus is JupyterLab's answer instead |

### intellij-community@406a27a8da071c59b052719fe6f1208c613e96b5

| Concern | Files |
|---|---|
| The modal model | `notebooks/notebook-ui/src/com/intellij/notebooks/ui/editor/actions/command/mode/NotebookEditorMode.kt` — `enum {EDIT, COMMAND}`, `setMode`, the message-bus topic |
| Command-mode caret | `…/command/mode/NotebookCellCaretTracker.kt` |
| Bindings | `notebooks/visualization/resources/intellij.notebooks.visualization.xml` — actions and `use-shortcut-of` indirections; the letters come from the user's keymap, not the plugin |

**Where they disagree:** JupyterLab puts command-mode focus on the **cell** (`.jp-Cell:focus`),
VS Code on the **list** (`NOTEBOOK_CELL_LIST_FOCUSED`). Both keep one focusable thing per mode —
that is the decision this item inherits, and the next section resolves it.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where command-mode keys land | jupyterlab → the cell node focused (`.jp-Cell:focus`); vscode → the cell **list** focused | Compose dispatches keys only to the focus owner, so command mode needs one; a list-level owner is one focusable but the "which cell" then rides on selection alone | **The selected cell's frame `focusable()` in command mode** — the `.jp-Cell:focus` analogue and `cellFocusChanged`'s own "focus *is* the mode". A frame gaining focus selects its cell (`_evtFocusIn`'s rule), which is also `GAP-005`'s tap-on-chrome primitive and the thing `Enter` → edit focuses *from*. The focus ring is ADR-0145's own rule, so the head shows it |
| The dispatch shape | termux-app → `terminalKeystroke(key, ctrlPressed, keyCode, …)` primitives → a JVM test; composable-level `onKeyEvent` on each field | Key mapping in Compose primitives is unit-testable where `KeyEvent`'s getters are not; one handler per surface duplicates the table | **One `onPreviewKeyEvent` on the cells container → a pure `notebookChord(mode, chord)` resolver.** Preview propagates root→focused, so the same handler serves the frame (command) and the field inside it (edit); unhandled returns `false` and typing/arrows keep working |
| Sequences (`D D`) | jupyterlab → `['D','D']` sequence in the keymap; a single `D` waits | A pending prefix is one small state; baking `DD` as "two events" inside a stateless map makes 'D' a lie the first time | **A pending-chord state in the dispatcher**, Lumino's semantics: exact-match-or-wait, cleared on a non-matching key or a mode change |
| `Escape` → command | jupyterlab → `notebook.mode = 'command'`, the editor blurs, focus returns to the cell | Setting mode directly would leave the field focused and immediately re-enter edit; a second mode path beside `cellFocusChanged` forks the truth | **Move focus field → frame.** `onCellInputFocus`'s blur drives `cellFocusChanged` → `COMMAND`; `Ctrl+M` (also bound upstream) is the same target — and the fallback if Esc never reaches Compose (Open decisions) |
| Cursor moves scroll | jupyterlab → `handleState`/`scrollToItem` keeps the head on screen; ours → `cellBeingEditedIndex` scrolls *nothing* in COMMAND ("stepping the selection is not a request to type") | That rule is right for programmatic selection changes and wrong for a key the user pressed to move the head | **A key-driven cursor move scrolls the head into view** — a second scroll trigger beside the edit-mode one, never fired by selection changes made elsewhere |
| The header's Run | ours → `runCell(cell)` leaves the head where it was; vscode → `focusNotebookCell(context.cell)` first (`executeActions.ts:295-297`) | GAP-005's tail: after running cell 5 with the head on 2, "run below" still means 2 | **The header's Run selects its cell first** — the same `withCellSelected` the menu's `applyingFrom` already uses |
| `Accel S` | jupyterlab → `docmanager:save` app-level binding, `plugin.json:103` | Not in `tracker.json`, but it is the one app-level key a notebook user presses | **Included** — `persistence.persist()`, one line through existing machinery |
| Which bindings ship | The whole `tracker.json` table where the command exists (the approved scope) | Rows whose commands are absent would lie on the table | **Every bound row whose command exists**; `Shift+R` (GAP-125), `run-in-console`/`match-brackets` (unbound upstream), `paste-above`/`paste-and-replace` (unbound upstream, `GAP-015`) are out of scope below |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| The two modes | `ui/editor/CellMode.kt`, `CellSelection.withMode`, `EditorViewModel.cellFocusChanged` | `jupyterlab@7f0e690 packages/notebook/src/widget.ts` `setMode` | complete — focus already is the mode; the key path to flip it is what is missing |
| Cell-scoped verbs | `ui/editor/CellAction.kt` + `CellEdits`/`CellRestructure`/`CellUndo` — every bound command's transform exists | `jupyterlab@7f0e690 packages/notebook/src/actions.tsx` | complete — insert A/B, kinds M/Y/R, headings 1–6, DD, C/X/V, merge, move, Z/Shift+Z all ship as transforms |
| Selection | `ui/editor/CellRangeSelection.kt` — extend four ways, select-all, collapse | `jupyterlab@7f0e690 widget.ts` `extendContiguousSelectionTo` | present — the Shift-extend keys land on `withSelectionExtended` |
| Run ranges | `ui/editor/NotebookCellRuns.kt` `run(RunRange)` + `cellsToRun` | `actions.tsx` `Private.runSelected` | present-and-partial — MARKED exists; the advance half does not (`GAP-020`) |
| A key-reading layer | none — `GAP-005` measured: "No composable in `ui/editor/` reads a key" | `jupyterlab@7f0e690 tracker.json`; `terminalKeystroke` (`app/src/…/ui/terminal/TerminalKeyEncoding.kt`) | **absent** — the item's core: `notebookChord` + `onPreviewKeyEvent` + the pending-chord state |
| Command-mode focus owner | none — in command mode nothing holds focus, so keys can arrive nowhere | `jupyterlab@7f0e690` `.jp-Cell:focus`; `vscode` `NOTEBOOK_CELL_LIST_FOCUSED` | **absent** — the frame-focus primitive, T2 |
| Run-and-advance | — | `actions.tsx:814-903` `runAndAdvance`/`runAndInsert` | **absent** — `GAP-020`, T1 |
| Cursor moves | — | `actions.tsx:1099-1142` `selectAbove`/`selectBelow`, heading moves `index.ts:245-248` | **absent** — `GAP-005`'s four commands, T3 |
| Line-number toggle | `EditorSettings.lineNumbersShown` (`domain/model/EditorSettings.kt`), `EditorSettingsRepository` already injected in `EditorViewModel` | `viewmenu:line-numbering` = `Shift L` | present — the binding is a settings write |
| Kernel-history keys | `SymbolRow`'s `Hist ↑/↓` gated on `accessKernelHistory` | `access-previous/next-history-entry` = `Alt ArrowUp/Down` | present — the chords bind the same two callbacks, same gate |

## Deviations

- **A chord-capable soft keyboard is the soft half, not Gboard.** Command-mode letters are
  unreachable from a hidden IME by design — the acts keep their on-screen surfaces (cell menu,
  action bars, `SymbolRow`); holding the keyboard up in command mode to fake them is the
  rejected alternative, because it would cover the notebook the mode exists to show.
- **The map is a table, not a switch.** `notebookChord` resolves `(mode, chord) → intent` from
  one binding list mirroring `tracker.json`; a new binding is a row, not an arm (this is also
  why the file cites the schema's command ids, which are the shared vocabulary).
- **Injected keys stand in for external keyboards in the demo**, and the commit body says so:
  `input` injects into the identical `dispatchKeyEvent` path a BT/USB device walks; the agent
  cannot attach hardware.
- **`Accel` is `Ctrl`.** JupyterLab's `Accel` is Cmd on macOS; Android has no Cmd — `Ctrl` is
  the platform's accelerator (the same reading `text-history-driver.sh` already uses for
  `Ctrl+Z`).

## Open decisions

- **Whether `KEYCODE_ESCAPE` reaches Compose on the tablet.** Some builds fold Esc → Back
  before the view tree; `Ctrl+M` is bound upstream for the same act, so the fallback exists by
  design. Measured in T2: `input keyevent KEYCODE_ESCAPE` reaches Compose on the SM-X216B (and
  dev36) — an unclaimed Escape in command mode matched no binding and the notebook stayed open.
  A claimed stroke still consumes its key-up, so a build that does translate never sees the
  release — the fallback stays real for hardware keyboards the demo cannot reach.
- **What Unexpected Keyboard's Enter sends.** `sendKeyEvent` arrives at `onPreviewKeyEvent`
  and the chords bind it; `commitText("\n")` would land as text in the field instead, which
  narrows what the IME half can drive. Measured on the device during T1 — the demo then drives
  whichever set the IME actually emits and says which in the evidence.
- **Whether `Alt` arrives on injected/IME events** for `Alt+Enter`, `Alt+arrows` —
  `META_ALT_ON` is read off the same event; measured alongside the Enter question.
- **Scroll strength for key-driven head moves** — JupyterLab scrolls `activeCellIndex` into
  view on every move; whether ours needs the full `scrollToItem` or `scrollIntoViewIfNeeded`'s
  cheaper form is settled against a long notebook in T3, not guessed.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — none: the intents resolve to existing model types (`CellAction`, `CellKind`, `SelectionReach`) |
| `domain/port` | — none: `EditorSettingsRepository` (Shift+L) already exists and is injected |
| `data/<mechanism>` | — none: no adapter touched; persistence uses `DocumentAutosave`/`persist` |
| `data/fake` | — none new |
| `ui/<screen>` | `ui/editor/NotebookKeymap.kt` (new — `notebookChord`, the binding table, the pending-chord state), `ui/editor/CellFrame.kt`/`EditorCell.kt` (the command-mode focusable frame), `EditorContent.kt`/`EditorScreen.kt` (the `onPreviewKeyEvent` wiring), `NotebookCellRuns.kt` (`runAndAdvance`/`runAndInsert`), `CellEdits`/`CellRangeSelection`/`NotebookCellSelection` (cursor-move transforms + heading moves), `EditorViewModel`/`EditorCellActions`/`NotebookCellEdits` (intent dispatch, `editorSettings` for Shift+L, header-Run-selects) |
| `di/AppContainer` | — unchanged |
| unit tests | `NotebookKeymapTest` (table, both modes, pending chord), `CellSelection`/`CellRangeSelection` cursor tests, `NotebookCellRunsTest` advance/insert, header-Run test |
| instrumented tests | `NotebookHardwareKeyTest` — `performKeyInput`/`withKeyDown` through the real dispatch (`TerminalHardwareKeyTest`'s precedent); frame focus ring on the head cell |
| `.maestro/` flow + `scripts/` driver | `jupyter-keys-*.yaml` + `scripts/jupyter-keys-driver.sh`; Unexpected Keyboard installed on the tablet for the IME half |

Every port gets a `Fake…` — unchanged: this item adds no port.

## Tasks

- [x] T1 — **The key layer and the run chords.** `notebookChord(mode, chord) → intent` with the
  edit-mode rows, `onPreviewKeyEvent` on the cells container, and the three Enter commands:
  `Ctrl+Enter` in place, `Shift+Enter` = `runAndAdvance` (head+1; at the last cell, an undoable
  default-cell insert + EDIT), `Alt+Enter` = `runAndInsert` (insert below + head+1 + EDIT,
  scrolled in). `NotebookHardwareKeyTest` drives `performKeyInput`. Closes `GAP-020`.
- [x] T2 — **The command-mode focus owner.** The selected cell's frame `focusable()` in
  command mode; focus-on-frame selects (`GAP-005`'s primitive — a tap on chrome selects); the
  head cell's frame follows the selection; `Enter` → field focus → EDIT; `Escape`/`Ctrl+M` →
  frame focus → COMMAND. The ADR-0145 ring draws on the head frame. Esc-reachability measured
  here. Closes `GAP-005`'s primitive.
- [ ] T3 — **Cursor moves.** `J`/`K`/`↑`/`↓` step the head skipping hidden cells
  (`selectAbove`/`selectBelow`'s scan), Shift-extend (`Shift+J/K/arrows/Home/End`),
  `←`/`→` heading moves-or-collapse (`index.ts:245-248`'s pair), and the head scrolls into
  view on a key-driven move. The header's Run selects its cell first. Closes `GAP-005`.
- [ ] T4 — **The letter set.** `A`, `B`, `D D` (pending chord), `M`, `Y`, `R`, `1`–`6`
  (markdown cells only — the selector is `.jp-MarkdownCell:focus`, so a digit on a code cell
  falls through), `C`, `X`, `V`, `Z`, `Shift+Z`, `Shift+M`, `Ctrl`+`Bksp`,
  `Ctrl`+`Shift`+`M`, `Ctrl`+`Shift`+`↑`/`↓`, `Shift`+`A`/`B`, `Ctrl`+`A`, `Shift`+`L`,
  `Ctrl`+`Shift`+`←`/`→` (collapse/expand all headings), `Ctrl`+`Shift`+`-` (edit-mode
  split), `Alt`+`↑`/`↓` (`accessKernelHistory`-gated history), `Ctrl`+`S` save — and the
  command-mode `Ctrl`+`Enter` row, bound on both selectors upstream. Each binds through
  `cellAction`/`edits`/`selection`/`editorSettings`, no new verb invented; `Shift` is part
  of the chord, which is what separates `M` (markdown) from `Shift M` (merge).
- [ ] T5 — **The demo and the ship.** The numbered Demo on `SM-X216B`: Unexpected Keyboard for
  the IME half, `input keyevent`/`keycombination` for the injected half; evidence per AGENTS
  rule 2, every frame viewed; `Demo run by the agent, not by a human` in the body; the durable
  decisions become an ADR (the chord table and the frame-focus model, if the shape survives
  the tasks); the PRD is deleted and the backlog edited.

## Out of scope

- `notebook:toggle-render-side-by-side-current` (`Shift R`) — no side-by-side layout exists
  (`GAP-125`); a binding for an absent command is a lie on the table.
- `notebook:run-in-console` and `viewmenu:match-brackets` — unbound upstream (`['']`), and
  foreign execution is `GAP-073` regardless.
- `notebook:paste-cell-above`, `notebook:paste-and-replace-cell` — unbound upstream; `GAP-015`
  stays a surface question, not a key one.
- `I,I` interrupt, `0,0` restart, `Space`/`Shift+Space` scroll, `H` help — classic-notebook
  bindings JupyterLab 4 dropped; the table shipped is the reference's current one.
- A user-editable keymap — `GAP-134` sits behind this item by its own text: the binding model
  lands here, the editor to write it is a later item.
- `notebook:select-*-modified-cell`, `select-last-run-cell` — unbound upstream (`GAP-021`,
  `GAP-022` own them).
