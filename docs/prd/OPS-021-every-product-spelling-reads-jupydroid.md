# OPS-021 — Every spelling of the product's old name reads Jupydroid

Milestone: `M10` · State: open · Depends on: — · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Every place that names the product — Kotlin identifiers, the Android theme resource, the Gradle
project name, the docs site's `site_name`, README and docs prose — says `Jupydroid`, verified by a
grep that finds no `JupyterDroid` left and by the deployed Pages site serving the new `site_name`.

## Demo

An OPS item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): no
screen changes — the app label has read `Jupydroid` since `8cf3e3d58` — so the demo is a run anyone
can repeat, not a Maestro script.

1. `git grep -in "jupyterdroid" -- . ':!docs/backlog.md'` answers nothing — the case-insensitive
   pattern covers `JupyterDroid`, `jupyterdroid` and every casing between; `docs/backlog.md` is the
   one exclusion, its OPS-021 row keeping the old spelling as the record of what was renamed.
2. `gh run list --workflow=docs.yml --branch=main` shows the `deploy` job `success` on the ship
   merge's push — the repository is public, so the job is live.
3. `curl -fsS https://dmenezesgabriel.github.io/jupydroid/ | grep -o "Jupydroid"` answers matches
   and `grep -o "JupyterDroid"` answers none — `site_name` feeds the page title and nav brand on
   every page, `pages/index.md`'s H1 and `pages/files.md`'s "Open with" line having moved with it.

## Who else implements this

It is this product's own name: no reference can carry it, so every row is ABSENT for want of the
spelling existing anywhere but here — the same shape OPS-019's hosted-state table took.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | — | upstream project, not our product name |
| `vscode@3f3380c668d0` | ABSENT | — | same |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | — | same |
| `intellij-community@406a27a8da07` | ABSENT | — | same |
| `termux-app@3df69d1da197` | ABSENT | — | same |
| `winlator@fb66541b93a4` | ABSENT | — | same |
| the shipped rootfs | ABSENT | — | not a repository with our product name |

**Count:** 0 of 7. The decision is measured against our own tree — the greps above are the
evidence, not a reference.

## Implementation inventory

Measured 2026-11-22: `git grep -l "JupyterDroid"` → 162 tracked files, `git grep -l "jupyterdroid"`
→ 6. Everything outside `app/src/androidTest` and `app/src/test` is 17 files:

| File | What it carries | Becomes |
|---|---|---|
| `app/src/main/java/com/jupydroid/app/theme/Theme.kt` | `fun JupyterDroidTheme` + its KDoc example | `JupydroidTheme` |
| `app/src/main/java/com/jupydroid/app/MainActivity.kt` | the import and the one call site | `JupydroidTheme` |
| `app/src/androidTest/**` (~145 files) | `JupyterDroidTheme { … }` wrappers, incl. `theme/FocusRingEdgeProbe.kt` | `JupydroidTheme` |
| `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml` | `<style name="Theme.JupyterDroid">` | `Theme.Jupydroid` |
| `app/src/main/AndroidManifest.xml` | `android:theme="@style/Theme.JupyterDroid"` | `Theme.Jupydroid` |
| `app/src/main/java/com/jupydroid/app/data/disk/NotebookFile.kt` | `data class JupyterDroidMeta` | `JupydroidMeta` |
| `app/src/main/java/com/jupydroid/app/data/space/RealKernelDebugger.kt` | DAP `initialize` arg `clientName: "JupyterDroid"` (`clientID` already `"jupydroid"`) | `"Jupydroid"` |
| `app/src/main/java/com/jupydroid/app/ui/terminal/TerminalPaneScreen.kt` | a comment naming `JupyterDroidTheme` | `JupydroidTheme` |
| `app/src/main/java/com/jupydroid/app/ui/editor/NotebookVirtualDocument.kt` | the LSP virtual URI `/jupyterdroid/notebooks/$notebookId.py` | `/jupydroid/` |
| `app/src/test/**` (5 files: `EditorCellActionTest`, `NotebookDefinitionTest`, `NotebookLanguageDocumentTest`, `NotebookDiagnosticsTest`, `LspRenameTest`) | `file:///jupyterdroid/…` URI literals | `/jupydroid/` |
| `settings.gradle.kts` | `rootProject.name = "JupyterDroid"` — display name only; no consumer reads it (`git grep rootProject.name` finds only the assignment), and the APK's name derives from `applicationId` and the release workflow's explicit artifact names, not from it | `"Jupydroid"` |
| `README.md`, `docs-site/pages/index.md` | `# JupyterDroid` H1 | `# Jupydroid` |
| `docs-site/pages/files.md` | "Open with JupyterDroid" — already wrong: `@string/app_name` has read `Jupydroid` since `8cf3e3d58` | `Jupydroid` |
| `docs-site/mkdocs.yml` | `site_name: JupyterDroid` — feeds every page's title and the nav brand; the live site serves it today (fetched 2026-11-22) | `Jupydroid` |
| `docs/architecture.md` | title, one prose line, and the §3 ASCII diagram's `│ JupyterDroid (Compose)│` box — `Jupydroid` is three characters shorter, so the box padding is redrawn, not just substituted | `Jupydroid` |
| `docs/research/parity-ledger.md` | four `JupyterDroidTheme` mentions in verdict prose | `JupydroidTheme` |
| `docs/adr/0094-the-apps-theme-is-pushed-down-to-the-platforms-night-mode.md` | names `JupyterDroidTheme`, a living identifier the rename moves | `JupydroidTheme` |
| `docs/backlog.md` | the OPS-021 row itself | **unchanged** — the ledger's record of what was renamed; the Demo's grep excludes it |

No other casing exists: `Jupyter Droid`, `jupyter-droid`, `jupyter_droid`, `JUPYTERDROID` are all
zero hits. The GitHub repo description already reads "Jupyter on Android" and `site_url` already
reads `jupydroid`.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| The lowercase `jupyterdroid` | rename `/jupyterdroid/` in `NotebookVirtualDocument.kt` + 5 test literals; leave it | the item's literal check `JupyterDroid` would pass either way, but "everything that names the product" does not; the URI is a `file://` path pylsp sees on every `didOpen`/`didChange` | rename — symmetric (the URI is built and compared in one place, nothing parses the segment), and it lets the closing check run case-insensitive with no residue |
| `JupyterDroidMeta` | rename the class; keep it | `metadata.jupydroid`'s serialized key and the `{"title": …}` shape are what files on disk carry — kotlinx names fields, never the class — so the class rename is wire-free; keeping it leaves a `JupyterDroid` grep hit inside `app/src/main` | rename — zero format risk, verified by reading `NotebookFile.kt`: the key `jupydroid` is untouched and `ignoreUnknownKeys` reads past anything older |
| DAP `clientName` | `"Jupydroid"`; leave `"JupyterDroid"` | `clientName` is informational in the `initialize` args — JupyterLab sends `"JupyterLab"` the same way and debugpy does not dispatch on it — but it is a product spelling crossing the wire | `"Jupydroid"` — the grep must find nothing, and the value was never a protocol contract |
| ADR-0094's `JupyterDroidTheme` mention | update it; leave it | an ADR records a decision at a point in time, but this mention names a *living identifier* — after T2 the old spelling resolves to nothing | update — one word, and the ADR keeps naming the thing that exists |
| `docs/backlog.md`'s row | keep the old spelling in the capability text; reword it away | the row documents what was renamed — erasing the spelling erases the record; the ledger is edited, never appended to | keep it, and the Demo's grep names `':!docs/backlog.md'` as its one exclusion — stated, not hidden |
| A permanent spelling gate | `scripts/check_*.py` + pre-commit hook; none | a gate stops the spelling returning, but the item ships a *measurement* — "verified by a grep" — and a hook is unrequested machinery guarding a name that has now been wrong once | none — minimum code; if the spelling rots again that is a new item's measurement, not this one's scope |
| One commit or several | one atomic rename; per-area commits | `JupyterDroidTheme`'s definition and ~150 call sites must move together or nothing compiles, so the code half is one commit regardless; splitting docs from code leaves a tree where the site still says the old name | one commit for the whole rename — atomic, and `git show -w`/word-diff makes 160 mechanical files reviewable |

## Gap analysis — what we already have vs what the item needs

| Concern | Ours today (file) | Needed | Gap |
|---|---|---|---|
| Theme composable | `JupyterDroidTheme` in `app/src/main/java/com/jupydroid/app/theme/Theme.kt` | `JupydroidTheme` | present, old spelling |
| Android theme resource | `Theme.JupyterDroid` in both `themes.xml` + the manifest reference | `Theme.Jupydroid` | present, old spelling |
| Serial metadata class | `JupyterDroidMeta` in `NotebookFile.kt` | `JupydroidMeta` | present, old spelling |
| DAP client name | `"JupyterDroid"` in `RealKernelDebugger.kt` | `"Jupydroid"` | present, old spelling |
| LSP virtual URI | `/jupyterdroid/` in `NotebookVirtualDocument.kt` | `/jupydroid/` | present, old spelling |
| Gradle project name | `rootProject.name = "JupyterDroid"` | `"Jupydroid"` | present, old spelling |
| App label / `app_name` | `@string/app_name` = `Jupydroid` | — | **complete** — `8cf3e3d58` shipped it |
| Deployed site | `site_name: JupyterDroid`, live Pages title `JupyterDroid` | `Jupydroid` | present, old spelling — verified by fetch 2026-11-22 |
| Repo description / `site_url` / package | "Jupyter on Android", `jupydroid`, `com.jupydroid.app` | — | **complete** |

## Deviations

- No port, no adapter, no screen: the rename changes identifiers and strings, never behavior, so
  there is no Maestro flow ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)).
  The instrumented suite still runs — ~145 androidTest files are touched, and "does it still
  compile and pass" is a real question the emulator answers.
- Git history keeps every old spelling — `git log -S JupyterDroid` will find them forever;
  history is not rewritten (AGENTS.md's no-rewrites rule stands).
- `evidence/` gets nothing: an OPS item's reproduction is the command in the commit body
  ([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)'s tested-script
  clause applies to results someone must rerun; a grep and a curl are the rerun).

## Open decisions

None — every question the rename raises was settled by reading the files above: the serialized
key was already `jupydroid`, `clientName` is informational, `rootProject.name` has no consumer,
and the URI segment is built and compared in one file.

## Surface

| Layer | Files |
|---|---|
| `app/src/main` Kotlin | `theme/Theme.kt`, `MainActivity.kt`, `data/disk/NotebookFile.kt`, `data/space/RealKernelDebugger.kt`, `ui/terminal/TerminalPaneScreen.kt`, `ui/editor/NotebookVirtualDocument.kt` |
| `app/src/main` resources | `AndroidManifest.xml`, `res/values/themes.xml`, `res/values-night/themes.xml` |
| `app/src/androidTest` | ~145 files — `JupyterDroidTheme` wrappers |
| `app/src/test` | 5 files — `file:///jupyterdroid/` URI literals |
| build | `settings.gradle.kts` (`rootProject.name`) |
| docs | `README.md`, `docs/architecture.md`, `docs/research/parity-ledger.md`, `docs/adr/0094-*.md` |
| site | `docs-site/mkdocs.yml`, `docs-site/pages/index.md`, `docs-site/pages/files.md` |
| ledger | `docs/backlog.md` row → `shipped`, `← next` dropped (T3) |

## Tasks

- [ ] T1 — This PRD.
- [ ] T2 — The rename, one commit: `JupyterDroidTheme` → `JupydroidTheme` (definition, KDoc,
  `MainActivity`, every androidTest caller — one mechanical pass, they must move together or
  nothing compiles); `Theme.JupyterDroid` → `Theme.Jupydroid` (both `themes.xml` + manifest);
  `JupyterDroidMeta` → `JupydroidMeta`; `clientName` `"JupyterDroid"` → `"Jupydroid"`; the
  `TerminalPaneScreen.kt` comment; `/jupyterdroid/` → `/jupydroid/` in `NotebookVirtualDocument.kt`
  **test-first**: the five `app/src/test` URI literals change first and go red against the old
  producer, then the producer follows green — the one place this rename has a real red→green; the
  rest is refactor-class, compile-gated, with no new tests (asserting a renamed identifier would be
  tautological). Then `rootProject.name`, the docs (`architecture.md`'s diagram box redrawn for the
  three missing characters), ADR-0094, parity-ledger, README, and the three `docs-site` files.
  Gates: `git grep -in "jupyterdroid" -- . ':!docs/backlog.md'` empty; `./gradlew
  :app:assembleDebug` green; `./gradlew :app:testDebugUnitTest` green; `./gradlew
  :app:connectedDebugAndroidTest` green on the emulator (no cell runs — the emulator is the right
  device); ktlint/detekt/Android Lint and `mkdocs build --strict` via the commit hooks;
  `grep -o "Jupydroid" build/site/index.html` non-empty as the pre-merge site evidence. The commit
  body quotes the empty grep, the gate results, the `mkdocs` evidence, names the reproduce commands,
  and names the principles that decided it (`principle-sequence-verifiable-units` — one atomic
  unit whose check is the grep plus the suites; `principle-prove-it-works` — the deployed page and
  the built HTML are checked, not inferred).
- [ ] T3 — Ship: T2's merge push fires `docs.yml` — `gh run watch`/`gh run list` the `deploy` job
  to green, then the Demo's curl against `https://dmenezesgabriel.github.io/jupydroid/` (and one
  inner page, `files/`, whose prose moved). Delete this PRD; `docs/backlog.md`'s OPS-021 row to
  `shipped` with `← next` dropped — the last marker in the ledger, so the next session's
  `grep -n "← next"` finds nothing, which is the correct signal the ledger is empty. The commit
  body names the run and job ids, the curl output, the gate results, and the reproduce commands.

## Out of scope

Git history — every old spelling remains reachable through `git log -S`; rewriting history is
forbidden. `docs/backlog.md`'s own row — it keeps the spelling as the record. A permanent
spelling gate in `scripts/` — the item ships a measurement, not a hook. Reference clones and
`evidence/` — untracked or third-party, never part of the product's spelling. The GitHub repo
description, `site_url`, package name and `app_name` — already correct, verified above.
