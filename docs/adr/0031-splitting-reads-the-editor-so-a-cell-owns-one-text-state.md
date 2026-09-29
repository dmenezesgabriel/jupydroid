# ADR-0031 — Splitting reads the editor, so a cell owns one text state

Status: accepted
Date: 2026-08-15

## Context

`splitCell` contradicts the obvious answer twice, and both are load-bearing: **the original cell
becomes the *last* fragment** — *"The last piece will remain in the original cell to preserve kernel
connection"* — and **every new cell is created with `outputs: undefined`**, so the results and the
execution count stay on that last fragment.

Neither costs anything here, because the original keeps its id and `outputs`, `executionCounts` and
`executions` are all keyed by id. Each fragment is stripped of its own leading and trailing
**newlines only** (`/^\n+/`, `/\n+$/`), so a fragment cut from inside a block keeps its indentation,
and a cell mid-run does not hand its `execution` metadata to fragments that never ran.

Measured on both devices and on disk: `code-1`, the fixture's id, comes back on the *second* cell,
with the output and `execution_count: 7`.

Split is the one cell operation that is **not a pure function of the document**. It needs the caret,
which is not in the document at all, and the field's text, which may be *ahead* of the model by up to
one 150 ms debounce — the tap that opens the cell menu can beat the report.

## Decision drivers

- An undo must not destroy text the operation itself created.
- A field on screen must never silently lose keystrokes to a model that is one debounce behind.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Split from the model's source | Pure function, easy to test | Splits at stale text: the undo record held the wrong source, and a test caught it |
| Pass the editor's text and caret alongside the action | Correct at the moment of the tap | A cell operation is no longer a pure function of the document, and that has to be stated |
| Let a field notice its text changed by comparing | No extra signal | **Cannot work**: the model is routinely one debounce behind, so a field that is merely *ahead* looks exactly like one that is *behind*, and adopting on the difference eats keystrokes |

## Decision

`CellEditorText` travels beside the action, and `CellEdits.applying` brings the cell up to it before
recording anything. The reference reads its editor for the same reason — `editor.getSelections()`
beside `sharedModel.getSource()`.

**ADR-0028's "a cell operation never restores text" gains exactly one exception, and it is not a
waiver.** The record names the cells whose source *the operation itself* rewrote —
`NotebookRevision.rewrote`, computed from the before/after diff — and restores only those; every other
surviving cell keeps the source it has now. Without it, undoing a split would drop the new fragment
and leave the original holding only the half below the caret, the text above it destroyed with nothing
left to undo. The diff is Yjs's own shape: a transaction records the delta, not the document.
Find-and-replace inherits the same mechanism.

**A field on screen is *told* its text was rewritten, never left to notice.** The same diff bumps
`EditorUiState.sourceRevisions`, and `trackCellSource` adopts on a bump. Both directions share one
`LaunchedEffect`, so a report held in the debounce cannot land *after* a rewrite and undo it.

**A cell owns one text state, not one per field.** A cell has one source however it is being shown,
and both fields are handed the same `TextFieldState`. That is what makes a markdown cell splittable at
all, and what keeps its caret across being rendered and opened again.

**One adaptation, and it is a narrowing.** The reference splits at *every* selection; a Compose
`TextFieldState` carries one, so this is two fragments from a caret and three from a range, never more.

**Split is offered on every cell whether or not a caret was ever put in one**, which is the reference's
`isEnabled` (`!!activeCell?.editor`) read literally: a cell whose caret was never placed splits at 0
and gets an empty cell above, and an undo takes it back. **A markdown cell is included, rendered or
not** — `renderInput` and `showEditor` swap only which widget is *displayed*, and `inputArea.editor`
exists either way, so the first guess that a rendered markdown cell has no editor to read a caret from
is wrong about the reference as well as unnecessary here.

## Consequences

One cell, one `TextFieldState`, shared by both fields. Giving each field its own would break markdown
splitting and lose the caret across a render toggle.

The bump-and-adopt signal cannot be replaced by a comparison, and that is the single most likely thing
for someone to "simplify" here. The debounce is why.

**A control's name goes on the button, not on the icon inside it.** `enabled` lands on the
`IconButton`'s accessibility node while a `contentDescription` on the `Icon` lands on a *child* of it,
so naming the icon leaves the label and the state on two different nodes — measured rather than
reasoned: a flow asking for the disabled control found a node reporting `enabled=true` with a disabled
parent one level up.
