# ADR-0151 — The Gradle hooks run when a commit touches what Gradle reads

Status: accepted
Date: 2026-11-15

## Context

The five Gradle hooks (ktlint, detekt, the copy-paste detector, Android Lint and the unit tests with the
`androidTest` compile) were `always_run: true`, so every commit paid for all of them, whatever it changed.
The host is shared with other repositories' work, and a job waits at `devjob` until the host's load is at or
under its cores.

Measured on 2026-11-14 and 2026-11-15:

- **The cost.** Five commits of this session, none touching Kotlin, each took 1 min 50 s to 2 min 37 s of
  wall time, 2 min 58 s to 3 min 52 s of CPU and 0.96 to 1 GB at peak to commit. That is the whole hook set and
  not the Gradle hooks alone, so it overstates what skipping them saves: their part alone is the 68 to 110 s the
  experiment below measured with Gradle warm, and the memory, since a commit that skipped them peaked at 336 to
  347 MB.
- **Who waits.** `devjob-report` for the previous day: this repository ran 46 jobs for 4.5 CPU-hours, a twentieth
  of the queue's CPU, and waited 3.7 of the 6.6 hours the queue's jobs waited in all. Slot wait was 0.0 hours: the
  wait is the load gate, which admits on host load and not on a job's own size, so a cheaper job of ours is not
  admitted sooner by that alone. The heavy load in the same window was another repository's end-to-end runs. What
  this decision removes is the jobs themselves, which is a smaller part of the load than the waiting suggests.
- **How many commits need them.** Of the last 300 non-merge commits, 214 touched no file a Gradle task reads
  (141 of the 300 were `docs`).
- **What Gradle itself says.** The five hooks' exact task lists were run four times in a clean tree (a warm-up,
  a baseline, a run after a newline was appended to each of 17 files across the excluded kinds, and a control).
  Baseline: 5 tasks executed. Excluded kinds changed: the same 5, none more. Control, a comment appended to
  `config/detekt/detekt.yml`: 9, the 5 plus all four `detekt` tasks. Gradle treats those files as no input.
- **What does not shrink on its own.** Those 5 tasks are `lintDebug` four times and `cpd`: Gradle executes them
  on every run with nothing changed (68 to 110 s), so its up-to-date check saves nothing there, and a commit
  that touches no input pays for them unless the hook is skipped.
- **What reads outside a module.** A search of the Kotlin sources and the build scripts found no read of a
  repository path outside its module except the ones the pattern below names: `config/detekt/detekt.yml`, `gradle/dependency-floors.gradle.kts`,
  `tools/grammars/python`, and the licence files copied into the assets.

## Decision drivers

- A commit that cannot change what a Gradle task reads should not pay for the Gradle tasks.
- A path rule is only as safe as its list, so it must fail closed when a file or a build input is new.
- CI is the witness for what a commit skipped: it runs every hook over every file.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep `always_run` | the rule as written | every commit pays for the five, and a docs commit waits behind the host |
| Run only the tasks of the module a commit touches | smallest cost | a shared input (`gradle/`, `config/`, a convention plugin) touches every module, so the rule is a second build graph to keep right |
| Skip the hooks and rely on CI alone | no local cost | a defect reaches the remote before the author sees it |
| **One `files` pattern shared by the five hooks, a gate that proves it complete, CI over everything** | the cost goes where the inputs are; fails closed | the list is ours to keep, and a hand edit of it is the risk the gate exists for |

## Decision

**The five hooks share one `files` pattern, defined once on `ktlint-check` and shared by a YAML anchor, and
`always_run` is removed from them.**

- The pattern is `app/`, `extension-api/`, `extensions/`, `gradle/`, `config/`, `tools/grammars/`, the root build
  scripts, `gradle.properties` and the two lockfiles, `.editorconfig`, the wrapper, the four licence files the
  build copies into the assets, `scripts/gradlew-jbr`, `scripts/java-home.sh` and `.pre-commit-config.yaml`. The
  config is in it so that a change to a hook runs them all.
- `scripts/check_gradle_hook_scope.py` is a hook that always runs (milliseconds) and fails when the five stop
  sharing one pattern or one keeps `always_run`; when a tracked file neither matches the pattern nor is named in
  its `NOT_GRADLE_INPUTS` with the reason no Gradle task reads it; and when a build script names a path outside its
  module (`$rootDir/...`, `projectDirectory.dir("../...")`) that the pattern does not match. A new module, a new
  root file or a new build input stops the commit until someone decides it.
- CI is unchanged: `pre-commit run --all-files` and one step per Gradle hook, so every file is checked before
  `main`.

## Consequences

A commit that touches only documentation, scripts, `.maestro`, `.github`, `.claude`, `tools/rootfs` or the
commit tooling skips the five hooks: 68 to 110 s of wall time with Gradle warm, far more with a cold daemon and
cache (the experiment's warm-up took 499 s), and about 0.7 GB of peak memory. The share of a commit's wall time
that was queue wait is not measured here; `devjob-report` a week after this lands is where to read it.

**What a commit costs with the scope in place.** A documentation-only commit made after this landed ran every hook
that applies and skipped the five Gradle ones: 1 min 14 s of wall time, 1 min 9 s of CPU and 131 MB at peak, against
the 1 min 50 s to 2 min 37 s, 2 min 58 s to 3 min 52 s and about 1 GB of the five commits measured above. The hook
that costs most of what is left is `maestro-gate-tests`, which runs the whole `scripts/` test suite on every commit:
the hook's own command, run three times on 2026-11-15 with the host at a load of 8 to 11, took 46 to 49 CPU-seconds,
50 to 53 s of wall time and 75 MB. The comment above it in `.pre-commit-config.yaml` that calls its tests
milliseconds does not hold. An earlier reading of 119 CPU-seconds for it, taken through `pre-commit run` for each hook in turn, did not reproduce.

**Why that hook is not scoped by path.** Each of the 76 test modules was run alone with an audit hook that logs
what it opens, lists and spawns. Ten of them read the live tree broadly (`app/`, `.maestro/`, `docs/`, `tools/`,
`extensions/`: the gates' own tests over this repository) and cost 16 of the suite's 55 CPU-seconds, so a path rule
would still run them on nearly every commit. Splitting the other 66, 38 CPU-seconds, from those ten would save that
on a commit that touches no file under `scripts/`, and would need a list of modules to keep right, with a gate to
prove it. That is a second list of the kind this decision exists to avoid, for under a minute of CPU on one kind of
commit, so the hook stays as it is. Revisit it if the suite grows past what a commit should wait for.

A defect reaches CI, not the author's machine, when a non-input file turns out to matter. The experiment and the
search show none does today, and the gate is what finds the next one. Do not widen `NOT_GRADLE_INPUTS` to make a
failing commit pass: add the path to the pattern when a build or a test reads it.
