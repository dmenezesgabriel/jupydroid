# ADR-0141 — Scanners read what ships: the built rootfs, the release APK, busybox, proot and the C shims

Status: accepted
Date: 2026-11-06

## Context

[ADR-0138](0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md) wired
the scanners and listed four known gaps. Before the first release was installed on the tablet, each shipped
surface was mapped to the scanner that reads it, with the pinned tools run on the tree. Five surfaces had no
reader:

| Surface | Why no scanner read it |
|---|---|
| The 51 apk packages added on top of the base (python3, git, libcurl, libssl3, pip) | `grype` scans the bare `FROM alpine:` image; an `apk add` happens after it |
| busybox 1.37.0 | built from a tarball, so it is in no apk database; grype read Alpine's package |
| proot-static 5.4.0 and its loader | staged into the APK's native libraries from an Alpine edge package, outside the rootfs |
| The three C shims preloaded into every Space process | opengrep ran `kotlin`, `python` and `bash` rules only; CodeQL has no `cpp` leg |
| The release APK | MobSF scanned `assembleDebug`, which adds `debuggable=true` and a debug signing key |

Measured on 2026-11-06 on the developer host:

| Scanner | Input | Result |
|---|---|---|
| grype 0.121.1 `--only-fixed --fail-on medium` | `sbom-arm64-v8a.json` from `tools/rootfs/sbom.sh` | 4 High in `libexpat` 2.9.0-r0, fixed in 2.10.0-r0; fails |
| grype 0.121.1 | `cpe:2.3:a:busybox:busybox:1.37.0` | 3 CVEs (1 Medium, 2 Low), none with a fix; passes |
| grype 0.121.1 | `cpe:2.3:a:proot:proot:5.4.0` | none |
| grype 0.121.1 | `purl` of `proot-static@5.4.0-r2` | none; Alpine's data has no proot entry, so the CPE is the useful lookup |
| opengrep 1.33.1, rules `c` + `dockerfile` | `tools/rootfs` | 10: 4 `insecure-use-memset` on structs that hold no secret, 1 `insecure-use-string-copy-fn` on a `strncpy` followed by an explicit terminator, 5 `set-pipefail` |
| MobSF 4.6.4 | unsigned `assembleRelease` APK | 2 high, 7 warning, 1 info; the debug-only highs are gone |

## Decision

- **The release workflow scans the rootfs it just catalogued.** `release.yml` runs the pinned grype over
  `sbom-arm64-v8a.json` with `--only-fixed --fail-on medium`, after the artifact upload. The `apk` job needs
  the `rootfs` job, so a fixable Medium-or-worse CVE in the shipped rootfs stops the release before it is signed.
  The pull-request and weekly runs cannot do this: the arm64 rootfs takes about 90 minutes under qemu.
- **busybox and proot are looked up by CPE** in the existing `grype` job, each version read at run time from
  the file that pins it (`tools/rootfs/Dockerfile`, `scripts/stage_proot.py`), same policy as the base.
- **opengrep gains the `c` and `dockerfile` rules.** It still reports and does not block: the findings above
  are design choices, and a scanner that always fails teaches the reader to ignore red.
- **MobSF scans `assembleRelease`.** The CI APK is unsigned, so "Missing Code Signing certificate" is expected
  and is not a finding.

## Consequences

**The next release fails until the rootfs moves off the vulnerable `libexpat`.** The fix is a newer Alpine base
digest, and 3.21 reached end of life on 2026-11-01 (grype prints
`259 packages from EOL distro "alpine 3.21.9"`), so the right move is a supported release, with a rootfs
rebuild and a `BUNDLED_ROOTFS_VERSION` bump. That is its own item.

**Not proven yet.** The `release.yml` step has not run: it runs after a 30-minute qemu build, on the next
release. The `security.yml` changes ran green on a `workflow_dispatch` of the branch that introduced them: both
CPE lookups printed `No vulnerabilities found`, opengrep listed the `c` and `dockerfile` findings, and MobSF
reported the same 2 high and 7 warning as the local run on the release APK.

**Named residuals.**
- Kotlin SAST has no blocking scanner while the repository is private. CodeQL's `java-kotlin` leg waits for a
  public repository (ADR-0138); until then MobSF's decompiled-code rules, Android Lint (debug variant) and
  opengrep's 14 Kotlin rule files report, and none blocks.
- CPE matching is NVD's, by upstream version. A proot or busybox flaw that NVD has not catalogued is not seen.
- JavaScript bundled inside rootfs wheels is read by no scanner: the SBOM lists 194 python, 51 apk and 14 binary
  artifacts and no JavaScript package, and osv-scanner reads PyPI names.
- Nothing scans the pull-request path for the rootfs, only the release path.
