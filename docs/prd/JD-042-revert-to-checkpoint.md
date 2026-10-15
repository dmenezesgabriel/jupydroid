# JD-042 — A document reverts to its checkpoint

Milestone: `M5` · State: in-progress · Depends on: JD-031, JD-038 · Closes: GAP-062

## Outcome

A long press on a notebook's or a file's tab offers **Revert to Checkpoint…**, which puts the
document back to what it held when it was first opened or last saved by the user, asking first,
and the checkpoint lives where jupyter_server keeps its own, so a JupyterLab client on the same
workspace sees the same one.

## Demo

On the arm64 tablet (`SM-X216B`) - the notebook's revert is checked by running a cell after it.

1. Fresh install, launch, enter `base`. Settings → Autosave off.
2. Open `notes.py`. `adb shell run-as … ls` shows `.ipynb_checkpoints/notes-checkpoint.py` beside it.
3. Type a line, tap the strip's Save. Type a second line (dirty).
4. Long-press the tab: **Rename…**, **Save As…**, **Revert to Checkpoint…**. Revert → "Revert File
   to Checkpoint" says it cannot be undone and when the checkpoint was made; Revert. The tab is
   clean and shows the first line only.
5. Open `target.ipynb`, edit a cell, Save; edit again; Revert to Checkpoint… → the saved cell, clean;
   run it.
6. A file whose checkpoint was removed by hand: Revert to Checkpoint… says "No Checkpoints".
7. Rename `notes.py` to `renamed.py` from its tab: the checkpoint moves to `renamed-checkpoint.py`,
   and Revert still answers with it. Delete a folder holding only a deleted file's
   `.ipynb_checkpoints`: it deletes as empty.
8. Kill and relaunch - Revert to Checkpoint… on `notes.py`'s tab still reverts to the saved line.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/docmanager-extension/src/index.tsx` (`CommandIDs.restoreCheckpoint`, `RevertConfirmWidget`, `Private.createRevertConfirmNode`, the `save` command's `createCheckpoint()`), `packages/docregistry/src/context.ts` (`_maybeCheckpoint`, `_populate`, `_finishSaveAs`, `restoreCheckpoint`, `revert`) | one checkpoint per file, made at first open when none exists, after the user's Save and after Save As; Revert asks, restores, re-reads |
| `vscode` | PARTIAL | `src/vs/workbench/contrib/localHistory/browser/localHistoryCommands.ts` (`workbench.action.localHistory.restore`, `restore`) | Local History: many entries per file, kept outside the workspace; Restore asks "Restoring will discard any unsaved changes", reverts dirty copies, `cloneFile`s the entry over the file |
| `vscode-jupyter` | ABSENT | — | the workbench's Local History, nothing of its own |
| `intellij-community` | PARTIAL | `platform/lvcs-api/src/com/intellij/history/LocalHistory.kt` | Local History with labels; a revert is a picked revision, not a checkpoint |
| `termux-app` | ABSENT | — | no documents |
| `winlator` | ABSENT | — | no documents |
| the shipped rootfs | FULL (server) | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/filecheckpoints.py`, `manager.py`, `filemanager.py` | `copy2_safe` into `<dir>/.ipynb_checkpoints/<stem>-checkpoint<ext>`, id `"checkpoint"`, one per file; rename and delete carry it; a folder holding only it is empty |

Pinned SHAs: jupyterlab@7f0e690, vscode@3f3380c, vscode-jupyter@f01d80c,
intellij-community@406a27a8da, termux-app@3df69d1, winlator@fb66541.

**Count:** 4 of 7 keep a point to return to; the two that are Jupyter keep exactly one, beside the
file, in the same layout.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| UI entry point | `docmanager-extension/src/index.tsx` `restoreCheckpoint`: label `Revert %1 to Checkpoint…`, File menu |
| When one is made | `context.ts` `_populate` → `_maybeCheckpoint(false)` (only if `listCheckpoints` is empty); `_finishSaveAs` → `_maybeCheckpoint(true)`; the `save` command → `createCheckpoint()` after `context.save()`. **Not** autosave (`docmanager/src/savehandler.ts` calls `context.save()` alone) and **not** `saveAll` |
| Empty state | `No checkpoints` / `No checkpoints are available for this %1.` |
| Confirm | `Revert %1 to checkpoint`; `Are you sure you want to revert the %1 to checkpoint? This cannot be undone.`; `The checkpoint was last updated at:` + the time; Cancel, warn Revert |
| The revert | `restoreCheckpoint(id)` then `context.revert()` |
| Read-only folder | `_maybeCheckpoint` swallows a 403 |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Restore | `localHistoryCommands.ts` `restore`: confirm, `workingCopy.revert({soft})` on dirty copies, `fileService.cloneFile(entry, resource)`, `revert({force})`; `Unable to restore '{0}'.` on a refusal |

### the shipped rootfs

| Concern | Files |
|---|---|
| Layout | `filecheckpoints.py` `checkpoint_path`: `<parent>/.ipynb_checkpoints/<basename>-checkpoint<ext>` |
| Copy | `fileio.py` `copy2_safe`: `shutil.copyfile` then `copystat`, errors in copystat logged |
| List | `list_checkpoints`: `[{id: "checkpoint", last_modified: mtime}]` or `[]` |
| Follow the file | `manager.py` `rename_file` → `rename_all_checkpoints`; `delete_file` → `delete_all_checkpoints` |
| Empty folder | `filemanager.py` `is_non_empty_dir`: `set(os.listdir(os_path)) - {cp_dir}` |

**The disagreement**: VS Code and IntelliJ keep many points outside the workspace; Jupyter keeps
one, beside the file, where every Jupyter client reads it. This app's workspace is a Jupyter
server's `root_dir`, so the Jupyter answer is the one a second client sees.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where it is written | ADR-0050 → through the server; rootfs → a file copy | The server is down most of the time (ADR-0024); the "algorithm" is a copy | **On disk, in FileCheckpoints's exact layout** - measured as I/O, so ADR-0050's reason does not reach it; a server started later lists the same file |
| How many | jupyterlab/rootfs → one; vscode/intellij → a history | Many needs a picker and a store outside the tree | **One per file**, the rootfs's |
| When made | jupyterlab → first open if none, user's Save, Save As | Autosave checkpointing would make the point follow every keystroke | **jupyterlab's three, not autosave, not Save All** |
| Where pressed | jupyterlab → File menu; vscode → timeline row menu | No menu bar; the tab menu already holds the document's acts | **The tab's long-press menu**, third item |
| Confirm | jupyterlab → cannot be undone + time; vscode → discards unsaved changes | — | **jupyterlab's words and the time**, Revert in the warning colour |
| The revert | jupyterlab → restore then `revert()`; vscode → revert dirty, clone, revert | — | **restore, then `OpenDocument.reloadFromDisk()`**, which drops the owed write first (ADR-0096) |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Checkpoint ops | — | `filecheckpoints.py` | absent - a port of its own |
| Re-read after restore | `OpenDocument.reloadFromDisk` (`OpenDocuments.kt`) | `context.revert()` | present |
| Hidden from the tree | `WorkspaceListing.kt` drops dot-entries; the searcher walks through it | `allow_hidden=False` | present |
| Opening seam | `rememberDocumentOpener` (`DocumentOpeners.kt`) | `_populate` | present, makes none |
| User's save | `ActiveTabSave.kt` → `saveNow {}` | the `save` command | present, makes none |
| Tab menu | `MainAreaTabStrip.kt`: Rename…, Save As… | — | present, no revert |
| Rename/delete carry it | `DiskWorkspaceRepository.rename/overwrite/delete` (T2 hands them the checkpoint's layout) | `rename_all_checkpoints`, `delete_all_checkpoints` | absent |

Ours is **absent**.

## Deviations

- Written on disk rather than through the server; recorded in an ADR at ship, which narrows ADR-0050.
- A notebook saved by autosave alone keeps the checkpoint from its first open, as JupyterLab's does.

## Open decisions

None: the item and the adapter were settled with the user when it was picked up.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `Checkpoint` |
| `domain/port` | `DocumentCheckpoints` (`create`, `list`, `restore`) - a port of its own beside `WorkspaceRepository`, which is the tree as it is and already at detekt's function cap; `catchingRefusal` |
| `data/disk` | `DiskDocumentCheckpoints` |
| `data/fake` | `FakeDocumentCheckpoints`, over any `WorkspaceRepository` |
| `di` | `EnteredSpacePorts.documentCheckpoints`; `JupydroidApplication` builds the disk one |
| `ui/shell` | `TabRevertToCheckpoint.kt`, `CheckpointAsk.kt` (new), `MainAreaTab.kt` (split out of `MainAreaTabStrip.kt`), `ActiveTabSave.kt`; `DocumentCheckpointing.kt` and `DocumentOpeners.kt`, `Navigation.kt` wiring |
| unit tests | `DocumentCheckpointsContract` (disk and fake), the Disk layout test, `CheckpointAskTest`, `DocumentCheckpointingTest` |
| instrumented tests | `TabRevertToCheckpointTest` |
| `.maestro/` flow + `scripts/` driver | `jd-042-checkpoint-t1-*.yaml`, `scripts/jd-042-checkpoint-t1-driver.sh` |

## Tasks

- [x] T1 — the checkpoint ops, made at first open, the strip's Save and Save As, and the tab's
  Revert to Checkpoint…; flow on SM-X216B: a saved then edited file and notebook revert to the save,
  clean, the checkpoint at its layout path, and a file without one says No Checkpoints
- [ ] T2 — a checkpoint follows its file: rename and overwrite move it, delete removes it, a folder
  holding only `.ipynb_checkpoints` deletes as empty; flow on SM-X216B
- [ ] T3 — the demo on a fresh install, then ship: an ADR narrowing ADR-0050, delete this PRD,
  GAP-062 closes

## Out of scope

- Many checkpoints, or a history outside the tree (VS Code's Local History).
- A diff against the checkpoint before reverting.
- Checkpoints made by Save All or autosave - neither reference makes one.
