# ADR-0048 — Touch rules, and the four that cost something to learn

Status: accepted
Date: 2026-08-14

## Context

This app is a mobile adaptation of a desktop product, so most interaction questions have no reference line
to copy. Four of them were learned the expensive way.

## Decision drivers

- A control the user cannot discover is not a control.
- A control on screen for a state it does not serve teaches the user that controls lie.

## Decision

**48dp is the minimum touch target** (Material), pinned by `CellTouchTargetTest`. The nuance is worth
keeping because it once produced a false accessibility bug report: `IconButton` applies
`minimumInteractiveComponentSize()` **outside** the caller's modifier, so wrapping one in
`Modifier.size(32.dp)` shrinks the ripple and *not* the pressable area — measured on device at 48×48dp with
the wrapper in place. What the test really guards is a control built from a bare `clickable`, which would
have a real 24dp target and nothing else to catch it.

**A touch target is reserved space, not expanded bounds.** The space is budgeted in the layout rather than
borrowed from a neighbour at press time.

**A long press on a cell is not available.** `BasicTextField` owns that gesture for text selection, so cell
actions need a visible control — which is why the cell header carries a `⋮` and not a hidden gesture. It
took three attempts to learn: a press on the cell produced Android's Select all / Autofill toolbar, and a
press on the header worked but was undiscoverable. **Nothing on screen announcing an interaction is not an
interaction.**

**A control that is on screen for a state it does not serve is a control that lies.** The symbol row shows
in edit mode whatever the cell is, and `MarkdownCellField` never registered itself with `FocusedCellInput` —
so in a markdown cell the row sat above the keyboard doing nothing at all. It is the same failure the `⋮`
exists to avoid, one layer down, and it went unseen until a flow tried to drive a caret with `←` there.
Registering is one shared composable now (`attachWhileFocused`) rather than a block each field could forget.

**Anything drawn above the soft keyboard needs `imePadding()` *and* `navigationBarsPadding()`** — the second
for the moment between the keyboard closing and the layout catching up.

**A behaviour that cannot be reached on a phone is adapted, never invented**, and the adaptation is written
down beside what it departs from.

## Consequences

`attachWhileFocused` exists so a new field type cannot forget to register; a field that opts out of it
reintroduces the lying symbol row.

The `IconButton` nuance will be re-discovered as a bug report again. The measurement is 48×48dp on device
with a 32dp wrapper in place, and the test that matters is the one guarding bare `clickable`.

The thing a future reader will be tempted to "fix" is adding a long-press shortcut for cell actions, since
it is the desktop idiom. `BasicTextField` owns the gesture and the platform's own toolbar wins.
