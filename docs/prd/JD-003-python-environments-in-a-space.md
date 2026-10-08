# JD-003 — Python environments inside a Space

Milestone: `M1` · State: open · Depends on: JD-009 (shipped) · Closes: —

## Outcome

In a running Space's terminal a person makes a venv, with `python3 -m venv` or with uv, and
installs into it with a plain `pip install`. Its console scripts run. From the home, **Settings** on
the stopped Space lists those environments, registers one as a kernel and makes one the Space's
**default kernel**. A new notebook in that Space then records it, and its cells run in that
environment.

## Demo

Who: a human on **`SM-X216B`**. Cells run, so rule 2 requires the arm64 tablet. Thesis is `space-1`.

1. Cold start: install and launch. **Run** Thesis, **Open** it, and open a terminal.
2. `python3 -m venv ~/envs/stats && ~/envs/stats/bin/pip install ipykernel tabulate` succeeds with no flag
   and no `unset`. `~/envs/stats/bin/pip --version` names the venv's own path, so a console script in
   the venv runs.
3. `apk add uv && uv venv ~/envs/fast && uv pip install --python ~/envs/fast/bin/python ipykernel`
   succeeds. Alpine's uv, not PyPI's: see Deviations.
4. `~/envs/stats/bin/pip install no-such-package-jd003` fails, and the terminal shows pip's own
   message naming the package.
5. Leave to the home and **Stop** Thesis. Thesis → Settings lists `envs/stats` and `envs/fast`.
   **Register as kernel** on `stats`. Pick **Default kernel** `stats`. Save.
6. Kill and relaunch. Thesis's Settings shows `stats` registered and as the default
   (`run-as … cat files/spaces/space-1/config.json`).
7. With Thesis still stopped, the kernel picker in an existing notebook already lists `stats`.
8. **Run** and **Open** Thesis, then create a notebook. Its `metadata.kernelspec.name` is `stats`.
   Run `import sys, tabulate; print(len(sys.prefix.split('/')))`. It prints the depth of
   `…/envs/stats`, which is a number rather than the cell's own source, and `tabulate` imports only
   because it is in the venv.
9. The same cell in a notebook on base's `python3` fails `import tabulate` with `ModuleNotFoundError`,
   so the venv is the only place `tabulate` lives. Not `six`: the image ships it in
   `usr/lib/python3.12/site-packages`.

Commit the screenshots, `maestro.log` and the `run-as` captures under
`evidence/jd-003-python-environments-in-a-space/`. `logcat` stays local.

## Who else implements this

Read from code at the SHAs in [`../README.md`](../README.md). The shipped rootfs is read from
`app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst` without extracting it (see
[`../research/closed-questions.md`](../research/closed-questions.md) for the command).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/apputils/src/sessioncontext.tsx` (`getDefaultKernel`), `packages/services/src/kernelspec/manager.ts` | Lists whatever kernelspecs the server finds and starts the server's `default`. Creates no environment and registers none. The user runs `python -m ipykernel install --user` |
| `vscode` | ABSENT | — | Core has no Python environment or kernel model. The only hit, `src/vs/platform/sandbox/common/terminalSandboxReadAllowList.ts`, lists `~/.local/share/virtualenv` as a readable path |
| `vscode-jupyter` | FULL | `vscode-jupyter@f01d80c src/kernels/raw/finder/localPythonAndRelatedNonPythonKernelSpecFinder.node.ts`, `src/kernels/raw/finder/interpreterKernelSpecFinderHelper.node.ts`, `src/notebooks/controllers/pythonEnvKernelConnectionCreator.node.ts`, `src/kernels/kernelDependencyService.node.ts`, `src/platform/interpreter/installer/uvInstaller.node.ts` | **Every interpreter is a kernel**, with no spec written. "Create environment" adds `ipykernel` to the new env (`additionalPackages: ['ipykernel']`). A missing ipykernel prompts an install. uv is one installer among pip, conda and poetry |
| `intellij-community` | FULL | `intellij-community@406a27a python/src/com/jetbrains/python/sdk/add/v2/venv/EnvironmentCreatorVenv.kt`, `sdk/add/v2/uv/EnvironmentCreatorUv.kt`, `sdk/uv/impl/UvLowLevel.kt` | Creates a venv or a uv env (`uv venv`, retried with `--force`) from a dialog and selects it as the project's interpreter, one per project |
| `termux-app` | ABSENT | — | No Python model; `pkg install python` and the user's own `python -m venv` |
| `winlator` | PARTIAL | `winlator@fb66541 app/app/src/main/java/com/winlator/core/WineInstaller.java` (`getInstalledWineInfos`), `ContainerDetailFragment.java` (`loadWineVersionSpinner`) | The nearest analogue: the runtimes **found on disk** are listed in a spinner on the container's detail screen, and the choice is stored on the container. The spinner is disabled in edit mode |
| the shipped rootfs | PARTIAL | `usr/lib/python3.12/venv/`, `ensurepip/_bundled/pip-25.0.1-py3-none-any.whl`, `ipykernel/kernelspec.py` (`make_ipkernel_cmd`, `install`), `jupyter_client/kernelspec.py` (`_kernel_name_pat`), `jupyter_client/multikernelmanager.py` (`default_kernel_name`), `jupyter_core/paths.py` (`jupyter_path`), `pip/_internal/commands/install.py` (`virtualenv_no_global`) | venv and pip are there; **uv is not**. `ipykernel install` writes `ipykernel_launcher`, which aborts here (`tools/rootfs/jupydroid_kernel.py`) |

Outside the image: Alpine 3.21 `community/aarch64` ships `uv 0.5.31-r0` (APKINDEX), and PyPI's
`uv 0.12.23` publishes a `musllinux_1_1_aarch64` wheel. Both were read with `curl` on 2026-10-07.
Both install under proot on SM-X216B, and only Alpine's resolves a host (T1).

**Count:** 2 of 7 make an environment a kernel (vscode-jupyter, IntelliJ), 2 more list runtimes
found on disk (JupyterLab's specs, Winlator's wine versions). The building is mature and common.
No reference lets a Space choose its default among them, but `jupyter_server` already models one
(`default_kernel_name`).

## Implementation inventory

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Discovery | `src/kernels/raw/finder/localPythonAndRelatedNonPythonKernelSpecFinder.node.ts`, `interpreterKernelSpecFinderHelper.node.ts` (a global spec whose module is **not** `ipykernel_launcher` is kept as itself; the rest are folded into their interpreter) |
| Create | `src/notebooks/controllers/pythonEnvKernelConnectionCreator.node.ts` (`createPythonEnvironment`, then waits for the finder to list the new env) |
| Missing ipykernel | `src/kernels/kernelDependencyService.node.ts` (`installMissingDependencies`, the "requires ipykernel" prompt) |
| uv | `src/platform/interpreter/installer/uvInstaller.node.ts` (`uv pip install --python <exe>`; `isSupported` runs `uv --version`) |

### intellij-community@406a27a8da

| Concern | Files |
|---|---|
| Create | `python/src/com/jetbrains/python/sdk/add/v2/venv/EnvironmentCreatorVenv.kt`, `sdk/add/v2/uv/EnvironmentCreatorUv.kt` |
| uv CLI | `python/src/com/jetbrains/python/sdk/uv/impl/UvLowLevel.kt` (`venv`, `--clear`, the `--force` retry) |
| Pick existing | `sdk/add/v2/venv/PythonExistingEnvironmentSelector.kt`, `sdk/add/v2/uv/UvExistingEnvironmentSelector.kt` |

### winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40

| Concern | Files |
|---|---|
| Found on disk | `app/app/src/main/java/com/winlator/core/WineInstaller.java` `getInstalledWineInfos`: the main version, plus every `wine*` directory |
| Chosen per container | `ContainerDetailFragment.java` `loadWineVersionSpinner`, stored as `wineVersion` |

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Default | `packages/apputils/src/sessioncontext.tsx` `Private.getDefaultKernel`, the server's `default` from `packages/services/src/kernelspec/manager.ts` |

### The shipped rootfs (jupyter_client, jupyter_core, ipykernel 7.3.0, pip 25.0.1)

| Concern | Files |
|---|---|
| Spec argv | `ipykernel/kernelspec.py` `make_ipkernel_cmd`: `[sys.executable, -m, ipykernel_launcher, -f, {connection_file}]` |
| Why that argv dies | `ipykernel/kernelapp.py` `init_iopub` hardcodes `IOPubThread(pipe=True)`; `tools/rootfs/jupydroid_kernel.py` is the repair |
| Spec name | `jupyter_client/kernelspec.py` `_kernel_name_pat`: `^[a-z0-9._\-]+$`, case-insensitive |
| Search path | `jupyter_core/paths.py` `jupyter_path`: `JUPYTER_PATH`, `$HOME/.local/share/jupyter`, the user base when `ENABLE_USER_SITE`, `sys.prefix/share/jupyter`, `/usr/local/share/jupyter`, `/usr/share/jupyter` |
| Default | `jupyter_client/multikernelmanager.py` `default_kernel_name` (traitlet, `python3`); `jupyter_server/services/kernelspecs/handlers.py` returns it as `default` |
| pip in a venv | `pip/_internal/commands/install.py` refuses `--user` when `virtualenv_no_global()`: "Can not perform a '--user' install. User site-packages are not visible in this virtualenv." |

**Where they disagree:** vscode-jupyter writes **no spec** and treats every interpreter as a kernel.
JupyterLab and the server know only **specs**. Our server is `jupyter_server`, so a kernel must be a
spec, and `ipykernel install`'s own spec cannot start here. The app therefore writes the spec, as
`SpaceKernelSpec` does for `python3`. Registration is an explicit act, as in JupyterLab, rather than
vscode-jupyter's discovery. Settings lists what it finds on disk the way Winlator does.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How an env becomes a kernel | vscode-jupyter → every interpreter, implicitly; jupyterlab → `ipykernel install`, by hand | Zero steps vs a catalogue that only holds what was chosen | **An explicit Register in the Space's Settings**, writing a spec the app owns. `ipykernel install`'s argv aborts here, so a by-hand route would register a kernel that dies |
| Where the env list comes from | winlator → scan a directory; intellij → a path picker | Found vs typed | **Scan the Space's workspace for `pyvenv.cfg`**, to a bounded depth. Venvs are where people make them, and the list shows what is there |
| Where the spec lives | rootfs → `$HOME/.local/share/jupyter`, `/usr/local/share/jupyter` | The person's visible folder vs the Space's own system path | **`usr/local/share/jupyter/kernels/<name>/` in the Space's rootfs**, which `jupyter_path` searches and the workspace never shows. HOME is the notebook folder (ADR-0011's reason). T3 confirms it |
| `PIP_USER` vs a venv's pip | — | ADR-0011's unflagged `%pip` vs a venv pip that refuses `--user` | **`user = true` in the Space's `usr/pip.conf`**, pip's SITE file for the image's pip and never a venv's. Measured in T1; ADR-0011 amended |
| uv | vscode-jupyter, intellij → an installed `uv` on PATH | Bundle it vs let the Space install it | **Not bundled.** A Space is its own system (ADR-0073), so installing uv is the Space's own act. **`apk add uv`**: PyPI's static build cannot resolve a host (Deviations) |
| Default kernel | jupyter_server → `default_kernel_name`; winlator → a per-container choice | A per-notebook choice vs a per-Space one | **A `SpaceSettings` field, passed as `--MappingKernelManager.default_kernel_name` at each start.** It must name a spec the Space has, or the port refuses it |
| ipykernel missing in the env | vscode-jupyter → prompt and install | Install for the person vs refuse with the reason | **Refuse Register, saying `ipykernel` is missing and giving the command**, and keep pip's own output where it was run. The app installs nothing into a person's env |

ADR at the end of the item: *a Space's environments become kernels through a spec the app writes*. It
amends ADR-0076 ("`python3` today") and, if T1 requires it, ADR-0011.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Venv creation | Terminal only, measured working (`docs/research/space-isolation.md` §4) | `intellij-community@406a27a sdk/add/v2/venv/` | present; the app's part is making it usable |
| Venv pip | `app/src/main/java/com/jupydroid/app/data/space/SpacePipConfig.kt` | — | closed in T1: a venv's pip installs into the venv |
| uv | absent from the image; `apk add uv` works in a Space (T1) | `vscode-jupyter@f01d80c uvInstaller.node.ts` | present as the Space's own install |
| Kernelspec catalogue while stopped | `domain/model/Space.kt` `spaceWithId` (base's fixed `python3`), `data/space/BaseSpaceKernelSpecs.kt` | `jupyter_core/paths.py` `jupyter_path` | present and partial: a registered spec is invisible until the server runs |
| Writing a spec | `data/space/SpaceKernelSpec.kt` (`python3` only) | `ipykernel/kernelspec.py` `install` | present and partial: one fixed spec |
| Env list | — | `winlator@fb66541 WineInstaller.getInstalledWineInfos` | absent |
| Default kernel | `domain/port/KernelSpecs.kt` `defaultSpec`; `jupyterServerArgs` passes no `default_kernel_name` | `jupyter_client/multikernelmanager.py` | present and partial: always `python3` |
| Settings screen | `ui/spaces/SpaceSettingsDialog.kt`, `domain/model/SpaceConfig.kt` `SpaceSettings` | `winlator@fb66541 ContainerDetailFragment.java` | present; no environment section |

## Deviations

- **The app writes the kernelspec, not `ipykernel install`.** The stock argv aborts on Android, so a
  spec the person writes by hand would register a dead kernel.
- **No implicit discovery.** vscode-jupyter makes every interpreter a kernel. Here the server needs a
  spec on disk, and only an explicit act writes one.
- **uv is the Space's to install**, not bundled. The image stays what `tools/rootfs/requirements.lock`
  pins.
- **The route is `apk add uv`, not `pip install uv`.** Both install and answer `--version` under proot.
  PyPI's wheel ships a **statically linked** binary, though, and no `LD_PRELOAD` reaches a static
  binary, `tools/rootfs/getaddrinfo_shim.c` included. Its `uv pip install` fails with "dns error:
  failed to lookup address information". Alpine's `uv 0.5.31` is linked against musl, so the shim
  reaches it, and it fills a venv with ipykernel's 29 packages. This holds for any static binary a
  Space runs, not only uv.

## Open decisions

- **The `PIP_USER` clash** (T1) is **settled: `user = true` moves into the Space's `usr/pip.conf`**
  (`SpacePipConfig`), and `PIP_USER` leaves the environment. pip reads its SITE file at
  `<sys.prefix>/pip.conf`, which for the image's pip is `/usr/pip.conf`. For a venv's pip it is the
  venv's own, so the venv never sees the setting. `etc/pip.conf` was rejected because it is GLOBAL,
  which a venv reads too. The file is written at every start, but only when it is missing, so it
  reaches a Space made earlier and is the Space's own to edit after that. `PIP_ONLY_BINARY` and
  `PIP_BREAK_SYSTEM_PACKAGES` stay in the environment, because both hold for every pip in the Space.
- **How `jupydroid_kernel` reaches a venv** (T3). The module is in the *system* site-packages, which a
  venv cannot see. Candidates: ship it in its own directory in the image and set `PYTHONPATH` in the
  spec's `env` (this needs a rootfs rebuild), or have the spec run base's interpreter with the venv's
  site-packages on its path (this needs no rebuild, but the env's own interpreter would not run).
  Measure each one's kernel start on the tablet.
- **The spec's name and display name** (T3). Name from the venv directory, folded to
  `_kernel_name_pat`; a clash with an existing spec is refused by name. Display name
  `Python (<dir>)`, as `ipykernel install --display-name` is conventionally used.
- **The scan's bounds** (T3). How deep, which directories are skipped (`pkgs/`, `.git`), and its cost
  on a large workspace.
- **A registered env that has since been deleted** (T4). Its spec would point at nothing. Either
  Settings shows it as missing, or the launch drops it. A default naming a missing spec falls back to
  `python3` with the reason on the home.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `SpaceSettings` (`registeredKernels`, `defaultKernel`), a `PythonEnvironment` (path, has ipykernel), the spec-name rule |
| `domain/port` | `SpaceRepository` (an env scan, or a new port such as `SpaceEnvironments`) — decided in T3 |
| `data/disk` | `DiskSpaceRepository` (`StoredSpaceConfig`) |
| `data/fake` | `FakeSpaceRepository`, and a fake for any new port |
| `data/space` | `SpaceRuntime` (the `PIP_*` set), `SpaceKernelSpec` (writing registered specs), `BaseSpaceKernelSpecs` (stopped catalogue from disk), `JupyterServerLauncher` (`jupyterServerArgs` default kernel) |
| `ui/spaces` | `SpaceSettingsDialog` (an Environments section, Register, Default kernel), `SpacesViewModel` |
| `tools/rootfs` | `Dockerfile`, if `jupydroid_kernel` moves to its own directory |
| unit tests | `SpaceRuntimeTest`, `SpaceKernelSpecTest`, `SpaceRepositoryContract`, `DiskSpaceRepositoryTest`, `SpacesViewModelTest`, `JupyterServerLauncherSpaceTest`, `KernelSpecsTest` |
| instrumented tests | `SpacesHomeScreenTest` / a `SpaceSettingsDialog` test (Register enabled iff ipykernel is present) |
| `.maestro/` flow + `scripts/` driver | `jd-003-python-environments-in-a-space` |

## Tasks

- [x] T1 — **A venv's own pip works, and uv reaches a Space.** In a Space's terminal,
  `python3 -m venv` plus a plain `pip install` lands in the venv with no flag, and `%pip` on base still
  lands in `pkgs/`. A venv's console script runs. uv's route (`apk add uv` or `pip install uv`) and
  `uv venv` are measured under proot. Open decision 1 is settled
- [ ] T2 — **The stopped catalogue is the Space's own.** `KernelSpecs` answers a stopped Space from the
  kernelspecs on its disk rather than `spaceWithId`'s fixed `python3`, so a spec written into the
  Space shows in the picker before it runs
- [ ] T3 — **Register as kernel.** Settings on a stopped Space lists the venvs under its workspace, and
  Register writes a spec that starts a kernel in that env. One without ipykernel is refused with
  the command that fixes it. Open decisions 2–4 are settled
- [ ] T4 — **Default kernel.** Settings picks the Space's default among its specs, passed as
  `--MappingKernelManager.default_kernel_name`. A new notebook records it (`defaultSpec`). Open
  decision 5 is settled
- [ ] T5 — The demo on SM-X216B, and the ADR amending ADR-0076 (and ADR-0011 if T1 changed it)

## Out of scope

- Conda, poetry, pipenv and hatch environments: none ships in the image, and no reference measurement
  covers them under proot.
- A Python other than the image's (`GAP-148`), such as one `uv python install` would fetch.
- Creating an environment from the app's UI. The terminal is where a Space's tools are used (ADR-0073).
- Installing, listing or removing packages from an app screen. pip's own output in the terminal or in a
  `%pip` cell is the record.
- Kernels for other languages.
