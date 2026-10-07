# ADR-0077 — A Space stores its own settings, and its derived variables stay locked

Status: accepted
Date: 2026-10-06

## Context

Every Space ran the same way: one idle time for every server (`jupyterServerArgs` fixed at 300 s),
no way to put a variable in front of a kernel, numpy's OpenBLAS starting a worker per core on a
tablet, and nothing to run before the server.
[ADR-0072](0072-a-spaces-environment-is-derived-from-its-directory.md) went further and forbade a
stored environment outright, because a stored `HOME` that disagrees with the Space's disk breaks
the Space.

The references all configure an environment, and all let the person's value win:

- Winlator keeps a per-container `envVars` bag and `cpuList` in the container's own JSON, edited on
  its detail screen, and `putAll`s the bag **after** the derived `TMPDIR`/`PATH`
  (`winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40
  app/app/src/main/java/com/winlator/container/Container.java`,
  `xenvironment/components/GuestProgramLauncherComponent.java`). `contentdialog/AddEnvVarDialog.java`
  strips every space out of a value, because `core/EnvVars.java` splits on one.
- VS Code's `terminal.integrated.env.linux` is merged over the process environment
  (`vscode@3f3380c668d07649b953eb8278354b2f263ea6ff
  src/vs/workbench/contrib/terminal/common/terminalEnvironment.ts` `mergeEnvironments`), and
  vscode-jupyter puts the kernelspec's `env` last (`vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155
  src/kernels/raw/launcher/kernelEnvVarsService.node.ts`). Its "startup commands" are Python run in
  **each kernel** (`src/kernels/kernel.ts` `getUserStartupCommands`).
- JupyterHub's `Spawner.environment` "has highest priority, allowing override of 'default' env
  variables" (<https://jupyterhub.readthedocs.io/en/stable/reference/api/spawner.html>).
- SageMaker Studio makes idle shutdown a per-space setting
  (<https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_SpaceIdleSettings.html>), and its
  lifecycle configuration is a **shell script run on every start** of the space, an error past
  5 minutes (<https://docs.aws.amazon.com/sagemaker/latest/dg/jl-lcc-debug.html>). Dev containers'
  `postStartCommand` also runs on every start, and a failing one stops the scripts after it
  (<https://containers.dev/implementors/json_reference/>).

What the shipped rootfs offers: jupyter_server 2.20.0's `ServerApp.shutdown_no_activity_timeout`
and `MappingKernelManager.cull_idle_timeout` are integer seconds where `0` disables
(`usr/lib/python3.12/site-packages/jupyter_server/serverapp.py`,
`jupyter_server/services/kernels/kernelmanager.py`), and both OpenBLAS builds, numpy's and scipy's,
read `OPENBLAS_NUM_THREADS` and `OMP_NUM_THREADS`.

Measured on `SM-X216B` while this shipped: a Space set to 2 minutes stopped its own server while
`base`, at the default, ran on; a variable saved in Settings reached the server, a kernel, a
terminal and `pylsp`; a kernel under a cap of 1 started no OpenBLAS workers where `base`'s started
7 on 8 cores; and a startup script's `exit 3` refused the start with its status and output on the
home.

## Decision drivers

- A Space's settings belong to the Space: copied or deleted with it, never in a store beside it.
- Whatever a process is told about where it writes must still agree with the disk (ADR-0072's
  driver, unchanged).
- Nothing is applied to a process that is already running.

## Options considered

| Option | Pro | Con |
|---|---|---|
| The person's value wins over a derived one (every reference) | Total freedom | A `HOME` or `PATH` that disagrees with the Space's disk breaks it, which is why ADR-0072 exists |
| Derived names locked: refused in the editor and layered over the person's at launch | The Space cannot disagree with its disk | No way to override a derived path, a deviation from every reference |
| Startup as Python per kernel (vscode-jupyter) | Runs where the code runs | Nothing runs before the server, and a terminal never sees it |
| Startup as a shell script per start (SageMaker, dev containers) | Runs once, before every process, in the Space's own environment | A slow or failing script holds up the start |
| A CPU affinity list (Winlator's `cpuList`) | The scheduler enforces it | Wine applies it there; here the libraries' thread count is what bounds a matmul's memory and heat |

## Decision

**Settings are fields of `SpaceConfig`**, stored in the Space's own `config.json`
([ADR-0010](0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md)) under `settings` —
`StoredSpaceSettings` in `app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceRepository.kt` —
and written through `SpaceRepository.configure`. Every field defaults, so a `config.json` without
them reads the defaults. They are editable **only while the Space is Stopped**: Settings on the
home is Open's mirror, and nothing reaches a running process.

- **Idle shutdown:** whole minutes, `0..1440`, default 5, `0` meaning never. Each start writes the
  one value as seconds to both server timers, since neither fires without the other.
- **Environment variables:** a name/value list in the order added, a name matching
  `[A-Za-z_][A-Za-z0-9_]*`, any value. **Derived names are locked**: the editor refuses one, naming
  it (`SpaceVariables` in `app/src/main/java/com/jupydroid/app/domain/model/`), and `SpaceRuntime`
  layers the person's variables *under* the derived set, so even a name that got past the editor
  loses to the derived value.
- **Thread cap:** one number, written as `OPENBLAS_NUM_THREADS` and `OMP_NUM_THREADS`, both then
  locked like the derived set. Unset leaves the libraries' own default.
- **Startup script:** shell, run under proot in the Space's environment and workspace, **before
  `jupyter_server`, on every start** (`app/src/main/java/com/jupydroid/app/data/space/SpaceStartupScript.kt`).
  Its output goes to `run/startup.log`, truncated each start. It is bounded at 5 minutes, SageMaker's
  limit, by busybox `timeout -s KILL` *inside* the Space under proot's `--kill-on-exit`, which also
  kills what the script backgrounded. An outer shell carries the kill out as status 137, because
  proot reports a signalled tracee as `0`. A non-zero status or the timeout refuses the start, and
  the home shows the status and the log's last 2000 characters.

**Settings are read at each start, never handed in.** `JupyterServerLauncher.start` asks for the
Space's settings on every start, so a value saved while Stopped is what the next Run uses. `pylsp`
is not a child of the server, so it reads the same stored settings at each spawn. A `PYTHONPATH`
then resolves an `import` the same way in the editor as in a kernel.

## Consequences

- This amends ADR-0072: a Space now stores an environment, but only the person's own variables.
  Every derived name is still derived and never stored.
- Unlike every reference, a derived path cannot be overridden. Someone who needs a different
  `PATH` entry adds it in the startup script or the shell's own rc files, which run inside the
  derived environment.
- A startup script slows every start by as long as it runs, up to 5 minutes. A failing one keeps the
  Space Stopped until it is fixed, and Settings stays reachable for exactly that reason.
- No CPU affinity, and no per-Space grace for the app-leave backstop
  (`AppContainer.IDLE_STOP_GRACE` stays app-wide).
- Held by `SpaceRepositoryContract` (`aDerivedOrMalformedVariableIsRefusedByNameAndNothingIsWritten`,
  `anIdleShutdownOutsideTheRangeIsRefusedAndNothingIsWritten`),
  `JupyterServerLauncherSpaceTest` (`eachStartReadsTheSpacesIdleShutdownAsItIsThen`,
  `aFailingStartupScriptFailsTheStartWithItsStatusAndOutputAndStartsNoServer`),
  `SpaceRuntimeTest.aDerivedNameAmongTheSpacesVariablesLosesToTheDerivedValue`, and on device by
  `SpaceStartupScriptDeviceTest.aScriptPastItsTimeoutIsKilledWithWhatItStartedAndExits137`.
