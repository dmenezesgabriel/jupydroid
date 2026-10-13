# JD-034 — A file recreated at a renamed tab's old name opens in its own tab

Milestone: `M6` · State: open · Depends on: — · Closes: GAP-141

## Outcome

A file or notebook created at the path an open tab was renamed away from opens in a tab of its own,
and the renamed tab keeps its document, its new name and its unsaved work.

## Demo

On the arm64 tablet (`SM-X216B`). Nothing here runs a cell, but every demo of this milestone ran
there, and the tab strip at the tablet's width is the one being judged.

1. Cold start: install, launch, enter `base`.
2. Create `a.ipynb`, open it, and type into its first cell so the tab carries unsaved work.
3. Rename the tab to `b.ipynb` from the strip. The strip reads `b.ipynb`.
4. In the explorer, create a new notebook and rename it to `a.ipynb`, then open it.
5. The strip carries **two** tabs, `b.ipynb` and `a.ipynb`. `a.ipynb` shows an empty notebook, and
   `b.ipynb` shows the typed cell, its dirty dot intact.
6. Repeat 2–5 with `a.py` → `b.py` in the file editor.
7. Tap `b.ipynb` in the explorer: it reveals the renamed tab rather than opening a third.

The item is not `shipped` until this has run on that device, with screenshots and `maestro.log`
committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e6906 packages/apputils/src/mainareawidget.ts` (`this.id = DOMUtils.createDomID()`), `packages/docmanager/src/manager.ts` (`openOrReveal` → `findWidget` → `_contextsForPath`) | a widget's identity is an opaque id minted per open; the reveal matches a context by its **current** path, so a renamed widget no longer answers to the old one |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/workbench/services/editor/browser/editorService.ts` (`handleMovedFile`) | the editor input is **replaced** by one for the target resource, in every group, keeping index, pin and focus; the old URI no longer names any editor |
| `vscode-jupyter` | ABSENT | — | draws no tab of its own; it inherits the workbench's |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileEditorManagerImpl.kt` (the `VirtualFile.PROP_NAME` listener) | a tab is keyed by its `VirtualFile` object, which survives a rename; a file recreated at the old path is a new `VirtualFile`, so it is a new tab |
| `termux-app` | ABSENT | — | no document tab |
| `winlator` | ABSENT | — | no document tab |
| the shipped rootfs | ABSENT | — | no document tab |

**Count:** 3 of 7, and all three that draw document tabs do it. None lets an old name keep owning a
tab after its rename.

## Implementation inventory

### jupyterlab@7f0e6906

| Concern | Files |
|---|---|
| Identity | `packages/apputils/src/mainareawidget.ts`, `packages/apputils/src/domutils.ts` (`createDomID` is `id-<uuid4>`) |
| Reveal | `packages/docmanager/src/manager.ts` (`findWidget`, `openOrReveal`, `_contextsForPath`) |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Rename follows the tab | `src/vs/workbench/services/editor/browser/editorService.ts` (`handleMovedFile`) |

### intellij-community@406a27a8

| Concern | Files |
|---|---|
| Rename follows the tab | `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileEditorManagerImpl.kt` (the `PROP_NAME` branch) |

**The disagreement** is between re-keying the tab (vscode) and keying it by something that never
moves (jupyterlab's id, intellij's file object). Either way, the old path stops owning the tab.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What a tab's key is | vscode → re-key on rename; jupyterlab → opaque id per open; intellij → the file object | Navigation3 keys an entry's `ViewModelStore` by `contentKey`, the key's `toString()` by default, so re-keying drops the editor's state, unsaved work included. There is no file object that outlives a path on our side | **jupyterlab's**: `Editor` and `FileEditor` carry an opaque id minted when a new tab is pushed. The path stays on the key as the one it was opened under |
| What a reveal matches | jupyterlab → a context's live path | — | the stack key whose **live** path is the asked one, else a fresh key |
| How a tab finds its document | ours today → `OpenDocuments.openedUnder(openedAs)`, ambiguous once two documents opened under one path | — | by the key's id: `OpenDocument.openId` |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Key identity | `app/src/main/java/com/jupydroid/app/Editor.kt`, `FileEditor.kt`: the path is the key | `jupyterlab@7f0e6906 packages/apputils/src/mainareawidget.ts` | present and partial: one open per path, forever |
| Reveal | `DocumentOpeners.kt` `openDocument` → `NotebookBackStack.kt` `openInMainArea` (equal key) | `manager.ts` `openOrReveal` | present and wrong after a rename |
| Tab → document | `MainAreaTabs.kt` `documentIn` → `OpenDocuments.openedUnder` | widget owns its context | ambiguous after a rename and a recreate |

## Deviations

None from jupyterlab's model. A process death still restores keys with their opened-under path
(ADR-0092 keeps the retained scope, not process death). That is unchanged here.

## Open decisions

None. The key shape is settled above, and an ADR records it when the item ships.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — (navigation keys live in `app/`) |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/` | `Editor.kt`, `FileEditor.kt`, `DocumentOpeners.kt`, `NotebookBackStack.kt`, `MainAreaTabs.kt`, `OpenDocuments.kt`, `Navigation.kt`, `ui/editor/{EditorViewModel,NotebookDocument}.kt`, `ui/fileeditor/FileEditorViewModel.kt`, `ui/shell/{MainAreaTabStrip,ActiveTabSave,MainAreaCloseGate}.kt` |
| `di/AppContainer` | — |
| unit tests | `DocumentOpenersTest`, `NotebookBackStackTest`, `OpenDocumentsTest` |
| instrumented tests | a sibling of `RenamedTabBeneathTest` |
| `.maestro/` flow + `scripts/` driver | `jd-034-recreated-path-t1`, then the demo's |

## Tasks

- [ ] T1 — a tab's key carries an opaque id per open, and `openDocument` reveals by live path. A file
  recreated at a renamed tab's old name opens its own tab: unit, instrumented, and a flow on the tablet.
- [ ] T2 — the demo above on a fresh install on SM-X216B. Then the ADR, GAP-141 closed in
  `docs/architecture.md` §11, the backlog row shipped, and this PRD deleted.

## Out of scope

- An attached Console keyed by its notebook's path: a session's record that does not follow a rename
  is `GAP-068`.
- Keys restored after a process death (ADR-0092).
