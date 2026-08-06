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
- Capture **screenshots, snapshots and logs** of every flow into
  `evidence/<step>/` and commit them. `maestro.log` (and `logcat` where the step
  touches a process or the runtime) counts as evidence, not just PNGs.
- Unit tests alone are never sufficient to close a step.

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

## Per-step procedure (mandatory loop)

The rules above say *what* must hold. This says *in what order* to work. Every
step runs this loop start to finish; no stage is skipped or reordered.

1. **Size the step.** Read the plan entry for the step. If it is too big to close
   in one functional commit, break it into sub-steps *before* writing any code.
2. **Research before acting.** Read the plan, the reference codebases
   (`reference/`, `reference/research_repos/`) and the web. Decisions are made on
   evidence — never on guesswork or assumption.
3. **TDD.** Failing test first, then the minimal implementation, then refactor
   (rule 1).
4. **Interact with the real app.** Maestro flows on dev36: screenshots,
   snapshots and logs into `evidence/<step>/` (rule 2).
5. **Commit semantically and atomically.** One coherent change per commit,
   conventional-commit subject. No complex git, no reverts, no rewriting
   history. Every commit is fully functional — never broken mid-series.
6. **Refactor rather than patch.** Band-aids layered on legacy code are worse
   than a clean rewrite. Favour clarity, idiom and discoverability; renaming or
   restructuring is preferred over working around a bad shape.

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
  `ServerLauncher`; `Space` modeled as a first-class domain type.
- Design-token system shared Compose ↔ WebView CSS vars (`--callisto-*`).

### Product end state: Spaces

The container/environment concept is user-facing and is called a **Space** (the
SageMaker Studio term). "Environment" is deliberately *not* used: it would
collide with the Python environments living inside a Space.

- Create a Space per project, the way Winlator creates a container.
- Configure it: pick the package manager (**uv**, **conda** or **pip**) and add
  system packages.
- Run it and get a notebook UI backed by a **real Jupyter server on the device**,
  never remote.
- UI is Jetpack Compose + WebView, styled after the Callisto study, tuned for
  phones *and* tablets.

Everything below builds toward this incrementally. The vocabulary is fixed now so
it stays consistent from the first commit.

### Space ordering (locked)
- **Step 8** = the **base Space**: rootfs extracted into `files/spaces/base/` and
  `jupyter_server` runs from it. Space-shaped from day one — no throwaway rootfs
  path to migrate later.
- **Steps 9–13** validate server / WebSocket adapter / kernelspecs against the
  single base Space.
- **Step 17** = full Space system (space-ids, create/remove, per-Space venvs +
  kernelspecs, package-manager choice, offline wheels, switching) — after the
  server's kernelspec-discovery mechanics are proven.
- Rootfs spike target: **x86_64 on the emulator**; arm64 deferred to real device
  (per-ABI splits in FEASIBILITY.md).

### Exec model (decided — evidence, not preference)

How a `targetSdk 36` app executes a Linux userland. This is the project's largest
technical risk and it is settled by packaging, with **no shims**.

`FEASIBILITY.md` rightly rejects proot/chroot (Android 15 seccomp kills them) and
picks Winlator's rootless-glibc-loader idea, but does not account for this:
**Winlator only gets away with `ProcessBuilder`-exec'ing out of `filesDir`
because it sets `targetSdkVersion 28`** — verified in
`reference/research_repos/winlator/app/app/build.gradle`: `compileSdk 34`,
`minSdkVersion 26`, `targetSdkVersion 28`, `abiFilters 'arm64-v8a'`. Termux does
the same (`reference/research_repos/termux-app/gradle.properties`:
`targetSdkVersion=28`, `compileSdkVersion=36`).

AOSP SELinux commit `0dd738d8` removed `execute_no_trans` from app data files but
kept `execute`:

| Operation on a file in `filesDir`, targetSdk ≥ 29 | Allowed? |
|---|---|
| `execve()` — needs `execute_no_trans` | **blocked** |
| `dlopen()` / `mmap(PROT_EXEC)` — needs `execute` | **allowed** |

The approved fix is packaging: files shipped in `jniLibs` are extracted at
install time to `/data/app/…/lib/<abi>/` with SELinux context `apk_data_file`,
where execution *is* permitted. This is **Termux's APKLF design** — their
NLnet-funded migration off `targetSdkVersion 28`, not yet shipped. Their current
installer already uses the half we need, though: `TermuxInstaller.java` reads a
`SYMLINKS.txt` manifest out of the bootstrap archive and replays it with
`Os.symlink` (lines 157–210). We adopt that pattern, pointed at
`nativeLibraryDir`.

We do **not** use the `/system/bin/linker64` + `LD_PRELOAD` interceptor
(`termux-exec`) — Termux's own docs flag it as Play-policy-violating, and it is
the shim-heavy path.

For jupydroid this is far smaller than for Termux, because exactly **two files
must be executable**:

- `jniLibs/<abi>/libld-linux.so` — the glibc dynamic loader
- `jniLibs/<abi>/libpython3.so` — the CPython binary

Everything else — all of glibc, the stdlib, every `site-packages` native
extension — lives in the rootfs under `filesDir` and is only ever `mmap`'d.

Two build-time `patchelf` edits and two first-run symlinks tie it together:

```
patchelf --set-interpreter /data/data/com.jupydroid.app/files/spaces/base/lib64/ld-linux-x86-64.so.2 \
         --set-rpath      /data/data/com.jupydroid.app/files/spaces/base/usr/lib/<triplet> \
         libpython3.so
```

`/data/data/<pkg>` is stable; `nativeLibraryDir` is **not** (per-install random
segments), which is why `PT_INTERP` points at a rootfs path that first-run
symlinks to `nativeLibraryDir`. The kernel resolves the symlink and checks the
*target's* context — `apk_data_file` — so the exec is permitted. This is exactly
Termux's arrangement.

Kernel subprocesses need no shim either: a kernelspec's `argv` is JSON we write,
and `ipykernel` writes `sys.executable` into it, which resolves to the
`nativeLibraryDir` path. `LD_LIBRARY_PATH`, `PYTHONHOME`, `HOME` and `TMPDIR` are
ordinary `ProcessBuilder` env vars (Winlator does the same).

Net: no proot, no chroot, no `LD_PRELOAD`, no exec interceptor, no `linker64`, no
targetSdk downgrade, **no NDK** (nothing is compiled — we ship prebuilt ELFs).
One Gradle line, `useLegacyPackaging = true`, so the libs are extracted to disk
rather than left inside the APK.

Licensing: Winlator is LGPL-2.1, sora-editor is LGPL-2.1 and Callisto is
proprietary, so per `STUDY_CASES.md` every one of them is clean-room — techniques
studied, no code vendored. For sora this is load-bearing rather than tidy: it is the
reference for the editor we are writing ourselves, and copying from it would put the
app under LGPL-2.1.

### Correction to the reference material

**Callisto runs no Jupyter server.** It is Chaquopy with in-process CPython 3.10
and a hand-rolled JSON envelope — no `jupyter`/`ipykernel`/`pyzmq`/`tornado` in
its assets, no `<service>` in its manifest. It is a **UX reference only**
(per-cell CodeMirror 6 WebViews, height-sync bridge). Its useful proof: a ~150 MB
Play-distributed app shipping CPython + numpy/scipy/pandas for `arm64-v8a` +
`x86_64` passes review. `reference/jnotes/` is an unrelated handwriting-notes app
and can be ignored.

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
| 2 | Home: notebook list | Recent list + relative timestamps, empty state, FAB; long-press rename/delete; New notebook / New Python file / Open `.ipynb` (VIEW intent). **Done** |
| 3 | Notebook persistence | `DiskNotebookRepository` over nbformat JSON; round-trip tests. **Done** |
| 4 | Editor shell | cell header Run/Stop + long-press context menu (run, insert code/markdown below, move up/down, duplicate, delete, clear outputs, collapse), Run All, busy indicator, dirty state |
| 5 | Cell editor | Compose-native `BasicTextField` + our own highlighting — broken into 5.1–5.5 below. **Was** CodeMirror-in-WebView; changed on measurement |
| 6 | Execute behaviour | Run → `KernelGateway.execute` → outputs; Running/Idle; Stop/interrupt; execution count (FakeKernel) |
| 7.1 | **Outputs persist** — nbformat round-trip of `outputs` and `execution_count` | **Done.** Fixed silent data loss: `toDomain`/`toFile` dropped both, so saving a notebook another tool had run destroyed its results. `evidence/step7.1/` |
| 7.2 | Output rendering | **Done** for images, tracebacks and streams: PNG decoded and drawn, ANSI colour codes stripped, errors in a themed block. `evidence/step7.2/`. Still open: collapse, clear outputs, and `text/html` (a pandas DataFrame falls back to its `text/plain`, which needs the WebView from Step 5) |
| 8 | **Risk gate** (base Space) | x86_64 rootfs → `files/spaces/base/`, loader launch, `jupyter_server` on 127.0.0.1, kernel started, cell run — broken into 8.1–8.6 below |
| 9 | Phase 1: JupyterLab-in-WebView | WebView → `/lab`, cleartext config. **Note:** this is a whole-app fallback view, *not* the cell editor — that is native as of Step 5.2 |
| 10 | Real KernelGateway (WebSocket) | **Done** — 10.1–10.5 below |
| 11 | Console REPL | REPL sharing the open notebook's kernel; Enter=run, Shift+Enter=newline; input history, clear transcript |
| 12–13 | xeus-python, xeus-sqlite, multi-kernel | kernelspecs + language switch |
| 14 | Workspace & export | data/ vs output files, `+ Data`, ZIP export, import |
| 15 | Settings | theme, accent presets, code font size, wrap lines, completion, coding-keyboard symbol bar, background autosave |
| 16 | Onboarding tour | 4-tip welcome |
| 17 | Spaces + offline wheels | full Space system (create/remove, package-manager choice, system packages) on top of the proven base Space |
| 18 | Foreground service + lifecycle | server held while notebook open |
| 19 | Settings polish + About | licences, storage |

**Step 2 is done** (2.1–2.4, each closed with unit tests + Maestro evidence + a
semantic commit):

- **2.1** Home list — recents list, relative timestamps, empty state. **(Done)**
- **2.2** Create — FAB menu (New notebook / New Python file) → item appears in recents. **(Done)**
- **2.3** Manage — long-press row → rename / delete. **(Done)**
- **2.4** Open — VIEW intent imports an `.ipynb` into the list (title only; nbformat
  parsing landed in Step 3). **(Done)** — driver: `scripts/step2-import-driver.sh` +
  `.maestro/step2-import.yaml`; note the filter matches any `content://` VIEW
  (`.ipynb` has no standard MIME type and file managers hand out content URIs
  whose path does not encode the extension), the app validates via the display name.

**Step 3 is done** (`DiskNotebookRepository` + nbformat DTOs, 9 repo tests,
persistence proven by Maestro `stopApp`→relaunch flow `step3-persistence.yaml`).

**Step 4 is in progress**, split in two because the runtime is pulled forward:

- **4a** Read-only editor — tap a recents row → cell list (code + markdown
  sources rendered, back to Home). No execution.
- **4b** Editing + execution shell. **Run, Stop, Run All and the busy indicator
  are done** (`evidence/step4b/`, `evidence/step4b-run-all/`), against a real
  kernel on the tablet. **The cell menu is done too** — insert code/markdown below,
  move up/down, duplicate, delete, clear outputs, persisted and read back off the
  device (`evidence/step4b-cell-menu/`). Still open: **collapse**, which needs
  `metadata.jupyter.source_hidden`/`outputs_hidden` round-tripped through the
  nbformat DTOs and so belongs with 7.2's remainder rather than here.

  **The way in is a `⋮` button on the cell header, and it took three attempts.** A long
  press on the cell is text selection — `BasicTextField` owns that gesture, and the first
  attempt produced Android's Select all / Autofill toolbar. Moving the press to the header
  worked but was undiscoverable: nothing on screen says a thin strip is interactive, and a
  gesture on the `Row` made the whole header one target that would fight every button added
  beside it later. JupyterLab settles it — `@jupyterlab/cell-toolbar` puts **six independent
  buttons** on the cell (duplicate, move up/down, insert above/below, delete), and only hides
  them below 760px, which is every screen this app runs on. So: a real button, no gesture on
  the Row, and the header stays somewhere more buttons can go.

  **A popup's position is not observable from a Compose test.** The menu first opened at the
  far left of the cell — a `DropdownMenu` anchors to its parent layout node, and the header
  `Row` fills the width. The geometry assertion written to catch it failed *identically*
  before and after the fix: a popup has its own window, so `getUnclippedBoundsInRoot` reports
  coordinates inside it. The test was deleted rather than left asserting something it cannot
  see; anchoring is a screenshot gate. `evidence/step4b-cell-menu/00b-…png`.

  Three semantics come from JupyterLab's `NotebookActions`, read rather than guessed, and
  all three differ from the obvious answer: deleting the **last** cell leaves an empty code
  cell rather than an empty notebook; **clear outputs also clears the execution count**
  (`clearExecution`, `packages/cells/src/model.ts:706`); and a **duplicate keeps the
  original's outputs and count**, with only its id renewed (`addCells`).

**Step 8 is pulled forward, ahead of Steps 5–7**, and split so the riskiest thing
fails first. Steps 5–7 (CodeMirror cells, execute behaviour, output rendering)
are all written against a real kernel; proving the runtime first avoids
rebuilding them. Each sub-step is one commit with its own evidence:

| # | Sub-step | Gate |
|---|---|---|
| 8.1 | Reproducible x86_64 rootfs (`tools/rootfs/Dockerfile`, host-side only) | tarball runs `python3 -c` in Docker; size recorded |
| 8.2 | Ship + install: patched ELFs in `jniLibs/x86_64`, `useLegacyPackaging`, extract to `files/spaces/base/` + `Os.symlink` | installed-file count + stamp on screen; `run-as ls -l` of the symlinks |
| 8.3 | **Execute the interpreter** — `libpython3.so -c "import sys; print(sys.version)"` | **the real risk gate**: SELinux/seccomp fails here or nowhere. **FAILED — see below** |
| 8.4 | `jupyter_server` on 127.0.0.1, ephemeral port, empty token | `GET /api/status` returns a version |
| 8.5 | Start a kernel — `POST /api/kernels` | proves the **subprocess** `execve` path needs no shim |
| 8.6 | Execute a cell over the kernel WebSocket | `2 + 2` → `4` |
| 8.7 | **numpy/scipy on musl** — add them to the image and import them on device | musllinux wheels are far less exercised than manylinux, and Step 8.3 showed what assuming costs. Gate it here rather than discover it in Step 12. **Passed** once SIGSYS was handled rather than left to kill the process |

### Step 5 broken down (a Compose-native cell editor)

**Decision changed 2026-08-05, on measurement.** Steps 5.1-5.5 built a CodeMirror 6 editor in
a per-cell WebView, following `reference/callisto/FINDINGS.md` §2b. It works and is on device.
It is being retired anyway, for reasons that are numbers rather than taste:

| | |
|---|---|
| App PSS, home screen | 125 MB |
| App PSS, one 30-cell notebook open | **181 MB** |
| Plus a WebView renderer process | **93 MB RSS** |

Measured on the Galaxy Tab A9+. LazyColumn recycles, so that +57 MB is the *visible* cells
only, and every scroll builds new WebViews that each re-load a 380 KB page. On top of that,
two of the last three editor bugs came from the bridge and nothing else - the `ready` race
(`Cannot read properties of undefined`, a blank editor) and CSS-pixels treated as physical
pixels (invisible on a 1x emulator, wrong on every real device). Both are impossible without
a bridge.

**What replaces it: `BasicTextField` + `TextFieldState`, with our own highlighting.** Compose
owns the hard part - IME composing regions, autocorrect, gesture typing, selection handles,
the magnifier - and we own only the language layer. The known weakness of that approach,
styling degrading over long text, does not reach us: a notebook cell is tens of lines.

**sora-editor was the other candidate and is cloned into
`reference/research_repos/sora-editor` for study.** It is **LGPL-2.1**, so the same rule as
Winlator applies and is not negotiable: read it to understand how a problem was solved, never
copy it. Nothing from that tree may be vendored, and a fork would not have helped anyway -
a derivative of LGPL-2.1 stays LGPL-2.1, so forking answers the licence question with "no".

| # | Slice | Test seam | Gate |
|---|---|---|---|
| 5.1 | `PythonSyntax`: ANTLR-generated lexer → highlight spans, behind a `SyntaxHighlighter` interface | JVM | **Done.** 8 tests: comments, f-string interpolation, triple-quoted strings, no zero-width spans, tolerates half-typed code |
| 5.2 | `CodeCellField`: `BasicTextField` + `TextFieldState`, spans applied through `OutputTransformation` | emulator, then tablet | **Done.** Maestro green on both. Like-for-like on the tablet: a 30-cell notebook costs **+10.5 MB** and **no renderer process**, where the per-cell WebView cost +56.7 MB plus a 93.4 MB renderer — about **139 MB saved**. `evidence/step5.2/tablet/` |
| 5.3 | `InputTransformation`: auto-indent after `:`, keep indent on newline, auto-close brackets and quotes | JVM - it is a pure text edit | **Done.** `PythonTypingRules`, 19 tests. Maestro on the tablet types **strictly fewer characters than the cell ends up holding**, so the result cannot be faked: no indent is ever typed, and `)` `'` only ever arrive on top of a partner already there. `evidence/step5.3/` |
| 5.4a | Bracket-match highlight | JVM for the matching, device for the drawing | **Done.** `bracketPairAround`, 11 tests. `evidence/step5.4a/` |
| 5.4b | Line numbers | JVM for where the logical lines start, device for the drawing | **Done.** Positions come from the field's `TextLayoutResult`, so a wrapped line keeps one number. `evidence/step5.4b/` |
| 5.5a | `KernelGateway.complete` over the socket — **the port's last fake, implemented** | `ScriptedKernelSocketOpener`, pure JVM | **Done.** No Maestro, and deliberately: nothing calls `complete` yet, so a flow would be theatre — the same call made for 10.1–10.3. The device gate is 5.5b's |
| 5.5b | Completion popup anchored with `TextLayoutResult.getCursorRect`, fed by 5.5a | fakes, then device | **Done.** `pri` typed on the tablet, `print` offered by the real kernel, tapped and spliced. `evidence/step5.5b/` — including the run that went green with **no popup on screen** |

#### The lexer is generated, never hand-written

A rule, not a preference. The grammar comes from **`antlr/grammars-v4`** (MIT, 11.1k stars,
maintained) and is turned into a lexer by **ANTLR 4.13.2** (BSD-3-Clause). Both are permissive:
attribution only, no copyleft, and no native code - so no NDK, no CMake, no 16 KB page-size
obligation, and no crash surface that debugs like C.

Everything below was verified by generating, compiling and lexing real Python before any of it
was written down. Pinned at grammars-v4 `42b1ea521705`.

**`python/python3_14`, not `python/python3`.** The older grammar discards comments -
`SKIP_: (SPACES | COMMENT | LINE_JOINING) -> skip` with `COMMENT` only a fragment - so they
never reach the token stream and cannot be coloured at all. `python3_14` emits
`COMMENT -> channel(HIDDEN)`, which a `CommonTokenStream` can still read.

**f-strings tokenise structurally**, which is better than expected:
`FSTRING_START`, `FSTRING_MIDDLE`, `LBRACE`, `NAME`, `RBRACE`, `FSTRING_END` - so `{name}`
inside a string can be coloured as an expression the way VS Code does it.

Two costs, both measured rather than guessed:

- **The parser has to be generated too.** `PythonLexerBase` constructs a `PythonParser`, so
  the lexer will not compile alone. That is 552 KB of extra generated Java (840 KB total,
  1.2 MB compiled).
- **`INDENT`/`DEDENT` are synthetic**, zero-width, and carry inverted ranges like `[33..32]`.
  They must be filtered when building spans or they produce negative-length ones.

**Deleted when 5.2 lands**, not before, so the tree is never without an editor: `EditorBridge`,
`EditorMessage`, `EditorPageHandle`, `EditorHeight`, the `CellEditor` WebView, `tools/editor/`
and the generated asset. Kept, because none of it was WebView-specific: the debounced save and
`saveNow`, `EditorUiState`'s transitions, and the theme derived from the Material scheme.

**Height sync stops being a feature.** A native text field measures itself; there is no page to
ask, no threshold, no debounce, and no unit to get wrong. That whole slice disappears rather
than being ported.

#### An `InputTransformation` is testable on the JVM, with no device and no Robolectric

Found by spiking it rather than assuming, and it is why 5.3 needed no instrumented test.
`TextFieldState.edit { }` is public and hands out the same `TextFieldBuffer` the field passes to
`transformInput`, with real change tracking - so a test types into a real buffer:

```kotlin
state.edit { insert(selection.start, "\n"); with(rules) { transformInput() } }
```

Two things the spike settled that the docs do not say:

- **Inserting at a collapsed cursor pushes the cursor past the insertion.** So auto-closing a
  bracket has to place the cursor back explicitly, or it lands *after* the closer.
- **`changes` coalesces.** Two adjacent inserts read back as one change, so the user's keystroke
  must be inspected *before* the transformation edits anything.

#### An `OutputTransformation` already knows where the cursor is

Read out of `TransformedTextFieldState` before designing 5.4a around it, and it collapsed that
slice: the transformed text is held in a `derivedStateOf` over `TextFieldState.value`, and
`TextFieldCharSequence.equals` covers `selection`. So the cursor moving invalidates it and
`transformOutput` re-runs, with the live selection on the buffer it is handed. Bracket matching
needed no extra plumbing at all.

The same mechanism carries focus: state read *inside* `transformOutput` joins that dependency
set (the source says so in as many words), so the transformation takes a `State<Boolean>` from
`collectIsFocusedAsState()` and reads it. That gate is not optional - a field's cursor starts at
the **end of its text**, so without it every notebook would open with brackets lit in cells
nobody had touched.

**Another language is another `.g4` from the same MIT repo** - no native build, no grammar
pipeline, no second engine. tree-sitter was the alternative and is rejected for now: it is
MIT and official (`io.github.tree-sitter:ktreesitter-android` even ships prebuilt runtime
natives), but grammars are not published as artifacts, so each one means NDK + CMake, a
`.so` per ABI, Play's 16 KB page-size obligation, and native crashes that debug like C.
Highlighting is lexical; none of that buys colour.

**LSP is a later step and is widget-agnostic** - JSON-RPC over stdio, and a Space can run
`python-lsp-server` exactly as it runs a kernel.

### Step 10 broken down (real `KernelGateway`)

`KernelChannel` is not the answer and must not be stretched into one: it says so in
its own docstring - *connect, run one statement, report, close*. The port needs a
socket that stays open and streams `KernelEvent`s. That is a rewrite, so it is split
into slices that each end green, testable and reversible.

What is reused rather than rebuilt: `JupyterMessage` already builds `execute_request`
and already correlates replies by `parent_header.msg_id`, and every reader in it is
total by design (a malformed or unrelated frame returns null, because a shared kernel's
iopub carries other people's traffic). That correlation logic is the hard part of the
protocol and it is already proven on device.

| # | Slice | Test seam | Gate |
|---|---|---|---|
| 10.1 | Frame readers: `stream`, `display_data`, `execute_result`, `error`, `clear_output`, `execute_input`, `status`, `execute_reply` | pure JVM, frames as strings | **Done.** `JupyterFrame` (decode + correlate), `KernelOutputs` (frames → `CellOutput`), `JupyterMessage` (requests). 17 unit tests, no device, no socket |
| 10.2 | `KernelSocket`: open, keep open, emit frames, close. No Jupyter semantics | `MockWebServer` on loopback, in-process | **Done.** `KernelSocket`/`KernelSocketOpener` (ours) + `OkHttpKernelSocketOpener`. 5 tests: endpoint + token, send/receive, many frames, close handshake, refused upgrade carries its status |
| 10.3 | `RealKernelGateway.execute` as a `Flow<KernelEvent>`: correlate by `msg_id`, map frames to `Output`/`ExecutionCount`, complete on **reply *and* idle** | `ScriptedKernelSocketOpener` (named fake) | **Done.** `KernelGatewayContract` now runs against both the fake and the real one, plus 7 protocol tests: ordering, idle-alone, reply-alone, another request's traffic, socket teardown, mid-execution failure |
| 10.4a | Kernel lifecycle over REST: `connect` starts a kernel via `POST /api/kernels`, `interrupt` via `POST /api/kernels/<id>/interrupt` | `MockWebServer`, then device | **Done.** On the tablet: kernel started over REST, `2 + 2` → `4` and `import numpy` → `10.0` as `KernelEvent`s, and a 30 s sleep interrupted in **2158 ms** with `KeyboardInterrupt`. `evidence/step10.4a/` |
| 10.4b | One socket per session rather than per execution, closed by `disconnect` | fakes, then device | **Done.** Witnessed by the server, not our own counter: `/api/kernels/<id>` reports `"connections": 1` after four cells and `0` after `disconnect`. `evidence/step10.4b/` |
| 4b-min | **Re-sequenced ahead of 10.5.** Run one code cell from the editor and render its output, against `FakeKernelGateway` | `FakeKernelGateway` + `EditorViewModel` tests | Maestro on the emulator: tap Run, see output. 10.5's gate says "run a cell, see output" and there was no Run button — the editor is read-only from Step 4a and `Cell` has no outputs |
| 10.5 | Swap `RealKernelGateway` into `AppContainer` behind the port and delete `SpaceProbe`/`KernelGatewayProbe`/`KernelChannel`/`ExecutionOutcome` | — | **Done.** Maestro on the tablet: `print(6 * 7)` → `42` and a numpy determinant → `np.float64(10.000000000000002)`, tapped in the real UI. `evidence/step10.5/` |

**Completion needs both signals, not just idle** — found by reading JupyterLab rather than
assuming, and the first draft of this table had it wrong. `KernelFutureHandler` is done only
once **both** the shell `execute_reply` and the iopub `status: idle` have arrived, in either
order (`reference/research_repos/jupyterlab/packages/services/src/kernel/future.ts:14-17`,
`241-243`, `265-269`). They travel on separate channels with no ordering guarantee, so
finishing on idle alone ends the cell while output may still be in flight, and finishing on
the reply alone does the same. Both readers exist as of 10.1; the state machine is 10.3's.

Notes that keep this honest:

- **`complete` is not in Step 10.** The port declares it, `FakeKernelGateway` answers it,
  and nothing calls it until the editor lands in Step 5. Implementing it here would be
  untested-by-use code. It stays fake until Step 5 needs it, and that is a deliberate
  deferral rather than an oversight. **Landed in 5.5a**, and the deferral paid: the port
  declared `cursorLine`/`cursorColumn`, and the first real caller showed that both ends of it
  speak absolute offsets — a text field's selection and the protocol's own `cursor_pos`
  (`reference/research_repos/jupyterlab/packages/services/src/kernel/messages.ts:860-866`).
  Two conversions that could only lose, deleted before anything depended on them.
- **Each slice keeps the app working.** `FakeKernelGateway` stays wired in `AppContainer`
  until 10.5, so 10.1–10.4 cannot break a running app.
- **Maestro lands at 10.5, not before.** 10.1–10.3 have no UI to drive; claiming a
  Maestro gate on them would be theatre. Device evidence at 10.4, real flow at 10.5.
- **10.2 uses a real loopback server, not a mocked OkHttp.** The table first said "no
  real server", which would have left the OkHttp wiring — the entire content of that
  slice — proven only on device at 10.4. `MockWebServer` is in-process and ephemeral, so
  the tests stay fast and hermetic while actually exercising the socket. It also caught
  that a client's `close()` never completes unless the peer echoes the close frame.
- **Do not reuse `ExecutionOutcome`.** It models one-shot execution and the port models a
  stream. Two shapes for one idea is how drift starts; `KernelChannel` and
  `ExecutionOutcome` retire with `SpaceProbe` at 10.5.

Risks to measure rather than assume: glibc syscalls under Android's seccomp
filter (the reason proot was rejected); bundle size once numpy/scipy join
(FEASIBILITY.md estimates 250–500 MB unpacked, forcing an
asset-pack-vs-first-run-download decision); first-run extraction time.

### Step 8.3 result: packaging works, seccomp does not (2026-08-04)

Measured on the `dev36` emulator (API 36, x86_64), from inside the app process.
Full evidence in `evidence/step8.3/`.

| Probe | Result |
|---|---|
| `ld.so --version` from `nativeLibraryDir` | **exit 0** |
| `/bin/echo` (coreutils) via that loader | **exit 159 = SIGSYS** |
| `python3 -S -c ...` | **exit 159 = SIGSYS** |
| the same binaries under `adb shell run-as` | **work** |

**The packaging half of the exec model is proven.** `execve` out of the APK
native library directory succeeds, the patched `PT_INTERP` resolves through the
installed symlink, and glibc's loader runs. Nothing about `apk_data_file`,
`useLegacyPackaging` or patchelf is wrong.

**The seccomp half is refuted.** A trivial coreutils binary dies the same way
Python does, so this is glibc *libc startup*, not CPython and not our packaging.
`ld.so --version` survives only because it exits before libc init. `run-as` works
because it has the app's uid and SELinux label but is not a zygote child, so it
carries no app seccomp filter. This matches the documented Android 15+ behaviour
where seccomp kills `set_robust_list` with SIGSYS — the very syscall
`FEASIBILITY.md` §5.3 cites as breaking proot, while assuming the loader approach
would sidestep it. It does not: the filter applies to the process, not to how it
was launched.

**Confirmed on real arm64 hardware** — Samsung Galaxy Tab A9+ (SM-X216B),
Android 16 / SDK 36, kernel 6.1.128, with an arm64 rootfs and `PT_INTERP` baked as
`lib/ld-linux-aarch64.so.1`. Byte-for-byte the same outcome: loader exit 0,
coreutils and Python both SIGSYS, `run-as` control prints `Python 3.11.2`. So this
is not an emulator artifact, not x86_64-specific, and not something a shipping
device relaxes. `evidence/step8.3/arm64-device.txt`.

**The packaging model is sound and stays.** `jniLibs` → `apk_data_file` → `execve`
works on both targets; `useLegacyPackaging`, the patchelf'd `PT_INTERP`, and the
installer's loader symlink are all validated. What is refuted is only the choice of
**glibc** as the Space's libc.

**Resolved: the rootfs is Alpine/musl, and the runtime target is arm64 hardware.**
Measured on Android 16 / SDK 36, everything else held constant:

|  | glibc (debian bookworm) | musl (alpine 3.21) |
|---|---|---|
| arm64 hardware (Tab A9+) | SIGSYS | **exit 0** |
| x86_64 emulator (dev36) | SIGSYS | SIGSYS |

The dynamic loader runs in every cell; it is full libc startup that is killed. The
filter is defined per architecture, and Android's x86_64 policy is stricter than its
arm64 one. arm64-v8a is the shipping target, so this does not block the product — but
it does mean **the emulator can no longer validate the runtime**. Steps 8.4–8.6 run
against the tablet; the emulator stays the target for UI and Maestro work.

The whole Jupyter stack installs on musl from wheels (`--only-binary=:all:`) at the
same versions as the Debian image, so nothing about the server or kernel design
changes. `evidence/step8.3/musl-is-arm64-only.txt`.

Two further things the device taught us, both now encoded rather than assumed:
`PT_INTERP` may not point inside the rootfs (EACCES — the interpreter faces the same
SELinux restriction as the program, which is why `BaseSpaceInstaller` symlinks it into
`nativeLibraryDir`), and the rootfs runs without a chroot, so its absolute symlinks
(`/bin/echo` → `/bin/busybox`) dangle against the device root.

Before 8.x: `Environment` is renamed to `Space` across the domain, ports and
fakes — 3 files today, many more once the runtime lands.

---

---

## Audit findings (2026-08-06) — tracked, one commit each

Found by driving the built app with Maestro rather than by reading it, after Step 4b's cell
menu landed. All three are reproduced on hardware; none was visible to any existing test.
Each is closed the same way as a plan step: evidence, TDD, Maestro, commit.

| # | Finding | Status |
|---|---|---|
| A1 | **A markdown cell cannot be edited**, and "Insert markdown below" makes an unusable one | **Done.** `MarkdownCellField`, 4 instrumented tests, `evidence/audit-a1-markdown/` |
| A1b | **Markdown is never rendered** — parse it with commonmark-java into a model | **Done.** `MarkdownDocument`, 13 JVM tests. No Maestro: nothing draws it yet, so a flow would be theatre — A1c carries the device gate |
| A1c | Draw that model in Compose, and toggle rendered ↔ source | **Done.** `MarkdownText` + `MarkdownCell`, 7 JVM + 5 instrumented tests, `evidence/audit-a1c-markdown-render/` |
| A2 | **A new notebook is a dead end** — "No cells yet" and no way to add a first cell | **Done.** `toEditorUiState` gives a cell-less notebook one code cell, `evidence/audit-a2-empty-notebook/` |
| A3 | **Creating a notebook looks like it does nothing** — the new row lands above the fold | **Done.** Recents scrolls to a newly-topmost notebook; `HomeCreateTest` seeds 30 to reproduce it. `evidence/audit-a3-create-shows-up/` |

### A1 — markdown cells are read-only

`EditorCell` gives `CodeCellField` only to `CellKind.CODE`; markdown gets a plain `Text`. So an
existing markdown cell cannot be edited (typing into one does nothing), and the **"Insert
markdown below"** item shipped with the cell menu creates a cell that is a bare header with
nothing under it - invisible, untappable, unfillable. A regression introduced by offering a
menu item for a cell type the editor cannot edit.

Reusing `CodeCellField` wholesale is the wrong fix: line numbers, bracket matching, Python
auto-indent and *kernel completions* are all meaningless in prose. `MarkdownCellField` is a
plain text field; the only thing shared is `ReportEdits`, because when an edit is worth telling
the ViewModel about is a property of the notebook, not of the language.

An empty one shows a dimmed italic `Write markdown…`, which is how JupyterLab marks the same
case (`packages/cells/style/widget.css`, `jp-mod-emptyMarkdownPlaceholder`) - without it the
cell is a row with nothing in it, which is the bug's worst half.

**Rendering markdown stays open** — it is A1b and A1c below. JupyterLab keeps a `_rendered`
flag per markdown cell and calls `showEditor()` to go back to the source
(`packages/cells/src/widget.ts:2500`, `2563`); we show the source always.

### A1b / A1c — render markdown natively, with no WebView and no HTML

**Parsed with `org.commonmark:commonmark` 0.30.0.** BSD-2-Clause, so the same permissive footing
as ANTLR and grammars-v4 - attribution only, no copyleft, no native code. Verified against the
published jar rather than the README: `Parser` produces a `Node` tree, `AbstractVisitor` has a
`visit` for every node kind we care about, `Heading.getLevel()` and `Text.getLiteral()` are
there, and **`org/commonmark/renderer/` is a separate package we never touch** - the AST is
walked directly, so no HTML is ever produced and no WebView is needed. Latest version confirmed
from Maven Central's `maven-metadata.xml`, not from a search result.

**The shape follows JetBrains Jewel**, cloned to `reference/research_repos/intellij-community`
(`platform/jewel/markdown`), which solves exactly this problem for Compose:

- Blocks are a recursive model - `BlockQuote(children)`, `ListItem(children)` - rendered by
  recursive composables (`MarkdownBlock.kt`, `DefaultMarkdownBlockRenderer.kt`).
- **Leaf blocks hold an inline *model*, not an `AnnotatedString`** (`InlineMarkdown.kt`). The
  string is built at render time. That is what keeps the parsing layer free of Compose and
  testable on the JVM, and it is the reason A1b and A1c split where they do.
- Inline code takes `SpanStyle.background` (`DefaultInlineMarkdownRenderer.kt:171-179`), and a
  block quote's rule is drawn with `drawBehind` on the Column
  (`DefaultMarkdownBlockRenderer.kt:324`).

**On `TextLayoutResult` + `drawBehind` for inline backgrounds:** that is the upgrade, not the
baseline. `SpanStyle.background` paints a flat rectangle with no padding or rounding, which is
why measuring the span and drawing it yourself looks better - but Jewel ships the simple version,
and so does A1c until the flat one is shown to be the problem.

Scope: headings, paragraphs, emphasis, strong, inline code, code blocks, lists, block quotes,
thematic breaks and links. Images and inline HTML are out - a cell that needs them is rarer than
the cost of getting them right.

### A2 — a new notebook has no cells and no way to get one

`DiskNotebookRepository.createNotebook` writes `cells = emptyList()`, and the editor's empty
branch is a dead "No cells yet". The cell menu lives on cell headers, of which there are none.

**Fixed at the view layer, not at creation**, which is where JupyterLab puts it: when a model
with no cells is displayed it inserts a default one (`packages/notebook/src/widget.ts:606`), and
again when the last cell is removed (`:663`). One rule then covers a notebook just created, one
imported with no cells, and the last cell being deleted. `toEditorUiState` does it here.

It is not an edit, so the notebook is not marked dirty and is not written back - writing on open
would bump its timestamp and reorder Recents for having been looked at.

### A3 — the created notebook lands above the fold

Not a data bug: the row **is** added. `LazyColumn` keeps the viewport anchored to the items
already on screen, so a prepended row goes above it and Recents looks unchanged. Scrolling up
finds it; restarting the app shows it at the top.

Ruled out one layer at a time, and this is why it hid: the repository, `HomeViewModel`,
`HomeScreen` and the whole `MainNavigation` graph all pass instrumented tests for
create-then-appear, **on an empty directory**. A short list does not scroll, so nothing anchors.
It only reproduces with enough notebooks to fill the screen - proven by copying the tablet's 16
notebooks onto the emulator, where it then failed identically (17 files to 18, five `Untitled`
rows before and after).

JupyterLab **opens** a newly created notebook rather than only listing it, which would sidestep
the anchoring question rather than answer it. That is a product decision and was left alone;
both bugs are fixed as bugs.

**All three audit findings are closed.** The lasting lesson is A3's: every layer had a passing
test for create-then-appear while the bug was live, because they all ran against an empty
directory. A list that does not scroll cannot show a scrolling bug - so `HomeCreateTest` seeds
thirty notebooks, and any future test of a list behaviour should ask what size makes it real.

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
- **Maestro does not honour `ANDROID_SERIAL`.** With an emulator and a phone both
  attached it picks one of its own accord, which silently runs a flow on the
  device the fixture is *not* on. Pass `--device`; the step drivers do.
- Compose `contentDescription` surfaces as accessibility **text**, so Maestro
  matches it with a text selector, not `id:`. Maestro also regex-matches the
  *whole* string: `"42"` will not match `np.float64(42)`, `".*42.*"` will.
- **Density matters for WebView work.** The dev36 emulator is 160 dpi, where px,
  dp and CSS px are all the same number — which hides unit bugs completely. Force
  a real density with `adb shell wm density 320` (and `wm density reset`) rather
  than assuming 1x is representative.
- The Galaxy Tab sleeps and then wants its PIN, which no adb command can supply.
  `adb shell svc power stayon usb` keeps it awake once unlocked; if a flow fails
  at the very first assertion, screenshot before debugging anything else.
- Maestro `takeScreenshot` rejects `../` paths; capture into Maestro's output dir
  and copy the PNG into `evidence/<step>/`.
- Emulator handwriting IME steals focus from text fields and swallows Maestro
  `inputText`; disable it once per boot: `adb shell settings put secure
  stylus_handwriting_enabled 0` (+ `settings put secure stylus_handwriting 0`).
- VIEW-intent evidence (2.4): `scripts/step2-import-driver.sh` pushes
  `.maestro/fixtures/Intro to NumPy.ipynb`, scans it into MediaStore, and starts
  the app with `am start -a VIEW -d content://media/external/file/<id> -t '*/*'
  -f 0x1` (shell can grant MediaStore URIs but not DocumentsUI ones, whose path
  also does not carry the extension).

---

## Where we are, and what to do next (2026-08-06)

**Working end to end on a Galaxy Tab A9+ (Android 16, arm64):** bundled Alpine/musl rootfs →
interpreter exec'd from `nativeLibraryDir` → stock `jupyter_server` on loopback → kernel as a
subprocess → tap Run in the app → `print(6 * 7)` shows `42` and a numpy determinant shows
`np.float64(10.000000000000002)`. Outputs persist across app restarts, images and tracebacks
render, cells are editable with syntax highlighting.

**Done:** Steps 0–3, 4a, 4b (execution half), 7.1, 7.2, 8 (all sub-steps), 10 (all sub-steps),
5.1, 5.2, 5.3, 5.4a, 5.4b, 5.5a, 5.5b. **Step 5 is closed**, and with it the last fake behind
the `KernelGateway` port.

**Step 6 is already satisfied** by 4b and Step 10 (run → outputs, Running/Idle, interrupt,
execution count, against a real kernel), and **4b's cell menu is now done** bar collapse.

**Next: collapse** — `metadata.jupyter.source_hidden` and `outputs_hidden` round-tripped through
the nbformat DTOs, which closes both 4b's menu and one of 7.2's leftovers. Then 7.2's
`text/html`, and Step 11 (Console REPL).

**Also open, in rough priority order:**

- **Collapse**, the one piece of 4b's menu still missing, and 7.2's `text/html` — a pandas
  DataFrame still falls back to its `text/plain`. Collapse needs
  `metadata.jupyter.source_hidden`/`outputs_hidden` through the nbformat DTOs; clear outputs
  landed with the cell menu.
- **Packaging**: debug APK is 115 MB, 79 MB of it the rootfs (278 MB unpacked). Asset pack
  versus first-run download is undecided.
- **LSP** (see below) would supersede 5.5's completion rather than duplicate it.

### Reference studies, and what each settled

`reference/` is **gitignored**, so these do not travel with a fresh clone — the summaries here
are the durable record. `reference/*/FINDINGS.md` has the detail on this machine.

| Reference | Shape | What it does | What it settled |
|---|---|---|---|
| **Callisto** | notebook, N cells | CodeMirror 6, one WebView **per cell** | The exec model and the bridge protocol; also the memory cost we later rejected |
| **Acode** (6.4k★, MIT) | one file | CodeMirror 6 in a WebView, 23 langs, own LSP client fork | WebView editing is viable at scale — for *one* editor |
| **JupyterLab** | notebook, N cells | CodeMirror 6, **one page**, many instances | The architecture we would have taken had we stayed on WebViews — and, still, the reference for *behaviour*: the kernel protocol's message shapes (5.5a) and the cell actions' semantics (4b) were both read out of it rather than guessed |
| **GitHub Mobile** | code browser | **No editor at all** — server-highlighted HTML (primer `.pl-*`) in a WebView, plain `EditText` for edits | Not a model: it moves tokenising to a server, which offline forbids |
| **sora-editor** (LGPL-2.1) | IDE, one file | native view + tree-sitter via an **archived LGPL** binding | Ruled out on licence; its tree-sitter route ruled out on NDK |

### Language intelligence: the options, priced

Highlighting is lexical and settled (ANTLR, Step 5.1). *Structure* — folding, semantic
colouring, go-to-definition — needs a parser, and there are four, in the order they should be
considered:

1. **The ANTLR parser we already generate.** `PythonParser` is compiled today because
   `PythonLexerBase` requires it, and is otherwise unused. Free. Untested against half-typed
   code, which is the normal state of a cell.
2. **CPython itself, in the Space.** `ast.parse` is the authoritative parser, always exactly
   the installed version, no grammar to drift. Costs an IPC round-trip, which folding can
   afford.
3. **LSP in the Space** — the strongest, and the one to take. `python-lsp-server` (MIT) and
   `jedi-language-server` (MIT) both publish **pure-Python `py3-none-any` wheels**, so they
   install into the musl rootfs exactly as `jupyter_server` did. Gives semantic tokens,
   folding, symbols, diagnostics *and* completion over one protocol, and is widget-agnostic.
   `pyright` is out: it needs Node, which the Space does not have.
4. **tree-sitter** — best error tolerance by design, `io.github.tree-sitter:ktreesitter-android`
   is MIT and ships prebuilt runtime natives. Rejected only because grammars are not published
   as artifacts, so each language costs NDK + CMake, a `.so` per ABI, Play's 16 KB page-size
   obligation, and crashes that debug like C.

### Known residual risks

Everything here is measured. Where a line used to say "probably", it now says what
the device did. Evidence: `evidence/step8.7/loose-ends-closed.txt`,
`evidence/step8.7/x86_64-no-space-degrades.txt`.

- **psutil `net_if_addrs` segfaults — confirmed live, not theoretical.**
  `exit=139`, `psutil/__init__.py:2246`, extension module `psutil._psutil_linux`.
  `getifaddrs()` gets EACCES from Android's network restrictions and psutil does
  not check the return. The SIGSYS handler cannot help: this is a SIGSEGV from a
  bad dereference, not a refused syscall, and handling *that* would be far more
  dangerous than the crash.
  psutil cannot simply be dropped - it is an `ipykernel` dependency - but
  ipykernel only uses it for process management, which is why the whole stack
  works. So the hazard is exactly one function, reachable only if a user's own
  notebook calls it. Known, bounded, upstream.
- A refused syscall that a package genuinely *needs* would still be fatal.
  `mbind` was tolerable; nothing guarantees the next one is. The set is now
  discovered by running software and reading the reported refusals, which is a
  bounded and observable unknown rather than an open-ended one.
- The re-extract path was never exercised on device until the rootfs version was
  bumped, and it crash-looped the app: `TarGzExtractor` wrote *through* the stale
  loader symlink, which an app update leaves dangling because
  `nativeLibraryDir` moves. Fixed and regression-tested. Worth remembering that
  every install-time path needs a second install to be tested at all.
- `scripts/gradlew-jbr` still hardcodes this machine's JBR path, and there is
  no CI. Dev-environment debt: it slows a new machine down, it cannot corrupt a
  build.
- **One unexplained stall in cell execution, seen once.** On the first run of the 5.5b flow
  the kernel came up in 11 s and the cell then never finished within 169 s. Not reproduced:
  step10 passed on the same build minutes later, and the flow has since passed three times,
  once directly after a reinstall — which was the hypothesis. No cause known, recorded so a
  second sighting is recognised as a second rather than a first.
- **`PythonTypingRules` reads the line as text, not as tokens**, so a block opener with a
  trailing comment (`if x:  # note`) gets no extra level. Fixing it means asking the lexer
  where the comment starts, which is a dependency the transformation does not otherwise have;
  cheap enough to add if it turns out to grate, deliberately not paid for up front.
  Replacing a selection is likewise left alone rather than wrapped in the typed bracket -
  wrapping is a separate feature, and half-doing it would leave an unmatched closer.
- **`SyntaxKind.NUMBER` has the weakest contrast of the five colours.** It maps to
  `colorScheme.secondary`, which on the dark scheme sits close to the body text - visible
  in `evidence/step5.2/tablet/04-syntax-colours.png`. The other four read clearly. Worth a
  deliberate palette rather than more scheme roles, but not wrong today.
- **The server has no lifecycle.** `BaseSpaceKernelGateway` starts `jupyter_server` on
  first use and nothing ever stops it (~143 MB RSS held while backgrounded).
  `disconnect()` closes the socket only. Step 18 owns this. The old `SpaceProbe`
  that made it worse - starting a server on every launch - is gone as of 10.5.

### Closed by measurement, so nobody re-investigates them

- **Orphaned Space processes: not a problem.** The server and kernel are ordinary
  children of the app process, and both `am force-stop` and `am kill` take them
  with it - Android kills the whole process group. Nothing leaks across app death.
- **`ExecutionOutcome.TimedOut`: exercised on device, then retired.** It was driven
  with a live kernel running `time.sleep(5)` against a 2 s channel - alive and merely
  slow, which is the case the branch existed for - and returned `TimedOut(after=2s)`.
  The type itself went with `KernelChannel` at 10.5; `RealKernelGateway` has no
  timeout, because a cell that runs for an hour is a cell, not a failure. **Stopping
  one is the user's job, via interrupt.**
- **x86_64 degrades cleanly.** The app starts on the emulator, logs
  `no Space runtime here: no rootfs is built for ABI 'x86_64'; expected one of
  [arm64-v8a]`, and runs. No crash, and no stack trace for a designed outcome.
