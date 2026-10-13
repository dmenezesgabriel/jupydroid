# JD-037 — A rename refused from the tab says why

Milestone: `M6` · State: in-progress · Depends on: — · Closes: GAP-069

## Outcome

When the disk refuses a rename asked from a document's tab, or the overwrite that follows a taken
name, a dialog says why, where today the dialog closes and the tab is unchanged in silence.

## Demo

On the arm64 tablet (`SM-X216B`): no cell runs, but the second case needs the Space's terminal.

1. Cold start: install, launch, enter `base`.
2. Create `a.py`, and a folder `full` holding a file. Open `a.py`.
3. Long-press the `a.py` tab, rename it to `full`. The overwrite is asked; accept it. A
   "Rename Error" dialog says `full` is a folder with something in it. OK closes it; the tab still
   reads `a.py` and the explorer still lists `a.py` and `full`.
4. Create and open `b.py`. In a terminal, `rm b.py`. Long-press the `b.py` tab and rename it to
   `c.py`. A "Rename Error" dialog says there is nothing at `b.py` to rename.

The item is not `shipped` until this has run on that device, with screenshots and `maestro.log`
committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/filebrowser/src/listing.ts` (`_doRename`), `packages/docmanager/src/dialogs.ts` (`renameFile`), `packages/docregistry/src/context.ts` (`_rename`) | the browser's rename catches every refusal but the user's own cancel and shows `showErrorMessage('Rename Error', error)`; the tab's `context.rename` rethrows to the command, which reaches only the console |
| `vscode` | FULL | `vscode@3f3380c src/vs/workbench/contrib/files/browser/fileActions.ts` (`renameHandler`) | the bulk edit is wrapped and a refusal goes to `notificationService.error(e)` |
| `vscode-jupyter` | ABSENT | — | renaming a notebook is the workbench's |
| `intellij-community` | FULL | `intellij-community@406a27a8da platform/lang-impl/src/com/intellij/refactoring/rename/RenameProcessor.java` | `IncorrectOperationException` becomes `CommonRefactoringUtil.showErrorMessage` titled with the rename |
| `termux-app` | ABSENT | — | renames sessions, never files |
| `winlator` | ABSENT, and silent | `winlator@fb66541 app/app/src/main/java/com/winlator/ContainerFileManagerFragment.java` (`menu_item_rename`) | `file.renameTo(newName)` and its boolean is dropped: the same silence as ours |
| the shipped rootfs | the refusals | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/filemanager.py` (`rename_file`) | 400 cannot rename, 404 nothing there, 409 taken, 500 anything else: each carries its reason |

**Count:** 3 of 6 clients put a refused rename in front of the user; the one that drops it is the
one with no error surface at all. jupyterlab is authoritative for behaviour (`cross-cutting.md`
§8.3), and of its two paths only the browser's reaches a human.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| The refusal surfaced | `packages/filebrowser/src/listing.ts` (`_doRename`: `catch (error) { if (error !== 'File not renamed') showErrorMessage('Rename Error', error) }`) |
| The 409 split | `packages/docmanager/src/dialogs.ts` (`renameFile`: 409 → `shouldOverwrite` → `overwrite`, anything else rethrown) |
| The tab's path | `packages/docregistry/src/context.ts` (`_rename` awaits `contents.rename`, no catch) |

### Ours

| Concern | Files |
|---|---|
| The tab's rename and overwrite | `app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt` (`TabbedDocumentPane`) |
| The refusals | `app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceRepository.kt` (`rename`, `overwrite`) |
| The explorer's answer to the same refusals | `app/src/main/java/com/jupydroid/app/ui/explorer/ExplorerViewModel.kt` (`move`: the error line) |
| The app's error dialog idiom | `app/src/main/java/com/jupydroid/app/ui/launcher/LauncherScreen.kt` ("Error", OK) |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Which jupyterlab path | browser → error dialog; tab → console only | the tab's path is the silence this gap reports | **the browser's**: a dialog titled "Rename Error" |
| Dialog or line | jupyterlab, intellij → dialog; vscode → notification; our explorer → its error line | the strip has no error line, and GAP-137 has no notification store | **a dialog**, as the ask that was refused was one |
| What it says | all → the refusal's own message | — | the repository's message, which names the path and the rule |

## Deviations

None from the browser's path.

## Open decisions

None.

## Surface

| Layer | Files |
|---|---|
| `ui/shell` | `MainAreaTabStrip.kt` - the failure arms and the dialog |
| instrumented tests | `MainAreaTabStripTest` |
| `.maestro/` flow + `scripts/` driver | `jd-037-tab-rename-refusal-t1`, then the demo's |

## Tasks

- [x] T1 — a rename or overwrite refused from the tab opens a "Rename Error" dialog carrying the
  refusal. Instrumented tests, plus a flow on the tablet: overwrite onto a folder with something
  in it, and rename a file removed from a terminal.
- [x] T2 — the demo above on a fresh install on SM-X216B. Then GAP-069 closes in
  `docs/architecture.md` §11, the parity ledger's line naming it is updated, the backlog row is
  marked shipped, and this PRD is deleted.

## Out of scope

- The tab hearing that its file was removed outside the app: `GAP-064`.
- A notification store for refusals: `GAP-137`.
