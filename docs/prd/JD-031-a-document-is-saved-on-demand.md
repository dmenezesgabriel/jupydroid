# JD-031 — A document is saved when the user asks

Milestone: `M5` · State: in-progress · Depends on: — · Closes: GAP-061

## Outcome

A notebook or a workspace file with unsaved work can be written to disk by one visible tap, without
closing it - so with autosave off an edit no longer lives only in memory until its tab closes.

## Demo

No step runs a cell, so the emulator is allowed; the tablet (`SM-X216B`) is the device the rest of
M5's evidence was taken on, and the one this is run on.

1. Cold start: install, launch, enter the Space. Through `run-as`, autosave is off and the workspace
   holds `notes.py` and a notebook.
2. Open `notes.py`. The strip's save icon is greyed. Type a line: the tab shows the dirty dot and
   the icon is enabled.
3. Tap the save icon. The dot becomes the ✕ and the icon greys. Through `run-as`, the file holds the
   typed line - the tab is still open.
4. Open the notebook, edit a cell, tap the save icon. Through `run-as`, the `.ipynb` holds the edit.
5. Type a second line into `notes.py`, and through `run-as` rewrite the file beneath the editor. Tap
   the save icon: the app asks which version to keep instead of writing over the disk's.
6. Kill and relaunch. `notes.py` opens holding what step 5's answer kept.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | jupyterlab@7f0e6906 `packages/docmanager-extension/src/index.tsx` (`CommandIDs.save`), `packages/docmanager-extension/schema/plugin.json` (File menu, `Accel S`), `packages/notebook/src/default-toolbar.tsx` (`createSaveButton`) | `context.save()` then `createCheckpoint()`, guarded by a `saveInProgress` set; a **visible** toolbar button on a notebook, enabled while the model is writable; the File menu and `Accel S` for every document. The tab's own context menu (`document-title`) carries rename, duplicate, delete, clone, show-in-browser - **not** save |
| `vscode` | FULL | vscode@3f3380c6 `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts` (`appendEditorTitleContextMenuItem(SAVE_FILE_COMMAND_ID, …, ActiveEditorDirtyContext, …)`) | Save sits on the **tab's context menu**, shown only while the active editor is dirty; also the File menu, the palette, the Open Editors row and a keybinding |
| `vscode-jupyter` | ABSENT of its own | vscode-jupyter@f01d80ca `src/commands.ts` (`'workbench.action.files.save'`) | An extension; it calls the workbench's save |
| `intellij-community` | PARTIAL | intellij-community@406a27a8 `platform/platform-impl/src/com/intellij/ide/actions/SaveDocumentAction.java`, `platform/platform-resources/src/idea/PlatformActions.xml` (`SaveAll`, `SaveDocument`) | Documents save themselves; `SaveAll` is a menu row and a key, `SaveDocument` an action with no control of its own, and no tab wears a dirty mark |
| `termux-app` | ABSENT (not applicable) | — | No document model |
| `winlator` | ABSENT (not applicable) | — | No document model |
| the shipped rootfs | ABSENT (not applicable) | — | `jupyter_server` answers a `PUT api/contents`; ours writes the shared disk itself ([ADR-0089](../adr/0089-a-document-save-writes-beside-the-file-and-renames-over-it.md)) |

**Count:** 2 of 7 give one document a save act of its own, and both that edit documents at all do.

## Implementation inventory

### jupyterlab@7f0e6906

| Concern | Files |
|---|---|
| The command, its enabled rule, the in-progress guard | `packages/docmanager-extension/src/index.tsx` |
| Where it is surfaced | `packages/docmanager-extension/schema/plugin.json`, `packages/notebook-extension/schema/panel.json` (`"save"`, rank 10) |
| The visible button | `packages/notebook/src/default-toolbar.tsx` |
| The dirty mark the save clears | `packages/ui-components/style/icons.css` (`jp-mod-dirty`) |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Save on the tab, gated on dirty | `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts` |

**Where they disagree.** JupyterLab draws a button that is always there on a notebook and gives a
plain file only a menu row and a key; VS Code hides the act in the tab's context menu and shows it
only while there is something to save.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the act lives | jupyterlab → notebook toolbar + File menu; vscode → tab context menu; ours → no bar on the file editor at all, a tab strip over every document | A row in the notebook's overflow leaves the file editor needing a second, invented control. A long-press menu is a gesture nobody is shown, and turns today's one-step rename into two | **One icon at the strip's trailing edge, acting on the active document.** The strip is the only chrome a notebook and a file share and it already wears the dirty dot, so the status and the act sit together - visibility of system status, recognition over recall |
| When it is enabled | jupyterlab → while writable; vscode → while dirty | Saving a clean document rewrites identical bytes and moves its mtime, which every other open client then sees as a change | **While dirty.** Greyed otherwise, and never removed, so the strip's layout does not shift |
| What says it worked | jupyterlab → the dot clears; vscode → the dot clears | A snackbar would be a second report of what the tab already shows | The dot becomes the ✕ and the icon greys |
| What a refused save does | jupyterlab → a dialog; ours → `saveFailure` banner, the file editor's error line, [ADR-0090](../adr/0090-a-save-is-refused-when-the-file-changed-since-it-was-read.md)'s ask | — | Nothing new: the active tab's screen is composed, so all three already draw |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The write | `app/src/main/java/com/jupydroid/app/OpenDocuments.kt` (`OpenDocument.saveNow`), implemented in `ui/editor/NotebookDocument.kt` and `ui/fileeditor/FileEditorViewModel.kt` over `ui/editor/DocumentAutosave.kt` (`flush`) | jupyterlab `context.save()` | None. `EditorAutosaveTest` and `FileEditorAutosaveTest` (`saveNowWritesThenCallsBack`) already hold it with autosave off |
| The act | Only the close gates call it: `ui/SaveBeforeCloseGate.kt`, `ui/shell/MainAreaCloseGate.kt` | jupyterlab `createSaveButton`, vscode's title-context row | **Absent.** Nothing visible saves a document that stays open |
| The dirty mark | `ui/shell/MainAreaTabStrip.kt` (`MainAreaTab`, `DirtyMarker`) | `jp-mod-dirty` | None |
| Failure and conflict reports | `ui/editor/EditorContent.kt` (`SaveFailureBanner`), `ui/fileeditor/FileEditorScreen.kt` (`FileEditorError`, `FileChangedDialog`) | — | None |

Ours is **present but unreachable**: the write exists and no act reaches it.

## Deviations

- **On the strip, not on a toolbar or in a context menu** - against both references, for the reasons
  in the table above.
- **No checkpoint follows the save.** There are none (`GAP-062`).
- **No rename prompt on an untitled first save** (`renameUntitledFileOnSave`): a new document is
  named when it is created.

## Open decisions

- How much of the strip the icon takes at one-pane width. T1 measures the tabs' bounds before and
  after; if a tab falls under its floor where it did not, this PRD is edited before the icon ships.

## Surface

| Layer | Files |
|---|---|
| `domain/`, `data/`, `di/` | none |
| `ui/shell` | `MainAreaTabStrip.kt` (438 lines: the icon goes in a file of its own beside it if it would pass 500) |
| shell (`com.jupydroid.app`) | `OpenDocuments.kt` - `saveNow`'s docstring gains its second caller |
| unit tests | none new: the write is already held with autosave off |
| instrumented tests | a strip test beside `MainAreaTabStripTest` (itself at 491 lines) |
| `.maestro/` flow + `scripts/` driver | `jd-031-*`, seeding autosave off as `scripts/jd-030-rotation-t1-driver.sh` does |

## Tasks

- [x] T1 — The strip's save icon writes the active document: enabled while it is dirty, greyed when
  it is clean or the top tab is not a document, and after a tap the file on disk holds the edit
  while the tab stays open - a file and a notebook, on the tablet.
- [ ] T2 — The demo above, seeded, from a cold start, including the save that asks. Then the ADR,
  the ledger's `docmanager:save` row, `GAP-061` out of `architecture.md`, this file deleted.

## Out of scope

- `Accel S`: no editor reads a hardware key, and there is no keymap to bind one in (`GAP-134`).
- `docmanager:save-all`, and saving a tab beneath the top from the strip.
- `docmanager:save-as` (`GAP-063`), checkpoints (`GAP-062`), reload (`GAP-064`).
- A menu on the tab's long press (`GAP-059`).
