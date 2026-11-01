# OPS-013 — Kotlin and Gradle quality gates beside detekt

Milestone: `M8` · State: open · Depends on: — · Closes: —

> An **OPS** item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): it
> changes the repository's build, not a screen, so its *Demo* is the run that reproduces it and no
> Maestro flow is owed. **Deleted when the item ships** — its durable residue is an ADR (which gates
> were kept and which rejected, and why), the comments in the build files and an `AGENTS.md` gate list
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Each candidate gate has been run on the tree and is either wired into pre-commit and the local gate
because its findings were real, or rejected with the measurement that rejected it. A rejection is a
valid outcome and is recorded; a gate that only reports noise is not kept.

## Demo

For every kept gate, from a clean checkout:

1. The gate's command (named in its task) is green on the tree.
2. A planted violation of the one thing it checks fails it, and reverting passes again.
3. The same command is in `.pre-commit-config.yaml` and in `AGENTS.md`'s local verification command.

For a rejected gate, the demo is the command and the numbers that rejected it, quoted in the commit
body of its task. Each step's output goes in that commit body.

## Who else implements this

Read at the pins in [`../README.md`](../README.md): `git grep` for `allWarningsAsErrors`, `explicitApi`,
`-Werror`, `kover`, `konsist`, `pitest` and `detekt` over each clone's build files.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 tsconfigbase.json` (`noUnusedLocals`) | The compiler's own unused-code checks, no separate duplication, coverage or mutation tool in `package.json` |
| `vscode` | PARTIAL | `vscode@3f3380c668d src/tsconfig.base.json` (`strict`, `noUnusedLocals`) | Same: strictness is a compiler flag |
| `vscode-jupyter` | ABSENT | — | No Kotlin or Gradle |
| `intellij-community` | PARTIAL | `intellij-community@406a27a8da platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/BazelBuildFileGenerator.kt` (`allWarningsAsErrors`), `platform/jewel/.idea/detekt.xml` | Warnings-as-errors is a per-module option its Bazel generator emits; detekt in one subproject. No Kover, Konsist or Pitest |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/build.gradle` (`-Werror` on C flags) | Strict C compiler flags only; no Kotlin gate |
| `winlator` | ABSENT | — | No gates in its build files |
| the shipped rootfs | ABSENT | — | Not Kotlin |

**Count:** 0 of 7 run a duplication, coverage, mutation, architecture-as-test or dependency-usage
Kotlin gate, and 2 of 7 turn compiler warnings into errors (intellij-community per module, the TS
projects as `noUnusedLocals`). The references give the shape for compiler strictness only; for the
rest the authorities are each tool's own documentation: the
[Dependency Analysis plugin](https://github.com/autonomousapps/dependency-analysis-gradle-plugin),
[Konsist](https://docs.konsist.lemonappdev.com/), Gradle's
[PMD plugin](https://docs.gradle.org/current/userguide/pmd_plugin.html) (CPD), Kotlin's
[`allWarningsAsErrors` and explicit API mode](https://kotlinlang.org/docs/gradle-compiler-options.html),
[Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/) and the
[Pitest Gradle plugin](https://gradle-pitest-plugin.solidsoft.info/). Which tool they cover is a
claim to verify in the task, on the pin that task resolves.

## Implementation inventory

### ours

| Concern | Files |
|---|---|
| Module build files | `app/build.gradle.kts`, `extension-api/build.gradle.kts`, `extensions/git/build.gradle.kts`, `extensions/ai/build.gradle.kts` |
| Plugin catalogue | `gradle/libs.versions.toml` (`[plugins]`) |
| Static analysis today | `config/detekt/detekt.yml`, `.editorconfig` (ktlint), Android Lint |
| Architecture gate today | `app/src/test/java/com/jupydroid/app/architecture/ArchitectureGuardTest.kt` |
| Commit gate | `.pre-commit-config.yaml`, `AGENTS.md` (Local verification command) |
| Dependency lock | `*/gradle.lockfile`, `buildscript-gradle.lockfile`, `gradle/verification-metadata.xml` ([ADR-0123](../adr/0123-dependencies-are-locked-strict-and-verified-by-sha256.md)) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Trial rule | adopt-then-tune · measure-then-decide | A tool adopted first and tuned later leaves a gate everyone suppresses | **Measure on the tree first.** Kept only if the findings are real defects or drift; a rejection is recorded in the ADR |
| Compiler strictness | `allWarningsAsErrors` per module (intellij-community) · none | No new dependency, so no lock change; fails on any new warning, including from a Kotlin bump | **Trial first (T1)**: it is the cheapest and decides how much the later tools add |
| Public API surface | `explicitApi()` on `:extension-api` · none | The module is the contract extensions build against ([ADR-0104](../adr/0104-extensions-are-compile-time-modules-against-an-extension-api.md)); an accidental `public` is a breaking change | **`explicitApi()` on `:extension-api` only**; `:app` is not a library |
| Architecture rules | Konsist beside `ArchitectureGuardTest` · in place of it · neither | A second tool must express a rule the scanner cannot, or it is duplication | Decided in T3 by listing the rules each can state |
| Dependencies | each new plugin or library rewrites locks and sha256 | Every addition costs the ADR-0123 procedure | A candidate that needs one must earn it in its trial |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Warnings as errors | none (`grep allWarningsAsErrors` over `*.kts` is empty) | absent |
| Explicit API | none on `:extension-api` | absent |
| Unused or misdeclared dependencies | none | absent |
| Architecture as tests | `ArchitectureGuardTest`, a source scanner | present |
| Duplication, coverage, mutation | none | absent |

## Open decisions

- **Floor-only dependencies.** `commonsLang3` and the zstd pin are declared so a constraint can raise
  them (`gradle/libs.versions.toml` comments). T2 must show whether the Dependency Analysis plugin
  reports them as unused, and if so how they are declared on purpose rather than suppressed.
- **Pitest on Android.** Its Kotlin and AGP 9 support is unverified; T6 is time-boxed and a rejection
  is the expected outcome unless the trial runs on this tree.

## Surface

| Layer | Files |
|---|---|
| build | the module `build.gradle.kts` files, `gradle/libs.versions.toml`, lockfiles, `gradle/verification-metadata.xml` |
| hooks | `.pre-commit-config.yaml` |
| docs | `AGENTS.md` (Local verification command and tooling table), an ADR beside ADR-0123 |

## Tasks

Each: trial on the tree → decide → wire (pre-commit and the `AGENTS.md` command) → commit, or reject
and record. Every line of code is still TDD: the red is a planted violation the gate must fail.

- [x] T1 — Compiler strictness: `allWarningsAsErrors` on every module and `explicitApi()` on
  `:extension-api`. Measure warnings per module first; fix real findings, suppress none to force green.
- [x] T2 — Dependency Analysis plugin. **Rejected for now**: 3.20.1 states it is only known to work
  with AGP 8.10.0 to 9.3.1 and the tree is on 9.5.0; retry when its range reaches the AGP in
  `gradle/libs.versions.toml`. Numbers are in the commit body.
- [x] T3 — Konsist against `ArchitectureGuardTest`. **Rejected**: 0.18.1 (built with Kotlin 2.4.21)
  parses all of `app/src/main` (565 files, 59,343 lines) per test class and ran out of memory at the
  default test heap and again at 2g, after 10m52s. Numbers are in the commit body.
- [x] T4 — PMD CPD duplication. **Kept at 150 tokens** (`:app:cpd`, PMD 7.29.0 via a locked `cpdTool`): the tree
  measures zero there in main and test sources. 100 tokens finds `NotebookSaveConflict` / `FileSaveConflict` (41
  lines), a real copy left unmerged, and 4 test clusters. Numbers are in the commit body.
- [x] T5 — Kover coverage. **Rejected**: 0.9.11 runs on AGP 9.5.0 and Kotlin 2.4.21 (`:app:koverXmlReportDebug`,
  3m52s cold, locks and sha256 written), but the logic packages already measure 94.8% of lines and 79.6% of
  branches, and what is left is device-only adapters Kover cannot credit, so any floor is either arbitrary slack
  or a demand for JVM tests of code only a device runs. Numbers are in the commit body.
- [ ] T6 — Pitest, time-boxed.
- [ ] T7 — The ADR (kept and rejected, with numbers) and ship: delete this PRD, edit the backlog row.

## Out of scope

- Python gates are `OPS-014` and `OPS-017`.
- Running these gates in CI is the CI item; this one wires pre-commit and the local command.
