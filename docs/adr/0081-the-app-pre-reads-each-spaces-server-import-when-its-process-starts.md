# ADR-0081 — The app pre-reads each Space's server import when its process starts

Status: accepted
Date: 2026-10-09

## Context

On a warm start, a Space's server boot (`starting jupyter_server` → `jupyter_server listening` in
`SpaceServer`'s log) takes 4.12 s median on the tablet (`SM-X216B`). `ServerBootProbeDeviceTest`
split it: the boot is the import of `jupyter_server.serverapp`. That is about 2.0 s of import work
plus about 1.3 s of reading the image's Python from storage, because between launches the page cache
no longer holds it. The server's own work after the import takes under 0.3 s, and the launcher's
poll under 0.12 s.

One way to import less was ruled out on facts before anything was measured. `serverapp.py` imports
`jupyter_client` and `jupyter_events.logger` at the top of the module (shipped rootfs,
`usr/lib/python3.12/site-packages/jupyter_server/serverapp.py`). Taking their 0.6 s and 0.46 s off
the path means patching jupyter_server in the image.

The probe measured the rest from an evicted page cache. These are the ranges over three probe runs,
each run's value a median of three:

| | start | other cost |
|---|---:|---|
| no repair | 3.54–3.56 s | — |
| pre-read: `import jupyter_server.serverapp` run once and thrown away, then the start | 2.37–2.38 s | nothing held |
| the same pre-read begun beside the start, as a **Run** tapped at once | 2.78–2.90 s | nothing held |
| jsonschema's eight optional format checkers made unimportable | import 0.25–0.5 s shorter | those formats go unchecked |
| a server pre-started before **Run** | none, once it is up | 47.1 MB PSS idle, 115.7 MB with its spare kernel (ADR-0080); ~0 ms CPU per 30 s idle, 40 ms with the spare |

## Decision drivers

The criteria were fixed before the probe ran:

1. The boot with the repair, from an evicted cache.
2. What the repair costs: memory held and CPU spent while idle.
3. What the repair obliges. At equal gain, the option with fewer obligations wins.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **Pre-read** | About 1.2 s off, or 0.7 s off when **Run** is tapped at once. Holds nothing, and runs nothing the user did not ask for, so the home's running state still means a Space was run | Leaves the ~2.0 s of import work. Holds only while the page cache does |
| Pre-read and uninstall the format checkers | 0.25–0.5 s more, the same kind of repair as [ADR-0014](0014-uninstall-rfc3987-syntax-from-the-image.md) | A rootfs rebuild for a quarter-second. The fqdn, idna, uri, date-time, color, json-pointer, uri-template and duration formats go unchecked in jupyter_events |
| Pre-start the server | Takes the whole boot off the path when **Run** comes after it is up. Upstream precedent: vscode-jupyter's `src/kernels/jupyter/launcher/serverPreload.node.ts` (`vscode-jupyter@f01d80ca`) | Holds 47.1–115.7 MB for a Space nobody may run. The home would show a Space running that nobody ran. Needs rules for which Space, for nobody tapping **Run**, and for the idle stop |

The pre-start gains more, so criterion 3's tie-break does not decide. It loses on criterion 2,
unlike ADR-0080's spare. There, the memory was what the notebook's kernel would hold seconds later,
on the path it exists for. Here it would be held from the moment the app opens, for any Space the
user did not run. The pre-read was chosen.

## Decision

When the app's process starts, it runs `import jupyter_server.serverapp` once in each installed
Space, through the Space's own runtime under proot, and throws the process away. What it leaves is
the page cache. The pre-reads run on the application scope, one Space after another, with base first.

## Consequences

The obligations, each with its answer:

- **A Space is not installed.** It is skipped. Installing it writes its files, and that leaves them
  in the page cache anyway.
- **Run is tapped while the pre-read runs.** The start goes ahead beside it and never waits for it.
  The probe measured that case at 2.78–2.90 s, still faster than no pre-read.
- **The pre-read fails or is killed.** Nothing else depends on it. It is an optimisation, never a
  reason for a start to fail, and its failure is only logged.
- **A Space's server is already running.** That Space is skipped, since its server already did the
  read.
- **Several Spaces.** Each Space owns its rootfs (ADR-0073), so each needs its own pre-read. One at a
  time keeps them from competing with each other. A Space later in the order may be run before its
  pre-read starts, and then starts as it does today.
- **The log says when a pre-read ran**, its start and its end. Otherwise a fast boot reads as a fast
  server.

**This forbids** a server start waiting on a pre-read, and a pre-read starting a server or holding
anything after it exits.
