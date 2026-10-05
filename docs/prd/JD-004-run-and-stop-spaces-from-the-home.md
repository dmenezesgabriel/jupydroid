# JD-004 — Run and stop a Space from the home, several at once

Milestone: `M1` · State: in-progress · Depends on: JD-008 (shipped) · Closes: —

## Outcome

From the home, any Space is run and stopped by a control on its row, several run at once, and every
row says Running or Stopped. That includes a server that stopped itself when idle. Stop keeps the
Space's files. Deleting a running Space stops it first.

## Demo

Who: a human on **`SM-X216B`**. Base's cell proves a real kernel, so rule 2 requires the arm64 tablet.
Entering a non-base Space is JD-005, so Thesis's server is checked from outside with `run-as … ps -A`.

1. Cold start: install and launch. The home lists Base and Thesis, and both read **Stopped**.
2. **Run** Base and then **Run** Thesis. Both rows read **Running**. `ps -A` shows two `jupyter_server`
   processes, one under each Space's root.
3. **Open** Base and run `print(6*7)`. It prints `42`. Back to the home, where Base still reads Running.
4. **Stop** Thesis. Its row reads Stopped, `ps -A` shows one server, and `files/spaces/space-1/` is
   unchanged (`run-as … ls`).
5. **Run** Thesis again, then **Delete** it and confirm. The row goes away, and no process under its
   root is left in `ps -A`.
6. Leave the app for more than 30 s with nothing running. Back on the home, every row reads Stopped.
7. Kill and relaunch. Every row reads Stopped, because no server outlives its app (`CLOSED-001`).

Commit the screenshots, `maestro.log` and the `ps`/`ls` captures under
`evidence/jd-004-run-and-stop-spaces/`. `logcat` stays local.

## Who else implements this

Read from code at the SHAs in [`../README.md`](../README.md).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/mainmenu-extension/src/index.ts` (`CommandIDs.shutdown`), `packages/running-extension/src/index.ts` | Stops **its own** server: confirm, `sessions.shutdownAll()` + `terminals.shutdownAll()`, then `POST api/shutdown`. One server, never several |
| `vscode` | ABSENT | — | No notion of a managed environment server |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80c src/kernels/jupyter/launcher/jupyterServerProvider.node.ts`, `jupyterServerStarter.node.ts` | One local server started lazily on first need, cached, and disposed with the extension |
| `intellij-community` | ABSENT | `jupyter/` holds notebook UI only | No server lifecycle in the community tree |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/src/main/java/com/termux/app/TermuxService.java` | Many sessions in one foreground service. The notification counts them ("N sessions"), and an exit action stops them all |
| `winlator` | PARTIAL | `winlator@fb66541 app/app/src/main/java/com/winlator/ContainersFragment.java` (`runContainer`), `XServerDisplayActivity.java` (`exit`, `onDestroy`) | Run per row starts a `singleTask` activity for **one** container. Exit stops its environment |
| the shipped rootfs | PARTIAL | `jupyter_server` 2.20.0 `serverapp.py` (`shutdown_no_activity`), `MappingKernelManager.cull_idle_timeout` | Each server stops itself when idle. That is per process, so it is already per Space |

**Count:** none of the 7 runs several environments' servers at once. Two references have the
stop half (JupyterLab and Winlator), and Winlator has the run half on a list row. SageMaker Studio,
outside the pinned set and cited in ADR-0074, runs many spaces at once with Run and Stop per space.
The backlog asks for "several at once", which is what puts us on SageMaker's side.

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Stop a server | `packages/mainmenu-extension/src/index.ts`, `CommandIDs.shutdown`: sessions and terminals are shut down first, failures are swallowed, then `api/shutdown` is posted |
| Running state | `packages/running-extension/src/index.ts`: kernels and terminals of the one server |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Lazy start, one cached | `src/kernels/jupyter/launcher/jupyterServerProvider.node.ts` |
| Process and dispose | `src/kernels/jupyter/launcher/jupyterServerStarter.node.ts` (`dispose`) |

### termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4

| Concern | Files |
|---|---|
| Several live at once, and a count | `app/src/main/java/com/termux/app/TermuxService.java`: notification text `"N sessions"`, plus `ACTION_STOP_SERVICE` |

### winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40

| Concern | Files |
|---|---|
| Run from the list row | `app/app/src/main/java/com/winlator/ContainersFragment.java` `runContainer` |
| One at a time | `app/app/src/main/AndroidManifest.xml`: `XServerDisplayActivity` is `singleTask` |
| Stop | `XServerDisplayActivity.java` `exit()` → `environment.stopEnvironmentComponents()` |

**Where they disagree:** Winlator runs one environment at a time, while SageMaker and Termux keep
several alive. JupyterLab stops gracefully through the server's own API, while Winlator and our
launcher kill the process. The backlog settles the first disagreement. T1 settled the second in
JupyterLab's favour (Open decisions).

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How many run | winlator → one; SageMaker, termux → several | ~130 MB per server against switching cost | **Several**, as the backlog states. A cap only if T1's RSS calls for one |
| Port shape | today: one `ServerLauncher`, one `BaseSpaceServer` for `BASE_SPACE` | Keyed launcher vs a registry of per-Space servers | **A `SpaceServers` port** (`running: StateFlow<Set<String>>`, `run`, `stop`, `stopAll`) over one generalised `SpaceServer` per id, each with its own `JupyterServerLauncher`. That keeps the launcher's `start`-begins-with-`stop` rule per Space. Base's clients take base's entry, so a lazy start from a cell shows on the home |
| Stop | jupyterlab → `api/shutdown`; winlator, ours → kill the process | Graceful kernel cleanup vs no dependency on a live HTTP endpoint | **`api/shutdown`**, JupyterLab's, because T1 measured that under proot no signal to the tracer stops the tree |
| Self-stop | the rootfs's idle flags | Polling vs watching the process | **Watch for exit**, so the row flips without polling |
| App left | ours → `IdleServerStopper` stops the one server | — | `stopAll()`, on the same grace and counters |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Server process | `app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt` | — | complete, per process |
| Which Space runs | `app/src/main/java/com/jupydroid/app/data/space/BaseSpaceServer.kt`, `BASE_SPACE` only | SageMaker | present and partial |
| Several at once | `app/src/main/java/com/jupydroid/app/JupydroidApplication.kt` builds one launcher | SageMaker | absent |
| Run / Stop / state on a row | `app/src/main/java/com/jupydroid/app/ui/spaces/SpacesHomeScreen.kt` | `winlator@fb66541 ContainersFragment.java` | absent |
| Self-stop reflected | `JupyterServerLauncher.isRunning()` is asked, never observed | — | absent |
| Delete stops first | `app/src/main/java/com/jupydroid/app/ui/spaces/SpacesViewModel.kt` deletes outright | — | absent |
| App-leave backstop | `app/src/main/java/com/jupydroid/app/data/space/IdleServerStopper.kt` | termux `ACTION_STOP_SERVICE` | present and partial: one server |

## Deviations

- No foreground service. The app-leave backstop stops servers rather than keeping them alive in the
  background the way Termux does. That is unchanged from today.

## Open decisions

**Settled by T1** on SM-X216B (`TwoSpacesAtOnceDeviceTest`, three runs, each a fresh tree per case):

- **A running Space costs ~124 MB**: proot ~0.36 MB, `jupyter_server` ~66 MB, and a booted kernel
  ~57 MB. Two run side by side on distinct ports, and each refuses the other's token. No cap for now.
- **Only `POST api/shutdown` stops a Space.** It took the whole tree in 772–991 ms, and base's server
  and kernel kept answering. **SIGTERM to proot, which is what `Process.destroy()` sends, stops
  nothing**: all three processes were alive 10 s later. **SIGKILL to proot orphans its tracees**: the
  tracer goes, and the server and kernel run on outside the app's tree. So Stop becomes
  `api/shutdown`, and neither signal can be the fallback.
- **That is a defect in what ships today**, not only a JD-004 question. `JupyterServerLauncher.stop()`
  is `destroy()`, so the app-leave backstop never stops base's server. And `start()` begins with
  `stop()`, so every restart leaves the old server running beside the new one until the app dies
  (`CLOSED-001` still holds for app death). **Fixed by T2**: `stop()` posts `api/shutdown` and waits
  for the exit. A server that outlives the wait is logged and kept, never signalled.
- **A kernel's websocket upgrade can take longer than OkHttp's default 10 s read timeout.**
  jupyter_server answers the upgrade only after `kernel_info`, and waits for it up to
  `kernel_info_timeout` (60 s, jupyter_server 2.20.0 `services/kernels/connection/base.py`). A cold
  kernel beside a second Space missed 10 s in two runs, once on base and once on the probe. `OkHttpKernelSocketOpener` defaults to
  a plain `OkHttpClient`, so a notebook opened while another Space runs can fail the same way. The test
  gives the opener a 60 s client, and the app's own opener is T3.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | `SpaceServers` (new) |
| `data/space` | `JupyterSpaceServers` (new), `SpaceServer` (from `BaseSpaceServer`), `PerRunningServer`, `IdleServerStopper` |
| `data/fake` | `FakeSpaceServers` |
| `ui/spaces` | `SpacesViewModel`, `SpacesHomeScreen` |
| `di/AppContainer` | `spaceServers`, and the backstop's `stopAll` |
| unit tests | `JupyterServerLauncher` stop, `JupyterSpaceServersTest`, `SpacesViewModelTest` |
| instrumented tests | `TwoSpacesAtOnceDeviceTest`, `SpacesHomeScreenTest` |
| `.maestro/` flow + `scripts/` driver | `jd-004-run-and-stop-spaces` |

## Tasks

- [x] T1 — Measure two Spaces' servers at once on SM-X216B, each with a kernel: distinct ports, each
  answering with its own token, the RSS of each, and which stop leaves no process under its root while
  the other still answers. This is a measurement only, so no flow
- [x] T2 — `JupyterServerLauncher.stop()` is `POST api/shutdown` and waits for the process to exit,
  replacing `destroy()`, which proot ignores. Red first: a JVM test against `FakeJupyterServerScript`
  whose server ignores SIGTERM, and `TwoSpacesAtOnceDeviceTest` driving the launcher's own `stop()`
- [ ] T3 — The kernel websocket's upgrade waits as long as jupyter_server's `kernel_info_timeout`, not
  OkHttp's 10 s default
- [ ] T4 — The `SpaceServers` port, `JupyterSpaceServers` and `FakeSpaceServers`: `BaseSpaceServer`
  generalised to one `SpaceServer` per Space, base's clients bound to base's entry, and `running`
  observed from the process's exit
- [ ] T5 — The home: Run/Stop and Running/Stopped on every row, and Delete stops a running Space first
- [ ] T6 — The app-leave backstop stops every running Space. The demo runs on the tablet

## Out of scope

- Entering a non-base Space: JD-005.
- An idle time per Space, and other per-Space settings: JD-009.
- Clearing `run/` and `tmp/` residue between starts: `GAP-150`.
- A cap on how many run at once, unless T1 measures a need for one.
