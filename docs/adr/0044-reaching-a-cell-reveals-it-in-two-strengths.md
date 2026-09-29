# ADR-0044 — Reaching a cell reveals it, in two strengths, and the search bar takes the notebook's

Status: accepted
Date: 2026-08-19

## Context

The reference anchors a floating panel at the top-right of the document carrying a find box, three option
toggles, a counter, two arrows, a filter button and a close — **nine controls in one row**. At 360dp that
is arithmetic this app has already done once and lost.

And revealing a match is two steps in the reference, not one: `activateNewMatch` scrolls when the cell is
out of view *and then* calls `editor.revealPosition(...)`. The second step is what makes the match
**readable** rather than merely present.

**`inViewport` alone is not the rule, and the IME is why.** Every surface this app can have on screen under
a soft keyboard carries its own `imePadding` — the symbol row in edit mode, the notebook bar in command
mode — so the cell list never had to. **Searching is the first state with a keyboard and no bottom bar at
all**, and without the inset the list lays out to the whole window and draws its lower half behind the keys.

Measured rather than noticed: with the IME up the list reported `viewport = 0..987` on the tablet, so
`visibleItemsInfo` called a cell *visible* while it was under the keyboard — and the arrows declined to
scroll to a match nobody could see, correctly by their own rule and wrongly by any other. **Three flow runs
went green through it; the screenshot is what said otherwise**, for the third time in this project.

## Decision drivers

- A control the user cannot reach at 360dp is not shipped.
- A match that is technically on screen but under the keyboard has not been revealed.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A floating find panel, as the reference has | Parity | Nine controls in one row do not fit at 360dp |
| Find bar takes the notebook's bar | Fits, and matches what marking a run already does | Two bars must not be on screen at once |
| Guard the scroll on `inViewport` | The obvious reading | A cell under the IME reports as visible, so the arrows refuse to move |
| Guard on **fully** visible, and give the list an `imePadding` | Matches the reference's two-step reveal with a cell-sized item | One more surface owning an inset |

## Decision

**The find bar takes the notebook's bar**, exactly as marking a run does.

**And the bottom bar goes with it** — not for symmetry but as a measured repeat of an earlier defect. The
find box holds the caret while the notebook is in *command* mode, a state nothing before this could reach,
so the kernel bar would be on screen under a soft keyboard. On a 320dp-tall landscape phone that stacks a
find bar, a kernel bar and the IME, and `Scaffold` resolves the overflow by handing the cell list **zero
height**.

**The cell list takes the IME inset**, because searching is the first state with a keyboard and no bottom
bar to carry it.

**The reveal guard is *fully* visible, not visible.** With a cell-sized list item, the equivalent of the
reference's second step — `revealPosition` — is asking for the whole cell.

**Any jump expands the collapsed heading above its target**, which is the reference's own wiring from
`activeCellChanged`.

**One command, two strings, and that is a harness rule.** The action on the notebook's bar is "Find in
notebook" and the box is "Find". A flow closing the bar asserts the box is gone, and Maestro matches whole
nodes — so two controls meaning the same thing must not be spelled the same way, or the assertion is
answered by the icon that just came back.

## Consequences

The "two controls meaning the same thing must not share a string" rule generalises past search: any pair
where one opens the other needs distinguishable labels, or a Maestro assertion cannot tell them apart.

The `imePadding` on the cell list is the kind of thing that looks removable, because every other state on
screen already has one. Searching is the state that does not.

The thing a future reader will be tempted to "fix" is the fully-visible guard, which looks stricter than
needed. It is the cell-sized equivalent of the reference's second reveal step, and the looser reading is
what let three flow runs pass over a match under the keyboard.
