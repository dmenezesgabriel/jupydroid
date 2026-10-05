# Working agreement

**A clean session starts at [`prompt-execution.md`](prompt-execution.md)** — it is the procedure, in
order. [`docs/README.md`](docs/README.md) is the map of the documents, and carries the id scheme, the
greps and the rule for what a code comment may cite. This file is *how work is done*: style, the
mandatory gates, and the tooling that enforces them.

`CLAUDE.md` is one line pointing here, so a Claude session and any other agent read the same
agreement rather than two that drift.

## Code style

- Functions: 4-20 lines. Split if longer.
- Files: under 500 lines. Split by responsibility.
- One thing per function, one responsibility per module (SRP).
- Names: specific and unique. Avoid `data`, `handler`, `Manager`.
  Prefer names that return <5 grep hits in the codebase.
- Types: explicit. No `any`, no `Dict`, no untyped functions.
- Type aliases that narrow a generic to a component-specific concrete type use
  the `Typed` prefix (e.g. `TypedChatGraph`), following Python's `TypedDict`
  convention. If an alias is shared across more than one module inside the same
  component, it lives in `<component>/infrastructure/types.py` and is imported
  from there — never redefined locally.
- No code duplication. Extract shared logic into a function/module.
- Early returns over nested ifs. Max 2 levels of indentation.
- Exception messages must include the offending value and expected shape.

## Comments

- Keep your own comments. Don't strip them on refactor — they carry
  intent and provenance.
- Write WHY, not WHAT. Skip `// increment counter` above `i++`.
- Docstrings on public functions: intent + one usage example, use google conventions.
- Reference issue numbers / commit SHAs when a line exists because
  of a specific bug or upstream constraint.

## Dependencies

- Inject dependencies through constructor/parameter, not global/import.
- Wrap third-party libs behind a thin interface owned by this project.

## Structure

- Prefer small focused modules over god files.
- **One home per kind of logic** (the greppable test), and `docs/architecture/building-blocks.md` is
  the full contract for what lives where:
  - **Domain** (`domain/model/`, `domain/port/`): the app's own types and its ports.
    Pure Kotlin — no `android.*`, no import of `data`/`ui`/`di`. Enforced by
    `ArchitectureGuardTest`, not by review.
  - **Adapter** (`data/<mechanism>/`): implements a port; frameworks live here.
    Name it `<Mechanism><PortConcept>` — the mechanism names the tech it wraps
    (`DiskNotebookRepository`, `DiskWorkspaceRepository`, `JupyterServerLauncher`,
    `OkHttpKernelSocketOpener`, `FakeKernelGateway`) — and subclass the port explicitly,
    so grepping the port name lands on its implementation.
  - **UI** (`ui/<screen>/`): screens and ViewModels. Reads ports; never constructs an
    adapter.
  - **Composition root** (`di/AppContainer`): the only place that builds implementations,
    constructed once per *process*, in `JupydroidApplication` - the activity is relaunched on
    every rotation, and a container per activity was a `jupyter_server` per activity
    (`evidence/p1g-one-container-per-process/`). Work that must outlive a screen goes on its
    `applicationScope`, never `viewModelScope`.
  - **Document and language logic** (`syntax/`, `markdown/`, `html/`, `import/`): parsers
    and formats. Not domain — they model documents and languages, not this app's domain —
    and not adapters, because they implement no port. Keep them free of `android.*` so
    every test for them is a plain JVM test.
  - **Compose-only** (`theme/`, and the rendering half of `richtext/`): design tokens and
    the composables that draw a model. Where a model and its renderer share a package,
    keep them in separate files so the model stays testable without Compose.
- Prefer a uniform port surface over case-by-case minimalism. Keep a
  single-implementation port when it keeps every boundary and operation discoverable in
  one place (`domain/port/`) — that consistency is the map a newcomer navigates by, not
  ceremony. Don't strip an abstraction just because it has one implementation today.
- Every port gets a `Fake…` in `data/fake/`, which is also what `AppContainer` defaults
  to: tests get one for free, and a device that cannot run a Space still runs the UI.

## Formatting

- Use the language default formatter (`cargo fmt`, `gofmt`, `prettier`,
  `ruff`, `rubocop -A`). Don't discuss style beyond that.

## Logging

- Structured JSON when logging for debugging / observability.
- Plain text only for user-facing CLI output.

## Working Agreement (mandatory, non-negotiable)

These three rules gate EVERY piece of work. There are no exceptions and nothing
is skipped "for speed". A step is only finished when all of them hold.

### 1. TDD is mandatory for every new or modified line of code
- Every feature/bugfix begins with a **failing test** (red), then the minimal
  implementation (green), then refactor.
- Applies to all layers: domain, ports, ViewModels, UI, adapters, bridges.
- No "I'll add tests later", no untested code merged, no exceptions for
  "obvious" changes.

### 2. Manual-interaction verification (Maestro, playwright-analogue) is mandatory at the end of every phase
- After a step's code is done and all tests pass, drive the **real app on a real
  device** with Maestro CLI flows: launch, tap, type, navigate.
- **Which device is not a free choice.** Anything that runs a cell must go on the
  **arm64 tablet** (`SM-X216B`): only arm64 ships a rootfs, so on the x86_64 `dev36`
  emulator `MainActivity` falls back to `FakeKernelGateway`, which echoes the cell's
  source back as its output. A flow that runs a cell there proves nothing about the
  kernel and can go green while the kernel has never started. The emulator is fine
  for anything that does not run a cell.
- Capture **screenshots, snapshots and logs** of every flow into
  `evidence/<step>/` and commit them. `maestro.log` (and `logcat` where the step
  touches a process or the runtime) counts as evidence, not just PNGs.
- **`logcat` is captured but never committed.** It is the whole device's log - home
  Wi-Fi BSSID/SSID, push tokens, other apps - so `.gitignore` keeps it local and the
  `device-secrets` gate refuses it. A README quotes the lines it cites.
- **Look at the screenshots.** More than one flow in this repo's history has gone
  green against the wrong thing; a green log is only half the evidence.
- Unit tests alone are never sufficient to close a step.
- **One exception, and it is named:** an `AUD-NNN` audit item ships a measurement rather than a
  screen, so it runs no flow and closes on the command that reproduces what it measured
  ([ADR-0067](docs/adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)). An audit
  task that does touch a screen owes the flow like any other.

### 2b. Instrumented tests exist as of Step 4b
- `app/src/androidTest` was empty until the cell menu, though the Compose test
  dependencies had been declared since Step 1. `CellHeaderTest` is the first, and
  earns it: whether there is something *visible to press* is not a question any
  JVM test can answer.
- Run with `./gradlew :app:connectedDebugAndroidTest`; the emulator is enough,
  since UI needs no Space runtime.

### 3. Evidence-gated progression
- A step is NOT complete — and the next step does NOT start — until:
  1. Unit + instrumented tests are green (`red → green → refactor`).
  2. Maestro flow passes on the device rule 2 names, with screenshots/snapshots in
     `evidence/<step>/`.
  3. `./gradlew :app:assembleDebug` builds green.
  4. Evidence committed.
- No proceeding on faith or "it looked right in code".

### 4. No overengineering, no workarounds
- Minimum code that solves the problem. No speculative features, no unrequested
  abstractions, no premature optimization.
- No hacks to force a test or build green. If a simpler approach exists, take it
  and say so.
- Touch only what the task requires; leave pre-existing code alone unless asked.

### 5. Formatting and linting are mandatory (automatic)
- Wired into the Gradle build so they cannot be skipped:
  - **ktlint** — formatter + checks (`ktlintFormat` auto-fixes, `ktlintCheck`
    gates). Configured via `.editorconfig` (max line 120).
  - **detekt** — static analysis from `config/detekt/detekt.yml`.
  - **Android Lint** — `./gradlew :app:lintDebug`.

### 6. File size and naming discipline (detekt-enforced)
- Files ≤ 500 lines (pre-commit gate + detekt `LargeClass.thresholdInFiles`).
- Functions 4–20 lines, classes ≤ 500 lines (detekt `LongMethod`, `LargeClass`).
  `@Composable` functions are exempt from `LongMethod` (they wire state +
  child composables and routinely exceed 20 lines; see `config/detekt/detekt.yml`).
- Idiomatic, specific, discoverable names for files/classes/vars/functions
  (detekt `NamingRules` + review); no `data`/`handler`/`Manager`-style names.
- Max line length 120.

### 7. Git hooks gate every commit (husky/pre-commit analogue)
- **pre-commit framework** with local Gradle hooks: file-length → ktlint →
  detekt → Android Lint → unit tests. Commit is blocked on any failure.
- Hooks are pure checkers (no mutation). Format locally with `ktlintFormat`.
- Install: `pip install pre-commit && pre-commit install`.
- Instrumented tests + Maestro stay step-level evidence gates (too slow to run
  per commit).

## Per-step procedure (mandatory loop)

The rules above say *what* must hold. This says *in what order* to work. Every
step runs this loop start to finish; no stage is skipped or reordered.

1. **Size the step.** Find the next item with `grep -n "← next" docs/backlog.md`, and read its
   PRD in `docs/prd/JD-NNN-*.md` — writing it from `docs/prd/_TEMPLATE.md` if it has none. If a task is
   too big to close in one functional commit, split it in the PRD *before* writing any code.
2. **Research before acting.** Read the relevant ADRs (`grep -rn "ADR-0022" docs/ app/src/`), the
   reference codebases and the web. `docs/architecture/cross-cutting.md` §8.3 says which reference is
   authoritative for which question; `docs/research/closed-questions.md` says what is already closed.
   Decisions are made on evidence — never on guesswork or assumption.
3. **TDD.** Failing test first, then the minimal implementation, then refactor
   (rule 1).
4. **Interact with the real app.** Maestro flows on the device rule 2 names:
   screenshots, snapshots and logs into `evidence/<step>/`.
5. **Commit semantically and atomically.** One coherent change per commit,
   conventional-commit subject. No complex git, no reverts, no rewriting
   history (the one exception, before the first push, is `docs/adr/0015-*`).
   Every commit is fully functional — never broken mid-series. Gate results go in the **commit
   body**, never in a doc.
6. **Refactor rather than patch.** Band-aids layered on legacy code are worse
   than a clean rewrite. Favour clarity, idiom and discoverability; renaming or
   restructuring is preferred over working around a bad shape.
7. **Close the documents.** A comment states its fact so it stands alone, then cites an `ADR-NNNN`, a
   path in `app/src/`, a path inside the shipped rootfs, an upstream project at a **pinned SHA**, a
   spec or issue URL, or a test name — never a step code, `docs/backlog.md`, a `JD-NNN`,
   `evidence/<step>/` or `reference/…` (`scripts/check_doc_citations.py` is the gate). A comment that
   already carries one of those **loses it** rather than trading it for another id; only a line
   that exists because of a specific defect names the change, as `Fixes: <12-hex> ("<subject>")`
   (`docs/adr/0066-*`). A non-trivial decision becomes an ADR. **When the item ships, delete its PRD** — see `docs/adr/0001-*`.

## Tooling

| Tool | Role | Config |
|---|---|---|
| ktlint | formatter + check | `.editorconfig` (`org.jlleitschuh.gradle.ktlint` **14.x**; 12.x registers no source-set task under AGP 9) |
| detekt | static analysis | `config/detekt/detekt.yml` |
| Android Lint | Android-specific checks | AGP built-in (`:app:lintDebug`) |
| pre-commit | commit gate | `.pre-commit-config.yaml` |
| file-length gate | ≤500 lines/file | `scripts/check-file-line-length.sh` |
| maestro-output gate | a post-Run wait must need a kernel | `scripts/check_maestro_output_assertions.py` (+ `_test.py`) |
| system-ANR clear | a stuck *system* dialog fails no flow but its own | `scripts/clear_system_anr.py` + `scripts/system_anr_dialog.py` (+ `_test.py`) |
| device-secrets gate | evidence proves the app, not the house it ran in | `scripts/check_device_secrets.py` (+ `_test.py`); local literals in gitignored `.sensitive-strings.local` |
| monospace-home gate | what code looks like has one home | `scripts/check_monospace_home.py` (+ `_test.py`) |
| doc-citations gate | a comment cites what still resolves in a fresh clone | `scripts/check_doc_citations.py` (+ `_test.py`); no allowlist, every file under `app/src/`+`tools/` read, an unknown type fails |
| retired-doc gate | no file names a document ADR-0001 deleted | `scripts/check_retired_doc_citations.py` (+ `_test.py`); the three names, and the eight files whose job is to hold them |
| comment scanners | one prose reader per comment syntax, shared by the gates | `scripts/kotlin_source.py` (`//`, `/* */`), `script_source.py` (`#`, docstrings), `xml_source.py` (`<!-- -->`) (+ `_test.py` each) |
| busybox-seccomp gate | the shell asks Android for no syscall it refuses apps | `scripts/check_busybox_seccomp.py` (+ `_test.py`) |
| palette-consumers gate | a palette states colours something draws | `scripts/check_palette_consumers.py` (+ `_test.py`) |
| pane-width measure | what each column of the shell gets, on device | `scripts/read_pane_widths.py` (+ `_test.py`) — a measurement, not a gate |
| vscode-theme resolve | what colour a VS Code role *actually* is, after its `include` chain | `scripts/resolve_vscode_theme.py` (+ `_test.py`) — a measurement, not a gate |
| reference-surface measure | what each reference offers, enumerated with the pattern that counted it | `scripts/read_reference_commands.py` (+ `_test.py`) — a measurement, not a gate |
| port-surface measure | every port operation, and whether production code uses it | `scripts/read_port_surface.py` (+ `_test.py`) — a measurement, not a gate |
| ledger-partition measure | every reference command id is owned by exactly one audit slice | `scripts/read_ledger_partition.py` (+ `_test.py`) — a measurement, not a gate; its cross-check skips where `reference/` is absent |
| ledger-verdicts measure | every id an examined audit task owns carries exactly one verdict, and the counts beside them | `scripts/read_ledger_verdicts.py` (+ `_test.py`) — a measurement, not a gate; its cross-check skips where `reference/` is absent |

Local verification command (same as the commit gate):
```sh
JAVA_HOME=<jdk> ./gradlew \
  ktlintCheck ktlintMainSourceSetCheck ktlintTestSourceSetCheck ktlintAndroidTestSourceSetCheck \
  detekt lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug
```

**The source sets are named on purpose.** `ktlintCheck` alone is an aggregate over whatever tasks
the plugin registered, and for months it registered none for Kotlin - so it checked the `.kts`
build scripts and reported success (`docs/adr/0016-*`). Named, a plugin that stops seeing a
source set fails with "Task not found" instead of passing quietly.
`compileDebugAndroidTestKotlin` is named for the same reason: nothing else in the list reaches
`androidTest` - `assembleDebug` compiles `main`, `testDebugUnitTest` compiles `test`, and the
ktlint check is a formatter, which parses without resolving.

And `python3 -m unittest discover -s scripts -p '*_test.py'`

Notes:
- `JAVA_HOME` must point at a JDK (the Gradle hooks run inside the current
  shell env); on this machine: `/home/user/android-studio/jbr`.
- pre-commit install alternatives: `pip install pre-commit` or `uv tool install
  pre-commit`.
- detekt is pinned to the `2.0.0-alpha.x` line because stable 1.23.8 cannot run
  on JDK 25; the alpha line is detekt's official compatibility path for
  AGP 9 + JDK 25 (no stable release supports it yet).


## Architecture (locked)

- Pragmatic hexagonal + UDF/MVVM.
- Single `:app` module; manual DI via `di/AppContainer` (no DI framework).
- Domain layer: pure Kotlin, **zero `android.*` imports** — enforced by an
  architecture-guard test.
- Ports (contract-tested in Step 1): `NotebookRepository`, `KernelGateway`,
  `ServerLauncher`; `Space` modeled as a first-class domain type.
- Design-token system shared Compose ↔ WebView CSS vars (`--callisto-*`).
