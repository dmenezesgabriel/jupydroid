# ADR-0173 — The release rootfs builds on the native arm64 runner

Status: accepted
Date: 2026-11-22

## Context

[ADR-0140](0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md) built the arm64
rootfs under qemu on `ubuntu-24.04` because the repository was private on the free plan, which has no
hosted arm64 runner: every `RUN` of the Dockerfile executed emulated, measured at 31 min 06 s for
`build.sh` alone. The repository is public now, and public repositories get `ubuntu-24.04-arm` at no
cost — the constraint that chose qemu no longer exists.

The same job also gained a defect while the site work was unmerged:
`tools/rootfs/stage-app-artifacts.sh arm64-v8a` has required
`build/rootfs/gnu-arm64-v8a.tzst` since `946f72d82`, and no workflow built it — the next release cut
from `main` would have failed at the staging step.

## Decision drivers

- The emulated build cost ~31 minutes of every release for no benefit the platform still requires.
- Every build script already says `docker build --platform linux/arm64`; on an arm64 host that is the
  native path, so the scripts need no change — only the runner label and the qemu scaffolding do.
- The pinned `anchore/syft` digest is an OCI index with an `arm64 linux` manifest (checked against the
  registry 2026-11-22), so `sbom.sh` resolves the right image unchanged.
- `security.yml`'s `grype` job keeps building `linux/amd64` on purpose — same package set without an
  emulated build — and is untouched.

## Decision

The `rootfs` job of `.github/workflows/release.yml` runs on `ubuntu-24.04-arm`. The binfmt
registration step is deleted; `tools/rootfs/build.sh`, `tools/rootfs/sbom.sh`,
`tools/proot/build.sh` and the newly added `tools/rootfs/build-gnu.sh` run natively. The gnu payload's
tarball and manifest join the `rootfs-arm64-v8a` artifact, which the `apk` job already downloads whole
into `build/rootfs/` for `stage-app-artifacts.sh`. `version`, `apk`, `publish` and `attest` stay on
`ubuntu-24.04`: they are architecture-independent.

`timeout-minutes` stays 90 until the probe reports the native time; the job summary carries each
step's elapsed seconds for exactly that read.

## Consequences

- **Verified before the next tag, not after.** A `workflow_dispatch` rebuild cannot prove this: it
  checks out the tag's tree, and no tag carries `tools/proot/build.sh` or `tools/rootfs/build-gnu.sh`,
  so it would fail for reasons unrelated to the runner — and on a lucky run it would re-publish a
  Release's assets for no added proof. A temporary probe workflow runs the same four builds on
  `ubuntu-24.04-arm` with no publish path; the first tagged release remains the end-to-end proof on
  the real path, as ADR-0138's guarded jobs were proven by their first real run.
- **If the label were ever unavailable**, the revert is one line — `runs-on` back to `ubuntu-24.04`
  plus the binfmt step — which is why the probe ran before the change shipped rather than after.
- The gnu build step cites `Fixes: 946f72d82360` at the point it applies, per the comment rule.
