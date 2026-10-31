# ADR-0128 — A Space's server starts with an empty `run/`

Status: accepted
Date: 2026-10-31

## Context

A server stopped by a kill removes nothing from `run/` (`JUPYTER_RUNTIME_DIR`). The tablet held 17
servers' worth: `jpserver-<pid>-open.html`, `jpserver-mcp-<pid>.json` and the kernels' connection
files. Every file is `0600` and every token in them dead, so the cost was a directory that grows
without bound. The launcher deleted only `jpserver-<pid>.json`, so a dead port was never read, and
upstream prunes that one file alone, lazily (`usr/lib/python3.12/site-packages/jupyter_server/serverapp.py`,
`list_running_servers`).

## Decision drivers

- Nothing a former server left is read by a later one.
- A killed server's token-bearing `-open.html` does not outlive the next start.
- No file in `run/` that a start needs is deleted.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| The `-open.html` | leave it and clear it at start; `ServerApp.no_browser_open_file` (`serverapp.py`, line 1768) | clear it at start. `no_browser_open_file` is declared without `.tag(config=True)`, so the server logs `Config option `no_browser_open_file` not recognized by `ServerApp`` and still writes the page; it cannot be set from argv or a config file |
| What a start clears | the files of the server (`jpserver-<digits>.json`), as before; every file in `run/` | every file: the logs are truncated by the redirect that opens them and are written after the clear, and the SIGSYS log is in `tmp/`, so nothing in `run/` outlives a start |
| Home | `JupyterRuntimeFile`; the launcher; `SpaceDirs` | `SpaceDirs.clearRuntime()`: it owns `runtime`, and `ensure()` is its provisioning twin |

## Decision

**`JupyterServerLauncher.start` calls `SpaceDirs.clearRuntime()` after `stop()`, which empties `run/` and keeps the directory.** The running server's own `jpserver-<pid>-open.html` stays until it stops.

`JupyterRuntimeFile.clear` had no other caller and is removed. A Space whose `run/` was never
provisioned is left alone: clearing creates nothing.

## Consequences

- `run/` holds one server's files: its `jpserver-<pid>.json`, `jpserver-mcp-<pid>.json`, kernels'
  connection files, its `ipc` sockets and `jupyter_server.log` (and `startup.log` where a Space has a startup script).
- A start deletes what the previous run left in `run/`, so a postmortem of a killed server reads
  its logs before the next start, not after.
