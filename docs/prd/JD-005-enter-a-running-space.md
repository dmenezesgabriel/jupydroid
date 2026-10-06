# JD-005 — Enter a running Space

Milestone: `M1` · State: in-progress · Depends on: JD-004 (shipped) · Closes: —

## Outcome

From the home, **Open** on any running Space enters it. The explorer, search, recents, kernels, the
Running panel, terminals, export and the language server then all act on that Space and not on base.
A new notebook records that Space's own kernel in `metadata.kernelspec.name`, and Back from the
workspace returns to the home.

## Demo

Who: a human on **`SM-X216B`**. A cell runs, so rule 2 requires the arm64 tablet. Thesis is `space-1`.

1. Cold start: install and launch. Base and Thesis both read **Stopped**, and Open is disabled on both
   rows.
2. **Run** Thesis. Its Open enables, and base's Open stays disabled.
3. **Open** Thesis. The explorer lists Thesis's own `home/jupydroid`, which is empty, and none of
   base's notebooks.
4. New notebook. `run-as com.jupydroid.app cat files/spaces/space-1/home/jupydroid/Untitled.ipynb`
   shows `"kernelspec": {"name": "python3", …}`, and no such file appears under `files/spaces/base/`.
5. With `thesis-only.txt` seeded into Thesis's home alone, run
   `import os; print(os.path.exists(os.path.expanduser('~/thesis-only.txt')))`, which prints `True`.
   `HOME` is the in-root `/home/jupydroid` in every Space (`SpaceRuntime.kt`), so a check on its
   path cannot tell two Spaces apart.
6. Open a terminal and run `ls ~ > ~/terminal-saw`. The file lands in space-1's home and lists
   `thesis-only.txt`, and none appears in base's. `pwd` cannot tell two Spaces apart, for step 5's
   reason. A never-run cell's undefined name gets `pylsp`'s error mark, and `ps -A` shows a `pylsp`
   under space-1's root and none under base's.
7. Back to the home. Thesis still reads Running. **Run** base and **Open** it: base's notebooks are
   back, and the notebook from step 4 is not among its recents.
8. Leave the app for more than 30 s. Both rows read Stopped, and `ps -A` shows no `pylsp` left.
9. Kill and relaunch. The files from step 4 are still on disk.

Commit the screenshots, `maestro.log` and the `run-as`/`ps` captures under
`evidence/jd-005-enter-a-running-space/`. `logcat` stays local.

## Who else implements this

Read from code at the SHAs in [`../README.md`](../README.md). Two products outside the pinned set
were read from their documentation because the backlog row names them: SageMaker Studio (ADR-0074)
and JupyterHub.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/notebook/src/panel.ts` (`_updateSpec`), `packages/docmanager/src/recents.ts` | One server per page. A notebook's `kernelspec.name` is the live kernel's `kernel.name`. Recents live in one store, each entry tagged `root: serverRoot` and filtered by the current root |
| `vscode` | FULL | `vscode@3f3380c src/vs/workbench/services/workspaces/electron-browser/workspaceEditingService.ts` (`enterWorkspace` → `hostService.reload()`), `browser/workspaceEditingService.ts` (`openWindow`, `forceReuseWindow`) | Entering a workspace **rebuilds the window**. Every window-scoped service is bound to exactly one workspace and is never retargeted in place |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80c src/kernels/execution/helpers.ts` (`updateNotebookMetadataWithSelectedKernel`) | For a kernel started from a Python environment it writes `name: 'python3'`, commented *"so that its portable"*. The environment is never written into the file |
| `intellij-community` | ABSENT | — | A project window is bound to one project. There is no Jupyter environment concept in the community tree |
| `termux-app` | ABSENT | — | One prefix and one environment, so there is nothing to enter |
| `winlator` | FULL | `winlator@fb66541 app/app/src/main/java/com/winlator/ContainersFragment.java` (`runContainer`: `intent.putExtra("container_id", …)`), `XServerDisplayActivity.java` (`getContainerById` + `activateContainer`), `container/ContainerManager.java` (`activateContainer`) | The container id **rides on the Intent** of the activity that runs it. The activity builds its environment for that id, and `activateContainer` repoints the global `home/xuser` symlink |
| the shipped rootfs | PARTIAL | `jupyter_server` `GET api/kernelspecs` (`default`), the kernelspec `SpaceKernelSpec` writes | Each Space's server advertises its own specs and default. Today every Space's is `python3` |

Outside the pinned set:

- **SageMaker Studio (spaces):** *Run space*, then *Open JupyterLab*
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-jl-user-guide-create-space.html>).
  Each space runs stock `jupyter lab` from its image
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-jl-admin-guide-custom-images.html>),
  so it writes JupyterLab's `kernel.name`. The notebook belongs to the space by living on that space's volume.
- **SageMaker Studio Classic** did the opposite. It wrote
  `"name": "python3__SAGEMAKER_INTERNAL__arn:aws:sagemaker:…:image/datascience-1.0"`, and the same
  notebook then failed outside Studio with *"No such kernel named python3__SAGEMAKER_INTERNAL__…"*
  (<https://github.com/aws-samples/sagemaker-run-notebook/issues/4>). Its successor dropped the scheme.
- **JupyterHub named servers:** a user may run several servers, each in its own environment. The
  notebook records nothing about which server ran it
  (<https://jupyterhub.readthedocs.io/en/stable/howto/configuration/config-user-env.html>).

**Count:** 2 of 7 enter an environment (VS Code, Winlator), and both bind the whole window or
activity to one environment at entry. None of the 7 writes an environment into `kernelspec.name`,
and the one product that did (Studio Classic) broke portability by doing so.

## Implementation inventory

### winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40

| Concern | Files |
|---|---|
| Which container is entered | `app/app/src/main/java/com/winlator/ContainersFragment.java` `runContainer`: the id is put on the Intent |
| Binding at entry | `XServerDisplayActivity.java` `onCreate`: `getContainerById(getIntent()…)`, then `activateContainer` |
| Global "active" | `container/ContainerManager.java` `activateContainer`: one `home/xuser` symlink, which is safe only because one container runs at a time (`singleTask`) |

### vscode@3f3380c668d07649b953eb8278354b2f263ea6ff

| Concern | Files |
|---|---|
| Entering | `src/vs/workbench/services/workspaces/electron-browser/workspaceEditingService.ts` `enterWorkspace` → `hostService.reload()` |
| The services bound to it | `src/vs/platform/workspace/common/workspace.ts` `IWorkspaceContextService`, `src/vs/platform/storage/common/storage.ts` `StorageScope.WORKSPACE` |

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| `kernelspec` written | `packages/notebook/src/panel.ts` `_updateSpec`: `name: kernel.name` |
| New notebook's kernel | `packages/notebook-extension/src/index.ts` `createNew`: `kernel: { name: kernelName }`, falling back to the server's default |
| Recents per root | `packages/docmanager/src/recents.ts`: one `docmanager:recents` store, each entry carrying `root`, filtered by `_serverRoot` |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Name written for an environment's kernel | `src/kernels/execution/helpers.ts` `updateNotebookMetadataWithSelectedKernel`, case `startUsingPythonInterpreter` |

**Where they disagree:** Winlator keeps a global mutable "active container", while VS Code binds
services per window and never retargets them. Winlator's global is safe there only because one container
runs at a time. Here several run at once (ADR-0075), so a global that adapters read would retarget an
open notebook's kernel underneath it. That is why we take VS Code's shape and carry the id the way Winlator does.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How the ports bind | winlator → a global active symlink; vscode → services built per window for one workspace | Global: one set of ports. Per Space: a set per entered Space, cached | **A per-Space set of workspace ports, built on first entry and cached per process** beside `JupyterSpaceServers`, so two entries for one Space are never two servers' clients. Nothing is retargeted in place |
| Where the entered id lives | winlator → the Intent; vscode → the window | Nav key vs a remembered variable | **On the workspace's nav key** (`Explorer(spaceId)`), Winlator's Intent extra. It survives process death with the back stack, and every workspace entry reads its Space from the key under it |
| Open on a Stopped Space | SageMaker → *Open JupyterLab* only once it runs | One tap vs an honest state | **Disabled until Run**. The row's Running is the only gate |
| `kernelspec.name` | Studio Classic → env-qualified; jupyterlab, vscode-jupyter, Studio spaces → the kernel's own name | Self-describing file vs portability | **The entered Space's own spec name**: its `KernelSpecs.specs(space).default`, `python3` today. The Space is the folder the notebook lives in. Studio Classic's breakage is the measured cost of the other option |
| Recents | jupyterlab → one store, entries tagged by root | One file per Space vs one file with a tag | **One file in `filesDir`, each entry tagged with its Space id**, filtered by the entered Space. An untagged entry from before is base's. Nothing is written into a rootfs (ADR-0074) |
| `pylsp` on leave | ours → base's alone (`AppContainer.idleServerStopper`) | — | **Every Space's**, beside `stopAll()` |

ADR at the end of the item: *entering a Space binds a per-Space set of ports, and a notebook records
its kernel, not its Space*.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Open per row | `app/src/main/java/com/jupydroid/app/ui/spaces/SpacesHomeScreen.kt` (`enabled = actsEnabled && isBase`, `onOpen: () -> Unit`) | `winlator@fb66541 ContainersFragment.java` | present and partial: base only, no id carried |
| Workspace key | `app/src/main/java/com/jupydroid/app/SpacesHome.kt` (`enterWorkspace` pushes `Explorer`) | winlator Intent extra | absent: no Space on the key |
| Files: notebooks, workspace, searcher, sharer | `app/src/main/java/com/jupydroid/app/JupydroidApplication.kt` (`notebookWorkspace(this)`, base's `home/jupydroid`) | vscode per window | present and partial: base only |
| Recents | `app/src/main/java/com/jupydroid/app/data/disk/DiskRecentDocumentsRepository.kt` | `jupyterlab@7f0e690 packages/docmanager/src/recents.ts` | present and partial: no Space tag |
| Kernels, sessions, specs, debugger, exporter, terminals | `JupydroidApplication.kt` `SpaceBackedPorts(servers.server(BASE_SPACE.id))` | vscode per window | present and partial: base's server only |
| Call sites naming base | `app/src/main/java/com/jupydroid/app/ui/editor/` (`NotebookKernel`, `NotebookKernelPicker`, `EditorViewModel`, `NotebookCompletion`, `NotebookHover`, `NotebookDefinition`, `NotebookRename`, `NotebookFormat`, `NotebookLanguageDocument`), `ui/launcher/LauncherViewModel.kt` | — | present and partial: `BASE_SPACE` is a compile-time answer |
| `pylsp` per Space | `app/src/main/java/com/jupydroid/app/data/space/StdioLanguageServer.kt`, already keyed by `Space` | — | complete in the adapter, unreachable for any Space but base |
| New notebook's kernel | `DocumentOpeners.kt`, `ui/explorer/ExplorerViewModel.kt`, `ui/launcher/LauncherViewModel.kt` pass `DEFAULT_KERNEL_ID` | `jupyterlab@7f0e690 notebook-extension createNew` | present and partial: a constant, not the Space's default |
| Non-base `Space` value | `app/src/main/java/com/jupydroid/app/data/space/AndroidBaseSpace.kt` `spaceServers` builds `Space(id, name = id, kernels = BASE_SPACE.kernels)` | — | present and partial: the name is the id |
| Backstop | `app/src/main/java/com/jupydroid/app/di/AppContainer.kt` (`languageServer.stop(BASE_SPACE)`) | — | present and partial |

## Deviations

- No window reload. VS Code rebuilds the window, while we keep one process and one activity and build
  the entered Space's set on first entry. The effect is the same, because nothing bound to one Space is
  ever pointed at another.
- No global active Space, unlike Winlator, for the reason under *Where they disagree*.

## Open decisions

- ~~**The import intent**~~ settled in T1: from the home it runs base and then enters it, and inside a
  workspace it stays in the Space already entered (`DocumentOpeners.kt` `importNotebook`,
  `DocumentOpenersTest`).
- ~~**The Running panel and the session registry**~~ settled in T3: one registry per Space. A stopped
  server's sessions port ends everything its registry holds (`BaseSpaceKernelSessions.running`), so a
  shared registry would put base's notebooks on No Kernel when Thesis's panel was looked at
  (`AppContainerTest.eachSpacesSessionsListOnlyWhatItsOwnGatewayOpened`).
- ~~**A Space stopped while it is entered**~~ settled in T3: the workspace stays. The notebook lands on
  No Kernel, and the next run starts that Space's server again. That was measured on the tablet after
  the idle backstop (`evidence/jd-005-enter-a-running-space/t3-kernels-bind/`).

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `Space` (built from `SpaceConfig`, its name not its id), `RecentDocument` (Space tag) |
| `domain/port` | `RecentDocumentsRepository` (scoped by Space); the per-Space set is a group of existing ports, not a new port |
| `data/space` | `AndroidBaseSpace.kt` (`spaceServers`, the per-Space port set), `StdioLanguageServer` (stop all) |
| `data/disk` | `DiskRecentDocumentsRepository` |
| `data/fake` | `FakeRecentDocumentsRepository` |
| `ui/spaces` | `SpacesHomeScreen` (`onOpen(spaceId)`, enabled iff running), `SpacesViewModel` |
| shell | `SpacesHome.kt` (`Explorer(spaceId)`), `Navigation.kt`, `DocumentOpeners.kt` |
| `ui/editor`, `ui/launcher`, `ui/explorer` | `BASE_SPACE` and `DEFAULT_KERNEL_ID` replaced by the entered Space and its default |
| `di/AppContainer` | the per-Space set, and the backstop stopping every `pylsp` |
| unit tests | `SpacesViewModelTest`, `DiskRecentDocumentsRepositoryTest`, `RecentDocumentsRepositoryContract`, `SpacesHomeNavigationTest` |
| instrumented tests | `SpacesHomeScreenTest` (Open enabled iff running) |
| `.maestro/` flow + `scripts/` driver | `jd-005-enter-a-running-space` |

## Tasks

- [x] T1 — The home's Open is enabled iff the row is Running and enters that Space: `onOpen(spaceId)`,
  with `Explorer` carrying the id. The import path is settled (open decision 1). Ports are still base's,
  so a flow on the emulator shows only the gate
- [x] T2 — The files side binds to the entered Space: notebook repository, workspace, searcher and sharer
  rooted at its `home/jupydroid`, from a per-Space set cached in the container. Recents are tagged and
  filtered by Space
- [x] T3 — The kernel side binds to the entered Space's server: gateway, sessions, specs, debugger and
  exporter. The `ui/editor` and launcher call sites take the entered `Space` in place of `BASE_SPACE`.
  Open decisions 2 and 3 are settled
- [x] T4 — Terminals and `pylsp` run on the entered Space, and the backstop stops every Space's `pylsp`
- [x] T5 — A new notebook records the entered Space's default kernel (`KernelSpecs.specs(space).default`)
  in `metadata.kernelspec.name`, at the explorer, the launcher and the import
- [x] T6 — The demo on SM-X216B, and the ADR

## Out of scope

- Switching Space without leaving the workspace, since VS Code's window is the boundary too.
- Per-Space settings (idle time, default kernel, environment): JD-009.
- Python environments registered as kernels: JD-003.
- A per-entry launch override: `GAP-146`.
- Clearing a Space's `run/` and `tmp/` residue: `GAP-150`.
