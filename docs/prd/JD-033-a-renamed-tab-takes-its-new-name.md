# JD-033 — A tab renamed beneath another takes its new name

Milestone: `M6` · State: in-progress · Depends on: — · Closes: GAP-172

> Delete this file when the item ships — its durable residue is an ADR and the code comments, nothing
> else ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A file or notebook renamed while its tab sits beneath another tab — a terminal, a console, another
document — reads its new name on the strip and in the Running panel's Open Tabs, as it already does
when it is on top.

## Demo

On the arm64 tablet (`SM-X216B`) — a terminal is one of the tabs, and only arm64 ships a rootfs, so on
`dev36` the terminal tab is not the one the user meant.

1. Fresh install, launch, enter `base`. Create `second.py` and a notebook `second.ipynb`.
2. Open `second.py`, then open a terminal: the terminal is on top, `second.py` beneath it.
3. Long-press the `second.py` tab, Rename, `renamed.py`. Without touching the strip, the tab reads
   `renamed.py`; the explorer lists `renamed.py`; Open Tabs lists `renamed.py`.
4. Same for `second.ipynb` beneath the terminal, renamed from the explorer's row: its tab reads the
   new name.
5. Tap the renamed tab: it opens on the moved file, and an edit saves to the new path only.
6. Kill and relaunch: the tab and the file both carry the new name.

Not `shipped` until this has run on that device, with screenshots, snapshots and `maestro.log`
committed under `evidence/jd-033-renamed-tab/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e6906 packages/docregistry/src/context.ts` (`_updatePath` → `_pathChanged.emit`), `packages/docregistry/src/default.ts` (`_onPathChanged` sets `title.label`) | The title is a subscriber of the context's path signal, so any widget follows a rename, on top or not |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/workbench/services/editor/browser/editorService.ts` (`handleMovedFile`) | Every group's every editor matching the moved resource is replaced in place, `inactive: !group.isActive(editor)` — a background tab is retitled too |
| `vscode-jupyter` | ABSENT (inherits) | — | Notebook editors are VS Code's; the workbench's `handleMovedFile` does it |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileEditorManagerImpl.kt` (`propertyChanged` on `PROP_NAME` → `scheduleUpdateFileName` → `updateFileNames`) | A VFS name change walks every window's composites and `tab.setText` on each, selected or not |
| `termux-app` | ABSENT | — | No document tabs; its renames are session names |
| `winlator` | ABSENT | — | No document editor |
| the shipped rootfs | ABSENT | — | Nothing draws a tab |

**Count:** 3 of 7, and every one that draws a document tab does it, **for every tab** — the title is
pushed from the rename, never pulled by a redraw that happens to occur. That is the repair's shape.

## Implementation inventory

### jupyterlab@7f0e6906

| Concern | Files |
|---|---|
| Domain model | `packages/docregistry/src/context.ts` — `_path`, `_updatePath`, `pathChanged` |
| UI entry point | `packages/docregistry/src/default.ts` — `DocumentWidget._onPathChanged` |
| Edge cases | `_updatePath` returns early when the path is unchanged; it also moves the session's path and name |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| UI entry point | `src/vs/workbench/services/editor/browser/editorService.ts` — `onDidRunFileOperation`, `handleMovedFile` (a parent folder moved is handled too) |
| Port / protocol | `src/vs/workbench/common/editor/editorInput.ts` — `onDidChangeLabel` |

### intellij-community@406a27a8

| Concern | Files |
|---|---|
| UI entry point | `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileEditorManagerImpl.kt` — `propertyChanged`, `updateFileNames` |
| Edge cases | Also retitles other open files of the same name (unique-name tab titles) |

The references agree; no disagreement to settle.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How the strip learns of the move | jupyterlab → a signal the title subscribes to; vscode → the editor service replaces the input; intellij → the manager pushes `setText` | Our strip is Compose: what it reads must be observable state, or it redraws only by accident | `OpenDocument.path` is backed by **snapshot state** in each ViewModel. `OpenDocuments` is already a `@Stable` snapshot holder (`mutableStateListOf`), so every existing reader — `runningTabTitle`, `documentIn`, `at`, `WorkspaceReplace`, the close gate — is observed with no signature change. A `StateFlow<String>` (as `dirty` is) would change the interface and need a `collectAsState` per tab for the same effect |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The document follows its file | `FileEditorViewModel.adopt`, `EditorViewModel.adopt` on `WorkspaceChange.Renamed` | `context.ts` `_updatePath` | complete |
| The title follows the document | `MainAreaTabs.kt` `runningTabTitle` reads `documentIn(openDocuments)?.path`, a plain `var` | `default.ts` `_onPathChanged` | **present and partial**: correct when something else recomposes the strip, stale beneath a terminal |
| The strip's own live-path map | `MainAreaTabStrip.kt` rename `onSuccess`, `Navigation.kt` `onFileMoved`/`onNotebookMoved` write `openDocumentPaths` | — | written before the ViewModel adopts, so the recomposition it causes reads the old path |

## Deviations

None.

## Open decisions

- Once the path is observable, which `openDocumentPaths[key] = moved` writes are still needed? The
  map also answers `liveDocumentPath` for a key whose document is not registered, and
  `Navigation.kt`'s side-pane reveal reads it. Settle by test in T1: remove a write only when a test
  proves the observable path covers it.

## Surface

| Layer | Files |
|---|---|
| `domain/model`, `domain/port`, `data/*`, `di` | none — a UI-state defect |
| `ui/` | `OpenDocuments.kt` (contract), `ui/fileeditor/FileEditorViewModel.kt`, `ui/editor/EditorViewModel.kt`, `ui/editor/NotebookDocument.kt`, possibly `ui/shell/MainAreaTabStrip.kt`, `Navigation.kt` |
| unit tests | `app/src/test/.../ui/fileeditor/`, `.../ui/editor/` — the path is observed through a rename |
| instrumented tests | beside `OpenDocumentMarkingTest` — a tab beneath another is retitled by a rename |
| `.maestro/` flow + `scripts/` driver | `jd-033-renamed-tab-*` |

## Tasks

- [ ] T1 — The fix: before-flow on SM-X216B, red JVM + instrumented tests, the live path as snapshot
  state, after-flow.
- [ ] T2 — The demo on a fresh install; ship: delete this PRD, close GAP-172, backlog row `shipped`.

## Out of scope

- Retitling other tabs sharing the new name (IntelliJ's unique-name titles) — no tab here shows a
  disambiguating directory.
- A rename's effect on the kernel session's path and name (`_updatePath`'s session half) — not what
  GAP-172 measured.
