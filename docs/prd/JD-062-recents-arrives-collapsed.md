# JD-062 — Recents arrives collapsed

Milestone: `M6` · State: in-progress · Depends on: — · Closes: —

## Outcome

On a fresh install the Files view's Recents section arrives shut, showing only its heading. The
listing gets the screen, and the user can still open the section with one tap, which is remembered
across launches as before.

## Demo

On the `dev36` emulator. No cell runs, so `FakeKernelGateway` is never asked anything (`AGENTS.md`
rule 2).

1. Cold start on a fresh install: open the base Space, open `file_editor.py`, go back to the root.
2. The `Recent` heading is drawn with its chevron shut, and the `file_editor.py` recent row is not.
3. Tap the heading: the row appears. Kill and relaunch: it is still open.
4. Tap the heading again to shut it. Kill and relaunch: it is still shut.

Evidence goes in `evidence/jd-062-recents-arrives-collapsed/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690680a7 packages/running/src/index.tsx` | The Running panel's sections are collapsible and start **open** (`useState(false)`). Recents is a menu, not a section |
| `vscode` | FULL | `vscode@3f3380c668d0 src/vs/workbench/contrib/outline/browser/outline.contribution.ts`, `contrib/timeline/browser/timeline.contribution.ts`, `contrib/files/browser/explorerViewlet.ts` | In the Explorer container, Outline and Timeline declare `collapsed: true`. Open Editors, the nearest match to Recents, declares `collapsed: false` but also `hideByDefault: true` |
| `vscode-jupyter` | ABSENT | — | Contributes no explorer section of recents |
| `intellij-community` | ABSENT | `intellij-community@406a27a8da07 platform/recentFiles/frontend/src/com/intellij/platform/recentFiles/frontend/Switcher.kt` | Recent files is a popup switcher, never a resident section of the project view |
| `termux-app` | ABSENT | — | No file browser |
| `winlator` | ABSENT | — | No recents in its sources |
| the shipped rootfs | ABSENT | — | — |

**Count:** 2 of 7 have a collapsible secondary section. VS Code, the one whose explorer this one
follows, keeps every secondary list of the Explorer container from taking space by default: Outline
and Timeline arrive shut, and Open Editors arrives hidden. JupyterLab's Running sections arrive open,
but they live in a panel of their own and do not share it with the file listing.

## Implementation inventory

### vscode@3f3380c668d0

| Concern | Files |
|---|---|
| Domain model | `src/vs/workbench/common/views.ts` (`IViewDescriptor.collapsed`) |
| Persisted state | `src/vs/workbench/services/views/common/viewContainerModel.ts` (`!!viewDescriptor.collapsed` unless stored state overrides) |
| UI entry point | `contrib/outline/browser/outline.contribution.ts`, `contrib/timeline/browser/timeline.contribution.ts` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| First-launch state | vscode → secondary views shut (Outline, Timeline) or hidden (Open Editors); jupyterlab → Running sections open | Open shows history without a tap; on a phone it pushes the listing down | **Shut**: Outline and Timeline's rule. We don't hide it, because the heading is the only way in on a phone |
| Install that never toggled | a missing store reads as the default (`viewContainerModel.ts`) | Starts shut after an upgrade too | Same: no choice was stored, so there is nothing to keep |

This becomes an ADR when the item ships.

## Gap analysis

| Concern | Ours today (file) | Gap |
|---|---|---|
| Collapse, toggle, persistence | `RecentsViewModel.kt`, `RecentsSection.kt`, `DiskExplorerSettingsRepository.kt` | complete |
| First-launch default | `ExplorerSettings.kt` (`recentsCollapsed = false`), `RecentsUiState` (`collapsed = false`) | the default only |

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `ExplorerSettings.kt` |
| `ui/explorer` | `RecentsViewModel.kt` (`RecentsUiState` default) |
| unit tests | `ExplorerSettingsRepositoryContract.kt`, `RecentsViewModelTest.kt` |
| instrumented tests | `ExplorerScreenTest.kt` |
| `.maestro/` flow + `scripts/` driver | `recents-arrives-collapsed.yaml` + driver; the Recents flows that read rows right after opening something |

## Tasks

- [ ] T1 — the default flips to shut, red test first. The flows that read recent rows open the
  section first, and the demo runs on `dev36` with evidence.

## Out of scope

Hiding the section, or a setting for its default. Neither was asked for.
