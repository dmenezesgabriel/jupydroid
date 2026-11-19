# ADR-0142 — The tab strip's controls keep the strip's height, and a press reaches what the strip leaves them

Status: accepted; the floor of a minimum-width tab is amended by [ADR-0160](0160-a-tabs-floor-keeps-its-middle-clear-of-its-closes-reach.md)
Date: 2026-11-09

## Context

[ADR-0048](0048-touch-rules-measured-on-device.md) sets 48dp as the minimum touch target and says what its
guard exists for: "a control built from a bare `clickable`, which would have a real 24dp target and nothing
else to catch it". [ADR-0093](0093-a-document-is-saved-on-demand-from-the-tab-strip.md) and
[ADR-0096](0096-an-open-document-is-reloaded-from-disk-on-demand-from-the-tab-strip.md) keep Reload and Save
in the strip's own height "rather than an `IconButton`'s 48dp". No ADR states the size of a tab or of its
close button, and the source says "at a touchable size".

A tab row is declared 32dp (`TAB_HEIGHT`) under a 2dp active edge, and its close is a bare `clickable` in a
24dp slot (`TAB_CLOSE_TOUCH`), both in `app/src/main/java/com/jupydroid/app/ui/shell/`. Three readings of the
same close disagree, each of them right about what it reads:

| Reading | The close | The tab row |
|---|---|---|
| Declared | 24 x 24dp | 32dp tall |
| `touchBoundsInRoot`, in a Compose test | 48 x 48dp | 48dp tall (a 107dp tab: 107 x 48) |
| `uiautomator dump`, tablet at two panes, density 240 | 40 x 40dp | clickable node 191.3 x 40dp |
| `uiautomator dump`, phone at one pane | 40 x 48dp | not read |

The dump's numbers are the 48dp area Compose puts around a clickable, cut where an ancestor ends. The close's
48dp area is centred on its slot, 12dp past the slot on each side. The slot sits 4dp in from the tab's right
edge, so the area is cut there and is 40dp wide. Vertically the area runs 8dp above and 8dp below the 32dp
row; at two panes the strip ends at the row's bottom edge and the dump is cut there, so 8 + 32 = 40dp. At one
pane the strip's row holds the Up arrow's `IconButton` whenever a tab exists
(`!twoPane && backStack.size > 1`, `MainAreaTabStrip.kt`), which is 48dp tall, so the row is centred in it and
the whole 48dp fits. The tab row's own clickable node reads the same way, 8dp above the row and cut at the
strip's bottom.

The dump therefore says nothing of what a press reaches, so a press was swept over the close at 2dp steps and
the result read, on `dev36` with a clickable document under the strip and the strip's neighbours in place.
Offsets are from the slot's top-left corner, in dp; the row runs from -4 to 28 vertically.

- **Across:** the close takes a press from -12 to 28, so 12dp into the label and up to the tab's right edge.
  At -14 the tab's own row takes it. At 28 and beyond a neighbour takes it, Reload in the sweep.
- **Down at two panes:** the close takes presses from -12 to 27, and from 28, under the strip, every press
  goes to the document. The 8dp the widening adds below the row is the document's.
- **Down at one pane:** the close takes presses to 36 and the document takes none, since the 48dp area lies
  inside the strip.

Compose says the cause itself: a widened area yields to a neighbour's direct hit (`NodeCoordinator`'s
`outOfBoundsHit`, quoted in `app/src/androidTest/java/com/jupydroid/app/ui/MinimumTouchTarget.kt`). ADR-0048's
premise that a bare `clickable` has a "real 24dp target" is wrong for the same reason, and that file's
docstring already says so.

## Decision drivers

- A number the document states is one a reader can trust: the declared size, the reported size and the reach
  are three numbers and the ADR names each.
- The strip's 32dp was chosen against the references and shared by every control in it, Reload and Save
  included. A control taller than the strip makes the strip taller.
- WCAG 2.2's minimum, 24 by 24 CSS pixels (<https://www.w3.org/TR/WCAG22/#target-size-minimum>), is a floor a
  tab's close can be held to when Material's 48dp cannot be reserved.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep the 24dp slot and state its reach | Nothing to build; the reach is already 40dp wide and 32dp tall at two panes | Under Material's 48dp at two panes, and the 8dp below the row is the document's |
| Make the slot as tall as the row, 32 x 32dp | Reserves the height the close reaches | Buys nothing: the reach inside the row is already 32dp tall and the width is cut by the tab's edge. Costs the label 8dp where a tab is squeezed to the 96dp floor |
| Make the strip 48dp tall at two panes | Reserves Material's size for every control in it | 14dp less document on every two-pane screen, and it reverses ADR-0093 and ADR-0096 |

## Decision

**The strip's controls reserve what the strip's height gives them, not 48dp.** A tab row is 32dp under a 2dp
edge at two panes, and the strip is the 48dp row of the Up arrow at one pane. The close keeps its 24dp slot.
Reload and Save stay as ADR-0093 and ADR-0096 left them.

**What a press reaches is what is stated, not what is declared or what a dump reads.** At two panes the close
takes a press over 40dp across, from 12dp into the label to the tab's right edge, and 32dp down, the row. At
one pane it is 40 x 48dp. Both clear WCAG 2.2's 24 by 24. Neither reaches Material's 48 across, and at two
panes neither reaches 48 down, which the strip's 32dp row does not allow.

`TabCloseTouchAreaTest` states the reach: a press 10dp into the label closes, 16dp does not, a press 30dp
right of the slot's left edge, which is past the tab's right edge, does not, a press under the two-pane strip goes to the document and the same press at one
pane closes. A change to the slot, the row or the strip's height that moves the reach fails it and sends the
reader here.

## Consequences

ADR-0048's sentence about a bare `clickable`'s "real 24dp target" is corrected by this one, and its status
line says so. What it guards stays true: a control reserves its space in the layout, and
`assertReservesMinimumTouchTarget` reads that.

A dump of the strip reads 40 x 40dp for a 24dp slot and that is right. `scripts/read_touch_targets.py` reads
the widened area cut by ancestors, so a bound under 48dp is a confirmed shortfall of that area and one at 48dp
or more says nothing of the layout. The reader's docstring says it.

Not measured here: the 8dp the close's area adds above the row at two panes, which is the status bar's side of
the strip, and a press on the document's own control at the strip's bottom edge, which a clickable document
stood in for.

The thing a future reader will be tempted to "fix" is the close's slot. Growing it to 32dp or 48dp does not
move its reach, which is cut by the tab's right edge and the row's height. A larger reach is a taller strip,
which is a decision about every control in it and supersedes this one.
