# AUD-005 — Files, workspace and recents

Milestone: `M0` · State: in-progress · Depends on: `AUD-004` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. What an `AUD-NNN` ships is a
> *measurement* rather than a screen
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), so the
> template's Outcome, Demo, Maestro flow and domain→port→adapter Surface have nothing to say here
> and are replaced by what is examined and the command that reproduces each number. The template's
> two load-bearing sections stay, because they are the whole point: a row per reference, and a gap
> analysis against our own code.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, and the code comments — nothing else
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **files, workspace and recents** — taken through four questions at once, never four
sweeps: what the reference offers, what we built, where we differ and *why*, and whether the code
on that path is sound in growth, branching, failure handling and coupling.

The area is the workspace **as files**, at three scales: **the tree** (the explorer listing it,
navigating it, creating, renaming and deleting in it), **a document's file lifecycle** (opening
one, saving it, autosaving it, renaming or deleting it while it is open) and **recents** (the
record of what was opened). The partition hands it three packages' command ids whole —
`filebrowser-extension`, `docmanager-extension` (persistence included: save, save-as, save-all,
autosave, checkpoint restore and reload are how a file's state reaches disk, and no other slice
names them) and `docmanager`, which declares `docmanager:recents` itself — plus two carried out of
`fileeditor-extension`: `fileeditor:create-new` and `fileeditor:create-new-markdown-file`, which
create files rather than edit one.

The surface with no command ids is as large:

- **The browser's settings** (`filebrowser-extension/schema/browser.json`): eighteen — sorting
  (`sortNotebooksFirst`, `sortFileNamesNaturally`), columns (`showLastModifiedColumn`,
  `showFileSizeColumn`, `showDateCreatedColumn`), `showHiddenFiles`, `showFileFilter`/
  `useFuzzyFilter`/`filterDirectories`/`clearFilterOnNavigation`, `breadcrumbs`, `showFullPath`,
  `singleClickNavigation`, `navigateToCurrentDirectory`, `showFileCheckboxes`, and the uploader's
  `allowFileUploads`/`autoOpenUploads`/`maxAutoOpenSizeMB`. Several are surfaced by
  `filebrowser:toggle-*` ids.
- **The document manager's settings** (`docmanager-extension/schema/plugin.json`): seven —
  `autosave`, `autosaveInterval`, `confirmClosingDocument`, `defaultViewers`,
  `lastModifiedCheckMargin`, `maxNumberRecents`, `renameUntitledFileOnSave`.
- **The machinery under the ids.** `Contents.fileChanged` and the open document following its
  path (`docregistry/src/context.ts`), `SaveHandler` (`docmanager/src/savehandler.ts`), the
  overwrite dialog on a 409 (`docmanager/src/dialogs.ts`), `RecentsManager`'s validation
  (`docmanager/src/recents.ts`), the uploader (`filebrowser/src/upload.ts`), breadcrumbs
  (`filebrowser/src/crumbs.ts`).
- **VS Code's explorer**, the presentation reference: `contrib/files/browser/` — `fileCommands`,
  `fileActions`, `explorerService`, `views/` — and `workbench.action.openRecent`
  (`browser/actions/windowActions.ts:290`).

**Carried in.** `AUD-001` left three `NotebookRepository` operations here that no production file
names — `renameNotebook`, `overwriteNotebook`, `deleteNotebook` (`read_port_surface.py --unused`) —
because renaming and deleting go through `WorkspaceRepository`, which owns the tree. This slice
decides whether the port keeps them.

**Neighbours, not this slice's.** `running-extension` and the running panel are `AUD-006`'s;
`filemenu:*` and the rest of the chrome are `AUD-009`'s; the other `fileeditor-extension` ids are
`AUD-008`'s and `AUD-009`'s; export (`ADR-0051`) is `AUD-008`'s format question, though the
`DocumentSharer` it leaves through is weighed here against `filebrowser:download`.

## The surface, produced

Never stored — ADR-0067's rule.

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package filebrowser-extension   # 38 ids
python3 scripts/read_reference_commands.py --jupyterlab-package docmanager-extension    # 17 ids
python3 scripts/read_reference_commands.py --jupyterlab-package docmanager              # 1: docmanager:recents
python3 scripts/read_reference_commands.py --jupyterlab-package fileeditor-extension    # 31; create-new, create-new-markdown-file

# the settings - which have no ids
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/filebrowser-extension/schema/browser.json'))['properties']))"
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/docmanager-extension/schema/plugin.json'))['properties']))"

# vscode's half is workbench code rather than a manifest
ls reference/research_repos/vscode/src/vs/workbench/contrib/files/browser/
grep -n "ID = '\|COMMAND_ID = '" reference/research_repos/vscode/src/vs/workbench/contrib/files/browser/fileConstants.ts
grep -n "openRecent" reference/research_repos/vscode/src/vs/workbench/browser/actions/windowActions.ts

# ours
python3 scripts/read_port_surface.py            # WorkspaceRepository 11, RecentDocumentsRepository 4,
                                                # ExplorerSettingsRepository 2, NotebookRepository 6, DocumentSharer 1
python3 scripts/read_port_surface.py --unused   # the three NotebookRepository operations above
```

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/filebrowser/src/{browser,listing,model,crumbs,upload}.ts`, `packages/docmanager/src/{manager,dialogs,savehandler,recents,widgetmanager}.ts`, `packages/docregistry/src/context.ts` | One `DirListing` whose contents are replaced on navigation; every file act goes through the server's Contents API; an open document is a `Context` that follows its path on `fileChanged` |
| `vscode` | FULL — presentation | `src/vs/workbench/contrib/files/browser/` (`fileCommands`, `fileActions`, `explorerService`, `views/`), `browser/actions/windowActions.ts` | A tree, not a replaced listing; copy/cut/paste, compare, reveal, copy-path; save/save-as/revert per editor; recents as a quick pick |
| `winlator` | PARTIAL | `com/winlator/BaseFileManagerFragment.java`, `core/FileUtils.java` | An Android file manager over a container's tree — paste, a recursive delete we decline, the symlink guard we copy |
| `intellij-community` | PARTIAL | `platform/lang-impl/src/com/intellij/ide/projectView/impl/` | A project tree; no Jupyter-specific file surface |
| `vscode-jupyter` | ABSENT for the tree | — | Its file-shaped contributions are export/import (`jupyter.export*`, `jupyter.importnotebookfile`) — `AUD-008`'s |
| `termux-app` | ABSENT | — | A terminal emulator has no file browser |
| the shipped rootfs | the backend | `jupyter_server/services/contents/filemanager.py`, `manager.py` | The Contents API our `WorkspaceRepository` mirrors: `newUntitled`'s names, the 409 on rename, the refused non-empty directory, `allow_hidden` |

**Count:** 2 of 7 implement the area whole, 2 in part; the rootfs is the behaviour we mirror rather
than a surface we draw.

## Gap analysis — ours today

Read before any verdict is written. Stated plainly: **present and narrow** — the tree, its
creation/rename/delete and recents exist and are instrumented; most of the browser's affordances
do not.

| Concern | Ours today (file) | State |
|---|---|---|
| Listing and order | `data/disk/WorkspaceListing.kt`, `DiskWorkspaceRepository.kt`, `WorkspaceRoot.kt` | present — directories first, natural order, hidden left out (`allow_hidden`), nothing above the root |
| Navigation | `ui/explorer/ExplorerViewModel.goTo`, `Explorer.kt` (no path in the key) | present — `go-up` is one directory; **no breadcrumbs found** |
| Create | `ui/explorer/ExplorerBar.kt` (`NewMenu`), `ExplorerUiState.NewItem`, `WorkspaceRepository.createFile`/`createFolder` | present — the server's `untitled` naming |
| Rename and overwrite | `ExplorerScreen.kt` row menu, `ui/RenameFileDialog.kt`, `FileNameTakenException`, `WorkspaceRepository.rename`/`overwrite`, the tab strip's long press | present — the 409 → ask → overwrite split |
| Delete | `ExplorerScreen.kt` row menu, `WorkspaceRepository.delete` | present — no trash, a non-empty folder refused |
| Open documents follow the tree | `WorkspaceRepository.changes` (`WorkspaceChange`) | present — `Contents.fileChanged`'s shape |
| Recents | `ui/explorer/{RecentsViewModel,RecentsSection,RecentsActionsMenu}.kt`, `RecentDocumentsRepository` → `DiskRecentDocumentsRepository`, `RecordOpening.kt`, `MAX_RECENTS` | present — record, revalidate, clear; the section's fold persisted (`ExplorerSettings`) |
| Save and autosave | `ui/editor/DocumentAutosave.kt`, `EditorSettings`'s `autosave`/`autosaveInterval` | present — debounce plus `SaveHandler`'s poll |
| Leaving the device | `DocumentSharer` → `IntentDocumentSharer` | adapted shape of `download` — to verdict |
| Copy, cut, paste, duplicate, copy-path, upload, filter, columns, checkpoints, save-as, reload | — | **likely absent** — to be verdicted, not assumed |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **One tree is the root** — the kernel's `HOME`, the server's `root_dir` and the repository's
  ([ADR-0022](../adr/0022-kernels-start-through-sessions-and-one-tree-is-the-root.md)).
- **A file's name is its identity**
  ([ADR-0064](../adr/0064-a-files-name-is-its-identity.md)); **a tab is an ordering**
  ([ADR-0059](../adr/0059-many-documents-live-in-the-stack-and-a-tab-is-an-ordering.md)).
- **An export lands in the workspace and leaves by share**
  ([ADR-0051](../adr/0051-an-export-is-named-by-the-server-and-the-menu-offers-what-was-measured.md)) —
  there is no browser download folder to hand it to.
- **Delete is permanent and refuses a non-empty folder**, stated on `WorkspaceRepository.delete`:
  Android's private files have no trash, and the server sharing the tree refuses it too.
- **A recent does not follow a rename**; it is dropped when it stops resolving — the reference's
  own, stated on `RecentDocumentsRepository.revalidate`.

## Open decisions

- Whether `NotebookRepository` keeps `renameNotebook`, `overwriteNotebook` and `deleteNotebook`
  (carried from `AUD-001`). Decided in T3, with an ADR if the answer is a removal or a kept
  single-use surface.

## Tasks

- [x] T1 — **The instrument and the surface, produced.** `read_ledger_partition.py`'s
      `COMMAND_IN_CELL` is closed to the families a slice has claimed; add `filebrowser` and
      `docmanager`, `PARTITIONED_PACKAGES` gains `filebrowser-extension`, `docmanager-extension`
      and `docmanager`, `CARRIED_IDS` gains `fileeditor:create-new` and
      `fileeditor:create-new-markdown-file` — red tests first in `read_ledger_partition_test.py`
      and `read_ledger_verdicts_test.py`, which shares the pattern. Land the `AUD-005` ledger
      section: the surface paragraphs, the produced counts, the partition rows — no verdicts.
- [ ] T2 — **Verdicts for the tree.** `filebrowser:*` navigation and view ids (`go-up`,
      `go-to-path`, `open-path`, `open-directory`, `refresh`, `show-panel`, `activate`,
      `toggle-*`, `select-all`, the main-area ids) and `browser.json`'s eighteen settings against
      `ExplorerViewModel`/`ExplorerScreen`/`WorkspaceListing`/`ExplorerSettings`; VS Code's
      explorer tree against our replaced listing.
- [ ] T3 — **Verdicts for file operations.** Create, rename, delete, duplicate, copy, cut, paste,
      download, copy-path, copy-download-link, upload, `open-url`, `open-browser-tab`, `shutdown`;
      `docmanager:new-untitled|open|rename|delete|delete-file|duplicate|clone|download|
      open-browser-tab|show-in-file-browser`; the two carried `fileeditor:create-new*`; Winlator's
      file manager; the three `NotebookRepository` operations decided.
- [ ] T4 — **Verdicts for persistence and recents.** `docmanager:save|save-all|save-as|
      toggle-autosave|restore-checkpoint|reload|clear-recents`, `docmanager:recents`, and
      `plugin.json`'s seven settings against `DocumentAutosave`/`EditorSettings`/
      `RecentDocumentsRepository`/`RecentsSection`; VS Code's save/revert and `openRecent`.
- [ ] T5 — **The walk.** Tap → `ExplorerViewModel` → `WorkspaceRepository` → disk → `changes` →
      an open document re-keyed; rename → 409 → the overwrite question → move aside, delete,
      move in; an open → `RecordOpening` → recents → `revalidate`; an edit → `DocumentAutosave`'s
      debounce and poll → write. Growth (a relist per act, recents revalidated per refresh),
      branching, failure (a refused rename, a delete of a non-empty folder, a write that fails),
      coupling (who learns of a move, and from whom).

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair
  it finds becomes a `JD-NNN` with a demo of its own.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's named exception; the
  reproducing command stands in for the device.
- **The neighbours' own areas.** The running panel and `running-extension` (`AUD-006`), export
  formats (`AUD-008`), `filemenu:*`, the tab strip and the rest of the chrome (`AUD-009`), Spaces
  rooting the explorer (`JD-005`, `AUD-010`).
