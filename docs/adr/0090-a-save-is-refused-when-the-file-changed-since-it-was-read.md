# ADR-0090 — A save is refused when the file changed since it was read

Status: accepted
Date: 2026-10-12

## Context

The workspace is the kernel's home directory and the terminal's, so a file open in an editor can be
rewritten by a cell, by a shell command or by a second Jupyter client. A notebook save and a text
file save each wrote whatever the editor held over whatever was there. A cell that appended to its
own notebook, or `echo changed > notes.txt` under an open `notes.txt`, was replaced by the next
autosave, and nothing said so. That was `GAP-065`.

What the references do:

- **JupyterLab** fetches the file's model without content before every save. A different hash, or a
  `last_modified` newer than the client's by more than `lastModifiedCheckMargin` (500 ms), opens a
  **File Changed** dialog with Cancel, Revert and Overwrite. A second conflict while it is up is
  rejected (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`, `_maybeSave`,
  `_raiseConflict`, `_conflictModalIsOpen`).
- **VS Code** hands the write the `mtime` and `etag` (mtime and size) of the read. A newer mtime
  with a different etag compares the bytes: identical bytes are written, anything else throws
  `FILE_MODIFIED_SINCE`, and the notification offers Compare and Overwrite (vscode@3f3380c6
  `src/vs/platform/files/common/fileService.ts`, `validateWriteFile`;
  `src/vs/workbench/contrib/files/browser/editors/textFileSaveErrorHandler.ts`).
- **IntelliJ** asks on a file-system change under an unsaved document: Load File System Changes,
  Keep Memory Changes, Show Difference (intellij-community@406a27a8
  `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/MemoryDiskConflictResolver.java`).
- **jupyter_server**, in the shipped rootfs, writes what it is sent: `save` reads no stamp from the
  request (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filemanager.py`,
  `…/contents/handlers.py`). The check is the client's.

All three references that edit documents refuse the blind write and leave the choice to the user.
None merges.

## Decision drivers

- A caller must not be able to forget the check.
- A save happens on every debounce, so the check must not read the whole file each time.
- The disk is local, and the other writer is often a cell whose write lands within milliseconds of
  the app's own save.
- `Notebook` carries no time on purpose (`app/src/main/java/com/jupydroid/app/domain/model/Notebook.kt`).

## Options considered

1. **JupyterLab's: the client stats, then saves, with a 500 ms margin.** Two calls leave every
   caller to remember the first one, and the margin hides a kernel write that lands within half a
   second of a save.
2. **VS Code's: the write is handed the stamp of the read and refuses.** One call, exact, with the
   identical-bytes pass so a `touch` does not ask.

## Decision

Option 2 for the check, JupyterLab's dialog for the question.

- **The check lives in the write.** `File.writeIfUnchanged`
  (`app/src/main/java/com/jupydroid/app/data/disk/StampedFileWrite.kt`) is handed the stamp the
  caller expects, refuses, or writes through `File.writeAtomically` (ADR-0089) and answers the new
  stamp.
- **The stamp is `FileStamp(modifiedNanos, size)`**, VS Code's etag
  (`app/src/main/java/com/jupydroid/app/domain/model/FileStamp.kt`). It is compared for equality,
  never for order: a file put back from an older copy is a change too.
- **Any difference refuses, with no margin**, unless the file already holds exactly the bytes being
  written. A file that is gone is written back, as `_maybeSave` saves on a 404.
- **The stamp travels beside the model.** `readStampedNotebook` answers `StampedNotebook`,
  `readStampedText` answers `StampedText`, and `saveNotebook(notebook, expected)` and
  `writeText(path, text, expected)` answer the stamp of the save after this one. `readNotebook` and
  `readText` stay for the readers that never write back.
- **The refusal is a type**, `FileChangedOnDiskException` in `domain/port/`, so a caller asks the
  user for exactly this and reports every other failure as a failure.
- **The user is asked with JupyterLab's dialog and its words** (`ui/FileChangedDialog.kt`): Cancel
  leaves both versions and the next save asks again, Revert reads the disk's version into the
  editor and drops the edit owed, Overwrite saves with no stamp. The notebook editor and the file
  editor share it (`ui/editor/NotebookSaveConflict.kt`, `ui/fileeditor/FileSaveConflict.kt`).
- **The conflict is a state, not an event.** A second refusal while the dialog is up sets what is
  already set, which is what `_conflictModalIsOpen` is for.
- **A refused save keeps the document open.** The close gate treats it as a save still owed, with
  the dialog over the document.

## Consequences

A cell, a shell command or a second client that rewrites an open document is never replaced without
the user being asked. A run whose cell rewrites its own notebook now ends in a question, because the
run's save is the first to find the change.

What a future reader will be tempted to "fix":

- **Adding JupyterLab's margin.** It exists for filesystems that report inconsistent times. Here it
  would let through exactly the write this decision is for.
- **Dropping the identical-bytes pass.** Without it a `touch`, or a tool that rewrites a file with
  the same content, raises a question with nothing to choose between.
- **Giving workspace replace a stamp.** It writes notebooks that are not open, and already refuses a
  file whose source is not the one it matched
  (`app/src/main/java/com/jupydroid/app/ui/shell/WorkspaceReplace.kt`, `sourceOnDisk`). A refusal
  thrown part-way through a replace-all would be a second failure with nowhere to land.

Not covered, on purpose:

- **Noticing a change before a save is owed.** A re-read asked for outside the dialog is
  ADR-0096's reload; one made by itself, on a watcher, is still `GAP-064`.
- **A save made as the screen is torn down is refused silently.** `onCleared` has no screen left to
  ask on, so the disk's version stays and the edit owed is dropped. The close gate asks first.
- **The check and the rename are two steps.** A writer landing between them is still replaced, as it
  is in both references. Closing that needs a lock every writer honours, and the kernel is not ours
  to make honour one.
- **A diff between the two versions.** VS Code's Compare and IntelliJ's Show Difference need a diff
  view, and none exists here.
