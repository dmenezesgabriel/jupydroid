# ADR-0093 — A document is saved on demand from the tab strip

Status: accepted
Date: 2026-10-13

## Context

A save was never an act. `DocumentAutosave` writes on a debounce and an interval poll
(`app/src/main/java/com/jupydroid/app/ui/editor/DocumentAutosave.kt`), and the only write asked for
was the close gates' flush (`ui/SaveBeforeCloseGate.kt`, `ui/shell/MainAreaCloseGate.kt`). With
`autosaveEnabled` off an edit reached the disk only when its tab closed. The write itself already
existed - `OpenDocument.saveNow` (`app/src/main/java/com/jupydroid/app/OpenDocuments.kt`), the flush
with ADR-0090's check - and no visible control reached it. That was `GAP-061`.

What the references do:

- **JupyterLab** runs `docmanager:save` - `context.save()` then `createCheckpoint()` - from a
  **visible** toolbar button on a notebook (`createSaveButton`, enabled while the model is writable),
  and from the File menu and `Accel S` for every document. A plain file gets no button. The tab's own
  context menu carries rename, duplicate, delete and clone, not save (jupyterlab@7f0e6906
  `packages/docmanager-extension/src/index.tsx`, `packages/notebook/src/default-toolbar.tsx`,
  `packages/docmanager-extension/schema/plugin.json`).
- **VS Code** puts Save on the **tab's context menu**, shown only while the active editor is dirty
  (`ActiveEditorDirtyContext`), besides the File menu, the palette and a keybinding (vscode@3f3380c6
  `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts`).
- **vscode-jupyter** has no save of its own; it calls the workbench's (vscode-jupyter@f01d80ca
  `src/commands.ts`).
- **IntelliJ** saves documents itself; `SaveAll` is a menu row and a key, and no tab wears a dirty
  mark (intellij-community@406a27a8 `platform/platform-resources/src/idea/PlatformActions.xml`).
- **jupyter_server**, in the shipped rootfs, answers a `PUT api/contents`; the app writes the shared
  disk itself (ADR-0089).

Both references that edit documents and mark them dirty give one document a save act of its own.

## Decision drivers

- A notebook and a plain file are saved the same way: the file editor has no bar of its own.
- What says a document owes a write, and what writes it, should be seen together.
- A gesture nobody is shown is not an act; today's long press on a tab is one step to rename.
- Saving a clean document rewrites the same bytes under a new modification time, which every other
  holder of the file then reads as a change (ADR-0090).
- The strip must not change height or shift its tabs for the control.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A row in the notebook's overflow, as JupyterLab's toolbar | The reference's place for a notebook | The file editor has no bar; it would need a second, invented control |
| The tab's long-press menu, as VS Code | The reference's place for every document | A gesture nobody is shown, and rename becomes two steps |
| One icon at the strip's trailing edge, acting on the top tab | The only chrome a notebook and a file share, beside the dirty dot it clears | Neither reference puts it there |

## Decision

One save icon sits at the tab strip's trailing edge and writes the document on top, through
`OpenDocument.saveNow`, leaving its tab open (`app/src/main/java/com/jupydroid/app/ui/shell/ActiveTabSave.kt`).

- **Live only while the top document is dirty**, VS Code's rule rather than JupyterLab's "while
  writable", so a tap never moves the mtime of a file with nothing to write.
- **Greyed, never removed**, for a clean document and for a top tab that is not a document at all,
  so the strip keeps its shape whichever tab is on top.
- **Fixed behind the scrolling tabs**, as the one-pane Up arrow is fixed ahead of them, in the
  strip's own 34dp height rather than an `IconButton`'s 48dp. Measured on SM-X216B by uiautomator
  dump at 1.5 px/dp, before and after: the strip 51px tall at two panes and 72px at one, each tab
  324px wide, unchanged.
- **It reports nothing of its own.** A write that succeeds turns the dot back into the close; a
  refused one is drawn by the document's own screen, which is composed because its tab is on top -
  `SaveFailureBanner`, the file editor's error line, or ADR-0090's File Changed question.
- **A report of the text already held is no edit.** A field reports its text once more as it
  leaves the screen (`reportCellEdits`, `app/src/main/java/com/jupydroid/app/ui/editor/CellSourceTracking.kt`),
  so a document saved from the strip and then put beneath another tab came back dirty with nothing
  to write. `FileEditorViewModel.edit` and `EditorViewModel.editCell` ignore it
  (`anEditThatChangesNothingLeavesASavedFileClean`, `anEditThatChangesNothingLeavesASavedNotebookClean`).
- **Held by test:** `MainAreaTabSaveTest` for the icon and its enabled rule; `EditorAutosaveTest` and
  `FileEditorAutosaveTest` for the write with autosave off.

## Consequences

With autosave off, an edit is written when the user asks and the document stays open; a file
changed on disk since it was read is asked about at that tap, not written over.

What a future reader will be tempted to "fix":

- **Enabling the icon on a clean document "so it always does something".** That rewrites identical
  bytes under a new mtime, and any other client of the file sees a change that is not one.
- **Hiding the icon when it has nothing to do.** The strip's tabs would shift each time the top
  document's state changed.
- **A snackbar saying "Saved".** The tab's dot already reports it; a second report says the same
  thing twice.

Not covered, on purpose:

- **Saving a tab beneath the top, and save-all.** Only the top tab composes, so a refused save
  beneath it would have no screen to ask on. Save All brings that tab to the top first
  ([ADR-0098](0098-every-open-document-is-saved-from-open-tabs.md)).
- **A checkpoint after the save.** There are none (`GAP-062`).
- **`Accel S`.** No editor reads a hardware key, and there is no keymap to bind one in (`GAP-134`).
- **`renameUntitledFileOnSave`.** A document is named when it is created, so a first save has
  nothing untitled to rename.
- **Save-as.** The tab's long-press menu writes a copy
  ([ADR-0099](0099-a-document-is-saved-under-a-new-name-from-its-tab.md)).
