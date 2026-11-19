# ADR-0154 — The bottom bar's centring is checked to the pixel the items are laid out in

Status: accepted
Date: 2026-11-15

## Context

`SidePanelBarTest.theItemsGroupInTheMiddleOnAMediumScreen` failed on `dev36` with `expected:<400.0> but was:<396.6667>`:
at the 800dp one-pane window the group of five items, read from Files' left edge to More's right edge, had its centre
3.33dp left of the window's, and the test allowed 2dp. The same failure reproduced with the author's changes stashed,
so no change under test caused it.

The five laid-out boxes were read on `dev36` (320x640 at 160dpi, so `ForcedSize(800dp x 1280dp)` is 0.45 px per dp, 360px
across), merged and unmerged semantics alike: Files 80.0 to 206.667dp, then four more of the same 126.667dp width, More
ending at 713.333dp. There was 80.0dp (36px) before Files and 86.667dp (39px) after More. Material's `Centered`
arrangement reserves 10% of the bar a side for five items and shares the remaining 288px: 288 / 5 = 57.6px, laid out as
57px, so five items take 285px and the 3px left over land after the last one. The group's centre is 1.5px (3.33dp) left
of the bar's, to the digit the test printed. One pixel is 2.22dp there, so a 2dp tolerance was smaller than the
resolution the layout can express.

At the tablet's own 1.5 px per dp the rule gives 120px a side and 192px an item with no remainder. Read in portrait
(one pane, 1200px across): Files `[120,1752][312,1848]`, More `[888,1752][1080,1848]`, so the group's centre is 600px,
which is 400dp exactly.

| Reference | Draws a bottom bar | How the group is centred |
|---|---|---|
| `vscode@3f3380c6` | yes, `ActivityBarPosition.BOTTOM` (`src/vs/workbench/services/layout/browser/layoutService.ts:105-110`) | a flex row with `justify-content: center`, by the browser (`src/vs/workbench/browser/parts/media/paneCompositePart.css:60-63`) |
| `jupyterlab@7f0e6906` | yes, `'side' \| 'top' \| 'bottom'` (`packages/application/src/shell.ts:172`) | not centred: a `lm-TabBar` that stretches its tabs across the width (`packages/application/style/sidepanel.css:285-298`) |
| `intellij-community`, `vscode-jupyter`, `termux-app`, `winlator`, the shipped rootfs | no | none lays out a bottom activity bar |

Two of seven references draw a bottom bar and neither computes a centred group's padding, so the arrangement is a
library behaviour (`ShortNavigationBarArrangement.Centered`), not something a reference answers.

## Decision drivers

- A test that fails on a correct layout teaches the next reader to loosen it or to move the bar.
- A layout change on a medium window touches every medium window; a measure change touches one assertion.
- The tolerance must still fail a group moved by a whole item.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Move the bar: pad one side by the leftover pixels | The number on `dev36` is 0 | Code in the product for a density only the forced test has; Material's rule is kept everywhere else |
| Widen the fixed tolerance to 4dp | One constant | Wrong at the tablet's density, where 4dp is 6px and hides a real offset; right at none |
| **State the tolerance in pixels of the density the node was laid out at** | Exact at every density; a moved item still fails | The test reads the density off the Files node |

## Decision

**The bar is not changed.** `SidePanelBar.kt` keeps Material's `Centered` arrangement and its 10% a side for five
items, which is the library's rule and where a flex row would instead size to its content.

**The test's measure is.** `centreSlackDp(itemCount, dpPerPixel)` allows `(itemCount - 1) / 2` pixels, the most five
whole-pixel floors can leave over after the last item, and the density is read off the Files node as its width in dp
over its width in pixels (`ui/shell/PixelRoundingSlack.kt`). At `dev36`'s 2.22 dp per pixel five items accept 4.44dp;
at the tablet's 0.667 dp per pixel they accept 1.33dp.

## Consequences

`PixelRoundingSlackTest` pins that five items accept the 3.33dp measured at `dev36`, that the tablet's slack is two
pixels of its own density, and that both are well under half an item, so a group moved by a whole item still fails.
`theItemsGroupInTheMiddleOnAMediumScreen` passes on `dev36`.

The cause was found by reading the five boxes before touching anything. The first guess, that an item was wider than
the others, was refuted by the equal widths, and the second, that the semantics node differs from the item's box, by
the identical merged and unmerged bounds.

Anyone tempted to widen the fixed tolerance again should convert it to pixels first: 2dp is under one pixel on a
forced 360px window, where one pixel is 2.22dp, and 3 pixels on the tablet, where one is 0.667dp.
