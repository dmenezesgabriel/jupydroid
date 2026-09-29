# ADR-0052 — Kernel history is the kernel's, and a walk belongs to the cell it started in

Status: accepted
Date: 2026-08-20

## Context

`notebook:access-previous-history-entry` / `access-next-history-entry`. The wire is `history_request` on the
shell channel — `{output: false, raw: true, hist_access_type: 'tail', n}` — a batch of **10** grown by 10 on
each exhaustion.

The shipped kernel answers out of IPython's **sqlite input history**:
`ipykernel/kernelbase.py` → `ipkernel.py` → `history_manager.get_tail`, which calls `writeout_cache()`
before it queries, so a cell run a moment ago is already in the reply, and it orders the **current**
session's rows last.

**So this is not a log of the notebook.** It reaches cells that have been deleted and sittings the document
was never open for, which is the whole of what it buys over scrolling to the cell that ran something.

## Decision drivers

- A control that can do nothing must not be on screen.
- A walk has to end on the user's next real action, and a debounced edit report can arrive late.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Show the raw history | Nothing to explain | `setFilter`'s three rules are what make a tap do the expected thing; the raw list repeats and includes the current text |
| Put the keys in the cell menu | No new surface | Eighteen rows deep, and reachable in command mode where the reference's own binding would not match |
| Put the keys on the symbol row | The row is on screen exactly when the reference's `editMode` selector would match | The row's real estate is contested, so the placement had to be measured |
| End the walk on a flag set while writing | The reference's own approach | A debounced edit report can land **after** the next tap, so a flag is already cleared. A compared *value* stays true whenever the report arrives |

## Decision

**The filtered list is not the history.** `setFilter` is three rules and each changes what a tap does: a run
of the same input counts once; the text **already in the cell is removed**; and that text is pushed on as the
walk's starting point.

**Access is off by default — the first reference default this app keeps.** `accessKernelHistory` is `false`,
both actions return on their first line without it, and the reference's own user guide makes it an
instruction: *"To use this feature, enable kernel history access in the notebook settings"*. The other
`EditorSettings` departures were each bought with a reason a phone supplies; this one has none, so the
reference's default stands.

**The keys go on the symbol row**, because the reference binds them to `Alt ArrowUp`/`Alt ArrowDown` under a
`.jp-Notebook.jp-mod-editMode` selector, there is no Alt key here, and the symbol row is the only thing on
screen exactly when that selector would match.

**Absent, not disabled, while the setting is off** — the rule this app applies to every control that could do
nothing.

**Labelled `Hist ↑` / `Hist ↓` rather than `↑` / `↓`.** Beside `←` and `→`, which move the caret one
character, a lone arrow reads as moving it one *line*. The word separates walking the document from walking
the kernel's history; the `contentDescription` carries the reference's own wording.

**Typing ends a walk; the write the walk itself made does not.** Here that is a compared **value** rather than
a flag, because a cell reports an edit on a 150 ms debounce and the report can land after the next tap.

**Leaving the cell ends it — a narrowing.** The reference binds its editor once and keeps the walk across
cells; a walk that survived a cell change would write one cell's history into another.

## Consequences

A walk's end condition is a comparison, not a flag, for the same reason adoption is a counter rather than a
comparison ([ADR-0031](0031-splitting-reads-the-editor-so-a-cell-owns-one-text-state.md)): the 150 ms
debounce means neither direction can be inferred from equality alone, and each mechanism picks the one that
is safe in its direction.

The symbol row is now load-bearing for a feature that is off by default, so the row's own visibility setting
and this one interact — a fact worth remembering before either is changed.

The thing a future reader will be tempted to "fix" is the `Hist` prefix, which is less clean than a bare
arrow. Beside the caret arrows, a bare arrow reads as a line move.
