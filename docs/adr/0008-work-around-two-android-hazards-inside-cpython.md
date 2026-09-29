# ADR-0008 — Work around two Android hazards inside CPython, and keep them named

Status: accepted
Date: 2026-08-14

## Context

Two failures inside the shipped Python stack are caused by Android's network restrictions rather
than by anything this app does. Neither is fixed by the SIGSYS handler
([ADR-0004](0004-handle-sigsys-and-return.md)), because neither is a refused syscall.

**`psutil.net_if_addrs()` segfaults.** `getifaddrs()` gets EACCES from Android's network
restrictions and psutil does not check the return — `psutil/__init__.py`,
`rawlist = _psplatform.net_if_addrs()`, `exit=139`. This is a SIGSEGV, so the SIGSYS handler cannot
reach it. psutil cannot be dropped, being an `ipykernel` dependency.

**IOPub's forked-subprocess pipe binds TCP and aborts.** `IOPubThread._setup_pipe_in` binds
`tcp://127.0.0.1` regardless of the configured transport; on Android libzmq turns the EACCES into an
`errno_assert` → SIGABRT that ipykernel's own `except zmq.ZMQError` never reaches.

## Decision drivers

- A hazard that cannot be removed must at least be bounded and written down.
- Prefer selecting a library's own supported configuration over patching it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Patch psutil and ipykernel in the image | Fixes both at the source | We would carry forks of two upstream packages through every upgrade |
| Drop psutil | Removes the hazard | It is an `ipykernel` dependency |
| Bound each hazard using the library's own options | No forks; upgrades stay cheap | One hazard remains reachable from user code, and one capability is lost |

## Decision

**psutil stays, and the hazard is bounded rather than removed.** ipykernel uses psutil only for
process management, so the segfault is reachable only from a user's own notebook calling
`net_if_addrs()` directly. `--KernelManager.transport=ipc` exists partly to keep `jupyter_client`
off it.

**IOPub's pipe is turned off through the library's own default.**
`tools/rootfs/jupydroid_kernel.py` selects `IOPubThread(pipe=False)`, which is ipykernel's own
supported configuration rather than a patch.

## Consequences

**Cost of the second workaround:** output from processes the kernel *forks* is not captured. Cell
output, tracebacks and display data are unaffected.

**Both hazards are still true**, which is why they are recorded rather than closed. A future
ipykernel or psutil release may fix either; neither should be assumed fixed without re-measuring on
the tablet.

The thing a future reader will be tempted to "fix" is `pipe=False`, which looks like a capability
being thrown away. Turning it back on aborts the kernel on Android before any capability is gained.
