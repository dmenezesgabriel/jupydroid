# ADR-0080 — A Space pre-starts its default kernel when its server starts

Status: accepted
Date: 2026-10-08

## Context

On a warm start the kernel window, `kernel.socket.opened → kernel.socket.connected`, takes 5.25 s
on the tablet (`SM-X216B`). That is the largest stage ahead of a notebook's first answer. The
jupyter_server socket accepts only once the kernel answers `kernel_info`, so the window is the
kernel starting. `KernelWindowProbeDeviceTest` split it: almost all of it is
`import jupydroid_kernel`, about 2.7 s of import work plus about 2.5 s of reading the image's
Python from storage, because between launches the page cache no longer holds it. Meanwhile the user
walks about 13 s from Run on the home to the first cell, and the device does no kernel work in
that time.

Two ways to shrink the window were ruled out on facts before anything was measured:
- **Importing less** means patching IPython and ipykernel in the image. ipykernel's `ipkernel.py`
  imports `.debugger` itself, and IPython's `__init__.py` imports its terminal frontend. The
  ceiling is about 1.2 s, and the storage half stays.
- **`unchecked-hash` bytecode** is forbidden by
  [ADR-0013](0013-ship-bytecode-invalidated-by-checked-hash.md).

The probe measured the two that remained, each from an evicted page cache. These are the ranges
over three probe runs, each run's value a median of three:

| | window | other cost |
|---|---:|---|
| no repair | 4.36–4.43 s | — |
| pre-read: `import jupydroid_kernel` run once and thrown away | 2.70–2.81 s | none: beside the server's start, the start went 3.54–3.75 s → 2.68–2.77 s |
| pre-start: a kernel started 13 s before a session binds it | 0.073–0.084 s | 53.6 MB PSS while it waits |

## Decision drivers

The criteria were fixed before the probe ran:

1. The window with the repair, from an evicted cache.
2. What the repair costs: memory held, and any slowdown of the server's start.
3. What the repair obliges. At equal gain, the option with fewer obligations wins.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Pre-read | No semantics: nothing runs that the user did not ask for. Costs the server nothing | Leaves ~2.7 s of import in the window. Holds only while the page cache does |
| **Pre-start the Space's default kernel** | Takes the window under 0.1 s, both halves, because the kernel's own start does the read and the import during the walk. Upstream precedent: kernel_gateway's `KernelGatewayApp.prespawn_count` pre-spawns kernels of the default language (`jupyter-server/kernel_gateway@8c483ff0 kernel_gateway/gatewayapp.py`) | 53.6 MB held until a session binds it. A lifecycle of its own, below |
| Both | — | A pre-started kernel already reads what a pre-read would; the pre-read adds nothing |

The gains are not equal: about 4.3 s against about 1.7 s, so criterion 3's tie-break does not
decide. The pre-start's cost is bounded. On the path it exists for, the 53.6 MB is the same memory
the notebook's kernel would hold 13 s later. On every other path, the server's own cull shuts the
kernel down.

## Decision

When a Space's server has started, the app starts one kernel of the Space's default kernel
(`POST api/kernels` with the name the server was started with as
`MappingKernelManager.default_kernel_name`). The first session that asks for that kernel name binds
it, with `POST api/sessions` carrying `{"kernel": {"id": …}}`, which stock jupyter_server binds to a
running kernel (`services/sessions/handlers.py`). Every other session starts its kernel by name,
as before.

## Consequences

The obligations, each with its answer:

- **A session asks for another kernel.** It starts that kernel by name. The spare is left to the
  server's cull, `cull_idle_timeout`, which `JupyterServerLauncher` already sets to the Space's idle
  setting. A closed notebook's kernel is handled the same way.
- **The spare died before it was bound.** The session falls back to starting by name. A spare is
  an optimisation and never a reason for a session to fail.
- **A session is asked for while the spare is still starting**, as when a cell's first run is what
  starts the server. It binds the starting kernel. Starting a second one would be a kernel nobody
  uses, and the socket already waits for the kernel to answer.
- **Only the first session of a server start is faster.** Later sessions pay the window as before.
  A refilled pool would hold another 53.6 MB at all times, for a case not measured.
- **Nobody opens a notebook.** The spare idles until the cull. jupyter_server's
  `shutdown_no_activity_timeout` waits for no kernels, so the server's self-stop also comes about
  one cull later than it would with none.
- **The kernel log says which kernel a session bound**, a spare or one it started. Otherwise a
  0.1 s window reads as the kernel starting in 0.1 s.

**This forbids** a session waiting on, or failing because of, a spare, and holding more than one
spare per running Space.
