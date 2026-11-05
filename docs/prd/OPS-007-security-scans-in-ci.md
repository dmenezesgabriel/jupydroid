# OPS-007 — Security scans run in CI, free and keyless

Milestone: `M10` · State: in-progress · Depends on: `OPS-006` (shipped) · Closes: —

## Outcome

A push, a pull request or the weekly run that brings in a committed secret, a known-vulnerable dependency, an
insecure code pattern or a finding in the built APK shows a red check on GitHub. The scanners that need a public
repository sit in the workflow now, inert, and start on their own the day `OPS-005` flips the visibility.

## Demo

An OPS item (ADR-0122): no screen changes, so the demo is a run anyone can repeat, not a Maestro script.

1. `gh run list --workflow security.yml --branch main --limit 1 --json conclusion,headSha` reports `success`
   for the commit at `git rev-parse HEAD`.
2. For each scanner that blocks, a throwaway branch carries one planted finding (a token-shaped string, a
   dependency pinned to a version with a published advisory, a pattern the rule set names) and
   `gh run list --branch <it>` reports `failure` with the failing job's log naming that scanner. A gate that has
   only ever been seen green has not been shown to fail.
3. `pre-commit run actionlint-docker --all-files` and `pre-commit run zizmor --all-files` exit 0 over
   `.github/workflows/security.yml` and `.github/workflows/scorecard.yml`.
4. `gh run view <id> --json jobs` shows `codeql` as `skipped` in a `security.yml` run and `scorecard` as `skipped` in a
   `scorecard.yml` run while the repository is private. Their first real run belongs to `OPS-005`, and the closing
   commit says so rather than calling them proven.

The repository stays private, so the minutes come from the free quota. The closing commit quotes the minutes one
`security.yml` run used (`gh run view <id> --json jobs`), added to the 21 that `ci.yml` uses
([ADR-0137](../adr/0137-dependabot-updates-actions-and-uv-and-only-secures-gradle.md)).

## Who else implements this

Read from the clones named in [`../README.md`](../README.md) by listing `.github/workflows/` in each, in the session
that wrote this PRD. An absence is evidence too.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e6906` | PARTIAL | `.github/workflows/codeql-analysis.yml`, `.github/workflows/zizmor.yml`, `.github/openssf-scorecard.yml` | CodeQL, zizmor and Scorecard as their own workflows; no secret, dependency or APK scan |
| `vscode@3f3380c6` | PARTIAL | `.github/workflows/codeql.yml`, `.github/codeql/codeql-config.yml` | CodeQL with `build-mode: none` for the languages that allow it |
| `vscode-jupyter@f01d80ca` | PARTIAL | `.github/workflows/codeql-analysis.yml` | the stock CodeQL template |
| `intellij-community@406a27a8` | ABSENT | — | `.github/workflows/` holds `code_quality.yml` and build workflows; none scans for security |
| `termux-app@3df69d1d` | PARTIAL | `.github/workflows/dependency-submission.yml`, `.github/workflows/gradle-wrapper-validation.yml` | the closest Android analogue: it submits its Gradle graph to GitHub and validates the wrapper, and runs no scanner |
| `winlator@fb66541b` | ABSENT | — | no `.github/` directory |
| `jupyterlab-git@b62a87b7` | ABSENT | — | build and release workflows only |
| the shipped rootfs | ABSENT | — | not a repository |

**Count:** 4 of 8 run some security automation, and all four stop at CodeQL, zizmor, Scorecard or dependency
submission. None runs a secret scan, a dependency-advisory scan, a rootfs scan or an APK scan, and none runs
opengrep or MobSF. The references are therefore evidence for the CodeQL and Scorecard shape only; the rest is
decided from the tools' own documentation and the maintainer's own repository.

`dmenezesgabriel/claude-acc-manager@5233d605` is authoritative for the workflow shape, as for `OPS-006`
(it is the maintainer's own and carries the SHA-pinning and `permissions: {}` habits this repository wants).

## Implementation inventory

### `claude-acc-manager@5233d605`

| Concern | Files |
|---|---|
| Audit battery in a file of its own, with a weekly cron for external drift | `.github/workflows/security.yml` |
| gitleaks over full history, `fetch-depth: 0`, `GITHUB_TOKEN` only to authenticate the action's owner lookup | `.github/workflows/security.yml` (job `gitleaks`) |
| CodeQL behind `if: github.event.repository.visibility == 'public'`, because SARIF upload needs code scanning | `.github/workflows/security.yml` (job `codeql`) |
| Blocking plain-text run in place of SARIF, "upload needs GHAS" | `.github/workflows/security.yml` (job `zizmor`) |
| Scorecard on push to `main` and weekly, never on pull requests, same visibility guard | `.github/workflows/scorecard.yml` |
| gitleaks allowlist for known non-secrets, anchored on the extracted secret | `.gitleaks.toml` |

### `vscode@3f3380c6`

| Concern | Files |
|---|---|
| CodeQL language matrix with an explicit `build-mode` per language | `.github/workflows/codeql.yml` |
| CodeQL query and path configuration | `.github/codeql/codeql-config.yml` |

Where they disagree: `vscode` analyses compiled languages with `build-mode: none`, which GitHub's documentation
says Kotlin does not support, since it needs a build. CodeQL for this repository's Kotlin therefore runs a Gradle
build, or Kotlin goes unanalysed. That is decision (e) below.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Private repository | `claude-acc-manager` → guard the public-only jobs and run the rest blocking | SARIF alerts need a Code Security licence on a private repository | the maintainer's choice, asked this session: write CodeQL, Scorecard and SARIF upload now behind the visibility guard; run the other scanners now with plain blocking output |
| Where the jobs live | `claude-acc-manager` → `security.yml` beside `ci.yml` | a file of its own costs a second set of boilerplate | `security.yml`: a weekly cron exists for drift that no code change triggers, and `ci.yml` stays one concern |
| Output | `claude-acc-manager` → blocking plain text; `jupyterlab` → SARIF to code scanning | plain output has no alert history | blocking now; the SARIF step is added in the guarded jobs only |
| Tool provenance | `claude-acc-manager` → SHA-pinned actions; `actionlint` in `OPS-006` → upstream image by tag | a floating tool changes what a run means | actions by SHA; a binary by release plus the sha256 its project publishes; an image by digest (ADR-0123's rule for anything that decides what ships). No community repackaging on PyPI |
| What blocks | `claude-acc-manager` → every finding blocks, with an allowlist for known non-secrets | a scanner that always fails teaches the reader to ignore it | decided per scanner in T1 from the findings the tree really has, and kept blocking only where they are real ([ADR-0131](../adr/0131-python-quality-gates-are-kept-where-their-findings-are-real.md)) |

Anything here that outlives the item becomes an ADR before the PRD is deleted (T8).

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Secret scan in CI | `scripts/check_device_secrets.py` reads staged files for the literals of one house, not for secrets in general | `claude-acc-manager@5233d605 .github/workflows/security.yml` (`gitleaks`) | absent |
| Dependency advisories | `.github/dependabot.yml` proposes bumps; Gradle is security-updates-only (ADR-0137) and the rootfs locks are not listed | `claude-acc-manager@5233d605 .github/workflows/security.yml` (`pip-audit`) | absent for the Gradle, `uv` and rootfs lockfiles |
| Workflow audit | zizmor and actionlint hooks, run by `python-gate` (`.pre-commit-config.yaml`) | `jupyterlab@7f0e6906 .github/workflows/zizmor.yml` | complete |
| Static analysis for security | detekt, ruff (`pyproject.toml`); neither is a security rule set | `vscode@3f3380c6 .github/workflows/codeql.yml` | absent |
| Rootfs vulnerabilities | `tools/rootfs/sbom.sh` makes an SBOM from a locally built rootfs, for `THIRD_PARTY.md` | none of the references | present but unreachable from CI: the tarball is a gitignored build artifact |
| APK scan | none | none of the references | absent |
| Scorecard | none | `jupyterlab@7f0e6906 .github/openssf-scorecard.yml` | absent |

## Deviations

- No SARIF upload and no code-scanning alerts while the repository is private. Plain blocking output stands in.
- Scorecard has no pull-request trigger: it runs on `main` and weekly, as `claude-acc-manager` does.
- Rootfs scanning cannot use the syft SBOM in CI before `JD-066` builds the rootfs on an arm64 runner. Decision (a)
  below defers the scan and reads the Alpine base instead.
- No scanner joins the pre-commit gate: they need the network and minutes, and the commit gate is milliseconds to
  minutes of local, offline work.

## Measurements

Taken on 2026-11-05 on the developer host (8 cores, 26 GB free), each through `devjob` under `/usr/bin/time -v`, from a
checkout at `3a8e02f2`. Raw output is local and never committed (ADR-0132). Binaries were downloaded to
`build/scan-tools/` and checked against the sums their projects publish; the wall time of a tool is not the minutes of
a job, which also pays for checkout and setup (measured in T2-T7).

| Scanner | Pinned by | Input | Wall | Peak RSS | Findings | Triage |
|---|---|---|---|---|---|---|
| gitleaks | v8.30.1, sha256 `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb` (`gitleaks_8.30.1_linux_x64.tar.gz`) | `gitleaks git .`, 1,530 commits | 1.8 s | 126 MB | 0 | clean |
| gitleaks | same | `gitleaks dir .`, 2.15 GB incl. untracked | 83 s | 174 MB | 205 | all false: 199 under `reference/` clones, 6 under `extension-api/build/`; none in a tracked file |
| osv-scanner | v2.7.0, sha256 `160c6253e2922e9e0a6a9771c5c4c40a4b3c6090b34a3cdd5e80c96cc2e4218b` (`osv-scanner_linux_amd64`) | `uv.lock`, both `tools/rootfs/*.lock`, five non-empty `gradle.lockfile`s: 1,645 packages | 2.8 s | 74 MB | 59 advisories, one package | real: `pillow` 12.3.0 in the `host` group, fixed in 13.0.0 |
| opengrep | v1.33.1, sha256 `46e8133cc490f9f50518061590ce2f6be5a08afaeda034471c0ceda4b3899808` (`opengrep_manylinux_x86`; upstream publishes a cosign `.sig`/`.cert`, no sum file) with `opengrep/opengrep-rules@f1d2b562b414783763fd02a6ed2736eaed622efa` | `kotlin/`, `python/`, `bash/` rules over `app/src`, the three module `src/`, `scripts`, `tools` (1,428 files) | 22 s | 371 MB | 103 (85 bash INFO, 18 other); 23 partial-parse errors | 0 real, below |
| grype | v0.121.1, sha256 `bc3ea3f9e6fd56ac781c08c6c5e614271b79f438200757f6932085c19b0a4657`, DB schema v6.1.10 built 2026-11-05T06:21Z | `alpine:3.21@sha256:ee16d92f…` (`Dockerfile:84`), `--platform linux/arm64` | 5.8 s (+ 74 s DB download) | 224 MB | 3 matches, one CVE (`CVE-2025-60876`, Medium, no fix) | real in substance, unfixable today, below |
| MobSF | v4.6.4, image `opensecurity/mobile-security-framework-mobsf@sha256:cf1465a3cc0c95488b9a6cdb1c7799d4b576d391ee28232c221f6a196bf2f74c` (2.49 GB on disk, 31 s pull) | `app-debug.apk` (181 MB), static | 332 s from `docker run` to report (58 s ready, 272 s scan) | 12.3 GiB (`docker stats`, container) | 3 high, 9 warnings, 1 info (the report's `appsec` block, one entry per rule) | mostly debug-build artifacts, below |

**Triage notes.**

- *opengrep.* The 18 non-bash hits: `LoopbackPort.kt:19` binds `127.0.0.1` by design; the four `dangerous-subprocess`
  hits pass a fixed argv list and no shell; the five `use-defused-xml` hits parse `uiautomator` dumps pulled from our
  own device; the rest are `open()` without an encoding and a `sleep` in a probe. The 85 bash hits repeat what the
  ShellCheck hook already passes.
- *grype.* The shipped `/bin/busybox` is built from source (`Dockerfile:59`), the same 1.37.0 the CVE names, so the
  finding applies to the image. Grype cannot see that binary, since it is not an apk package; it sees Alpine's.
  `--only-fixed --fail-on medium` exits 0.
- *MobSF.* The three highs are the debug signing certificate, `android:debuggable=true` and a cleartext-traffic domain
  config (whose source was not traced). The exported `PreviewActivity` and the `allowBackup` warning come with a debug
  build; a release APK would not carry the debug ones. The four "secrets" are an RFC 6455 handshake GUID,
  a sha256 constant and two non-secrets.

**First runs on GitHub (T2).** `gitleaks` on the runner, 2026-11-05: the green run on `ops-007-t2` (run
`42022695242`) took 14 s of job time, which GitHub bills as 1 minute; checkout, the checksum-verified download and both
scans are inside it. The red run on a throwaway pull request carrying a `ghp_`-shaped fake token (run `42022594502`,
closed and its branch deleted) failed at the step `Scan every commit` with `leaks found: 1`, and skipped the second scan.

**First runs on GitHub (T3).** Pillow went to 13.0.0 first, in its own commit (`uv lock --upgrade-package pillow`; the
`uv.lock` diff moves only `pillow`). `osv-scanner` on the runner, 2026-11-05: the green run on pull request #13 (run
`42024835496`) took 8 s of job time, billed as 1 minute, and printed `No issues found` over the same 1,645 packages T1
counted. The red run on a throwaway pull request carrying `requests==2.19.0` in `tools/rootfs/requirements.lock` (run
`42024712526`, closed and its branch deleted) failed at `Scan the lockfiles`, naming `requests` 2.19.0 and 5
advisories (`PYSEC-2018-28` among them). The two rootfs `.lock` files need the `requirements.txt:` prefix on `-L`:
without it the scanner skips them without an error and counts 1,463 packages, not 1,645.

**First runs on GitHub (T4).** `opengrep` on the runner, 2026-11-05: the run on pull request #14 (run `42026708831`)
took 52 s of job time, billed as 1 minute, and printed `103 Code Findings`, the count T1 measured locally over the same
six paths. The binary has no `--metrics` flag, so none is passed; `--quiet` hides the partial-parse count T1 saw (23),
so the runner's is not recorded. The job does not block, so it has no red run: it exits 0 on findings, and its text
and SARIF output are the `opengrep-findings` artifact (75,614 bytes, kept 14 days).

**First runs on GitHub (T5).** `grype` on the runner, 2026-11-05: the manual dispatch on `ops-007-t5` (run
`42028107133`) took 64 s of job time, billed as 2 minutes, and printed `No vulnerabilities found` for the digest
`Dockerfile` names, the result T1 measured locally; the whole run (four jobs) is 5 billed minutes. The red run
dispatched on a throwaway branch with the final `FROM alpine:` pinned to `alpine:3.21.0`
(`sha256:21dc6063…`; run `42028608429`, branch deleted) failed at `Scan the Alpine base` on `libcrypto3` and
`libssl3` (CVE-2025-15467, Critical, fixed in 3.3.6-r0, among others). `alpine:3.15` was tried first and passed:
grype has no data for a distro that old, so a planted finding needs a release the database still covers.
**Both runs print `15 packages from EOL distro "alpine 3.21.x"`**: Alpine 3.21 reached end of life on 2026-11-01, so
the data for it is going stale and this gate weakens until the base moves to a supported release (a `JD-NNN` of its
own; the `Dockerfile` pin is not touched here).

**First runs on GitHub (T6).** `mobsf` on a private-repository runner (2 vCPU, 8 GB per GitHub's hosted-runner
reference), 2026-11-05: the manual dispatch on `ops-007-t6` (run `42037684071`) took 6 min 12 s of job time, billed as
7 minutes (the build 52 s, the scan step 4 min 49 s), and uploaded the `mobsf-findings` artifact. The summary is the one
the local run printed, 3 high / 9 warning / 1 info, and the `code_analysis`, `manifest_analysis`, `network_security` and
`appsec` sections match the stored local report entry for entry. The job does not block, so it has no red run.
Under `--memory=6g --memory-swap=6g` on the dev host the same scan also finished (298 s) but the kernel killed jadx's
`java` at 5.9 GB of anonymous memory (`journalctl -k`, "Memory cgroup out of memory"), so the 12.3 GiB `docker stats`
peak is real growth, not page cache. That the runner's job went green is the evidence it fits; the runner's kernel log
is not readable, so a silent jadx kill there cannot be ruled out beyond the matching sections, and this is one run.
**The CI-built APK is 73 MB, the local one 172 MB:** the rootfs tarball and `libproot*.so` are gitignored build
outputs a fresh checkout lacks, so `binary_analysis` has 8 entries on the runner against 10 locally and the scan does not
read the proot binaries or the rootfs. It covers the app's code and manifest, which is what `gradle-gate` builds as well.

**First runs on GitHub (T7).** `codeql` has never run with its guard on a public repository, so what follows is the
analysis alone, measured on a throwaway branch with the visibility guard dropped, `upload: never` and the other five
jobs off; the branch is deleted. The first dispatch (run `42042274485`) passed `actions` and `python` and **failed the
`java-kotlin` leg** at `analyze` with "CodeQL detected code written in Java/Kotlin but this run didn't build any of
it": `setup-gradle` had restored Gradle's build cache, `:extension-api:compileDebugKotlin` was `FROM-CACHE`, and no
compiler ran under the tracer. The step became `./gradlew --no-daemon --no-build-cache assembleDebug` (`Fixes:
2d51f3ac`) and the second dispatch (run `42043820482`, 2026-11-05) went green: `actions` 2 min 31 s and `python`
2 min 31 s of job time, `java-kotlin` 9 min 29 s (the build `BUILD SUCCESSFUL in 5m 7s`, no task `FROM-CACHE`), billed
as 3 + 3 + 10 = 16 minutes. Every Kotlin compile task ran, and the log reads "CodeQL scanned 634 out of 1334 Kotlin
files": 634 is the tracked `src/main` count, so **`assembleDebug` analyses production code only**, not the 496 unit-test
and 197 instrumented-test files. One run, one commit's cache state (the branch run reads the cache and never saves it);
a `main` run, which also saves, is not measured. Not proven: the SARIF upload, the `category` per language, a pull
request's two-leg matrix, and everything in `scorecard.yml`, which needs a public repository.

## Decisions

- **(a) What grype reads:** the Alpine base by its pinned digest, `linux/arm64`, blocking with `--only-fixed
  --fail-on medium`. An unfixed CVE is reported and does not block, since nothing can be done about it. The scan of
  the full rootfs waits for `JD-066`; building the image under emulation to get a syft SBOM was not measured and is
  not attempted here. What the base misses (Python wheels) is covered by osv-scanner on the two rootfs locks, and the
  source-built busybox is covered by no scanner until the rootfs is scanned.
- **(b) What blocks:** gitleaks, osv-scanner and grype block. opengrep and MobSF do not: their findings on this tree
  are all false or debug-only, and a scanner that always fails teaches the reader to ignore it
  ([ADR-0131](../adr/0131-python-quality-gates-are-kept-where-their-findings-are-real.md)). They run, publish their
  output, and block on nothing until a rule is chosen that has a real finding to catch. Demo step 2 therefore plants
  a finding for the three that block only.
- **(c) MobSF:** a plain `docker run` with `MOBSF_API_KEY` in the environment, then `POST /api/v1/upload`,
  `POST /api/v1/scan` and `POST /api/v1/report_json`, authenticated by the `Authorization` header (read from
  `MobSF/Mobile-Security-Framework-MobSF@9e15fccef0dc3f8843ca94664300771c8faf1d48`, `mobsf/MobSF/init.py`,
  `views/api/api_middleware.py`, `urls.py`; its docs site does not render). `scripts/mobsf_scan.py` with a `_test.py`
  drives those calls. The job builds its own APK with `assembleDebug` rather than fetching `gradle-gate`'s: the two are
  separate workflows, and handing an artifact across them needs a run lookup and an `actions: read` scope for a
  52-second build. It runs on `main`, the weekly cron and dispatch, and joins no guard: the private runner it was
  measured on is the one it runs on (see *First runs on GitHub (T6)*).
- **(d) opengrep rules:** the project's own `opengrep-rules` at the pinned SHA above. It is a mirror of `semgrep-rules`
  under LGPL-2.1 with a Commons Clause that bars selling it, not scanning with it; the repository is **archived**
  (last push 2025-11-28), so the rules will not move. Fetched by SHA in the job, never vendored. The registry
  `--config auto` is not used: it is third-party content fetched at run time.
- **(e) CodeQL for Kotlin:** GitHub's documentation says "Kotlin analysis requires a build" and that `build-mode: none`
  skips Kotlin with a warning, so Kotlin needs `build-mode: manual` around `assembleDebug`. Python and Actions need no
  build and run on every trigger; Kotlin runs on pushes to `main`, the weekly cron and a dispatch. The build must
  bypass Gradle's cache (`--no-build-cache`), and it covers production code only. Both were measured in T7 on the
  private repository (*First runs on GitHub (T7)*); the upload waits for `OPS-005`.
- **(f) gitleaks scope:** `gitleaks git` over full history plus `gitleaks dir` over the checkout, which in CI holds
  tracked files only. Both are clean, so no `.gitleaks.toml` is written; one appears with the first real false
  positive, anchored on the extracted secret. It sits beside `scripts/check_device_secrets.py` without overlap: that
  gate reads for one house's literals before a commit, this one for token shapes after a push. Run the pinned binary
  directly rather than `gitleaks-action`, whose owner lookup needs a token and whose binary is not pinned by sha256.
- **(g) Minutes:** the tools alone add up to about 8 minutes (gitleaks 2 s, osv 3 s, opengrep 22 s, grype 80 s,
  MobSF 332 s plus a 31 s pull). A job also pays for checkout and setup, unmeasured until T2-T7. With `ci.yml`'s 21
  minutes, a push to `main` costs on the order of 35-40 minutes, so the 2,000 free minutes allow about 50 such
  pushes a month, before the weekly run. Pull requests therefore run gitleaks, osv-scanner and opengrep only; grype
  (its input changes with `Dockerfile` alone) and MobSF run on `main` and the weekly cron. The total is re-measured
  from `gh run view <id> --json jobs` in T8.
- **Precondition for T3.** osv-scanner blocks, and `pillow` 12.3.0 fails it on 59 advisories. `pyproject.toml` caps
  it at `<13`, and Pillow 13.0.0 exists. Bumping it is its own commit before T3 (`uv lock --upgrade-package pillow`,
  then the Python gate), since a job that is red the day it lands is not a gate. The `read_*` image measures that
  import Pillow are the check that 13 still serves them.

## Surface

| Layer | Files |
|---|---|
| `.github/workflows/` | `security.yml`; `scorecard.yml`, a file of its own because Scorecard's results publishing refuses a workflow that mixes in `run:` steps and `env` |
| `.github/` | `dependabot.yml` untouched; no `.gitleaks.toml` (decision f) |
| `scripts/` | `mobsf_scan.py` with its `_test.py` (decision c); the Pillow bump touches `pyproject.toml` and `uv.lock` |
| `.pre-commit-config.yaml` | unchanged: no scanner joins the commit gate |

No `domain/`, `data/`, `ui/` or `di/` file changes, so there is no port and no `Fake…` to add. Each job's own test
is the run that must fail once (Demo step 2); any script this item writes is TDD like the rest.

## Tasks

- [x] T1 — measure each scanner on the tree with `devjob`: pinned version and sha256 or digest, findings triaged as
  real or false, wall time and peak memory; settle (a)–(g) in *Decisions*
- [x] T2 — `security.yml` skeleton (`permissions: {}`, SHA-keyed concurrency, push, pull request, weekly cron) and the
  `gitleaks` job; red first: a planted token-shaped string turns it red
- [x] T3 — `osv-scanner` over `uv.lock`, `tools/rootfs/*.lock` and the Gradle lockfiles
- [x] T4 — `opengrep` over the Kotlin and Python sources
- [x] T5 — `grype` over the input T1 chose for (a)
- [x] T6 — `mobsf` static scan of the debug APK, with a tested driver script if (c) needs one
- [x] T7 — the guarded jobs: `codeql` (Python, Actions, and Kotlin per (e)), `scorecard`, and the SARIF upload
- [ ] T8 — close: Demo steps 1–4, an ADR for what outlives the item (the visibility guard; blocking output in place of
  SARIF), delete this PRD, edit the backlog row to `shipped`, and move `← next` to `OPS-008`

## Out of scope

Going public, required checks and branch protection (`OPS-005`); the conventional-commit check (`OPS-008`); the
release pipeline and the arm64 rootfs build (`JD-066`); fixing what a scanner finds in the app itself, which becomes
a `JD-NNN` or `DEF-NNN` of its own.
