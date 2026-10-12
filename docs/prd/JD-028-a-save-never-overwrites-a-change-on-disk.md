# JD-028 — A save never silently overwrites a change made on disk

Milestone: `M5` · State: in-progress · Depends on: JD-026 · Closes: GAP-065

## Outcome

When a cell, a terminal command or a second Jupyter client rewrites a file that is open in an editor,
the editor's next save stops and asks instead of overwriting it: the user chooses the disk's version
or their own, and neither is lost without being asked for.

## Demo

On the arm64 tablet (`SM-X216B`), because step 2 runs a cell:

1. Cold start: install, launch, enter the Space, open a notebook with two cells.
2. Run a cell that rewrites the notebook's own `.ipynb`, adding a markdown cell `ADDED ON DISK`.
   The **File Changed** dialog appears, naming the notebook, with Cancel, Revert and Overwrite.
3. Tap **Revert**. The notebook shows the `ADDED ON DISK` cell, and through `run-as` the file still
   holds it.
4. Run the cell again, and tap **Overwrite** in the dialog. Through `run-as` the file holds the
   editor's cells and the cell's output, and no second `ADDED ON DISK` cell.
5. Edit a cell with nothing else writing. No dialog appears, and the file holds the edit.
6. Open `notes.txt` in the file editor. In the terminal, `echo changed > notes.txt`. Type in the
   editor: the same dialog appears. **Revert** shows `changed`.
7. Force-stop and relaunch. Both files open as they were left.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | jupyterlab@7f0e690 `packages/docregistry/src/context.ts` (`_maybeSave`, `_raiseConflict`) | Before every save it fetches the file's model without content. A different hash, or a `last_modified` newer than the client's by more than `lastModifiedCheckMargin` (500 ms), opens a **File Changed** dialog: Cancel, Revert, Overwrite. `_conflictModalIsOpen` refuses a second dialog |
| `vscode` | FULL | vscode@3f3380c6 `src/vs/platform/files/common/fileService.ts` (`validateWriteFile`), `src/vs/workbench/contrib/files/browser/editors/textFileSaveErrorHandler.ts` | The write carries the `mtime` and `etag` (mtime + size) of the read. A newer mtime with a different etag compares the bytes; identical bytes are written, anything else throws `FILE_MODIFIED_SINCE`, and the notification offers Compare and Overwrite |
| `vscode-jupyter` | ABSENT of its own | — | A notebook is a VS Code working copy, so it inherits the row above |
| `intellij-community` | FULL | intellij-community@406a27a8 `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/MemoryDiskConflictResolver.java` | On a file-system change under an unsaved document whose stamp differs: "Changes have been made to X in memory and on disk", with Load File System Changes, Keep Memory Changes and Show Difference |
| `termux-app` | ABSENT | — | No document editor |
| `winlator` | ABSENT | — | No document editor |
| the shipped rootfs | ABSENT | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/filemanager.py` (`save`), `…/contents/handlers.py` (`put`) | The server writes what it is sent: `save` goes straight to `_save_notebook`/`_save_file`, and `put` reads no stamp from the request. The check is the client's, which is why JupyterLab fetches before it saves. Read on the tablet during T1 |

**Count:** 3 of 3 references that edit documents refuse the blind write, and all three leave the
choice to the user. None merges.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| The check | `packages/docregistry/src/context.ts` `_maybeSave` |
| The dialog and its three outcomes | `packages/docregistry/src/context.ts` `_raiseConflict` |
| Revert | `packages/docregistry/src/context.ts` `revert`, `_revert` |
| After a save: the model's stamp is replaced | `packages/docregistry/src/context.ts` `_save`, `_updateContentsModel` |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| The check, and the identical-bytes pass | `src/vs/platform/files/common/fileService.ts` `validateWriteFile` |
| The stamp kept from the last read or write | `src/vs/workbench/services/textfile/common/textFileEditorModel.ts` (`lastResolvedFileStat`) |
| What the user is offered | `src/vs/workbench/contrib/files/browser/editors/textFileSaveErrorHandler.ts` |

### intellij-community@406a27a8

| Concern | Files |
|---|---|
| The check and the dialog | `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/MemoryDiskConflictResolver.java` |
| The dialog's words | `platform/platform-api/resources/messages/UIBundle.properties` (`file.cache.conflict.*`) |

**Where they disagree.** JupyterLab checks in the client with a 500 ms margin and two calls; VS Code
checks inside the write, exactly, and lets identical bytes through. JupyterLab offers Revert in the
dialog; VS Code offers Compare. Those are the decisions below.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the check lives | jupyterlab: the client stats, then saves; vscode: the write is handed the stamp and refuses | Two calls leave every caller to remember the first one | In the write. The repository is handed the stamp it expects and refuses; the stat and the rename sit in one IO block of the adapter |
| What the stamp is | jupyterlab: hash, else `last_modified`; vscode: mtime + size | A hash reads the whole file on every save | `FileStamp(modifiedNanos, size)`, vscode's etag |
| How exact | jupyterlab: 500 ms margin; vscode: exact, then bytes | The margin is for filesystems that report inconsistent times, and it hides a kernel write landing within 500 ms of our own save | Any difference refuses, unless the bytes on disk equal the bytes being written (vscode's third step). The disk is local |
| How the stamp travels | A field on the model; beside the model | `Notebook` deliberately carries no time (`domain/model/Notebook.kt`) | Beside it: `readStampedNotebook` answers `StampedNotebook`, `saveNotebook(notebook, expected)` answers the new stamp. `readNotebook` stays for the readers that never write back |
| The refusal | An error code; a typed exception | — | `FileChangedOnDiskException` in `domain/port/`, as `FileNameTakenException` is |
| What the user is offered | jupyterlab: Cancel / Revert / Overwrite; vscode: Compare / Overwrite; intellij: Load / Keep / Show Difference | Compare needs a diff view, which does not exist here | JupyterLab's three. Its wording too |
| A second conflict while the dialog is up | jupyterlab: `_conflictModalIsOpen` rejects it | — | The conflict is a state, not an event: a second refusal sets what is already set |
| A caller that is not an editor | — | `WorkspaceReplace` writes a notebook that is **not** open, and already refuses a file whose source is not the one it matched (`sourceOnDisk`) | It writes with no stamp. Its own check covers it, and a refusal thrown part-way through a replace-all would be a new failure with nowhere to land |

The stamp, the exact comparison and the identical-bytes pass become an ADR when the item ships.

## Gap analysis

| Concern | Ours today (file) | Gap |
|---|---|---|
| Notebook save | `app/src/main/java/com/jupydroid/app/data/disk/DiskNotebookRepository.kt` `saveNotebook` | Writes blind |
| Text save | `app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceRepository.kt` `writeText` | Writes blind |
| A stamp at read | — | Absent. `GAP-065` says a document already records one; it does not |
| A failed save on screen | `EditorUiState.saveFailure`, `SaveFailureBanner` | Present, and not what this is: a conflict is a question, not a report |
| Re-reading an open document | `EditorViewModel`'s `init` | Present for the first read only |

Ours is **absent**.

## Deviations

- **No margin.** Stated above.
- **Revert is offered only inside the dialog.** A standing re-read act on a document is `GAP-064`'s,
  and stays open.
- **A save made as the screen is torn down is refused silently.** `onCleared` has no screen left to
  ask on, so the disk's version stays and the edit owed is dropped. The close gate asks first: a
  refused save keeps the document open, with the dialog over it.
- **The check and the rename are not one syscall.** A writer landing between the stat and the rename
  is still overwritten. Both references have the same window; closing it needs a lock every writer
  honours, and the kernel is not ours to make honour one.

## Open decisions

None that the references leave open.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `FileStamp.kt`, `StampedNotebook.kt` |
| `domain/port` | `NotebookRepository.kt`, `WorkspaceRepository.kt` (T2), `FileChangedOnDiskException.kt` |
| `data/disk` | `StampedFileWrite.kt`, `DiskNotebookRepository.kt`, `DiskWorkspaceRepository.kt` (T2) |
| `data/fake` | `FakeNotebookRepository.kt`, `FakeWorkspaceRepository.kt` (T2) |
| `ui` | `FileChangedDialog.kt` |
| `ui/editor` | `EditorViewModel.kt`, `EditorUiState.kt`, `EditorScreen.kt`, `NotebookSaveConflict.kt` |
| `ui/fileeditor` | `FileEditorViewModel.kt`, `FileEditorScreen.kt`, `FileSaveConflict.kt` (T2) |
| unit tests | `NotebookRepositoryContract.kt`, `StampedFileWriteTest.kt`, `NotebookSaveConflictTest.kt`, `WorkspaceTextSaveContract.kt` (T2), `FileEditorSaveConflictTest.kt` (T2) |
| instrumented tests | `FileChangedDialogTest.kt`, `FileEditorSaveConflictScreenTest.kt` (T2) |
| `.maestro/` flow + `scripts/` driver | `jd-028-save-conflict-*` |

## Tasks

- [x] T1 — A notebook's save is refused when its file changed since it was read, and the editor asks:
  Cancel, Revert, Overwrite.
- [x] T2 — A workspace text file's save is refused the same way, and the file editor asks with the
  same dialog.
- [ ] T3 — The demo above, seeded, on the tablet.

## Out of scope

- A re-read act on a clean document, and noticing a change before a save is owed: `GAP-064`.
- A diff between the two versions: no diff view exists.
- Save on demand: `GAP-061`.
