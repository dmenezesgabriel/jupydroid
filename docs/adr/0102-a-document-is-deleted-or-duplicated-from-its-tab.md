# ADR-0102 — A document is deleted or duplicated from its own tab

Status: accepted
Date: 2026-10-15

## Context

The open document's own surface could only rename it. Delete was only on the explorer's row, and
nothing anywhere could copy a file. That was `GAP-059`. On a phone at one pane, the explorer is not
on screen beside the document it would act on.

What the references do:

- **JupyterLab** puts both on the document's tab context menu (`[data-type="document-title"]`):
  rename at rank 20, duplicate at 21, delete at 22. `docmanager:duplicate` is
  `DocumentManager.duplicate`, which is `contents.copy(path, dirname)` and opens nothing.
  `docmanager:del` asks "Are you sure you want to delete %1", with Cancel and a warning Delete. It
  then runs `deleteFile`, which is `sessions.stopIfNeeded(path)`, then `contents.delete(path)`, then
  `deleteWidgets` for every context on the path. `onDelete` disposes the widget without the
  unsaved-work ask (jupyterlab@7f0e690 `packages/docmanager-extension/schema/plugin.json`,
  `packages/docmanager-extension/src/index.tsx` `CommandIDs.duplicate`, `CommandIDs.del`,
  `packages/docmanager/src/manager.ts`, `packages/docmanager/src/widgetmanager.ts`). Its file
  browser asks the same delete in other words: "Are you sure you want to permanently delete: %1?".
- **The shipped rootfs's `jupyter_server`** names the copy. `copy` takes any `-Copy\d*.` out of the
  source's name (`copy_pat`), then counts `insert="-Copy"` past what the folder holds
  (`increment_filename`). The suffix is the last dot for `.ipynb` and the first dot otherwise. A
  folder is a 400, `Can't copy directories`. The copy is `get` + `save`, so it is the file as saved
  and carries no checkpoint (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/manager.py`).
- **VS Code** keeps both acts on the explorer's row. Its tab menu holds close, split, pin, save,
  copy path and reveal (vscode@3f3380c `src/vs/workbench/browser/parts/editor/editor.contribution.ts`,
  `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts`).
- **IntelliJ** offers Rename File on the tab, but neither delete nor copy; both belong to the
  Project view (intellij-community@406a27a8da
  `platform/platform-resources/src/idea/PlatformActions.xml` `EditorTabPopupMenu`).
- **vscode-jupyter** has only the workbench's tab. **termux-app** and **winlator** have no document
  tabs.

## Decision drivers

- At one pane the tab is the only surface on screen that names the document.
- One question reads one way wherever this app asks it.
- A copy made here is named exactly as a Jupyter client on the same workspace would name it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| The tab's long-press menu (JupyterLab) | On screen whenever the document is | A menu that is five items deep |
| The explorer's row only (VS Code, IntelliJ) | Nothing new on the tab | Off screen at one pane: the user has to leave the document to find its row |

## Decision

**Duplicate** and **Delete…** are the fourth and fifth items of the tab's long-press menu, after
Rename…, Save As… and Revert to Checkpoint…, in JupyterLab's order
(`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTab.kt`).

- **Delete asks in the file browser's words.** The prompt is "Are you sure you want to permanently
  delete: %1?" (`extension-api/src/main/java/com/jupydroid/extension/api/DeleteFileDialog.kt`), not docmanager's
  plainer "delete". The delete is permanent, and the explorer already asks it in those words.
- **Unsaved work goes with the file, unasked.** The delete ask is the only question, as in
  `onDelete`. The tab's edits are discarded so that the close does not ask again.
- **The strip closes the tab itself, on top or beneath.** `deleteWidgets` disposes every widget on
  the file. Here a screen beneath the top is not composed to hear its file go, so the strip closes
  it once the delete lands (`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt`,
  `app/src/main/java/com/jupydroid/app/ui/shell/TabDelete.kt`). A notebook's kernel session ends on
  the same `Deleted` announcement that a delete from the explorer makes
  (`app/src/main/java/com/jupydroid/app/domain/model/DeletedDocumentSessions.kt`).
- **The copy is the server's.** `WorkspaceFiles.copy` copies the file as saved beside itself, under
  the `-Copy<n>` name that `nameBeforeCopies` and `freeNameIn` compute by the rootfs rule. It
  refuses a folder (`app/src/main/java/com/jupydroid/app/domain/port/WorkspaceFiles.kt`,
  `app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceFiles.kt`). The copy is one of the
  tree's acts, so it lives on that port
  ([ADR-0101](0101-the-trees-acts-are-a-port-of-their-own.md)).
- **Duplicate opens nothing.** The explorer lists the copy at its next read of the folder, and the
  tab stays where it was (`app/src/main/java/com/jupydroid/app/ui/shell/TabDuplicate.kt`).
- **A refusal says why**, in a "Delete Error" or "Duplicate Error" dialog. The reference leaves a
  refusal to the console.

Measured on SM-X216B, on a fresh install with autosave off:
- `notes.py`, edited, was deleted from its tab. Cancel kept the tab dirty. Delete closed it with no
  save ask, and the file left the disk.
- `kernel.ipynb`, run and then deleted from beneath `other.py`, closed its tab and took its row out
  of Kernel Sessions.
- `other.py` duplicated to `other-Copy1.py` and then `other-Copy2.py`, each byte for byte the same
  as `other.py`, and its tab stayed on top. `other-Copy1.py` duplicated to `other-Copy3.py`.
- `kernel.ipynb` duplicated to `kernel-Copy1.ipynb`, whose cell ran.
- After `other-Copy2.py` was deleted from its tab and the app was killed and relaunched, it stayed
  gone. The other copies opened with their content, and the notebook's copy ran again.

## Consequences

The open document can be removed or copied without leaving it. A JupyterLab on the same workspace
would have named each copy the same way.

What a future reader will be tempted to "fix":

- **Asking to save before a delete.** The file is going away. Saving it first would write a file
  only to delete it, and the reference does not ask.
- **Opening the copy.** Neither reference does. The user duplicated the document they are reading
  and stays on it.
- **Copying what is on screen.** Save As does that
  ([ADR-0099](0099-a-document-is-saved-under-a-new-name-from-its-tab.md)). Duplicate is the
  server's copy of the file as saved.

Not covered, on purpose:

- **Clone**, a second view on one document (`GAP-060`).
- **Show in file browser** on the tab.
- **Duplicating or deleting several explorer rows at once** (`GAP-050`).
