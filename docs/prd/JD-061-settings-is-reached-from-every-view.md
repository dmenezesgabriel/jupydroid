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
4. Rotate to portrait (one pane). The bar's fifth item is "More"; the Files bar has no gear. From Files,
   Search and Sessions and Tabs, "More" opens the modal rail with New terminal in it and Settings at its
   foot; Settings opens, and closing it leaves the panel where it was. "New terminal" in the modal rail
   opens a terminal.
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
| One pane | Termux/Winlator → drawer; Material → 3–5 bar items, and the drawer deprecated for the expanded rail | A sixth item reaches 45dp at 360dp once extension panels follow | A modal expanded rail from the bar's fifth item, "More", which New terminal gives up ([ADR-0109](../adr/0109-at-one-pane-the-rail-opens-as-a-modal-from-the-bars-last-item.md)) |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Two-pane reach | `ui/explorer/ExplorerBar.kt` `FilesBar` gear, Files only | `vscode@3f3380c6 globalCompositeBar.ts` | gear on the rail's foot |
| One-pane reach | the same gear, Files only | Termux/Winlator drawer | reach from Search and Sessions and Tabs |

Ours is **present and partial**: Settings exists and opens (`NotebookBackStack.kt` `openSettings`), from
one view only.

## Deviations

None at two panes. At one pane the phone references' drawer becomes Material's modal expanded rail, and
New terminal leaves the bar for it (ADR-0109).

## Open decisions

None. Where Settings goes at one pane is ADR-0109, measured on SM-X216B in portrait
(`evidence/settings-at-one-pane/`).

## Surface

| Layer | Files |
|---|---|
| `domain/model`, `domain/port`, `data/*` | none — Settings and `openSettings` exist |
| `ui/shell` | `SidePanelRail.kt`, `SidePanelBar.kt`, the modal rail (T3) |
| `ui/explorer` | `ExplorerBar.kt`, `ExplorerScreen.kt` |
| shell wiring | `Navigation.kt`, `ShellSidePane.kt`, `Settings.kt` (KDoc) |
| instrumented tests | `SidePanelRailTest.kt`, `SettingsNavigationTest.kt`, `SidePanelBarTest.kt` (T3) |
| `.maestro/` flow | `settings-from-the-rail.yaml` (T1), one-pane flow (T3) |

## Tasks

- [x] T1 — Two panes: the gear sits at the foot of `SidePanelRail`, below every panel and the `+`; it
  opens Settings and is never selected; the Files bar drops its gear at two panes.
- [x] T2 — One pane: research Material's guidance and measure the candidates on the tablet in portrait;
  decide, as an ADR (ADR-0109).
- [ ] T3 — One pane: the bar's fifth item becomes "More", opening a `ModalWideNavigationRail` with New
  terminal in it and Settings at its foot; the Files bar drops its gear at both widths. Update
  `SidePanelBarTest`, `TerminalNavigationTest` and every one-pane flow that taps the bar's "New
  terminal" (the portrait drivers: `grep -ln "rotate_to 0" scripts/*.sh`, and the phone flows).
- [ ] T4 — The demo on `SM-X216B` in both orientations; ship.

## Out of scope

Extension panels at one pane (`JD-051`), though it reuses T2's decision. A settings search or JSON editor
(`settingeditor:open-json`).
