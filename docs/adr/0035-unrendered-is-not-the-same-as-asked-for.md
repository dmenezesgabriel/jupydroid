# ADR-0035 — Unrendered is not the same as asked for, and only a tap asks

Status: accepted
Date: 2026-08-18

## Context

`changeCellType` ends with *"Fetch the new widget and unrender it"* — `(child as MarkdownCell).rendered
= false` — and that `if` sits **outside** the `child.model.type !== value` branch, so it reaches every
cell the command visited and not only the ones whose type changed. A mixed run changed to markdown
opens the ones that already were markdown.

Ours rendered them at once, so converting a code cell **hid the very text that had just been
converted**.

**Fixing that exposed one of ours.** `MarkdownCellField` requested focus for having been composed at
all — a complete answer while a tap on the rendered prose was the only way to compose one. With a
second way in, a run changed to markdown composed several editors in one frame, each asking for the
caret and one winning it: the soft keyboard up over a notebook nobody was typing in.

Both of the flow's assertions were green; the screenshot is what said otherwise.

## Decision drivers

- Being open for editing and holding the caret are different facts, and conflating them puts a keyboard
  up unasked.
- Only one field can hold the caret, so the state that tracks it is an id, not a set.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Render a converted markdown cell at once | Shows the prose | Hides the text the user just converted, which is the opposite of what the command means |
| Unrender it, and let the editor take the caret on composition | One state | Several editors compose in one frame after a run conversion, and one wins a caret nobody asked for |
| Two states: unrendered, and asked-for | Matches the reference's mode exactly | Two flags where one looks sufficient |

## Decision

The two facts are **separate state**. `markdownBeingEdited` is whether a cell is unrendered;
`markdownTakingCaret` is whether its editor was **asked for**, set by `withMarkdownEditorOpened` — the
tap — and by nothing else, and withdrawn when the cell renders again.

One id and not a set, because only one field can hold the caret.

That is the reference's mode: `changeCellType` ends on `notebook.deselectAll()` with **no mode change**,
so the notebook stays in **command** mode.

The flow asserts the symbol row's first key is *not* on screen afterwards, which is how "still in
command mode" becomes a claim rather than something a screenshot happens to reveal.

## Consequences

Any new way of opening a markdown editor must go through `withMarkdownEditorOpened` or it will open the
cell without the caret — which is the correct default, and the reason the one tap path is explicit.

The thing a future reader will be tempted to "fix" is the second flag, which looks like it could be
derived from the first. It cannot: a run conversion unrenders many cells and asks for none.
