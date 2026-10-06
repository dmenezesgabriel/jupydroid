# JD-009 — Configure a Space

Milestone: `M1` · State: in-progress · Depends on: JD-005 (shipped) · Closes: —

## Outcome

From the home, **Settings** on a stopped Space edits its idle-shutdown time, its own environment
variables (the paths the app derives stay locked), a thread cap for the numeric libraries, and a
startup script — and every process in that Space honours them from its next start.

## Demo

Who: a human on **`SM-X216B`**. Cells run, so rule 2 requires the arm64 tablet. Thesis is `space-1`.

1. Cold start: install and launch. Both rows read **Stopped** and offer **Settings**. Run base: its
   Settings disables, Thesis's stays enabled.
2. Thesis → Settings. Set idle shutdown to 2 min, add `GREETING=hello`, try to add `HOME=/tmp` — it is
   refused, naming `HOME` as derived — set the thread cap to 1, and write a startup script
   `echo started > ~/startup.txt` in its code editor, numbered and coloured as bash. Save.
3. Kill and relaunch: Thesis's Settings shows the same values (`run-as … cat files/spaces/space-1/config.json`).
4. **Run** Thesis, **Open** it. `~/startup.txt` is listed in the explorer.
5. Run `import os; print(len(os.environ['GREETING']))`, which prints `5` — a length, so the assertion
   cannot match the cell's own source.
6. In a fresh kernel, run `import os; n = len(os.listdir('/proc/self/task')); import numpy; print(len(os.listdir('/proc/self/task')) - n)`.
   It prints `0`, because OpenBLAS capped at 1 starts no workers. The same cell on base, uncapped,
   prints the core count minus one: `7` on this tablet's 8 cores, as T3 measured.
7. In a terminal, `echo ${#GREETING}` prints `5`.
8. Close the notebook's tab and return to the home with the app in front. Within ~3 min Thesis reads
   Stopped by itself (2 min idle + the 60 s cull poll); base, at its default, does not.
9. Thesis → Settings, startup script `exit 3`. **Run**: the row stays Stopped and the home shows the
   script's exit status and its last output.

Commit the screenshots, `maestro.log` and the `run-as` captures under
`evidence/jd-009-configure-a-space/`. `logcat` stays local.

## Who else implements this

Read from code at the SHAs in [`../README.md`](../README.md). Three products outside the pinned set
were read from their documentation, because they are the closest prior art for a per-environment
startup script and idle time.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | The frontend edits nothing about the server's environment; it is server config (`jupyter_server_config.py`) and a kernelspec's `env` |
| `vscode` | PARTIAL | `vscode@3f3380c src/vs/workbench/contrib/terminal/common/terminalConfiguration.ts` (`terminal.integrated.env.linux`), `common/terminalEnvironment.ts` (`mergeEnvironments`) | Variables for the terminal only, merged over the process env; `null` deletes one. Nothing is locked |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80c src/kernels/raw/launcher/kernelEnvVarsService.node.ts`, `src/kernels/kernel.ts` (`getUserStartupCommands`), `package.json` (`jupyter.runStartupCommands`) | Process env, then the interpreter's or the `.env` file's, then the kernelspec's — kernel vars win, nothing locked. "Startup commands" are Python run silently in **each kernel**, not a shell script per environment |
| `intellij-community` | PARTIAL | `intellij-community@406a27a platform/execution-impl/src/com/intellij/execution/configuration/EnvironmentVariablesComponent.java` | Variables per **run configuration**, with "include system environment" as a toggle — per launch, not per environment |
| `termux-app` | ABSENT | `termux-app@3df69d1 termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java` | The app derives `HOME`, `PREFIX` and the rest and offers no editor; a user's variables live in the shell's own rc files |
| `winlator` | FULL | `winlator@fb66541 app/app/src/main/java/com/winlator/container/Container.java` (`envVars`, `cpuList`), `ContainerDetailFragment.java`, `contentdialog/AddEnvVarDialog.java`, `xenvironment/components/GuestProgramLauncherComponent.java` | A per-container variable bag and CPU list, edited on the container's detail screen; the bag is `putAll` **after** the derived `TMPDIR`/`PATH`, so a user value overrides a derived one |
| the shipped rootfs | PARTIAL | `usr/lib/python3.12/site-packages/jupyter_server/serverapp.py` (`shutdown_no_activity_timeout`, `shutdown_no_activity`), `jupyter_server/services/kernels/kernelmanager.py` (`cull_idle_timeout`), `numpy.libs/libscipy_openblas64_-*.so`, `scipy.libs/libscipy_openblas-*.so` | Both idle timers are integer seconds where `0` disables them. Both OpenBLAS builds read `OPENBLAS_NUM_THREADS`, `GOTO_NUM_THREADS` and `OMP_NUM_THREADS` (`strings`) |

Outside the pinned set:

- **SageMaker Studio spaces.** Idle shutdown is a per-space setting,
  `SpaceSettings.AppLifecycleManagement.IdleSettings.IdleTimeoutInMinutes`
  (<https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_SpaceIdleSettings.html>). A
  *lifecycle configuration* is a shell script run on each start of the space's app; only its stdout
  and stderr are logged, and one that runs past **5 minutes** is an error
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/jl-lcc-debug.html>).
- **Dev containers.** `containerEnv` "sets or overrides" variables for every process; `postStartCommand`
  runs "each time the container is successfully started", and "if one of the lifecycle scripts
  fails, any subsequent scripts will not be executed"
  (<https://containers.dev/implementors/json_reference/>).
- **JupyterHub.** `Spawner.environment` "has highest priority, allowing override of 'default' env
  variables", and `pre_spawn_hook` runs before the server starts
  (<https://jupyterhub.readthedocs.io/en/stable/reference/api/spawner.html>).

**Count:** 1 of 7 configures an environment as a unit (Winlator), 3 more configure variables for one
launch or one kind of process. **None locks a derived variable**: every one lets the user's value win.
No pinned reference runs a shell script per environment; SageMaker and dev containers both do, on
every start.

## Implementation inventory

### winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40

| Concern | Files |
|---|---|
| Domain model | `container/Container.java`: `envVars` (one space-separated string), `cpuList`, `DEFAULT_ENV_VARS` |
| Persisted state | `Container.saveData` / `loadData`: the same `.container` JSON as every other field; `checkObsoleteOrMissingProperties` merges new defaults into an old bag (`appVersion < 16`) |
| UI entry point | `ContainerDetailFragment.java` (create and edit share one screen), `contentdialog/AddEnvVarDialog.java` |
| Validation | `AddEnvVarDialog`: `trim().replace(" ", "")` on name and value — no value may hold a space, because `core/EnvVars.java` splits on it |
| Applied at launch | `XServerDisplayActivity.java` `envVars.putAll(container.getEnvVars())`, then `GuestProgramLauncherComponent.java` puts derived vars and `putAll`s the bag over them |
| CPU list | `core/Win32AppWorkarounds.java` → `ProcessHelper.getAffinityMask`: an affinity mask, applied by wine to the guest process |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Merge order | `src/kernels/raw/launcher/kernelEnvVarsService.node.ts`: `Object.assign(mergedVars, interpreterEnv, kernelEnv)` — kernel vars win |
| Startup | `src/kernels/kernel.ts` `getUserStartupCommands`, executed silently after the kernel starts |

### jupyter_server 2.20.0, in the shipped rootfs

| Concern | Files |
|---|---|
| Server idle | `serverapp.py` `shutdown_no_activity_timeout` (default `0`), `shutdown_no_activity`: returns early while any kernel or terminal exists |
| Kernel idle | `services/kernels/kernelmanager.py` `cull_idle_timeout` (`0` or lower disables culling), `cull_interval`, and a per-kernelspec `metadata.cull_idle_timeout` override |

**Where they disagree:** Winlator, VS Code, vscode-jupyter, IntelliJ and JupyterHub all let a user's
value override a derived one. ADR-0072 exists because a `HOME` that disagrees with the disk breaks
the Space, and this item keeps that guarantee. Startup is a **shell script per environment** in
SageMaker and dev containers, and **Python per kernel** in vscode-jupyter.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where settings live | winlator → the container's own JSON | One record vs a settings store beside it | **New fields on `SpaceConfig`, in the Space's `config.json`** (ADR-0010). A copied or deleted Space takes its settings with it |
| Which idle timer | rootfs → `shutdown_no_activity_timeout` and `cull_idle_timeout`; SageMaker → per space | Per server vs the app-wide 30 s leave backstop | **The server's own two**, one minutes value written to both at launch. The leave backstop (`AppContainer.IDLE_STOP_GRACE`) stays app-wide |
| User vs derived variables | winlator, vscode, vscode-jupyter, JupyterHub → the user wins | Freedom vs a Space that cannot disagree with its disk | **Derived names are locked**: the editor refuses one, naming it, and the launch layers user vars under them. A deviation from every reference, for ADR-0072's reason |
| Variable syntax | winlator → space-separated, no spaces in values | Simple vs any value | **A name/value list**, a name matching `[A-Za-z_][A-Za-z0-9_]*`, any value. Winlator's no-space rule is a parser limit we do not have |
| Thread cap | winlator → a CPU affinity list; the rootfs's OpenBLAS → `OPENBLAS_NUM_THREADS`/`OMP_NUM_THREADS` | Affinity (scheduler) vs thread count (libraries) | **One number, written as `OPENBLAS_NUM_THREADS` and `OMP_NUM_THREADS`**, both then locked in the editor. Unset leaves the libraries' own default. Thread count is what limits a numpy matmul's memory and heat on a tablet |
| Startup script | SageMaker, dev containers → shell on each start, failure stops the start; vscode-jupyter → Python per kernel | Per environment vs per kernel | **Shell, under proot with the Space's environment, before `jupyter_server`**, on every start. A non-zero exit or a timeout fails the start, and the home shows the exit status and the output's tail |
| When editable | winlator → while not running (one runs at a time) | Live edit vs honest state | **Only while Stopped**, the mirror of Open, which is enabled only while Running. Nothing is applied to a running process |

ADR at the end of the item: *a Space stores its own settings, and derived variables stay locked*. It
amends ADR-0072, which forbids a stored environment per Space.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Record | `app/src/main/java/com/jupydroid/app/domain/model/SpaceConfig.kt`, `data/disk/DiskSpaceRepository.kt` (`StoredSpaceConfig`) | `winlator@fb66541 container/Container.java` | present and partial: no settings fields |
| Port | `app/src/main/java/com/jupydroid/app/domain/port/SpaceRepository.kt` (`spaces`, `create`, `rename`, `delete`) | — | absent: no op writes settings |
| Home | `app/src/main/java/com/jupydroid/app/ui/spaces/SpacesHomeScreen.kt` (Run/Stop, Open, Rename, Delete) | `winlator@fb66541 ContainerDetailFragment.java` | absent |
| Environment | `app/src/main/java/com/jupydroid/app/data/space/SpaceRuntime.kt` (`environment()`, derived only) | `winlator@fb66541 GuestProgramLauncherComponent.java` | absent by decision (ADR-0072) |
| Idle timers | `app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt` (`jupyterServerArgs`: `300`, `300`, `60` fixed) | rootfs `serverapp.py` | present and partial: one value for every Space |
| Launch reads config | `data/space/AndroidBaseSpace.kt` `spaceServers` (`spaceWithId(id)`), `JupyterServerLauncher.start(space: Space)` | — | absent: the launcher sees a `Space`, never a `SpaceConfig` |
| Failed start on the home | `data/space/JupyterServerLauncher.kt` (`failStart` reads `jupyter_server.log`), `ui/spaces/SpacesViewModel.kt` (`failure`) | SageMaker LCC log | present: a refusal already reaches the home verbatim |
| Thread cap | — | — | absent |

## Deviations

- **Derived variables are locked**, unlike every reference. A Space's `HOME`, `TMPDIR`, `PATH`, the
  `PIP_*` set, `LD_PRELOAD` and the rest are what make its disk and its processes agree (ADR-0072).
- **No CPU affinity.** Winlator's `cpuList` is applied by wine. The thread cap is the part that limits
  what numpy does on a tablet.
- **Startup output goes to the home, not to a log service.** SageMaker sends it to CloudWatch; here
  there is only the screen and the Space's own `run/`.

## Open decisions

- **How the launcher reads settings — settled in T1.** `JupyterServerLauncher.start` asks its
  `settingsFor` on every start, and `spaceServers` answers from the `SpaceRepository` the home writes
  through. Both start paths go through `SpaceServer.connection()` → `start`, so neither caller hands
  in values. Held by `JupyterServerLauncherSpaceTest.eachStartReadsTheSpacesIdleShutdownAsItIsThen`.
- **`pylsp` — settled in T2: yes.** It is not a child of the server, so it does not inherit its
  environment; `baseSpaceLanguageServerPort` reads the Space's stored settings at each spawn, so a
  `PYTHONPATH` resolves an `import` the same in the editor as in a kernel. No spawn can hold stale
  variables: Settings is editable only while Stopped, and leaving a Space stops every `pylsp`.
- **Where the script's output goes, and its timeout — settled in T4.** `run/startup.log`, truncated
  each start beside `jupyter_server.log`, its last 2000 characters in the home's refusal. 5 minutes,
  SageMaker's limit, enforced *inside* the Space by busybox `timeout -s KILL` under proot's
  `--kill-on-exit`, which also kills anything the script backgrounded. An outer shell carries the
  kill out as status 137, because proot reports a signalled tracee as `0`
  (`SpaceStartupScriptDeviceTest`).
- **The idle field's unit and range — settled in T1.** Whole minutes, `0..1440`, default 5, `0`
  meaning never; written as seconds to both timers. The ceiling keeps a typo from meaning a year.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `SpaceConfig` (idle minutes, variables, thread cap, startup script), the locked-name rule |
| `domain/port` | `SpaceRepository` (`configure`) |
| `data/disk` | `DiskSpaceRepository` (`StoredSpaceConfig`) |
| `data/fake` | `FakeSpaceRepository` |
| `data/space` | `SpaceRuntime` (user vars, thread cap), `JupyterServerLauncher` (`jupyterServerArgs` idle, the startup script), `AndroidBaseSpace.kt` (`spaceServers`), `StdioLanguageServer.kt` (`startPylsp`) |
| `ui/spaces` | `SpacesHomeScreen` (Settings on a stopped row), `SpacesViewModel`, a settings screen |
| `syntax`, `theme` | `ShellSyntax`, `syntaxForFile`; `SyntaxPalette.variable` |
| unit tests | `SpaceRepositoryContract`, `DiskSpaceRepositoryTest`, `SpacesViewModelTest`, `SpaceRuntimeTest`, `JupyterServerLauncherSpaceTest` |
| instrumented tests | `SpacesHomeScreenTest` (Settings enabled iff stopped, a locked name refused) |
| `.maestro/` flow + `scripts/` driver | `jd-009-configure-a-space` |

## Tasks

- [x] T1 — **Settings** on a stopped row opens the Space's settings with **idle shutdown** as the
  first field. `SpaceRepository.configure` runs across domain, port, disk, fake and UI. The launch
  reads it into `jupyterServerArgs`'s two timers. Open decisions 1 and 4 are settled
- [x] T2 — **Environment variables**: added, edited and removed in settings, with a derived name
  refused by name. They are layered under the derived environment in `SpaceRuntime`, so the server,
  kernels and terminals see them. `pylsp` is settled
- [x] T3 — **Thread cap**: one number written as `OPENBLAS_NUM_THREADS` and `OMP_NUM_THREADS`, both
  locked in the editor. On device, the kernel's `/proc/self/task` count after a matmul is measured
  capped and uncapped
- [x] T4 — **Startup script**: run under proot in the Space's environment before the server on every
  start. A non-zero exit or the timeout fails the start, with the status and the output's tail on the
  home. Open decision 3 is settled
- [x] T5 — **Startup script editor**: the script is edited in the code cell's own editor (line
  numbers, the symbol row) with bash highlighting, a hand lexer after JupyterLab's CodeMirror
  `shell` mode, `$VAR`/`${…}`/`NAME=` in a variable colour. `.sh`, `.bash` and `.ksh` files in
  the file editor take the same highlighter, as JupyterLab's file-type registry maps them
- [ ] T6 — The demo on SM-X216B, and the ADR amending ADR-0072

## Out of scope

- The Space's default kernel, which moves to JD-003, where a second kernel first exists.
- A per-Space grace for the leave backstop. It stays app-wide (`AppContainer.IDLE_STOP_GRACE`).
- Per-entry launch overrides: `GAP-146`.
- CPU affinity.
- Editing the settings of a running Space.
