# JD-103 — A Space's projects each get an environment and a kernel

Milestone: `M13` · State: open · Depends on: JD-003 (shipped) · Closes: —

## Outcome

In a Space, a person creates an environment for a project folder — from the new Environments
panel or from the Space's own terminal — and its kernel appears in every notebook's picker under
the project's name. Two projects' notebooks then run on their own environment's packages, so
`%pip` in one never reaches the other.

## Demo

Who: the agent, on **`SM-X216B`** — cells run, so rule 2 requires the arm64 tablet. Thesis is
`space-1`.

1. Cold start: install, launch, **Run** Thesis, **Open** it.
2. Open the **Environments** panel from the rail. It lists the Space's environments by project;
   Thesis starts with none.
3. Create an environment for `proj-a` and one for `proj-b` from the panel. Each create streams
   its progress and ends with the environment listed *and* its kernel registered.
4. Open a notebook in `proj-a` and pick `proj-a`'s kernel in the kernel picker; open another in
   `proj-b` and pick `proj-b`'s.
5. `proj-a`'s notebook: `%pip install tabulate==0.9.0`, then `import tabulate;
   print(tabulate.__version__)` prints `0.9.0`. `proj-b`'s prints `0.10.0` the same way. The two
   installs never meet: each cell runs on its own environment's interpreter.
6. In the Space's terminal, `jupydroid-env create proj-c` makes a third environment and kernel
   with no app act; the panel and the picker list it.
7. Kill and relaunch. The environments, their specs and the notebooks' `kernelspec` metadata all
   persist; the pickers still offer them, including on the stopped Space.
8. A musl environment made by hand — `python3 -m venv ~/envs/hand` then
   `~/envs/hand/bin/pip install ipykernel` — still registers from the panel: the JD-003 route is
   unchanged.
9. Kernel creation as its own act: an existing interpreter — one of the Space's own pythons, no
   environment made — becomes a kernelspec every picker offers, through the same act the
   Environments panel and `jupydroid-env register` in the terminal both reach; a notebook picks
   it and runs. The verb's exact arguments are T8's to settle; the act is the backlog's "a kernel
   created for an existing interpreter with no env create at all".

The flow and driver that reproduce this ship as `jd-103-project-environments` (`.maestro/` +
`scripts/`); the screenshots, `maestro.log` and `run-as` captures they produce stay local
([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)), and `logcat`
never leaves the device.

## Who else implements this

**The kernel model first, because the item demands it.** Read from the shipped rootfs
(`app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst`, extracted read-only; T1 re-measures it
on the device):

- **One kernelspec catalogue, shared by every notebook.** `GET api/kernelspecs` returns one
  `default` plus a flat `kernelspecs` map from `ksm.get_all_specs()`
  (`usr/lib/python3.12/site-packages/jupyter_server/services/kernelspecs/handlers.py`
  `MainKernelSpecHandler.get`); nothing in it is scoped to a path or a document.
- **A kernel per session, not per spec.** `POST api/sessions` reaches
  `SessionManager.create_session` → `start_kernel_for_session` → `kernel_manager.start_kernel`,
  which mints a fresh `kernel_id` per session
  (`jupyter_server/services/sessions/sessionmanager.py`); `PATCH` with `kernel.name` starts a new
  kernel for the session and shuts the old one down
  (`jupyter_server/services/sessions/handlers.py`). The session database is `:memory:` by
  default, so session→kernel bindings die with the server — what persists is the notebook's own
  `metadata.kernelspec.name`.
- **The kernel a notebook's name suggests is client-supplied.** The server never reads the
  `.ipynb`; the client sends `kernel.name` in the session POST, and an absent one falls back to
  `default_kernel_name` (`jupyter_server/services/sessions/handlers.py`,
  `jupyter_client/multikernelmanager.py`). Ours is `Notebook.kernelPreference`, seeded from
  `metadata.kernelspec` (`packages/docregistry/src/context.ts` `_populate`,
  `jupyterlab@7f0e690`).
- **A kernel's cwd comes from the session's path**, not its spec
  (`contents_manager.get_kernel_path`), so isolation between projects is the spec's `argv[0]`
  interpreter, never the working directory.

Read from code at the SHAs in [`../README.md`](../README.md).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/apputils/src/sessioncontext.tsx`, `packages/notebook-extension/src/index.ts` | The kernel picker lists every spec the server finds, and the launcher draws **one card per kernelspec** (`index.ts`, default at rank 0). Creates and registers no environment — `ipykernel install` by hand writes the spec |
| `vscode` | ABSENT | — | Core has no Python environment or kernel model |
| `vscode-jupyter` | FULL | `vscode-jupyter@f01d80c src/notebooks/controllers/kernelSource/environmentCreationCommand.ts`, `src/notebooks/controllers/pythonEnvKernelConnectionCreator.node.ts`, `src/kernels/raw/finder/localPythonKernelSelector.node.ts` | "Create environment" runs the Python extension's creation provider with `additionalPackages: ['ipykernel']` and waits for the finder to list it; **every interpreter is implicitly a kernel** — no spec is written |
| `intellij-community` | FULL | `intellij-community@406a27a python/src/com/jetbrains/python/sdk/add/v2/venv/EnvironmentCreatorVenv.kt`, `sdk/add/v2/uv/EnvironmentCreatorUv.kt`, `python/src/com/jetbrains/python/sdk/PySdkRendering.kt` | Creates a venv or uv env from a dialog and selects it as **the project's** interpreter, one per project |
| `termux-app` | ABSENT | — | No Python model; `pkg install python` and the user's own `python -m venv` |
| `winlator` | ABSENT | — | No environment concept at all |
| the shipped rootfs | PARTIAL | musl `usr/lib/python3.12/venv/`, `ensurepip/_bundled/pip-25.0.1-py3-none-any.whl`; gnu payload `opt/gnu/python/lib/python3.12/venv/`, `ensurepip/_bundled/pip-26.3-py3-none-any.whl`, `site-packages/ipykernel/`; `ipykernel/kernelspec.py` `make_ipkernel_cmd` | Both interpreters can build a venv and carry ipykernel — the gnu one already does. `ipykernel install`'s argv still aborts here (`tools/rootfs/jupydroid_kernel.py`) |

**Count:** 2 of 7 create an environment that becomes a kernel (vscode-jupyter, IntelliJ), and
JupyterLab's own picker and launcher already assume many kernels per workspace. The building is
mature; what none of them has is *our* constraint — a spec server whose stock `ipykernel install`
argv dies on Android, inside a Space that is its own rootfs.

`microsoft/vscode-python@f4c86e4f5f7c34b99c5333b1aef50e997288f936` is the creation-flow
authority the way it was for JD-003's discovery: per-workspace-folder env locators
(`src/client/pythonEnvironments/base/locators/lowLevel/workspaceVirtualEnvLocator.ts`,
`globalVirtualEnvronmentLocator.ts`) and `python_files/create_venv.py` run by the selected
interpreter through `src/client/pythonEnvironments/creation/provider/venvCreationProvider.ts`.

## Implementation inventory

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Create entry | `src/notebooks/controllers/kernelSource/environmentCreationCommand.ts` (`jupyter.createPythonEnvAndSelectController`) |
| Create flow | `src/notebooks/controllers/pythonEnvKernelConnectionCreator.node.ts` (`createPythonEnvironment`, waits for the finder) |
| Interpreter as kernel | `src/kernels/raw/finder/localPythonKernelSelector.node.ts`, `localPythonEnvKernelSourceSelector.node.ts`, `interpreterKernelSpecFinderHelper.node.ts` |
| Missing ipykernel | `src/kernels/kernelDependencyService.node.ts` |
| Env API | `src/api.proposed.notebookEnvironment.ts` |

### vscode-python@f4c86e4f5f7c34b99c5333b1aef50e997288f936

| Concern | Files |
|---|---|
| Creation provider | `src/client/pythonEnvironments/creation/provider/venvCreationProvider.ts` |
| Creation script | `python_files/create_venv.py` — `sys.executable -m venv`, pip upgrade, requirements |
| Discovery | `src/client/pythonEnvironments/base/locators/lowLevel/workspaceVirtualEnvLocator.ts`, `globalVirtualEnvronmentLocator.ts`, wiring in `src/client/pythonEnvironments/index.ts` |

### intellij-community@406a27a8da

| Concern | Files |
|---|---|
| Create | `python/src/com/jetbrains/python/sdk/add/v2/venv/EnvironmentCreatorVenv.kt`, `sdk/add/v2/uv/EnvironmentCreatorUv.kt` |
| Per-project pick | `python/src/com/jetbrains/python/sdk/PySdkRendering.kt` |

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Per-spec launcher | `packages/notebook-extension/src/index.ts` — one card per `specs.kernelspecs` entry, `args: {kernelName}`, `rank = name === specs.default ? 0 : Infinity`, `kernelIconUrl` from spec resources |
| Kernel picker | `packages/apputils/src/sessioncontext.tsx` (`getDefaultKernel`, `kernelOptions`) |

### The shipped rootfs

| Concern | Files |
|---|---|
| Kernel model | `jupyter_server/services/kernelspecs/handlers.py`, `services/sessions/handlers.py`, `services/sessions/sessionmanager.py`, `jupyter_client/multikernelmanager.py` |
| venv in both payloads | `usr/lib/python3.12/venv/` (musl); `opt/gnu/python/lib/python3.12/venv/` + `ensurepip/_bundled/pip-26.3-py3-none-any.whl` (gnu) |
| Spec argv that dies | `ipykernel/kernelspec.py` `make_ipkernel_cmd` → `ipykernel_launcher`; repair is `tools/rootfs/jupydroid_kernel.py` |

**Where they disagree:** vscode-jupyter writes **no spec** and treats every interpreter as a
kernel; JupyterLab and the server know only **specs**. Ours is a spec server, so an environment
becomes a kernel only through a spec the app or its helper writes (ADR-0078). vscode-python
discovers envs per workspace *folder*; our workspace is one root, so "project" here means a
top-level directory inside it.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How creation reaches a Space | vscode-python → bundled script run by the interpreter; `chat-harnesses/*.sh` → asset script via `SpaceShell`; rootfs → helper baked into the image | An asset script reaches every Space that already exists; a baked one reaches only Spaces created after a rebuild; an inline `SpaceShell` script is unreachable from the terminal | **A script asset, installed into the Space at `/usr/local/bin`** — `SpaceShell` runs it for the panel's act, the terminal runs it by name, and one script is both routes (the `opencode.sh` precedent: install to `/usr/local/bin`, which `SpaceRuntime` puts on PATH) |
| Which interpreter creates | vscode-python → interpreter picker over every found interpreter; the Space has two — musl `python3`, gnu `/opt/gnu/python/bin/python3.12` | A musl venv installs only musllinux and pure-Python wheels — the very gap ADR-0166 bought the gnu payload to close; a picker offers a choice that is strictly worse for kernels | **Gnu only** (decided): every app-created env is glibc-based, like the default kernel. Musl envs remain the terminal's own `python3 -m venv` + Register |
| Register on create | vscode-jupyter → implicit, every interpreter a kernel; ADR-0078 → an explicit Register act | Bundling registration into creation adds a spec write; a separate step makes every env take two acts | **Auto-register** (decided): creation's purpose is a kernel, so the act produces one — the script writes the same spec `SpaceKernelSpec.installEnvironment` writes. Foreign envs keep the explicit Register |
| Discovery scope | vscode-python → env roots per workspace folder; JD-003 → fixed roots only, "a project's own `.venv` is one level too deep" | A recursive walk is unbounded; fixed roots miss `proj/.venv` entirely — the item's whole point | **The same env roots scanned inside each top-level directory** — a bounded number of listings however deep the workspace grows; nesting below one project level stays unfound, as it does for vscode-python outside its opened folder |
| Spec env per libc | — (new ground: `installEnvironment` writes the musl env map for every env) | A glibc kernel inheriting the musl preload and without `PROOT_SIGSYS_ENOSYS` was measured fatal under threads (ADR-0166) | **`pyvenv.cfg`'s `home` names the base interpreter; a gnu env's spec gets the glibc half of the env split.** T4 measures the exact map on the tablet |
| Env→kernel naming | JD-003 → the directory's name; `proj-a/.venv` and `proj-b/.venv` both answer `venv` | Unqualified names collide across projects; qualified ones must stay deterministic and `_kernel_name_pat`-safe | **Open decision** — the rule must keep `envs/stats` → `stats` and derive a unique name from the project path; T2 settles it |
| Sidebar surface | new `SidePanel` vs Settings-only vs a main-area document | Settings lives on the home and serves stopped Spaces; the panel is the in-Space surface the item names; the bottom bar is already at Material's five-item ceiling | **`SidePanel.ENVIRONMENTS`** on the rail, with a `MoreRail` row so the one-pane window reaches it; Settings keeps its section for Spaces that are not running |
| Launcher per kernel | JupyterLab → one launcher card per spec | More cards vs the faithful "new notebook on *this* kernel" reach | **Open decision** — adopting it is iterating `specs` the way `index.ts` does; T-task only if it stays cheap |

ADR at the end of the item: *a Space's projects get environments the app creates, registered as
kernels through one script both the panel and the terminal run* — amending nothing in ADR-0078,
whose "registration is the person's act" the create act preserves.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Kernel model | `domain/model/Notebook.kt` (`kernelPreference`, `withKernelSpec`), `domain/model/KernelPicker.kt`, `ui/editor/NotebookKernelSession.kt` | shipped rootfs `sessionmanager.py` | complete — T1 documents the measurement the item demands |
| Env discovery | `data/disk/DiskSpaceEnvironments.kt` `ENV_ROOTS` — workspace root + `envs`, `Envs`, `.direnv`, `.venvs`, `.virtualenvs`, `.local/share/virtualenvs` | `vscode-python@f4c86e4 workspaceVirtualEnvLocator.ts` per folder | partial — a project's own `.venv` is unfound by design; T3 extends the same roots into top-level dirs |
| Env creation | terminal only: musl `python3 -m venv` measured (JD-003 T1); gnu `python -m venv` unverified | `vscode-jupyter@f01d80c pythonEnvKernelConnectionCreator.node.ts` | absent from the app; no script exists — T1 measures the gnu venv, T5 ships it |
| Env→kernel spec | `data/space/SpaceKernelSpec.kt` `installEnvironment`, `data/disk/DiskSpaceEnvironments.kt` `register` | — | partial — the spec env is libc-blind; a gnu env registered today gets the musl map and its kernel dies under threads; T4 fixes |
| Kernel picker | `kernelPickerGroups` lists every spec under its language group | `jupyterlab@7f0e690 sessioncontext.tsx` | complete — an env's spec appears the moment it is registered, named by `display_name` |
| Sidebar surface | `SidePanel` (FILES, RUNNING, SEARCH, TABLE_OF_CONTENTS, DEBUGGER), `ShellSidePane`, `MoreRail` | vscode-jupyter's env views; JupyterLab's panels | absent — T6 adds `ENVIRONMENTS` |
| Terminal reproducibility | env creation yes; spec registration no — the kernel.json write is app-only | `ipykernel install` (dead argv here, ADR-0078) | partial — the `/usr/local/bin` helper is the missing half; T5/T7 |
| Launcher | `ui/launcher/LauncherItems.kt` — one card on the default kernel | `jupyterlab@7f0e690 notebook-extension/src/index.ts` card-per-spec | adapted — open decision whether per-kernel cards ship |

## Deviations

- **The spec is still never `ipykernel install`'s argv.** The create script writes the same
  `kernel.json` `SpaceKernelSpec.installEnvironment` writes — one shape with two writers (the
  script for create and the terminal, Kotlin for Register of foreign envs), pinned by a contract
  test so they cannot drift.
- **Creation is gnu-only**, against vscode-python's interpreter picker: the musl interpreter
  cannot carry the wheels a kernel exists for (ADR-0166), so offering it would offer a worse
  kernel. The terminal's own `python3 -m venv` + Register keeps musl envs reachable.
- **Discovery is bounded at one project level** — the env roots inside each top-level directory,
  not a recursive walk — the same "handful of listings" bound JD-003 chose, and the same bound
  vscode-python keeps outside its opened folder.
- **No implicit discovery stands** (ADR-0078): an environment becomes a kernel through the
  person's create act or an explicit Register, never because it exists.

## Open decisions

- **The spec name and display name for a project env** (T2) is **settled: the env's path minus one
  leading conventional-envs directory, `/` folded to `-`** — `PythonEnvironment.specPath` feeding
  `kernelName`/`kernelDisplayName`. `envs/stats` → `stats` / `Python (stats)` is preserved exactly,
  since a spec on disk keeps the name it was written with; `proj-a/.venv` → `proj-a-.venv`,
  displayed `Python (proj-a/.venv)`, so the picker says which project an env belongs to. Container
  segments after the first stay — `proj-a/envs/stats` → `proj-a-envs-stats`, distinct from
  `proj-a/stats` → `proj-a-stats` — the more unique of the two candidates, a `/`→`-` join not being
  injective; residual folding collisions are refused at Register naming the holder, as before. The
  container list is `ENVIRONMENT_CONTAINER_DIRS` in `domain/model`, shared with the scan so the
  naming rule and the discovery roots cannot drift.
- **The env directory a create makes** (T5) is **settled: `<project>/.venv`** — vscode-python's
  `create_venv.py` and IntelliJ both make `.venv` inside the project/content root, and our
  project is a top-level workspace folder, so the env lives at `<project>/.venv`. Discovery
  already finds it (T3 scans each project folder's env roots), its lifecycle binds to the
  project folder, and T2's naming gives `proj-a/.venv` → `proj-a-.venv` /
  `Python (proj-a/.venv)` for free. `envs/<project>` was rejected: a cleaner spec name but an
  env detached from the project tree that matches no reference's create convention.
- **Whether the launcher gains a card per kernel** (T7, only if cheap). JupyterLab's own pattern
  (`index.ts` above); our `LAUNCHER_ITEMS` collapses to one card today.
- **Whether the panel offers "make Space default"** — recommended no: `SpaceSettings.defaultKernel`
  and its Settings picker already own that act, and a second writer would split the rule
  `effectiveDefaultKernel` keeps in one place.
- **Whether the helper also registers** — *reopened by the backlog's standalone-kernel-creation
  amendment*: `jupydroid-env register` is its own act (T8), an existing interpreter or env
  becoming a kernelspec with no env create, reachable from the panel and reproducibly from the
  terminal. What `register` accepts — an env path, an interpreter path, or both — its spec
  naming, and whether the panel lists interpreters are T8's to settle. T5's settlement stands
  for `create`: it makes the env and its spec in one act, and unknown or missing arguments are a
  usage refusal, exit 2.
- **The discovery predicate's details** (T3) is **settled: a project is any top-level directory
  that is not hidden and not itself a conventional env container** — no `.ipynb` or
  `pyproject.toml` marker is required, the env's own `pyvenv.cfg` is the signal, matching
  vscode-python's per-folder locator which asks for no marker either. Dot-directories are skipped
  as project bases (`.git`, `.ipynb_checkpoints`, `.venv` itself) but not as env children, so
  `proj-a/.venv` is still found; env containers are skipped as bases because their children are
  already environments, which also makes duplicate paths impossible. The bound stays one project
  level: `proj/sub/.venv` and `envs/group/deep` are unfound.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `PythonEnvironment` — project-qualified `kernelName`/`kernelDisplayName`; a project grouping if the panel needs one |
| `domain/port` | `SpaceEnvironments` — `create` or a sibling port in the `HarnessScripts` shape (script text in, `SpaceShell` run out); every port gets a `Fake…` |
| `data/disk` | `DiskSpaceEnvironments` — env roots inside top-level dirs; `pyvenv.cfg` `home` read for the libc |
| `data/space` | `SpaceKernelSpec.installEnvironment` — libc-aware spec env; the `/usr/local/bin` helper's install step beside `install` |
| `data/asset` | the create/register script (`environment-create.sh` or similar), `AssetHarnessScripts`-shaped reader |
| `data/fake` | `FakeSpaceEnvironments`, `FakeSpaceShell`, the new port's fake |
| `ui/environments` or `ui/shell` | `EnvironmentsSidePanel` + its ViewModel — list by project, create flow with streamed progress, Register, gone state |
| `ui/spaces` | `SpaceEnvironmentsSection` — kept for stopped Spaces; shares the row model rather than duplicating it |
| `app/` | `SidePanel.ENVIRONMENTS`, `SidePanelPresentation` label/icon, `ShellSidePane` branch, `SidePanelRail`/`MoreRail` reach |
| `ui/launcher` | per-kernel cards, only if the open decision adopts them |
| `di/AppContainer`, `JupydroidApplication` | wiring — `spaceShell`, `spaceEnvironments`, the scripts port already exist |
| unit tests | `PythonEnvironmentTest`, `DiskSpaceEnvironmentsTest`/contract, `SpaceKernelSpecTest`, the new port's contract, ViewModel tests, `SidePanel`/`ShownPanel`/`MoreRail` tests |
| instrumented tests | the panel's rows and acts — Register enabled iff ipykernel, create flow states |
| `.maestro/` flow + `scripts/` driver | `jd-103-project-environments` |

## Tasks

- [x] T1 — **Measure the kernel model on the tablet.** On `SM-X216B`: two sessions on `python3`
  start two kernels (`GET api/kernels`); a notebook's `metadata.kernelspec.name` becomes the
  session's `kernel.name`; `GET api/kernelspecs` is one server-global catalogue; and
  `/opt/gnu/python/bin/python3.12 -m venv` plus a kernel start inside it is measured — the facts
  the PRD's Who-else section states from source, confirmed on the device. The reproducing command
  goes in the commit body.
- [x] T2 — **Project-aware environment model.** `PythonEnvironment`'s name and display rules
  cover `proj/.venv`-shaped paths: deterministic, unique, `_kernel_name_pat`-safe,
  `envs/stats` → `stats` preserved. Open decision 1 is settled
- [x] T3 — **Discovery reaches project envs.** `DiskSpaceEnvironments` scans the env roots inside
  each top-level workspace directory, bounded; dot-dir and project-predicate details settled with
  fixtures. Open decision 6 is settled
- [x] T4 — **The spec env follows the env's libc.** `pyvenv.cfg` `home` picks the glibc or musl
  env map for `installEnvironment`; the exact glibc map is measured on the tablet. A hand-made
  gnu env then Registers into a kernel that actually starts
- [x] T5 — **Create an environment from the app.** The script asset, its port, the
  `/usr/local/bin` install, the `SpaceShell` run with streamed progress, and auto-registration —
  red-first at every layer. Open decisions 2 and 5 are settled
- [x] T6 — **The Environments panel.** `SidePanel.ENVIRONMENTS` end to end: enum, label, icon,
  panel UI, ViewModel, rail and `MoreRail` reach at one pane; instrumented tests for what is
  visible to press
- [ ] T7 — **Per-kernel launcher cards** — only if the open decision adopts them; otherwise this
  task is struck and the demo covers the picker alone
- [ ] T8 — **`jupydroid-env register` — kernel creation as its own act.** An existing interpreter
  or env becomes a kernelspec every notebook's picker offers: the script's `register` verb, the
  port and panel reach the settled open decision gives it, red-first at every layer, verified on
  the tablet by a kernel created for an existing interpreter with no env create at all
- [ ] T9 — **The demo on `SM-X216B`, and the ship.** The numbered Demo above as a Maestro flow with
  evidence; the durable decisions become an ADR; the PRD is deleted and the backlog edited

## Out of scope

- Conda, poetry, pipenv and hatch environments — none ships in the image, and no reference
  measurement covers them under proot (carried from JD-003).
- Creating a **musl** environment from the app's UI — the terminal's `python3 -m venv` + Register
  remains the route, deliberately.
- Deleting an environment or its spec from the app — `jupyter kernelspec remove` in the terminal
  stays the cleanup, as JD-003 left it.
- Installing, listing or removing packages from an app screen — pip's own output in the terminal
  or a `%pip` cell is the record.
- Per-notebook environment binding beyond `metadata.kernelspec` — shipped in JD-005.
- Kernels for other languages.
- Recursive project discovery — the bound in Trade-offs is the design, not a gap to close later.
