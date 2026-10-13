# ADR-0095 — A tab is keyed by its opening, not its path

Status: accepted
Date: 2026-10-13

## Context

A document tab's `NavKey` was the path it opened under: `Editor("a.ipynb")`,
`FileEditor("a.py")`. A rename cannot re-key the entry, because Navigation3 keys an entry's
`ViewModelStore` by `NavEntry.contentKey`, the key's `toString()` by default, and a new key is a new
store: the editor's state, unsaved work included, would be dropped. So a tab renamed to `b.ipynb`
stayed `Editor("a.ipynb")`, and `openInMainArea` revealed by key equality. A file recreated at
`a.ipynb` while that tab lived could never open; every tap revealed the renamed tab instead
(`GAP-141`). And once two documents had opened under one path, `OpenDocuments.openedUnder(path)`
could not tell which a tab's key meant.

What the references do:

- **JupyterLab** gives every main-area widget an opaque id minted per open
  (jupyterlab@7f0e6906 `packages/apputils/src/mainareawidget.ts`, `DOMUtils.createDomID()`, an
  `id-<uuid4>`), and `openOrReveal` finds a widget through `_contextsForPath`, which matches a
  context's **current** path (`packages/docmanager/src/manager.ts`). A renamed widget no longer
  answers to the old name, and a second widget for it is lawful.
- **VS Code** replaces the editor input with one for the target resource in every group, keeping
  index, pin and focus (vscode@3f3380c6
  `src/vs/workbench/services/editor/browser/editorService.ts`, `handleMovedFile`). The old URI names
  no editor afterwards.
- **IntelliJ** keys a tab by its `VirtualFile`, which survives a rename; a file recreated at the old
  path is a new `VirtualFile` and so a new tab (intellij-community@406a27a8
  `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileEditorManagerImpl.kt`, the
  `VirtualFile.PROP_NAME` listener).

All three that draw document tabs let the old name go once the tab has moved off it.

## Decision drivers

- A rename must keep the entry's `ViewModelStore`, so the key cannot change.
- Nothing on our side outlives a path the way a `VirtualFile` does.
- Keys stay readable, and tests deterministic, with no clock or random source to inject.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Re-key on rename, as VS Code | The key always names the live path | A new key is a new `ViewModelStore`: the editor and its unsaved work are rebuilt from disk |
| A file object that outlives the path, as IntelliJ | Identity is the file itself | There is no such object here; a path is all the workspace gives |
| An opaque id per open, as JupyterLab | The key never moves; the reveal matches by live path | A uuid is unique across the app, more than a key needs, and needs a random source |

## Decision

JupyterLab's model, narrowed. A key carries the path it opened under and an `opening` ordinal:
`Editor(notebookId, opening)` and `FileEditor(path, opening)`
(`app/src/main/java/com/jupydroid/app/Editor.kt`, `FileEditor.kt`). Together they are a
`DocumentOpening(path, ordinal)` (`app/src/main/java/com/jupydroid/app/OpenDocuments.kt`).

- **The ordinal is the lowest no open tab on that path holds.** A key need only be unique among the
  tabs open on one path at once, since that is what a `ViewModelStore` is keyed against; the first
  open of a path is ordinal 0, so a key without a rename behind it reads as it always did
  (`aNewTabTakesTheLowestOpeningNoOpenTabOnItsPathHolds`).
- **The reveal matches by live path.** `openDocument` reveals the stack entry whose document lives
  at the asked path **now**, else opens a fresh key
  (`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`). A tab whose document has not
  registered yet is matched by the path it opened under
  (`aTabWhoseDocumentHasNotRegisteredIsRevealedByThePathItOpenedUnder`).
- **A tab finds its document by its opening.** `OpenDocument.openedAs` is a `DocumentOpening`, looked
  up by `OpenDocuments.openedAs`, so two documents opened under one path are never confused
  (`eachTabFindsItsOwnDocumentWhenTwoOpenedUnderOnePath`).

Measured on SM-X216B on a fresh install (`d9421fce`): `a.ipynb`, typed into with autosave off, was
renamed to `b.ipynb` from its tab, and a notebook created as `a.ipynb` opened beside it. The strip
read `Launcher, b.ipynb, a.ipynb`; `a.ipynb` showed an empty cell and `b.ipynb` the typed one with
its dot, and the typed line had not reached the disk. The same held for `a.py` → `b.py`, and
tapping `b.ipynb`'s row revealed the renamed tab and left the strip unchanged.

## Consequences

A rename keeps the tab's entry, its ViewModel and its unsaved work, and the old name is free the
moment the rename lands. A tab reaches its document through its `DocumentOpening`
(`documentIn`, `app/src/main/java/com/jupydroid/app/MainAreaTabs.kt`), never through the bare path.

The tests that hold it: `RecreatedPathOpeningTest` (unit: a notebook and a file recreated at a
renamed tab's old name each open a tab of their own, and each tab is revealed by its own name) and
`RecreatedPathTabTest` (instrumented: the recreated file opens beside the renamed tab), with
`OpenDocumentsTest` and `NotebookBackStackTest`.

What a future reader will be tempted to "fix":

- **Re-keying the tab on rename** so the key names the live path. It drops the entry's
  `ViewModelStore`, and with it everything typed since the last save.
- **Revealing by key equality again**, because the ordinal is 0 almost everywhere. It brings back a
  tab that owns a name it has left.
- **A uuid instead of the ordinal.** It is unique across more than a key needs to be, and makes
  every test that builds a key inject a random source.

Not covered, on purpose:

- **A stack restored after a process death** still keys a renamed document by the path it opened
  under, since the ViewModel that knew the live path does not survive it (ADR-0092).
- **A Console attached to a notebook**, whose key and title keep the path the notebook had when it
  was opened. The session beneath it does follow the rename (`KernelSessions.setPath`,
  `app/src/main/java/com/jupydroid/app/domain/port/KernelSessions.kt`).
- **Recent documents** keep the name a document was opened under after it is renamed.
