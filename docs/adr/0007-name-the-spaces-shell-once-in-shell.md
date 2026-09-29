# ADR-0007 — Name the Space's shell once, in `SHELL`

Status: accepted
Date: 2026-08-29

## Context

Three separate things in the shipped stack look for a shell, and each has its own fallback. All
three were read in the shipped source rather than assumed:

- `jupyter_server_terminals` consults `SHELL` before falling back to `which("sh")`.
- pexpect resolves it for a cell's *printing* escape, `!ls` (`_process_posix.py`).
- `IPython/utils/_process_common.py` passes it as `Popen(executable=…)` for the *capture* escape,
  `files = !ls`.

**Unset, that last one stays `None` and CPython falls back to the absolute `/bin/sh`, which Android
does not have.** So the command ran nowhere and the cell got an empty string back, **with no error**.
That silent-wrong-answer is why this is a decision and not a tidy-up.

## Decision drivers

- A shell path must be stated once, not discovered three times with three different fallbacks.
- A wrong answer with no error is worse than a failure.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Pin each consumer separately | Explicit at each site | Three places to keep in step, and the IPython one has no configuration hook at all |
| Set `SHELL` in the Space environment | One statement; every child inherits it | Relies on each consumer honouring it — which is why all three were read rather than assumed |

## Decision

`SpaceRuntime.environment()` sets `SHELL` to `spaceShellIn(baseDir)`. Every process in the Space is a
child of that environment, so all three consumers resolve the same shell.

`jupyter_server_terminals` additionally has `shell_command` pinned outright, so `SHELL` is its belt
rather than its braces.

## Consequences

A cell's `!ls` and `files = !ls` both reach a real shell, and the capture form no longer returns an
empty string silently.

The thing a future reader will be tempted to "fix" is the apparent redundancy between `SHELL` and the
pinned `shell_command`. They cover different consumers: removing `SHELL` breaks the two IPython
escapes, which have no `shell_command`.
