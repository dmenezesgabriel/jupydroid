# ADR-0129 — Kotlin quality gates are kept where their findings are real

Status: accepted
Date: 2026-11-01

## Context

The tree had detekt, ktlint and Android Lint, and nothing that failed on a compiler warning, an
accidental `public` in the contract extensions build against, a copied block, an unused dependency,
an architecture rule the scanner cannot state, thin coverage or a surviving mutant. Six candidates
were each run on the tree before any was wired in, so that a gate nobody trusts, and everybody
suppresses, never entered the commit hook.

None of the references settles this. Read at the pins in `docs/README.md`, 0 of 7 run a duplication,
coverage, mutation, architecture-as-test or dependency-usage Kotlin gate, and 2 of 7 turn compiler
warnings into errors (intellij-community per module, the TypeScript projects as `noUnusedLocals`).
The authority for the rest is each tool's own documentation.

## Decision drivers

- A gate is kept only if what it reports on this tree is a defect or drift, not noise.
- A commit gate runs in the memory the host has and in seconds to a few minutes.
- Every added dependency pays [ADR-0123](0123-dependencies-are-locked-strict-and-verified-by-sha256.md)'s
  lock and checksum procedure, so it has to earn that.

## Options considered

| Candidate | Result on the tree | Decision |
|---|---|---|
| Compiler strictness | 658 warnings under `--rerun-tasks`: 643 one cause (coroutines test APIs without an opt-in, 55 files), 15 real | **Kept** |
| `explicitApi()` on `:extension-api` | 48 missing visibilities and 3 missing return types | **Kept** |
| PMD CPD | 150 tokens finds nothing; 100 finds one 41-line main cluster and 4 test clusters; 50 finds 10 main clusters | **Kept at 150** |
| Dependency Analysis plugin 3.20.1 | states it works with AGP 8.10.0 to 9.3.1; the tree is on 9.5.0. 3 plausible findings, 1 false positive, about 60 "declare the BOM-managed transitive" items; 2m57s, 5.4 GB | Rejected |
| Konsist 0.18.1 | parses all of `app/src/main` per test class; out of memory at the default heap and at 2g, after 10m52s | Rejected |
| Kover 0.9.11 | runs on AGP 9.5.0; logic packages 94.8% of lines and 79.6% of branches, the rest device-only adapters it cannot credit | Rejected |
| Pitest (`pl.droidsonroids.pitest` 0.3.0) | runs; 3 of 11 survivors in `glob` are compiler intrinsics that only the commercial Arcmutate Kotlin plugin filters; the tree extrapolates to about 80 minutes | Rejected |

## Decision

- **Compiler strictness.** `allWarningsAsErrors` in `:app`, `:extension-api`, `:extensions:git` and
  `:extensions:ai`, and `explicitApi()` on `:extension-api` only: it is the contract
  ([ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)), `:app` is not a
  library. Every declaration flagged was already used, so each is `public` and stated; none was
  narrowed. No dependency is added. The `.kts` build scripts' own deprecations are script-compile
  warnings this flag does not reach.
- **Duplication.** `:app:cpd` runs PMD CPD 7.29.0 through a locked, sha256-verified `cpdTool`
  configuration (Gradle's PMD plugin is Java-only and has no CPD) over `src/main` and `src/test` of the
  four modules, at **150 tokens**, the largest threshold that finds nothing. It is in
  `.pre-commit-config.yaml` and the `AGENTS.md` gate command. The 100-token hit,
  `NotebookSaveConflict` and `FileSaveConflict`, is a real copy and stays open as its own item.
- **Rejections are for now, each with the condition that reopens it:**
  - Dependency Analysis: until its tested range reaches the AGP in `gradle/libs.versions.toml`.
  - Konsist: until a release parses a module of this size inside a default test heap.
  - Kover: not while the logic packages are already covered by TDD and the remainder only a device
    runs, since a floor would be either arbitrary slack or a demand for JVM tests of device code.
  - Pitest: until a package-scoped on-demand run is wanted, or the Kotlin intrinsic filter is free.
- `ArchitectureGuardTest` stays the only architecture gate.
- Gate results go in the commit body, not here.
