# ADR-0055 — The bottom bar is drawn wherever the shell draws one pane

Status: accepted
Date: 2026-08-27

## Context

**This reverses an earlier decision of this project's**, which was that one pane grows no navigation bar.
That decision's reasoning was sound and is worth keeping: two destinations is below Material's own floor —
Android's guidance for `NavigationBar` is *"three to five destinations of equal importance"* — and at one
pane only one of this app's two panels is a destination at all. Files is a screen you navigate to; the table
of contents is a surface over the document you are already in.

What overturned it was a **measured gap**, not a change of taste. A later plan row asked for a bottom
navigation *"present below 600dp and absent at and above it"*, which is Material's own breakpoint. That is
**not this app's rail rule**: the rail is drawn at `maxHorizontalPartitions > 1`, which is 840dp — so the
row as written left **600–840dp with neither control**, and that band is this project's own tablet in
portrait, measured at `w800dp`, where workspace search would have been unreachable with no overlay to fall
back to.

The sharper rule had already been recorded and the row had not caught up with it: *the problem is not "the
compact window" but every window the shell draws as one pane.*

## Decision drivers

- No width may have neither control.
- Every visible string must be unique, or a flow and a screen reader cannot address it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| No bar at one pane | Two destinations is below Material's floor | Leaves 600–840dp with no way to reach the panels at all |
| Bar below 600dp, rail at 840dp+ | Material's own breakpoint | **600–840dp gets neither** — which is this project's tablet in portrait |
| Bar at `!twoPane`, rail at `twoPane` | No width with neither; consistent with how the rail has always been decided here | Departs from Material's bar/rail width split |

## Decision

**The bar is `!twoPane` and the rail is `twoPane`, so there is no width with neither.**

**What that departs from is Material's bar/rail split, and it is a departure this app already made.**
`NavigationSuiteScaffold`'s documented default is a bar at compact and a rail at everything else; this app
has drawn its rail on **pane count** rather than width class ever since the rail became the activity bar
*beside the side area*, and a side area is what two panes means. Keeping the bar on the same rule is the
consistent half of that choice, not a second exception.

**The bar is drawn over the panel and never over a document**, and **it does not collapse where the rail
does** — picking the showing panel on the rail collapses the side area, and there is no side area at one
pane to collapse.

**Its items carry no visible text, and two strings measured that.** `ExplorerScreen` titles itself
**"Files"** and `SearchSidePanel` titles itself **"Search"**. A labelled item sits directly under one of
those, so each string would be on screen **twice** at one pane: `onNodeWithText("Files")` throws on two
matches and Maestro's `tapOn` picks one silently. Keeping the names in semantics keeps every visible string
unique — and it is what makes the flow's own addressing work, since on the browser only the bar says
"Search" and on the search panel only the bar says "Files". VS Code agrees: its activity bar is icon-only in
`DEFAULT`, `TOP` **and** `BOTTOM`.

**The arrangement is the reference's own sentence, and the tablet is what asked for it.** One arrangement at
both one-pane widths was measured wrong on the tablet in portrait: each of two items was **396dp** wide with
centres **404dp** apart — a 24dp icon inside a tap target half the window across, with the two controls
stranded at the quarter points. `ShortNavigationBar`'s own KDoc states the rule: *"In small screens … its
arrangement should be `EqualWeight`"*; *"In medium screens … `Centered`, so that the navigation items are
distributed grouped on the center of the bar"*. `itemArrangementAt` is that, keyed on
`WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND`, so the width branch is the reference's rather than an
adaptation of it. `ShortNavigationBar` is stable, so this cost no experimental opt-in, and at 64dp against
`NavigationBar`'s 80dp it gave the panel 16dp back at both widths.

**Back reaches the start destination before it leaves the app.**

## Consequences

The two-destination objection stands and is accepted knowingly: this bar is below Material's recommended
floor, and the alternative was a width band with no way to reach either panel.

**Every visible string in the shell must stay unique.** That is not a style rule here — it is what makes
both the accessibility tree and the flow addressable, and it has already cost this project once.

The thing a future reader will be tempted to "fix" is putting labels on the bar's items, which Material
recommends. Each label duplicates a screen title that is on screen at the same time.
