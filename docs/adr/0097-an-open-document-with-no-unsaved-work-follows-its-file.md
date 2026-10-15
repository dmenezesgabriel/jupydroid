# ADR-0097 — An open document with no unsaved work follows its file

Status: accepted
Date: 2026-10-14

## Context

The workspace is the kernel's home and the terminal's, so a cell, a shell command or a second
Jupyter client rewrites files that are open in a tab. A tab re-read its file only when the user tapped
the strip's reload icon (ADR-0096), and until then a clean tab showed what it opened.
`WorkspaceRepository.changes` announces only what this app moved
(`app/src/main/java/com/jupydroid/app/domain/port/WorkspaceRepository.kt`), so nothing told an open
document that something else had written its file. That was `GAP-064`.

What the references do:

- **VS Code** re-reads every clean model on an UPDATED or ADDED file event and "never reload[s] dirty
  models". A queue keeps at most one running and one waiting re-read per resource, and a burst of
  events is gathered for `FILE_CHANGES_HANDLER_DELAY = 75` ms (vscode@3f3380c
  `src/vs/workbench/services/textfile/common/textFileEditorModelManager.ts` `onDidFilesChange`,
  `src/vs/platform/files/node/watcher/parcel/parcelWatcher.ts`). Its own save is not re-read
  because the resolve carries the etag it holds (`textFileEditorModel.ts` `resolveFromFile`). A
  notebook follows the same rule through `storedFileWorkingCopyManager.ts`.
- **IntelliJ** re-reads a document with no unsaved changes on a VFS change fed by a native watcher,
  and skips the event its own save raised (intellij-community@406a27a8da
  `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileDocumentManagerImpl.java`
  `contentsChanged`, `isFromSave`; `platform/platform-impl/src/com/intellij/openapi/vfs/impl/local/FileWatcher.java`).
- **JupyterLab** has no watcher. `Context._onFileChanged` hears only the app's own contents calls,
  and a change on disk is found when a save compares `last_modified` (jupyterlab@7f0e690
  `packages/docregistry/src/context.ts`), which is ours already (ADR-0090).
- **vscode-jupyter**, **termux-app** and **winlator** watch nothing of the kind, and the shipped
  rootfs carries neither `watchfiles` nor `watchdog`: `jupyter_server` serves a file when asked
  (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filemanager.py`).

Whether inotify sees a write made under proot was measured on SM-X216B before any code was written.
`run-as com.jupydroid.app inotifyd - files/spaces/base/home/jupydroid:wcmynd` watched the workspace
while a cell rewrote `notes.py` and `target.ipynb` and the app saved the notebook the cell was in:

```
c  notes.py        c  target.ipynb       n  .~writer.ipynb   (the app's own save of writer.ipynb)
c  notes.py        c  target.ipynb       c  .~writer.ipynb
w  notes.py        w  target.ipynb       w  .~writer.ipynb
                                         m  .~writer.ipynb
                                         y  writer.ipynb
```

The cell's write arrives as `CLOSE_WRITE` (`w`) on the file itself. The app's own save arrives as
`MOVED_TO` (`y`) onto it, because a save writes beside and renames over (ADR-0089). A second client's
save through `jupyter_server` writes in place, since `atomic_writing` copies a backup aside and opens
the file itself for writing (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py`),
so it is a `CLOSE_WRITE` too. The terminal runs under the same proot (ADR-0073), so its writes are the
same syscalls.

## Decision drivers

- Unsaved work is never replaced by what something else wrote.
- A clean tab on screen shows a kernel's write while it is in view, not on the next tap.
- The app's own save is not a change to follow: re-reading it moves the caret and the revision.
- Android gives an app a bounded number of inotify watches.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Re-read on focus, as IntelliJ's terminal refreshes the VFS when it loses focus | No watcher to keep | Misses a write to the tab already on screen, which is the case a running cell makes |
| Poll each open document's stamp | No platform API | A delay or a busy loop per document, and the explorer's 10 s poll shows how stale that reads |
| The workspace watched recursively, as VS Code | The explorer could listen too | One inotify watch per directory, kept in step with every `mkdir` |
| inotify on each open document's folder | Immediate, and as many watches as folders with an open document | The explorer still polls |

## Decision

A clean open document re-reads its file when inotify reports the file written, and a dirty one is left
alone.

- **The watch:** `InotifyWorkspaceWatcher` puts an `android.os.FileObserver` on the folder of each
  open document, for `CLOSE_WRITE | MOVED_TO`, the two events measured above
  (`app/src/main/java/com/jupydroid/app/data/disk/InotifyWorkspaceWatcher.kt`). `MODIFY` is left out
  because it fires per chunk, so it would report a file half-written.
- **One observer per folder**, shared by every open file in it. inotify answers a second watch on a
  folder with the first one's descriptor, and `FileObserver` keeps one observer per descriptor, so a
  second observer took the first one's events and stopping either removed the watch from both
  (`twoFilesWatchedInOneFolderAreEachHeard`, `aFileStillWatchedIsHeardAfterItsNeighbourStops`).
- **The rule:** VS Code's. `FileSaveConflict.followDisk` and `NotebookSaveConflict.followDisk`
  re-read under the editor's write lock, unless the document is dirty or the stamp heard is the one
  it holds. A dirty document's conflict is found at its save (ADR-0090), or settled by Reload's
  question (ADR-0096).
- **Our own save is recognised by its stamp,** because inotify reports it like anyone else's and
  names no writer. A save adopts the stamp it answered under the same lock, so the report of that
  save carries the stamp the document already holds (`theEditorsOwnSaveIsNotFollowed`, in
  `FileEditorFollowTest` and `EditorFollowTest`).
- **A burst settles first:** `DocumentFileFollow` acts once 75 ms pass with no further write, VS
  Code's figure, and a rename re-aims the watch at the new path
  (`app/src/main/java/com/jupydroid/app/ui/editor/DocumentFileFollow.kt`).
- **The reload icon stays live on a clean document** (ADR-0096). The watcher does not make it
  redundant: it is how a dirty document takes the disk's version, and re-reading a clean one costs
  nothing.

Measured on SM-X216B on a fresh install: a cell rewrote two open documents, and both showed the
cell's text with no tap and no dot. A typed edit survived the cell's next write and kept its dot.
With autosave on, the debounce's own save left the tab's text and caret where they were.

## Consequences

A tab with no unsaved work shows what a cell, the terminal or a second client wrote to its file,
including a tab beneath the top, whose ViewModel lives on. A tab with unsaved work never loses it to
a write it did not make.

What a future reader will be tempted to "fix":

- **Adding `MODIFY` to the mask.** A cell writing in chunks would be read half-written.
- **One `FileObserver` per document.** Two documents in one folder then silence each other.
- **Dropping the stamp comparison because the debounce "only saves what is on screen".** Every
  autosave would re-read, and the caret would jump while the user types.
- **Greying the reload icon on a clean document, as VS Code does.** See above.

Not covered, on purpose:

- **The explorer's listing**, which keeps its 10 s poll (`GAP-056`).
- **A file deleted or moved by another process.** `WorkspaceRepository.changes` still announces only
  what this app moved, and the watch hears writes and renames onto a file, not away from it.
