# ADR-0179 — Command-mode focus is the head cell's frame, and the key bindings are a table

Status: accepted
Date: 2026-11-27

## Context

Jupyter's keyboard shortcuts needed two decisions before any binding could exist. The first:
where does a key *land* when no text field wants it? A Compose field's focus is already spoken
for — it reports edit mode (`EditorViewModel.cellFocusChanged`), so command-mode keys cannot be
read off it. The second: the reference's binding model is not a `when` over key names.
`packages/notebook-extension/schema/tracker.json`
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`) is a list of `(keys, selector, command)`
rows where the CSS selector is half the answer — the same `Ctrl`+`Enter` runs in command mode and
in edit mode, and `notebook:delete-cell` is the *sequence* `["D","D"]`, which no single-chord
shape expresses.

## Decision drivers

- One focusable per notebook in command mode, matching the reference: JupyterLab gives every cell
  `tabIndex="-1"` and the active cell `tabIndex="0"` (`packages/notebook/src/widget.ts:2734-2750`)
  — a single Tab stop, and it is the selected cell's.
- A key must not need a new code path per keyboard shape. `input keyevent`, a USB/Bluetooth
  keyboard and a soft keyboard that sends `KeyEvent`s all reach `dispatchKeyEvent`, so a binding
  written once works for all three — measured on device with Unexpected Keyboard
  (`juloo.keyboard2`), whose `Ctrl`/`Shift`/`Alt` latch as system modifiers and whose `enter` and
  `esc` go down as real keyevents carrying the latched meta state.
- Adding a binding is editing a row, not a code path: `notebook:insert-heading-above` shipped as
  one `NotebookBinding` line in `NOTEBOOK_BINDINGS`.
- Sequences are data, not state machines in the handler: `D` `D` resolves through
  `NotebookChordResolution.Pending` in `notebookChord` rather than a timer or a flag.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Read keys on each cell's field | No new focusable | The field's focus *is* edit mode here; a command-mode key would have to be intercepted before the field's own handling on every cell — a per-editor patch, and the frame's "selected without editing" state still could not be held |
| The head cell's frame owns command-mode focus; bindings as a `tracker.json`-shaped table | One Tab stop like the reference; mode and focus-selector as row data; sequences in the resolver | The frame is a synthetic focusable — a UI dump cannot read its "focus" flag (`SemanticsProperties.Focused` is not invalidated by `requestFocus`), so tests read `frameOwnsFocus` and Maestro asserts the frame's bounds |
| IME-level key interception | Would work with no field focused | Reaches around the app's own mode model and breaks the hardware path the field already walks |

## Decision

The head cell's frame is the notebook's one command-mode focusable, and the bindings are a
declarative table keyed on `(CellMode, NotebookChordSelector, chords)`.

`Modifier.headCellFrame` in `app/src/main/java/com/jupydroid/app/ui/editor/CellFrameFocus.kt`
sits on the head cell's frame: `focusable` with a `FocusRequester` the dispatch site drives,
`onEnter` firing only on the *rising* edge of subtree focus — a descendant handing focus up (the
editor's `Escape`, or the focus system's own restore during an edit-mode recomposition) arrives
with `hasFocus` already held and does not retrigger, which is upstream's `_evtFocusIn` ignoring
`relatedTarget` inside the cell. The ring is drawn on `isFocused` in keyboard input mode only, so
a `⋮` inside the cell keeps its own keys without ringing the cell.

`NOTEBOOK_BINDINGS` in `NotebookChordTable.kt` mirrors `tracker.json` one row per binding:
`Accel` read as `ctrl` (Android has no command key; a chromebook's search-key-Enter produces
`Ctrl`+`Enter`), `commandMode`/`editing` as `CellMode`, the `:focus` classes as
`NotebookChordSelector` (`NOTEBOOK_FOCUS` any descendant, `CELL_FOCUS` the cell node itself,
`MARKDOWN_CELL_FOCUS` the markdown cell node). `notebookChord` resolves a chord to an intent, a
`Pending` for a sequence head like `D`, or none. `EditorNotebookKeys.kt` and
`NotebookChordRuns.kt` turn intents into ViewModel acts — `runAndAdvance` an
`EditorUiState::withRunAdvanced` transform, the letter set `CellAction`s, `DD` a two-step
`Pending`.

## Consequences

A new Jupyter binding is one table row plus, when the intent is new, one act — the letter set,
the cursor keys and the split/merge chords each shipped as rows. Both keyboard halves reach the
same dispatch, so the demo drives one path for the virtual keyboard and the injected keyevents a
hardware keyboard sends.

The cost is that the frame is invisible to the usual probes: `SemanticsProperties.Focused` does
not track a programmatic `requestFocus`, so `CellFrameFocus` publishes `frameOwnsFocus` for
tests, and a Maestro run asserts the frame's bounds wrap a cell's text and looks at the
screenshot — the shape the demo driver takes.
