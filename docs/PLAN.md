# JupyterDroid — Build Plan

Offline Jupyter notebook app for Android: Compose shell + stock `jupyter_server`/kernels
in a bundled rootless glibc rootfs on loopback.

Source material: `reference/xeus-android/FEASIBILITY.md`, `reference/xeus-android/STUDY_CASES.md`,
`reference/callisto/FINDINGS.md`.

---

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
- After a step's code is done and all tests pass, drive the **real app on the
  dev36 emulator** with Maestro CLI flows: launch, tap, type, navigate.
- Capture **screenshots and snapshots** of every flow into `evidence/<step>/`
  and commit them.
- Unit tests alone are never sufficient to close a step.

### 3. Evidence-gated progression
- A step is NOT complete — and the next step does NOT start — until:
  1. Unit + instrumented tests are green (`red → green → refactor`).
  2. Maestro flow passes on dev36 with screenshots/snapshots in `evidence/<step>/`.
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

---

## Tooling

| Tool | Role | Config |
|---|---|---|
| ktlint | formatter + check | `.editorconfig` (`org.jlleitschuh.gradle.ktlint`) |
| detekt | static analysis | `config/detekt/detekt.yml` |
| Android Lint | Android-specific checks | AGP built-in (`:app:lintDebug`) |
| pre-commit | commit gate | `.pre-commit-config.yaml` |
| file-length gate | ≤500 lines/file | `scripts/check-file-line-length.sh` |

Local verification command (same as the commit gate):
`JAVA_HOME=<jdk> ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug`

Notes:
- `JAVA_HOME` must point at a JDK (the Gradle hooks run inside the current
  shell env); on this machine: `/home/user/android-studio/jbr`.
- pre-commit install alternatives: `pip install pre-commit` or `uv tool install
  pre-commit`.
- detekt is pinned to the `2.0.0-alpha.x` line because stable 1.23.8 cannot run
  on JDK 25; the alpha line is detekt's official compatibility path for
  AGP 9 + JDK 25 (no stable release supports it yet).

---

## Architecture (locked)

- Pragmatic hexagonal + UDF/MVVM.
- Single `:app` module; manual DI via `di/AppContainer` (no DI framework).
- Domain layer: pure Kotlin, **zero `android.*` imports** — enforced by an
  architecture-guard test.
- Ports (contract-tested in Step 1): `NotebookRepository`, `KernelGateway`,
  `ServerLauncher`; `Environment` modeled as a first-class domain type.
- Design-token system shared Compose ↔ WebView CSS vars (`--callisto-*`).

### Container ordering (locked)
- **Step 8** = the **base environment**: rootfs extracted into
  `files/containers/base/` and `jupyter_server` runs from it. Container-shaped
  from day one — no throwaway rootfs path to migrate later.
- **Steps 9–13** validate server / WebSocket adapter / kernelspecs against the
  single base env.
- **Step 17** = full container system (env-ids, create/remove, per-env venvs +
  kernelspecs, offline wheels, env switching) — after the server's
  kernelspec-discovery mechanics are proven.
- Rootfs spike target: **x86_64 on the emulator**; arm64 deferred to real device
  (per-ABI splits in FEASIBILITY.md).

### Phase-2 UX target (from Callisto study)
Self-sizing keyboard-aware per-cell CodeMirror 6 WebViews (height sync, 4px
threshold, 80ms debounce, 8000px cap), theme via CSS vars, id-based async
completion (5s timeout), long-press cell menu, Run All, Console REPL sharing the
kernel namespace, data/output workspace + ZIP export, Settings, onboarding tour,
VIEW intent for `.ipynb`/`.py`.

---

## Steps

Each step: red test → green impl → instrumented test → **Maestro flow +
screenshots → `assembleDebug` green → commit → proceed**.

| # | Step | Deliverable / behaviour |
|---|---|---|
| 0 | Verification harness | adb on PATH, boot dev36, install Maestro, `evidence/` convention, **wire ktlint/detekt/Lint + pre-commit gate and verify it blocks a bad commit** |
| 1 | Architecture + draw interfaces | `docs/architecture.md`: layers, ports, data model, wireframes (Home Recent, Editor cells, Console, Workspace, Settings); AppContainer skeleton; architecture-guard test |
| 2 | Home: notebook list | Recent list + relative timestamps, empty state, FAB; long-press rename/delete; New notebook / New Python file / Open `.ipynb` (VIEW intent) |
| 3 | Notebook persistence | `DiskNotebookRepository` over nbformat JSON; round-trip tests |
| 4 | Editor shell | cell header Run/Stop + long-press context menu (run, insert code/markdown below, move up/down, duplicate, delete, clear outputs, collapse), Run All, busy indicator, dirty state |
| 5 | CodeMirror 6 cell editor | per-cell WebView: self-sizing + keyboard-aware height, theme via CSS vars, id-based async completion, `Mod-Enter`, debounced edit, `?mode=`; bridges as JVM-testable core |
| 6 | Execute behaviour | Run → `KernelGateway.execute` → outputs; Running/Idle; Stop/interrupt; execution count (FakeKernel) |
| 7 | Output rendering | stream/error/display_data renderers, themed output area, collapse, clear outputs |
| 8 | **Risk gate** (base env) | x86_64 rootfs → `files/containers/base/`, dynamic-loader launch, bind 127.0.0.1 | `adb forward` + HTTP 200 + run a cell |
| 9 | Phase 1: JupyterLab-in-WebView | WebView → `/lab`, cleartext config |
| 10 | Real KernelGateway (WebSocket) | swap fake → real behind the port |
| 11 | Console REPL | REPL sharing the open notebook's kernel; Enter=run, Shift+Enter=newline; input history, clear transcript |
| 12–13 | xeus-python, xeus-sqlite, multi-kernel | kernelspecs + language switch |
| 14 | Workspace & export | data/ vs output files, `+ Data`, ZIP export, import |
| 15 | Settings | theme, accent presets, code font size, wrap lines, completion, coding-keyboard symbol bar, background autosave |
| 16 | Onboarding tour | 4-tip welcome |
| 17 | Environments + offline wheels | full container system on top of the proven base env |
| 18 | Foreground service + lifecycle | server held while notebook open |
| 19 | Settings polish + About | licences, storage |

**Step 2 is the current step, broken into sub-steps (each closes with unit tests
+ Maestro evidence + a semantic commit):**

- **2.1** Home list — replace the scaffold landing with Home: recents list, relative
  timestamps, empty state. Removes scaffold `MainScreen`/`DataRepository` cruft.
- **2.2** Create — FAB menu (New notebook / New Python file) → item appears in recents.
- **2.3** Manage — long-press row → rename / delete. **(Done)**
- **2.4** Open — VIEW intent imports an `.ipynb` into the list (nbformat parsing lands
  in Step 3; Step 2 imports the title only).

On-device verification is Maestro flows (the established harness); unit tests cover
state/logic. `connectedAndroidTest` is not part of the gate to keep each sub-step fast.

---

## Harness notes

- Emulator: `dev36` AVD = **API 36 (Android 16, `google_apis/x86_64`, `x86_64-36_r07`)**,
  `-gpu swiftshader_indirect`. The originally planned API 37 (`dev37`, Android 17
  CE2A) image was **abandoned after evidence**: its guest vendor HAL
  (`/vendor/lib64/hw/mapper.ranchu.so`) asserts `!hasReadColorBufferDma`, so
  surfaceflinger `RegionSampling` SIGABRT-crash-loops every ~5s against the
  emulator 37.1.11 gfxstream host — verified under `swiftshader_indirect` and
  `angle_indirect` (`adb logcat -b crash`, screenshots/logs in `/tmp/opencode/`).
  The API 36 image boots cleanly (surfaceflinger + system_server pids stable,
  `pm` responsive). UI flows are API-agnostic, so no plan scope changed.
- `pkill -f <pattern>` self-matches the calling shell and hangs it — use
  `pkill -f '[q]emu-system'` (bracket trick).
- Maestro `takeScreenshot` rejects `../` paths; capture into Maestro's output dir
  and copy the PNG into `evidence/<step>/`.
