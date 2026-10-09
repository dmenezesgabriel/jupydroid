# JD-011 — `jupyter_server` boots sooner under proot

Milestone: `M2` · State: in-progress · Depends on: — · Closes: —

## Outcome

On the tablet, a Space started from the home's **Run** is ready sooner. Today the server's boot,
`server.starting → listening`, takes about 3.4 s cold and 3.9 s warm. Before every process moved
under proot it took about 2.2 s warm (`evidence/jd-006-cold-path/README.md`,
`evidence/p8c-2-server-boot/README.md`). This item splits the boot into parts and repairs the
largest one.

## Demo

On the arm64 tablet (`SM-X216B`). The flow runs a cell, so on the emulator `FakeKernelGateway` would
answer it with an echo.

1. `P8A_EVIDENCE_ROOT=evidence/jd-011-server-boot scripts/p8a-cold-path-driver.sh warm rN`, for
   N = 1, 2, 3. The flow runs base from the home, opens `Cold Path.ipynb`, runs `print(6 * 7)` and
   writes a file from a new terminal.
2. Each `01-first-cell-answered.png` shows `42` under the cell, from the real kernel.
3. Each run's `starting jupyter_server for Space base` → `jupyter_server listening on …` span has a
   median below the warm baseline T1 takes. `cold_path_report.py` reads the span from logcat.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | `jupyterlab@7f0e690 jupyterlab/serverextension.py` | Is a server extension the server loads. It neither times nor shortens the server's boot |
| `vscode` | ABSENT | — | Starts no Jupyter server |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca src/kernels/jupyter/launcher/serverPreload.node.ts`, `jupyterServerStarter.node.ts`, `jupyterConnectionWaiter.node.ts`, `src/telemetry.ts` (`PerceivedJupyterStartupNotebook`) | Starts the server ahead of need: when the extension activates, if a notebook was used in the last 7 days, or as soon as a notebook opens. It times start-to-first-cell, and treats the server as ready once the URL shows up on its stdout. It passes an empty `--config` file so no user config loads |
| `intellij-community` | ABSENT | — | Its only Jupyter code is `platform/platform-impl/src/com/intellij/help/impl/JupyterHelpUsageCollector.kt`. It starts no server |
| `termux-app` | ABSENT | — | Runs no server |
| `winlator` | ABSENT | — | Runs no server |
| the shipped rootfs | — | `usr/lib/python3.12/site-packages/jupyter_server/serverapp.py` (`initialize`, `start_app`), `usr/etc/jupyter/jupyter_server_config.d/jupyter_server_terminals.json` | It is what is being waited on. `initialize` finds and loads the extensions, then sets up the web app. `start_app` logs "is running at" and only then writes the runtime file the app polls for. `jupyter_server_terminals` is the one server extension enabled |

**Count:** 1 of 7 touch this. vscode-jupyter hides the boot by starting the server early. It does
not make the boot itself shorter. Our server already starts on the home's **Run**, ahead of the
first cell. What to repair comes from our own split.

## Implementation inventory

### vscode-jupyter@f01d80ca

| Concern | Files |
|---|---|
| When to pre-start | `src/kernels/jupyter/launcher/serverPreload.node.ts` |
| Command line | `src/kernels/jupyter/launcher/jupyterServerStarter.node.ts` (`generateArguments`, `getConfigArgument`) |
| Readiness | `src/kernels/jupyter/launcher/jupyterConnectionWaiter.node.ts` (stdout URL pattern) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How to split the boot | In-app logcat only · a device probe | Logcat sees the boot as one span. A probe can time the interpreter, the imports and the evicted cache one at a time, and read the server's own log between spawn and runtime file | A probe, `ServerBootProbeDeviceTest`, the way JD-010 split the kernel window. In-app warm runs are the baseline it is checked against |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Gap |
|---|---|---|
| Stage marks | `data/space/SpaceServer.kt`, `KernelLog.kt` | `server.starting → listening` is one span. Nothing says how much of it is interpreter, imports, extension loading, web-app setup or our poll |
| Readiness | `data/space/JupyterServerLauncher.kt` (`awaitConnection`, 100 ms poll on the runtime file) | Up to 0.1 s late by construction. Not measured |
| Server flags | `JupyterServerLauncher.kt` (`--ServerApp.log_level=DEBUG`) | The server's DEBUG log is priced at nothing so far |
| Process model | `data/space/ProotCommand.kt`, `SpaceRuntime.kt` | Every syscall that names a path is translated by proot. Its share of the +1.5 s since proot is unmeasured |

Present and partial: the stage is marked, and the parts inside it are not.

## Deviations

T1 does not time the server under `PROOT_NO_SECCOMP=1`, because the server cannot run that way: it
dies of SIGSEGV right after its extension links. It times the import that way instead.

## Open decisions

Which repair. T1 measured the boot as the import of `jupyter_server.serverapp`: about 2.0 s of
import work, plus about 1.3 s of reading it from storage on a warm start. The server's own work after
the import is under 0.3 s, and the poll under 0.12 s (`evidence/jd-011-server-boot/README.md`). T2
measures the three repairs that leaves, each from an evicted page cache:
- a pre-read of the import before **Run**
- the server started before **Run**, as vscode-jupyter's `serverPreload.node.ts` does
- importing less

T2 also weighs what a server started early holds while nobody uses it.

## Surface

| Layer | Files |
|---|---|
| `data/space` | depends on T1's dominant part |
| instrumented tests | `ServerBootProbeDeviceTest`, plus the probe helpers it shares with `KernelWindowProbeDeviceTest` |
| `.maestro/` flow + `scripts/` driver | `p8a-cold-path.yaml`, `p8a-cold-path-driver.sh`, `cold_path_report.py` (unchanged) |

## Tasks

- [x] T1 — Split the boot on the tablet. The probe reports a median of 3 for each of:
  - the interpreter starting under proot
  - `import jupyter_server.serverapp`, with its slowest imports, warm and evicted
  - the server's start through the shipped launcher, warm and evicted
  - inside the start, from the server's own log: spawn → first line, first line → "is running at",
    and "is running at" → the launcher seeing the runtime file, plus the largest gaps between lines
  - the evicted start with `PROOT_NO_SECCOMP=1`

  Three in-app warm runs are the baseline the probe is checked against. The evidence names the
  dominant part.
- [ ] T2 — Measure the repair or repairs that T1's dominant part points to, on the tablet, each from
  an evicted page cache, median of 3. An ADR then picks one.
- [ ] T3 — Ship it, red test first, then the demo's warm ×3.

## Out of scope

The kernel window was JD-010, and the cold extraction was JD-006.
