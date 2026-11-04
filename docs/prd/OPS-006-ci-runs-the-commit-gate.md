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
| Job shape | `claude-acc-manager` → one `gate` job | the Gradle hooks take far longer than the Python ones, so one job reports nothing until the slow half ends | two jobs, `python-gate` and `gradle-gate`, split with pre-commit's `SKIP`; named in T3 and T4 |
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

- **Does a fresh clone build?** No, not on a cold Gradle cache, and the rootfs asset and `proot` are not why.
  Measured in T1 on 2026-11-04 at `a36ca0e4`, a `git clone` of the committed tree (no `spaces/`, no `jniLibs/`,
  no `local.properties`, no `reference/`) with the Gradle half of the commit gate and `assembleDebug`:
  - Strict verification stops the build with an empty `GRADLE_USER_HOME`: `gradle/verification-metadata.xml`
    has no checksums for `bc-jdk18on-bom-1.80.2.module` and `guava-parent-33.7.2-jre.pom` (the `classpath`
    configuration, at configuration time) and `groovy-bom-4.0.29.pom` (`:app:androidLintTool`). Gradle's report
    says "Checksums are missing from verification metadata". These are the only three: a lenient run logs no
    others. The host's populated cache passes the same file, which is why the commit gate never saw them.
  - `--write-verification-metadata sha256` on a home that had already downloaded them changed nothing, so
    ADR-0123's procedure on a warm home does not record them. Whether an empty home does is unmeasured; T2 settles it.
  - With `--dependency-verification=lenient` every task passes without the rootfs asset or `proot`: ktlint, detekt,
    `:app:cpd`, the four `lintDebug`s, the four unit-test tasks, `compileDebugAndroidTestKotlin` and `assembleDebug`.
  - Wall time on this 8-core host at load 2: 8m52s with an empty Gradle home (222 tasks executed, 22 from the
    build cache; the home ends at 2.4 GB); 29s with a fresh checkout on that populated home (108 executed, 136 from
    the build cache, which `gradle.properties` turns on). A private-repository runner has 2 vCPU and 8 GB, so the
    cold figure there is longer; T4 measures it.
- **Which JDK, which SDK platform.** Gradle ran on the Android Studio JBR 25.0.2; the Kotlin toolchain is 17 and
  the foojay resolver downloaded Temurin 17.0.21_9, since the host has no system JDK 17. The wrapper fetched Gradle
  9.6.1. No SDK component was downloaded: the host's `platforms/android-37.0` and `build-tools/36.0.0` sufficed.
  The `ubuntu-24.04` runner image (its README, read in T1) has JDK 17.0.21+9 as the default plus 21 and
  25.0.5+8 (`JAVA_HOME_25_X64`), `android-37.0` (rev 2) and `build-tools` 36.0.0 under
  `/usr/local/lib/android/sdk`, and lists no `uv`, with Python 3.14.9 only in the tool cache. So the gate needs
  `setup-java` for 25 as the Gradle JVM, because the gate is proven on 25 and not on the runner's default 17, and
  needs nothing from `sdkmanager`. Whether Gradle finds the runner's JDK 17 for the toolchain or downloads one
  is for T4 to read from its first run.
- **How actionlint is installed.** zizmor is on PyPI from its own project, so it joins the `dev` group and
  `uv.lock` (ADR-0130). actionlint is a Go binary; a community repackaging on PyPI is excluded by the
  standing rule against obscure packages, so the choice is the upstream release binary checked against a
  sha256, or its published container image pinned by digest.
- **Dependabot against strict locks.** ADR-0123 locks Gradle strictly with verification metadata, which
  Dependabot cannot rewrite. Whether it opens Gradle pull requests that fail the gate by design, or
  watches Actions and `uv` only and leaves Gradle to the documented manual bump, is decided in T5 on what
  a trial pull request does. An ADR if the answer is "leave Gradle out".
- **Does the free quota carry it?** Private repositories get a monthly minutes allowance. T4's first green run
  reports the minutes; if a month of pushes would exceed it, the trigger narrows (pull requests and `main`
  only, not every branch) before the item closes.

## Surface

| Layer | Files |
|---|---|
| `.github/workflows/` | `ci.yml` |
| `.github/` | `actionlint.yaml` if its false positive reproduces, `dependabot.yml` |
| `pyproject.toml`, `uv.lock` | `pre-commit` and `zizmor` in the `dev` group |
| `.pre-commit-config.yaml` | `actionlint` and `zizmor` hooks over `.github/workflows/` |
| `gradle/verification-metadata.xml`, `AGENTS.md` | the three missing checksums; the lock-bump note if it needs the empty home |

No `domain/`, `data/`, `ui/` or `di/` file changes, so there is no port and no `Fake…` to add. The
workflow's own tests are actionlint and zizmor, plus the run that must fail once (Demo step 2).

## Tasks

- [x] T1 — measure a fresh clone: build, unit tests and `lintDebug` without the rootfs asset or `proot`,
  with `devjob`; record the JDK, SDK platform and wall time in *Open decisions*
- [ ] T2 — repair `gradle/verification-metadata.xml`. Red: in a fresh clone with an empty `GRADLE_USER_HOME`,
  the gate's Gradle tasks fail naming the three artifacts above. Green: they pass once the three are recorded by
  ADR-0123's write procedure run against an empty home. If only an empty home records them, AGENTS.md's
  lock-bump note says so
- [ ] T3 — `pre-commit` and `zizmor` into the `dev` group and `uv.lock`; the `actionlint` and `zizmor`
  hooks; `ci.yml` with `python-gate` (the Python and ShellCheck hooks, `SKIP`ing the Gradle ones). Red
  first: the workflow's first draft uses a tag, the hooks refuse it, then it is pinned
- [ ] T4 — `gradle-gate` in `ci.yml`: the Gradle hooks plus `assembleDebug`; record the minutes of one run
- [ ] T5 — `dependabot.yml` for the ecosystems T4's trial supports, with a 7-day cooldown
- [ ] T6 — close: Demo steps 1–4, an ADR only if a decision outlives the item (Dependabot against strict
  locks is the likely one), delete this PRD, edit the backlog row

## Out of scope

The required-check ruleset, secret scanning and going public (`OPS-005`); the security scanners
(`OPS-007`); the conventional-commit check (`OPS-008`); the release pipeline and the arm64 rootfs build
(`JD-066`); instrumented tests and Maestro in CI.
