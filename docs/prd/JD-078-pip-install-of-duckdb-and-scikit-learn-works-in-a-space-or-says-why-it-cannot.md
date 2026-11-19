# JD-078 — `%pip install` of duckdb and scikit-learn works in a Space, or says why it cannot

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-196`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A user who runs `%pip install duckdb` or `%pip install scikit-learn` in a Space either gets a package that imports,
or is told in the cell's own output why this Space's platform cannot have it. Today pip answers that it found
no matching distribution, which reads as a typo.

## Demo

Everything here runs a cell, so the device is the arm64 tablet `SM-X216B` (AGENTS.md rule 2); on `dev36`
`FakeKernelGateway` echoes the source back and a green flow proves nothing about the kernel.

1. Cold start: install, launch, open a notebook in a Space.
2. `%pip install pandas` then `import pandas` in the next cell: the control, which has a musllinux wheel.
3. `%pip install duckdb` and `%pip install scikit-learn`, each in its own cell: whatever the chosen option
   says happens (an import that works, or a message that names the platform and the reason).
4. Kill and relaunch: the outcome is the same.

Not `shipped` until this has run on the tablet, with the frames looked at.

## Who else implements this

Not a capability a reference ships. The references are editors and a terminal, and none of them runs pip on
musl. A grep for `musllinux`, `scikit-learn` and `duckdb` over the pinned clones of `jupyterlab`,
`vscode-jupyter`, `termux-app` and `winlator` found nothing on 2026-11-19; `vscode` and `intellij-community`
were not searched.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | the kernel's own pip answers; no platform message |
| `vscode` | ABSENT | — | not searched: an editor with no Python host of its own |
| `vscode-jupyter` | ABSENT | — | a static `!pip` lint (`GAP-034`), no platform reasoning |
| `intellij-community` | ABSENT | — | not read for this: its Python package manager is a desktop one |
| `termux-app` | ABSENT | — | an app shell; its packages come from `termux-packages`, which is not a pinned reference |
| `winlator` | ABSENT | — | not a Python host |
| the shipped rootfs | PARTIAL | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.in` | Alpine 3.23 on musl, `--only-binary=:all:`, no C toolchain, so only a musllinux wheel installs |

**Count:** 0 of 7 implement a message for it; the problem is the platform's, which `ADR-0003` chose. What
decides this item is measured on PyPI and on Alpine's repository, not read from a reference.

## Measured (2026-11-19)

| Question | Answer |
|---|---|
| cp312 `musllinux_*_aarch64` wheels, any release of duckdb | 0 of 168 releases |
| the same, scikit-learn | 0 of 76 |
| the same, for contrast: pandas / numpy / scipy | 21 of 121 / 38 of 153 / 16 of 108 |
| what the two do ship for aarch64 | manylinux only: duckdb `cp312-abi3-manylinux_2_26_aarch64`, scikit-learn `cp312-cp312-manylinux_2_27_aarch64` |
| can a manylinux wheel load here | no: glibc's startup is killed by Android's seccomp filter (`ADR-0003`) |
| Alpine v3.23 aarch64, scikit-learn | `py3-scikit-learn` 1.5.2-r0 in `community`: 8.5 MB, 27 MB installed, depends on `py3-numpy`, `py3-scipy`, `py3-joblib`, `py3-threadpoolctl`, libgomp, libstdc++ |
| Alpine v3.23 aarch64, duckdb | absent; `duckdb` 1.5.6 is in `edge` `community` (18 MB) and `py3-duckdb` 1.5.6 in `edge` `testing` (21 MB) |
| a source build | not an exit: the image carries no C toolchain on purpose (`tools/rootfs/Dockerfile`), and a Space's pip runs with `PIP_ONLY_BINARY=:all:` (`SpaceRuntime.kt`), so a package with only an sdist is refused too |
| the 100 most downloaded packages (30 days, `top-pypi-packages` as of 2026-11-01), latest release has no installable wheel | 0 of 100 |
| the 1000 most downloaded, same test | 71 of 1000: ranks 101-300 → 8, 301-600 → 19, 601-1000 → 44; 39 once the 32 CUDA, `torch`, `triton` and `pywin32` entries (no use on an Android arm64 Space) are set aside |
| the 1000 most downloaded, no release at all installable | 48 of 1000 |
| in the 39, by rank | `scikit-learn` 155, `matplotlib` 172, `duckdb` 366, `pymongo` 362, `numba` 387, `psycopg2` 390, `statsmodels` 570, then `opencv-python`, `xgboost`, `lightgbm`, `onnx`, `pymupdf`, `playwright`, `watchdog`, `lz4`, `pyspark`, `ray`, `mysqlclient`, `docopt`, `h3` and others |

An older release does not help: the count is over every release.

The popular-package counts reproduce with the unchanged T1 script over the list's first N names:

```sh
curl -sSL https://hugovk.dev/top-pypi-packages/top-pypi-packages.min.json -o top.json   # a new, empty directory
python3 -I -c "import json,sys; print(' '.join(r['project'] for r in json.load(open(sys.argv[1]))['rows'][:1000]))" top.json > top1000.txt
python3 scripts/read_wheel_platforms.py $(cat top1000.txt) > out.txt   # about 3 minutes, one PyPI request per package
grep -c 'ships instead' out.txt                          # 71: the latest release has no wheel pip would pick
```

The dataset moves monthly and PyPI daily, so the numbers carry their date.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where a missing wheel is handled | **A** explain: a message in the cell's output when pip finds no distribution and the package has wheels for no musllinux platform. **B** bundle the apk package in the base rootfs (sklearn only). **C** a wheelhouse built in CI from source | A costs no bytes and is true for every package in the same case; B adds about 27 MB installed plus numpy and scipy, at a version (1.5.2) years behind PyPI's; C needs a C toolchain stage, hours of build for duckdb, and somewhere to host the wheels | open; decided at T3 on T1 and T2's numbers, and recorded as an ADR |

## Gap analysis — what we already have

| Concern | Ours today | Gap |
|---|---|---|
| a package with a musllinux aarch64 wheel installs | works (`ADR-0011` user site; pandas measured) | none |
| a package with none | pip's own "No matching distribution found", shown as the cell's output | the message names no platform and no reason; **present and partial** |
| which packages are in this case | `GAP-196` names two; 71 of the 1000 most downloaded are in it, none of the first 100 | `matplotlib` is in it too, so bundling one package (B) would leave the case standing |

## Deviations

None yet.

## Open decisions

1. Which of A, B and C, or A for duckdb and B for scikit-learn. The evidence above lets A be chosen without a
   device; B and C need an APK-size and build-time number first.
2. Where an explanation is produced: in the rootfs (a wrapper around pip's error) or in the app (reading the
   cell's stderr). `docs/architecture/building-blocks.md` says which side owns a kernel's text; read it at T3.

## Surface

Decided at T3.

## Tasks

- [x] T1 — `scripts/read_wheel_platforms.py` reproduces the PyPI count per package from a command, with a test
- [x] T2a — the other popular packages in the same case, counted over the 1000 most downloaded with the T1 script
- [ ] T2b — pip's own message on the tablet. **Blocked until the tablet is attached to the host:** `adb devices`
  and `adb mdns services` listed no device on 2026-11-19, and `dev36` has no rootfs, so `%pip` cannot run there
- [ ] T3 — the option chosen on T1 and T2, as an ADR, then implemented red → green. **Waits behind T2b:** the
  explanation hangs off the text pip prints on the device, and open decision 2 (where it is produced) depends
  on that text. The evidence above already supports A, so T2b narrows the wording and the place, not the option
- [ ] T4 — the demo on the tablet; backlog row shipped; `GAP-196` closed; this file deleted. **Blocked** on the
  tablet like T2b

## Out of scope

`GAP-195`'s `%pip` then `import` failure (`JD-077`). A package that has a musllinux wheel and still fails.
