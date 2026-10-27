# ADR-0121 — The Recents section arrives shut

Status: accepted
Date: 2026-10-27

## Context

The Files view's Recents section sits above the directory listing at the workspace root. It can be
collapsed from its heading, and the collapse persists in `explorer-settings.json`
(`app/src/main/java/com/jupydroid/app/data/disk/DiskExplorerSettingsRepository.kt`). The default,
for an install where nobody had pressed the heading, was open. The justification was VS Code's
generic rule: a view keeps `!!viewDescriptor.collapsed` until a stored state overrides it
(`vscode@3f3380c src/vs/workbench/services/views/common/viewContainerModel.ts`), and nothing had
asked for this section to arrive shut.

On a phone, the open section takes the listing's space before anyone has asked for it. On SM-X216B
in portrait, each recent row is 88 px (about 59 dp). Three recents pushed the listing's first row
from y ≈ 333 px to y ≈ 597 px, and the list holds up to `MAX_RECENTS`.

What the references do with a secondary list that shares a container with the file tree:

- **vscode@3f3380c.**
  - Outline declares `collapsed: true` (`src/vs/workbench/contrib/outline/browser/outline.contribution.ts`).
  - Timeline declares it too (`src/vs/workbench/contrib/timeline/browser/timeline.contribution.ts`).
  - Open Editors, the nearest match to Recents, declares `collapsed: false` but `hideByDefault: true`
    (`src/vs/workbench/contrib/files/browser/explorerViewlet.ts`). Every secondary list in the
    Explorer container stays out of the tree's way until it is asked for.
- **jupyterlab@7f0e690.** The Running panel's sections start open (`packages/running/src/index.tsx`,
  `useState(false)`). They are in a panel of their own and share no space with the file browser.
  Recents there is a menu, not a section.
- **intellij-community@406a27a.** Recent files is a popup switcher
  (`platform/recentFiles/frontend/src/com/intellij/platform/recentFiles/frontend/Switcher.kt`), and is
  never resident in the project view.
- `vscode-jupyter`, `termux-app`, `winlator` and the rootfs have no such section.

## Decision

`ExplorerSettings.recentsCollapsed` defaults to `true`. A fresh install draws the `Recent` heading,
shut, above the listing. One tap opens it, and the user's choice is stored and survives a relaunch,
exactly as before.

- **Shut, not hidden.** Open Editors is hidden by default, but VS Code has a views menu to bring it
  back. Here the heading is the only way into the section.
- **One home for the default.** `RecentsUiState.collapsed` reads `ExplorerSettings().recentsCollapsed`
  instead of keeping its own `false`, so the frame drawn before the store answers is shut too.
- **An install that never toggled is treated as fresh.** The store is only written on a toggle, so an
  install with no `explorer-settings.json` made no choice to keep. It gets the new default after
  an upgrade, as `viewContainerModel.ts` gives a view with no stored state its descriptor's default.

## Consequences

- The listing starts directly under one heading row at the root, however many recents there are.
- Reaching a recent costs one more tap until the user opens the section. After that it stays open
  until they shut it.
- Tests that need an open section say so: `ExplorerScreenTest.showExplorer` defaults its store to
  open, and the Maestro flows that read recent rows tap the heading first.
- A test that proves persistence writes the non-default value (`false`), since writing `true` would
  pass with nothing stored (`DiskExplorerSettingsRepositoryTest.survivesTheNextLaunch`,
  `RecentsViewModelTest.aSectionOpenedBeforeTheProcessDiedComesBackOpen`).
