# JD-001 — a Space's runtime environment is its own

Milestone: `M1` · State: `in-progress` · Depends on: — · Closes: —

## Outcome

After this item, a process launched "in" a Space — its `jupyter_server`, its kernels, its
terminals, its `pylsp`, and everything `pip` touches — sees that Space's own directories:
`PYTHONUSERBASE=<space>/pkgs`, `HOME=<space>/home/jupydroid`, `JUPYTER_RUNTIME_DIR=<space>/run`,
`TMPDIR=<space>/tmp`. Two Spaces' packages, homes, kernel connection files and scratch cannot
collide, because each environment is derived from `files/spaces/<id>` — while the interpreter,
stdlib, shell and CA bundle stay shared off `base`'s rootfs.

## Demo

Who: **SD2** on device `SM-X216B` (arm64 — real kernel, LSP and pip; the x86_64 emulators would
prove a fake, per ADR-0001). Prereq: the app installed via `./gradlew installDebug` on a synced
repo. The driver is `scripts/jd-001-per-space-runtime-driver.sh`; its artefacts go to
`evidence/jd-001-per-space-runtime/`.

This item changes no pixels, so the proof is a runtime script plus flow re-runs, not a new flow:

1. **`rm -rf files/spaces/space-1`** and capture the state the reference case is measured
   against: `ls files/spaces/` and base's `pkgs/` count.
2. **Run `PerSpaceRuntimeDeviceTest`** (`am instrument -e class`, per
   `scripts/p5b-language-server-driver.sh`). It seeds `spaces/space-1/config.json` itself —
   the driver's `rm -rf` is a *clean* state, not a seeded one, so no repository call is needed:
   the disk scan `DiskSpaceRepository` already does would count the dir either way. The test:
   a. `spaceServerLauncher(context).start(space-1)` against a booted dir containing only `config.json`
      — `space-1/run/jupyter_server.log` appears (the server boots from a Space that was never
      provisioned ahead of time).
   b. GET `api/status` → 200; assert `space-1/run/jupyter-*.json` (kernel/connection files land
      in the Space's own `JUPYTER_RUNTIME_DIR`) and `base/run/` untouched.
   c. Start a python3 kernel on that server and execute
      `[sys.executable, os.environ["HOME"], tempfile.gettempdir(), site.USER_BASE]` — all four
      resolve inside `files/spaces/space-1`.
   d. `GET api/terminals` on space-1's server → 200 (its `TerminalsManager` is what proves a
      Space-rooted process survives the runtime's seccomp filter — the app-side sweep is
      `BusyBoxAppletsDeviceTest`'s `seccompRefusalsIn`, and `TerminalServiceTest` already
      shows the terminals API answering on a real server).
   e. `start(base)` on the same launcher in the same test — base unaffected by space-1 existing.
3. **Launch the app** and re-run `p8b-space-packages.yaml` into
   `evidence/jd-001-p8b-space-packages/` — the terminal installs `rich` and the pip-list file
   lands under `files/spaces/base/pkgs` while a `space-1/` exists beside `base/` on disk.
   `read_ws` now also asserts `files/spaces/space-1/pkgs/` contains *no* `rich`.
4. **Re-run `p7e-1-space-python.yaml`** into `evidence/jd-001-p7e-1-space-python/` — the
   kernel/terminal smoke proves base still runs through the generalised runtime.
5. **Save** `evidence/jd-001-per-space-runtime/{instrumentation.txt,server.log,
   space-1-tree.txt,runtime-dir.txt,kernel-env.out}` and the two flow dirs as committed
   evidence. The binary answer is `grep "OK (" instrumentation.txt`; reviewable artefacts are
   the JSON the test wrote into its own files and the `find space-1` tree.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
| --- | --- | --- | --- |
| `jupyterlab` | **PARTIAL** | `packages/services/src/kernelspec/restapi.ts` (`ISpecModel.env`), `kernelspec/validate.ts` | kernelspecs may carry `env` — per-kernel, applied server-side; not per-server dirs |
| `vscode` | **PARTIAL** | `src/vs/platform/terminal/common/terminal.ts` (`ITerminalProfile.env`/`IShellLaunchConfig.env`) | per-profile/per-entry env overrides |
| `vscode-jupyter` | **FULL** | `src/kernels/raw/launcher/kernelEnvVarsService.node.ts` | merges kernelspec env → `process.env` → workspace `.env` → interpreter activation per kernel launch |
| `intellij-community` | **PARTIAL** | `platform/execution-impl/.../EnvironmentVariablesData.java`; `SimpleProgramParameters.setupEnvs` | per-run-config envs; per-module SDK for the process itself — not per-env dirs |
| `termux-app` | **PARTIAL** | `termux-shared/.../environment/TermuxShellEnvironment.java`; `TermuxInstaller.java` | layered env construction off one `$PREFIX` — the layer boundaries are right, but there is only ever one environment |
| `winlator` | **FULL** | `container/Container.java` (`envVars`, `.container` keys); `container/ContainerManager.java` | per-container env bag + per-container dir extracted from `container_pattern.tzst` at create |
| the shipped rootfs | — | `tools/rootfs/Dockerfile`, `build.sh` | the shared layer itself: ships `home/`, `home/jupydroid/`, `run/`, `tmp/` as bare dirs; `pkgs/` and `.jupydroid/` are runtime-created |

Count: **2 of 7** implement it fully; 4 of 7 partially; the rootfs row is the shared layer this
item generalises around, not an implementation of it.

## Implementation inventory

For every FULL or PARTIAL reference — the files an implementer must open:

### winlator @ `fb66541b` (`reference/research_repos/winlator`)

| Concern | File |
| --- | --- |
| Domain model | `app/.../winlator/container/Container.java` — `envVars` field; `DEFAULT_ENV_VARS` (a space-separated `NAME=value` string: `ZINK_… mesa_glthread=true WINEESYNC=1`); `.container` JSON keys `envVars`/`extraEnvVars`/`rootfsPath` |
| Port/protocol | `app/.../winlator/core/EnvVars.java` — the bag itself: `EnvVars(String)` splits on `" "`, so no value may carry a space |
| Adapter | `app/.../winlator/container/ContainerManager.java` — `createContainerAsync` → `extractContainerPatternFile` (pattern `.tzst` unpacked into the container dir); `activateContainer` symlinks `envContainers/rootfs` into `XServerDisplayActivity`'s env |
| UI entry point | `app/.../winlator/XServerDisplayActivity.java:481-503` — merge order: activity base env → `container.getEnvVars()` → shortcut extra vars |
| Persisted state | `.container` JSON inside the container dir + `envContainers/` store |
| Edge cases | `checkObsoleteOrMissingProperties` merges newly-added default keys into stored `envVars` on load — a modelled env evolves by merge, ours evolves by re-derivation |

### termux-app @ `3df69d1d` (`reference/research_repos/termux-app`)

| Concern | File |
| --- | --- |
| Env construction | `termux-shared/.../environment/TermuxShellEnvironment.java` — `HOME`, `PREFIX`, `TMPDIR`, `PATH`, `LD_LIBRARY_PATH` off `TermuxConstants`; `TermuxAppShellEnvironment.java` for app-layer vars; `AndroidShellEnvironment.java` as the base layer |
| Provisioning | `TermuxInstaller.java` — stages the bootstrap into `files/usr`; `TermuxConstants` names `FILES_DIR_PATH`/`PREFIX_PATH`/`HOME_PATH`/`TMP_PATH` |
| The limit | one `$PREFIX` per install — layering exists so *sources* compose, not so a second env can exist |

### jupyterlab @ `7f0e690` (`reference/research_repos/jupyterlab`)

| Concern | File |
| --- | --- |
| Env surface | `packages/services/src/kernelspec/restapi.ts` — `ISpecModel.env`; `kernelspec/validate.ts` — `env` validated as part of the spec |
| Applied by | jupyter_client's kernel provisioning — server-side, inside our own rootfs (`tools/rootfs/requirements.lock`) |
| Not ours | `packages/services/src/workspaces/` is UI state dirs, not runtime env |

### vscode @ `3f3380c` (`reference/research_repos/vscode`)

| Concern | File |
| --- | --- |
| Env surface | `src/vs/platform/terminal/common/terminal.ts` — `ITerminalEnvironment`, `ITerminalProfile.env`, `IShellLaunchConfig.env`; `terminalProcess.ts` applies them |

### vscode-jupyter @ `f01d80ca` (`reference/research_repos/vscode-jupyter`)

| Concern | File |
| --- | --- |
| Env merge | `src/kernels/raw/launcher/kernelEnvVarsService.node.ts` — for a raw kernel launch: kernelspec `env` → `process.env` → workspace `.env` file → interpreter activation script output |
| Interpreter activation | `src/platform/interpreter/environmentActivationService.node.ts` |

### intellij-community @ `406a27a8` (`reference/research_repos/intellij-community`)

| Concern | File |
| --- | --- |
| Run env | `platform/execution-impl/src/com/intellij/execution/configuration/EnvironmentVariablesData.java`; `SimpleProgramParameters.setupEnvs` |
| Per-env binary | `platform/projectModel-api/.../SdkTypeId.java` — per-module SDK selects the process itself, not per-env writable dirs |

## Trade-offs and what we adopt

| Axis | What the references do | Trade-off | Our choice |
| --- | --- | --- | --- |
| Where env vars come from | winlator: a user-editable `envVars` bag per container | an editable bag can disagree with disk truth (a `HOME` pointing somewhere else); a derived one cannot | **derived** — a Space's env is its directory; no `EnvVars`-style bag |
| When dirs materialise | winlator: extract `container_pattern.tzst` at `createContainerAsync` | create-time provisioning is self-describing, but `create` is not the only way a dir becomes a Space — the disk scan counts any dir with `config.json`, and a T2 `ensure` would ship dead code before T3 can reach it | **first launch** — the same lazy moment that starts a server installs the Space's writable skeleton; the scan stays the truth |
| One server per Space | jupyterlab/vscode-jupyter put per-Space config in *kernelspec env*; ours puts it in the server's own env | the kernelspec arm is JD-004's priced question (one server/Space vs shared server + spec env); this item changes neither the one-server lifecycle nor answers it | unchanged — `start(space)` honours its operand; whether two ever run concurrently is JD-004 |
| TMPDIR | shared `base/tmp` would leak one Space's scratch into another's | per-Space `tmp` is the same derivation as `HOME` — no reason to exempt it | **per-Space** — `TMPDIR=<space>/tmp` |
| `JUPYTER_RUNTIME_DIR` | today injected by `JupyterServerLauncher` as `extraEnv` | keeping it launcher-side means terminals/LSP/`pip` launched in the same Space see a different runtime dir | **in `environment()`** — every process in the Space inherits it |

## Gap analysis — ours today vs the references

| Concern | Ours today | Best reference | Gap |
| --- | --- | --- | --- |
| env derivation | `SpaceRuntime.environment()` — one `baseDir` plays both roles (rootfs + writable) | winlator's layered env | env fixed for the one Space — this item's core split |
| space id → dir | `File(spacesDir, id)` inline in `DiskSpaceRepository` and duplicated in launcher composition | `ContainerManager`'s scan + `envContainers` | no one home for the layout — two callers spell it by hand |
| provisioning | none — `DiskSpaceRepository.create` writes only `config.json`; the rootfs tarball carries `base`'s `home/`/`run/`/`tmp/` | `extractContainerPatternFile` | `SpaceDirs.ensure()` at first launch |
| server runtime dir | `File(baseSpaceDir, "run")` baked into `JupyterServerLauncher`'s constructor | per-container dirs | per-Space `run/`; kernels write `jupyter-*.json` there |
| launcher's operand | `JupyterServerLauncher.start(space)` ignores `space` — `runtimeDir`/`rootDir`/`shell` baked to `base` | `activateContainer` reads through the container's symlink | honour the operand |
| LSP env | `StdioLanguageServer`'s pool is keyed by `space.id`, but `spawn` receives only the base `InstalledSpace` — a `space-1` pylsp would run with base's `PYTHONUSERBASE`/`HOME` | per-container env | `spawn` gains `space` |
| terminal env | `TerminalGateway` shells run through the same `environment()` map | — | fixes itself once env is per-Space and the gateway receives a real `Space` |
| kernel env | kernels inherit the server's env | kernelspec `env` field (jupyterlab) | inherited is sufficient — the server *is* the Space's own env |

## Deviations

- **No user-editable env bag.** Winlator's `envVars` exists because containers run arbitrary wine
  builds; a Jupydroid Space's env is *derived from its directory*, so it can never disagree with
  disk. `EnvVars(String)` couldn't carry a value containing a space anyway (it splits on `" "`).
- **No UI entry point ships.** `BASE_SPACE` remains the only Space a screen can reach — JD-002's
  surface and JD-005's switching make a second Space reachable on device. This item's claim is
  proven at the runtime layer (the device test drives `space-1` through the same composition the
  app will use).
- **`languageServer.stop(BASE_SPACE)` stays the idle backstop's arm.** Until switching exists no
  second Space's pylsp can be running, so the app-layer stop still names `base`; the port itself
  is Space-agnostic after T4.
- **`installSpace`/`installedSpace`/`pythonRunner` keep returning `base`'s seam.** They are the
  *installation* half (unchanged rootfs); the *process* half is what gains `space`.

## Open decisions

- **`SpaceRuntime` signature** — `rootfsDir` defaulting to `spaceDir` keeps every existing call
  site valid (base = both roles on one dir); requiring both arguments would make the compiler
  audit every construction. Defaulted.
- **The launcher's new constructor shape** — `(Space) -> SpaceRuntime` + `(Space) -> SpaceDirs`
  factories (so `pythonRunner` keeps `base`'s stable runtime while `runtimeFor` returns a
  fresh per-Space one) vs fields `(spacesDir, rootfsDir, interpreter, dns)`. Factory pair.
- **`processBuilder`'s `extraEnv` parameter** — survives once `JUPYTER_RUNTIME_DIR` moves in
  (terminals/`ls -l` callers may still pass extras), but the launcher stops using it.
- **Where `ensure()` is invoked** — inside `start(space)`/`spawn(space)` (any first launch
  provisions) vs a provisioning step callers must remember. Inside — a dir that exists but was
  never launched stays skeletal, which matches "dirs materialise when the Space first runs".

## Surface

| Layer | Files |
| --- | --- |
| domain/model | — (`Space`, `SpaceConfig` unchanged) |
| domain/port | — (signatures already carry `Space` end-to-end) |
| data/space | `SpaceRuntime.kt`, `JupyterServerLauncher.kt`, `StdioLanguageServer.kt`, `AndroidBaseSpace.kt`, `SpacesDirectory.kt` (`spaceDirIn`), new `SpaceDirs.kt` |
| data/disk | `DiskSpaceRepository.kt` (route through `SpaceDirs`) |
| data/fake | — (`FakeServerLauncher` already takes `space`) |
| ui | — |
| di | `JupydroidApplication.kt` (builder rename) |
| unit tests | `SpaceRuntimeTest`, `JupyterServer*Test`, `StdioLanguageServer*Test`, `SpacesDirectoryTest`, new `SpaceDirsTest` |
| instrumented | new `PerSpaceRuntimeDeviceTest`; `SpaceProcessRun` generalised to take an env |
| maestro/scripts | `scripts/jd-001-per-space-runtime-driver.sh`; re-runs of `p8b-space-packages` + `p7e-1-space-python` under `EVIDENCE_ROOT`; no new `.maestro/` flow |

## Tasks

- [x] T1 — `SpaceRuntime` derives env from `(spaceDir, rootfsDir = spaceDir)`;
      `JUPYTER_RUNTIME_DIR` joins `environment()`; the launcher drops its `extraEnv` for it.
- [x] T2 — `spaceDirIn` (one home for the `<id>` → dir convention); `DiskSpaceRepository` and
      `baseSpaceDir` route through it.
- [x] T3 — `SpaceDirs.ensure()` provisions `home/jupydroid`, `run`, `tmp`, `pkgs`, invoked first in
      `start(space)`; `JupyterServerLauncher.start(space)` honours its operand: per-Space `runtimeDir`,
      `rootDir`, `env` (the shell stays one constructor field — after T1 it lives in the shared
      rootfs); composition rewired: `baseSpaceServerLauncher` → `spaceServerLauncher(context)`.
- [x] T4 — `StdioLanguageServer.spawn` gains `space`: per-Space env + `cwd`, `ensure()` first.
- [ ] T5 — device test `PerSpaceRuntimeDeviceTest` + driver + `evidence/jd-001-*`; re-run
      `p8b`/`p7e-1`; `SpaceProcessRun`/`LanguageServerDeviceTest` generalised to take an env (moved
      from T3: no caller until this test); tidy `SpaceDirs` API surface.

## Out of scope

- JD-002 (any Space entry point a user can reach), JD-003 (notebooks bound to a Space),
  JD-004 (running two Spaces' servers at once / which arm wins), JD-005 (switching a live
  Space) — this item is their precondition, not their work.
- The GAP ledger items this unblocks: GAP-145/146/147/148/150 (duplication, launch overrides,
  size claim, interpreter pick, scratch clearing) — per-Space dirs are their mechanism, not
  their fix.
- User-editable env bags (winlator's `envVars`) — ours is derived, by design.
- Per-Space kernel env via kernelspec `env` — the JD-004 arm's surface.
