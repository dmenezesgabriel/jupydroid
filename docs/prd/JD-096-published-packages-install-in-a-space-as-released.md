# JD-096 — Published packages install in a Space as released

Milestone: `M11` · State: in-progress · Depends on: — · Closes: `GAP-196`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A user who runs `%pip install duckdb` or `%pip install scikit-learn` in a Space gets what PyPI
publishes, installed and importable in the cell below, with no explanation and no per-package
patching. The environment changes so ordinary pip works; the wheels are the ones PyPI ships.

The bar is the backlog's must-list, not two packages: each of the thirty-six libraries below
installs from PyPI's wheels and then runs a small snippet taken from **its own official quickstart
or tutorial**, in a cell in the Space on the tablet. Install alone proves nothing.

## Demo

Everything here runs a cell, so the device is the arm64 tablet `SM-X216B` (AGENTS.md rule 2); on
`dev36` `FakeKernelGateway` echoes the cell's source back and a green flow proves nothing about the
kernel.

1. Cold start: install, launch, create a Space, open a notebook in it.
2. For each package in the must-list, in its own group of cells: `%pip install <package>`, then the
   snippet from that library's official quickstart/tutorial, with the output it is supposed to
   produce. The groups are the demo's unit; a group that fails names its package and the line.
3. Kill and relaunch after a group: its installed packages still import, with no reinstall and no
   kernel reselection.
4. The must-list's own rule holds throughout: nothing pre-bundled beyond jupyterlab and its
   dependencies, nothing patched per package, no workaround.

Not `shipped` until this has run on the tablet, with the frames looked at.

## Must-list

The user named these on 2026-11-20. Each installs from the wheels PyPI publishes and works properly;
each is proved by a snippet from its own official docs, run in the Space.

NumPy, pandas, SciPy, Polars, PyArrow, Matplotlib, Seaborn, Plotly, scikit-learn, XGBoost,
LightGBM, CatBoost, imbalanced-learn, PyTorch, TensorFlow, Keras, JAX, Hugging Face Transformers,
Hugging Face Datasets, spaCy, NLTK, OpenCV, Pillow, torchvision, statsmodels, SHAP, Optuna, MLflow,
PySpark, Dask, Ray, Jupyter, IPython, FastAPI, SQLAlchemy and Apache Airflow.

Two consequences the demo will surface rather than assume: a package that needs a JVM (PySpark runs
Spark's jars) or a system library the image does not carry is a finding for T7, not a reason to
pre-bundle or patch. The item's rule forbids both.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | — | The kernel's own pip answers; no platform message, no wheel selection of its own |
| `vscode@3f3380c668d0` | ABSENT | — | An editor with no Python host of its own; not read for this |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | — | A static `!pip` lint (`GAP-034`), no platform reasoning |
| `intellij-community@406a27a8da07` | ABSENT | — | Its Python package manager is a desktop one; not read for this |
| `termux-app@3df69d1da197` | ABSENT | — | An app shell; its packages come from `termux-packages`, which is not a pinned reference and rebuilds every package against bionic |
| `winlator@fb66541b93a4` | PARTIAL | `app/app/src/main/assets/rootfs.tzst`, `glibc_patches/sysdeps/unix/sysv/linux/` | Ships a glibc 2.39 rootfs run under proot, but with a **patched** libc: six shm files in `glibc_patches/`, and its `usr/lib/libc.so.6` carries no `set_robust_list` call at all (measured below) |
| the shipped rootfs | PARTIAL | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.in` | Alpine 3.23 on musl, `PIP_ONLY_BINARY=:all:`, no C toolchain, so only a `musllinux` wheel installs |

**Count:** 0 of 7 install PyPI's wheels as released. One reference, Winlator, proves that a **patched**
glibc rootfs runs under proot inside an Android app; none tests stock glibc against the filter this
app carries, and the item's premise (ADR-0003) is exactly that.

## Measured

| Question | Answer |
|---|---|
| cp312 `musllinux_*_aarch64` wheels, any release of duckdb | 0 of 168 |
| the same, scikit-learn | 0 of 76 |
| the same, for contrast: pandas / numpy / scipy | 21 of 121 / 38 of 153 / 16 of 108 |
| the 1000 most downloaded packages, latest release has no wheel this Space's pip could install | 71 of 1000; 39 once the 32 CUDA, `torch`, `triton` and `pywin32` entries are set aside |
| the same, no release at all installable | 48 of 1000 |
| in the 39, by rank | `scikit-learn` 155, `matplotlib` 172, `duckdb` 366, `pymongo` 362, `numba` 387, `psycopg2` 390, `statsmodels` 570, then `opencv-python`, `xgboost`, `lightgbm`, `onnx`, `pymupdf`, `playwright`, `watchdog`, `lz4`, `pyspark`, `ray`, `mysqlclient`, `docopt`, `h3` and others |

The PyPI counts were taken on 2026-11-19 with `scripts/read_wheel_platforms.py`; they reproduce
with the command in JD-078's history and carry their date.

| Question | Answer |
|---|---|
| when ADR-0003's glibc measurement was taken | 2026-08-25, **before** ADR-0004's SIGSYS handler existed. It measured a glibc binary dying of an unhandled `SECCOMP_RET_TRAP` at `set_robust_list`; the handler now turns that trap into `ENOSYS` |
| Winlator's glibc, version and size | 2.39, `app/app/src/main/assets/rootfs.tzst` 65,259,285 B |
| Winlator's `usr/lib/libc.so.6` | sha256 `704f3a72…`; **0** `movz x8,#99` (`set_robust_list`) instruction pairs |
| stock Ubuntu 24.04.4's `usr/lib/aarch64-linux-gnu/libc.so.6` | sha256 `fe5966a4…`; **2** `movz x8,#99` pairs |
| the shipped `lib/libsigsys_report.so` | `NEEDED libc.musl-aarch64.so.1` — a glibc root cannot load it |
| the same C file built against glibc | with `-DSYS_SECCOMP=1` (glibc lacks the macro, musl defines it), `gcc-aarch64-linux-gnu` in `debian:bookworm-slim@sha256:734a72b3…` produces a 70,016 B aarch64 `.so` with `NEEDED libc.so.6` |
| the probe rootfs | `ubuntu-base-24.04.4-base-arm64.tar.gz`, 29,870,567 B, sha256 `04207713…` (Ubuntu's `SHA256SUMS`), glibc 2.39, has `usr/bin/true`, `usr/bin/sh`, `usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1`, no `python3` |
| **T1 — stock glibc under the shipped runtime** (2026-11-20, SM-X216B) | **it does not start, and a preload cannot save it.** With no handler `/usr/bin/true` dies of SIGSYS; with a glibc-built handler preloaded it still dies, because `ld.so` issues `set_robust_list` **before any preload constructor runs**. proot's own trace names the sequence: `set_robust_list` → `signal 31` → `terminated with signal 31`, while `LD_DEBUG=libs` never reaches `calling init`; the glibc handler's log is never opened. The shipped musl handler cannot even load (`libc.musl-aarch64.so.1`), exiting 127 |
| **T2 — `gcompat` with the Space's musl CPython** (2026-11-20, SM-X216B) | **it cannot carry the wheels.** `apk add gcompat` lands `libgcompat.so.0` and its symlinks (apk's own database write fails with `EACCES` in a private root; the files are what the import needs). The Space's `python3` has `EXT_SUFFIX=.cpython-312-aarch64-linux-musl.so`, so: `duckdb`'s tag-less `_duckdb.abi3.so` is loaded and fails relocating `__res_init` (`gcompat` has no such symbol); `scikit-learn`'s `cp312` extensions are named `.cpython-312-aarch64-linux-gnu.so` and Python never looks for them. `joblib` and `threadpoolctl` install normally as `py3` wheels |
| gcompat 1.1.0-r4's surface | `libc.so.6`, `libm.so.6`, `libpthread.so.0`, `librt.so.1`, `libcrypt.so.1` and `libresolv.so.2` are symlinks to `libgcompat.so.0`; there is no `libdl.so.2` and no `__res_init` |

## Trade-offs and what we adopt

The decision is T5's; each row names the evidence that settles it.

| Axis | Options seen | Trade-off | What decides it |
|---|---|---|---|
| Where glibc lives | a whole glibc image as the Space's rootfs (Winlator's shape, libc patched); a glibc CPython beside Alpine's musl, used as the kernel | a whole image changes every seam the app has (`apk`, `pip.conf`, the `.pth`, `busybox`, the SIGSYS handler); a second interpreter keeps the system and moves the kernel | T3 and T4's measurements: does either install `duckdb` and import it, at what size, extraction time and install time |
| How the refusal is tolerated | patch glibc's `set_robust_list` out (Winlator, Termux Pacman); have the **tracer** return `ENOSYS` for it; `gcompat`'s glibc ABI over musl | T1 measured that an `LD_PRELOAD` handler cannot arm `ld.so`; T2 measured that `gcompat` cannot carry the wheels (no `__res_init`, and the `-gnu.so` names never match musl's `EXT_SUFFIX`). Patching the libc is ruled out by the item; the tracer keeps the libc as released but means shipping a modified proot (GPLv2, source publication) | T3: does a tracer that returns `ENOSYS` make a stock glibc CPython install and import `duckdb` |
| DNS inside a glibc root | glibc reads `etc/resolv.conf` normally (unlike musl, whose raw `openat` is why ADR-0005's shim exists); the app already knows Android's nameservers through `DnsServerSource` | writing the file is a mechanism glibc supports; a shim would be a second implementation of the same thing | T3, if a glibc root cannot reach PyPI |
| The SIGSYS handler | one build per libc; one libc-agnostic build with raw syscalls | per-libc is two artifacts and a choice at launch; libc-agnostic is a rewrite of a file that already works. T1 measured that neither can arm a glibc `ld.so` | T6, if a glibc path survives at all |
| JD-078's explanation | keep it for packages with no wheel on either platform; remove it when a glibc environment makes the case disappear | the note is true today and would be false in a glibc Space for manylinux-only packages | T5; a package with no wheel anywhere still needs it |

## Gap analysis — what we already have

| Concern | Ours today | Gap |
|---|---|---|
| a package with a musllinux aarch64 wheel installs and imports | works (`ADR-0011`, `ADR-0164`; pandas measured) | none |
| a package with only a manylinux wheel | pip says "No matching distribution found"; JD-078's note explains it from PyPI's release files (`ADR-0165`) | the package is not installed; the note is the outcome |
| how many packages are in that case | 39 of the 1000 most downloaded, none of the first 100 | the item exists because 39 is not a corner |
| a glibc process under the shipped runtime | T1 measured it on 2026-11-20: it cannot start, because `ld.so`'s own `set_robust_list` is refused before any preload constructor runs | the `LD_PRELOAD` route is closed; only a tracer that returns `ENOSYS` (T3) keeps a stock libc |
| a manylinux wheel imported by the musl CPython through `gcompat` | T2 measured it on 2026-11-20: no — the `cp312` extension names do not match the musl `EXT_SUFFIX`, and the tag-less `abi3` one fails on `__res_init` | `gcompat` is closed; the interpreter itself has to be glibc's (T3) |

## Deviations

- **Termux's answer is out:** rebuilding every package against bionic maintains a build farm and
  gives up PyPI's wheels; the item says the environment changes, not the libraries.
- **Winlator's answer is out:** its glibc is patched, and the patch is precisely the call T1 tests a
  handler against. It is a technique to read, not one to copy.
- **`gcompat` is out:** T2 measured that a musl CPython cannot import a `cp312` manylinux wheel's
  extensions at all, and the one tag-less extension it does load needs `__res_init`, which `gcompat`
  does not provide.

## Open decisions

1. **Whole glibc image or a second libc** — T3 and T4 measure both; T5 records the choice as an ADR.
   T1 narrowed *how* either can work (the refusal is in `ld.so`, before any preload, so a handler
   cannot tolerate it) and T2 closed `gcompat`. The one surviving mechanism is a tracer that returns
   `ENOSYS` for `set_robust_list`; a libc patch is ruled out by the item's text.
2. **What a glibc Space does with Alpine's seams** — `apk`, `usr/pip.conf`, `jupydroid_usersite.pth`,
   `busybox` and the image's `PATH` were built for the musl image. A whole-image change replaces
   them; a second interpreter keeps them and must say which processes see which.
3. **DNS** — whether a glibc root reads a written `etc/resolv.conf` or needs the app's nameservers
   another way.
4. **The tracer route's licence** — proot is GPLv2 and is shipped as Alpine's unmodified binary,
   pinned by sha256 (`scripts/stage_proot.py`). A modified proot must publish its source; whether
   that is acceptable, and where, is T5's call.
5. **The handler** — one build per libc or one libc-agnostic build, if any glibc path survives.
6. **The explanation's fate** — which packages still need JD-078's note after the change.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | possibly `WheelTarget.SPACE`'s platform tags (`InstallableWheel.kt`) if the image's platform changes |
| `domain/port` | none expected |
| `data/<mechanism>` | `SpaceRuntime.kt`, `ProotCommand.kt`, `SpaceImageInstaller.kt`, `BaseSpaceInstaller.kt` |
| `data/fake` | none expected |
| `ui/<screen>` | none expected |
| `di/AppContainer` | only if a runtime choice becomes a dependency |
| rootfs | `tools/rootfs/Dockerfile`, `tools/rootfs/build.sh`, a per-libc `sigsys_report` build |
| unit tests | the wheel-tag rule, if it changes |
| instrumented tests | `GlibcUnderProotProbeDeviceTest` (T1), then whichever probe T2–T4 need |
| `.maestro/` flow + `scripts/` driver | `scripts/glibc-under-proot-driver.sh` (T1); the item's demo flow in T7 |

## Tasks

- [x] T1 — the premise measured: does stock glibc start under the shipped runtime (proot plus a
  glibc-built SIGSYS handler), and what refuses it? A private root from Ubuntu base 24.04.4 runs
  `/usr/bin/true` and `sh -c` in the app process, in three arms (no handler, glibc handler, shipped
  musl handler), and the refusals are recorded. **Result:** it cannot start — `ld.so` issues
  `set_robust_list` before any preload constructor, so the handler is never armed
- [x] T2 — `gcompat` measured: a musl Python loading a manylinux extension through Alpine's glibc
  ABI shim, which avoids glibc's `ld.so` altogether. **Result:** it cannot carry them — the `cp312`
  extensions are named `-gnu.so` and the musl interpreter never looks for them, and the tag-less
  `abi3` one fails on `__res_init`
- [ ] T3 — a glibc Python installs a manylinux wheel: `duckdb` and `scikit-learn` installed and
  imported under a glibc interpreter whose `set_robust_list` the tracer returns `ENOSYS` for,
  including what DNS a glibc process needs. **The only surviving mechanism after T1 and T2**
- [ ] T4 — the whole glibc image measured against the second-interpreter shape: size, extraction
  time, install time, and what it does to Alpine's `apk`, `pip.conf` and `.pth`
- [ ] T5 — the candidates compared and one chosen, as an ADR; the proot licence question settled
- [ ] T6 — the chosen mechanism implemented red → green, including the tracer change (if that is
  the choice) and the rootfs build
- [ ] T7 — the must-list demo: each of the thirty-six packages installs from PyPI's wheels and runs
  a snippet from its own official quickstart, in groups, on the tablet; the row shipped; `GAP-196`
  closed; this file deleted

## Out of scope

- A package with no wheel for any platform: still JD-078's explanation.
- Source builds: `PIP_ONLY_BINARY=:all:` stays; the image deliberately carries no C toolchain.
- Other ABIs: only arm64 ships a runtime (ADR-0003).
