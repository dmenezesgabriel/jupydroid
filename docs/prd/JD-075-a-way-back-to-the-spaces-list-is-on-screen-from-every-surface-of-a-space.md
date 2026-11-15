# JD-075 — A way back to the Spaces list is on screen from every surface of a Space

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-193`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

From the browser, a notebook, a file, a terminal, Settings or the Launcher, one visible, named control returns to
the Spaces list, the same on a phone and a tablet. Today the route exists only as the end of an unwinding.

## Demo

No cell runs, so `dev36` serves the phone's width and the instrumented test; the tablet (`SM-X216B`) serves both
of its widths.

1. Cold start, enter a Space, open a notebook, a file, a terminal and Settings. From each surface, say what a person
   sees that leaves for the Spaces list and count the taps. Today: the one-pane arrow reads "Back to Spaces" only
   when the home lies directly beneath the top entry; with documents open it goes to the previous tab first, and the
   two-pane rail names nothing.
2. After: the same named control on every surface at one pane and at two, one tap each.
3. With a dirty document open, use it: the save-before-close ask appears rather than the edit being dropped.
4. Enter the Space again from the list: it opens as a cold entry, as it does today after the system Back.

The item is not `shipped` until this has run on both devices and the frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode@3f3380c6` | FULL | `src/vs/workbench/browser/actions/workspaceActions.ts:162-175` (`workbench.action.closeFolder`, "Close Workspace"), `:415-423` (File menu, group `6_close`) | one named command, in the File menu and the palette; no persistent control |
| `intellij-community@406a27a8` | FULL | `platform/platform-resources/src/idea/PlatformActions.xml:512` (`CloseProject`) | the same: File menu |
| `jupyterlab@7f0e6906` | PARTIAL | `packages/hub-extension/src/index.ts:86` ("Hub Control Panel"), `:100` ("Log Out"), `:117-118` (palette) | leaves for the Hub's own page, only when running under a Hub; named, in the palette |
| `vscode-jupyter`, `termux-app`, `jediterm`, `winlator`, the shipped rootfs | not read | — | none has a workspace to leave; an extension, a terminal or a runtime |

**Count:** 3 of 7 read, and every one names the act and puts it in one fixed place (a menu or the palette). None
uses a back-stack route for it. A phone has no menu bar, which is the deviation below.

## Implementation inventory

### Ours

| Concern | Files |
|---|---|
| The route that exists | `app/src/main/java/com/jupydroid/app/SpacesHome.kt` (`leaveWorkspace`, `popWorkspaceEntry`); its only caller outside tests is the system Back |
| The root of the stack | `Navigation.kt` (`rememberNavBackStack(SpacesHome)`, `onBack`) |
| The one-pane arrow | `ui/shell/MainAreaTabStrip.kt` (`upDestinationLabel`) |
| Rails | `ui/shell/SidePanelRail.kt` (two panes), `ui/shell/MoreRail.kt` (one pane), `ui/shell/SidePanelBar.kt` |
| Where the bar is drawn | `ShellSidePane.kt`: on the browser entry only, so a document has no bar ([ADR-0055](../adr/0055-the-bottom-bar-is-drawn-wherever-the-shell-draws-one-pane.md)) |
| The dirty gate | `ui/shell/MainAreaCloseGate.kt` ([ADR-0103](../adr/0103-a-tabs-menu-closes-its-siblings.md)) |
| Tests that name today's label | `app/src/androidTest/java/com/jupydroid/app/ui/shell/MainAreaTabStripTest.kt`, `app/src/test/java/com/jupydroid/app/SpacesHomeBackStackTest.kt` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the entry lives | a row in both rails · a persistent control in the strip · the Up arrow always going to Spaces · an overflow action on the strip | the rails alone leave a document at one pane with none, because the bar is the browser's; a strip control costs width the strip's height budget ([ADR-0142](../adr/0142-the-tab-strips-controls-keep-the-strips-height.md)) already spends; making Up mean Spaces breaks Up as "the previous tab" | **Recommendation, to be settled on T1's measurement:** one control named "Spaces" in the strip at one pane and at the rail's foot beside Settings at two. If the strip's free width at 320dp cannot hold a 48dp target, an overflow action |
| What leaving does to open documents | drop them · ask first | `leaveWorkspace` retains only the home; the dirty gate lives in the close path | ask first, through the gate the tabs already use |

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| A named act of leaving | `leaveWorkspace` in `SpacesHome.kt` | `workspaceActions.ts:162-175` | present but unreachable from any control |
| Visible on every surface | the one-pane arrow, conditional on what lies beneath | `workspaceActions.ts:415-423` (always in File) | present and partial |
| Dirty documents on leaving | not read | — | **unknown**; T1 reads what system Back from the browser does to a dirty document today |

## Deviations

A phone has no File menu, so the named act becomes a visible control rather than a menu item. It becomes an ADR
before this file is deleted.

## Open decisions

- Whether leaving stops the Space's server or leaves it running. Not read; T1 reads what the list shows for an
  entered Space after Back, and the decision follows that evidence.
- The control's placement at one pane, per the trade-off table.

## Surface

A UI item with no domain, port or adapter.

| Layer | Files |
|---|---|
| `ui/shell` | the files under "Ours" above |
| unit tests | `SpacesHomeBackStackTest`, plus a test of the control's action |
| instrumented tests | `MainAreaTabStripTest` and one test per rail, on `dev36` |
| `.maestro/` flow + `scripts/` driver | a flow that leaves from each surface, on both devices |

## Tasks

- [ ] T1 — read what leaving does today (dirty documents, the Space's server), measure the strip's free width at 320,
  360 and 412dp, and settle the placement in this file.
- [ ] T2 — red tests that each surface exposes the control and that leaving runs the dirty gate, then the minimal UI.
- [ ] T3 — run the flow on both devices at both widths, look at the frames, and record the ADR.

## Out of scope

Switching Space in place (the workspace never does: `enterWorkspace`), what the Up arrow does for the previous tab,
and the Space's server lifecycle beyond what T1 finds.
