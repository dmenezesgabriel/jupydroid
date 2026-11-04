# OPS-006 — CI runs the commit gate on every push and pull request

Milestone: `M10` · State: open · Depends on: — · Closes: —

## Outcome

A push or pull request that the commit gate would refuse shows a red check on GitHub, and one it would
accept shows a green one, without the author's machine having to be the only place that ran it.

## Demo

An OPS item (ADR-0122): no screen changes, so the demo is a run anyone can repeat, not a Maestro script.

1. `gh run list --workflow ci.yml --branch main --limit 1 --json conclusion,headSha` reports
   `success` for the commit at `git rev-parse HEAD`.
2. On a throwaway branch, push a Kotlin file ktlint refuses; `gh run list --branch <it>` reports
   `failure` and the failing job's log names ktlint. A gate that has only ever been seen green has
   not been shown to fail.
3. `pre-commit run actionlint zizmor --all-files` exits 0 on every file under `.github/workflows/`.
4. `gh pr list --app dependabot` lists a pull request for each ecosystem the config names, or the
   closing commit says which ecosystem was left out and why.

The repository stays private for now, so the run's minutes come from the free quota. The closing commit
quotes the minutes one run used (`gh run view <id> --json jobs`), since that number decides whether the
quota carries a month.

## Who else implements this

Read from the clones named in [`../README.md`](../README.md), in the session that wrote this PRD, by listing
`.github/workflows/` in each. An absence is evidence too.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e6906` | FULL | `.github/workflows/`, `.github/dependabot.yml` | many workflows, one per concern, plus CodeQL and a release check |
| `vscode@3f3380c6` | FULL | `.github/workflows/pr-linux-test.yml`, `.github/workflows/codeql.yml`, `.github/dependabot.yml` | a workflow per platform and per surface |
| `vscode-jupyter@f01d80ca` | FULL | `.github/workflows/build-test.yml`, `.github/workflows/codeql-analysis.yml`, `.github/dependabot.yml` | one build-and-test workflow, CodeQL beside it |
| `intellij-community@406a27a8` | PARTIAL | `.github/workflows/code_quality.yml`, `.github/workflows/Gradle_wrappers_validation.yml` | a quality workflow and a wrapper validation; its other workflows were listed, not read |
| `termux-app@3df69d1d` | FULL | `.github/workflows/run_tests.yml`, `.github/workflows/gradle-wrapper-validation.yml`, `.github/workflows/debug_build.yml`, `.github/dependabot.yml` | the closest Android analogue: JDK 17, `./gradlew test`, and a wrapper validation |
| `jupyterlab-git@b62a87b7` | FULL | `.github/workflows/build.yml` | one build workflow and release workflows |
| the shipped rootfs | ABSENT | — | not a repository |

**Count:** 6 of 7 run CI on Actions, and all six that do keep their workflows in `.github/workflows/` and
cover more than one concern in separate files. The Android one runs the unit tests and validates the
Gradle wrapper, and nothing else. That agrees with the backlog's M10 pattern, which is
`dmenezesgabriel/claude-acc-manager`'s workflows (`ci.yml`, `security.yml` and the rest), authoritative
here because it is the maintainer's own and already carries the SHA-pinning and `permissions: {}` habits
this item wants.

## Implementation inventory

### `claude-acc-manager@5233d605`

| Concern | Files |
|---|---|
| The merge gate runs the pre-commit hooks verbatim | `.github/workflows/ci.yml` (job `gate`) |
| Default-deny permissions, SHA-keyed concurrency | `.github/workflows/ci.yml` (top-level `permissions: {}`, `concurrency:`) |
| A shared setup step | `.github/actions/setup-env/action.yml` |
| actionlint's one false positive ignored | `.github/actionlint.yaml` |
| Dependabot with a 7-day cooldown | `.github/dependabot.yml` |
| zizmor and pip-audit as `uv run` tools | `.github/workflows/security.yml` |

### `termux-app@3df69d1d`

| Concern | Files |
|---|---|
| JDK 17 and Gradle on `ubuntu-latest` | `.github/workflows/run_tests.yml` |
| Gradle wrapper validation | `.github/workflows/gradle-wrapper-validation.yml` |

Where they disagree: `termux-app` pins nothing by SHA and runs `./gradlew test` alone, while
`claude-acc-manager` runs the whole hook set and pins every action. This repository's gate is the second
kind (AGENTS.md rule 7), so the second is the model.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| What CI runs | `termux-app` → `./gradlew test`; `claude-acc-manager` → `pre-commit run --all-files` | the first misses ruff, mypy, ShellCheck and every `scripts/check_*.py` gate | the hooks verbatim, so CI cannot pass what a commit cannot |
| Job shape | `claude-acc-manager` → one `gate` job | the Gradle hooks take far longer than the Python ones, so one job reports nothing until the slow half ends | two jobs, `python-gate` and `gradle-gate`, split with pre-commit's `SKIP`; named in T2 and T3 |
| Action pinning | `claude-acc-manager` → full SHA with a `# vX.Y.Z` comment; `termux-app` → tags | a tag can be moved | SHA, kept fresh by Dependabot |
| Linting workflows | `claude-acc-manager` → actionlint and zizmor | one more pair of tools to pin | both, as pre-commit hooks so a commit is refused before CI is |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| The gate | `.pre-commit-config.yaml` and the command in `AGENTS.md` | `claude-acc-manager@5233d605 .github/workflows/ci.yml` | complete locally, absent on GitHub |
| `pre-commit` itself | installed by hand (`AGENTS.md`); not in `pyproject.toml` | `claude-acc-manager@5233d605 .github/actions/setup-env/action.yml` | unpinned: ADR-0130 wants every Python tool locked and verified by sha256 |
| Workflow linting | none | `claude-acc-manager@5233d605 .github/actionlint.yaml` | absent |
| Dependabot | none; `.github/` holds only the issue forms and the PR template | `claude-acc-manager@5233d605 .github/dependabot.yml` | absent |

## Deviations

- Instrumented tests and Maestro stay local. AGENTS.md rule 7 calls them too slow for a per-commit gate,
  and the Maestro half needs the arm64 tablet, which no runner is.
- No required-check ruleset. Branch protection answers 403 on a private repository of this plan, so
  requiring these checks belongs to `OPS-005`, whenever the repository goes public.

## Open decisions

Each needs a measurement from T1, not a guess.

- **Does a fresh clone build?** The rootfs asset (`app/src/main/assets/spaces/`) and the `proot` pair are
  gitignored build artifacts. The README says an x86_64 build needs neither. Whether `assembleDebug`,
  the unit tests and `lintDebug` pass on a clone with neither is untested here. T1 clones, builds and times it.
- **Which JDK, which SDK platform.** The Kotlin toolchain is 17 (`app/build.gradle.kts`), `compileSdk` is 37,
  and AGENTS.md pins detekt to the alpha line because stable 1.23.8 cannot run on JDK 25, which implies the
  host builds on 25. T1 reads what the runner image has and what `setup-java` and
  `sdkmanager` must add.
- **How actionlint is installed.** zizmor is on PyPI from its own project, so it joins the `dev` group and
  `uv.lock` (ADR-0130). actionlint is a Go binary; a community repackaging on PyPI is excluded by the
  standing rule against obscure packages, so the choice is the upstream release binary checked against a
  sha256, or its published container image pinned by digest.
- **Dependabot against strict locks.** ADR-0123 locks Gradle strictly with verification metadata, which
  Dependabot cannot rewrite. Whether it opens Gradle pull requests that fail the gate by design, or
  watches Actions and `uv` only and leaves Gradle to the documented manual bump, is decided in T4 on what
  a trial pull request does. An ADR if the answer is "leave Gradle out".
- **Does the free quota carry it?** Private repositories get a monthly minutes allowance. T3's first green run
  reports the minutes; if a month of pushes would exceed it, the trigger narrows (pull requests and `main`
  only, not every branch) before the item closes.

## Surface

| Layer | Files |
|---|---|
| `.github/workflows/` | `ci.yml` |
| `.github/` | `actionlint.yaml` if its false positive reproduces, `dependabot.yml` |
| `pyproject.toml`, `uv.lock` | `pre-commit` and `zizmor` in the `dev` group |
| `.pre-commit-config.yaml` | `actionlint` and `zizmor` hooks over `.github/workflows/` |

No `domain/`, `data/`, `ui/` or `di/` file changes, so there is no port and no `Fake…` to add. The
workflow's own tests are actionlint and zizmor, plus the run that must fail once (Demo step 2).

## Tasks

- [ ] T1 — measure a fresh clone: build, unit tests and `lintDebug` without the rootfs asset or `proot`,
  with `devjob`; record the JDK, SDK platform and wall time in *Open decisions*
- [ ] T2 — `pre-commit` and `zizmor` into the `dev` group and `uv.lock`; the `actionlint` and `zizmor`
  hooks; `ci.yml` with `python-gate` (the Python and ShellCheck hooks, `SKIP`ing the Gradle ones). Red
  first: the workflow's first draft uses a tag, the hooks refuse it, then it is pinned
- [ ] T3 — `gradle-gate` in `ci.yml`: the Gradle hooks plus `assembleDebug`; record the minutes of one run
- [ ] T4 — `dependabot.yml` for the ecosystems T3's trial supports, with a 7-day cooldown
- [ ] T5 — close: Demo steps 1–4, an ADR only if a decision outlives the item (Dependabot against strict
  locks is the likely one), delete this PRD, edit the backlog row

## Out of scope

The required-check ruleset, secret scanning and going public (`OPS-005`); the security scanners
(`OPS-007`); the conventional-commit check (`OPS-008`); the release pipeline and the arm64 rootfs build
(`JD-066`); instrumented tests and Maestro in CI.
