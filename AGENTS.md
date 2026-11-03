# Working agreement

**A clean session starts at [`prompt-execution.md`](prompt-execution.md)** — it is the procedure, in
order. [`docs/README.md`](docs/README.md) is the map of the documents, and carries the id scheme, the
greps and the rule for what a code comment may cite. This file is *how work is done*: style, the
mandatory gates, and the tooling that enforces them.

Claude Code reads this file directly, as other agents do, so every session reads the same
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
    every rotation, and a container per activity started one `jupyter_server` per activity,
    a new server on every rotation. Work that must outlive a screen goes on its
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
  `evidence/<step>/`. `maestro.log` (and `logcat` where the step touches a process
  or the runtime) counts as evidence, not just PNGs.
- **Evidence is local and never committed**
  ([ADR-0132](docs/adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)).
  `.gitignore` keeps all of `evidence/` on the disk that ran it, since `logcat` alone is the
  whole device's log: home Wi-Fi BSSID/SSID, push tokens, other apps. The commit body names the
  flow and quotes the lines it relies on. A result someone must rerun ships as a tested script,
  and a frame a test asserts on is a fixture in `scripts/testdata/`.
- **Look at the screenshots.** More than one flow in this repo's history has gone
  green against the wrong thing; a green log is only half the evidence.
- Unit tests alone are never sufficient to close a step.
- **Two exceptions, and they are named:** an `AUD-NNN` audit item ships a measurement rather than a
  screen, so it runs no flow and closes on the command that reproduces what it measured
  ([ADR-0067](docs/adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)); an
  `OPS-NNN` item changes the repository or its delivery rather than the app, and closes on the
  command or workflow run that reproduces it
  ([ADR-0122](docs/adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)). A task of either
  kind that does touch a screen owes the flow like any other.

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
  2. Maestro flow passes on the device rule 2 names, and its screenshots/snapshots
     have been looked at.
  3. `./gradlew :app:assembleDebug` builds green.
  4. The commit body names the gate results and the command or flow that reproduces them.
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
   screenshots, snapshots and logs into the local `evidence/<step>/` (ADR-0132).
5. **Commit semantically and atomically.** One coherent change per commit,
   conventional-commit subject. No complex git, no reverts, no rewriting
   history (the one exception, while the repository is private and shared with nobody, is
   `docs/adr/0015-*` and `docs/adr/0133-*`).
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
| compiler strictness | a new Kotlin warning fails the build; `:extension-api` also states every visibility and return type | `kotlin { compilerOptions { allWarningsAsErrors } }` in each module, `explicitApi()` in `extension-api/build.gradle.kts` |
| pre-commit | commit gate | `.pre-commit-config.yaml` |
| python lint and format | ruff `E,F,I,B,UP` and `ruff format --check` over `scripts/` and `tools/`; each exception a named `per-file-ignores` entry | `pyproject.toml` `[tool.ruff]`, `tools/rootfs/ruff.toml` (py312, the image's interpreter) |
| python types | mypy `--strict` over `scripts/` sources (the `_test.py` files are out: 74 findings of Optional narrowing and unittest fakes beyond unannotated methods) and over `tools/rootfs/` as py312, its image-only imports named one by one | `pyproject.toml` `[tool.mypy]`, `tools/rootfs/mypy.ini` |
| file-length gate | ≤500 lines/file | `scripts/check-file-line-length.sh` |
| ShellCheck | every shell script, followed into what it sources | `.pre-commit-config.yaml` (`koalaman/shellcheck-precommit` v0.11.0, needs Docker) + `.shellcheckrc` |
| duplication gate | no Kotlin block of 150+ tokens is copied, tests included; PMD CPD, which Gradle's PMD plugin does not run | `:app:cpd` in `app/build.gradle.kts` (`cpdTool`, `libs.versions.toml` `pmd`) |
| maestro-output gate | a post-Run wait must need a kernel | `scripts/check_maestro_output_assertions.py` (+ `_test.py`) |
| system-ANR clear | a stuck *system* dialog fails no flow but its own | `scripts/clear_system_anr.py` + `scripts/system_anr_dialog.py` (+ `_test.py`) |
| device-secrets gate | no staged file, test fixtures included, describes the house a device run happened in | `scripts/check_device_secrets.py` (+ `_test.py`); local literals in gitignored `.sensitive-strings.local` |
| monospace-home gate | what code looks like has one home | `scripts/check_monospace_home.py` (+ `_test.py`) |
| doc-citations gate | a comment cites what still resolves in a fresh clone | `scripts/check_doc_citations.py` (+ `_test.py`); no allowlist, every file under `app/src/`+`tools/`+`scripts/`+`.maestro/`+each extension module's `src/` read (a notebook by its code cells' comments), an unknown type fails |
| retired-doc gate | no file names a document ADR-0001 deleted | `scripts/check_retired_doc_citations.py` (+ `_test.py`); the three names, and the eight files whose job is to hold them |
| evidence-citations gate | no tracked Markdown names a directory under the local `evidence/`; a placeholder or glob passes | `scripts/check_evidence_citations.py` (+ `_test.py`) |
| comment scanners | one prose reader per comment syntax, shared by the gates | `scripts/kotlin_source.py` (`//`, `/* */`), `script_source.py` (`#`, docstrings), `xml_source.py` (`<!-- -->`) (+ `_test.py` each) |
| busybox-seccomp gate | the shell asks Android for no syscall it refuses apps | `scripts/check_busybox_seccomp.py` (+ `_test.py`) |
| palette-consumers gate | a palette states colours something draws | `scripts/check_palette_consumers.py` (+ `_test.py`) |
| pane-width measure | what each column of the shell gets, on device | `scripts/read_pane_widths.py` (+ `_test.py`) — a measurement, not a gate |
| launch-frames measure | what colour each frame of a cold launch is, from screenrecord's raw frames | `scripts/read_launch_frames.py` (+ `_test.py`) — a measurement, not a gate |
| import-profile measure | what importing one module costs, self time by top-level package, from `-X importtime` | `scripts/read_import_profile.py` (+ `_test.py`) — a measurement, not a gate |
| vscode-theme resolve | what colour a VS Code role *actually* is, after its `include` chain | `scripts/resolve_vscode_theme.py` (+ `_test.py`) — a measurement, not a gate |
| reference-surface measure | what each reference offers, enumerated with the pattern that counted it | `scripts/read_reference_commands.py` (+ `_test.py`) — a measurement, not a gate |
| port-surface measure | every port operation, and whether production code uses it | `scripts/read_port_surface.py` (+ `_test.py`) — a measurement, not a gate |
| transcribed-upstreams measure | which cited upstreams a file transcribes rather than studied; exits 1 only for a clean-room project marked transcribed | `scripts/read_transcribed_upstreams.py` (+ `_test.py`) — a measurement, not a gate |
| ledger-partition measure | every reference command id is owned by exactly one audit slice | `scripts/read_ledger_partition.py` (+ `_test.py`) — a measurement, not a gate; its cross-check skips where `reference/` is absent |
| ledger-verdicts measure | every id an examined audit task owns carries exactly one verdict, and the counts beside them | `scripts/read_ledger_verdicts.py` (+ `_test.py`) — a measurement, not a gate; its cross-check skips where `reference/` is absent |

Local verification command (same as the commit gate):
```sh
JAVA_HOME=<jdk> ./gradlew \
  ktlintCheck ktlintMainSourceSetCheck ktlintTestSourceSetCheck ktlintAndroidTestSourceSetCheck \
  :extension-api:ktlintMainSourceSetCheck :extension-api:ktlintTestSourceSetCheck \
  :extensions:git:ktlintMainSourceSetCheck :extensions:git:ktlintTestSourceSetCheck \
  :extensions:ai:ktlintMainSourceSetCheck :extensions:ai:ktlintTestSourceSetCheck \
  detekt :extension-api:detekt :extensions:git:detekt :extensions:ai:detekt \
  :app:cpd \
  lintDebug :extension-api:lintDebug :extensions:git:lintDebug :extensions:ai:lintDebug \
  testDebugUnitTest :extension-api:testDebugUnitTest :extensions:git:testDebugUnitTest :extensions:ai:testDebugUnitTest \
  compileDebugAndroidTestKotlin assembleDebug
```

**The source sets are named on purpose.** `ktlintCheck` alone is an aggregate over whatever tasks
the plugin registered, and for months it registered none for Kotlin - so it checked the `.kts`
build scripts and reported success (`docs/adr/0016-*`). Named, a plugin that stops seeing a
source set fails with "Task not found" instead of passing quietly.
The extension modules ([ADR-0104](docs/adr/0104-extensions-are-compile-time-modules-against-an-extension-api.md))
are named by path for that reason too: an unqualified task runs in every module that has it and says
nothing about one that does not.
`compileDebugAndroidTestKotlin` is named for the same reason: nothing else in the list reaches
`androidTest` - `assembleDebug` compiles `main`, `testDebugUnitTest` compiles `test`, and the
ktlint check is a formatter, which parses without resolving.

And `uv run --locked ruff check scripts tools`, `uv run --locked ruff format --check scripts tools`, `uv run --locked mypy`, `uv run --locked mypy --config-file tools/rootfs/mypy.ini` and
`uv run --locked python -m unittest discover -s scripts -p '*_test.py'`

Notes:
- `JAVA_HOME` must point at a JDK (the Gradle hooks run inside the current
  shell env); unset, `scripts/java-home.sh` falls back to `$HOME/android-studio/jbr`.
- pre-commit install alternatives: `pip install pre-commit` or `uv tool install
  pre-commit`.
- detekt is pinned to the `2.0.0-alpha.x` line because stable 1.23.8 cannot run
  on JDK 25; the alpha line is detekt's official compatibility path for
  AGP 9 + JDK 25 (no stable release supports it yet).
- Every dependency is locked STRICT and verified by sha256
  ([ADR-0123](docs/adr/0123-dependencies-are-locked-strict-and-verified-by-sha256.md)), so a
  dependency or plugin bump rewrites both. Run the gate command above plus
  `assembleDebugAndroidTest` once with `--write-locks --write-verification-metadata sha256`
  together, then run it again with neither, so STRICT and verification both prove out on the
  new files. The flags must go on the *same* run: `--write-locks` resolves unlocked, which
  downloads candidate metadata the recorded sums never covered, so it fails verification
  unless that run is also writing them. Running only `<module>:dependencies` misses
  configurations that only a task resolves, and STRICT then fails on them. Commit the lockfiles and
  `gradle/verification-metadata.xml`, and read their diff. A Gradle bump re-runs
  `./gradlew wrapper --gradle-version <v> --distribution-type bin --gradle-distribution-sha256-sum <published>`
  and never edits `gradle-wrapper.properties` by hand.
- Python dependencies are declared, locked and verified by sha256 too
  ([ADR-0130](docs/adr/0130-python-dependencies-are-locked-and-verified-by-sha256.md)). A host bump
  edits `pyproject.toml`, runs `uv lock` (`--upgrade-package <name>` for one package), then the
  unittest command above, and commits `uv.lock`. A rootfs bump edits `tools/rootfs/requirements.in`
  (or `jupyter-ai-tools.in`), re-runs the command in the header of the matching `.lock` from
  `tools/rootfs/` (add `--upgrade-package <name>` to move one package; a bare compile keeps today's
  pins), rebuilds the image, and commits the lock after reading its diff. Never edit a lock by hand
  or drop `--require-hashes` to get a build through.


## Host resources

<!-- host-resources v1: a standard block, kept word-for-word identical wherever it is used. Limits specific to this repository go under "In this repository", below it. -->

This repository can share its host with other work, and hosts differ. Act on what you measure on the host in front of you, never on a core count, memory size or timing remembered from another run or written in a doc.

**Measure before every heavy command**: a test suite, a coverage or mutation run, a build, a browser, an emulator, a container.

```sh
nproc; cut -d' ' -f1-3 /proc/loadavg                # logical cores; 1, 5 and 15 min load
grep -E 'MemAvailable|SwapFree' /proc/meminfo       # memory a new process can still get
grep some /proc/pressure/cpu /proc/pressure/memory  # avg60: % of the last minute tasks stalled
findmnt -no FSTYPE -T "${TMPDIR:-/tmp}"             # tmpfs: the temp dir is RAM
```

These are Linux's commands; on another OS take the same four readings with its own tools.

- **Wait when the host is busy.** A 1 min load above `nproc` means more runnable tasks than cores, so one more job only lengthens every queue. A memory `some avg60` above zero on two readings a minute apart means tasks already stall for memory. Wait and measure again; if it stays that way, report the readings.
- **Fit the job to the memory.** Start a job only when its measured peak fits in `MemAvailable`. `/usr/bin/time -v <command>` prints `Maximum resident set size` for the largest single process of a run; multiply it by the number of workers.
- **Cap parallelism yourself.** A tool's `auto` worker count takes every logical core. Pass an explicit count of at most half of `nproc`: logical cores usually come two to a physical core, and the rest of the host needs the other half.
- **One heavy command at a time.** Queue the next one; never start two together.
- **Bound and contain every heavy command.** When the host has a job runner (`command -v devjob`), run through it: `devjob <command>`. Otherwise run `nice -n 10 timeout <limit> <command>`, so the job yields the CPU and cannot hang forever.
- **Stop what you start, in the step that finishes with it**: dev servers, browsers, emulators, daemons, containers, watchers. Then list what is left (`pgrep -af <name>`) and stop only processes this session started. One whose parent is alive and is not yours belongs to someone else.
- **Keep bulk off a RAM temp dir.** When the temp dir is `tmpfs`, every byte in it is memory until it is deleted. Installs, virtualenvs, downloaded binaries and large logs go to a path `findmnt -T <path>` shows on a disk filesystem, and you delete them when the task ends.
- **Re-run a failure alone before calling it a regression.** A timeout on a loaded host measures the neighbours. Measure again, run the test by itself, then judge.
- **Never relieve pressure by force.** Do not kill a process you did not start, raise a limit or switch off a cap to get a job through. Report the readings and wait.

### In this repository

Unbounded builds and test runs once filled a host's 4 GB swap on 4 of 5 days and ended in a kernel
OOM kill (`sar`, `journalctl -k`, 2026-10-09). Where `devjob` (`~/.local/bin/devjob`) exists, every
heavy command therefore runs through it:

```sh
devjob git commit -F msg.txt                  # the whole hook gate: one job, one warm daemon
devjob ./gradlew testDebugUnitTest
devjob scripts/<driver>.sh                    # one that starts the emulator or calls Gradle
nice -n 10 timeout 3600 scripts/<driver>.sh   # one that only drives the external tablet
DEVJOB_MAX_SEC=7200 devjob ./gradlew connectedDebugAndroidTest
devjob --status                               # slots in use, jobs running
```

- A job waits for one of the host's slots, runs capped in `background-devjobs.slice`, and when it
  ends systemd kills everything it left: Gradle and Kotlin daemons, an emulator, Maestro. Nothing
  outlives its job, so there is nothing to hunt down afterwards.
- **Wrap whole jobs, not each Gradle call.** A daemon dies with its job, and a cold one costs 10-13 s
  against a warm 2.7 s (`detekt`, measured). `devjob` inside a job runs inline, so a wrapped driver
  that calls Gradle does not deadlock on a second slot.
- **A driver that only drives the external tablet does not wait for a slot.** The app, its kernel
  and its shell run on the tablet; the host carries Maestro's JVM (about one core and a few hundred
  MB), `adb` and a pixel read, so holding the flow behind a slot only delays it. It still runs under
  `nice -n 10 timeout <limit>`. A driver that starts the emulator, or installs through Gradle, is
  host work and stays on `devjob`.
- **Never run Gradle on the host while a device flow runs**, and stop the emulator when its tests are
  done: `dev36` alone is 4 GB and 4 cores (its `config.ini`).
- `connectedDebugAndroidTest` uninstalls the app from **every** attached device, `ANDROID_SERIAL`
  or not, so reinstall on the tablet before its next flow.
- A test that bounds time bounds **thread CPU time**, never wall time: on a shared host a wall clock
  measures the neighbours (`StreamFoldTest.aRunawayPrintFoldsInLinearTime`).

## Architecture (locked)

- Pragmatic hexagonal + UDF/MVVM.
- `:app`, plus one module per extension built against `:extension-api` and never against `:app`
  (ADR-0104); manual DI via `di/AppContainer` (no DI framework).
- Domain layer: pure Kotlin, **zero `android.*` imports** — enforced by an
  architecture-guard test.
- Ports (contract-tested in Step 1): `NotebookRepository`, `KernelGateway`,
  `ServerLauncher`; `Space` modeled as a first-class domain type.
- Design-token system shared Compose ↔ WebView CSS vars (`--callisto-*`).
