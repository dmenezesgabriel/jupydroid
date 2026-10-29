# JD-064 — A Space's rootfs carries no advisory that has a fix

Milestone: `M8` · State: in-progress · Depends on: — · Closes: GAP-183, GAP-184

## Outcome

A Space's HTTPS, the kernel's `%pip install` and the loopback server all run on OpenSSL, zlib,
tornado, jupyter-server and certifi at versions that have no published advisory with a fix. Today
grype finds 7 Critical and 29 High matches in the image, and all but sqlite-libs's three have a
fixed version.

## Demo

This runs a cell, so it goes on the arm64 tablet (`SM-X216B`).

1. Cold start: install the APK built with the rebuilt `rootfs-arm64-v8a.tzst`, then launch. Base
   re-extracts, because `BUNDLED_ROOTFS_VERSION` was bumped.
2. Run base and open a notebook. One cell prints the `libcrypto3`, `libssl3` and `zlib` versions
   from `/lib/apk/db/installed`, and `importlib.metadata.version` for tornado, jupyter_server and
   certifi. Each one reads at or above its fixed version.
3. A second cell runs `%pip install tabulate`, which goes over HTTPS through this OpenSSL and its
   trust store. It prints `Successfully installed`.
4. Kill the app and relaunch it. The first cell prints the same versions.

The item is not `shipped` until this has run on the tablet, with screenshots, snapshots and
`maestro.log` under `evidence/jd-064-rootfs-advisories/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | It ships no userland. The Python environment belongs to the user |
| `vscode` | ABSENT | — | Same: it ships no interpreter or OS packages |
| `vscode-jupyter` | ABSENT | — | Same: it finds the user's interpreters |
| `intellij-community` | ABSENT | — | Same |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/build.gradle` (`downloadBootstraps`) | Its bootstrap userland is a prebuilt zip, pinned per arch by a version and a sha256, and refreshed by bumping that pin. Its packages come from termux-packages, and after install the user runs `pkg upgrade` |
| `winlator` | PARTIAL | `winlator@fb66541 installable_components/` | It ships prebuilt component archives inside the app. They are refreshed only when a new app release ships |
| the shipped rootfs | PARTIAL | `lib/apk/db/installed`, `usr/lib/python3.12/site-packages/*.dist-info` | `apk` and `pip` are present in the image, but nothing upgrades the base on its own |

**Count:** 3 of 7 ship a userland at all, and every one of them refreshes it the way we would: bump a
pinned input and rebuild. None scans its image. That is the gap AUD-012 measured.

## Implementation inventory

### termux-app@3df69d1

| Concern | Files |
|---|---|
| Pinned input | `app/build.gradle` (`downloadBootstrap(arch, expectedChecksum, version)`, `downloadBootstraps`) |
| Re-install on change | `app/src/main/java/com/termux/app/TermuxInstaller.java`, which installs only when the prefix is empty or absent (`TermuxFileUtils.isTermuxPrefixDirectoryEmpty`). There is no version stamp, so an update reaches only a fresh install |

### ours

| Concern | Files |
|---|---|
| Image recipe | `tools/rootfs/Dockerfile`: three stages, all `FROM alpine:3.21@sha256:48b0309c…`. The last one runs `apk add --no-cache python3 py3-pip git` |
| Python pins | `tools/rootfs/requirements.lock` (installed with `-r`), plus `jupyter-ai-tools==0.7.0` with `--no-deps` |
| Build | `tools/rootfs/build.sh arm64-v8a` (qemu under binfmt; `qemu-aarch64` is registered on this host). It writes `build/rootfs/rootfs-arm64-v8a.{tzst,manifest.txt}` |
| Staging into the APK | `tools/rootfs/stage-app-artifacts.sh` |
| Re-extract on device | `BaseSpaceInstaller.BUNDLED_ROOTFS_VERSION` (17), compared with `.jupydroid/rootfs-version` |
| Scan | the syft and grype commands in `evidence/aud-012-dependency-scans/README.md` (syft 1.55.0, grype 0.121.0, both pinned by digest) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How the Alpine packages move | bump the pinned base digest (the termux-style pin bump) · add `apk upgrade <pkg>` after `apk add` | A digest bump reproduces exactly, but it moves every package in the base. `apk upgrade` is narrow, but it does not reproduce, because Alpine's stable branch serves only the latest revision | **Digest bump.** Measured on 2026-10-29: the current `alpine:3.21` (`sha256:ee16d92f96db08d9d2d07da48555a27f3a69d8f0759021dbc601195ed0d2b5bd`) installs `libcrypto3`/`libssl3` 3.3.7-r2 and `zlib` 1.3.2-r1, the fixed revisions. The pinned digest installs -r0 for both, and `apk list -u` offers r2 and r1 as upgrades. Every stage moves together, so the builder and the shipped image share a libc |
| How the Python pins move | re-lock the whole file · bump the six named pins | A re-lock fails today: the lock pins `aiofile==3.12.3` and a dependency declares `aiofile<3.10` (AUD-012, `de8118dc`). A hand bump touches only what was named | **Bump the named pins**, tornado and jupyter-server first. pip's `-r` install resolves them against the rest of the lock, so a conflict fails the build and does not ship |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Fixed Alpine packages | the base digest from 2026-08-04 | present but stale |
| Fixed Python packages | `requirements.lock` | present but stale |
| A scan gate on the image | none. AUD-012 ran grype by hand | absent. It belongs to `OPS-007` (CI scanners), not to this item |

## Deviations

None.

## Open decisions

- sqlite-libs 3.48.0-r4 has three High matches with no fixed version. T1 reads its state again from
  the new image, and the item ships with it named if it is still unfixed.

## Surface

| Layer | Files |
|---|---|
| image | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.lock` |
| `data/space` | `BaseSpaceInstaller.BUNDLED_ROOTFS_VERSION` |
| evidence | `evidence/jd-064-rootfs-advisories/` (grype before and after, the manifest, the flow) |
| `.maestro/` flow | a base cell reading versions, and `%pip install`, modelled on `.maestro/jd-003-python-environments-in-a-space-t1-part2-base-pip-cell.yaml` |

## Tasks

- [ ] T1 — Alpine packages. Red: grype's fixable apk matches on the current image. Green: every
  stage's digest moves to the current `alpine:3.21`, the arm64 image is rebuilt, and syft+grype
  find 0 fixable apk matches. Bump `BUNDLED_ROOTFS_VERSION`.
- [ ] T2 — Python pins. Red: grype's fixable pypi matches. Green: bump the six pins, rebuild, and
  find 0 fixable pypi matches. The image's own build-time probes still pass.
- [ ] T3 — The demo flow on the tablet, with evidence. Then ship: delete this PRD, and edit the
  backlog row.

## Out of scope

- A scan gate that keeps the image clean is `OPS-007`.
- The MCP bearer's HTTP-layer refusal is `JD-071`. It needs a rebuild too, but it changes our code,
  not a pin.
