# ADR-0016 — Name every gate task explicitly; never rely on an aggregate

Status: accepted
Date: 2026-08-20

## Context

**`ktlintCheck` never checked a `.kt` file, and reported success for months.**

Found by removing three imports a step had made unused and noticing the gate had not asked for them.
`./gradlew :app:tasks --all` listed **only** `runKtlintCheckOverKotlinScripts` and
`ktlintKotlinScriptCheck`: no per-source-set task existed, so `ktlintCheck` — an aggregate over
whatever the plugin registered — resolved to the `.kts` build scripts and nothing else. Probed
directly, an unused import *and* an out-of-order one produced `BUILD SUCCESSFUL in 715ms`.

**The cause was one line that is not in `app/build.gradle.kts`.** `ktlint-gradle` 12.2.0 attaches its
Android applier to the plugin id `org.jetbrains.kotlin.android`; AGP 9 ships Kotlin support built in,
so nothing applies that id and the applier never ran. Nothing was misconfigured and nothing logged —
the plugin was waiting for a plugin that no longer needs to exist. `ktlint-gradle` 14.x registers
`ktlintMainSourceSetCheck` and its siblings against AGP 9's own source sets.

Turning it on reported **11,939 offences, and 9,864 of them were `.editorconfig` being wrong rather
than the code**: it had claimed `indent_size = 4` for `.kt` since the first step while every `.kt`
file in the repository is 2-space — a claim nothing had ever checked. What survived was **1,600 real
offences**, net **−172 lines** across 175 files, including 185 unused imports. One could not be
auto-corrected and was a genuine defect: a test class left with the old name of the composable under
it.

**The decision then failed its own rule, one level up.** The hook first named
`ktlintTestDebugSourceSetCheck` and `ktlintAndroidTestDebugSourceSetCheck`. Those tasks exist —
`ktlint-gradle` registers one per *variant* source set — but `src/testDebug/` and
`src/androidTestDebug/` do not, so both were permanently `NO-SOURCE → SKIPPED`. Every unit and
instrumented test file went unchecked by the named half of the gate for as long as it had existed.
The verification that was supposed to prove otherwise had watched the aggregate `ktlintCheck` fail
beside them on the same command line — the exact substitution this decision forbids, performed by
the verification of the decision.

Re-measured one task at a time, on an unused import planted in a real test file:
`ktlintTestDebugSourceSetCheck` alone → `NO-SOURCE`, `BUILD SUCCESSFUL`;
`ktlintTestSourceSetCheck` alone → `BUILD FAILED`, naming the line.

## Decision drivers

- A gate that cannot fail is worse than no gate, because it is trusted.
- A tooling change must be able to *break* the build, not quietly stop covering things.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Run the aggregate task | Short command; survives plugin changes | Resolves to whatever the plugin registered. When that is nothing, it passes |
| Name each source-set task | A plugin that stops seeing a source set fails with *"Task not found"* | The command is longer, and the names must match real source sets |

## Decision

**The commit gate names `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck` and
`ktlintAndroidTestSourceSetCheck` explicitly.** `ktlintCheck` stays first so the `.kts` scripts are
still covered.

Three `.editorconfig` settings now describe the style the code was actually written in, and each
earns its line: 2-space for `.kt` and 4 for `.kts`; `ktlint_code_style = intellij_idea`, because
ktlint's own default breaks any signature of two or more parameters across lines whatever its width,
which added 108 lines to one test file and pushed five over the 500-line gate; and
`no-consecutive-comments` disabled, because a file-header KDoc is this project's convention.

**The rule generalises past ktlint:** any gate whose task list is *discovered* rather than *declared*
can stop covering something without saying so.

## Consequences

**This forbids** relying on an aggregate task to prove a gate ran, and verifying a named task by
watching a different task fail beside it. Verify one task at a time, against a planted defect.

`src/` has three source-set directories and not five, which is why the *source-set* names are correct
and the *variant* names were not.

**The same rule caught `check_doc_citations.py` four times, and each time by the file set rather than
the task name.** It globbed `app/src/**/*.kt` plus three extensions under `tools/`, and every file
outside that list was not reported as unchecked — it was not reported at all. Measured, after each
fix was made to fail first:

| What the gate could not see | How many citations it hid | Why |
|---|---|---|
| `.sh`, `.py`, `.c` read with Kotlin's scanner | 14 | `comments_only` finds no `//` in a shell script, so the file came back with no prose |
| A step code ending in `'` or `/` | 199 | The boundary was `[\s,.;:)\]]`, so `P6b-0's` and `P5e-3/P5e-5` read as ordinary words |
| `Dockerfile`, two `.g4` grammars, `AndroidManifest.xml`, `libs.versions.toml`, `busybox.config` | 33 | Discovery was a list of four extensions; anything else under a scanned tree was never opened |
| A KDoc after a raw string spelled with four quotes | 5, in 86 blind comment lines across 5 files | Kotlin closes a raw string at the **last** triple of a quote run; the scanner stopped at the first and read the rest of the file as string content |

So discovery is now **whole trees with a per-language scanner map, and an unknown type fails the run
by name**. Returning a default scanner is what made the first of those four silent. Binary files are
skipped by git's rule — a NUL byte in the first 8000 bytes — rather than by an extension list, because
`jniLibs/` and `assets/` hold gitignored build output.

Two of those fixes widened a pattern, and a widened pattern finds citations in files that were
previously clean. That is not a regression in the tree; it is the gate seeing more. `docs/adr/0066`
states how such a citation is then removed.

The thing a future reader will be tempted to "fix" is the long gate command. Each name in it is there
because the short form once reported success over nothing.
