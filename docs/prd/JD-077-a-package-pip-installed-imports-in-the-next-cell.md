# JD-077 — A package `%pip` installed imports in the next cell

Milestone: `M11` · State: in-progress · Depends on: — · Closes: `GAP-195`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

After `%pip install <package>` in one cell, the `import <package>` in the cell below resolves in the
same kernel, without the person selecting or restarting the kernel.

## Demo

Everything here runs a cell, so the device is the arm64 tablet `SM-X216B` (AGENTS.md rule 2); on
`dev36` `MainActivity` falls back to `FakeKernelGateway` and echoes the cell's source back.

1. Cold start: install the build, launch, open a Space, create a notebook.
2. `import tabulate` in a cell: `ModuleNotFoundError` — the Space's `pkgs/` has never been written,
   which is the state every Space starts in.
3. `%pip install tabulate` in the next cell: pip installs it.
4. `import tabulate; print(tabulate.__file__)` in the cell below: the path under `/pkgs/`. Before
   this item it raises `ModuleNotFoundError`, and only restarting the kernel made it work.
5. `%pip install pandas` then `import pandas` in a fresh Space: the control, unchanged — a package
   that installs today keeps installing.
6. Kill the app and relaunch: the same imports resolve.
7. A screenshot and a UI dump at every step that changes the screen, looked at.

## Who else implements this

Each row is a grep over the pinned clone named in [`../README.md`](../README.md), run on 2026-11-20,
for the thing this item is about: putting a package installed while an interpreter is running on
that interpreter's import path.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | No post-install rescan: `grep -rn invalidate_caches packages/ src/` finds nothing, and the kernel manager (`packages/services/src/kernel/default.ts`) only relays the cell's own output |
| `vscode` | ABSENT | — | Not a Python host: it reaches a Jupyter server through an extension and runs no interpreter of its own |
| `vscode-jupyter` | ABSENT | `src/standalone/intellisense/diagnosticsProvider.ts` (`NotebookCellBangInstallDiagnosticsProvider`) | Its `!pip` work is a static lint that says "use `%pip` instead" (`GAP-034`); nothing reads what an install did, and its `pip install` strings are for jupyter's own dependencies (`src/kernels/errors/kernelErrorHandler.ts`) |
| `intellij-community` | ABSENT | — | A desktop package manager over an interpreter it owns; no cell runs an install behind a live kernel |
| `termux-app` | ABSENT | — | An app shell; its packages come from `termux-packages`, which is not a pinned reference |
| `winlator` | ABSENT | — | Not a Python host |
| the shipped rootfs | PARTIAL | `usr/lib/python3.12/site-packages/IPython/core/magics/packaging.py`, `usr/lib/python3.12/site.py` | IPython's `%pip` runs `sys.executable -m pip …` and then prints "Note: you may need to restart the kernel to use updated packages" — it names the workaround and repairs nothing; CPython 3.12's `site.addusersitepackages` adds the user site only when the directory already exists |

**Count:** 0 of 7 put a running interpreter's import path right; 1 of 7 tells the person to restart.
The absence is unanimous, and the one that speaks says what the reporter did. What decides this item
is measured on the device, not read from a reference.

## Implementation inventory

Only the shipped rootfs is FULL or PARTIAL, so it is the only one inventoried.

### the shipped rootfs (Alpine 3.23, `tools/rootfs/Dockerfile`)

| Concern | Files |
|---|---|
| Where `%pip` runs pip | `usr/lib/python3.12/site-packages/IPython/core/magics/packaging.py` (`PackagingMagics.pip`) |
| What it does afterwards | nothing: the magic returns after `self.shell.system(...)` and a printed note |
| How the user site reaches `sys.path` | `usr/lib/python3.12/site.py` (`addusersitepackages`: `if ENABLE_USER_SITE and os.path.isdir(user_site)`) |
| What puts `PYTHONUSERBASE` there | `app/src/main/java/com/jupydroid/app/data/space/SpaceRuntime.kt` (`environment()`), `SpacePipConfig.kt` (`usr/pip.conf`, `user = true`) |
| Where the kernel starts | `tools/rootfs/jupydroid_kernel.py`, launched by the spec `SpaceKernelSpec` writes |
| The image's own python version dir | `usr/lib/python3.12/` — the only `python3.*` the image carries |

**The disagreement this item has to decide** is not between references, which all decline: it is
between *where* the repair belongs — the image (a file every interpreter reads), the kernel module
(ours alone), or the app (the directory `PYTHONUSERBASE` names). Those are the four rows below.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What the cause is | A: the import system's directory cache; B: the install lands off the kernel's `sys.path` | Different fixes: `importlib.invalidate_caches()` after the install, or the path itself | **B, by reading, confirmed by T1.** CPython 3.12 guards the user site on `os.path.isdir`; the image ships no `pkgs/`, and `SpaceDirs.ensure()` creates `pkgs` but not `pkgs/lib/python3.12/site-packages`, so a kernel started before the first install never has the path. The probe's `invalidate_caches` arm excludes A on the device |
| Where the path is repaired | (A) `sitecustomize.py` in the image appending `site.getusersitepackages()`, guarded on `site.ENABLE_USER_SITE`; (B) the same append in `tools/rootfs/jupydroid_kernel.py`; (C) the app creating `pkgs/lib/python3.X/site-packages` in `SpaceDirs.ensure()`, X discovered from `usr/lib`; (D) `PYTHONPATH` in `SpaceRuntime.environment()` | A and B need an arm64 rootfs rebuild for the demo. C is app-only but reintroduces the interpreter-version coupling the parity ledger already flags for `pkgs/` (`research/parity-ledger.md`, "`pkgs/` strands on an interpreter bump"). D changes import precedence (before site-packages) and reaches venv kernels, which deliberately exclude the user site | Open until T2. The criterion is stated now so T2 does not re-litigate it: the repair names no interpreter version, does not reach a venv (where `ENABLE_USER_SITE` is already False), and leaves the image the one home of what an interpreter in the Space does |
| What the person is told | nothing (today's silent failure); or the existing IPython note | The note says "restart the kernel", which is true today and false after the repair | Nothing new on screen: after the repair the note is stale rather than wrong for a first install, and removing it would edit a third-party magic's text |
| The regression test | a JVM unit test; an instrumented probe; a Maestro flow | Only a device can start the real interpreter under proot | The instrumented test is the regression test (red before the repair), because it runs the Space's own interpreter; the Maestro demo shows the cell |

## Gap analysis — what we already have

| Concern | Ours today (file) | Gap |
|---|---|---|
| An install lands in the Space's user base | `PipInstallDeviceTest`, `SpacePipConfig`, `SpaceRuntime.environment()` | present and complete |
| `%pip` reaches pip through the Space's shell | `ShellEscapeDeviceTest` | present and complete |
| A fresh interpreter imports what was installed | `PipInstallDeviceTest`'s `python3 -c 'import tabulate'` | present — and it is why this defect went unseen: a fresh interpreter is the restart the reporter used |
| A **running** kernel imports what was just installed | nothing; `sys.path` is fixed at kernel start | **absent** — this item |
| The path exists before anything is installed | `SpaceDirs.ensure()` creates `pkgs`, not `pkgs/lib/python3.12/site-packages`; the image carries no `pkgs/` at all | absent |

## Deviations

The references decline the capability, so there is nothing to deviate from. One deviation from
IPython's own advice: we do not tell the person to restart the kernel, and after the repair a first
install needs no restart at all.

## Open decisions

1. Which of the four repair shapes (A–D above), decided at T2 on T1's measurement and on the demo
   cost each carries. A and B are version-free and need an arm64 rootfs rebuild; C needs no rebuild
   but must discover `python3.X`.
2. Whether a venv kernel keeps its isolation. `site.ENABLE_USER_SITE` is False in a venv, so a repair
   guarded on it does not reach one; a repair that appends unconditionally would. The evidence: a
   venv's own `%pip` installs into the venv (`python-environments-in-a-space-venv-pip-and-uv-driver.sh`),
   so it needs no repair.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | not applicable: no app type carries this |
| `domain/port` | not applicable: no port operation changes |
| `data/<mechanism>` | `SpaceDirs.ensure()` only if option C is chosen; otherwise `tools/rootfs/` |
| `data/fake` | not applicable: no port changes, so no fake changes |
| `ui/<screen>` | not applicable: nothing on screen changes |
| `di/AppContainer` | not applicable |
| unit tests | only for a Kotlin change (option C): `SpaceDirsTest` |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/data/space/PipImportProbeDeviceTest.kt` (T1), then the same class as the regression test (T2) |
| `.maestro/` flow + `scripts/` driver | a flow shaped like `.maestro/python-environments-in-a-space-base-pip-cell.yaml`, run by a driver that clears `pkgs/` first |

## Tasks

- [x] T1 — the measurement on the tablet, by `PipImportProbeDeviceTest`. It is the path: a
  long-lived interpreter reads `user_site_exists: False` and `user_site_on_path: False`, fails
  `import` before **and after** the install, fails again after `importlib.invalidate_caches()`
  (which excludes the cache reading), imports once the user site is appended, and a second
  interpreter imports it with no append — the restart the reporter used. Through the app's own
  gateway the same three cells read the same, the `%pip` cell printing IPython's "you may need to
  restart the kernel" and the `import` cell raising `ModuleNotFoundError`
- [ ] T2 — the repair chosen on T1, red → green: the probe's import arm becomes an assertion, then the
  chosen shape turns it green; an arm64 rootfs rebuild if the repair is in the image
- [ ] T3 — the demo above on the tablet, `GAP-195` closed, the backlog row `shipped`, this file deleted

## Out of scope

`GAP-196` and `JD-078`: a package with no musllinux aarch64 wheel, which is pip's answer about the
platform and not this item's. A venv's own install, which already works.
