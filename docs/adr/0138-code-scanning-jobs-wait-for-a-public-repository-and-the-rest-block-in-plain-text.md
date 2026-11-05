# ADR-0138 — Code-scanning jobs wait for a public repository and the other scanners block in plain text

Status: accepted
Date: 2026-11-05

## Context

`.github/workflows/security.yml` runs six scanners beside `ci.yml` on a push to `main`, a pull request and a
weekly cron (a pull request runs only three of them): gitleaks, osv-scanner, opengrep, grype, MobSF and CodeQL.
`scorecard.yml` adds OpenSSF Scorecard as a seventh. Of the 8 references read at the pins in `docs/README.md`, 4 run some security automation and all four
stop at CodeQL, zizmor, Scorecard or dependency submission. None runs a secret scan, an advisory scan, a rootfs
scan or an APK scan, so the references settle the CodeQL and Scorecard shape only; the rest comes from each
tool's own documentation.

GitHub's SARIF upload feeds code scanning, which a private repository only has with a paid licence. This
repository is private, so a job that uploads SARIF fails on every run. Each scanner was run on the tree before
it was wired in, so that a gate nobody trusts, and everybody ignores, never entered the workflow
([ADR-0131](0131-python-quality-gates-are-kept-where-their-findings-are-real.md)).

Measured on 2026-11-05 on the developer host and then on 2 vCPU `ubuntu-24.04` runners:

| Scanner | Findings on this tree | Verdict |
|---|---|---|
| gitleaks 8.30.1 | 0 in history, 0 in tracked files (205 on an untracked checkout, all in clones and build output) | blocks |
| osv-scanner 2.7.0 | 59 advisories in one package, `pillow` 12.3.0, fixed by moving to 13.0.0 first | blocks |
| grype 0.121.1 over the pinned Alpine base | one CVE, `CVE-2025-60876` (busybox, Medium), no fix | blocks on `--only-fixed --fail-on medium` |
| opengrep 1.33.1 | 103, all a design choice or a repeat of what ShellCheck passes | reports |
| MobSF 4.6.4 | 3 high, 9 warning: debug signing key, `debuggable=true`, a cleartext-traffic config | reports |
| CodeQL | not run with its guard on a public repository | guarded |

## Decision drivers

- A scanner that always fails teaches the reader to ignore red.
- A scanner we cannot run yet should sit in the workflow, inert, and start on its own the day it can.
- Nothing that decides what ships is fetched by a floating name: a binary by version and the sha256 its project
  publishes, an image by digest, an action by SHA ([ADR-0123](0123-dependencies-are-locked-strict-and-verified-by-sha256.md)).

## Options considered

| Option | Pro | Con |
|---|---|---|
| Wait for the public repository, write everything then | no inert YAML | the first runs happen the day the repository goes public, on a workflow nobody has seen |
| Upload SARIF from every job now | alert history | fails on every run while the repository is private |
| Guard `codeql` and `scorecard` on `github.event.repository.visibility == 'public'`; run the rest with plain blocking output | the five runnable scanners protect now; the two guarded ones are written and linted | two jobs are unproven until the repository is public |

## Decision

The `codeql` and `scorecard` jobs carry `if: ${{ github.event.repository.visibility == 'public' }}` and are
reported as `skipped` while the repository is private; every other scanner runs now and writes plain output.
Only a scanner whose findings are real blocks: gitleaks, osv-scanner and grype. opengrep and MobSF publish their
output as an artifact (14 days) and exit 0, until a rule is chosen that has a real finding to catch.

`scorecard` has a file of its own, `scorecard.yml`, because Scorecard's results publishing refuses a workflow that
mixes in `run:` steps and `env`. It has no pull-request trigger.

CodeQL for Kotlin has no `build-mode: none` (GitHub: "Kotlin analysis requires a build"), so it runs
`./gradlew --no-daemon --no-build-cache assembleDebug` under the tracer. Python and Actions need no build and run
on every trigger; Kotlin runs on `main`, the cron and a dispatch.

## Consequences

**Not proven yet.** `codeql` and `scorecard` have never run with their guard open, so the SARIF upload, the
`category` per language, a pull request's two-leg matrix and everything in `scorecard.yml` are untested. The analysis
alone was measured on a throwaway branch with the guard dropped and the upload off: `actions` and `python` took
2 min 31 s each, `java-kotlin` 9 min 29 s, 16 billed minutes. The first real run belongs to the item that flips the
visibility. A reader who sees these two jobs always skipped will be tempted to delete them as dead; they are what
starts the day the repository goes public.

**Required checks bind to job names.** The matrix job is named `codeql (<language>)`, so a ruleset needs
`codeql (actions)`, `codeql (python)` and `codeql (java-kotlin)`; renaming a job means updating the ruleset in the
same commit.

**CodeQL's Kotlin build must bypass Gradle's cache.** A compile task served `FROM-CACHE` never runs, the tracer sees no
source, and `analyze` fails with "CodeQL detected code written in Java/Kotlin but this run didn't build any of it".
It also sees production code only: 634 of 1334 Kotlin files, the tracked `src/main` count, not the unit-test or
instrumented-test sources.

**Known gaps in what is scanned.**
- The source-built `/bin/busybox` (`tools/rootfs/Dockerfile`) is not an apk package, so grype reads Alpine's. The
  CVE it names applies to the image and no scanner sees the shipped binary until the rootfs is built on an arm64
  runner and scanned from an SBOM.
- Alpine 3.21 reached end of life on 2026-11-01. Grype prints `15 packages from EOL distro "alpine 3.21.x"`, so its
  data for the base goes stale and the grype gate weakens until the `FROM alpine:` pin moves to a supported release.
- The APK MobSF scans is the CI-built debug APK, 73 MB against 172 MB locally: the rootfs tarball and `libproot*.so`
  are gitignored build outputs. It covers the app's code and manifest, not the proot binaries or the rootfs.
- The two rootfs `.lock` files are requirements files, which osv-scanner skips without an error unless `-L` gives
  them the `requirements.txt:` prefix; without it the count falls from 1,645 packages to 1,463.

**Cost.** One push to `main` is billed 18 minutes for `Security` (mobsf 7, grype 2, gitleaks 1, osv-scanner 1,
opengrep 1) and 6 for `CI` (`python-gate` 3, `gradle-gate` 3, warm Gradle cache) while `codeql` is skipped, run
`42053066587` and `42053066650`. The estimate of 35-40 minutes made before measuring took `ci.yml` at its earlier 21 billed
minutes (17m51s for `gradle-gate`); `codeql` adds about 16 once public. A pull request runs gitleaks, osv-scanner and opengrep only.
