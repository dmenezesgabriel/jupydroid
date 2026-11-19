# ADR-0156 — The rootfs scan reads the image we build, because Alpine's base lags its repository

Status: accepted
Date: 2026-11-16

## Context

`grype` over the Alpine base exited 2 on `zlib 1.3.2-r1` (Critical, CVE-2026-4738, fixed in `1.4-r0`), and the base
was `alpine:3.21`, which has reached end of life. The plan was to move to a supported release whose base carries the
fix.

No published base does. Read on 2026-11-16 from the images themselves (`apk list -I zlib` inside each):

| Image | `zlib` in the image |
|---|---|
| `alpine:3.22` (3.22.8) | `1.3.2-r1` |
| `alpine:3.23` (3.23.8, digest `469c9ea4…`) | `1.3.2-r1`, with `1.4-r0` offered by `apk` |
| `alpine:3.24` (3.24.4) | `1.3.2-r1` |
| `alpine:edge` | `1.3.2-r0` |

`1.4-r0` is in `v3.23/main` (built 2026-11-06, read from its `APKINDEX`), so the repository has the fix and the images
do not. An `apk add` of a package the base already holds never upgrades it, so a Dockerfile that only moves its `FROM`
cannot ship the fixed library, and `grype registry:<base>` stays red whichever release it names.

| Reference | Has it | How a shipped userland meets an advisory the base lacks |
|---|---|---|
| `termux-app@3df69d1` | partly | the bootstrap is refreshed by moving its pin; the user runs `pkg upgrade` afterwards |
| `winlator@fb66541` | partly | component archives refreshed only with an app release |
| `jupyterlab`, `vscode`, `vscode-jupyter`, `intellij-community` | no | ship no userland |

No reference scans what it ships against a database, so there is nothing to adopt for the scan itself.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Wait for Alpine to republish the base | No change here | The Critical advisory stays open for as long as Alpine takes |
| Waive CVE-2026-4738 in `grype` | The job goes green | A waiver outlives its reason and hides the next advisory on `zlib` |
| A bare `apk upgrade` in the final stage | Takes whatever is current | Ships a different library on each build, and the build never fails when the fix moves |
| **Pin `zlib=1.4-r0` in the final stage's `apk add`, and scan the built image** | The shipped rootfs holds the fix; the build fails the day the repository drops that version; no advisory is waived | A pin to move by hand, and a scan that builds an image |

## Decision

All three stages start from `alpine:3.23@sha256:469c9ea4…`, the longest-supported release that keeps Python 3.12
(3.24 would move the lock's interpreter). The final stage runs `apk add --no-cache python3 py3-pip git zlib=1.4-r0`.
The Dockerfile's header says why the base alone does not carry the fix, and what to do when `1.4-r0` leaves the
repository: move the pin after reading the advisories it answers, and delete it once a base carries a fixed `zlib`.

The `grype` job builds `tools/rootfs/Dockerfile` for `linux/amd64` on the runner and scans that image with
`--only-fixed --fail-on medium`. It waives nothing, and `scripts/rootfs_scan_config_test.py` fails a `--ignore`,
`--vex`, `--config` or `.grype*` file. The scan of the bare base is gone, because it cannot pass.

The amd64 image stands in for the arm64 one for packages only. `requirements.lock` hashes cover both ABIs and `apk`
resolves one version per release, so the package set is the same; the emulated build `tools/rootfs/build.sh arm64-v8a`
needs is not added to a weekly run. It differs in the x86_64-only `musl_at_shim`, which holds no package.

A Dependabot pull request for the base is a signal only: it is not merged until the arm64 image is rebuilt from it and
the kernel answers a cell on the tablet.

## Consequences

Measured on this host (8 cores), with `grype` 0.121.1 and `osv-scanner` 2.7.0 at the pins in `security.yml`:

- Before: `grype registry:alpine:3.21@sha256:ee16d92f… --platform linux/arm64 --only-fixed --fail-on medium` exits 2 on
  `zlib 1.3.2-r1`, and the same command on the 3.23 digest exits 2 on the same row.
- After: the amd64 image built from this Dockerfile holds `zlib-1.4-r0`, `libcrypto3-3.5.9-r0` and `musl-1.2.5-r24`;
  `grype docker:<image> --only-fixed --fail-on medium` exits 0 with `No vulnerabilities found`, and `osv-scanner scan
  source` over both rootfs locks exits 0.
- The arm64 image `build.sh arm64-v8a` builds (Python 3.12.16, tarball sha256 `2fc2ae2d…`) passes every probe in the
  Dockerfile under qemu, and `grype docker:<image> --platform linux/arm64 --only-fixed --fail-on medium` on it exits 0
  with `No vulnerabilities found`. Run apart from the build, the SIGSYS reporter prints `sigsys reporter armed` on the
  arm64 image, `at_shim_probe.py` prints `at-shim armed` on the amd64 one, and the arm64 image holds no
  `libmusl_at_shim.so`.
- The cold amd64 build, every probe included, took 140 s. The job's timeout moves from 10 to 20 minutes because a hosted
  runner has fewer cores and the scan fetches a database.

The weekly run now fails when the image's own packages gain a fixable advisory, which the bare base never showed for
what `apk add` installs. A fix that Alpine ships only in a newer repository version also fails the build at the pin
instead of passing unnoticed.
