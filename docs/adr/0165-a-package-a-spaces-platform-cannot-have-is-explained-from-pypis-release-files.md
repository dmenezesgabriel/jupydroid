# ADR-0165 — A package a Space's platform cannot have is explained from PyPI's release files

Status: accepted
Date: 2026-11-20

## Context

`%pip install duckdb` and `%pip install scikit-learn` in a Space answer as if the package did not
exist. Measured on the tablet (SM-X216B), verbatim, on the cell's `stdout` stream:

```
ERROR: Could not find a version that satisfies the requirement duckdb (from versions: none)
ERROR: No matching distribution found for duckdb
Note: you may need to restart the kernel to use updated packages.
```

The same two lines, naming `duckdbzz`, come back for a package that exists nowhere — so **pip's own
text cannot tell a typo from a package this platform cannot have**, and a message read off it would
be a lie half the time.

The cause is the platform the app chose. A Space is Alpine on musl with CPython 3.12 on arm64, and
its pip installs wheels only: `PIP_ONLY_BINARY=:all:` rides every Space process
(`SpaceRuntime.environment`) and the image carries no C toolchain on purpose
(`tools/rootfs/Dockerfile`). So pip looks for a `cp312` `musllinux_*_aarch64` wheel, and nothing else
will do: a `manylinux` wheel is built for glibc, whose startup Android's seccomp filter refuses
(ADR-0003).

How many packages are in that case is a count PyPI holds. `scripts/read_wheel_platforms.py` reads it
per package, and `installable()` there is the rule: the Python tag matches, or is `py3`, or is an
`abi3` wheel of an older CPython; the platform is `any` or a `musllinux_*` tag of the architecture.
Read on 2026-11-19 over every release: **duckdb 0 of 168, scikit-learn 0 of 76**, against pandas 21
of 121, numpy 38 of 153 and scipy 16 of 108. Over the 1000 most downloaded packages, **71** have no
installable wheel in their latest release, and **39** once the 32 CUDA, `torch`, `triton` and
`pywin32` entries (no use in an arm64 Space) are set aside; none of the first 100 are among them.
duckdb is rank 366, scikit-learn 155, matplotlib 172, psycopg2 390, numba 387.

The alternatives were measured too. Alpine v3.23 for aarch64 carries `py3-scikit-learn` 1.5.2-r0 in
`community`: 8.5 MB, 27 MB installed, and it needs `py3-numpy`, `py3-scipy`, `py3-joblib`,
`py3-threadpoolctl`, libgomp and libstdc++. It carries no duckdb at all — that is in `edge`
`community` (1.5.6) and `edge` `testing` (`py3-duckdb` 1.5.6), which is not the image's repository.
A source build is not an exit for the same reason the image has no toolchain, and pip refuses an
sdist outright under `PIP_ONLY_BINARY`.

The app already holds pip's text: `%pip` runs `shell.system("python -m pip …")`
(`IPython/core/magics/packaging.py`, `PackagingMagics.pip`), so pip writes to the kernel's own
descriptors and the app receives it as the cell's output. The rootfs offers no seam between pip and
the cell to intercept.

## Decision drivers

- One answer true for every package in the case, not one package at a time: bundling scikit-learn
  leaves the other 70 standing.
- Never say something false. pip's text for a typo is identical to pip's text for this case, so the
  claim must be checked against the source of truth — PyPI's own release files — and said only when
  they confirm it.
- The app's words must not be read as pip's.
- What is written must survive a save and a relaunch, because the cell's output is the record of what
  the run said.
- No bytes added to the APK for a case whose population is known but whose members are not.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **A** explain, in the cell's own output, checked against PyPI | costs no bytes; true for every package in the case; the text is already in the cell | one request to PyPI per failed install, and the app speaks where a tool used to |
| **B** bundle Alpine's package in the base rootfs (scikit-learn only) | offline; no request | 27 MB installed plus numpy and scipy, at 1.5.2 — years behind PyPI; duckdb is in no v3.23 repository; and 70 other packages are left in the case |
| **C** a wheelhouse built in CI from source | offline, for whatever it builds | a C toolchain stage the image deliberately lacks, hours of build for duckdb, and somewhere to host the wheels |

## Decision

**A.** When pip reports that it found no distribution at all for a requirement, the app asks PyPI
what that package publishes, and — only when no release ships a wheel this Space's pip could
install — appends a note of its own to the cell's output saying so, and what the package ships
instead.

- The question is asked through `WheelIndex` (`domain/port/WheelIndex.kt`), which reads
  `https://pypi.org/pypi/<normalized name>/json`; a name PyPI does not have, or a host that cannot
  be reached, is `null` and produces nothing.
- The rule is `wheelFitsTarget` (`domain/model/InstallableWheel.kt`), the same tags
  `scripts/read_wheel_platforms.py` measured with, and `WheelTarget.SPACE` takes its Python tag from
  `BUNDLED_PYTHON_VERSION` (`domain/model/SpaceConfig.kt`) so the interpreter version keeps one home.
- The trigger is pip's `(from versions: none)` line, read from the cell's stdout streams.
- The wording is `PackageReleases.platformRefusal` (`domain/model/PackageReleases.kt`), three
  shapes: the latest release ships wheels for this architecture and none fits, the latest ships no
  wheel for this architecture at all, and no release ships a wheel (source only). For duckdb, whose
  latest release's aarch64 wheels are `cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64`:

  > duckdb ships no musllinux aarch64 wheel for Python 3.12 in any release, and this Space's pip
  > installs wheels only, so pip cannot install it here. Latest release 2.0.1 ships
  > manylinux_2_26_aarch64, manylinux_2_28_aarch64 instead, built for glibc, which Android's
  > seccomp filter refuses to start.

  The glibc clause is said only when every tag named is a `manylinux` one; a wheel of another
  family is named without a reason attached, because the reason would not be true of it.

- The note is a `CellOutput.DisplayData` carrying `text/plain`, appended by the editor when the run
  ends. **It must not be a `stream`**: `List<CellOutput>.withOutputFolded`
  (`domain/model/StreamFold.kt`) folds a stream into the same-name stream before it, so the app's
  words would be saved and drawn as pip's. A `display_data` is nbformat's own shape for text an
  output area shows, so a save keeps it and JupyterLab draws it, and it stands as its own block.

## Consequences

- A person who runs `%pip install duckdb` is told why, and what the package ships instead, in the
  cell they ran — not in a dialog, and not only in a log.
- The app now contacts PyPI: one request, at the end of a run, only when pip says
  `(from versions: none)`. Offline, or for a name PyPI does not have, it says nothing at all — the
  cell reads exactly as it did before.
- The check is over **every** release, so a package pinned to an old version whose siblings do have
  a fitting wheel is never claimed: `platformRefusal` answers `null` as soon as any release fits.
- Bundling (B) is not forbidden: a package can still be added to the rootfs later without touching
  this, and the note would then never fire for it.
- The note arrives after the run's own checkpoint, so it is written by a second one. A notebook
  closed before PyPI answers keeps pip's text and no note.
- What a future reader will be tempted to "fix": make the note a `stream` "for symmetry with the
  kernel's output" — it must not be, because the fold would erase the line between pip's words and
  the app's; and drop the PyPI request by reading the claim off pip's text — it must not be, because
  pip's text for `duckdbzz` is the same two lines.
