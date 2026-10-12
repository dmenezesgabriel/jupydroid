# JD-030 — Open documents survive a rotation

Milestone: `M5` · State: in-progress · Depends on: — · Closes: GAP-143 (and the configuration-change half of GAP-142)

## Outcome

Rotating the tablet, or anything else that relaunches the activity, no longer makes the shell forget
what is open: a tab with unsaved work still shows its dot and still asks before it closes, a
workspace replace still edits the open editor instead of the file behind it, and the strip keeps its
order, its renamed titles and its open Console.

## Demo

No step runs a cell, so the emulator is allowed; the tablet (`SM-X216B`) is the device that matters,
because its rotation crosses the two-pane threshold (1280dp landscape, 800dp portrait).

1. Cold start in landscape: install, launch, enter the Space. Through `run-as`, autosave is off and
   the workspace holds `first.py`, `second.py` and a notebook.
2. Open `first.py`, then `second.py`, then the notebook. Type a line into `second.py` and go back to
   the notebook, so `second.py` is a dirty tab beneath the top. Its tab shows the dirty dot.
3. Rotate to portrait and back to landscape. The strip draws the three tabs in the order they were
   opened, and `second.py` still shows its dot.
4. Close `second.py` from the strip. The app asks to save. Cancel: the tab stays.
5. Replace a word across the workspace from the search panel. `first.py`, open and clean, shows the
   replacement in its field.
6. Rename the notebook from its tab, rotate: the tab still carries the new name.
7. Open a Console, rotate: the Console still draws its prompt.
8. Close `second.py` and answer Save. Through `run-as`, the file holds the typed line.

## Who else implements this

A desktop window is never torn down while its documents live, so the desktop references answer a
neighbouring question - what a *reload* restores - and the Android references answer this one.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL (reload, not relaunch) | jupyterlab@7f0e6906 `packages/application/src/layoutrestorer.ts` (`LayoutRestorer`), `packages/docmanager/src/manager.ts` (`_contextsForPath`, `_findContext`) | A resize destroys nothing. Across a page reload the restorer re-runs each tracker's open command, so every widget and its context is **rebuilt** and re-registers as it is built; unsaved edits do not cross |
| `vscode` | PARTIAL (reload, not relaunch) | vscode@3f3380c6 `src/vs/workbench/common/editor/editorGroupModel.ts` (`serialize`, `deserialize`), `src/vs/workbench/browser/layout.ts` (`restoreEditors`), `src/vs/workbench/services/workingCopy/common/workingCopyBackupTracker.ts` | A resize destroys nothing. Across a reload each group's editors, **in order**, are serialized and reopened, and dirty working copies come back from backups |
| `vscode-jupyter` | ABSENT of its own | — | An extension; the workbench owns its editors |
| `intellij-community` | ABSENT (not applicable) | — | A desktop frame; a resize destroys nothing |
| `termux-app` | FULL, by avoidance | termux-app@3df69d1d `app/src/main/AndroidManifest.xml` (`android:configChanges="orientation\|screenSize\|smallestScreenSize\|density\|screenLayout\|keyboard\|keyboardHidden\|navigation"`) | The activity declares it handles the change, so it is never relaunched; sessions live in a service besides |
| `winlator` | FULL, by avoidance | winlator@fb66541b `app/app/src/main/AndroidManifest.xml` (the same `configChanges` list on `MainActivity` and `XServerDisplayActivity`) | Never relaunched for a rotation |
| the shipped rootfs | ABSENT (not applicable) | — | The server has no window |

**Count:** 2 of 2 Android references keep everything across a rotation, both by declining the
relaunch. The two desktop references that restore anything restore the tab **order** and re-register
every document.

## Implementation inventory

### termux-app@3df69d1d, winlator@fb66541b

| Concern | Files |
|---|---|
| Declining the relaunch | the two manifests above |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Tab order as persisted state | `src/vs/workbench/common/editor/editorGroupModel.ts` |

### jupyterlab@7f0e6906

| Concern | Files |
|---|---|
| A registry filled by construction, read by live path | `packages/docmanager/src/manager.ts` |

**Where they disagree.** The Android references decline the relaunch; nothing in them survives one.
The desktop references rebuild every document and let it re-register.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Relaunch or not | termux, winlator: `configChanges`, no relaunch; ours today: relaunch | `configChanges` is a list, and whatever is not on it still relaunches - `uimode` (dark mode, measured: `cmd uimode night no` blanks the Console), locale, font scale, and changes no app can decline. Declining some hides the defect behind the ones that are left | Survive the relaunch. The app already treats it as a fact (`CompositionRootTest`, one container per process), and a holder that is right across a relaunch is right for every cause of one |
| Where the holders live | process (`AppContainer`); the activity's retained scope; a `Bundle` (`rememberSaveable`) | Process scope outlives the documents after the activity finishes. A `Bundle` cannot carry a live ViewModel. The retained scope is the lifetime of the ViewModels the registry indexes | The activity's retained scope: the registry lives exactly as long as what it points at |
| Who re-registers | jupyterlab: each document as it is rebuilt; ours: `init` only | Re-registering from the screen reaches only the tab on top - a tab beneath it does not compose | Nobody re-registers: the registry itself survives |
| Mechanism | a `ViewModel` from `viewModel {}` above `NavDisplay`; Compose `retain {}` | Both keep an object across a configuration change. The `ViewModel` is what the documents themselves use, and is already on the classpath (lifecycle 2.10) | The `ViewModel`, unless T1 measures it does not hold |

## Gap analysis

| Concern | Ours today (file) | Gap |
|---|---|---|
| The document registry | `app/src/main/java/com/jupydroid/app/Navigation.kt` (`openDocuments = remember { OpenDocuments() }`); `ui/editor/EditorViewModel.kt` and `ui/fileeditor/FileEditorViewModel.kt` register in `init` only | By reading, wider than `GAP-143` records: the ViewModels survive holding the old registry and the new one is empty, for the top tab too. T1's red test measures it |
| What reads the registry | `ui/shell/MainAreaCloseGate.kt`, `ui/shell/MainAreaTabStrip.kt`, `ui/shell/WorkspaceReplace.kt` through `ShellSidePane.kt`, `DocumentOpeners.kt` | Each reads "not open": no ask on a dirty close, no dot, a replace written to the file behind an open editor |
| The strip's order, live paths, live titles | `Navigation.kt` (`openTabOrder`, `openDocumentPaths`, `liveTabTitles`) | Plain `remember`: the order reseeds from the stack, a renamed tab shows the name it left, an OSC title drops |
| The Console's session | `Navigation.kt` (`consoleSession`) | Plain `remember`: a relaunched Console entry composes nothing |
| The stack, the side panel, the browsed directory | `Navigation.kt` (`rememberNavBackStack`, `rememberSaveable`) | None |
| The composition root | `JupydroidApplication`, `CompositionRootTest` | None |

Ours is **present and partial**: the stack and the ViewModels survive, the holders between them do not.

## Deviations

- **The relaunch is survived, not declined** - against both Android references. Stated above.
- **Nothing here survives process death.** A ViewModel does not, so neither does what indexes it;
  the stack does, and each entry rebuilds its document when it is next shown.

## Open decisions

- Whether the per-entry ViewModels survive `ActivityScenario.recreate()` under
  `rememberViewModelStoreNavEntryDecorator`, as read. T1's red test is the measurement; if they do
  not, the item's shape changes and this PRD is edited before any code.

## Surface

| Layer | Files |
|---|---|
| `domain/`, `data/`, `di/` | none |
| shell (`com.jupydroid.app`) | a new file holding the retained holders; `Navigation.kt` (which must end shorter: it is at 496 lines) |
| unit tests | the holder's own |
| instrumented tests | a recreation test beside `CompositionRootTest` |
| `.maestro/` flow + `scripts/` driver | `jd-030-*`, rotating as `scripts/p1g-one-container-driver.sh` does |

## Tasks

- [x] T1 — The document registry survives a relaunch: after a rotation a dirty tab beneath the top
  keeps its dot and its close still asks. A workspace replace reads the same registry; the demo's
  step 5 is where it is seen on the device.
- [x] T2 — The strip keeps its opening order, a renamed tab keeps its new name and a terminal keeps
  its reported title across a rotation.
- [ ] T3 — An open Console still draws after a rotation and after a dark-mode switch.
- [ ] T4 — The demo above, seeded, from a cold start.

## Out of scope

- Process death: the Console's half of it stays `GAP-142`.
- `browseRequest` and `revealRequest`: one-shot requests consumed as they are made.
- A tab key that is not the path: `GAP-141`.
- Declining the relaunch with `configChanges`.
