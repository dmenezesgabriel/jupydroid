# ADR-0078 — A Space's environments become kernels through a spec the app writes

Status: accepted
Date: 2026-10-08

## Context

A Space owns its rootfs and is its own system
([ADR-0073](0073-a-space-owns-its-rootfs-and-runs-under-proot.md)): its terminal makes
environments the way Alpine makes them — `python3 -m venv`, or Alpine's `uv` — and an
unflagged `pip install` inside one lands in it
([ADR-0011](0011-install-packages-into-the-spaces-user-site-via-environment.md), amended
2026-10-08). What makes such an environment a *kernel* is the question.

The references split:

- vscode-jupyter writes **no spec**: every interpreter is a kernel candidate, and a notebook
  records `python3` "so that its portable" (`vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155
  src/kernels/execution/helpers.ts`).
- JupyterLab and the server know only **specs**: a kernel is a directory `jupyter_path` finds
  (`jupyter_core/paths.py`), registered by hand with `ipykernel install`.

Our server is `jupyter_server`, so a kernel must be a spec — and `ipykernel install`'s own argv
(`python -m ipykernel_launcher`) cannot start here: stock ipykernel's IOPub thread binds a TCP
socket, which Android refuses an app. `tools/rootfs/jupydroid_kernel.py` is the repair, and
`SpaceKernelSpec` already writes the image's `python3` spec to run it. A by-hand
`ipykernel install` therefore registers a kernel that dies; the app must write the spec itself.

## Decision drivers

- Registration is the person's act: an environment is theirs, made in their terminal, and only
  they say it becomes a kernel.
- A stopped Space's catalogue must be the same one its server will search — no server may have
  to start to answer "what kernels does this Space have".
- A spec the app writes must start the kernel only the app can start — the image's module under
  the environment's own interpreter, never `ipykernel_launcher`.
- A chosen default survives process death, and what it names must still resolve after the
  environment is deleted.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Every interpreter is implicitly a kernel (vscode-jupyter) | Nothing to register | Our server is a spec server; the catalogue would hold interpreters nothing can start |
| `ipykernel install` in the terminal (JupyterLab) | The standard tool | Writes an argv that aborts on Android — a registered kernel that dies |
| Register in Settings: the app writes the env's spec | The spec is exactly the one that starts | One more act in the UI |
| Specs in `$HOME/.local/share/jupyter` | `jupyter_path`'s user dir | HOME is the visible workspace — ADR-0011's reason for `/usr/pip.conf` |
| Specs in `usr/local/share/jupyter/kernels/` | `jupyter_path` searches it; the workspace never shows it | — |
| Server-side `default_kernel_name` alone | Jupyter's own default | A stopped Space could not say its default; a stored name nothing lists would default a notebook onto a kernel nothing can start |

## Decision

**Register** in a stopped Space's Settings writes
`usr/local/share/jupyter/kernels/<name>/kernel.json` under the Space's rootfs
(`SpaceKernelSpec.installEnvironment`, `app/src/main/java/com/jupydroid/app/data/space/`):
argv[0] the environment's own `bin/python` as a process in the Space names it, running
`jupydroid_kernel` by path through `runpy.run_path` — a venv's `sys.path` lacks the system
site-packages the module lives in — with the same flags, env and metadata as the image's
`python3` spec. `<sys.prefix>/local/share/jupyter` is on `jupyter_path`, and the spec lives where
the person's workspace never shows it. The spec's name is the environment's directory name, the
one string `_kernel_name_pat` already accepts.

The catalogue is **read off disk** (`DiskKernelSpecs` over `SpaceKernelDirs`' two searched
roots), so a stopped Space's Settings and an open notebook's picker list what the server will
list. A spec whose interpreter is gone is kept off the usable picks, and the native `python3`
is re-added when no kept spec carries the name, so `defaultSpec` always resolves.

Environments are **found, not typed**: `DiskSpaceEnvironments` scans fixed roots for
`pyvenv.cfg` — vscode-python's fixed roots, not intellij's path picker. Register is offered
where the environment has `ipykernel`; where it does not the row says so and refuses, naming
the install command — the app installs nothing into a person's environment.

The **default kernel** is a `SpaceSettings` field, stored in the Space's `config.json` and
passed at each start as `--MappingKernelManager.default_kernel_name` (jupyter_client's
traitlet; `JupyterServerLauncher`) — winlator's per-container choice, in jupyter_server's
vocabulary. A stored name the catalogue no longer lists falls back to `python3`: one rule
answers the stopped catalogue and the launch alike, so a new notebook never records a kernel
nothing can start.

This amends [ADR-0076](0076-entering-a-space-binds-per-space-ports-and-a-notebook-records-its-kernel.md)'s
"`python3` today": a new notebook still records the kernel's own name through
`specs(space).defaultSpec` — now the Space's stored default rather than always `python3`.

Measured on `SM-X216B` (`scripts/python-environments-in-a-space-demo-driver.sh`): Register on
`envs/stats` wrote `usr/local/share/jupyter/kernels/stats/kernel.json`, argv
`/home/jupydroid/envs/stats/bin/python -c "import runpy; runpy.run_path(...)"`; `config.json`
read `"defaultKernel":"stats"` with the app killed and a relaunch read it back; a notebook left
open while its server stopped listed `Python (stats)` and `Python 3` in its picker with no
server started; a new notebook recorded `kernelspec: stats` and printed `5` —
`len(sys.prefix.split('/'))` of `/home/jupydroid/envs/stats` — while the same cell on base's
`python3` died `ModuleNotFoundError: No module named 'tabulate'`.

## Consequences

- A deleted environment leaves its spec behind: the catalogue drops it from the usable picks,
  Settings says the environment is gone and names `jupyter kernelspec remove` as the cleanup,
  and a stored default pointing at it reads back as `python3` until removed.
- `uv` is not bundled: installing it is the Space's own act (`apk add uv` — Alpine's
  musl-linked build resolves through the shim where PyPI's static build cannot, ADR-0073
  amended 2026-10-08).
- A Space's kernels stay portable both ways: `python3` still opens a notebook anywhere, and an
  environment's spec is a file in the Space's own rootfs — a copy of the Space sees exactly
  what the stopped catalogue saw.
