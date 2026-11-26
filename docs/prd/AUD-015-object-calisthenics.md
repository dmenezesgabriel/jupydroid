# AUD-015 — Object calisthenics, measured then enforced

Milestone: `M13` · State: in-progress · Depends on: `AUD-011` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. What an `AUD-NNN` ships is a
> *measurement* rather than a screen
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), so the
> template's Outcome, Demo, Maestro flow and domain→port→adapter Surface are replaced by what is
> examined and the command that reproduces each number. The two load-bearing sections stay: a row
> per reference, and a gap analysis against our own code.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, the fix items it names and the code
> comments — nothing else ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **the Kotlin tree's own style against four object-calisthenics rules** — taken through
four questions at once, never four sweeps: what the references' toolchains enforce of these rules,
what we have stated and what keeps each statement true, where we differ and *why*, and whether the
path that would hold them is sound — deterministic, cheap enough for the commit gate, fails closed,
tested, and wired where a commit or a step actually runs it (the `AUD-011` harness read, applied to
one rule family).

The four rules as the backlog names them, each pinned to a predicate the census can count. Two
restate prose the Working Agreement already carries; two the item proposes — the census measures
all four against the tree as it is, and the verdicts decide which join the gates:

| # | Rule | What the census counts | Standing today |
|---|---|---|---|
| 1 | no `else` | every `else` leaf — an `if`'s else branch, an `else if` link, a `when` entry's `else ->` — counted by kind | restates AGENTS.md's "early returns over nested ifs" (code style) |
| 2 | nesting | control-flow constructs (`if`/`when`/`for`/`while`/`try`/`catch`) nested inside one another in a function body; depth per function | restates "max 2 levels of indentation" — the backlog says "beyond one level", so the threshold is an open decision |
| 3 | one dot | selector chains `recv.a.b()` on one expression beyond one dot; package-qualified *names* (`com.jupydroid.app.…`) are not chains — PSI distinguishes them; builder-type exemptions must be documented | **stated nowhere** — the item proposes it |
| 4 | no abbreviations | declared names matching a pinned abbreviation dictionary; which declaration kinds (param, property, function, class) and which carve-outs (`it`, `e` in `catch`) are open decisions | **stated nowhere** — AGENTS.md's naming rule bans generic names (`data`/`handler`/`Manager`), not abbreviations |

**Found while scoping — measured, not guessed.** Facts that shape the mechanism and the tasks:

- The Kotlin tree is 1506 `.kt` files across `app/src`, `extension-api/src` and `extensions/*/src`;
  702 sit under `*/main/*`, the rest under `test`/`androidTest`. detekt's `detekt` task reads
  `src/main` and `src/test` by default — `src/androidTest` is in no default source set (the plugin's
  own source strings), so "the Kotlin tree" needs a scope decision, not an assumption.
- Rule 1's raw shape: ~1132 `else` tokens, 178 `else ->` when-arms and 9 `else if` across the tree —
  comments and strings inflate the first number; the AST count is the measurement.
- Rule 3's raw grep is useless on purpose: ~31k three-segment `a.b.c` matches, almost all
  package-qualified type names. A census that cannot tell a call chain from a package name is the
  grep the item forbids — the walk must be a real parse.
- detekt's defaults already gate fragments of the four at *different* thresholds:
  `NestedBlockDepth` (active, `allowedDepth: 4`), `ReturnCount` (active, `max: 2` — it limits
  returns while "early returns" adds them; `excludeGuardClauses` exists, default false),
  `ComplexCondition` (3) and the naming `*Naming` patterns. Where detekt's rule already counts the
  same predicate the change is `detekt.yml`, not new code; the audit reads each for semantic fit
  before writing a custom rule.
- `AUD-011`'s ledger already classified "max 2 levels of indentation" and "early returns" as
  `convention` — prose-level style nothing gates. This census is the measurement that verdict was
  waiting for.

**Carried in.** The style the gates protect is every earlier slice's code; the machinery is
`AUD-011`'s measured harness — the same question (does each gate fail closed, is it tested, is it
wired) now asked of four style rules.

## The surface, produced

Never stored (ADR-0067). The census command the tasks produce is a detekt run's findings, one row
per violation with file, line and rule; its inputs reproduce today:

```sh
# the tree the census walks — four modules, every source set
find app/src extension-api/src extensions -name '*.kt' | wc -l
# what already gates fragments of the four rules — the repo's overrides over detekt's defaults
# (buildUponDefaultConfig = true, app/build.gradle.kts; the defaults live in dev.detekt:detekt-core's
# default-detekt-config.yml — NestedBlockDepth@4, ReturnCount@2, ComplexCondition@3 are active there)
sed -n '1,30p' config/detekt/detekt.yml
grep -n "buildUponDefaultConfig" app/build.gradle.kts
# the raw material's shape, grep-level only — the AST count is the measurement
grep -rnoE '\belse\b' app/src extension-api/src extensions --include='*.kt' | wc -l
grep -rnoE 'else\s*->' app/src extension-api/src extensions --include='*.kt' | wc -l
# the harness the detector joins — the Kotlin-side hook and its scope rule
grep -n "id: detekt" .pre-commit-config.yaml
uv run --locked python scripts/check_gradle_hook_scope.py
# the census itself once the ruleset exists — the hook's own command, findings in
# app/build/reports/detekt/detekt.{html,md,sarif,xml}
scripts/gradlew-jbr detekt :extension-api:detekt :extensions:git:detekt :extensions:ai:detekt
```

## Who else implements this

Verify by reading code; cite `<project>@<sha> <path>` (pinned SHAs in [`../README.md`](../README.md)).
The question is not whether a reference *has* these rules — object calisthenics is a minority
practice — but whether any mature project's toolchain enforces them on its own tree, because an
absence across all of them is itself the finding: these are our rules to keep or drop deliberately.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode` | ABSENT | `eslint.config.js`, `build/hygiene.ts` | `naming-convention` limited to PascalCase classes; hygiene filters cover copyright, indentation, unicode — no else/nesting/dot/abbreviation rule |
| `jupyterlab` | PARTIAL | `eslint.config.mjs` | `switch-exhaustiveness-check: error` — the constructive half of no-`else` (an exhaustive `when` needs no `else` arm); naming-convention for interfaces; nothing for nesting, dots or abbreviations |
| `intellij-community` | ABSENT | `.editorconfig`, `qodana.yaml` | formatting settings plus a fatal-errors-only inspection profile (`idea_fatal_errors.yml`); its own tree uses `else` pervasively — no calisthenics enforcement |
| `vscode-jupyter` | ABSENT | `.eslintrc.js` | prettier + `@typescript-eslint` defaults; no calisthenics rules |
| `termux-app` | ABSENT | `.editorconfig`, `app/build.gradle` `lint {}` | formatting plus Android lint defaults |
| `jediterm` | ABSENT | `.editorconfig` | formatting only — `ij_any_else_on_new_line` places the `else`, it does not ban it |
| `winlator` | ABSENT | — | no `.github/` workflows and no lint configuration found |
| the shipped rootfs | n/a | — | a Python artifact the gates watch, not a watcher (`AUD-011`'s verdict) |

**Count:** 0 of 7 enforce object calisthenics; 1 (`jupyterlab`) enforces the exhaustiveness mechanism
that makes rule 1 satisfiable in Kotlin. What that says: rules 1–2 restate this repo's own Working
Agreement prose and rules 3–4 are proposals the item brings — nothing here is parity debt, and the
detector joining the gates is *adoption of our own stated style*, which is why the census, not a
reference, is the measurement.

## What already exists — reuse, never rebuild

- **detekt's default ruleset**, already active on the tree: `NestedBlockDepth` (`allowedDepth: 4`),
  `ReturnCount` (`max: 2`), `ComplexCondition` (3), `CyclomaticComplexMethod` (15) and the naming
  `*Naming` patterns. Each is read for semantic fit against its rule's predicate before any custom
  rule is written — a threshold or option change in `config/detekt/detekt.yml` where they match, new
  code where they do not.
- **The designed extension points, verified present:** the `dev.detekt` Gradle plugin carries a
  `detektPlugins` configuration (`DetektBasePlugin`) for custom rule artifacts, `detekt-test-utils`
  `2.0.0-alpha.5` is on Maven Central for rule unit tests, `dev.detekt:detekt-api` exposes
  `dev.detekt.api.{Rule,RuleSet,RuleSetProvider,Issue}`, and `DetektCreateBaselineTask` /
  `detektBaseline` is the ratchet for findings that predate the gate.
- **`ArchitectureGuardTest`'s planted-violation red-proof** — how a Kotlin-side gate proves it fails
  closed without a suite; the same proof shape applies to the new ruleset.
- **`check_gradle_hook_scope.py`** — every tracked path is a Gradle input or a named non-input, so
  `detekt-rules/` cannot arrive unclassified: the gate fails until the `gradle_inputs` pattern in
  `.pre-commit-config.yaml` claims it. Self-enforcing, named here so the task plans for it.
- **`kotlin_source.py`** — the comment-blanking scanner. Not needed if the rules run on compiler
  PSI: strings and comments never parse as code, which is the whole reason the census is an AST
  walk and not a scanner.

## Gap analysis — ours today

| Rule | Where stated | Enforced by | State |
|---|---|---|---|
| no `else` (rule 1) | AGENTS.md code style — "early returns over nested ifs" | nothing counts `else` leaves | convention |
| nesting (rule 2) | AGENTS.md code style — "max 2 levels of indentation" | detekt `NestedBlockDepth` at depth 4 — looser, and its block-depth semantics vs control-flow nesting is unread | enforced at 4, convention at 2 |
| one dot (rule 3) | nowhere — the item proposes it | nothing | unstated |
| no abbreviations (rule 4) | AGENTS.md naming rule — generic names, not abbreviations | detekt `*Naming` patterns — format only | unstated |

Ours is: two rules stated but ungated at their stated threshold, two unstated. The `AUD-011`
verdicts stand until this census re-measures them per rule.

## Deviations

- **Four rules, not the Object Calisthenics catalog.** The practice names more (small entities,
  few fields, no getters/setters, …); the item names four. The census measures the four and the
  rest stay unmeasured — adopting the catalog is a different item.
- **detekt PSI, not a second parser.** The census *is* the detekt ruleset, so measurement, report
  and gate are one artifact — findings, counts and enforcement cannot drift apart. A tree-sitter
  route would parse Kotlin a second time on a grammar (`tree-sitter-kotlin@1.1.0`) that predates
  the Kotlin 2.4 syntax this tree uses; parse errors would silently shrink the census, which is the
  one failure a measurement cannot have.
- **`jupyterlab`'s exhaustiveness check is the model for rule 1's Kotlin shape**, not a rule we
  port: `when` over a sealed type or enum is exhaustive without `else`, so the census counts
  `else ->` arms but the verdict reads whether the subject *could* have been exhaustive — an
  `else` on an exhaustive `when` and an `else` on an open `when` are different findings.

## Open decisions

- **Predicate semantics at the edges** — does an `else if` chain count as one `else` or a chain;
  does `?.` count as a dot; do lambdas and receiver lambdas (`let`, `apply`, Compose content)
  deepen rule 2's nesting; which declaration kinds rule 4 covers. Pinned at T1 as fixture Kotlin —
  each rule ships fixtures proving what counts and what does not.
- **The abbreviation dictionary** — the census first *reports* abbreviation-shaped names; the
  enforced list is pinned from that measurement, with Kotlin-idiomatic carve-outs (`it`, `e` in
  `catch`) decided on evidence, not taste.
- **Baseline or clean tree at activation** — `detektBaseline` records today's findings so only new
  ones fail (the designed ratchet), versus rules staying inactive until the fix items land. Decided
  at T4 from the measured counts — a few dozen findings argue baseline, hundreds argue sequencing.
- **`androidTest` scope** — detekt's default reads `src/main` + `src/test`; `src/androidTest` holds
  Compose tests under the same style rules but nothing analyzes it today (ktlint does, detekt does
  not). "The Kotlin tree" argues for joining it via `source.from(...)`; status quo argues leaving
  it. Decided at T1 with the module stood up.
- **Where adopted rules are stated** — rules 3–4 have no prose home; a gate holding an unstated
  rule is backwards. AGENTS.md's code-style block gains the lines, or an ADR states them — decided
  when the gate lands, since adoption is the item's mandate and restating 1–2 is not.

## Tasks

- [ ] **T1 — the predicates pinned, the mechanism spiked.** The four rules' measurable definitions
  written as fixture Kotlin per open decision 1. `:detekt-rules` stood up end-to-end — a plain JVM
  module, no Android plugin: `settings.gradle.kts` `include`, its `build.gradle.kts` (`detekt-api`,
  `detekt-test-utils` as the test dep), `META-INF/services` `RuleSetProvider` registration,
  `detektPlugins(project(":detekt-rules"))`
  on `:app`, `:extension-api`, `:extensions:git` and `:extensions:ai`; `detekt-rules/` classified in
  `check_gradle_hook_scope.py`'s `gradle_inputs` pattern, and the module's own source-set tasks named
  in the hook entries where the ADR-0016 convention requires. One rule reports a finding on a
  fixture and its unit test is green; the module's own Kotlin passes the same gates it helps
  enforce. A mechanism that fails here falls back to the tree-sitter route before four rules are
  written on it.
- [ ] **T2 — the census.** All four rules implemented with rule unit tests; the tree walked; the
  per-rule findings produced — the command and its counts are the measurement. The
  `## Object calisthenics — AUD-015` section lands in `parity-ledger.md` with the produced commands,
  and the Areas row is added.
- [ ] **T3 — the verdicts.** Per rule: the reference verdict, the our-state verdict and the
  obligation — `absent` → `GAP-NNN` (next free ≈ `GAP-213`), `undocumented` → an ADR, `defect` → a
  red-first fix in this slice. Splits per rule as the counts demand (the `AUD-014` precedent).
- [ ] **T4 — the detector joins the gates.** The rules activated in `config/detekt/detekt.yml` —
  baseline or clean tree per open decision 3; the adopted rules stated per open decision 5;
  `androidTest` scope applied per open decision 4; the whole hook set green with `--all-files`.
- [ ] **T5 — close.** The Areas row set to `examined`; the fix items named as a `JD-NNN` row (next
  free ≈ `JD-103`) if the findings warrant repairs of their own; this PRD deleted per ADR-0001; the
  backlog row edited and `← next` moved to `AUD-016`.

## Out of scope

- **Building the repairs.** Findings become `GAP-NNN` rows and `JD-NNN` fix items with their own
  sessions, PRDs and demos; this slice fixes only `defect`s, red first.
- **The rest of the Object Calisthenics catalog** — see Deviations.
- **A Maestro flow** — nothing on screen changes; the reproducing command is the evidence (the
  `AUD-NNN` exception to the device gate).
- **Rewriting the Working Agreement's existing rules** — the audit enforces what is stated plus
  what the item proposes; restating 1–2's wording is a different act from adopting 3–4.
