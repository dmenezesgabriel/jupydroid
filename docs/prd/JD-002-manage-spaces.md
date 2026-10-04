# JD-002 — list, create, rename and delete a Space from the UI

Milestone: `M1` · State: `open` · Depends on: JD-001 (shipped) · Closes: —

## Outcome

A user opens a **Spaces** panel from the activity rail and sees every Space on the device, base
first. From there they can create one by name, rename one, and delete one after confirming. Each act
shows progress while it runs. Today the port exists and has no caller, so none of this is reachable.

## Demo

Who: a human on **`SM-X216B` in landscape**. Nothing here runs a cell, so rule 2 does not require
the tablet. It is chosen for its **width**: the panel lives on the two-pane rail only (see
Trade-offs), and `dev36` is a 320dp one-pane window that draws the bottom bar instead
(`docs/architecture/cross-cutting.md` §8.6, "The 320dp emulator…"). Prereq:
`./gradlew installDebug` on a synced repo. The driver is `scripts/jd-002-manage-spaces-driver.sh`,
and its artefacts go to `evidence/jd-002-manage-spaces/`.

1. **Cold start** with `files/spaces/` holding only `base`. The driver removes any `space-*` with
   `run-as` and lists the directory before the flow.
2. Tap **Spaces** on the rail. One row, "Base", appears with no delete control and no rename
   control.
3. **Create** "Thesis". Progress shows. A row "Thesis · space-1" appears below Base, and
   `files/spaces/space-1/config.json` exists on disk.
4. Try to create with a blank name. The confirm button stays disabled.
5. **Rename** space-1 to "Dissertation". The row updates, and `config.json`'s `name` reads
   "Dissertation" while the directory is still `space-1`.
6. **Delete** space-1:
   - The dialog names "Dissertation".
   - **Cancel** keeps the row and the directory.
   - Delete again and **confirm**: progress shows, the row goes, and `files/spaces/space-1` is gone.
7. Create "Scratch". It mints `space-1` again: deleting the highest id hands it back (ADR-0010).
8. **Kill and relaunch.** The panel lists Base and Scratch.

The driver checks each disk claim (steps 3, 5, 6 and 7) with `adb shell run-as`, because a row on
screen does not prove what the filesystem holds. Commit the screenshots, `maestro.log` and the
`ls`/`cat` captures. `logcat` stays local.

## Who else implements this

Read from code today, at the SHAs in [`../README.md`](../README.md).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/workspaces-extension/src/sidebar.ts`, `commands.ts` | Named *workspaces* (layouts, not environments) listed as a section of the Running sidebar, with rename and a warn-button delete |
| `vscode` | FULL | `vscode@3f3380c src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditor.ts`, `userDataProfilesEditorModel.ts` | Profiles editor: list with the default first, create, rename, delete. The default can be neither renamed nor deleted, and delete is confirmed |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80c src/notebooks/controllers/pythonEnvKernelConnectionCreator.node.ts` | Create only, delegated to the Python extension's `python.createEnvironment` from the kernel picker. No list, rename or delete |
| `intellij-community` | PARTIAL | `intellij-community@406a27a python/src/com/jetbrains/python/configuration/PythonInterpreterMasterDetails.kt` | Interpreter list with Add / Remove / Rename. Rename validates (blank, duplicate). Remove does not confirm, because it only unregisters |
| `termux-app` | ABSENT | — | One `$PREFIX`; there is no second environment to manage |
| `winlator` | FULL | `winlator@fb66541 app/src/main/java/com/winlator/ContainersFragment.java`, `ContainerDetailFragment.java`, `core/PreloaderDialog.java`, `container/ContainerManager.java` | Containers list. An Add toolbar opens an 18-key form, and a per-row popup offers Edit, Duplicate, Remove and Storage Info. Acts run `*Async` behind a modal preloader. Remove and duplicate are confirmed |
| the shipped rootfs | ABSENT | — | Nothing in the image manages environments. `pip`/`venv` live inside a Space and are JD-003's |

**Count:** 2 of 7 implement it fully and 3 partially. The two full ones agree on the shape: a list
with the built-in entry first and protected, create by name, rename, and confirm before delete.
They disagree on *how progress is shown* and *how much a create asks for*, and those are the two
decisions below.

## Implementation inventory

### winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40

| Concern | Files |
|---|---|
| Domain model | `container/Container.java` (name + 18 run keys + `extraData`) |
| Port / protocol | `container/ContainerManager.java`: `createContainerAsync`, `duplicateContainerAsync` and `removeContainerAsync` each run on `Executors.newSingleThreadExecutor()` and post the callback to the main handler |
| UI entry point | `ContainersFragment.java`, which is the app's main screen. `res/menu/containers_menu.xml` holds the single Add item, `res/menu/container_popup_menu.xml` the 5-verb row popup, `res/layout/container_list_item.xml` the row (icon, title, Run, menu) |
| Create / edit | `ContainerDetailFragment.java`. Edit calls `saveData` in place. Create runs `preloaderDialog.show(R.string.creating_container)` → `createContainerAsync` → `close` → back. The name is not validated |
| Progress | `core/PreloaderDialog.java`: a full-screen, non-cancelable `Dialog` carrying one label ("Removing Container...", `res/values/strings.xml`) |
| Confirm | `contentdialog/ContentDialog.java` `confirm(context, msgResId, callback)`, worded "Do you want to remove this container?". It does not name the container |
| Prompt (name only) | `ContentDialog.prompt` trims the text and **silently ignores an empty one**. It is used for profile names and file renames, but not for containers |
| Empty state | `res/layout/containers_fragment.xml` `TVEmptyText`: "No items to display" |
| Edge cases | Add is refused while `!RootFS.find(context).isValid()`. Remove can delete a container a session is running |

### vscode@3f3380c668d07649b953eb8278354b2f263ea6ff

| Concern | Files |
|---|---|
| UI entry point | `src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditor.ts`: an editor (not an activity-bar container) with a list on the left and details on the right |
| Order | `getSwitchProfileActions` sorts with the default first, then by `name.localeCompare` |
| Default is protected | `userDataProfilesEditorModel.ts`: the delete action is pushed only `if (!profile.isDefault)`. In `userDataProfilesEditor.ts` `renderName`, the name input is `disable()`d for the default profile, with the tooltip "Name cannot be changed for the built in profiles" |
| Validation | `userDataProfilesEditorModel.ts` `validate()`: "Profile name is required and must be a non-empty value." and "Profile with name {0} already exists." |
| Confirm | `userDataProfilesEditorModel.ts` `removeProfile`: `dialogService.confirm`, "Are you sure you want to delete the profile '{0}'?", with primary button "Delete" and "Cancel" |
| Progress | `IEditorProgressService.showWhile(action.run())` on every action button, and `show(true, 500)` elsewhere. An **inline** progress bar, delayed 500 ms, that blocks nothing |

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| UI entry point | `packages/workspaces-extension/src/sidebar.ts`: workspaces are a *section of the Running sidebar* (`managers.add({ name: 'Workspaces', … })`) with a per-row delete icon and "Delete All" |
| Confirm | `commands.ts` `workspace-ui:delete`: `showDialog` titled "Delete workspace", body 'Deleting workspace "%1" … cannot be recovered.', `Dialog.warnButton({ label: 'Delete' })`, `defaultButton: 0` (Cancel) |
| Rename | `commands.ts` `workspace-ui:rename`: a name dialog. An empty or cancelled result returns silently |

### intellij-community@406a27a8da071c59b052719fe6f1208c613e96b5

| Concern | Files |
|---|---|
| UI entry point | `python/src/com/jetbrains/python/configuration/PythonInterpreterMasterDetails.kt`, a master-details list: `createActions` returns Add (popup group) / Remove / Rename / Show Paths |
| Rename | `RenameAction`: `Messages.showInputDialog` with an `InputValidatorEx` that refuses a blank name and one that duplicates any SDK name. An unchanged name is a no-op |
| Remove | `RemoveAction` calls `projectSdksModel.removeSdk` with **no confirm**. It deletes nothing on disk, and the settings dialog's Cancel undoes it |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Create only | `src/notebooks/controllers/pythonEnvKernelConnectionCreator.node.ts` `createPythonEnvironment` runs `commands.executeCommand('python.createEnvironment', …)`. The environment is the Python extension's to manage, not this extension's |

**Where the references disagree:**

- **Progress.** Winlator blocks the whole screen with a modal preloader. VS Code draws an inline
  bar that blocks nothing and appears only after 500 ms.
- **What create asks for.** Winlator asks for an 18-key form. VS Code, IntelliJ and JupyterLab ask
  for a name.
- **Name validation.** VS Code and IntelliJ refuse a blank name and a duplicate one. Winlator's
  form refuses nothing, and its `prompt` drops a blank one silently. Ours already permits a
  duplicate (ADR-0010, `SpaceRepositoryContract.twoSpacesMayShareAName`).
- **Can the built-in entry be renamed?** VS Code says no. Winlator and IntelliJ have no built-in
  entry.
- **Confirm wording.** VS Code and JupyterLab name the thing being deleted. Winlator does not.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the list lives | winlator → the main screen. vscode → an editor. jupyterlab → a section of the Running sidebar | A main screen would be a second top-level navigation next to the shell. A Running section mixes a configuration list with live sessions | **A new `SidePanel.SPACES` on the two-pane rail**, chosen by the user in the session that wrote this PRD. It sits beside Files, where JD-005's "active Space roots the explorer" will need it |
| One-pane reach | The bar already carries 5 items (`SidePanel.kt` `onePaneDestinations` KDoc: 3 panels + Launcher + Terminal), which is Material's ceiling | A sixth item breaks the ceiling. Swapping one out demotes a panel people already use | **Rail only**, like `TABLE_OF_CONTENTS` and `DEBUGGER`. `showingAtOnePane()` already falls back to Files. Also the user's choice. Reach at phone width goes to JD-005, whose switcher needs a one-pane surface anyway |
| What create asks for | winlator → 18-key form. vscode/intellij/jupyterlab → a name | `SpaceConfig` has one user-mutable field. A form would be a form for one field | **A name-only dialog** for both create and rename |
| Name validation | vscode/intellij → refuse blank and duplicate. winlator → refuse nothing | Ours permits a duplicate on purpose: the id is the identity (ADR-0010) | **Refuse blank (trimmed), allow duplicate.** The confirm button is disabled on a blank name, as `RenameFileDialog` does it, and an unchanged rename is a dismiss, as JupyterLab and `RenameFileDialog` do it |
| Renaming base | vscode → the built-in name is locked | Lawful at the port. AUD-010 notes that it writes `config.json` into the extracted rootfs | **No rename control on base**, VS Code's rule. The port stays as it is: this is a screen decision |
| Progress | winlator → modal preloader. vscode → inline bar delayed 500 ms | A modal blocks a panel that sits beside an open document. Inline keeps the document usable | **Inline**: a `LinearProgressIndicator` at the top of the panel while an act runs, with the panel's act controls disabled. This also serializes acts, the race AUD-010 found on both sides |
| Confirm | winlator → generic wording. vscode/jupyterlab → name the item, Cancel is the default | Naming the Space prevents deleting the wrong row of two same-named Spaces | **`DeleteFileDialog`'s shape** ("permanently delete", Delete in `error`, Cancel dismisses), naming `"<name> (<id>)"` so that same-named Spaces stay distinguishable |
| Failure | winlator → `return null`, silent. Ours throws `IllegalArgumentException`/`IllegalStateException` naming the path | A silent failure leaves a row that looks done | **Surface the exception's message** in the panel. The UI does not catch per type: any failure is shown, and the list is re-read |
| Wiring | `AppContainer` defaults every port to its fake (`FakeSpaceRepository` KDoc). `DocumentSharer`, `WorkspaceSearcher` and the settings repositories are "always real" | `DiskSpaceRepository` needs only `filesDir`, no runtime | **Always real** in `JupydroidApplication`: `DiskSpaceRepository(spacesDir(this))`. `FakeSpaceRepository` is the `AppContainer` default |

Anything here that outlives the item becomes an ADR before the PRD is deleted. That means at least
the rail-only placement and why the bottom bar does not carry it, and the decision that base cannot
be renamed from the UI.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Model, ids, order | `domain/model/SpaceConfig.kt` (`nextSpaceId`, `inSpaceOrder`, `spaceWithId`, `requireDeletableSpace`) | `winlator@fb66541 container/ContainerManager.java` | complete |
| Port | `domain/port/SpaceRepository.kt`: `spaces`/`create`/`rename`/`delete`, all `suspend` | vscode `IUserDataProfilesService` | complete |
| Adapters | `data/disk/DiskSpaceRepository.kt` (scan per call, IO dispatcher, injected `Clock`), `data/fake/FakeSpaceRepository.kt` (`Instant.now()`, no `Clock`) | — | complete. The fake's clock is a gap only if a test asserts on `created` |
| Contract | `test/.../domain/port/SpaceRepositoryContract.kt`, run by `DiskSpaceRepositoryTest` and `FakeSpaceRepositoryContractTest` | — | complete |
| Composition | `di/AppContainer.kt` has no `spaceRepository`. `JupydroidApplication.containerFor` builds none | — | **absent** |
| ViewModel | none | vscode `UserDataProfilesEditorModel` | **absent** |
| Panel | `SidePanel.kt` has five entries and none for Spaces. The rail iterates `SidePanel.entries` (`ui/shell/SidePanelRail.kt`) | vscode profiles editor, winlator `ContainersFragment` | **absent** |
| Dialogs | `ui/RenameFileDialog.kt`: its validator is `isValidFileName` and it pre-selects the stem before an extension, both of which are wrong for a Space name. `ui/DeleteFileDialog.kt` is generic in its wording | vscode `validate()`, jupyterlab `showDialog` | **present and partial**: the name dialog needs its validator and selection parametrised (see Open decisions) |
| Created time | `ui/RelativeTime.kt` `relativeTime(updatedAt, now, zone)` | — | complete, reused for the row's subtitle |

Overall: **present but unreachable.** Everything below the UI exists, and nothing calls it.

## Deviations

- **No Run button, no file manager, no storage info, no duplicate.** Winlator's row carries all
  four. Run/activate is JD-005's switching, storage info is GAP-147, duplicate is GAP-145, and
  browsing a Space is the explorer's job once JD-005 roots it there.
- **Deleting a running Space is unreachable, not handled.** Only `base` ever runs today
  (`BASE_SPACE` is the compile-time answer everywhere a Space is chosen), and `base` cannot be
  deleted. Winlator deletes a running container without a check. JD-005 makes a second Space
  runnable and so **inherits** the "stop its server and language server before deleting" question.
  This item must not pretend to answer it.
- **Not reachable at one pane.** See Trade-offs. The `showingAtOnePane()` fallback is the existing
  mechanism, not new code.

## Open decisions

- **The rail position of `SPACES`.** No reference ranks a Spaces container against Files, Search or
  Debugger: JupyterLab has none, and VS Code's profiles are an editor. Appending it after `DEBUGGER`
  moves no existing item. Placing it after `FILES` matches its importance. **Default: after `FILES`.**
  Every Maestro selector on the rail is a text label rather than an index (checked in T2 by grepping
  `.maestro/` for rail `index:`/`point:`), so the order is free. ADR-0062 gets the sentence either
  way. **Settled in T2: after `FILES`**, and ADR-0062 carries it.
- **The name dialog: generalise `RenameFileDialog` or write a second one.** Two dialogs would
  duplicate the focus-on-open fix and the unchanged-is-dismiss rule (both are in its KDoc).
  **Default: parametrise.** Pass the validator, its refusal message and the initial selection, and
  keep the file defaults so that no caller changes. Settle it in T3, red test first, in
  `RenameFileDialogTest`'s neighbourhood.
- **Whether `FakeSpaceRepository` gains a `Clock`.** Only if a ViewModel or UI test asserts on a
  row's created time. Decide in T1 when that test is written. If it does, inject the clock the way
  `DiskSpaceRepository` does.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — (`SpaceConfig` unchanged) |
| `domain/port` | — (`SpaceRepository` unchanged) |
| `data/disk` | — (`DiskSpaceRepository` unchanged) |
| `data/fake` | `FakeSpaceRepository.kt`: its KDoc's "nothing constructs the port yet" becomes false, and `Clock` is added only if T1 needs it |
| `ui/spaces/` (new) | `SpacesViewModel.kt` (list, busy, failure, create/rename/delete), `SpacesSidePanel.kt` (rows, actions, progress, dialogs) |
| `ui/` | `RenameFileDialog.kt` parametrised (T3). `DeleteFileDialog.kt` reused as is |
| shell | `SidePanel.kt` (`SPACES`, with its KDoc's reference argument), `ui/shell/SidePanelPresentation.kt` (label "Spaces", icon), `ShellSidePane.kt` (the `SPACES ->` branch) |
| `di/AppContainer` | `val spaceRepository: SpaceRepository = FakeSpaceRepository()`. `JupydroidApplication.containerFor` passes `DiskSpaceRepository(spacesDir(this))` |
| unit tests | new `SpacesViewModelTest` over `FakeSpaceRepository`, and a fake that fails for the error path |
| instrumented tests | new `SpacesSidePanelTest`: base has no delete or rename control, and a user Space has both. `RenameFileDialogTest` covers the new parameters |
| `.maestro/` flow + `scripts/` driver | `.maestro/jd-002-manage-spaces.yaml` and `scripts/jd-002-manage-spaces-driver.sh`, which runs the disk checks with `run-as`, modelled on `scripts/explorer-rename-driver.sh` |

## Tasks

- [x] T1 — `AppContainer.spaceRepository` (default fake; `JupydroidApplication` wires
      `DiskSpaceRepository(spacesDir(this))`) and `SpacesViewModel`. The ViewModel exposes the
      Spaces in `inSpaceOrder`, a `busy` flag, and the last failure's message. `create`, `rename`
      and `delete` each run on `viewModelScope`, refuse to start while busy, and re-list when they
      finish. JVM tests come first, including the failure surfacing.
- [x] T2 — `SidePanel.SPACES` on the rail (label, icon, position per Open decisions) and the
      `SpacesSidePanel` list: each row shows the name with `id · created` underneath, and a
      progress bar shows while busy. An instrumented test proves the panel is reachable from the
      rail and lists base.
- [ ] T3 — Create and rename: the name dialog is parametrised (blank refused, unchanged rename is a
      dismiss), an add action sits in the panel's bar, and each row has a rename action except
      base. Instrumented tests cover base having no rename control and a blank name disabling
      confirm.
- [ ] T4 — Delete: a row action except on base, and `DeleteFileDialog` naming `"<name> (<id>)"`.
      Progress shows while it runs. Instrumented tests cover cancel keeping the row and base having
      no delete control.
- [ ] T5 — `.maestro/jd-002-manage-spaces.yaml` and its driver on `SM-X216B` in landscape, with
      evidence in `evidence/jd-002-manage-spaces/`. Run the demo above, look at every screenshot,
      then ship: ADR(s) for the rail-only placement and the locked base name, delete this PRD, and
      update the backlog row.

## Out of scope

- Duplicating a Space (GAP-145), measuring its size (GAP-147), picking its interpreter (GAP-148).
- Making a Space active, rooting the explorer in it, binding notebooks to it, and reaching Spaces at
  one pane: JD-005.
- Packages inside a Space: JD-003.
- One server per Space vs. one shared server: JD-004.
- Stopping a Space's processes before deleting it: JD-005, once a second Space can run.
- Atomic `config.json` writes: AUD-010 records parity with the reference, and no item owns it.
