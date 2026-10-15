# JD-044 — A tab's menu closes the tabs beside it

Milestone: `M6` · State: in-progress · Depends on: JD-043 · Closes: GAP-174

## Outcome

A long press on any main-area tab offers **Close All Other Tabs** and **Close Tabs to Right**, so
the strip can be cleared down to one document without closing each tab by its ✕, and a tab with
unsaved work still asks before it goes.

## Demo

On the arm64 tablet (`SM-X216B`), the device every M6 demo ran on. No cell runs, so the emulator
would also do.

1. Fresh install, launch, enter `base`. Settings → Autosave off.
2. Open `a.py`, a Terminal, `b.py` (type a line so it is dirty) and `c.ipynb`, in that order.
3. Long-press the Terminal's tab: the menu holds **Close All Other Tabs** and **Close Tabs to
   Right**, and no document rows.
4. Close Tabs to Right: `b.py` asks to save. Cancel: `b.py` stays, dirty, and `c.ipynb` closes. The
   strip reads `a.py`, Terminal, `b.py`.
5. Long-press `a.py`'s tab: the two close rows lead, followed by Rename…, Save As…, Revert to
   Checkpoint…, Duplicate and Delete….
6. Close All Other Tabs: the Terminal closes and `b.py` asks again. Discard: the strip reads `a.py`
   alone.
7. On the lone tab, both rows are greyed.
8. Kill and relaunch: `b.py` opens with its last save, without the discarded line.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/application-extension/src/index.tsx` (`CommandIDs.closeOtherTabs`, `closeRightTabs`, `widgetsRightOf`, `closeWidgets`), `packages/application-extension/schema/commands.json` (`context`, selector `#jp-main-dock-panel .lm-DockPanel-tabBar .lm-TabBar-tab`, ranks 4 and 5) | on every main-area tab: `widget.close()` for each other widget, or for each one after it in its tab area; enabled at ≥2 widgets and with one to the right |
| `vscode` | FULL | `src/vs/workbench/browser/parts/editor/editor.contribution.ts` (`MenuId.EditorTitleContext`, group `1_close`), `editorCommands.ts`, `editorGroupView.ts` (`closeEditors`, `except`, `direction`) | the tab menu leads with Close, Close Others (disabled at one editor), Close to the Right (disabled on the last), Close Saved, Close All |
| `vscode-jupyter` | ABSENT | — | the workbench's tab |
| `intellij-community` | FULL | `platform/platform-resources/src/idea/PlatformActions.xml` (`EditorTabPopupMenu` → `CloseEditorsGroup`: `CloseAllEditorsButActive`, `CloseAllToTheRight`) | the tab popup leads with its close group, others and to-the-left/right included |
| `termux-app` | ABSENT | — | sessions in a drawer, no tab strip |
| `winlator` | ABSENT | — | no document tabs |
| the shipped rootfs | ABSENT | — | a server concern does not reach a tab strip |

Pinned SHAs: jupyterlab@7f0e690, vscode@3f3380c, vscode-jupyter@f01d80c,
intellij-community@406a27a8da, termux-app@3df69d1, winlator@fb66541.

**Count:** 3 of 7, which is every reference that has a tab strip. All three put the close group
first on the tab's own menu.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| UI entry point | `application-extension/schema/commands.json` `context`: `close` 4, `close-other-tabs` 4, `close-all` 4, `close-right-tabs` 5, over every main-area tab |
| Labels | `index.tsx`: `Close All Other Tabs`, `Close Tabs to Right` |
| Enabled | others: `some(shell.widgets('main'), (_, i) => i === 1)`; right: `widgetsRightOf(...).length > 0` |
| Close itself | `widget.close()` per widget, which is where `saveBeforeClose` asks for a dirty document; no bulk ask, no explicit activation |

### vscode@3f3380c

| Concern | Files |
|---|---|
| UI entry point | `editor.contribution.ts` `EditorTitleContext` group `1_close`, orders 10–50 |
| Enabled | Close Others: `EditorGroupEditorsCountContext.notEqualsTo('1')`; Close to the Right: `ActiveEditorLastInGroupContext.toNegated()` |
| Close itself | `editorGroupView.ts` `closeEditors` with `except` and `direction`; dirty editors ask |

### intellij-community@406a27a8da

| Concern | Files |
|---|---|
| UI entry point | `PlatformActions.xml` `CloseEditorsGroup`, referenced first by `EditorTabPopupMenu` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Labels | jupyterlab → "Close All Other Tabs", "Close Tabs to Right"; vscode → "Close Others", "Close to the Right" | — | JupyterLab's, the app's model |
| Which tabs get the menu | all three → every tab | ours opened the menu on document tabs only | every main-area tab; the document rows only where the tab has a path |
| Position | all three → the close group first | the document rows move down | the close rows first, a divider, then the document rows |
| Bulk ask | none of them ask for the bulk act itself | — | none; each close goes through `rememberMainAreaCloseGate`, which queues a dirty tab's ask |
| "Right" | jupyterlab → the tab area's order | — | the strip's opening order, which is what the strip draws left to right |
| Activation | jupyterlab → nothing explicit | — | nothing explicit; `closeMainAreaTab`'s survivor rule lands on what stays |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Tab menu | `ui/shell/MainAreaTab.kt`, document tabs only | jupyterlab `commands.json` `context` | absent on non-document tabs; no close rows |
| Per-tab close with the dirty ask | `ui/shell/MainAreaCloseGate.kt` | `widget.close()` → `saveBeforeClose` | complete, reused |
| Bulk close | `ui/shell/RunningSidePanel.kt` Close All, `openTabs.forEach { onCloseTab(it.key) }` | `closeWidgets` | complete for all; absent for others and to the right |
| Strip order | `MainAreaTabs.kt` `orderedAsOpened` | `widgetsRightOf` | complete |

## Deviations

- One dirty ask at a time, queued, rather than one dialog per dirty widget at once: the gate's own
  existing rule.

## Open decisions

None. Each axis above follows the three references, which agree.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/shell` | `MainAreaTab.kt`, `MainAreaTabStrip.kt`; `MainAreaTabs.kt` (which tabs are beside one) |
| `di/AppContainer` | — |
| unit tests | `MainAreaTabs.kt`'s sibling rule |
| instrumented tests | `TabCloseSiblingsTest`, the strip's terminal-tab menu test |
| `.maestro/` flow + `scripts/` driver | `jd-044-tab-close-siblings-t1-*`, `jd-044-tab-close-siblings-demo-*` |

## Tasks

- [ ] T1 — Close All Other Tabs and Close Tabs to Right on every tab's menu, each through the close gate
- [ ] T2 — the demo on a fresh install; ADR, ledger rows, ship

## Out of scope

- `application:close-all` on the tab's menu: the Running panel's Open Tabs carries Close All.
- VS Code's Close Saved and IntelliJ's Close to the Left: no reference this app is modelled on has both.
- `docmanager:clone`, a second view on one document: GAP-060.
