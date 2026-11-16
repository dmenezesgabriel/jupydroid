# JD-090 — A Space's rootfs carries no advisory at medium or above that has a fix

Milestone: `M8` · State: in-progress · Depends on: — · Closes: GAP-206

## Outcome

`grype` over the Alpine base and `osv-scanner` over the two rootfs locks both exit 0 on `main`, and the image
built from the new base still starts the kernel and answers a cell on the tablet. Today `grype` exits 2 on
`zlib 1.3.2-r1` (Critical, CVE-2026-4738, fixed in `1.4-r0`) and `osv-scanner` exits 1 on seven advisories in
four packages.

## Demo

This runs a cell, so it goes on the arm64 tablet (`SM-X216B`).

1. Cold start: install the APK built with the rebuilt `rootfs-arm64-v8a.tzst`, then launch. Base re-extracts,
   because `BUNDLED_ROOTFS_VERSION` was bumped.
2. Run base and open a notebook. One cell prints `zlib`'s version from `/lib/apk/db/installed` and
   `importlib.metadata.version` for `dnspython`, `opentelemetry-api`, `soupsieve` and `tornado`. Each reads at or
   above its fixed version (`1.4-r0`, `2.9.2`, `1.45.2`, `4.0.0`, `6.6.1`).
3. A second cell runs `%pip install tabulate`, over HTTPS through this image's OpenSSL.
4. A third cell runs numpy, so the interposers and the SIGSYS handler are exercised, not only the interpreter.
5. Kill the app and relaunch it. The first cell prints the same versions.

Not `shipped` until this has run on the tablet, with its screenshots, dumps and `maestro.log` kept in the
local, uncommitted evidence directory (ADR-0132).

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | Ships no userland; the Python environment is the user's |
| `vscode` | ABSENT | — | Ships no interpreter or OS packages |
| `vscode-jupyter` | ABSENT | — | Finds the user's interpreters |
| `intellij-community` | ABSENT | — | Same |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/build.gradle` (`downloadBootstrap`) | A prebuilt bootstrap pinned per arch by version and sha256, refreshed by bumping the pin; the user runs `pkg upgrade` afterwards |
| `winlator` | PARTIAL | `winlator@fb66541 installable_components/` | Prebuilt component archives inside the app, refreshed only with an app release |
| the shipped rootfs | PARTIAL | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.lock` | A pinned base digest and a hashed lock; nothing moves either by itself |

**Count:** 3 of 7 ship a userland, and each refreshes it by moving a pinned input and rebuilding. None offers a
signal that a newer input exists; ours must, and GAP-206 asks that this repair say how.

## Implementation inventory

### ours

| Concern | Files |
|---|---|
| Base | `tools/rootfs/Dockerfile`: three stages (`sigsys-builder`, `busybox-builder`, final), each `FROM alpine:3.21@sha256:ee16d92f…`; the digest note at the top |
| Python pins | `tools/rootfs/requirements.lock` (header holds the `uv pip compile` command, `--python-version 3.12`, `aarch64-unknown-linux-musl`), `requirements.in` |
| Probes the build runs | `getaddrinfo_shim_probe.py`, `at_shim_probe.py`, `acp_*_probe.py`, `mcp_*_probe.py`, the kernel entry-point and `pylsp --version` checks, the busybox checks |
| Build | `tools/rootfs/build.sh arm64-v8a` (needs a registered `qemu-aarch64` binfmt handler; none is registered now) |
| Listing | `tools/rootfs/sbom.sh` regenerates `THIRD_PARTY.md`, which `scripts/render_third_party_listing_test.py` reads |
| Re-extract on device | `BaseSpaceInstaller.BUNDLED_ROOTFS_VERSION` (20), ADR-0125 |
| The scans | `.github/workflows/security.yml`: `osv-scanner` 2.7.0 and `grype` 0.121.1, each pinned by sha256 |
| Dependency signal | `.github/dependabot.yml`: `github-actions`, `uv`, `npm`, `gradle`; no `docker` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Which Alpine release | 3.22 (EOL 2027-05-01) · 3.23 (2027-11-01) · 3.24 (2028-06-01) | All three repositories carry `zlib 1.4-r0`, **none of their base images does** (ADR-0156). 3.22 and 3.23 carry Python 3.12.16; 3.24 carries 3.14.10, which would move the lock's Python, `BUNDLED_PYTHON_VERSION` and three Dockerfile paths | **3.23**: supported longest of the two that keep Python 3.12. Read from Alpine's own package and release pages before the ADR cites it, and probe it on the tablet before shipping. A probe failing on 3.23 stops the item for a decision; it does not fall back to 3.22 unasked |
| How the four wheels move | re-lock everything · `--upgrade-package` for the four | A bare re-lock moves every pin; the four-package form changes only what the advisories name (plus what the resolver must move with them) | **`--upgrade-package` x4**, then read the lock's diff. `soupsieve` has no 2.x fix (the 2.x line ends at 2.9.2), so it moves to 4.x; `beautifulsoup4 4.15.0` needs `soupsieve>=1.6.1` |
| How the next advisory reaches us | a `docker` entry in `dependabot.yml` · the weekly `grype` cron alone | The cron finds a vulnerable base but proposes nothing; Dependabot proposes a digest. CI cannot build the image, so a PR is never proof | **Both.** A `docker` entry for `/tools/rootfs`, weekly with the 7-day cooldown the others carry. The ADR says such a PR is a signal only: it is not merged until the image is rebuilt from it and the kernel answers a cell on the tablet. The cron stays the check |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Fixed `zlib` | the 2026-10-29 digest of `alpine:3.21` | present but stale; 3.21 is EOL |
| Fixed wheels | `requirements.lock` | present but stale |
| Signal for a new base | the weekly cron | present and partial: nothing proposes the fix |

## Deviations

- T3 found that no published Alpine base carries `zlib 1.4-r0`, so moving the base cannot make `grype` over the
  base green. Decided by the user: `zlib=1.4-r0` pinned exactly in the final stage's `apk add`, the `grype` job
  builds the Dockerfile for amd64 and scans that image, and nothing is waived (ADR-0156).
- `BUNDLED_ROOTFS_VERSION` was bumped without a red test of its own: re-extraction on a newer version is already
  held by `BaseSpaceInstallerTest.install_reinstallsWhenTheBundledVersionIsNewer`, and a test of `== 21` would
  assert an arbitrary number. The commit before it that bumped the stamp (`9b11cec4`) did the same.

## Open decisions

None. The busybox build, the interposers and the `acp`/`mcp` patches build and run on 3.23: the arm64 image built
with every probe passing under qemu.

## Surface

| Layer | Files |
|---|---|
| image | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.lock` |
| `data/space` | `BaseSpaceInstaller.BUNDLED_ROOTFS_VERSION` |
| delivery | `.github/dependabot.yml` |
| listing | `THIRD_PARTY.md` via `tools/rootfs/sbom.sh` |
| unit tests | `BaseSpaceInstallerTest` for the version; a script test for the `docker` entry |
| `.maestro/` flow | a base cell reading the versions and `%pip install`, modelled on the flow `ed36dd64` added |

## Tasks

- [x] T1 — The wheels. Red: `osv-scanner` 2.7.0 over the two rootfs locks exits 1 with seven advisories.
  Green: the lock recompiled with `--upgrade-package` for the four, its diff read, the same command exits 0.
- [x] T2 — The signal. Red: a test that `dependabot.yml` names a `docker` entry for `/tools/rootfs` with the
  weekly schedule and cooldown the other ecosystems carry. Green: the entry.
- [x] T3 — The base. Red: `grype registry:<current digest> --platform linux/arm64 --only-fixed --fail-on medium`
  exits 2. Green: all three `FROM`s on `alpine:3.23` at one digest, the same command exits 0, the arm64 image
  builds with every probe in the Dockerfile passing, and the SIGSYS and `at_shim` probes are run apart. Bump
  `BUNDLED_ROOTFS_VERSION`, red test first, and regenerate `THIRD_PARTY.md`.
- [ ] T4 — The demo on the tablet, with evidence, then ship: the ADR, delete this PRD, edit the backlog row and
  GAP-206.

## Out of scope

- The Python 3.14 migration that 3.24 would need.
- A scan of the source-built busybox, which is not an apk package (ADR-0131).
