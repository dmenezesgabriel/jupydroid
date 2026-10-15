# JD-043 — A document is deleted or duplicated from its own tab

Milestone: `M6` · State: in-progress · Depends on: JD-036, JD-041 · Closes: GAP-059

## Outcome

A long press on a notebook's or a file's tab offers **Duplicate** and **Delete…** beside Rename…,
Save As… and Revert to Checkpoint…, so the open document can be copied beside itself or removed
without finding its row in the explorer.

## Demo

On the arm64 tablet (`SM-X216B`) - a deleted notebook's kernel leaving the Running panel needs a
kernel.

1. Fresh install, launch, enter `base`. Settings → Autosave off.
2. Open `notes.py`, type a line (dirty). Long-press its tab: **Rename…**, **Save As…**, **Revert to
   Checkpoint…**, **Duplicate**, **Delete…**.
3. Delete… → "Are you sure you want to permanently delete: notes.py?" → Cancel: the tab is still
   there, still dirty. Delete… → Delete: the tab closes without asking to save, and the explorer no
   longer lists `notes.py`.
4. Open `target.ipynb`, run a cell; open a second document on top. Long-press `target.ipynb`'s
   background tab → Delete… → Delete: its tab closes, and the Running panel no longer lists its
   kernel.
5. Open `other.py`, Duplicate from its tab: `other-Copy1.py` appears in the explorer beside it with
   `other.py`'s saved content, and the tab stays on `other.py`. Duplicate again → `other-Copy2.py`.
   Duplicate `other-Copy1.py` → `other-Copy3.py` (the server strips `-Copy1` first).
6. Duplicate a notebook → `name-Copy1.ipynb`, which opens and its cell runs.
7. Kill and relaunch: the deleted files stay gone, and the copies open with their content.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/docmanager-extension/schema/plugin.json` (`context`, `[data-type="document-title"]`: rename 20, duplicate 21, delete 22, clone 23, show-in-file-browser 24), `packages/docmanager-extension/src/index.tsx` (`CommandIDs.duplicate`, `CommandIDs.del`, `CommandIDs.deleteFile`), `packages/docmanager/src/manager.ts` (`duplicate`, `deleteFile`), `packages/docmanager/src/widgetmanager.ts` (`deleteWidgets`, `onDelete`) | the tab's context menu acts on the document's path: duplicate = `contents.copy(path, dirname)`, opens nothing; delete asks, then `stopIfNeeded` + `contents.delete` + dispose every widget on the context, unasked about unsaved work |
| `vscode` | ABSENT on the tab | `src/vs/workbench/browser/parts/editor/editor.contribution.ts`, `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts` (`MenuId.EditorTitleContext`) | the tab menu holds close, split, pin, Save/Save As, copy path, reveal in explorer; delete and copy live on the explorer's row |
| `vscode-jupyter` | ABSENT | — | the workbench's tab |
| `intellij-community` | ABSENT on the tab | `platform/platform-resources/src/idea/PlatformActions.xml` (`EditorTabPopupMenu`), `LangActions.xml` (`RenameFile` → `EditorTabPopupMenuEx`) | the tab offers Rename File but neither delete nor copy; both are the Project view's |
| `termux-app` | ABSENT | — | no documents |
| `winlator` | ABSENT | — | a file manager, no document tabs |
| the shipped rootfs | FULL (server) | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/manager.py` (`copy`, `increment_filename`, `copy_pat`), `filemanager.py` (`delete_file`) | `copy` into the source's own folder strips `-Copy\d*\.` to `.`, then counts `insert="-Copy"` past what exists; a directory is a 400 `Can't copy directories` |

Pinned SHAs: jupyterlab@7f0e690, vscode@3f3380c, vscode-jupyter@f01d80c,
intellij-community@406a27a8da, termux-app@3df69d1, winlator@fb66541.

**Count:** 1 of 7 offers both on the document's tab, and it is the one this app is modelled on;
the server half every Jupyter client calls is in the rootfs. The two desktop IDEs keep the file acts
on the tree. On a phone at one pane the tree is not on screen beside the document, which is the
case for following JupyterLab here.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| UI entry point | `docmanager-extension/schema/plugin.json` `context`; labels `Duplicate %1`, `Delete %1` (`fileType` of the widget) |
| Delete's ask | `index.tsx` `CommandIDs.del`: title `Delete`, body `Are you sure you want to delete %1` (`context.path`), Cancel and a warn `Delete` |
| Delete itself | `manager.ts` `deleteFile`: `sessions.stopIfNeeded(path)` → `contents.delete(path)` → `deleteWidgets(context)` for every context on the path |
| Unsaved work | `widgetmanager.ts` `onDelete` = `widget.dispose()`: no `saveBeforeClose` ask |
| Duplicate | `manager.ts` `duplicate` = `contents.copy(path, PathExt.dirname(path))`; the command opens nothing |
| Naming | the server's `copy` (rootfs `manager.py`) |

### the shipped rootfs

| Concern | Files |
|---|---|
| Copy naming | `manager.py` `copy_pat = re.compile(r"\-Copy\d*\.")`, `copy` → `increment_filename(name, to_path, insert="-Copy")`; the suffix is the last dot for `.ipynb`, the first dot otherwise |
| Refusals | `copy`: `400 Can't copy directories`; a missing source is the `get`'s 404 |
| Checkpoints | `copy` is `get` + `save`: the copy gets no checkpoint of its own |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the acts live | jupyterlab → the tab; vscode, intellij → the tree only | the tree is off screen at one pane | the tab's long-press menu, after Revert to Checkpoint… |
| Delete's wording | jupyterlab docmanager → "Are you sure you want to delete %1"; filebrowser → "…permanently delete: %1?" | one app, two wordings | `DeleteFileDialog`, the browser's: our delete is permanent, as it says, and one question reads one way wherever it is asked |
| Unsaved work on delete | jupyterlab → disposed unasked | the delete ask is already the question | discard and close, unasked beyond the delete ask |
| A background tab | jupyterlab → `deleteWidgets` disposes every widget, on screen or not | ours closes a screen on `gone` only while composed | the strip closes the tab itself after the delete lands |
| Copy name | the server's `-Copy<n>` series | — | the server's, through `incrementedFileName` |
| After Duplicate | jupyterlab → opens nothing | — | opens nothing; the explorer lists it |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Tab menu | `ui/shell/MainAreaTab.kt` (Rename…, Save As…, Revert to Checkpoint…) | jupyterlab `plugin.json` context | absent: no Delete, no Duplicate |
| Delete op | `WorkspaceRepository.delete`, `DiskWorkspaceRepository` | jupyterlab `deleteFile` | complete below the UI |
| Session stop | `domain/model/DeletedDocumentSessions.kt` | `stopIfNeeded` | complete |
| Ask | `ui/DeleteFileDialog.kt` | `CommandIDs.del` | complete, reused |
| Copy op | — | rootfs `copy` | absent: no `copy` on the port |
| Copy naming | `domain/model/NotebookFileName.kt` `incrementedFileName`, `data/disk/WorkspaceRoot.kt` `freeNameIn` | rootfs `increment_filename` | present; `copy_pat` stripping absent |

## Deviations

- Delete asks in the browser's words, not docmanager's (above).
- The strip closes a deleted tab itself instead of waiting for the screen's `gone`, which a
  background tab never composes to see.

## Open decisions

None open: each axis above is settled by the reference it names.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `NotebookFileName.kt` (the `-Copy` name, T2) |
| `domain/port` | `WorkspaceRepository.copy` (T2) |
| `data/disk` | `DiskWorkspaceRepository.copy` (T2) |
| `data/fake` | `FakeWorkspaceRepository.copy` (T2) |
| `ui/shell` | `MainAreaTab.kt`, `MainAreaTabStrip.kt`, `TabDelete.kt` (T1), `TabDuplicate.kt` (T2) |
| `di/AppContainer` | nothing |
| unit tests | the port contract for `copy`, the name rule (T2) |
| instrumented tests | `TabDeleteTest`, `TabDuplicateTest`, the strip's menu |
| `.maestro/` flow + `scripts/` driver | `jd-043-tab-delete-t1-*`, `jd-043-tab-duplicate-t2-*`, `jd-043-tab-demo-*` |

## Tasks

- [x] T1 — Delete… on the tab's menu: asks, deletes, closes the tab (background too), and says why a refusal failed
- [ ] T2 — `WorkspaceRepository.copy` with the server's `-Copy<n>` naming (Disk, Fake, contract test), and Duplicate on the tab's menu
- [ ] T3 — the demo on a fresh install; ADR, ledger rows, ship

## Out of scope

- `docmanager:clone`, a second view on one document: GAP-060.
- `docmanager:show-in-file-browser` on the tab: not in GAP-059.
- Duplicate or delete of several explorer rows at once: GAP-050.
