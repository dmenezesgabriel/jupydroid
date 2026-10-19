# JD-061 — Settings is reached from every view

Milestone: `M6` · State: in-progress · Depends on: — · Closes: `GAP-178`

## Outcome

Whatever panel is showing — Files, Sessions and Tabs, Search, Table of contents, Debugger, Source
Control or Chat — and at either width, the user opens Settings without going back through Files.

## Demo

On the arm64 tablet `SM-X216B` (nothing here runs a cell, but landscape gives two panes and portrait
one, so both halves are measured on the one device):

1. Cold start, landscape: enter the base Space. The rail shows a gear at its foot, below every panel's
   icon and the `+`. The Files bar has no gear of its own.
2. Pick Search on the rail, tap the gear: Settings opens in the main area, Search stays on the side.
   Close Settings: Search is still showing.
3. The same from Sessions and Tabs, and from Chat.
4. Rotate to portrait (one pane). From Files, Search and Sessions and Tabs, Settings opens from the place
   T2 decides.
5. Kill and relaunch: the gear is where it was, at both widths.

Evidence under `evidence/<flow-name>/`; not `shipped` until this has run on the tablet.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e6906 packages/mainmenu-extension/schema/plugin.json` (`jp-mainmenu-settings` → `settingeditor:open`), `packages/settingeditor-extension/schema/plugin.json` (`Accel ,`) | A **Settings** menu in the global menu bar, plus a shortcut |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/workbench/common/activity.ts` (`GLOBAL_ACTIVITY_ID = 'workbench.actions.manage'`), `src/vs/workbench/browser/parts/globalCompositeBar.ts` | **Manage**, the gear in the global activity at the foot of the activity bar, below every view's icon |
| `vscode-jupyter` | ABSENT | `vscode-jupyter@f01d80ca src/commands.ts` (calls `workbench.action.openSettings`) | Inherits VS Code's Manage; adds no entry point |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/platform-resources/src/idea/PlatformActions.xml` (`ShowSettings` in `FileMainSettingsGroup`), `platform/platform-resources/src/keymaps/$default.xml` (`control alt S`) | File menu item plus a shortcut |
| `termux-app` | FULL (phone) | `termux-app@3df69d1d app/src/main/res/layout/activity_termux.xml` (`settings_button` at the top of `left_drawer`) | A gear inside the navigation drawer |
| `winlator` | FULL (phone) | `winlator@fb66541b app/app/src/main/res/menu/main_menu.xml` (`menu_item_settings` in the `NavigationView`) | Settings as a navigation-drawer destination |
| the shipped rootfs | ABSENT | — | Not a UI |

**Count:** 5 of 7 implement it, and every one that has a UI puts Settings in chrome that is on screen
whatever view is open. The desktop references put it in global chrome (menu bar, activity bar foot); both
phone references put it in the navigation drawer.

## Implementation inventory

### vscode@3f3380c6

| Concern | Files |
|---|---|
| UI entry point | `src/vs/workbench/browser/parts/globalCompositeBar.ts` (`GlobalCompositeBar`, `globalActivityAction`) |
| Domain model | `src/vs/workbench/common/activity.ts` (`GLOBAL_ACTIVITY_ID`, `ACCOUNTS_ACTIVITY_ID`) |
| Placement | the global composite bar sits after the view containers, at the activity bar's foot |

### termux-app@3df69d1d, winlator@fb66541b

| Concern | Files |
|---|---|
| UI entry point | `activity_termux.xml` `settings_button`; `main_menu.xml` `menu_item_settings` |
| Shell | `DrawerLayout` in both (`activity_termux.xml`, `app/app/src/main/res/layout/main_activity.xml`) |

**The disagreement:** at two panes VS Code's rail-foot gear maps one to one onto `SidePanelRail`. At one
pane the phone references use a drawer — which this app does not have — and our bottom bar already holds
Material's five-item ceiling.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Two panes | VS Code → rail foot; JupyterLab/IntelliJ → menu bar | We have a rail and no menu bar | The rail's foot, as VS Code: one place, on screen with every panel |
| The Files bar's gear at two panes | keep both / drop it | Two "Settings" nodes on one screen, and two homes | Drop it at two panes; one home |
| One pane | Termux/Winlator → drawer; Material → 3–5 bar items | Open decision, below | T2 |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Two-pane reach | `ui/explorer/ExplorerBar.kt` `FilesBar` gear, Files only | `vscode@3f3380c6 globalCompositeBar.ts` | gear on the rail's foot |
| One-pane reach | the same gear, Files only | Termux/Winlator drawer | reach from Search and Sessions and Tabs |

Ours is **present and partial**: Settings exists and opens (`NotebookBackStack.kt` `openSettings`), from
one view only.

## Deviations

None at two panes. At one pane no reference has our bottom bar; T2 records what replaces the drawer.

## Open decisions

**Where Settings goes at one pane.** `SidePanelBar` already carries five items (`onePaneDestinations`'
three panels, New Launcher, New terminal — `SidePanel.kt`), Material's ceiling for a navigation bar
(`developer.android.com/develop/ui/compose/components/navigation-bar`). Candidates: a sixth bar item;
a modal navigation drawer, as both phone references; the gear on each panel's own title bar; an overflow
item on the bar. `JD-061`'s answer is the home `JD-051` reuses for extension panels at one pane, so it is
decided with that in view, measured on the tablet in portrait, and becomes an ADR.

## Surface

| Layer | Files |
|---|---|
| `domain/model`, `domain/port`, `data/*` | none — Settings and `openSettings` exist |
| `ui/shell` | `SidePanelRail.kt`, `SidePanelBar.kt` (T3) |
| `ui/explorer` | `ExplorerBar.kt`, `ExplorerScreen.kt` |
| shell wiring | `Navigation.kt`, `ShellSidePane.kt`, `Settings.kt` (KDoc) |
| instrumented tests | `SidePanelRailTest.kt`, `SettingsNavigationTest.kt`, `SidePanelBarTest.kt` (T3) |
| `.maestro/` flow | `settings-from-the-rail.yaml` (T1), one-pane flow (T3) |

## Tasks

- [x] T1 — Two panes: the gear sits at the foot of `SidePanelRail`, below every panel and the `+`; it
  opens Settings and is never selected; the Files bar drops its gear at two panes.
- [ ] T2 — One pane: research Material's guidance and measure the candidates on the tablet in portrait;
  decide, as an ADR. Split T3 here if the chosen pattern is more than one unit.
- [ ] T3 — One pane: build the chosen pattern.
- [ ] T4 — The demo on `SM-X216B` in both orientations; ship.

## Out of scope

Extension panels at one pane (`JD-051`), though it reuses T2's decision. A settings search or JSON editor
(`settingeditor:open-json`).
