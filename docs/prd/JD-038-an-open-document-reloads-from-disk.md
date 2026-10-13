# JD-038 — An open document reloads from disk

Milestone: `M5` · State: in-progress · Depends on: — · Closes: GAP-064 (the explicit act)

## Outcome

A notebook or a file whose file was rewritten by a cell, a shell command or a second client can be
read back into its open tab with one tap, where today it sits stale until the next save is refused.

## Demo

On the arm64 tablet (`SM-X216B`): a cell does the rewriting.

1. Cold start: install, launch, enter `base`.
2. Create `notes.txt` holding `before`, and a notebook `a.ipynb`. Open both.
3. In `a.ipynb`, run `open('notes.txt', 'w').write('after')`. Switch to the `notes.txt` tab: it
   still reads `before`. Tap the strip's Reload icon: it reads `after`, with no dot.
4. Type into `notes.txt` so it shows the dot, then tap Reload. A "Reload File from Disk" dialog asks.
   Cancel keeps the typed text and the dot. Tap Reload again and accept: it reads `after`, with no dot.
5. Create and open `b.ipynb`. From `a.ipynb`, run a cell that rewrites `b.ipynb` with one new cell
   (`json.dump`). Switch to `b.ipynb` and tap Reload: its new cell shows.

The item is not `shipped` until this has run on that device, with screenshots and `maestro.log`
committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL (an act) | `jupyterlab@7f0e690 packages/docmanager-extension/src/index.tsx` (`CommandIDs.reload`, `isEnabled`, `fileType`), `schema/plugin.json` (File menu, rank 5), `packages/docregistry/src/context.ts` (`revert`, `_revert`) | `docmanager:reload` is enabled for any widget with a context; on a dirty model it asks "Reload %1 from Disk" (Cancel, warn Reload); then `context.revert()` re-reads and reinitialises the model |
| `vscode` | FULL (an act, plus a watcher) | `vscode@3f3380c src/vs/workbench/contrib/files/browser/fileActions.contribution.ts` (`REVERT_FILE_COMMAND_ID`), `src/vs/workbench/services/textfile/common/textFileEditorModelManager.ts` (`onDidFilesChange`) | "Revert File" is enabled only while the editor is dirty; a clean model is re-read on a file-change event ("never reload dirty models") |
| `vscode-jupyter` | ABSENT | `vscode-jupyter@f01d80c src/commands.ts` | no reload of its own; the workbench's revert applies to notebooks |
| `intellij-community` | FULL (a watcher, plus a refresh act) | `intellij-community@406a27a8da platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileDocumentManagerImpl.java` (`contentsChanged`, `reloadFromDisk`), `platform/platform-impl/src/com/intellij/ide/actions/SynchronizeAction.java`, `MemoryDiskConflictResolver.java` | a VFS change re-reads an unsaved-free document; Synchronize (Refresh icon) saves all and refreshes the VFS, which re-reads; a dirty document asks Load / Keep |
| `termux-app` | ABSENT | — | edits no documents |
| `winlator` | ABSENT | — | edits no documents; its file manager's `refreshContent` re-lists a folder |
| the shipped rootfs | the read | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/handlers.py` (`ContentsHandler.get`) | a reload is the client's; the server answers a plain GET |

**Count:** 3 of 6 clients let an open document re-read its file. Every one that edits documents
does. jupyterlab is authoritative for behaviour (`cross-cutting.md` §8.3) and offers the act;
the two desktop IDEs add a watcher on top of it.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| The act and its guard | `packages/docmanager-extension/src/index.tsx` (`CommandIDs.reload`: dirty → `showDialog` Cancel / `warnButton('Reload')`, then `context.revert()`) |
| Where it is offered | `packages/docmanager-extension/schema/plugin.json` (File menu, beside rename and restore-checkpoint) |
| The re-read | `packages/docregistry/src/context.ts` (`_revert`: `contents.get`, `model.fromJSON` / `fromString`, `dirty = false`) |

### Ours

| Concern | Files |
|---|---|
| The re-read, today reachable only from File Changed's Revert | `app/src/main/java/com/jupydroid/app/ui/editor/NotebookSaveConflict.kt` (`revert`, `revertedTo`), `app/src/main/java/com/jupydroid/app/ui/fileeditor/FileSaveConflict.kt` (`revert`) |
| What the strip reaches a document through | `app/src/main/java/com/jupydroid/app/OpenDocuments.kt` (`OpenDocument`) |
| The strip's trailing control | `app/src/main/java/com/jupydroid/app/ui/shell/ActiveTabSave.kt` |
| The owed write a reload must drop | `app/src/main/java/com/jupydroid/app/ui/editor/DocumentAutosave.kt` (`cancelPending`) |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Act or watcher | jupyterlab → act; vscode, intellij → watcher plus act | a watcher needs an observer on a directory the kernel writes under proot, and a policy for every change it reports | **the act**, jupyterlab's; the watcher stays recorded as the rest of GAP-064 |
| When it is offered | jupyterlab → any document; vscode → only when dirty | vscode's rule exists because its watcher already re-reads a clean model, and without one a clean document is exactly the stale case | **any document**, jupyterlab's |
| Dirty guard | jupyterlab → Cancel / warn Reload; intellij → Load / Keep | — | jupyterlab's words: "Reload Notebook from Disk" or "Reload File from Disk", "Are you sure you want to reload the notebook from the disk?" |
| Where | jupyterlab → File menu; vscode → File menu and Open Editors; intellij → a Refresh icon | the file editor has no bar, and the strip is the chrome both documents share (ADR-0093) | **a Refresh icon on the strip**, beside Save, acting on the top tab |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| The re-read | `NotebookSaveConflict.revert`, `FileSaveConflict.revert` | `jupyterlab@7f0e690 packages/docregistry/src/context.ts` (`_revert`) | present but unreachable outside a refused save |
| Dropping the owed write | none: a debounce armed before a revert still writes after it | the model's reset clears `dirty`, and `SaveHandler` saves only a dirty model | absent: `persist` writes whether or not the document is dirty |
| The act | none | `jupyterlab@7f0e690 packages/docmanager-extension/src/index.tsx` (`CommandIDs.reload`) | absent |

## Deviations

- **The type word is "Notebook" or "File".** jupyterlab takes the first registered file type's
  `displayName`; ours has no file-type registry and the editor is chosen by `.ipynb` alone.

## Open decisions

None.

## Surface

| Layer | Files |
|---|---|
| `OpenDocuments.kt` | `OpenDocument.reloadFromDisk` |
| `ui/editor` | `NotebookDocument`, `NotebookSaveConflict` (drops the owed write) |
| `ui/fileeditor` | `FileEditorViewModel`, `FileSaveConflict` (the same) |
| `ui/shell` | `ActiveTabReload.kt`, its call site in `MainAreaTabStrip.kt` |
| unit tests | `EditorReloadTest`, `FileEditorReloadTest` |
| instrumented tests | `MainAreaTabReloadTest` |
| `.maestro/` flow + `scripts/` driver | `jd-038-tab-reload-t1`, then the demo's |

## Tasks

- [x] T1 — the strip's Reload icon re-reads the top document, asking first when it is dirty, and a
  reload drops the write it owed. Unit and instrumented tests, ADR, plus a flow on the tablet: a
  cell rewrites an open file and an open notebook, and each is reloaded, clean and dirty.
- [ ] T2 — the demo above on a fresh install on SM-X216B. Then GAP-064 narrows to the watcher in
  `docs/architecture.md` §11, the parity ledger's two rows naming it are updated, the backlog row
  is marked shipped, and this PRD is deleted.

## Out of scope

- Re-reading a clean document on its own when its file changes: the watcher half of `GAP-064`.
- Checkpoints, and restoring one: `GAP-062`.
- A diff between the two versions: ADR-0090 records why there is none.
