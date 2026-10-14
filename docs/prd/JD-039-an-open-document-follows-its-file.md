# JD-039 — An open document follows its file

Milestone: `M5` · State: in-progress · Depends on: JD-038 · Closes: GAP-064

## Outcome

A notebook or a file open with no unsaved work shows what a cell, a shell command or a second client
wrote to its file without anyone tapping Reload, and one with unsaved work keeps it.

## Demo

On the arm64 tablet (`SM-X216B`), because a cell has to write the file:

1. Fresh install, launch, enter `base`. Seed `notes.py`, `target.ipynb` and `writer.ipynb`, whose one
   cell rewrites both with values only a kernel computes (`after_42`, `reloaded_42`).
2. Open `notes.py` and `target.ipynb`, then `writer.ipynb`, and run its cell.
3. Tap `notes.py`'s tab: it shows `after_42` with no Reload tapped and no dot.
4. Tap `target.ipynb`'s tab: its cell reads `reloaded_42`, again with no tap and no dot.
5. Type into `notes.py` (autosave off) and run the writer cell again: the typed edit is still there
   with its dot; Reload asks "Reload File from Disk", as ADR-0096 says.
6. Turn autosave on, type into `notes.py` and let the debounce save it: the tab's text and caret
   stay where they are. The app's own write is not a change to follow.

Screenshots, snapshots and `maestro.log` under `evidence/jd-039-document-follows-file/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | jupyterlab@7f0e690 `packages/docregistry/src/context.ts` | `_onFileChanged` hears only `contents.fileChanged`, which only the app's own contents calls emit. A change on disk is found at save time, by comparing `last_modified` |
| `vscode` | FULL | vscode@3f3380c `src/vs/workbench/services/textfile/common/textFileEditorModelManager.ts`, `src/vs/workbench/services/workingCopy/common/storedFileWorkingCopyManager.ts` | `onDidFilesChange`: on an UPDATED or ADDED event every clean model is re-resolved ("never reload dirty models"), through a queue that keeps at most one running and one waiting re-read per resource |
| `vscode-jupyter` | ABSENT | vscode-jupyter@f01d80c `src/platform/common/variables/customEnvironmentVariablesProvider.node.ts` | Its only watcher is over `.env` files. A notebook follows its file through VS Code's `storedFileWorkingCopyManager` |
| `intellij-community` | FULL | intellij-community@406a27a8da `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileDocumentManagerImpl.java`, `platform/platform-impl/src/com/intellij/openapi/vfs/impl/local/FileWatcher.java`, `platform/execution-impl/src/com/intellij/terminal/TerminalVfsSynchronizer.kt` | A native watcher refreshes the VFS. `contentsChanged` re-reads a document with no unsaved changes. The terminal refreshes the VFS when it loses focus, "to get the updates from a long-running command" |
| `termux-app` | ABSENT | termux-app@3df69d1 | No `FileObserver`, and no document to follow |
| `winlator` | ABSENT | winlator@fb66541 | No `FileObserver` |
| the shipped rootfs | ABSENT | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/filemanager.py` | `jupyter_server` serves the file on request, and neither `watchfiles` nor `watchdog` is in the image |

**Count:** 2 of 7 implement this, and they are the two desktop editors. JupyterLab learns of a
change only by refusing a save, and that is already ours (ADR-0090). On this device the workspace is
written by the kernel and the terminal as often as by the editor, which is the desktop editors'
situation, so their rule is the one to adopt.

## Implementation inventory

### vscode@3f3380c

| Concern | Files |
|---|---|
| The rule | `textFileEditorModelManager.ts` `onDidFilesChange`: `if (model.isDirty()) continue`, and `e.contains(resource, UPDATED, ADDED)` → `queueModelReload` |
| Collapsing a burst | `queueModelReload`: `modelResolveQueue.queueSize(resource)` is at most 2, one running and one waiting |
| Own save is not a change | `textFileEditorModel.ts` `resolveFromFile`: the resolve carries the last `etag`, and a file with the same etag is not re-read |
| Notebook | `storedFileWorkingCopyManager.ts` `onDidFilesChange`, the same rule over working copies |

### intellij-community@406a27a8da

| Concern | Files |
|---|---|
| The rule | `FileDocumentManagerImpl.contentsChanged`: `reloadFromDisk(document)` only when the document is not unsaved, or its stamp is the event's old one |
| Own save is not a change | the same method returns first on `event.isFromSave()` |
| The watcher | `FileWatcher.java` (native `fsnotifier`) |
| Polling a long-running writer | `TerminalVfsSynchronizer.kt`: a refresh on terminal focus-loss |

The two agree on all three: a clean document is re-read, a dirty one is left alone until its save
conflicts, and the editor's own save is not a change. They differ only in how they recognise their own
save. VS Code compares the etag it holds, and IntelliJ tags the event. Ours has no event to tag,
because inotify reports a write and not who made it, so we take VS Code's comparison against the
`FileStamp` already held (ADR-0090).

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What fires the re-read | VS Code and IntelliJ: an OS watcher. IntelliJ's terminal also refreshes on focus-loss | A watcher is immediate. A focus refresh misses a write to a tab already on screen | `inotify` through `android.os.FileObserver`, so a kernel write lands on the open tab while it is in view. *Open decisions* has the measurement showing inotify sees it under proot |
| How much to watch | VS Code: the workspace, recursively | Recursion needs one inotify watch per directory, kept in step with every `mkdir` | Only the parent directory of each open document. Only an open document acts on an event |
| A dirty document | VS Code and IntelliJ: skipped, and the save conflicts later | — | The same. The save's File Changed (ADR-0090) and the Reload icon (ADR-0096) answer it |
| A burst of writes | VS Code: queue of at most 2 per resource | | Conflate, then let the events settle before re-reading |
| Our own save | VS Code: an etag-conditioned resolve. IntelliJ: `isFromSave` on the event | Without it every save re-reads, and the caret and revision jump. An inotify event carries no author | Compare the read's `FileStamp` (ADR-0090) with the one held, and change nothing when they are equal |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The re-read | `FileSaveConflict.revert`, `NotebookSaveConflict.revert` (under the ViewModel's write lock, dropping the owed write) | vscode `resolveFromFile` | present. It needs only the stamp guard |
| Knowing the file changed | `WorkspaceRepository.changes` is what this app moved, nothing else | vscode `onDidFilesChange` | absent |
| The dirty rule | `OpenDocument.dirty` | vscode `isDirty` | present |

## Deviations

- **One directory per open document, not the whole tree.** `inotify` is per directory and Android
  gives an app a bounded number of watches. The explorer keeps its 10 s poll (`GAP-056`).

## Open decisions

- **Does inotify see a write made under proot?** Settled by measurement, before any code was
  written. On SM-X216B, `run-as com.jupydroid.app inotifyd - files/spaces/base/home/jupydroid:wcmynd`
  watched the workspace while `writer.ipynb`'s cell rewrote `notes.py` and `target.ipynb` under proot:

  ```
  c  notes.py        c  target.ipynb       n  .~writer.ipynb   (the app's own save of writer.ipynb)
  c  notes.py        c  target.ipynb       c  .~writer.ipynb
  w  notes.py        w  target.ipynb       w  .~writer.ipynb
                                           m  .~writer.ipynb
                                           y  writer.ipynb
  ```

  The kernel's write arrives as `CLOSE_WRITE` on the file itself. The app's own save arrives as
  `MOVED_TO` onto it, because a save writes beside and renames over (ADR-0089). A second client's
  save through `jupyter_server` writes in place: `atomic_writing` copies a backup aside and opens the
  file itself for writing (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py`),
  so it arrives as `CLOSE_WRITE`. So the mask is `CLOSE_WRITE | MOVED_TO`, for a writer that renames
  over, as any editor saving like ours does. Our own save has to be recognised by its stamp, because
  inotify reports it like anyone else's. The terminal runs under the same proot (ADR-0073), so its writes
  are the same syscalls.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | `WorkspaceWatcher.kt` |
| `data/disk` | `InotifyWorkspaceWatcher.kt` |
| `data/fake` | `FakeWorkspaceWatcher.kt` |
| `ui/` | a class shared by both editors that follows a clean document's file; `FileEditorViewModel.kt`, `FileSaveConflict.kt`, `EditorViewModel.kt`, `NotebookSaveConflict.kt` |
| `di/` | `EnteredSpacePorts.kt`, `JupydroidApplication.kt` |
| unit tests | the shared class's test, a follow test for each editor |
| instrumented tests | `InotifyWorkspaceWatcherTest` (`FileObserver` is `android.*`) |
| `.maestro/` flow + `scripts/` driver | `jd-039-document-follows-file-t1.yaml`, `scripts/jd-039-document-follows-file-t1-driver.sh` |

## Tasks

- [x] T1 — A clean open notebook or file re-reads its file when something else writes it, and a dirty
  one keeps its edit. The port, the inotify adapter, the fake and both editors, with the tablet flow.
- [ ] T2 — The demo on a fresh install, ADR-0097, GAP-064 closed, and this PRD deleted.

## Out of scope

- A tab beneath the top shows nothing until it is brought up, but it still follows its file, because
  its ViewModel lives on.
- The explorer's listing stays on its poll (`GAP-056`).
- A file deleted or moved by something other than this app (`WorkspaceRepository.changes` still
  announces only what this app moved).
