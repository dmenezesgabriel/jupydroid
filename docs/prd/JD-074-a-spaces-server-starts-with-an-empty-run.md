# JD-074 — A Space's server starts with an empty `run/`

Milestone: `M8` · State: open · Depends on: — · Closes: GAP-192

## Outcome

A Space's `run/` holds only the files of the server that is running: a killed server's connection
files, kernel files and `-open.html` are gone before the next one starts, and `-open.html` is never
written at all.

## Demo

On the arm64 tablet (`SM-X216B`), from a cold start:

1. Install, launch; seed stale files into `files/spaces/base/run/` with `adb shell run-as`
   (`jpserver-1-open.html`, `jpserver-mcp-1.json`, `kernel-stale.json`).
2. Open a notebook and run a cell: it runs on the real kernel.
3. `run-as ls` on `run/`: the stale files are gone, there is no `jpserver-<pid>-open.html`, and
   the live server's `jpserver-<pid>.json`, `jpserver-mcp-<pid>.json`, kernel file and two logs remain.

Not `shipped` until run on that device, with screenshots and `maestro.log` under `evidence/jd-074-*/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | The browser client; the server's files are not its concern |
| `vscode` | ABSENT | — | Starts no `jupyter_server` of its own |
| `vscode-jupyter` | PARTIAL | spawns `jupyter notebook`, reads its URL from stdout | Never reads the runtime dir, so a stale file cannot mislead it; nothing cleans it |
| `intellij-community` | ABSENT | — | Jupyter support is not a server owner |
| `termux-app` | ABSENT | — | No server |
| `winlator` | ABSENT | — | No server |
| the shipped rootfs | FULL | `usr/lib/python3.12/site-packages/jupyter_server/serverapp.py` | `no_browser_open_file` (line 1768) skips the `-open.html`; the server removes its own files only on a clean stop (`remove_server_info_file`, `remove_browser_open_files`) |

**Count:** 1 of 7 offers it, and it is upstream itself. The other rows are read from their role, not
from their code; verify by grep if a row is cited later. The rest of the answer is ours: a killed
process never reaches upstream's cleanup.

## Trade-offs and what we adopt

| Axis | Options seen | Our choice, and why |
|---|---|---|
| `-open.html` | upstream `no_browser_open_file` | Pass it: we never open a browser from it, and it carries a token |
| Stale files | upstream prunes `jpserver-<pid>.json` lazily; ours deletes that one file at start | Clear the contents of `run/` at start: nothing there outlives a start (logs are truncated, the SIGSYS log is in `tmp/`) and `start()` has stopped the previous server |
| Where | launcher inline; `JupyterRuntimeFile`; `SpaceDirs` | `SpaceDirs.clearRuntime()`: it already owns `runtime` |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| `-open.html` | `jupyterServerArgs` passes `--no-browser` only | absent |
| Stale runtime files | `JupyterRuntimeFile.clear` removes `jpserver-<pid>.json` only | partial |

## Deviations

None.

## Open decisions

Whether `JupyterRuntimeFile.clear` keeps a caller once `clearRuntime` replaces its call in `start()`:
grep decides; a function with no caller is removed with its test.

## Surface

| Layer | Files |
|---|---|
| `data/space` | `JupyterServerLauncher.kt` (`jupyterServerArgs`, `start`), `SpaceDirs.kt` |
| unit tests | a new args test, `SpaceDirsTest`, `JupyterServerLauncherSpaceTest` |
| `.maestro/` flow + `scripts/` driver | a run-dir flow beside the other server flows |

## Tasks

- [x] T1 — `--ServerApp.no_browser_open_file=True` in the server's arguments
- [ ] T2 — `SpaceDirs.clearRuntime()`, called by `start()` in place of the single-file clear
- [ ] T3 — the device demo above

## Out of scope

Pruning `run/` while a server is running: nothing grows there during one run.
