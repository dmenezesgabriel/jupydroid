# OPS-018 — Scanners read what ships

Milestone: `M8` · State: in-progress · Depends on: — · Closes: —

> Delete this file when the item ships; its residue is
> [ADR-0141](../adr/0141-scanners-read-what-ships-the-built-rootfs-the-release-apk-busybox-proot-and-the-c-shims.md)
> and the comments in the two workflows ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A fixable Medium-or-worse CVE in the rootfs, busybox or proot stops a release before it is signed, and the
scanners read the C shims, the image recipe and the release APK, not only the sources they already covered.

## Demo

No device flow ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)). It closes on:

1. `workflow_dispatch` of `security.yml` on a branch: `grype` (with the CPE step), `opengrep` (with `c` and
   `dockerfile`) and `mobsf` (release APK) all complete.
2. The pinned grype over `build/rootfs/sbom-arm64-v8a.json` exits 2 on the 4 High `libexpat` CVEs.
3. The next release run reaches the `Scan the rootfs SBOM` step.

## Tasks

- [x] T1 — measure each input with the pinned tool before wiring it (ADR-0141's table)
- [x] T2 — `security.yml`: CPE step for busybox and proot, `c` and `dockerfile` rules, MobSF on `assembleRelease`
- [x] T3 — `release.yml`: grype over the SBOM before the `apk` job
- [x] T4 — actionlint and zizmor pass on both workflows
- [x] T5 — dispatch `security.yml` on a branch and read the three changed jobs
- [ ] T6 — the next release run reaches `Scan the rootfs SBOM`; then delete this PRD

Out of scope, and its own item: moving the Alpine base to a supported release (rootfs rebuild and version bump).
