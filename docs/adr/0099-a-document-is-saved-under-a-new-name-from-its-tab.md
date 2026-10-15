# ADR-0099 — A document is saved under a new name from its tab

Status: accepted
Date: 2026-10-15

## Context

A document could be written only to its own path. `rename` moves the file instead of copying it,
and nothing wrote the document's bytes under a second name. That was `GAP-063`. With autosave off,
the only way to keep an experiment's edits without touching the original was to copy the file by
hand and redo the edits in the copy.

What the references do:

- **JupyterLab** runs `docmanager:save-as` from the File menu (rank 4, `Accel Shift S`). The
  "Save File As…" dialog (`Private.getSavePath`) holds the full path and lets the user edit it. A
  path that is already taken asks "File Overwrite?" (`_maybeOverWrite`). `_finishSaveAs` then
  writes the model as it is, dirty or not, to the new path, and the command's `fileChanged`
  listener closes the old widget and opens the new path. An unchanged path saves in place, and a
  refused write shows `File Save Error for %1` (jupyterlab@7f0e690
  `packages/docmanager-extension/src/index.tsx` `CommandIDs.saveAs`, `schema/plugin.json`,
  `packages/docregistry/src/context.ts` `saveAs`, `_maybeOverWrite`, `_finishSaveAs`).
- **VS Code** offers `workbench.action.files.saveAs` from the File menu and from the tab's context
  menu, beside Save, while the editor is dirty. It asks through the platform's save dialog. The
  editor on the original is replaced, in its place in the group, by one on the target, and the
  original is reverted (vscode@3f3380c `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts`
  `SAVE_FILE_AS_COMMAND_ID`).
- **IntelliJ** has no Save As. A document is saved in place, and a copy is made with Refactor →
  Copy on the file, not from the editor (intellij-community@406a27a8da
  `platform/lang-impl/src/com/intellij/refactoring/copy/CopyFilesOrDirectoriesHandler.java`).
- **vscode-jupyter** has only the workbench's command. **termux-app** and **winlator** have no
  documents. The shipped rootfs's `jupyter_server` sees a save-as as an ordinary
  `PUT api/contents/<new path>`
  (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/handlers.py`), so a save-as
  is the client's job.

The references disagree on what becomes of the tab. JupyterLab closes it and opens the new file.
VS Code replaces the editor in place.

## Decision drivers

- What is on screen, unsaved edits included, reaches a second file in one act, and the original
  keeps what was last saved.
- A file that already exists is never replaced without asking.
- The act is reachable at phone width without spending tab-strip width on a rare action.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A strip icon beside Save | Visible | Costs tab width at phone size for an act used rarely |
| The tab's long press becomes a menu: Rename…, Save As… (VS Code's tab menu) | No new chrome; the long press already acted on the tab | The menu is one tap deeper than the old direct rename |
| Replace the editor in place on the new path (VS Code) | The tab keeps its position | Needs a path adoption through both editors and the kernel sessions |
| Close the old tab unwritten and open the copy (JupyterLab) | Reuses the close gate's discard and the document opener | The copy's tab opens where a new tab opens, not where the old one was |

## Decision

**A long press on a notebook's or a file's tab opens a menu, Rename… and Save As…**, where it used
to open the rename dialog directly. This is VS Code's tab-menu placement
(`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt`).

- **The prompt** is "Save File As…" over the document's own path, under a Path label, with the
  leaf's stem selected, so typing a bare name keeps the folder and the extension. Every segment
  must pass `isValidWorkspacePath`. An unchanged path dismisses, as the rename dialog does, because
  the strip's save icon already saves in place
  (`app/src/main/java/com/jupydroid/app/ui/shell/TabSaveAs.kt`).
- **Always enabled** on a document tab. A copy of a clean document is still a copy.
- **A taken path** asks `OverwriteFileDialog` ("Overwrite file?"), the same dialog the rename uses.
  Only an answer of Overwrite writes over it. Whether the path is taken is answered from
  `WorkspaceRepository.list` of its folder rather than from a new port operation. That listing
  leaves out only hidden names, which the path rule refuses, so the answer is exact, and a folder
  that does not exist is refused there before anything is written
  (`app/src/main/java/com/jupydroid/app/ui/shell/SaveAsOutcome.kt`).
- **The write** is `OpenDocument.writeCopyTo(path)`, which writes what is on screen to the new path
  and leaves the document's own path, stamp and dirty flag alone. A notebook writes its copy under
  the copy's id and title (`app/src/main/java/com/jupydroid/app/OpenDocuments.kt`).
- **Afterwards**, the old tab drops its edits unwritten (`discardChanges`), closes without the dirty
  ask, and the copy opens through the document opener, so Recents records it. A notebook's copy
  gets a kernel of its own when it runs. This is JupyterLab's `closeFile` and then
  `docmanager:open`.
- **A write the disk refuses** says why in "Save Error", and the tab stays as it was.

Measured on SM-X216B on a fresh install with autosave off: an edited `notes.py` saved as
`notes-copy.py` opened clean with the edit, while `notes.py` on disk kept its seed. A Save As into
a missing folder said "no directory at 'gone' in the workspace" and left the tab. An edited
`target.ipynb` saved over the existing `other.ipynb` asked first, then opened with the edited cell
while `target.ipynb` kept its seed. After a kill and relaunch, both copies opened with what Save As
wrote, and the notebook's copy ran in a kernel.

## Consequences

A document can branch into a new file in one act, and the original stays what it last was on disk.
The tab's long press is now a menu, which leaves room for more file acts on a tab.

What a future reader will be tempted to "fix":

- **Writing the old document's edits to its own file before closing it.** Both references leave
  the original as last saved. The edits went to the copy, and writing them twice would change a
  file the user chose not to change.
- **Saving in place on an unchanged path, as JupyterLab does.** The strip's save icon already does
  that. Here an unchanged path is a dismissal, the rename dialog's rule.
- **Adding a `stat`-style port operation for the taken check.** The folder's listing is exact for
  every path the dialog accepts.

Not covered, on purpose:

- **Delete, duplicate and clone on the tab's menu** (`GAP-059`, `GAP-060`).
- **`Accel Shift S`.** No editor reads a hardware key, and there is no keymap (`GAP-134`).
- **A checkpoint** after the copy is written (`GAP-062`).
