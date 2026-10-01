# ADR-0068 — Leave the text inside a cell to its field; keep only the cells' clipboard and history

Status: accepted
Date: 2026-10-01

## Context

JupyterLab gives a notebook **two** of everything a user does to text. Cells have a clipboard
(`notebook:copy-cell`, `cut-cell`, `paste-cell-below` …) and a history (`notebook:undo-cell-action`),
and the text inside one cell has its own: `notebook:copy-selected-text`, `cut-selected-text`,
`paste-text`, `notebook:undo` / `notebook:redo`, and `notebook:replace-selection` for code that
inserts text into the active cell (`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/notebook-extension/src/index.ts:280-286`, `:3786-3920`, `:4476-4547`, `:5055`). The text
three are rows in its cell context menu (`packages/notebook-extension/schema/tracker.json:250-266`);
the undo pair is **Edit → Undo / Redo**, wired through `editMenu.undoers` (`index.ts:5365-5372`); and
`replace-selection` has no surface at all — nothing inside the reference calls it, it is there for
other extensions.

This app built the cell half: `EditorUiState.clipboard` is a `List<Cell>`, and
`ui/editor/CellUndo.kt` is the cell history, whose docstring already names the split — *"the text one
is already ours: Compose's `TextFieldState` carries its own"*. The text half was never written down
as a decision. It was measured instead, on the arm64 tablet (`SM-X216B`, Android 16, Compose
foundation 1.10.6 from BOM 2026.03.01):

- **The text clipboard is the platform's toolbar.** A long press in a cell's field opens Android's
  floating toolbar (`android:id/floating_toolbar_menu_item_text`): Cut, Copy and Select all over a
  selection, Paste once the system clipboard holds text, Autofill. Compose's `TextContextMenuItems`
  enumerates exactly Cut, Copy, Paste, SelectAll and Autofill — there is **no Undo item** to offer.
  Copy from it and Paste into it both went through the system clipboard and back into the field.
- **The two clipboards are two.** A cell copied with the cell menu's Copy was *not* what the next
  text Paste produced: the paste brought back the text copied before it. A cell never reaches the
  system clipboard, and text never reaches `EditorUiState.clipboard`.
- **The text history is `TextFieldState`'s**, reached by the platform key mapping only: Compose maps
  `Ctrl+Z` to `KeyCommand.UNDO` and `Ctrl+Shift+Z` / `Ctrl+Y` to `REDO`
  (`KeyMappingKt.commonKeyMapping`). Typed `typed` and one `Ctrl+Z` took all five keys back — the
  state merges a run of typing into one entry — further presses did nothing, and `Ctrl+Shift+Z` put
  the run back. The cell-operation undo stayed disabled throughout.
- **And the active IME sees the key first.** Android hands a key event to the input method before
  the view hierarchy. Under Gboard, `Ctrl+Z` took back *one character* per press and, past its start,
  put characters back — the keyboard's history of its own commits, not the field's. Only with an IME
  that keeps no history (Google's voice IME) did the field's own history answer.
- **`replace-selection`'s counterpart is a call, not a command.** Everything in this app that puts
  text into the active cell — the symbol row, history recall — goes through
  `ui/editor/FocusedCellInput.kt`, an object handed the focused `TextFieldState`.

## Decision drivers

- The platform's text selection is what every Android text field does; a second copy of it, in a
  menu, is a control that duplicates one the user already knows.
- A cell is not text. Pasting a cell is a structural edit with ids, outputs and sections
  (`CellRestructure.withClipboardPasted`); text pasted into a field is not.
- No code outside this app runs in its UI, so a command id with no surface has no caller.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Rows for the three text commands in the cell menu | Literal parity with the reference's context menu | Two menus for the same Copy, in a cell menu that already has to scroll |
| Put cells on the system clipboard as nbformat JSON | Cells could cross to another app | A cell pasted into a text field would arrive as JSON; the reference keeps its cell clipboard in-process too |
| **The field's own selection, clipboard and history; ours for cells** | Each half uses the store that fits it; no duplicate controls | Text undo has no touch control, and outputs are not selectable — both recorded as gaps |

## Decision

**Leave the text inside a cell to its field — the platform's toolbar, the system clipboard and
`TextFieldState`'s history — and keep a clipboard and a history of our own only for cells.**

A text command that the reference registers is answered by the field rather than mirrored: Copy,
Cut and Paste by the floating toolbar, Undo and Redo by `Ctrl+Z` / `Ctrl+Shift+Z`, and
`replace-selection` by `FocusedCellInput`, whose `type` replaces a selection exactly as a key does
(`FocusedCellInputTest.typingOverASelectionReplacesItJustAsTheKeyboardsDoes`). The two histories
stay apart for `CellUndo.kt`'s reason: a cell operation must never carry text back with it.

## Consequences

**What the platform does not offer, this app does not have.** Two things fall out, and both are
gaps rather than decisions: the text history has **no touch control** — the reference's Edit menu
has one, and the platform toolbar has no Undo item (`GAP-018`) — and **output text cannot be
selected**, because outputs draw through a plain `Text` with no `SelectionContainer`, so the half of
`copy-selected-text` that falls back to the page's selection has nothing to read (`GAP-017`).

**The keyboard can take `Ctrl+Z` before the field does.** That is the platform's order, and it is
why a hardware-keyboard undo can behave differently under different keyboards. Intercepting the key
above the IME would make the field's history win, and would also take the keyboard's own undo away
from a user who expects it; it is not done.

**What a future reader will be tempted to "fix".** Adding Cut/Copy/Paste rows to the cell menu, or
widening `EditorUiState.clipboard` to the system clipboard. The first duplicates a control every
field already has; the second makes a pasted cell arrive in a text field as JSON. A text Undo
*control* is a different matter, and is the gap above.
