# JD-036 — Deleting a notebook stops the kernel session it was running

Milestone: `M6` · State: in-progress · Depends on: — · Closes: GAP-058

## Outcome

After a notebook with a running kernel is deleted, or replaced by a rename over it, its kernel
session ends: the Running panel no longer lists a row for a file that is gone, whether the
notebook's tab was open or already closed.

## Demo

On the arm64 tablet (`SM-X216B`), because it runs a cell.

1. Cold start: install, launch, enter `base`.
2. Create `a.ipynb`, open it, and run `x = 41`. Sessions and Tabs lists one kernel row, `a.ipynb`.
3. Delete `a.ipynb` from the explorer and confirm. Its tab closes with it, and the kernel row is
   gone.
4. Create `b.ipynb`, run a cell, and close its tab. The kernel row `b.ipynb` is still listed.
5. Delete `b.ipynb` from the explorer. The kernel row is gone.
6. Create `c.ipynb` and run a cell. Create `d.ipynb`, rename it to `c.ipynb` and accept the
   overwrite. No kernel row reads `c.ipynb` until a cell is run in it again.
7. Create `e.ipynb` and `keep.ipynb`, run a cell in each, delete `e.ipynb`. The row `keep.ipynb`
   stays, and a second cell in `keep.ipynb` still sees what the first defined.

The item is not `shipped` until this has run on that device, with screenshots and `maestro.log`
committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e6906 packages/docmanager/src/manager.ts` (`deleteFile`, `overwrite`), `packages/services/src/session/manager.ts` (`stopIfNeeded`) | the document manager stops the session at the path, then deletes the file, then disposes its widgets; `overwrite` deletes its target through the same call |
| `vscode` | ABSENT | `vscode@3f3380c6 src/vs/workbench/services/editor/browser/editorService.ts` (`handleDeletedFile`) | the workbench holds no Jupyter session; what it does on a delete made inside the app is close the file's editors |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca src/kernels/kernelProvider.base.ts` (`onDidCloseNotebookDocument` → `disposeOldKernel`), `src/kernels/kernel.ts` (`dispose`), `src/kernels/jupyter/session/jupyterSession.ts` (`canShutdownSession`) | no delete handler: the kernel is disposed because the workbench closed the notebook, and a remote notebook session is never shut down at all |
| `intellij-community` | ABSENT | — | the community tree has no `api/sessions` client |
| `termux-app` | ABSENT | — | no kernel session |
| `winlator` | ABSENT | — | no kernel session |
| the shipped rootfs | ABSENT (server half), and the verb is there | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/handlers.py` (`ContentsHandler.delete`), `services/sessions/handlers.py` (`SessionHandler.delete`) | deleting contents calls `cm.delete(path)` and never looks at a session; ending one is the client's `DELETE api/sessions/<id>` |

**Count:** 1 of 6 clients ties the stop to the delete, a second reaches the same end through the
editor closing, and the server we ship leaves it to the client. jupyterlab is authoritative for
behaviour and the shipped rootfs for what the server does (`cross-cutting.md` §8.3).

## Implementation inventory

### jupyterlab@7f0e6906

| Concern | Files |
|---|---|
| Where the delete is composed with the stop | `packages/docmanager/src/manager.ts` (`deleteFile`: `stopIfNeeded` → `contents.delete` → `deleteWidgets`) |
| The overwrite's half | `packages/docmanager/src/manager.ts` (`overwrite`: rename aside, `deleteFile(newPath)`, rename into place) |
| Port / protocol | `packages/services/src/session/manager.ts` (`stopIfNeeded`: `listRunning`, filter `value.path === path`, `shutdown` when `matches.length === 1`) |
| Empty / error states | `stopIfNeeded` catches everything: "Always succeed" |
| Edge cases it handles | two sessions on one path (a console opened for a notebook) stop neither |

### vscode-jupyter@f01d80ca

| Concern | Files |
|---|---|
| What ends the kernel | `src/kernels/kernelProvider.base.ts` (`disposeOldKernel(e, 'notebookClosed')`) |
| When it may not | `src/kernels/jupyter/session/jupyterSession.ts` (`canShutdownSession`: never for a live remote kernel, never for a notebook on a remote server) |

### the shipped rootfs

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/contents/handlers.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/sessions/handlers.py
```

**The disagreement:** jupyterlab stops by *path*, whatever is open; vscode-jupyter stops by
*document*, so a kernel whose notebook is not open is out of its reach. Ours keeps a session after
its tab closes (`NotebookKernel.close` disconnects and shuts nothing down), so only the path rule
covers a notebook deleted with its tab closed.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| What names the session | jupyterlab → the path, over the server's list; vscode-jupyter → the open document | a closed notebook's kernel outlives its tab here | **the path**, over `KernelSessions.running()` |
| How many matches | jupyterlab → exactly one, else nothing | ours mints one session per path, an attached Console sharing its notebook's | **exactly one**, kept as it is |
| Where the two are composed | jupyterlab → inside `deleteFile`, which every delete and overwrite goes through | ours has no document manager: three callers reach `WorkspaceRepository` directly (`ExplorerViewModel.delete`, `ExplorerViewModel.answerOverwrite`, the tab strip's overwrite), and an overwrite's target path is derived inside the repository | **one listener on `WorkspaceRepository.changes`**: `Deleted(path)` is already announced for a delete and for an overwrite's target, so one place serves all three and any caller added later |
| Stop before or after | jupyterlab → before the delete | before, a refused delete (a folder with something in it, a disk error) has already cost the kernel | **after**: the listener hears only a delete that happened |
| A refusal | jupyterlab → swallowed | — | swallowed, and the watch goes on to the next deletion |
| Running row refresh | — | `shutdown` emits `ended`, which `RunningPanelViewModel` already relists on | nothing new |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The delete is announced | `data/disk/DiskWorkspaceRepository.kt` (`delete`, and `overwrite` for its target) | `manager.ts` `deleteFile` | complete |
| The open document hears it | `ui/editor/EditorViewModel.kt` (`gone`), `ui/fileeditor/FileEditorViewModel.kt` | `deleteWidgets` | complete |
| Sessions by path, and the stop | `domain/port/KernelSessions.kt` (`running`, `shutdown`) | `session/manager.ts` | complete, both passive on a stopped server |
| A stop lands on the open notebook and the panel | `ui/editor/NotebookKernel.kt` and `ui/shell/RunningPanelViewModel.kt` (`ended`) | — | complete |
| The delete asks for the stop | — | `manager.ts` `deleteFile` | **absent** |

Ours is present and unreachable: every part exists and nothing joins them.

## Deviations

- **The stop follows the delete** rather than preceding it. The reference's order ends a kernel
  for a delete the disk then refuses; ours ends it only for a file that is gone. Stated where the
  listener is.
- **A change made outside the app is not heard.** `WorkspaceRepository.changes` announces what
  this app moved; a `rm` from a terminal or a cell stops nothing, as it closes no document today.
  jupyterlab's `stopIfNeeded` has the same reach: only its own `deleteFile`.

## Open decisions

None.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `DeletedDocumentSessions.kt` - pure Kotlin over two ports, where `KernelSessionRegistry` already is |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/<screen>` | — |
| `di/AppContainer` | `portsOf` starts the watch with a Space's ports, on `applicationScope` |
| unit tests | `DeletedDocumentSessionsTest`, `AppContainerTest` |
| instrumented tests | — (no control is added; the row and the gone state are already drawn) |
| `.maestro/` flow + `scripts/` driver | `jd-036-delete-stops-session-t1`, then the demo's |

## Tasks

- [x] T1 — a deleted path's one session is shut down, from a listener on the workspace's changes
  started with each Space's ports. Unit tests, plus a flow on the tablet: delete a notebook with
  its tab open, one with its tab closed, and one replaced by an overwrite, checking the kernel row
  goes each time and another notebook's row stays.
- [ ] T2 — the demo above on a fresh install on SM-X216B. Then GAP-058 closes in
  `docs/architecture.md` §11, the parity ledger's `filebrowser:delete` and `docmanager:delete`
  rows say the session stops, the backlog row is marked shipped, and this PRD is deleted.

## Out of scope

- Deleting from the document's own tab: `GAP-059`.
- A minted Console's session, whose path is `console-<n>-<uuid>` and never a file's: `GAP-075`.
- A file removed from a terminal or a cell: nothing announces it, and `GAP-064` owns the re-read.
