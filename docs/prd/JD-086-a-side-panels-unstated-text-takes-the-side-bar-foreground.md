# JD-086 — A side panel's unstated text takes the palette's side-bar foreground

Milestone: `M11` · State: open · Depends on: — · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Text in the Running, Search, Debugger, Table of contents and contributed side panels that states no colour is
drawn in the theme's `sideBar.foreground`, as the Files panel's rows are; today it is Material's
`onSurfaceVariant`, the secondary colour.

## Demo

Nothing here runs a cell, so the tablet (`SM-X216B`) serves; `dev36` is for the instrumented test only.

1. Cold start on the tablet, Dark, two panes. Open each of the five panels with something to show (a
   running kernel, a search with matches, a paused debugger or its empty text, a notebook with headings, a
   contributed panel). Read the unstated text's colour with `scripts/read_text_contrast.py` against the
   panel's `sideBar.background`. Today: the secondary `#8C8C8C`, 5.18:1 in Dark.
2. After: `sideBar.foreground`, `#bfbfbf` in Dark (9.48:1) and `#202020` in Light.
3. Repeat in the six combinations of the app's System, Light and Dark against the device's Light and Dark
   (`scripts/theme-six-combinations-driver.sh`).
4. Text a panel states itself (the title, a dimmed hint, an error) is the colour it was; only what was
   unstated moved.

The item is not `shipped` until this has run on the tablet and the frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode@3f3380c6` | FULL | `src/vs/workbench/common/theme.ts:621` (`SIDE_BAR_FOREGROUND`, registered `null`), `src/vs/workbench/browser/parts/sidebar/sidebarPart.ts:173` | the container sets `style.color` once, so every unstated child inherits it; 2026 Dark `#bfbfbf` and Light `#202020` (`extensions/theme-defaults/themes/2026-*.json`) |
| `jupyterlab@7f0e6906` | not read | — | a browser; `--jp-ui-font-color1` is a CSS inheritance, not a separate question |
| `vscode-jupyter@f01d80ca` | ABSENT | — | draws no side bar of its own |
| `intellij-community@406a27a8` | ABSENT | `IntelliJPlatform.themeMetadata.json` (`SidePanel.background` only) | states a side-panel background and no foreground key; text takes the Swing label default |
| `termux-app@3df69d1d`, `winlator@fb66541b` | not read | — | not side-bar references |
| the shipped rootfs | ABSENT | — | no UI |

**Count:** 1 of 7 states a side-bar foreground, and it is the one our palette is transcribed from
(`ChromePalette.sideBarForeground`). Material's own answer is the `Scaffold`'s `contentColor`.

## Implementation inventory

### Compose material3

| Concern | Files |
|---|---|
| Content colour of a `Scaffold` | its `contentColor` parameter, defaulting to `contentColorFor(containerColor)`, which matches the container to a role *by hex* |

### Ours

| Concern | Files |
|---|---|
| The colour, stated | `theme/ChromePalette.kt` (`sideBarForeground`), `theme/ChromePalettes.kt` |
| The Files panel stating it | `ui/explorer/ExplorerScreen.kt` (`rowForeground`), `ExplorerRowColourTest` |
| The five panels | `ui/shell/RunningSidePanel.kt`, `SearchSidePanel.kt`, `DebuggerSidePanel.kt`, `TableOfContentsSidePanel.kt`, `ContributedSidePanel.kt` |
| Their shared chrome | `ui/shell/SidePanelPresentation.kt` (`sideBarTopAppBarColors`) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where it is stated | each `Text` · each panel's `Scaffold` · one wrapper | per `Text` is what the Files rows do and misses every future one; VS Code states it on the container | the container: one `SideBarScaffold` stating `containerColor` and `contentColor` together, so a sixth panel cannot state one and forget the other |
| What it does to the Files panel | migrate it too · leave it | its rows state their own colours (open row differs) | leave it: it is not broken |

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Unstated text in five panels | takes `onSurfaceVariant` by hex match (`containerColor = sideBarBackground`, five calls) | `vscode@3f3380c6 sidebarPart.ts:173` | present and wrong in all five: unstated text resolved to `#8C8C8C` where `#BFBFBF` is stated (`SideBarForegroundTest`, red 5 of 5 on `dev36`) |
| A test that holds it | `ExplorerRowColourTest` for the Files rows, `SideBarForegroundTest` for the five | — | complete |

## Deviations

None. Contributed panels' content is an extension's; it inherits the same colour, which is the point.

## Open decisions

- Settled by T1's red run: every panel had unstated text, and none was already correct.

## Surface

A theme and UI item with no domain, port or adapter.

| Layer | Files |
|---|---|
| `ui/shell/` | `SidePanelPresentation.kt` (`SideBarScaffold`), the five panels |
| unit tests | `ArchitectureGuardTest` only if a rule is wanted against a raw `Scaffold(containerColor = sideBarBackground)` |
| instrumented tests | a side-panel colour test beside `ExplorerRowColourTest`, on `dev36` |
| `.maestro/` flow + `scripts/` driver | the existing `theme-six-combinations-driver.sh`, `read_text_contrast.py` |

## Tasks

- [x] T1 — red test that each of the five panels' unstated text resolves to `sideBarForeground`, then
  `SideBarScaffold` and the five callers.
- [ ] T2 — read the five panels on the tablet in the six combinations, with the numbers before and after, and
  edit the ledger row in `parity-ledger.md`.

## Out of scope

The Files panel, the top app bar's title and action colours, and any new palette key.
