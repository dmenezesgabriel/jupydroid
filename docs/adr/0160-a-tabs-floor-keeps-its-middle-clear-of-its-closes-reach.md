# ADR-0160 — A tab's floor keeps its middle clear of its close's reach

Status: accepted; amends [ADR-0142](0142-the-tab-strips-controls-keep-the-strips-height.md)'s reach for a minimum-width tab
Date: 2026-11-18

## Context

[ADR-0142](0142-the-tab-strips-controls-keep-the-strips-height.md) states what a press on a tab's close
reaches: 40dp across, from 12dp into the label to the tab's right edge. Its 24dp slot sits 4dp in from that
edge and Compose widens the slot's press area by 12dp on each side. The reach therefore starts `w - 40`dp from
a tab's left edge, and a tab's middle is `w / 2`. The middle is the close's while `w / 2 >= w - 40`, that is
`w <= 80`.

A tab's width is `maxWidth / tabs.size`, floored at `TAB_MIN_WIDTH` (`MainAreaTabStrip.kt`), and the floor was
80dp. JupyterLab sets no floor on a horizontal tab (`min-width: 0`, `jupyterlab@7f0e690
packages/application/style/tabs.css:53`) and VS Code's 80px applies only where its close shows on the left
(`vscode@3f3380c src/vs/workbench/browser/parts/editor/media/multieditortabscontrol.css:289`), so 80dp was our
number and it sat exactly on the boundary. It is reached with four tabs at 320dp and five at 360dp. Read on
`dev36` at 320x640: a press at x=40 of an 80dp tab closed it, at x=39 its menu opened. Under the 80dp floor
a tap on the tab's middle closed it and left another one active, and a long press on the middle opened no menu
(`TabMiddlePressTest`, 4 of 4 red).

## Options considered

| Option | Pro | Con |
|---|---|---|
| **Raise the floor so the middle clears the reach by a margin** | ADR-0142's reach, its slot and `TabCloseTouchAreaTest` stand; the label gains room | A tab at the floor is wider, so one strip scrolls sooner |
| Narrow the close's reach with a smaller minimum touch target around it | The floor stays 80dp | Takes the reach under what ADR-0142 states and supersedes it; the close becomes harder to hit on a phone |

## Decision

**The floor is derived from the reach, not picked: `2 x (reach + margin)`.** The reach is 40dp and the margin
is a touch slop of 8dp, so the floor is `2 x (40 + 8)` = **96dp** and a tab's middle sits 8dp clear of the
close's reach at the narrowest width a tab gets. The slot, the row and ADR-0142's reach are unchanged.

`TabMiddlePressTest` presses the middle of an inactive tab at the floor, by tap and by long press, with four
tabs at 320dp and five at 360dp. A change to the slot, to the 12dp Compose widens it by or to the margin moves
the floor, and `TAB_MIN_WIDTH`'s comment states the arithmetic that has to move with it.

## Consequences

- `scripts/tab-middle-press-driver.sh` runs the demo on `dev36` at 320dp and at a 360dp `wm size` override and
  `scripts/read_tab_close_reach.py` reads each whole tab off the dump. At the 96dp floor a tab reads 96dp, its
  close's reach starts 56dp in, its middle 48dp, the margin 8dp. At the 80dp floor, built locally for the
  comparison, it read 80dp, 40dp, 40dp and a margin of 0dp.
- A tab at the floor gives its label 16dp more than before, never less. A strip of 96dp tabs scrolls one tab
  sooner than a strip of 80dp ones.
- The thing a future reader will be tempted to "fix" is the close, not the floor: ADR-0142 shows a larger
  slot does not move the reach, and a smaller reach supersedes that ADR.
- Not measured: a tap at the same point on a device, as against the injected input the emulator takes.
