# JD-010 — the kernel answers sooner

Milestone: `M2` · State: in-progress · Depends on: — · Closes: —

## Outcome

On the tablet, a notebook's first cell gets its answer sooner. Today the wait between the kernel
socket opening and the kernel's first answer takes about 5.1 s cold and warm. That is 55% of the warm
runtime ahead of the first answer (`evidence/jd-006-cold-path/README.md`). This item splits the wait
into parts and repairs the largest one.

## Demo

On the arm64 tablet (`SM-X216B`). A cell runs, so the emulator's `FakeKernelGateway` would time an
echo.

1. `P8A_EVIDENCE_ROOT=evidence/jd-010-kernel-window scripts/p8a-cold-path-driver.sh warm rN`, for
   N = 1, 2, 3. The flow runs base from the home, opens `Cold Path.ipynb`, runs `print(6 * 7)` and
   writes a file from a new terminal.
2. Each `01-first-cell-answered.png` shows `42` under the cell from the real kernel.
3. `python3 scripts/cold_path_report.py evidence/jd-010-kernel-window/warm-*/logcat.txt` prints the
   `kernel.socket.opened → kernel.socket.connected` row with a median below JD-006's 5.11 s warm.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | The client asks the server for a session. The kernel's spawn and readiness are the server's, and the client times neither |
| `vscode` | ABSENT | — | Has no kernel of its own |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca src/telemetry.ts` (`NotebookFirstStartBreakDown`), `src/kernels/telemetry/notebookTelemetry.ts`, `src/kernels/raw/session/rawKernelConnection.node.ts` | Times first start in parts (`kernelReady`, `kernelInfo`, `kernelIdle`, `executeCell`). Its raw kernel retries `kernel_info` on shell and control every 500 ms until iopub and the reply both arrive, the same cadence as jupyter_server's nudge. `standalone/variables/preWarmVariables.node.ts` pre-warms an interpreter's env vars, not a kernel |
| `intellij-community` | ABSENT | — | Not examined for this item; it ships no bundled kernel runtime |
| `termux-app` | ABSENT | — | Runs no kernel |
| `winlator` | ABSENT | — | Runs no kernel |
| the shipped rootfs | — | `usr/lib/python3.12/site-packages/jupyter_server/services/kernels/connection/channels.py` (`prepare`, `nudge`), `ipykernel/debugger.py` | It is what is being waited on. The server's socket waits for `kernel_manager.ready`, then nudges with `kernel_info` every 0.5 s until a reply. ipykernel 7.3.0's `debugger.py` imports `debugpy.server` at module level |

**Count:** 1 of 7 touch this. Only vscode-jupyter measures the window, and none shortens it. Its
500 ms retry matches the server's, so that granularity is shared practice rather than our defect. What
to repair comes from our own split.

## Implementation inventory

### vscode-jupyter@f01d80ca

| Concern | Files |
|---|---|
| Per-part timing | `src/kernels/telemetry/notebookTelemetry.ts`, `src/telemetry.ts` |
| Readiness loop | `src/kernels/raw/session/rawKernelConnection.node.ts` (`postStartRawSession`) |
| Kernel process | `src/kernels/raw/launcher/kernelProcess.node.ts` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How to split the window | In-app logcat only · a device probe that runs each part in isolation | Logcat sees the window as one span. A probe can take the interpreter, the imports, contention and logging apart one at a time | A probe (`KernelWindowProbeDeviceTest`), with the in-app warm runs as the baseline it is checked against |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Gap |
|---|---|---|
| Stage marks | `data/space/KernelLog.kt` | `SOCKET_OPENED → SOCKET_CONNECTED` is one span. Nothing says which part of it is the interpreter, the imports, the app's init or the nudge |
| What else runs in the window | `data/space/StdioLanguageServer.kt` | pylsp starts under proot about 0.14 s after the socket opens in every JD-006 run, so two interpreters import at the same time |
| Kernel flags | `data/space/SpaceKernelSpec.kt` | `--log-level=DEBUG` on every kernel, priced at nothing so far |
| Kernel module | `tools/rootfs/jupydroid_kernel.py` | Imports `ipykernel.debugger`, which imports `debugpy.server`, before the app starts |

Present and partial: every stage is marked, and the parts inside the dominant one are not.

## Deviations

None yet.

## Open decisions

Candidate repairs. Each is decided on T1's numbers, never before them:
- start pylsp after the kernel connects rather than with it
- a quieter kernel log level
- debugpy imported when a debug session starts rather than at kernel start
- the nudge's 0.5 s granularity, which vscode-jupyter shares

## Surface

| Layer | Files |
|---|---|
| `data/space` | Decided by T1 — `StdioLanguageServer.kt`, `SpaceKernelSpec.kt` or `tools/rootfs/jupydroid_kernel.py` |
| instrumented tests | `KernelWindowProbeDeviceTest` |
| `.maestro/` flow + `scripts/` driver | `p8a-cold-path.yaml`, `p8a-cold-path-driver.sh`, `cold_path_report.py` (unchanged) |

## Tasks

- [ ] T1 — Split the window on the tablet. The probe reports, median of 3:
  - the interpreter starting under proot
  - `import jupydroid_kernel`, with its top imports
  - the window with nothing else running
  - the window with pylsp starting beside it
  - the window at a quieter kernel log level

  Warm ×3 in-app runs are the baseline it is checked against. The evidence names the dominant part.
- [ ] T2 — Repair the dominant part, red test first, then the demo's warm ×3.

## Out of scope

`jupyter_server`'s own boot under proot is JD-011. The cold extraction was JD-006.
