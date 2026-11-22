# ADR-0167 — A Space's server log rotates into the Space's own archive, under the error log's caps

Status: accepted
Date: 2026-11-21

## Context

A Space's `jupyter_server` writes its stdout to `run/jupyter_server.log`, and every server start
destroyed it twice: `SpaceDirs.clearRuntime()` empties `run/` before the process is built
(`app/src/main/java/com/jupydroid/app/data/space/SpaceDirs.kt`, ADR-0128) and `redirectOutput`
truncates whatever a clear missed (`app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt`).
ADR-0128's consequence was that a postmortem of a killed server reads its logs *before* the next
start, not after — so the restart that follows a crash overwrote the crash's own account of itself.

The log is not safe to move to the app-specific external directory: at `--ServerApp.log_level=DEBUG`
the server logs its whole config, `IdentityProvider.token` among it, and the token is minted per
launch (`scripts/space-server.sh` scrubs every pulled copy for exactly this). On Android 10 and
below another app holding the storage permission can read that directory, which is why ADR-0163
redacts an error-log entry when it is written rather than when it is shared. A server writes this
file itself; nothing stands between its bytes and the disk, so the archive must stay where the live
log already sits: under `files/`, private to the app.

The caps policy already exists for the app's own error log: `ErrorLogCaps`, 1 MiB and 7 UTC days,
oldest first, never the newest day for its size (`app/src/main/java/com/jupydroid/app/domain/model/ErrorLogCaps.kt`,
`app/src/main/java/com/jupydroid/app/data/disk/ErrorLogPruning.kt`, ADR-0163).

## Decision drivers

- A crash is not overwritten by the restart that follows it.
- What the log costs is bounded without anyone asking.
- `run/` keeps holding one server's files — nothing a dead process left is read by the next one.
- The token never reaches a location another app could read.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep truncating | Nothing to maintain | The restart after a crash destroys the crash's log |
| Append across runs in `run/` | One file, whole history | Unbounded within `run/`, and every driver that reads `run/jupyter_server.log` as "this run's log" starts matching stale lines |
| **Archive per run under `server-logs/`, pruned by the caps** | Each run's log stays intact and dated; `run/` invariant kept; caps already decided | One more directory per Space and a pruning pass per start |
| Feed the error log | One store, one set of caps | Server lines are not redacted error entries; the token would land unredacted in the external directory |
| Archive under the external files dir | `adb pull` works on a release build | The token is in the bytes and nothing redacts them on the way |

## Decision

**At every server start, a non-empty `run/jupyter_server.log` is renamed into the Space's own
`server-logs/` directory before `clearRuntime()` empties `run/`, and the archive is pruned under
the error log's caps.** The live file is still the current run's alone; `run/` still ends each
start holding one server's files.

- One file per run: `jupyter_server-yyyyMMdd-HHmmss-SSS.log`, UTC, named by the clock the launcher
  is given, so names sort chronologically and a same-millisecond collision takes a `-N` suffix
  rather than failing the start.
- `server-logs/` sits beside `run/` in the Space's directory — never provisioned by `ensure()`,
  created on the first archive, and deleted with the Space. The caps are per Space: 1 MiB and
  7 days each, the same `ErrorLogCaps` the error log defaults to.
- Age drops whole archives whose UTC day is past the cap; size drops the oldest until the total
  fits, and never the newest — it is the most recent postmortem, the thing the archive exists for.
- `run/startup.log` is out of scope: it is the startup script's output, not the server's, and
  stays truncated at every start.

## Consequences

- Amends one consequence of ADR-0128: a postmortem no longer has to read before the next start —
  the previous run's log waits in `server-logs/`. The decision itself stands: `run/` is still
  emptied at every start.
- Amends one consequence of ADR-0163: the rotation it deferred to the arm64 tablet is this.
- A release build still cannot pull the archive without `run-as` — the access path is unchanged;
  what changed is that the bytes survive the restart at all.
- The live `run/jupyter_server.log` is uncapped while a run is alive, as it always was; the caps
  bind the archive at rotation. A single runaway run can still exceed 1 MiB and is kept whole as
  the newest file.
- A wedged server that `stop()` gave up on keeps writing into the renamed archive rather than a
  deleted inode — its last words are kept instead of lost.
- Reproduce: `grep -rn "ADR-0167" docs/ app/src/`; `ServerLogRotationTest` (unit) and
  `ServerLogRotationDeviceTest` (tablet) are the measurements.
