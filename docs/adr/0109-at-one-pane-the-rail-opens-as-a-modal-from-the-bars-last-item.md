# ADR-0109 — At one pane the rail opens as a modal from the bar's last item, and Settings sits at its foot

Status: accepted
Date: 2026-10-19

## Context

Beside two panes, Settings is the gear at the foot of `SidePanelRail`, below every panel and the `+`.
That is where VS Code puts Manage (`vscode@3f3380c6 src/vs/workbench/browser/parts/globalCompositeBar.ts`,
`GLOBAL_ACTIVITY_ID`). At one pane there is no rail. Settings is the Files bar's gear, so from Search or
Sessions and Tabs it is reached only by going back to Files first. On SM-X216B in portrait
(`w800dp-h1280dp`), the window's hierarchy has one node named `Settings` on Files and **none** on
Search or Sessions and Tabs.

The bottom bar is full. Measured on the same tablet, it draws **five** 64dp-tall items, each **128dp**
wide, across x 80–720dp of 800: Files, Sessions and Tabs, Search, New Launcher and New terminal.
`ShortNavigationBar` gives its items `10 × (n + 3)` % of the bar under `Centered`, and **stops
centring above six** (`calculateCenteredContentHorizontalPadding`,
`androidx@cdb1a6a2 compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ShortNavigationBar.kt`).
Under `EqualWeight` it splits the width evenly.

| Items | Each at 800dp, `Centered` | Each at 360dp, `EqualWeight` |
|---|---|---|
| 5 (today) | 128dp | 72dp |
| 6 (+ Settings) | 120dp | 60dp |
| 8 (+ Settings, Source Control, Chat) | 100dp, no longer centred | **45dp**, below the 48dp touch target |

This answer also has to hold the extension panels, which have no one-pane home yet: a contributed
panel narrows to Files there ([ADR-0105](0105-an-extension-contributes-a-side-panel-the-host-frames.md),
which rejected a sixth bar item for the same ceiling).

## Decision drivers

- Settings is reachable from every panel the bar can show.
- Material's own ceiling: a navigation bar carries *"three to five destinations of equal importance"*
  (`developer.android.com/develop/ui/compose/components/navigation-bar`).
- One home for every destination that does not fit on the bar, so extension panels need no second
  decision.
- Every visible string stays unique ([ADR-0055](0055-the-bottom-bar-is-drawn-wherever-the-shell-draws-one-pane.md)),
  and each screen has one `Settings` node, as beside two panes.
- No gesture that competes with the system's Back edge or the terminal's own touch handling.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A sixth bar item, the gear | One tap; 120dp and 60dp still fit | Over Material's five. Every later panel needs another item, and the 45dp row above is where that ends |
| A gear on each panel's own bar | Room is there: Search's bar is empty, and Sessions holds only Refresh at x 748–796dp | One command drawn in every panel's frame, and no home for an extension panel |
| A modal navigation drawer, as `termux-app@3df69d1d` and `winlator@fb66541b` | Both phone references; holds any number of rows | *"The navigation drawer is being deprecated in the Material 3 expressive update … use an expanded navigation rail"* (`material-components-android@fca1418b docs/components/NavigationDrawer.md`). Termux's edge swipe is the gesture that competes with Back |
| **A modal expanded rail, opened from the bar's last item, which New terminal gives up** | Material's named replacement for the drawer, stable in material3 1.4.0 (`ModalWideNavigationRail`, release-note change I62998 graduated it with `ShortNavigationBar`). It is the two-pane rail shown modally, so Settings sits at its foot in both layouts. The bar stays at five | New terminal takes two taps at one pane instead of one |

## Decision

**At one pane, the bar's fifth item is "More", which opens a `ModalWideNavigationRail` holding what the
bar does not.** That means New terminal now, every extension panel when the extension work reaches
one pane, and Settings at its foot, where the two-pane rail has it. Closing it, by Back or by the
scrim, leaves the panel where it was.

**The modal rail does not repeat the bar's panels.** A row named Files over a bar item named Files
would put one string on screen twice, which ADR-0055 rules out. Each destination at one pane is on
exactly one of the two surfaces.

**The Files bar drops its gear at one pane too**, so there is one `Settings` node at either width. Every
flow's `tapOn: "Settings"` then reaches the rail's foot: on the rail beside two panes, inside the modal
rail at one.

**New terminal is what moves**, at the user's call. It is the bar's own addition rather than a reach
point either reference draws (`SidePanelBar`'s KDoc). It is also on the Launcher's card behind the `+`,
so it keeps a second reach. It keeps its name, so `tapOn: "New terminal"` still finds it once the rail
is open.

**The rail opens from a tap only.** `ModalWideNavigationRail` has no edge-swipe to open it, so it does
not compete with the system Back edge on a phone with gesture navigation. SM-X216B uses three-button
navigation, so that conflict was not exercised there.

## Consequences

- One-pane flows that tap the bar's "New terminal" tap "More" first. Beside two panes,
  "New terminal" is the Launcher's card and is unchanged.
- The extension work reuses this: a contributed panel at one pane is a row in the modal rail, and the
  fallback to Files in `showingAtOnePane` narrows to panels nobody can reach.
- The bar's five items are the permanent ceiling. Anything new at one pane goes in the modal rail.

The thing a future reader will be tempted to "fix" is New terminal's extra tap, by putting it back on
the bar as a sixth item. The table above is where a sixth item leads once the extension panels arrive.
