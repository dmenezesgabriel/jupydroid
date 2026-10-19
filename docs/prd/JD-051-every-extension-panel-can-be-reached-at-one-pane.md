# JD-051 — Every extension panel can be reached at one pane

Milestone: `M7` · State: in-progress · Depends on: `JD-061` · Closes: —

## Outcome

On a phone, or a tablet held upright, the user opens Source Control, Chat and any later contributed
panel from the bottom bar's More, where today a contributed panel narrows to Files.

## Demo

On the arm64 tablet `SM-X216B` in portrait (`w800dp-h1280dp`, one pane). Source Control reads the
Space's `jupyter_server` and Chat talks to its agent, so the emulator's fake gateway proves nothing here.

1. Cold start: install, launch, enter the base Space. Tap More: the modal rail lists New terminal, then
   Source Control and Chat, then Settings at its foot.
2. Tap Source Control: the rail closes and Source Control fills the pane, with its title bar. None of the
   bar's five items is marked.
3. Tap More again: Source Control's row is the selected one. Tap Chat: Chat fills the pane.
4. Back: Files is showing, marked on the bar.
5. Open Chat again, rotate to landscape: the two-pane rail marks Chat and the panel stays.
6. Kill and relaunch in portrait: More still lists both rows, and each still opens its panel.

Not `shipped` until this has run on the tablet, with screenshots, snapshots and `maestro.log` under
`evidence/<flow-name>/`.

## Who else implements this

The question is: when a bar has more panels than room, where do the rest go, and how is the open one
marked?

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | `jupyterlab@7f0e6906 packages/application/src/shell.ts` (`SideBarHandler`) | The side bar's tabs never overflow into a menu; it has no one-pane form ([ADR-0109](../adr/0109-at-one-pane-the-rail-opens-as-a-modal-from-the-bars-last-item.md)) |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/workbench/browser/parts/compositeBar.ts` (`getOverflowingComposites`), `src/vs/workbench/browser/parts/compositeBarActions.ts` (`CompositeOverflowActivityAction`, "Additional Views") | Containers that do not fit go into a "…" menu; the active one is radio-checked there, and the bar swaps it back into view when it can |
| `vscode-jupyter` | ABSENT | — | Contributes to VS Code's containers; no bar of its own |
| `intellij-community` | ABSENT | — | Tool-window stripes; no one-pane form |
| `termux-app` | PARTIAL (phone) | `termux-app@3df69d1d app/src/main/res/layout/activity_termux.xml` (`left_drawer`) | A navigation drawer holding the sessions and the settings gear |
| `winlator` | PARTIAL (phone) | `winlator@fb66541b app/app/src/main/res/menu/main_menu.xml` | A navigation drawer holding every destination |
| the shipped rootfs | ABSENT | — | Not a UI |

**Count:** 1 of 7 overflows activity entries into a second surface (VS Code); the two phone references
put every destination in a drawer, which ADR-0109 rejected for the modal rail. ADR-0109 already decided
the surface; this item fills it.

## Implementation inventory

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Which entries overflow | `src/vs/workbench/browser/parts/compositeBar.ts` (`updateCompositeSwitcher`, `getOverflowingComposites`) |
| The overflow menu | `src/vs/workbench/browser/parts/compositeBarActions.ts` (`CompositeOverflowActivityActionViewItem.getActions`: `action.checked = getActiveCompositeId() === action.id`, `getCheckedActionsRepresentation: 'radio'`) |
| Edge case | the active container is re-added to the visible bar, popping the one before it ("We always try show the active composite") |

**The disagreement with us:** VS Code's bar resizes, so the active entry can always be swapped into it.
Ours is fixed at five ([ADR-0109](../adr/0109-at-one-pane-the-rail-opens-as-a-modal-from-the-bars-last-item.md)),
so the active contributed panel stays in the modal rail and is marked there, as VS Code marks it in its menu.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the rows go | VS Code → overflow in bar order; ADR-0109 → between New terminal and Settings | — | After New terminal, before Settings, in `railOrder`'s contributed order: Settings stays at the foot as on the two-pane rail |
| What is marked | VS Code → radio check in the menu; swap into the bar | The bar cannot grow | The showing panel's row is selected; none of the bar's five items is (decided with the user) |
| The one-pane fallback | ADR-0105 → contributed narrows to Files | Now reachable, so no reason to narrow | `showingAtOnePane` keeps a contributed panel; the table of contents, the debugger and `null` still narrow to Files |
| Back from a contributed panel | the existing `backSelectsFiles` rule | — | Files, as from Search or Sessions and Tabs |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The surface | `app/src/main/java/com/jupydroid/app/ui/shell/MoreRail.kt`, New terminal and Settings only | `vscode@3f3380c6 compositeBarActions.ts` | a row per contribution |
| Drawing the panel at one pane | `app/src/main/java/com/jupydroid/app/ShellSidePane.kt` draws `ContributedSidePanel` whenever shown | — | none |
| Keeping it shown | `app/src/main/java/com/jupydroid/app/ShownPanel.kt` `showingAtOnePane` narrows to Files | — | keep a contributed panel |
| Marking it | `app/src/main/java/com/jupydroid/app/ui/shell/SidePanelBar.kt` `selected: SidePanel` | radio check | `ShownPanel`, and a selected row |

Ours is **present but unreachable**: the panel draws at one pane, and nothing gets there.

## Deviations

The active panel is not swapped into the bar, as VS Code's is; the bar's five are fixed (ADR-0109).

## Open decisions

**One string on screen twice — measured in T1, and it is not.** With Source Control showing and More
open over it, Compose's lookup, which walks every root, finds two nodes named "Source Control": the
panel's title in the main window and the row in the rail's dialog. On `SM-X216B` in portrait the title
sits at x 24–246 px, under the rail's 330 px (220dp), so it cannot be seen, and `uiautomator dump`
reports one node, the row at `[102,220][259,254]`. What a flow and the eye address stays unique
([ADR-0055](../adr/0055-the-bottom-bar-is-drawn-wherever-the-shell-draws-one-pane.md)), so nothing changes.
A Compose test that reads names with the rail open has to scope its lookup to the rail.

## Surface

| Layer | Files |
|---|---|
| `domain/*`, `data/*`, `di/AppContainer` | none — `sidePanelContributions` exists |
| shell state | `ShownPanel.kt` (`showingAtOnePane`), `Navigation.kt` |
| `ui/shell` | `MoreRail.kt`, `SidePanelBar.kt`, and `ShellSidePane.kt` wiring |
| unit tests | `ShownPanelTest.kt` |
| instrumented tests | `SidePanelBarTest.kt`, `SidePaneNavigationTest.kt` |
| `.maestro/` flow + `scripts/` driver | a one-pane flow opening each contributed panel from More (T2) |

## Tasks

- [x] T1 — One pane: More lists every contributed panel between New terminal and Settings; picking one
  shows it and closes the rail; its row is selected and no bar item is; it no longer narrows to Files,
  and Back from it selects Files. The open decision, measured: no change.
- [x] T2 — The Maestro flow on `SM-X216B` in portrait, with evidence.
- [ ] T3 — The demo; ADR-0105's one-pane line marked superseded in part by ADR-0109; delete this PRD;
  ship.

## Out of scope

A badge on More, and reordering or pinning panels (VS Code's context menu). The symbol row (`JD-052`).
