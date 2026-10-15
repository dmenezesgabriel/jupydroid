# JD-041 — A document is saved under a new name

Milestone: `M5` · State: in-progress · Depends on: JD-031 · Closes: GAP-063

## Outcome

A long press on a notebook's or a file's tab offers **Save As…**: the document as it is on screen,
unsaved edits included, is written to a path the user types, a taken path asked about first, and
the new file opens in a tab of its own while the old file keeps what was last saved.

## Demo

On the arm64 tablet (`SM-X216B`) - the notebook's copy is checked by running a cell in it.

1. Fresh install, launch, enter `base`. Settings → Autosave off.
2. Open `notes.py`; type a line. The tab wears the dirty dot.
3. Long-press the tab. A menu offers **Rename…** and **Save As…**.
4. Save As… → the dialog "Save File As…" holds `notes.py` with `notes` selected; type
   `notes-copy`, Save. The `notes.py` tab is gone; `notes-copy.py` is on top, clean, showing the
   typed line. `adb shell cat` shows the line in `notes-copy.py` and not in `notes.py`.
5. Open `target.ipynb`, edit a cell, Save As… onto `notes-copy.py`'s neighbour `other.ipynb`, which
   already exists: "Overwrite file?" asks; Overwrite. `other.ipynb` opens with the edited cell; run
   it.
6. Save As… into a folder that does not exist: "Save Error" says why, and the tab stays as it was.
7. Kill and relaunch - both copies open with what Save As wrote.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/docmanager-extension/src/index.tsx` (`CommandIDs.saveAs`), `schema/plugin.json` (File menu rank 4, `Accel Shift S`), `packages/docregistry/src/context.ts` (`saveAs`, `_maybeOverWrite`, `_finishSaveAs`, `Private.getSavePath`) | a "Save File As…" dialog over the full path; a taken one asks "File Overwrite?"; the model is written to the new path, then the old file's widget closes and the new path opens |
| `vscode` | FULL | `contrib/files/browser/fileActions.contribution.ts` (`SAVE_FILE_AS_COMMAND_ID`: File menu, and the tab context menu under `ActiveEditorDirtyContext`) | the platform's save dialog; the editor is replaced by one on the new resource, the original reverted |
| `vscode-jupyter` | ABSENT | `src/commands.ts` | types the workbench's `workbench.action.files.saveAs`, nothing of its own |
| `intellij-community` | ABSENT | `platform/lang-impl/src/com/intellij/refactoring/copy/CopyFilesOrDirectoriesHandler.java` | no Save As: a document is saved in place and a copy is the Refactor Copy act on the file, not the editor |
| `termux-app` | ABSENT | — | no documents |
| `winlator` | ABSENT | — | no documents |
| the shipped rootfs | ABSENT | `jupyter_server/services/contents/handlers.py` | `PUT api/contents/<new path>` is an ordinary save; save-as is the client's |

Pinned SHAs: jupyterlab@7f0e690, vscode@3f3380c, vscode-jupyter@f01d80c,
intellij-community@406a27a8da, termux-app@3df69d1, winlator@fb66541.

**Count:** 2 of 7 - both that edit a document in an editor which owns its model.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| UI entry point | `docmanager-extension/src/index.tsx` `saveAs`; `schema/plugin.json` File menu rank 4, `Accel Shift S` |
| Path prompt | `docregistry/src/context.ts` `Private.getSavePath` → `SaveWidget(path)`: the full local path, editable |
| Taken path | `_maybeOverWrite`: `"%1" already exists. Do you want to replace it?`, Cancel/Overwrite; Overwrite deletes then writes |
| The write | `_finishSaveAs` → `contents.save(newPath, _createSaveOptions())` - the model as it is, dirty or not |
| Afterwards | the command's `fileChanged` listener: `closeFile(context.path)` then `docmanager:open` the new path |
| Same path | `saveAs` saves in place |
| Refusal | `_handleError(err, 'File Save Error for %1')` |

### vscode@3f3380c

| Concern | Files |
|---|---|
| UI entry point | `fileActions.contribution.ts` - File menu; `appendEditorTitleContextMenuItem(SAVE_FILE_AS_COMMAND_ID, …, ActiveEditorDirtyContext, '1_close_save', …, 20)`, the tab's menu beside Save |
| Afterwards | the editor on the original is replaced in its group by one on the target |

**The disagreement** is what becomes of the tab: JupyterLab closes it and opens the new file;
VS Code replaces the editor in place, keeping its position. Settled below with the user.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where it is pressed | jupyterlab → File menu, key; vscode → File menu, tab context menu | No menu bar and no keymap here; a strip icon costs tab width at phone size for a rare act | **The tab's long press becomes a menu: Rename…, Save As…** - VS Code's tab-context place, and the menu GAP-059's comment declined only while it was one item deep |
| What is asked | jupyterlab → full path; vscode → platform dialog with folders | A bare name cannot save into another folder | **A path**, prefilled with the current one and the leaf's stem selected; every segment passes `isValidFileName` |
| Enabled rule | jupyterlab → writable; vscode tab menu → dirty | A copy of a clean document is still a copy | **Always**, on a document tab |
| Taken path | jupyterlab → "File Overwrite?" | — | **`OverwriteFileDialog`** ("Overwrite file?", the rename's own), then an unconditional write |
| Afterwards | jupyterlab → close + open; vscode → replace in place | In place needs a new path-adoption through both editors and the kernel sessions | **Close the old tab without writing it, open the new path** - jupyterlab's, through `openDocument`; a notebook's copy gets its own kernel |
| Same path | jupyterlab → saves | The strip's save icon already saves | **Dismisses**, `RenameFileDialog`'s unchanged rule |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Write the model elsewhere | `persist` in `FileEditorViewModel`/`EditorViewModel` writes to the live path only | `_finishSaveAs` | absent |
| Probe a path | `WorkspaceRepository.list` of the target's folder - exact, because the listing hides only hidden names and the path rule refuses them | `contents.get(path, {content:false})` | present - no new port operation |
| Path prompt | `RenameFileDialog`, parameterised heading/validator/selection/confirm label | `getSavePath` | present - reused |
| Overwrite ask | `OverwriteFileDialog` | `_maybeOverWrite` | present - reused |
| Close without asking | the close gate's Discard: `discardChanges` + `closeMainAreaTab` (`ui/shell/MainAreaCloseGate.kt`) | `closeFile` | present |
| Open the copy | `openDocument` (`DocumentOpeners.kt`) | `docmanager:open` | present |
| Tab context menu | long press → rename dialog (`ui/shell/MainAreaTabStrip.kt`) | vscode's tab menu | present, one item deep |

## Deviations

- An unchanged path dismisses rather than saving in place.
- The old document's unsaved work is dropped from it, not written to the old file: it went to the
  copy, as in both references.

## Open decisions

None left: the entry point and what becomes of the tab were settled with the user when the item
was picked up.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `isValidWorkspacePath` (`NotebookFileName.kt`) |
| port seam | `OpenDocuments.kt` - `OpenDocument.writeCopyTo(path)` |
| ViewModels | `ui/fileeditor/FileEditorViewModel.kt` (its save moved to `FileEditorWrites.kt` to stay at detekt's eleven functions), `ui/editor/NotebookDocument.kt`, `ui/editor/EditorViewModel.kt` |
| `ui/shell` | `SaveAsOutcome.kt`, `TabSaveAs.kt`, `MainAreaTabStrip.kt`; `Navigation.kt` wiring; `ui/RenameFileDialog.kt` takes a field label |
| unit tests | `SaveDocumentAsTest`, `FileEditorSaveAsTest`, `EditorSaveAsTest`, `NotebookFileNameTest` |
| instrumented tests | `TabSaveAsTest`, `MainAreaTabStripTest`, `WorkspaceTabs` rename helper |
| `.maestro/` flow + `scripts/` driver | `jd-041-save-as-t1-*.yaml`, `scripts/jd-041-save-as-t1-driver.sh` |

## Tasks

- [x] T1 — `writeCopyTo`, the tab's menu and the Save As flow; flow on SM-X216B: a dirty
  file saved as a new name opens clean with the edit while the original keeps its own, and a
  notebook saved over a taken name asks File Overwrite? and opens
- [x] T2 — the demo on a fresh install, then ship: ADR-0099, delete this PRD, GAP-063 closes

## Out of scope

- Delete, duplicate and clone on the tab's menu (`GAP-059`, `GAP-060`); `Accel Shift S` (`GAP-134`);
  checkpoints (`GAP-062`).
