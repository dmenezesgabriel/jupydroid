# JD-088 — The bottom bar's items are centred on a medium window

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-204`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

At the 800dp one-pane window, the five bottom-bar items sit centred: the group read from Files' left edge to More's
right edge has its centre within the test's 2dp of the window's. Today it is 3.33dp left of it, and nobody has said
which item or inset moves it.

## Demo

No cell runs, so `dev36` serves the instrumented test. The frame of the bar at 800dp is read on the tablet
(`SM-X216B`) in portrait, which is one pane at that width.

1. Cold start, open a Space, so the bottom bar is on screen. Take a screenshot and a UI dump.
2. Read the five items' bounds and the bar's own bounds from the dump, in dp. Say what the frame shows and where the
   group's centre falls against 400dp (today: 396.67).
3. Run `SidePanelBarTest.theItemsGroupInTheMiddleOnAMediumScreen` on `dev36`: red today with
   `and the group is centred expected:<400.0> but was:<396.6667>`, green after.
4. Repeat 1-2 after the repair; the five bounds are read from the same dump, not from the test.

Not `shipped` until this has run on both devices and the frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode@3f3380c6` | PARTIAL | `src/vs/workbench/services/layout/browser/layoutService.ts:105-110` (`ActivityBarPosition.BOTTOM`), `src/vs/workbench/browser/parts/media/paneCompositePart.css:60-63` | a bottom bar exists; its items are a flex row with `justify-content: center`, so the group is centred by the browser, not computed |
| `jupyterlab@7f0e6906` | PARTIAL | `packages/application/src/shell.ts:172` (`'side' \| 'top' \| 'bottom'`), `packages/application/style/sidepanel.css:69-76`, `:285-298` | a horizontal `lm-TabBar` that stretches its tabs across the full width (`align-items: stretch`); no centred group |
| `intellij-community`, `vscode-jupyter`, `termux-app`, `winlator`, the shipped rootfs | ABSENT | — | a tool-window stripe, a notebook UI, a terminal, a runtime, an image: none lays out a bottom activity bar |

**Count:** 2 of 7 draw a bottom bar and neither computes a centred group's padding: one centres by a flex rule, the
other stretches. The arrangement under test is Material3's `ShortNavigationBarArrangement.Centered`
(`calculateCenteredContentHorizontalPadding`), a library behaviour that is not one of the seven references.

`SidePanelBar.kt`'s KDoc says JupyterLab "answers nothing here". `shell.ts:172` and `sidepanel.css:285-298` show it has
a `bottom` position, so that sentence is wrong. It is a comment, not this item's defect, and T3 corrects it where the
KDoc is touched anyway.

## Implementation inventory

### Ours (`app/src/`)

| Concern | Files |
|---|---|
| The bar and its arrangement | `main/java/com/jupydroid/app/ui/shell/SidePanelBar.kt`: `itemArrangementAt` picks `Centered` from 600dp up; five `ShortNavigationBarItem`s, each with a `semantics { contentDescription }` modifier |
| The failing test | `androidTest/java/com/jupydroid/app/ui/shell/SidePanelBarTest.kt`: `theItemsGroupInTheMiddleOnAMediumScreen`, `showAt` (`ForcedSize` 800 x 1280), `centreGapFraction`, `TOLERANCE_FRACTION` |
| What the test reads | `getUnclippedBoundsInRoot` of the node carrying the `contentDescription`, which is the node the item's modifier lands on, not necessarily the box the arrangement laid out |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Whose number is wrong | the bar's layout · the test's measure | a layout change touches every medium window; a measure change touches one assertion and hides a real offset if one exists | **Settled on T1's bounds**, not on a guess: if the five laid-out boxes are symmetric about 400dp and only the semantics nodes are not, the test is wrong; if the boxes themselves are off, the bar is. **T1's bounds: the boxes are symmetric to within a pixel, so the test's measure is what changes** |

## Gap analysis

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| A medium window's group is centred | 3.33dp left at 800dp under `ForcedSize` on `dev36`; 0dp at 800dp on the tablet | `paneCompositePart.css:60-63` | present; the bar is centred, and the test's measure cannot resolve it (T1) |

What is known: the failure reproduces at `7df2c58e` with the author's changes stashed, and again at `8b13d163` (T1).
What is not: the commit that first made it fail, because the test was not run at an earlier one.

### T1's measurement

`SidePanelBarTest.theItemsGroupInTheMiddleOnAMediumScreen` on `dev36` (`am instrument`, run from a job that built
both APKs): `and the group is centred expected:<400.0> but was:<396.6667>`. `dev36` is a 320x640 window at 160dpi, so
`ForcedSize(800dp x 1280dp)` lays the bar out at **0.45 px per dp**: 360px for 800dp, one pixel being 2.22dp. A
temporary probe, since deleted, read each item's merged and unmerged bounds at that size:

| Item | left dp | right dp | width dp |
|---|---|---|---|
| Files | 80.0 | 206.667 | 126.667 |
| Sessions and Tabs | 206.667 | 333.333 | 126.667 |
| Search | 333.333 | 460.0 | 126.667 |
| New Launcher | 460.0 | 586.667 | 126.667 |
| More | 586.667 | 713.333 | 126.667 |

The merged and unmerged bounds were identical for every item, so the semantics node is the item's own box and the
bar is not off by the node. The five widths are equal, so no item is wider than another. The group has 80.0dp (36px,
10% of the bar) before Files and 86.667dp (39px) after More.

**Attribution: whole-pixel rounding of the item width, not the bar.** The centred padding is 36px a side and the
five items share the remaining 288px. 288 / 5 = 57.6px, laid out as 57px (126.667dp), so five items take 285px and
the 3px (6.667dp) left over lands after the last one. The group's centre is therefore 1.5px (3.33dp) left of the
bar's: (80.0 + 713.333) / 2 = 396.667, to the digit the test printed. At the tablet's own 1.5 px per dp the same
rule gives 120px a side and 192px an item, no remainder, and the frame read there agrees: Files `[120,1752][312,1848]`
to More `[888,1752][1080,1848]` in a 1200px window, whose centre is 600px = 400dp exactly.

The offset is at most half of four pixels (an item width can lose up to 4px over five items), so on a real window at
1 px per dp or finer it is under 2dp, which is the test's own tolerance. Under `ForcedSize` on a 360px-wide window one
pixel is already 2.22dp, so the tolerance is smaller than the test's resolution.

## Deviations

Material's `Centered` arrangement reserves `(100 - 10 * (itemsCount + 3)) / 2` percent a side, which is 10% for five
items, where a flex row sizes to its content. That is the library's rule and is kept.

## Open decisions

The one that outlives the item becomes an ADR.

- **Settled by T1:** the 3.33dp is whole-pixel rounding of the item width at `ForcedSize`'s 0.45 px per dp, not an
  inset, not an item's width and not the semantics node. The bar is centred to within a pixel, and the repair is in
  the test's measure.
- Open, for T2: how the measure absorbs the rounding without hiding a real offset. Either the tolerance is stated in
  pixels of the forced density (the group may be off by `(items - 1) / 2` pixels), which needs the forced density
  read inside the composition; or the test runs at a size whose item width is a whole pixel count, which pins the
  test to one density. A red test that names the cause comes first: five items at a width that leaves a remainder
  must be accepted, and a group moved by a whole item must still fail.

## Surface

A UI item with no domain, port or adapter.

| Layer | Files |
|---|---|
| `ui/shell` | `SidePanelBar.kt`: none, T1's bounds show the bar is centred; its KDoc line on JupyterLab is T3's |
| instrumented tests | `SidePanelBarTest`: a test that names the cause, beside the symptom test |
| `.maestro/` flow + `scripts/` driver | none; the frame is read by hand with `adb`, as the Demo says |

## Tasks

- [x] T1 — run the test red on `dev36` and read the five items' bounds and the bar's own; attribute the 3.33dp to an
  item or an inset, and record the attribution in this file.
- [ ] T2 — a red test that names the cause, then the minimal change in the test's measure (T1: the bar is centred;
  the tolerance is under one pixel of the forced density).
- [ ] T3 — read the frame on the tablet and on `dev36`, look at it, record the ADR, and delete this file.

## Out of scope

The small-window `EqualWeight` arrangement, the More rail, and `backClosesTheRailWithoutActing`'s
`RootViewWithoutFocusException` (`has-window-focus=false`, a separate cause which `GAP-182` is about).
