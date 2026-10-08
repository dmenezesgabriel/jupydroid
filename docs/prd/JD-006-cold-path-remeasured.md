# JD-006 — the first answer comes sooner, from wherever today's path spends most

Milestone: `M2` · State: in-progress · Depends on: — · Closes: —

## Outcome

The stage that dominates base's cold path **as it runs today** — Spaces home → Run → Open → first cell
→ first terminal, every process under proot — is measured on the tablet and repaired, so the first
answer arrives sooner than the re-measured baseline.

## Demo

On the arm64 tablet (`SM-X216B`) — a cell runs, so the emulator's `FakeKernelGateway` would time an
echo:

1. `scripts/p8a-cold-path-driver.sh cold rN` three times and `warm rN` three times, with
   `P8A_EVIDENCE_ROOT=evidence/jd-006-cold-path`: the flow runs base from the home, opens
   `Cold Path.ipynb`, runs `print(6 * 7)` and writes a file from a new terminal.
2. Each run's `01-first-cell-answered.png` shows `42` under the cell; each `02-terminal-open.png`
   shows the typed command at a real prompt, and the driver reads `cp-term.txt` back from the
   workspace.
3. `python3 scripts/cold_path_report.py evidence/jd-006-cold-path/cold-*/logcat.txt` (and `warm-*`)
   prints a row for every stage, `space.installing` and `server.starting` included.
4. After the repair, the same runs show the named stage shorter than in step 3's medians.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | The server is started by the user before the client exists; nothing in the client times or shortens it |
| `vscode` | ABSENT | — | Ships no bundled runtime to unpack or boot |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca src/kernels/telemetry/notebookTelemetry.ts`, `src/telemetry.ts` (`NotebookFirstStartBreakDown`) | Times run-cell → first execution count and splits it into connection, kernel-ready, kernel-info, idle and execute — the same per-stage shape as our `JupydroidSpace` lines |
| `intellij-community` | ABSENT | — | Not examined for this item; it ships no bundled runtime to unpack |
| `termux-app` | PARTIAL | `termux-app@3df69d1d app/src/main/java/com/termux/app/TermuxInstaller.java` | First run extracts the bootstrap zip, loaded from a shared library, into a staging prefix on one background thread behind a modal progress dialog, then renames it into place; an existing prefix means installed |
| `winlator` | PARTIAL | `winlator@fb66541b app/app/src/main/java/com/winlator/xenvironment/RootFSInstaller.java`, `core/TarCompressorUtils.java` | `rootfs.tzst` is extracted on a single-thread executor with byte-weighted progress, then a version file is written; `installIfNeeded` re-extracts when that version is below `LATEST_VERSION` |
| the shipped rootfs | — | — | It is what is being booted, not a way of booting it |

**Count:** 3 of 7 touch this. Both Android references pay a one-off archive extraction on first run
and show a progress dialog over it, which is ours ([ADR-0012](../adr/0012-ship-the-rootfs-as-zstd.md)).
None times a warm server boot or a kernel handshake, because none boots a Jupyter server on the
device. What to repair next comes from our own measurement, not from a reference.

## Implementation inventory

For T2, the extraction. Ours: `app/src/main/java/com/jupydroid/app/data/space/BaseSpaceInstaller.kt` and
`TarZstdExtractor`. termux-app extracts from an in-memory zip into a staging prefix on one thread;
winlator's `TarCompressorUtils.extract` streams `ZstdCompressorInputStream` through a
`BufferedInputStream` of `StreamUtils.BUFFER_SIZE`, also on one thread.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What to measure | P8a's harness as-is · P8a's harness repaired to today's stage names and with the session POST's start marked | As-is silently drops `space.installing` and `server.starting`, and books the UI between the home's Run and the cell as "kernel spawn" | Repair the harness first, red test first; a number taken through a broken reader is not a baseline |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Gap |
|---|---|---|
| Stage marks | `data/space/SpaceServer.kt`, `KernelLog.kt`, `TerminalLog.kt` | The session POST's start is not marked: since the server starts on Run, `listening → session.opened` includes the user's walk to the cell |
| Reading them | `scripts/cold_path_report.py` | Knows `installing base Space` / `starting jupyter_server`; the server has logged `installing Space <id>` / `starting jupyter_server for Space <id>` since the Spaces home |
| Driving the path | `scripts/p8a-cold-path-driver.sh`, `.maestro/p8a-cold-path.yaml` via `open-base-space.yaml` | Present; whether it still reaches the cell on today's home is checked on the tablet |

## Deviations

None yet.

## Open decisions

Where the extraction lost ~6 s since ADR-0013 on an unchanged archive — T2 measures that before it repairs anything.

## Surface

| Layer | Files |
|---|---|
| `data/space` | `KernelLog.kt`, `RealKernelGateway.kt` |
| unit tests | `RealKernelGateway`'s test suite |
| `scripts/` | `cold_path_report.py` (+ `_test.py`) |
| `.maestro/` flow + `scripts/` driver | `p8a-cold-path.yaml`, `p8a-cold-path-driver.sh` |

## Tasks

- [x] T1 — Repair the harness (the report reads today's server stage names; the session POST's
  start is logged and reported) and re-measure base cold ×3 and warm ×3 on the tablet; the evidence
  names the dominant stage.
- [x] T2 — **The extraction**, `space.installing → server.starting`: ~19 s median cold, 68% of the
  ~27.7 s runtime ahead of the first answer, and slower than the ~13 s it measured after
  [ADR-0013](../adr/0013-ship-bytecode-invalidated-by-checked-hash.md) on the same 76.4 MB archive.
  First find where the ~6 s went (`TarZstdExtractor` and `BaseSpaceInstaller` since then, timed on
  the device), then repair it, red test first. Demo: the cold runs again, the row shorter.

Measured and **not** this item's — each is its own item when picked up: the kernel window,
`socket.opened → connected` (~5.1 s cold and warm, was ~3.0 s spawn + handshake), and
`jupyter_server` boot (~3.4 s cold, ~3.9 s warm, was ~2.3 s).

## Out of scope

Creating a Space — the image copy JD-008 added — and the cold path of a Space other than base.
Measuring those is a separate item if base's numbers point there.
