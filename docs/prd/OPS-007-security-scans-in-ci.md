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
3. `pre-commit run actionlint zizmor --all-files` exits 0 on `.github/workflows/security.yml`.
4. `gh run view <id> --json jobs` shows `codeql` and `scorecard` as `skipped` while the repository is private. Their
   first real run belongs to `OPS-005`, and the closing commit says so rather than calling them proven.

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
  below picks a cheaper input or defers the scan.
- No scanner joins the pre-commit gate: they need the network and minutes, and the commit gate is milliseconds to
  minutes of local, offline work.

## Open decisions

Each needs a measurement from T1, not a guess.

- **(a) What grype reads.** Candidates: the Alpine base image by its pinned digest for `linux/arm64`
  (`tools/rootfs/Dockerfile`); a syft SBOM of a rootfs built in CI under emulation; or waiting for `JD-066`.
  The base alone misses `python3 py3-pip git` and the Python wheels, which `requirements.lock` and
  `jupyter-ai-tools.lock` cover through osv-scanner.
- **(b) What blocks.** Run every scanner on the tree and triage each finding as real or false before choosing, and
  record the allowlist entries with the reason for each.
- **(c) MobSF, headless.** How the container starts, how its API key is set, and the upload, scan and report calls
  (its documentation page did not render for a fetch this session, so read it in T1). Whether the APK comes from
  `gradle-gate` as an uploaded artifact or from a rebuild, and what a debug APK says that a release one would not.
- **(d) opengrep rules.** Which rule set, and from whom. The engine is the opengrep project's; rules pulled from a
  community registry are third-party content and follow the standing rule against obscure packages.
- **(e) CodeQL for Kotlin.** It needs the Gradle build, so the job costs about an `assembleDebug` (3 minutes on
  `gradle-gate`, plus CodeQL's tracing) on top of `gradle-gate`. Whether that fits the free minutes, or whether
  CodeQL analyses Python and Actions on every run and Kotlin only weekly.
- **(f) gitleaks scope.** A scan of the whole tree and of full history after the `OPS-002` rewrite; whether a
  `.gitleaks.toml` allowlist is needed at all, and how it sits beside `scripts/check_device_secrets.py`.
- **(g) Minutes.** The total per run against the 2,000 free Linux minutes, and whether pull-request runs narrow
  (scanners that read only the diff) before the item closes.

## Surface

| Layer | Files |
|---|---|
| `.github/workflows/` | `security.yml` |
| `.github/` | `.gitleaks.toml` at the root if (f) needs it; `dependabot.yml` untouched |
| `scripts/` | a MobSF API driver with its `_test.py`, only if (c) needs one |
| `.pre-commit-config.yaml` | unchanged: no scanner joins the commit gate |

No `domain/`, `data/`, `ui/` or `di/` file changes, so there is no port and no `Fake…` to add. Each job's own test
is the run that must fail once (Demo step 2); any script this item writes is TDD like the rest.

## Tasks

- [ ] T1 — measure each scanner on the tree with `devjob`: pinned version and sha256 or digest, findings triaged as
  real or false, wall time and peak memory; settle (a)–(g) in *Open decisions*
- [ ] T2 — `security.yml` skeleton (`permissions: {}`, SHA-keyed concurrency, push, pull request, weekly cron) and the
  `gitleaks` job; red first: a planted token-shaped string turns it red
- [ ] T3 — `osv-scanner` over `uv.lock`, `tools/rootfs/*.lock` and the Gradle lockfiles
- [ ] T4 — `opengrep` over the Kotlin and Python sources
- [ ] T5 — `grype` over the input T1 chose for (a)
- [ ] T6 — `mobsf` static scan of the debug APK, with a tested driver script if (c) needs one
- [ ] T7 — the guarded jobs: `codeql` (Python, Actions, and Kotlin per (e)), `scorecard`, and the SARIF upload
- [ ] T8 — close: Demo steps 1–4, an ADR for what outlives the item (the visibility guard; blocking output in place of
  SARIF), delete this PRD, edit the backlog row to `shipped`, and move `← next` to `OPS-008`

## Out of scope

Going public, required checks and branch protection (`OPS-005`); the conventional-commit check (`OPS-008`); the
release pipeline and the arm64 rootfs build (`JD-066`); fixing what a scanner finds in the app itself, which becomes
a `JD-NNN` or `DEF-NNN` of its own.
