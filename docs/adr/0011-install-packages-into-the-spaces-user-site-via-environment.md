# ADR-0011 — Install packages into the Space's own user site, steered by environment

Status: accepted
Date: 2026-09-27

## Context

Alpine's Python ships `EXTERNALLY-MANAGED` (PEP 668), and PEP 668's own text settles the question
users ask next: the marker covers `pip install --user` too — *"packages in either location show up on
the `sys.path` of `/usr/bin/python3`"* — so there is no polite way through. The flag is
`--break-system-packages`, and with nothing else it puts packages on the rootfs's own site-packages,
which is the behaviour reported against `pandas`.

A virtualenv is not available: `pyvenv.cfg` must sit beside `sys.executable`, which here is a
per-install path inside `nativeLibraryDir` ([ADR-0002](0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md)).

`HOME` is the user's **visible** workspace (`notebookWorkspaceIn`, `home/jupydroid`), so anything
defaulting to `$HOME` writes into the file explorer's tree.

## Decision drivers

- A user must not have to remember a flag for `%pip install` to do the right thing.
- Installed packages belong to a Space, not to the shared rootfs.
- Nothing may appear in the file explorer that the user did not put there.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `--break-system-packages` alone | One flag | Writes into the shared rootfs site-packages, so one Space's install changes every Space |
| A virtualenv per Space | The standard answer | Impossible: `pyvenv.cfg` must sit beside `sys.executable`, which is a per-install path |
| `pip.conf` in `$HOME` | Configuration in a file | `$HOME` is the user's visible workspace; the config file and the default `~/.local` user base would both appear in the file explorer |
| `PYTHONUSERBASE` + `PIP_*` environment variables | Every child of the Space env inherits it; nothing lands in the workspace | Configuration is invisible unless you read `SpaceRuntime` |

## Decision

**`PYTHONUSERBASE=<space>/pkgs` moves the user site inside the Space** — stdlib `site.py` reads the
variable directly — and pip is pointed at it by **environment rather than by flags**:
`PIP_USER=1`, `PIP_ONLY_BINARY=:all:` and `PIP_BREAK_SYSTEM_PACKAGES=1` in
`SpaceRuntime.environment()`.

Kernel, terminal, `%pip` and `pylsp` all inherit it, because each is a child of the Space
environment. `pkgs/bin` joins `PATH` so a `--user` install's console scripts resolve.

## Consequences

**This forbids** installing into the system site-packages on purpose again, and shipping a `pip.conf`
inside the workspace.

`PIP_ONLY_BINARY=:all:` means a package with no musllinux wheel fails rather than trying to build.
That is deliberate: there is no compiler in the Space, so a source build would fail later and less
clearly.

The thing a future reader will be tempted to "fix" is the use of environment variables over a config
file, which is harder to discover. A config file would have to live in `$HOME`, and `$HOME` is the
user's notebook folder.

## Amendment — 2026-10-05

The "a virtualenv is not available" premise was retired by
[ADR-0073](0073-a-space-owns-its-rootfs-and-runs-under-proot.md): under proot, `sys.executable`
is the image's own stable `/usr/bin/python3`, and a venv was measured working. The decision
stands because it answers a different question than whether a venv can exist — an unflagged
`%pip install` must land somewhere that is neither the shared system site-packages nor the
user's visible workspace, and `PYTHONUSERBASE` + `PIP_*` is still that somewhere. A venv inside
a Space is now the user's own tool, not the install default.
