# ADR-0096 — An open document is reloaded from disk on demand, from the tab strip

Status: accepted
Date: 2026-10-13

## Context

An open notebook or file was read once and never again. The workspace is the kernel's home and the
terminal's, so a cell, a shell command or a second Jupyter client rewriting an open file left its tab
showing what it opened until the next save was refused (ADR-0090). The re-read already existed, as
File Changed's Revert (`ui/editor/NotebookSaveConflict.kt`, `ui/fileeditor/FileSaveConflict.kt`),
and nothing else reached it. That was `GAP-064`.

What the references do:

- **JupyterLab**'s `docmanager:reload` is enabled for any widget with a context. On a dirty model it
  asks "Reload %1 from Disk", with Cancel and a **warn** Reload, then calls `context.revert()`, which
  re-reads the file and leaves the model clean. It sits in the File menu beside rename
  (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx`, `schema/plugin.json`,
  `packages/docregistry/src/context.ts` `_revert`).
- **VS Code**'s Revert File is enabled only while the editor is dirty, because a clean model is
  re-read by a watcher on any file change, "never reload dirty models" (vscode@3f3380c6
  `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts`,
  `src/vs/workbench/services/textfile/common/textFileEditorModelManager.ts`).
- **IntelliJ** re-reads a document with no unsaved changes on a VFS change, and offers Synchronize as
  a Refresh icon (intellij-community@406a27a8da
  `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileDocumentManagerImpl.java`,
  `platform/platform-resources/src/idea/PlatformActions.xml`).
- **vscode-jupyter**, **termux-app** and **winlator** have no reload of their own; the shipped
  rootfs's `ContentsHandler.get` is the plain read a reload would make.

## Decision drivers

- A notebook and a plain file are reloaded the same way, and the file editor has no bar of its own.
- A reload throws away unsaved work, so that work must not go without a question.
- The edit a reload drops must not be written after it: the bytes would be the ones just read, under
  a new modification time that every other holder of the file reads as a change (ADR-0090).
- No watcher exists, and a clean document is exactly the one that goes stale.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A row in the notebook's overflow, as JupyterLab's File menu | The reference's place | The file editor has no bar; it would need a second home |
| The tab's long-press menu | Both documents share it | A gesture nobody is shown, and rename becomes two steps |
| A Refresh icon beside the strip's Save, acting on the top tab | The chrome both documents share, beside the act it undoes | Neither web reference puts it there; the scrolling tabs lose one more slot |

## Decision

A Refresh icon sits at the tab strip's trailing edge, before Save, and reloads the document on top
through `OpenDocument.reloadFromDisk` (`app/src/main/java/com/jupydroid/app/ui/shell/ActiveTabReload.kt`).

- **Live for any document**, JupyterLab's rule rather than VS Code's: VS Code greys it on a clean
  editor because its watcher already re-reads one, and here nothing does.
- **A dirty document is asked first**, with JupyterLab's dialog and words: "Reload Notebook from
  Disk" or "Reload File from Disk", Cancel, and Reload in the warning colour. The type word is
  "Notebook" for an `.ipynb` and "File" for anything else, because no file-type registry names more.
- **One re-read.** The reload is File Changed's Revert, and both now drop the write the document
  owed before reading (`DocumentAutosave.cancelPending`), so a debounce armed by the dropped edit
  writes nothing afterwards (`aReloadDropsTheWriteTheEditOwed`, in `EditorReloadTest` and
  `FileEditorReloadTest`).
- **Greyed, never removed**, for a top tab that is not a document, so the strip keeps its shape.
  The trailing slot's size is shared with Save (`TRAILING_SLOT_WIDTH`, `TRAILING_ICON`).
- **The strip keeps its height and the tabs pay the slot.** Measured on SM-X216B by uiautomator
  dump at 1.5 px/dp, with the icon in place: the strip 51px tall at two panes and 72px at one, as
  ADR-0093 measured it, the reload slot 60px wide beside Save's, and the scrolling tabs 40dp
  narrower than before.
- **Held by test:** `MainAreaTabReloadTest` for the icon, its reach and the question.

## Consequences

A file a cell or a terminal rewrote is one tap from its tab showing it, and unsaved work is never
dropped without being asked.

What a future reader will be tempted to "fix":

- **Greying the icon on a clean document, as VS Code does.** Without a watcher that is the one
  document that needs it.
- **Reading the file without cancelling the owed write.** The debounce then rewrites the reloaded
  bytes under a new mtime, and a second client sees a change nobody made.

Not covered, on purpose:

- **A watcher that re-reads a clean document by itself.** That needs an observer over a directory
  the kernel writes under proot, and a policy for every change it reports; ADR-0097 is that
  watcher, and it keeps this icon live.
- **Reloading a tab beneath the top.** Only the top tab composes, as ADR-0093 says of Save.
- **Checkpoints to reload to** (`GAP-062`).
