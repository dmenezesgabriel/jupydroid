# JD-040 — Every open document is saved from Open Tabs

Milestone: `M5` · State: in-progress · Depends on: JD-031 · Closes: GAP-061

## Outcome

With several tabs holding unsaved work, one tap on **Save All** in the Running panel's Open Tabs
section writes every one of them, beneath the top or not; a save the disk refuses brings its tab to
the top, where its own screen asks or says why.

## Demo

On the arm64 tablet (`SM-X216B`) - the refused save is made by a cell rewriting an open file.

1. Fresh install, launch, enter `base`. Settings → Autosave off.
2. Open `notes.py` and `target.ipynb`; type into each. Both tabs wear the dirty dot.
3. Open the Running panel. Open Tabs shows **Save All** beside Close All, enabled.
4. Tap Save All. Both dots turn back into closes; `adb shell cat` shows both edits on disk. Save All
   greys.
5. Type into `notes.py` and into `target.ipynb`, leaving `target.ipynb` on top; a cell writes
   `notes.py` on disk.
6. Tap Save All. `notes.py` comes to the top asking **File Changed**; `target.ipynb` keeps its dot
   (it comes after `notes.py` in tab order, so the sweep stopped first).
7. Answer Overwrite. Tap Save All again: `target.ipynb` is written, every dot is gone.
8. Kill and relaunch - both files open with what Save All wrote.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/docmanager-extension/src/index.tsx` (`CommandIDs.saveAll`), `schema/plugin.json` | `context.save()` for every writable main-area context at once, one per path; File menu |
| `vscode` | FULL | `contrib/files/browser/fileCommands.ts` (`SAVE_ALL_COMMAND_ID`), `fileActions.contribution.ts`, `views/openEditorsView.ts` | `saveDirtyEditorsOfGroups`, enabled on `DirtyWorkingCopiesContext`; File menu, and an icon in Open Editors' title beside Close All |
| `vscode-jupyter` | ABSENT | `src/test/datascience/notebook/helper.ts` | calls the workbench's `workbench.action.files.saveAll` from a test, nothing of its own |
| `intellij-community` | FULL | `platform/platform-resources/src/idea/PlatformActions.xml` (`SaveAll`), `platform/platform-impl/src/com/intellij/ide/actions/SaveAllAction.kt` | `FileDocumentManager.saveAllDocuments()`; menu row and key |
| `termux-app` | ABSENT | — | no documents |
| `winlator` | ABSENT | — | no documents |
| the shipped rootfs | ABSENT | `jupyter_server/services/contents/handlers.py` | `PUT api/contents/<path>` saves one path; a save-all is the client's loop |

Pinned SHAs: jupyterlab@7f0e690, vscode@3f3380c, vscode-jupyter@f01d80c,
intellij-community@406a27a8da, termux-app@3df69d1, winlator@fb66541.

**Count:** 3 of 7, every one that edits documents and marks them dirty.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| UI entry point | `packages/docmanager-extension/src/index.tsx` `saveAll`; `schema/plugin.json` File menu rank 4 |
| Edge cases | a `paths` set so two widgets on one context save once; a read-only context gets `readonlyNotification` |
| Refusal | `packages/docregistry/src/context.ts` `_maybeSave` → `_raiseConflict`: a modal "File Changed" whatever widget is current - every conflict asks at once |

### vscode@3f3380c

| Concern | Files |
|---|---|
| UI entry point | `fileActions.contribution.ts` (File menu, Open Editors context); `views/openEditorsView.ts` `workbench.action.files.saveAll` in `MenuId.ViewTitle`, order 20, beside `openEditors.closeAll` order 30 |
| Enabled rule | `DirtyWorkingCopiesContext` - live only while something is dirty |
| Refusal | `editors/textFileSaveErrorHandler.ts` `staleSaveError`: one notification per file, offering Compare and Overwrite - no editor is brought forward |

### intellij-community@406a27a8da

| Concern | Files |
|---|---|
| UI entry point | `PlatformActions.xml` `SaveAll`; `SaveAllAction.kt` |
| Refusal | `FileDocumentManagerImpl` asks its memory/disk conflict per file, modally |

**The disagreement** is what a refusal looks like: jupyterlab and IntelliJ ask modally wherever the
user is, VS Code reports without moving. Neither fits a screen where only the top tab composes.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where it is pressed | jupyterlab → File menu; vscode → File menu + Open Editors title; IntelliJ → menu + key | No menu bar here; the strip's trailing slots cost tab width at phone size | **Open Tabs' header, beside Close All** - VS Code's Open Editors title is the same section with the same pair |
| Enabled rule | jupyterlab → any writable; vscode → any dirty | Writing a clean document moves its mtime for nothing (ADR-0093) | **Any open document dirty**, greyed otherwise |
| Order | jupyterlab → all at once; vscode → per group MRU | At once means several refusals with one screen to ask on | **One at a time, in strip order** |
| A refusal | jupyterlab, IntelliJ → modal ask wherever; vscode → notification | Only the top tab composes, so its question has nowhere to draw beneath | **The first refused document comes to the top and the sweep stops**; its own screen asks. Tapping again carries on |
| Confirm first | none of them | Saving loses nothing | **No dialog** - unlike Close All |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| One document's save | `OpenDocument.saveNow` (`app/src/main/java/com/jupydroid/app/OpenDocuments.kt`) | `context.save()` | complete, but reports success only - a caller cannot wait for it or learn of a refusal |
| A refused save beneath | `saveConflict` / `error` in the document's UI state, drawn when its tab is on top | `_raiseConflict` | present - waits for the tab to be shown |
| Save-all | — | `docmanager:save-all`, `saveAll` | absent |
| A place for it | Open Tabs has Close All (`ui/shell/RunningSidePanel.kt`) | Open Editors' title | present, one action deep |

## Deviations

- Sequential and stopping, where jupyterlab is parallel and asks every conflict at once: one modal
  surface, and only the top tab's screen exists.

## Open decisions

None left: placement and refusal were settled with the user when the item was picked up.

## Surface

| Layer | Files |
|---|---|
| port seam | `OpenDocuments.kt` - `OpenDocument.saveAndConfirm(): Boolean` |
| ViewModels | `ui/fileeditor/FileEditorViewModel.kt`, `ui/editor/NotebookDocument.kt` |
| sweep | `ui/shell/SaveAllDocuments.kt` |
| `ui/shell` | `RunningSidePanel.kt`, `ShellSidePane.kt`, `Navigation.kt` |
| unit tests | `SaveAllDocumentsTest`, `save()` cases in the editor ViewModels' tests |
| instrumented tests | `RunningSidePanelTest` |
| `.maestro/` flow + `scripts/` driver | `jd-040-save-all-t1-*.yaml`, `scripts/jd-040-save-all-t1-driver.sh` |

## Tasks

- [x] T1 — `OpenDocument.saveAndConfirm()`, the sweep, Save All on Open Tabs; flow on SM-X216B: two dirty tabs
  saved, then a refused one brought to the top asking File Changed with the next keeping its dot
- [x] T2 — the demo on a fresh install, then ship: ADR-0098, delete this PRD, GAP-061 closes

## Out of scope

- A key binding (`GAP-134`), save-as (`GAP-063`), checkpoints (`GAP-062`).
