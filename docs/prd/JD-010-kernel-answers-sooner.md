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
3. Each run's `kernel.socket.opened` → `kernel.socket.connected` span, read from those two lines
   in its logcat, has a median below T1's 5.25 s baseline. (`cold_path_report.py`'s row for it
   starts at `lsp.start`, which lands between the two.)

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

T1 measured the window as the kernel module's import: about 2.7 s of import work, plus about
2.5 s of reading it from storage on a warm start (`evidence/jd-010-kernel-window/README.md`).
pylsp, the DEBUG log level and the nudge are not where it goes.

T2's decision is **how to take the import out of the window**. Nothing on the device works during
the user's ~13 s walk from Run to the first cell. The options:
- pre-read the kernel's files into the page cache while the server starts. This removes the storage
  half and leaves about 2.7 s.
- pre-start a kernel the first session binds to. This removes both halves, but it is a kernel
  nobody asked for, holding memory. No reference does it, and jupyter_server has no pool.
- import less: IPython's terminal frontend (0.84 s) and `debugpy.server` (0.37 s) are on the path.
  That means patching packages in the image.

Two of these are out before anything is measured:
- **Import less is out.** ipykernel's `ipkernel.py` imports `.debugger` itself, and IPython's
  `__init__.py` imports `IPython.terminal.embed`. Taking either off the path means patching
  third-party packages in the image. The ceiling is about 1.2 s, and the storage half stays.
- **`unchecked-hash` bytecode is out.** It would skip reading and hashing each `.py`, but
  ADR-0013 forbids it.

That leaves pre-read and pre-start. Pre-start needs no server patch, because `POST api/sessions`
takes `{"kernel": {"id": …}}` and binds a kernel that is already running (jupyter_server
`services/sessions/handlers.py`).

**The criteria, fixed before T2 measures anything:**
1. The window's median with the repair, from an evicted page cache as the in-app warm start finds
   it, against T1's 5.25 s.
2. What the repair costs: a pre-started kernel's resident memory while idle, and how much a pre-read
   slows the server's own start.
3. What the repair obliges. A pre-started kernel needs a lifecycle: a kernel other than the default
   picked, a restart, a session that never opens a notebook, and the idle stop. At equal gain, the
   option with fewer obligations wins.

If neither beats the baseline net of its cost, the ADR says so and T3 is rewritten, not forced.

## Surface

| Layer | Files |
|---|---|
| `data/space` | Decided by T2 — `SpaceServer.kt` / `JupyterServerLauncher.kt` (pre-read or pre-start), or `tools/rootfs/` (import less) |
| instrumented tests | `KernelWindowProbeDeviceTest` |
| `.maestro/` flow + `scripts/` driver | `p8a-cold-path.yaml`, `p8a-cold-path-driver.sh`, `cold_path_report.py` (unchanged) |

## Tasks

- [x] T1 — Split the window on the tablet. The probe reports, median of 3:
  - the interpreter starting under proot
  - `import jupydroid_kernel`, with its top imports
  - the window with nothing else running
  - the window with pylsp starting beside it
  - the window at a quieter kernel log level

  Warm ×3 in-app runs are the baseline it is checked against. The evidence names the dominant part.
- [ ] T2 — Measure both repairs on the tablet with the probe, each from an evicted page cache, median of 3:
  - the window after a pre-read (`import jupydroid_kernel` run and thrown away)
  - the window on a kernel pre-started a walk's length before its session
  - an idle pre-started kernel's resident memory
  - the server's start, alone and with a pre-read beside it

  An ADR then applies the criteria in Open decisions and picks one.
- [ ] T3 — Make the repair T2's ADR chose, red test first, then the demo's warm ×3.

## Out of scope

`jupyter_server`'s own boot under proot is JD-011. The cold extraction was JD-006.
