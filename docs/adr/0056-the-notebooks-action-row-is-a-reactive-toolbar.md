# ADR-0056 — The notebook's action row is a `ReactiveToolbar`, and the overflow is its popup opener

Status: accepted
Date: 2026-08-22

## Context

Removing this app's invented `markdown` and `raw` prompt words was right
([ADR-0053](0053-a-cell-at-rest-carries-no-mark-and-the-editor-border-is-the-boundary.md)) and it left a
hole: **nothing named the active cell's type at all.**

The reference's answer is not the prompt but the `cellType` item on the *notebook* toolbar at rank 40 — an
`HTMLSelect` whose value is `notebook.activeCell.model.type`. It is deliberately **not** a cell-toolbar item
there; that toolbar's entries are duplicate, move, insert and delete.

**And the notebook's action row has 72dp free at 320dp against a 73.3dp label**, measured. So the bar cannot
carry the cell-type control on a phone.

What happens then is not a choice. A `ReactiveToolbar` **moves** an item too wide for the row into a popup
opener, and its popup opener is **always the last widget**: it is inserted first, every later `insertItem`
clamps to `widgets.length - 1` so items land *before* it, and `_getWidgetsToRemove` walks
`while (index < toIndex)` with `toIndex = widgets.length - 1` — the opener's own index — so it is never
collected either.

That last mechanism also answers a separate question. An earlier plan row asked for the kernel indicator to
be *"the rightmost item"*. JupyterLab ranks `kernelName` **1000** and `executionProgress` **1002**, the last
two things on its notebook toolbar — but the opener sits after both.

## Decision drivers

- A control that does not fit must go somewhere, and the reference already says where.
- A rule read out of the reference's layout beats a rule recalled from its rank numbers.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Put the cell type back on the cell | Always visible | The reference puts it on the notebook toolbar, deliberately not on the cell's |
| Keep it on the bar at every width | One place | 72dp free against a 73.3dp label at 320dp |
| Bar where it fits, overflow where it does not | What a `ReactiveToolbar` actually does | The overflow menu's meaning widens, which has to be stated |
| Kernel indicator as the rightmost item | What the plan asked for | The reference's own popup opener is after `executionProgress`, so "rightmost" is the overflow, not the indicator |

## Decision

**The cell type is named on the bar where there is room and in the notebook's overflow where there is not**,
which is what a `ReactiveToolbar` does with an item that does not fit. `NotebookActionsMenu` is this app's
popup opener — the one overflow a phone's bar has room for.

**That widens what the menu is, and the widening is stated rather than absorbed.**

**The kernel indicator is right of Find and left of the `⋮`**, because the popup opener is the last widget in
the reference too. `ActionRowPlacement` states this about itself.

The reference's `-` for a mixed or absent type is **not modelled**; there is always exactly one active cell
here.

## Consequences

`ActionRowPlacement` is the one place that knows what goes where and in what order, so a new bar item is a
placement question rather than a layout edit.

The overflow menu is now two things — cell actions and displaced bar items — and that is the reference's shape
rather than drift.

The thing a future reader will be tempted to "fix" is moving the kernel indicator to the far right, because
that is where its rank suggests. The opener outranks its rank by construction.
