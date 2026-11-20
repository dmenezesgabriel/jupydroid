# ADR-0164 — An interpreter in a Space adds its user site before the first install

Status: accepted
Date: 2026-11-20

## Context

Reported on the published build: after `%pip install <package>` in one cell, `import <package>` in
the cell below sometimes says the package is not found, and selecting the kernel again makes it
work. Measured on the tablet (SM-X216B) by `PipImportProbeDeviceTest`, in two arms — one long-lived
interpreter and the app's own gateway driving a real kernel:

- `site.addusersitepackages` adds the user site **only when the directory already exists**
  (`usr/lib/python3.12/site.py`: `if ENABLE_USER_SITE and os.path.isdir(user_site)`).
- The Space's user base is `PYTHONUSERBASE=/pkgs` (`SpaceRuntime.environment`), so the user site is
  `/pkgs/lib/python3.12/site-packages`. The image ships no `pkgs/` at all, and `SpaceDirs.ensure`
  creates `pkgs` but not the `lib/python3.12/site-packages` under it; `usr/pip.conf` makes every
  install `--user`, so it is the first `pip install` that writes that directory.
- A kernel started before that first install reads `user_site_on_path: False`. The install that
  creates the directory runs in a subprocess that never re-runs `site`, so the kernel's own
  `sys.path` is unchanged. The same interpreter still fails after
  `importlib.invalidate_caches()`, which excludes the import system's directory cache as the cause,
  and a second interpreter imports the package with no append — that is the restart the reporter
  used. "Sometimes" is the Space that had an earlier install.

A second failure, found while choosing the repair, decides its shape: a path added to `sys.path`
while its directory is missing is cached as "no finder" (`sys.path_importer_cache[path] = None`), so
the install that creates the directory afterwards stays invisible to that interpreter. Measured:
appending a missing directory and then writing a module into it still raised `ModuleNotFoundError`,
while creating the directory first and then appending made the later write importable. The repair
must therefore **create** the directory, not only add the path.

The references all decline the capability: jupyterlab, vscode, vscode-jupyter, intellij-community,
termux-app and winlator rescan nothing after an install, and IPython's `%pip`
(`usr/lib/python3.12/site-packages/IPython/core/magics/packaging.py`) prints "Note: you may need to
restart the kernel to use updated packages" and repairs nothing.

## Decision drivers

- Fix the first place the system went wrong — the `isdir` guard at interpreter start — rather than
  the `import` that fails.
- The image is the one home of what an interpreter in the Space does. The app must not learn the
  interpreter's version or its user-site layout: the parity ledger already records that `pkgs/`
  strands on an interpreter bump, and a second coupling point would strand it twice.
- Name no interpreter version in the repair, so the image moving to python3.13 does not edit it.
- A venv keeps its isolation: `ENABLE_USER_SITE` is false there, and its own `%pip` installs into
  the venv.
- Do not change precedence. When the directory exists, `site` puts the user site **before** the
  image's site-packages, so a package a person upgrades shadows the shipped one.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A `.pth` hook in the image importing `jupydroid_usersite` | no interpreter version in the code; every interpreter in the Space; takes no name another hook needs; the image already ships a `.pth` that does this (`distutils-precedence.pth`) | needs an image rebuild to reach a device |
| `sitecustomize.py` in the image | one file, the documented CPython hook | owns a name that `debugpy/_vendored/pydevd/pydev_sitecustomize/sitecustomize.py` and a person's own hook both want, and silently shadows either |
| The kernel module (`jupydroid_kernel.py`) | smallest surface | only kernels: a terminal's `python3` and `pylsp` keep the bug; an exception there kills the kernel, where `site` catches it |
| The app creates the directory (`SpaceDirs.ensure`) | no rebuild | the app names the interpreter's version and its layout — the coupling the parity ledger already flags for `pkgs/` |
| The image ships an empty `pkgs/lib/python3.12/site-packages` | no code at all | `replaceImage` keeps `pkgs`, so the directory arrives only through an extract into an existing tree; a person who deletes `pkgs` reproduces the bug until a re-extract |

## Decision

A `.pth` in the image's site-packages imports `jupydroid_usersite`, whose `add_user_site()` creates
the user site when it is missing and inserts it before the image's own site-packages, guarded on
`site.ENABLE_USER_SITE`.

`site.addsitedir` executes a `.pth` for every interpreter whose `sys.path` carries the image's
site-packages — the kernel, the terminal's `python3` and `pylsp` — and it does so before
`execsitecustomize`, so a person's own `sitecustomize` still runs. It names no interpreter version
(`site.getusersitepackages()` does), and it takes no name another hook needs. Creating the directory
is what makes an install that lands afterwards visible to the interpreter that is already running;
inserting at `site`'s own position is what keeps a person's `--upgrade` of a shipped package
winning.

## Consequences

- An interpreter in a Space sees `pkgs/lib/python3.X/site-packages` from its first start, so
  `%pip install` and then `import` in the next cell resolves, and so does
  `python3 -c "import <package>"` in the terminal.
- `pkgs/lib/python3.X/site-packages` now exists, empty, in a Space that has never installed
  anything. `import <package>` still raises `ModuleNotFoundError`; what changed is that the
  directory is there when the install lands.
- A venv created without `--system-site-packages` does not load the hook and has
  `ENABLE_USER_SITE` false. Measured: a `--system-site-packages` venv does load it and gets the user
  site, which is what a desktop does with the same flag.
- The repair reaches a device only through an image bump (`BUNDLED_ROOTFS_VERSION`), which
  re-extracts base and keeps `home`, `pkgs` and `config.json`. A person's own Space keeps the image
  it was created from until a restore puts the bundled one back (ADR-0073, ADR-0107).
- IPython's `%pip` note stays as it is: it is a third-party magic's text, and after this repair it
  is stale rather than wrong.
- What a future reader will be tempted to "fix": `insertion_index()` looks as if it could be
  `sys.path.append`. It cannot — appending would make a person's `--upgrade` of a shipped package
  (numpy is pinned in `tools/rootfs/requirements.lock`) resolve to the image's copy instead of the
  one they installed.
