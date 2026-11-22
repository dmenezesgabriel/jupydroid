# OPS-005 — The repository's public settings are finished

Milestone: `M9` · State: open · Depends on: — (the M10 checks the ruleset names shipped) · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

The repository's GitHub-side settings match what a public project needs: secret scanning and push
protection on, private vulnerability reporting on, `main` behind a required-checks ruleset — and the
release rootfs built on the native arm64 runner the public repository makes free, the qemu path gone.

## Demo

An OPS item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): no screen
changes, so the demo is a run anyone can repeat, not a Maestro script.

1. `gh api repos/dmenezesgabriel/jupydroid --jq '.security_and_analysis'` reports
   `secret_scanning.status` and `secret_scanning_push_protection.status` as `enabled`, and
   `gh api repos/dmenezesgabriel/jupydroid/secret-scanning/alerts` answers `200` with a list (it
   answered `404 "Secret scanning is disabled"` before).
2. `gh api repos/dmenezesgabriel/jupydroid/private-vulnerability-reporting --jq .enabled` reports
   `true`; the "Report a vulnerability" path `SECURITY.md` already describes is live.
3. `gh api repos/dmenezesgabriel/jupydroid/rulesets` lists `main required checks`, and
   `gh api repos/dmenezesgabriel/jupydroid/rulesets/<id>` shows the eight required contexts. A pull
   request then cannot merge while one of them is red or absent.
4. CodeQL and Scorecard need no new run: they flipped on the day the repository went public and are
   already proven — `gh api repos/dmenezesgabriel/jupydroid/code-scanning/alerts` lists open alerts,
   `gh api "repos/dmenezesgabriel/jupydroid/code-scanning/analyses?per_page=3"` shows CodeQL uploads,
   and the Scorecard schedule run of 2026-11-16 (`gh run view 43905823883`) is green.
5. The arm64 probe run (`gh run view <id>`) shows `tools/rootfs/build.sh arm64-v8a`,
   `tools/rootfs/sbom.sh arm64-v8a`, `tools/proot/build.sh` and `tools/rootfs/build-gnu.sh` all green
   on `ubuntu-24.04-arm`. The first tagged release after this lands is the end-to-end proof on the
   real path; the commit that ships this item says so rather than calling it proven, as
   [ADR-0138](../adr/0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md)
   did for its guarded jobs.

## Who else implements this

Repository settings are not in any clone: a reference's `.github/` shows its workflows, not its
hosted settings, so every reference reads ABSENT for want of evidence rather than for want of the
feature. The one prior implementation of the ruleset shape is the maintainer's own sibling
repository, read through the API in the session that wrote this PRD.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | — | settings are hosted state, not in the tree |
| `vscode@3f3380c668d0` | ABSENT | — | same |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | — | same |
| `intellij-community@406a27a8da07` | ABSENT | — | same |
| `termux-app@3df69d1da197` | ABSENT | — | same |
| `winlator@fb66541b93a4` | ABSENT | — | same |
| the shipped rootfs | ABSENT | — | not a repository with settings |
| `claude-acc-manager@5233d605` | PARTIAL | ruleset `24735780` via `gh api` | "main required checks": `required_status_checks` on `~DEFAULT_BRANCH`, `strict_required_status_checks_policy: false`, every PR-gating check named; its own security settings were not read as a pattern — this item sets ours from GitHub's docs |

**Count:** 0 of 7 references can show the settings; the sibling supplies the ruleset shape only. The
settings themselves are decided from GitHub's REST documentation and measured read-backs, not from a
reference.

## Implementation inventory

### GitHub REST API (docs.github.com, verified against the live repository 2026-11-22)

| Concern | Endpoint |
|---|---|
| Secret scanning + push protection | `PATCH /repos/{owner}/{repo}` with `security_and_analysis.secret_scanning.status` and `secret_scanning_push_protection.status` — **both in one payload**: a push-protection-only PATCH returns 200 and silently keeps it off while scanning is disabled (GitHubSecurityLab/gh-secure#21, measured on six repositories) |
| Private vulnerability reporting | `PUT /repos/{owner}/{repo}/private-vulnerability-reporting`; read back with `GET` on the same path (`.enabled`) |
| Required-checks ruleset | `POST /repos/{owner}/{repo}/rulesets`; read back with `GET /repos/{owner}/{repo}/rulesets` |

### `claude-acc-manager` ruleset 24735780 (the shape to copy)

`target: branch`, `enforcement: active`, `conditions.ref_name.include: ["~DEFAULT_BRANCH"]`, one rule
`required_status_checks` whose parameters are `do_not_enforce_on_create: false`,
`strict_required_status_checks_policy: false`, and a `context` per check that runs on `pull_request`
and gates the merge (`gate`, `test (3.11..3.13)`, `pip-audit`, `zizmor`, `gitleaks`, `codeql`, `docs`).

### Our own files the tasks touch

| Concern | Files |
|---|---|
| The checks the ruleset names | `.github/workflows/ci.yml` (`commit-messages`, `python-gate`, `gradle-gate`), `.github/workflows/security.yml` (`gitleaks`, `osv-scanner`, `opengrep`, `codeql (actions)`, `codeql (python)` on pull requests) |
| The job that moves to arm64 | `.github/workflows/release.yml` `rootfs` job: `runs-on`, the binfmt step, its comments, `timeout-minutes` |
| The gnu payload the job must also build | `tools/rootfs/build-gnu.sh`, `tools/rootfs/stage-app-artifacts.sh` (requires `gnu-arm64-v8a.tzst` for `arm64-v8a` since `946f72d82`) |
| The tests that hold the workflow to its rules | `scripts/release_rootfs_workflow_test.py` (asserts `runs-on: ubuntu-24.04` and the digest-pinned binfmt step today — the RED side of T4), `scripts/release_publish_workflow_test.py`, `scripts/workflow_reader.py` |
| The probe that proves arm64 before the next tag | `.github/workflows/arm64-probe.yml` — temporary, deleted at ship |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the settings are made | GitHub UI; REST API | The UI leaves no reproducible record; the API's read-back is itself the evidence | REST API via `gh api`, every flip verified by a `GET` in the same task's commit body |
| Protection mechanism | classic branch protection; a ruleset | The sibling uses a ruleset; rulesets are the current API and name checks by job `name:` | a `required_status_checks` ruleset on `~DEFAULT_BRANCH`, sibling's parameters verbatim |
| Which checks are required | every workflow job; only the pull-request gates | `codeql (java-kotlin)` never runs on a pull request, `scorecard` has no pull-request trigger, `grype`/`mobsf` skip on one, and the release/docs deploy jobs are not merge gates — requiring any of them would deadlock or mean nothing | the eight that run on `pull_request` and must pass: `commit-messages`, `python-gate`, `gradle-gate`, `gitleaks`, `osv-scanner`, `opengrep`, `codeql (actions)`, `codeql (python)` |
| `docs` in the ruleset | now; when `docs.yml` reaches `main` | `docs.yml` exists only on the unmerged site branch; requiring `docs` today would block every pull request branched off `main` | later: the ship commit adds a backlog row for adding `docs` and watching the first Pages `deploy` run once the file lands (decided with the maintainer) |
| Validity checks, Dependabot security updates | on; off | Neither is in the item's row, and the sibling has both off | off — minimum scope; revisit only if a need appears |
| arm64 runner | `ubuntu-24.04-arm`; stay on qemu | qemu measured the rootfs build at ~31 min; native arm64 is the free capability the public repository unlocks | `ubuntu-24.04-arm` for the `rootfs` job only — `apk`, `version`, `publish`, `attest` are arch-independent and stay on `ubuntu-24.04`; `grype` builds `linux/amd64` on purpose (its comment: same package set without the emulated build) |
| arm64 verification | `workflow_dispatch` of `release.yml`; a probe workflow | dispatch checks out the *tag's* tree, and no tag carries `tools/proot/build.sh` or `tools/rootfs/build-gnu.sh` — it would fail for reasons unrelated to arm64 and still reach `publish` on a lucky run | a temporary probe workflow on the working branch runs all four builds with no publish path at all |
| Probe lifetime | permanent `rootfs-build` workflow; temporary file | a permanent dispatch workflow is unrequested scope; the probe exists to produce one piece of evidence | temporary `.github/workflows/arm64-probe.yml`, deleted in the ship commit — its `on: push` `paths:` filter means the deletion push finds no workflow to run |

## Gap analysis — what we already have vs what the item needs

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| Secret scanning | `disabled` (`security_and_analysis`, measured 2026-11-22) | GitHub enables it for public repositories on request | absent — one PATCH away |
| Push protection | `disabled` | same | absent — same PATCH, same payload |
| Private vulnerability reporting | `enabled: false`; `SECURITY.md` already describes the flow | GitHub REST `PUT` | absent — one PUT away |
| `main` protection | no ruleset, no branch protection | sibling ruleset 24735780 | absent — one POST away |
| CodeQL, Scorecard | running and green since the repository went public | — | **present** — the visibility guard did its job; this item only cites the proof |
| Pages deploy | `docs.yml` `deploy` guarded on `visibility == 'public'`, file not yet on `main` | sibling `docs.yml` | present, awaiting the merge — no action here beyond the `docs` follow-up row |
| arm64 rootfs build | `ubuntu-24.04` + qemu binfmt, ~31 min measured | free `ubuntu-24.04-arm` on public repositories | absent — T4 |
| gnu payload in the release | `stage-app-artifacts.sh arm64-v8a` requires `gnu-arm64-v8a.tzst`; no workflow builds it | — | **latent defect**: the next release cut from post-merge `main` fails at the stage step — T4 adds the missing build and artifact path |

## Deviations

- No `domain`/`data`/`ui`/`di` surface: this item ships no port and no app capability, so there is no
  Maestro flow ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)).
- The settings live on GitHub, not in the tree, so T1–T3's commits carry the PRD's checkbox tick and
  the API read-back in the body — there is no file to change, and inventing one would be ceremony.
- The probe workflow is scaffolding for evidence, not a shipped capability; it is deleted at ship.
- `docs` joins the ruleset after `docs.yml` reaches `main` — a deliberate deferral, tracked by the
  backlog row the ship commit adds (decided with the maintainer, not an accident of timing).

## Open decisions

| Decision | Evidence to gather | Task |
|---|---|---|
| `rootfs` job `timeout-minutes` after the move | the probe run's step-summary timings on `ubuntu-24.04-arm` | T5 measures, T6 tunes if the 90-minute ceiling is now silly |
| The `docs`-in-ruleset follow-up's backlog id | whatever id is free when the ship commit lands (`OPS-018` exists on another branch) | T6 |

## Surface

| Layer | Files |
|---|---|
| repo settings | GitHub API only — no file |
| ruleset | GitHub API only — no file |
| workflow | `.github/workflows/release.yml` (T4); `.github/workflows/arm64-probe.yml` (T5, temporary) |
| tests | `scripts/release_rootfs_workflow_test.py` (T4, RED→GREEN); `scripts/release_publish_workflow_test.py` if the artifact contract moves |
| ADR | `docs/adr/0173-*.md` — the arm64 runner decision (T4) |
| docs | this PRD (deleted at ship); `docs/backlog.md` row (T6) |

## Tasks

- [x] T1 — Secret scanning and push protection on: one `PATCH` carrying both
  `security_and_analysis` statuses (the gh-secure#21 payload trap), then the `GET` read-back and a
  `200` from `/secret-scanning/alerts` as the commit-body evidence.
- [x] T2 — Private vulnerability reporting on: `PUT`, then `GET …/private-vulnerability-reporting`
  reporting `enabled: true`.
- [x] T3 — Ruleset `main required checks`: `POST` the sibling's shape with the eight pull-request
  contexts; `GET` read-back naming them. The commit body notes `docs` is deliberately absent until
  `docs.yml` reaches `main`.
- [x] T4 — arm64 runner, RED→GREEN: update `scripts/release_rootfs_workflow_test.py` to assert
  `runs-on: ubuntu-24.04-arm`, no binfmt step, and the gnu payload in the artifact; then edit
  `release.yml` — `runs-on`, binfmt step deleted, comments rewritten off qemu, a `build-gnu.sh` step
  added before the artifact upload, `gnu-arm64-v8a.tzst` and its manifest added to the upload paths
  (the `apk` job's `build/rootfs` download already carries them to `stage-app-artifacts.sh`). ADR-0173
  records why the runner moved and what the probe must still prove.
- [ ] T5 — Probe: add `.github/workflows/arm64-probe.yml` (`on: push` to the working branch, `paths:`
  covering itself and `tools/rootfs/**`, `tools/proot/**`; `ubuntu-24.04-arm`; the same four build
  steps, timed to the job summary, no publish path), push the branch so it runs, and read the run —
  every build green natively is the evidence; the measured times feed T6's timeout decision.
- [ ] T6 — Ship: delete the probe file and this PRD; `docs/backlog.md` row to `shipped`; add the
  `docs`-in-ruleset + first-Pages-deploy follow-up row; the commit body quotes every `gh api`
  read-back, the probe run id and its step times, and says the first tagged release is the
  end-to-end proof still owed.

## Out of scope

Adding `docs` to the ruleset and verifying the first Pages `deploy` run — owned by the follow-up row
T6 files, because `docs.yml` is not on `main` yet. Running a release on arm64 — the next tag's run
proves it; a `workflow_dispatch` rebuild was rejected (it checks out the tag's tree, which lacks
`tools/proot/build.sh` and `tools/rootfs/build-gnu.sh`, and it would re-publish a Release's assets for
no added proof). `secret_scanning_validity_checks` and `dependabot_security_updates` stay disabled.
`allow_auto_merge` and `delete_branch_on_merge` are not in the item's row. Moving `apk`, `version`,
`publish`, `attest` or the `grype`/`mobsf`/`codeql` jobs to arm64 — none of them needs it.
