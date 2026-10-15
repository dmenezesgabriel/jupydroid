# ADR-0100 — A document reverts to its checkpoint, kept beside it where jupyter_server keeps its own

Status: accepted
Date: 2026-10-15

## Context

A document had no point to go back to. With autosave off, an edit the user regretted after a save
could only be undone by hand, and nothing kept what the file held when it was opened or last saved.
That was `GAP-062`. The shipped server serves checkpoints, but no port named them and nothing ever
made one.

What the references do:

- **JupyterLab** keeps one checkpoint per file. It is made at first open when the file has none
  (`_maybeCheckpoint(false)` from `_populate`), after the user's `docmanager:save`
  (`createCheckpoint()` after `context.save()`) and after a Save As (`_finishSaveAs`), and **not** by
  autosave (`docmanager/src/savehandler.ts` calls `context.save()` alone) or `saveAll`.
  `docmanager:restore-checkpoint`, "Revert %1 to Checkpoint…" in the File menu, says "No
  checkpoints" when there is none. Otherwise it asks "Are you sure you want to revert the %1 to
  checkpoint? This cannot be undone.", with "The checkpoint was last updated at:" and the time, then
  runs `restoreCheckpoint(id)` and `context.revert()`. A read-only folder's 403 is swallowed
  (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx` `CommandIDs.restoreCheckpoint`,
  `RevertConfirmWidget`, `packages/docregistry/src/context.ts`).
- **The shipped rootfs's `jupyter_server`** writes a checkpoint as a file copy, `copy2_safe`, to
  `<folder>/.ipynb_checkpoints/<stem>-checkpoint<ext>` under the id `"checkpoint"`, one per file.
  `rename_file` carries it with `rename_all_checkpoints` and `delete_file` with
  `delete_all_checkpoints`, and `is_non_empty_dir` leaves the checkpoint folder out, so a folder
  holding only it deletes as empty
  (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filecheckpoints.py`,
  `fileio.py`, `manager.py`, `filemanager.py`).
- **VS Code** keeps a Local History of many entries per file outside the workspace. Its restore
  asks, reverts dirty copies, and clones the entry over the file
  (vscode@3f3380c `src/vs/workbench/contrib/localHistory/browser/localHistoryCommands.ts`).
- **IntelliJ** keeps a Local History with labels, and a revert is a picked revision
  (intellij-community@406a27a8da `platform/lvcs-api/src/com/intellij/history/LocalHistory.kt`).
- **vscode-jupyter** has only the workbench's Local History. **termux-app** and **winlator** have
  no documents.

The references disagree on where the point lives. VS Code and IntelliJ keep many outside the
workspace. Jupyter keeps one beside the file, where every Jupyter client reads it. This app's
workspace is a Jupyter server's `root_dir`.

## Decision drivers

- A second Jupyter client on the same workspace sees the same checkpoint this app made.
- It works with the server stopped, which is most of the time
  ([ADR-0024](0024-the-app-stops-its-own-server-so-the-app-repairs-what-that-breaks.md)).
- The point is the user's last deliberate save, not wherever a debounce landed.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Through the server's checkpoints API ([ADR-0050](0050-reads-hit-the-repository-server-side-algorithms-do-not.md)) | The server's own code | Every open and save would need a running server |
| A history outside the tree (VS Code, IntelliJ) | Many points | A picker and a store of its own, and no other Jupyter client sees it |
| One copy beside the file, in `FileCheckpoints`' layout, written from disk | Works offline; a server started later lists it | The layout is now this app's to keep in step with the rootfs |

## Decision

**One checkpoint per file, written from disk in the shipped `FileCheckpoints`' exact layout**, through
a port of its own, `DocumentCheckpoints` (`create`, `list`, `restore`), beside `WorkspaceRepository`
(`app/src/main/java/com/jupydroid/app/domain/port/DocumentCheckpoints.kt`,
`app/src/main/java/com/jupydroid/app/data/disk/DiskDocumentCheckpoints.kt`). The server's checkpoint
is a file copy and nothing more, measured in `copy2_safe`, so ADR-0050's reason for going through
the server does not apply to it.

- **When one is made:** at first open when the file has none (`checkpointIfNone`, on the
  application scope), after the strip's Save and after Save As (`checkpointSaved`). Autosave and Save
  All make none, as in the reference (`app/src/main/java/com/jupydroid/app/DocumentCheckpointing.kt`).
  A checkpoint that cannot be made is dropped, and the file still opens.
- **Where pressed:** **Revert to Checkpoint…**, the third item of the tab's long-press menu after
  Rename… and Save As… (`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTab.kt`). There is no
  menu bar to hold JupyterLab's File menu entry.
- **The ask:** the reference's words, "Revert File to checkpoint" or "Revert Notebook to checkpoint"
  with "This cannot be undone." and the checkpoint's time in the device's short date and time.
  Cancel, and Revert in the warning colour. "No checkpoints" when the list is empty. One point,
  so no picker (`app/src/main/java/com/jupydroid/app/ui/shell/TabRevertToCheckpoint.kt`).
- **The revert:** restore, then `OpenDocument.reloadFromDisk()`, which drops the write the edits
  owed ([ADR-0096](0096-an-open-document-is-reloaded-from-disk-on-demand-from-the-tab-strip.md)).
  The tab comes back clean.
- **It follows the file:** `DiskWorkspaceRepository`'s rename and overwrite move it, and delete
  removes it. A folder holding only `.ipynb_checkpoints` deletes as empty
  (`app/src/main/java/com/jupydroid/app/data/disk/CheckpointFiles.kt`).

Measured on SM-X216B on a fresh install with autosave off: `notes.py`, saved and edited again,
reverted to the save, clean, and `.ipynb_checkpoints/` beside it held `notes-checkpoint.py` and
`target-checkpoint.ipynb`. `target.ipynb` did the same. After a kill and relaunch, `notes.py` still
reverted to its saved line, and the reverted notebook's cell ran in a kernel. A checkpoint made at
open and then removed by hand, under the open tab, answered "No checkpoints". `notes.py` renamed
to `renamed.py` took its checkpoint along and still reverted. A folder left holding only
`.ipynb_checkpoints` deleted as empty.

## Consequences

The user can go back to the last save in one act, and a JupyterLab on the same workspace lists the
same checkpoint. `.ipynb_checkpoints` folders appear in the tree on disk. The explorer hides them,
as `allow_hidden=False` does.

What a future reader will be tempted to "fix":

- **Routing checkpoints through `/api/contents`.** That makes every open and save need a server.
  The copy is the whole algorithm.
- **Checkpointing on autosave or Save All.** The point would then follow every pause in typing,
  and Revert would have nothing to go back to.
- **A picker over many checkpoints.** The layout holds one per file, and so does every Jupyter
  client that reads it.

Not covered, on purpose:

- **Many checkpoints, or a history outside the tree** (VS Code's Local History).
- **A diff against the checkpoint** before reverting.
