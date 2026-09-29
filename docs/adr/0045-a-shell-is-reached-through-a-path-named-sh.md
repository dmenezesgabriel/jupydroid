# ADR-0045 — A shell is reached through a path named `sh`, and all 304 applets with it

Status: accepted
Date: 2026-08-29

## Context

busybox dispatches on the **basename of the path it is reached by**, and its `argv[1]` fallback engages
only for a basename prefixed `busybox`.

`libbusybox.so` is not so prefixed, and `lib*.so` is **not negotiable** — it is what AGP packages into
`lib/<abi>/`, which is the only place execution is permitted
([ADR-0002](0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md)).

The applet name cannot ride on `argv[0]` either: `ptyprocess.PtyProcess.spawn`, which is how terminado
reaches a shell, does `command = which(argv[0])` and then **overwrites `argv[0]` with it**.

Measured against the shipped BusyBox 1.37.0 rather than argued from its source, with a control.

## Decision drivers

- The native library's name is fixed by the packaging, not by us.
- terminado's spawn path rewrites `argv[0]`, so anything carried there is lost.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Pass the applet as `argv[1]` | busybox supports it | Only when the basename is prefixed `busybox`, which `libbusybox.so` cannot be |
| Pass the applet as `argv[0]` | No filesystem changes | `PtyProcess.spawn` overwrites `argv[0]` with the resolved path |
| Symlink one path per applet, each named after the applet | Dispatch works by the mechanism busybox actually uses | 304 links to create and keep in step |

## Decision

Each applet gets a **path named after it**, symlinked to `bin/busybox`, which is itself a link into
`nativeLibraryDir`. terminado is pointed at the path named `sh`, and all 304 applets are reachable the
same way.

`BusyBoxApplets` owns the link set, and `scripts/check_busybox_seccomp.py` guards the build that produces
the binary behind them ([ADR-0006](0006-build-busybox-here-with-suid-compiled-out.md)).

## Consequences

The 304 links are created on every launch alongside the four `nativeLibraryDir` repairs, because the
directory they point through moves on every app update.

The dispatch mechanism is the reason `PATH` order matters: a name in `bin/` is shadowed by the image's own
copy, which is why the console-script trampoline is reached from `usr/local/bin` instead.

The thing a future reader will be tempted to "fix" is the link count, by passing the applet name as an
argument. Both argument routes are closed — one by busybox's own basename check, one by terminado's spawn.
