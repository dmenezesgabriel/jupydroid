# JD-102 — a Space stopped by hand stays stopped

Milestone: `M11` · State: open · Depends on: JD-100 | — · Closes: —

## Outcome

A Space the user stops stays stopped: nothing the app does on its own afterwards boots its
`jupyter_server` back up, so the Spaces row keeps offering Run until the user asks for it again.

## Demo

On the arm64 tablet (`SM-X216B`), from a cold install:

1. Launch, enter Base, open a notebook, start a cell that is still running.
2. Home → Spaces, tap **Stop Base (base)** while the run is in flight.
3. Wait ~3 s: the row must still read **Run Base (base)** — today it flips back to
   **Stop Base (base)** ~1 s later — and logcat must show no second
   `starting jupyter_server for Space base`.
4. Tap **Run Base (base)**: the row flips to running and the Space works as before.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` under `evidence/<flow-name>/`. Unit tests alone never close it.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | no server lifecycle: the client attaches to whatever server it was given |
| `vscode` | ABSENT | — | no `jupyter_server` lifecycle; kernels live in the extension's own host |
| `vscode-jupyter` | ABSENT | — | same — there is no stoppable server to keep down |
| `intellij-community` | ABSENT | — | no equivalent |
| `termux-app` | ABSENT | — | packages and shells, no server lifecycle |
| `winlator` | ABSENT | — | containers end with the activity; nothing polls a stopped one back up |
| the shipped rootfs | PARTIAL | `--ServerApp.shutdown_no_activity_timeout` (`JupyterServerLauncher.kt`) | `jupyter_server` may stop *itself* when idle — a self-stop, not a user's, and restart policy stays ours |

**Count:** 0 of 7 implement "a stopped server stays stopped until asked". The lifecycle is ours
(ADR-0024, ADR-0075); the references settle only that none of them restart a stopped server behind
the user's back — they never had the mechanism to.

## Implementation inventory

No reference ships this mechanism; the design is ours, recorded in ADR-0024 (the idle stop and its
silent repair), ADR-0080 (the spare prestart) and `docs/architecture/runtime-views.md` §6.3–6.4.
The files an implementer must open:

### ours

| Concern | Files |
|---|---|
| The lazy start and its lock | `app/src/main/java/com/jupydroid/app/data/space/SpaceServer.kt` |
| The client cache whose passive read must not start | `app/src/main/java/com/jupydroid/app/data/space/PerRunningServer.kt` |
| The passive callers it serves | `BaseSpaceKernelGateway.endingIfStillLive`, `BaseSpaceKernelSessions`, `BaseSpaceKernelSpecs`, `BaseSpaceTerminalGateway` (all in `data/space/`) |
| The stop's own callers | `JupyterSpaceServers.stop/stopAll`, `SpacesViewModel`, `IdleServerStopper` |
| The measured failure | the JD-100 demo's part8, `.maestro/run-survives-leaving-part8-space-stop-ends-run.yaml` — its run's evidence stays local per ADR-0132 |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How a passive read meets a stopped server | check-then-act outside the lock (today) · a peek under `startLock` · an explicit-stop latch refusing every start until `run()` | the latch rewrites the lazy-start contract for every client — a cell run on a stopped Space would fail instead of starting it, a product change nobody asked for; the peek is ~15 lines and makes the code keep the contract `liveOrNull` already documents | the peek under `startLock` |
| Which callers may start the server | `get()`/`connection()` are asks; `liveOrNull()` is inspection | unchanged — only the inspection path was ever meant to be passive | unchanged |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Passive read of a stopped Space's server | `PerRunningServer.liveOrNull` (`PerRunningServer.kt`) checks `isRunning()` outside `startLock`, then `get()` → `connection()` starts one — its own KDoc says it must not | the KDoc contract itself | present but broken: during a stop's shutdown window `isRunning()` still answers true, the queued `connection()` starts a fresh server after `stop()` releases the lock |

## Deviations

None. Lazy start stays — ADR-0024's silent repair of the *idle* stop depends on the next use
starting the server again. The fix narrows only the path that was never meant to start anything.

## Open decisions

None — the contract already exists in KDoc and the architecture docs; this item makes the code keep
it. A `liveOrNull` asked mid-stop waits on `startLock` (bounded by the stop's own timeout) and then
answers for the settled state, which is the honest answer.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | `data/space/SpaceServer.kt`, `data/space/PerRunningServer.kt` |
| `data/fake` | — (the port surface does not change) |
| `ui/<screen>` | — |
| `di/AppContainer` | — |
| unit tests | `app/src/test/java/com/jupydroid/app/data/space/SpaceServerTest.kt` |
| instrumented tests | — |
| `.maestro/` flow + `scripts/` driver | a stop-stays-stopped flow on the tablet; its screenshots and `maestro.log` stay local under `evidence/<flow-name>/` per ADR-0132 |

## Tasks

- [x] T1 — `SpaceServer.liveOrNull()` peeks the cached start under `startLock` and never starts;
      `PerRunningServer.liveOrNull()` delegates through the same client-cache path as `get()`.
      Red first: a `liveOrNull` during a stop in flight returns null and the launcher's
      `startCount` stays 1.
- [ ] T2 — the demo on `SM-X216B`: stop mid-run, the row keeps Run and logcat shows no second
      `starting jupyter_server for Space base`, Run brings it back. Ships the item — PRD deleted,
      backlog edited.

## Out of scope

- An explicit-stop latch that refuses *active* asks — cell runs, terminals, exports, extension
  addresses — until Run. That is a product change to the lazy-start contract, not this defect.
- `RunningSpaceServerEndpoint.address()` and every other active ask keep lazy-starting by design.
