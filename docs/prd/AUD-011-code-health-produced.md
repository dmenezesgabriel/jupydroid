# AUD-011 — Code health, produced

Milestone: `M0` · State: in-progress · Depends on: `AUD-010` · Closes: `GAP-006`, `GAP-008`

> An **audit** item, so this is not `_TEMPLATE.md` filled in. There is no Outcome, Demo or Maestro
> section: an `AUD-NNN` ships a *measurement*, not a capability
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), and the audit
> replaces the device flow — nothing on a screen changes. What it does produce: what the references
> gate for themselves, what this repo gates, the diff with its rationale or its absence, and a
> soundness read of the harness itself — does each gate fail closed, is it tested, and does anything
> actually run it.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, the repaired §11 entries and the code
> comments — nothing else ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **the repo's own health machinery**: every rule the Working Agreement and the
architecture docs state, and what keeps each one true — taken through four questions at once, never
four sweeps: what the references gate for themselves, what we gate, where we differ and *why*, and
whether each gate of ours is sound — does it fail closed, does it carry its own tests, and is it
wired anywhere a commit or a step actually runs.

The row's order is load-bearing — *"the two gate gaps first, then the measured tail"*. The audit's
snapshot must describe the harness as it ships, so the repairs land **before** the sweep: `GAP-006`
is three dependency rules held only by convention, `GAP-008` a source set the local gate never
compiles. The tail is the claims map: every rule classified **enforced** (a gate names it),
**produced** (a command answers it), or **convention** (nothing keeps it true) — and the parity read
both ways, what mature projects gate that we do not and the bespoke gates no reference has.

The area at three scales:

- **The rules.** AGENTS.md — the Working Agreement's seven numbered rules plus the code-style,
  comments, dependencies, structure, formatting and logging blocks — and
  `docs/architecture/building-blocks.md` §5.1–5.6: the layer rules, the per-package placement test,
  the ports table's "every port gets a `Fake…`", and the §5.4 dependency-rules table that names its
  own enforcement status.
- **The harness that carries them.** `.pre-commit-config.yaml` — the whole commit-time gate, since
  there is no `.github/` — plus `scripts/check-file-line-length.sh`, the ten `check_*.py` gates,
  the `read_*`/`resolve_*`/`shard_*` measures, the three comment scanners (`kotlin_source.py`,
  `script_source.py`, `xml_source.py`), every one with a `_test.py` the `maestro-gate-tests` hook
  runs unconditionally; `app/src/test/.../architecture/ArchitectureGuardTest.kt`; the detekt
  (`config/detekt/detekt.yml`, 2.0.0-alpha line — JDK 25 compatibility, AGENTS.md), ktlint (14.x
  plugin, named source sets per ADR-0016) and `lintDebug` wiring; the documented local command in
  AGENTS.md. The driver-bound arms (`check_chrome_parts`, `check_node_fill`) live inside their
  flows by necessity — a device frame is their input, so no commit hook could hold them.
- **What the references gate for themselves.** Real machinery this time:
  `vscode@3f3380c668d07649b953eb8278354b2f263ea6ff` ships `build/checker/layersChecker.ts` — the
  `src/vs/` layer split enforced by a TS-API checker over `tsconfig.<layer>.json` boundaries plus a
  `RULES` table of disallowed types — `build/hygiene.ts` (copyright header, indentation and unicode
  filters, the `.eslint-allowed-javascript-files` allowlist, `checkCopilotEnginesVersion`), and
  `build/lib/checkCyclicDependencies.ts`, with `build/lib/test/layersChecker.test.ts` and
  `hygiene.test.ts` testing the gates themselves; `jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
  ships `lint` = prettier + eslint (a second *typed* config) + stylelint + shellcheck and ~28
  workflows — license-header, check-i18n, check-external-links, spell-check, zizmor,
  galata-flakiness-report; `intellij-community@406a27a8da071c59b052719fe6f1208c613e96b5` runs
  Qodana (`.github/workflows/code_quality.yml`) and its JPS `.iml`/Bazel module model — the module
  graph *is* the dependency gate there; `termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4`
  runs `./gradlew test` and a debug build; `vscode-jupyter` a `checkDependencies` script plus
  codeql/build-test; **`winlator` ships no `.github/workflows` at all**; the shipped rootfs carries
  no health machinery — it is the artifact the gates watch, not a watcher.

**Found while scoping — measured, not guessed.** Four facts that shape the repairs and the tail:

- §5.4's "`ui` never imports `data` — true today" is **already false**, and deliberately so:
  eight `ui/` files carry 16 `import com.jupydroid.app.data.fake.*` lines — the DI-default
  mechanism §5.6 prescribes (`kernelSessions: KernelSessions = FakeKernelSessions()`). Zero `ui`
  files name `data.disk`/`data.space`/`data.intent`. The gate guards the rule that actually holds —
  *`ui` names no real adapter* — and §5.4 and `GAP-006` are corrected to state it, rather than
  eight files refactored to satisfy a rule nobody meant.
- `glob/` is absent from `ArchitectureGuardTest.documentAndLanguageRoots`: §5.2 names six
  document-and-language packages (`glob`, `html`, `import`, `markdown`, `syntax`, `terminal`) and
  the guard covers five. The fix is the list, not a new test.
- `compileDebugAndroidTestKotlin` appears nowhere in the tree — the documented command and every
  hook stop short of the `androidTest` source set, exactly as `GAP-008` measured.
- There is no CI at all: the pre-commit hooks are the entire commit-time gate, `assembleDebug` is
  deliberately step-level (evidence gate 3), and `pre-commit install` is opt-in by the framework's
  design. That tiering is a design fact the audit records, not a hole this slice fills.

**Carried in.** The *behaviour* the gates protect is every earlier slice's. The gates' *inputs* —
the `.maestro` flows the driver-bound arms read, the rootfs artifacts the seccomp/shebang gates
watch (`AUD-010`), `evidence/`'s content rules — are those slices' ground. What a comment may cite
is ADR-0001/0066 policy, verdicted here as a *gate* (does the gate catch, is it tested), never
re-litigated as a rule.

## The surface, produced

No command partition applies — the area is the harness itself, not a reference's `x:y` command
surface (the AUD-007 Termux / AUD-010 Winlator precedent: prose + reproduction greps, and a new
`read_*` instrument only where a count needs asserting). Everything below reproduces from a clone:

```sh
# the commit gate, and what each hook runs
grep -n "id:\|entry:" .pre-commit-config.yaml
# every gate, scanner and measure — and whether each is wired somewhere that runs it
ls scripts/check_*.py scripts/read_*_test.py scripts/*_source.py
grep -rn "scripts/check_" scripts/*-driver.sh .pre-commit-config.yaml
# each gate fails closed on purpose — proven by its own suite
ls scripts/*_test.py
# the documented local gate, and the source set it never compiles
grep -n "gradlew" AGENTS.md
grep -rn "compileDebugAndroidTestKotlin" . AGENTS.md .pre-commit-config.yaml  # today: zero hits
# the dependency rules as they actually hold
grep -rn "^import com.jupydroid.app.data" app/src/main/java/com/jupydroid/app/ui/
grep -rln "com\.jupydroid\.app\.data\.\(disk\|space\|intent\)" app/src/main/java/com/jupydroid/app/
grep -rn "^import com.jupydroid.app.\(ui\|di\)" app/src/main/java/com/jupydroid/app/data/
# the produced counts building-blocks.md keeps un-stored (§5.2, §5.3)
for p in glob html import markdown syntax terminal theme richtext; do
  d=app/src/main/java/com/jupydroid/app/$p
  printf '%-10s %s of %s import android.*/androidx.*\n' "$p" \
    "$(grep -rlE '^import (android|androidx)\.' $d | wc -l)" "$(ls $d/*.kt | wc -l)"
done
diff <(grep -lE '^(interface|fun interface) ' app/src/main/java/com/jupydroid/app/domain/port/*.kt \
         | xargs -n1 basename | sed 's/\.kt$//' | sort) \
     <(ls app/src/main/java/com/jupydroid/app/data/fake/Fake*.kt \
         | xargs -n1 basename | sed 's/^Fake//;s/\.kt$//' | sort)
# every rule whose status is only words — the audit's raw material
grep -n "convention\|unguarded\|true today" docs/architecture/building-blocks.md AGENTS.md
```

## Who else implements this

| Reference | Gates itself? | The health machinery it owns |
|---|---|---|
| `jupyterlab` | full | `lint` composite (prettier + eslint + typed eslint + stylelint + shellcheck), `buildutils/lib/prepublish-check.js`, ~28 workflows: license-header, check-i18n, check-external-links, spell-check, zizmor (action security), galata flakiness tracking |
| `vscode` | full | `build/checker/layersChecker.ts` + `tsconfig.<layer>.json` boundaries (the layer gate), `build/hygiene.ts` (copyright, indentation, unicode, JS allowlist, engine parity), `checkCyclicDependencies.ts`, eslint/stylelint/formatter, `build/lib/test/` testing the gates |
| `vscode-jupyter` | partial | `checkDependencies` script, eslint, codeql + build-test workflows |
| `intellij-community` | full | Qodana (`code_quality.yml`); the JPS `.iml` module graph as the dependency gate — a module cannot reference what it does not declare; `jewel-checks.yml` |
| `termux-app` | partial | `run_tests.yml` (`./gradlew test`), `debug_build.yml`, gradle-wrapper-validation |
| `winlator` | absent | no `.github/` at all |
| `jediterm` | partial | `test.yml` |
| the shipped rootfs | n/a | it is the artifact the gates watch |

That is **3 of 7** shipping real machinery — the pattern the references share: the layer/dependency
gate exists *because the codebase is too big for review to hold it* (VS Code's checker, IntelliJ's
module model), and every gate is cheap enough to run per commit (hygiene is a filter stream, not a
build). Ours is the same shape at one module's scale: a Kotlin guard test instead of a TS-API
checker, Python gates instead of gulp filters, pre-commit instead of a pipeline.

## Implementation inventory

### `vscode` @ `3f3380c668d07649b953eb8278354b2f263ea6ff`

| Concern | Files |
|---|---|
| Layer/dependency gate | `build/checker/layersChecker.ts` (`RULES` table, `tsconfig.<layer>.json` per-layer type-check via `layersTypeCheck.ts`) |
| Hygiene filters | `build/hygiene.ts`, `build/filters.ts` (copyright, indentation, unicode, ts-formatting, stylelint, eslint), `gulpfile.hygiene.ts` |
| Repo-shape rules | `build/hygiene.ts` `checkNoNewJavaScriptFiles` (allowlist + `git ls-files`), `checkCopilotEnginesVersion`; `build/lib/checkCyclicDependencies.ts` |
| The gates' own tests | `build/lib/test/layersChecker.test.ts`, `build/lib/test/hygiene.test.ts` |
| Formatter/linter wiring | `build/lib/formatter.ts`, `build/eslint.ts`, `build/gulp-eslint.ts`, `build/stylelint.ts` |

The transferable idea: the layer gate is a *rules table* with per-target disallowed types, and its
tests plant violations to prove the checker catches (`layersChecker.test.ts` feeds synthetic
sources). Ours asserts on the real tree and proves red differently — a planted violation,
uncommitted, recorded in the commit body.

### `jupyterlab` @ `7f0e690680a763d3c3db5dc0231803b0ba0e1e12`

| Concern | Files |
|---|---|
| Lint composite | root `package.json` `lint`/`lint:check` scripts — prettier, eslint, `eslint.config.typecheck.mjs`, stylelint, shellcheck |
| Repo-integrity checks | `buildutils/lib/prepublish-check.js`, `check-release.yml`, `reject-staging-checks.yml` |
| File-shape gates | `license-header.yml`, `check-i18n.yml`, `check-external-links.yml`, `spell-check.yml`, `zizmor.yml` |

### `intellij-community` @ `406a27a8da071c59b052719fe6f1208c613e96b5`

| Concern | Files |
|---|---|
| Static analysis | `.github/workflows/code_quality.yml` (Qodana, scheduled) |
| Dependency gate | the `.iml` module graph + `build/jpsModelToBazel.cmd` — a module references only what it declares, enforced by the build model rather than a checker |

## Gap analysis — ours today

The rule table, read against the tree. **enforced** = a gate fails without it; **produced** = a
command answers it and nothing gates the answer; **convention** = neither.

| Rule | Where stated | Enforced by | State |
|---|---|---|---|
| `domain` imports no `android.*` | §5.4 | `ArchitectureGuardTest.domainLayer_hasSources_withZeroAndroidImports` | enforced |
| `domain` imports no `data`/`ui`/`di` | §5.4 | `ArchitectureGuardTest.domainLayer_doesNotImportOtherLayers` | enforced |
| doc/language packages android-free | §5.2, AGENTS.md | `documentAndLanguageModules_*` — **minus `glob/`** | enforced, one hole |
| `ui` names no real adapter | §5.4 (as "ui ↛ data") | nothing | unguarded — and mis-stated: `data.fake` is the DI default |
| `data` names no `ui`/`di` | §5.4 (as "data ↛ ui") | nothing | unguarded, true at zero |
| real adapters built only at the composition root | §5.4 | nothing | unguarded, true: `data/`, `di/AppContainer.kt`, `JupydroidApplication.kt` only |
| `androidTest` compiles in the local gate | GAP-008 | nothing — `compileDebugAndroidTestKotlin` named nowhere | unguarded |
| files ≤ 500, functions 4–20, line ≤ 120, naming | AGENTS.md r.6 | `check-file-line-length.sh` + detekt `LargeClass`/`LongMethod`/`CyclomaticComplexMethod` + ktlint defaults | enforced |
| formatters/lint wired into build | AGENTS.md r.5 | gradle plugin wiring + the ktlint/detekt/lint hooks | enforced |
| hooks gate every commit | AGENTS.md r.7 | `.pre-commit-config.yaml` — **opt-in**: `pre-commit install` is manual, no CI backstop | enforced where installed |
| every `check_*`/`read_*`/scanner carries `_test.py` | AGENTS.md tooling table | `maestro-gate-tests` hook runs all of them, `always_run` | enforced |
| comments cite what resolves | docs/README.md | `check_doc_citations.py` + `check_retired_doc_citations.py` | enforced |
| evidence names the app, not the house | AGENTS.md r.2 | `check_device_secrets.py` + `.gitignore` logcat rules | enforced |
| flows observe kernel output | AGENTS.md | `check_maestro_output_assertions.py` | enforced |
| a gap has a name / one monospace home / palette entries draw / flows start seeded / seccomp / shebang-exec | AGENTS.md tooling table | `check_ui_dimensions`, `check_monospace_home`, `check_palette_consumers`, `check_driver_isolation`, `check_busybox_seccomp`, `check_shebang_exec` | enforced |
| port ↔ `Fake…` 1:1 | §5.3 | the `diff` command — produced | produced, ungated |
| android-free per-package counts | §5.2 | the `for` loop — produced | produced, ungated (and it counts `glob`, which the guard test misses) |
| "max 2 levels of indentation", "no `any`", "early returns", "exception messages name the value" | AGENTS.md code style | nothing gates prose-level style; detekt's default `NestedBlockDepth` allows 4, not 2 | convention |
| the gates' inputs stay honest (flow↔driver↔evidence naming) | ADR-0001 | `check_retired_doc_citations.py` covers doc names; the step-code lockstep is convention | mixed |

## Deviations

- **`data.fake` in `ui/` is not a violation — it is the mechanism.** §5.6 prescribes constructor
  injection defaulting to fakes, and `AppContainer`'s own defaults are the same call. The §5.4 rule
  was written broader than its intent; the audit restates the rule rather than refactoring eight
  files to satisfy wording nobody enforced.
- **No CI is a finding, not a repair this slice makes.** The references all assume a pipeline; this
  repo gates at commit-time locally and at step-time on a device. Whether that absence is `absent`,
  `adapted` or a `GAP` is the tail's verdict.
- **The gates test themselves, not the build that runs them.** Every `check_*` proves it fails
  closed via `_test.py`; `ArchitectureGuardTest`'s new arms prove red by a planted violation during
  the session, recorded in the commit body — the `layersChecker.test.ts` idea at repo scale.

## Open decisions

- Does the composition-root rule bind **references** (import or qualified name) or strictly
  **construction**? This PRD takes references — an inline
  `com.jupydroid.app.data.disk.DiskNotebookRepository(` must trip it — and T1 verifies no KDoc or
  comment false-positives on the scan.
- Does `data ↛ di` join `data ↛ ui` in the same test? Same inward-dependency shape, held at zero
  today; bundled unless the walk finds a legitimate use.
- What does a *repaired* GAP's §11 entry look like — there is no precedent (AUD-002's GAP-005 was
  re-measured, not repaired). The entries must stay true; the form (restated-in-place vs.
  annotated) is T1/T2's call, and GAP-007's "parked, not closed" shows entries already carry state
  in prose.

## Tasks

- [x] **T1 — `GAP-006`: the three dependency rules gated.** `ArchitectureGuardTest` gains the rules
  in their true shape — `ui` names no `data.disk`/`space`/`intent` (a `data.fake` default stays
  legal), `data` names no `ui`/`di`, and `data.{disk,space,intent}` is named only inside `data/`,
  `di/` and `JupydroidApplication.kt`, scanned for the qualified reference rather than `import`
  lines alone. `glob` joins `documentAndLanguageRoots`. §5.4's table moves three rows to
  **enforced** with the corrected statements; the `GAP-006` entry is restated so §11 stays true.
  Red-proof: a planted violation, watched fail, reverted — the proof goes in the commit body.
- [x] **T2 — `GAP-008`: the local gate compiles `androidTest`.** `compileDebugAndroidTestKotlin`
  joins the AGENTS.md command and the `unit-tests` hook entry (one gradle call, seconds); the
  `GAP-008` entry and the `decisions-triage.md` row citing it are restated.
- [x] **T3 — the surface, produced and opened.** The `## Code health — AUD-011` ledger section
  lands: the claims map from §"Gap analysis" re-run, the produced commands, the reference table.
  Areas row added, `unexamined`; no verdicts yet.
- [ ] **T4 — the verdicts.** Every claim gets a verdict; every gate gets its soundness read (fails
  closed? tested? wired?). Reference parity rows both directions — what they gate that we do not
  (license headers, cyclic deps, i18n/link checks, CI itself) and the bespoke gates no reference
  has (doc citations, device secrets, maestro output assertions, driver isolation). `undocumented`
  → ADR; `absent` → `GAP-NNN`; `defect` → red-first fix in-slice.
- [ ] **T5 — the walk and the close.** The end-to-end read: a commit passes through the hooks, the
  hooks through their tests, the step through the documented gate. Areas row → `examined`; the PRD
  is deleted per ADR-0001; the backlog row ships and the marker moves to `JD-001`.

## Out of scope

- Building whatever the tail finds — each repair is a `JD-NNN` with its own session, PRD and demo;
  this slice fixes only `defect`s, red first.
- Adopting CI, licensing headers, cyclic-dependency machinery — findings to verdict, not builds.
- Rewriting the Working Agreement's rules — the audit states what enforces them; changing the rules
  is a different act.
- `test/` and `androidTest/` sources constructing adapters freely — the composition-root rule
  binds `src/main` only; test code building `Disk*` over temp dirs is the contract-test mechanism.
