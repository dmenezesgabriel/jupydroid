# JupyterDroid — Build Plan

Offline Jupyter notebook app for Android: Compose shell + stock `jupyter_server`/kernels
in a bundled rootless glibc rootfs on loopback.

Source material: `reference/xeus-android/FEASIBILITY.md`, `reference/xeus-android/STUDY_CASES.md`,
`reference/callisto/FINDINGS.md`.

**How we work lives in `CLAUDE.md`**, not here: the Working Agreement and its numbered rules,
the per-step procedure, the tooling table and the locked architecture. It is loaded into every
session, which this file is not, so the rules that gate every commit belong there. This file is
the *record* — what was decided, what was measured, what is done and what is next.

---

## ▶ Picking this up? Start here

**Next step: Step 18 — the server's lifecycle.** `jupyter_server` is started on first use and
nothing ever stops it (~143 MB RSS held while backgrounded). Step 17 landed the half beneath it:
kernels start through `POST /api/sessions` now, so reopening a notebook reattaches to its running
kernel, and a session is the thing a shutdown has to account for (`DELETE /api/sessions/<id>`).
**When** to stop the server is the open question and has no answer in JupyterLab — a browser tab
never owns the server's process — so it is decided against Android's lifecycle, not copied.

Read **["Where we are, and what to do next (2026-08-13)"](#where-we-are-and-what-to-do-next-2026-08-13)**
before anything else — it names what to read first, in what order, and which code changes. That
section is the only one kept current. **Everything else in this file is a record of how each step
was decided, not a statement about today**; where a step write-up disagrees with it, that section
is right and the write-up is history. Phases U and X are both done, whatever older paragraphs say
is "next".

---

## Product and platform decisions

The layering itself — hexagonal, single `:app` module, manual DI, a pure-Kotlin domain — is
locked and lives under "Architecture" in `CLAUDE.md`. What follows is everything the
architecture does *not* settle: what a Space is to a user, how execution actually works on
Android, and what the UX is aiming at.

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
| 4 | Editor shell | cell header Run/Stop + long-press context menu (run, insert code/markdown below, move up/down, duplicate, delete, clear outputs, collapse), Run All, busy indicator, dirty state (**modelled, not shown** — see "Known residual risks") |
| 5 | Cell editor | Compose-native `BasicTextField` + our own highlighting — broken into 5.1–5.5 below. **Was** CodeMirror-in-WebView; changed on measurement |
| 6 | Execute behaviour | Run → `KernelGateway.execute` → outputs; Running/Idle; Stop/interrupt; execution count (FakeKernel) |
| 7.1 | **Outputs persist** — nbformat round-trip of `outputs` and `execution_count` | **Done.** Fixed silent data loss: `toDomain`/`toFile` dropped both, so saving a notebook another tool had run destroyed its results. `evidence/step7.1/` |
| 7.2 | Output rendering | **Done.** PNG decoded and drawn, ANSI colour codes stripped, errors in a themed block (`evidence/step7.2/`); `text/html` parsed with jsoup and drawn as Compose, tables included, with the mime order read off JupyterLab's `defaultRank` (`evidence/step7.2-html/`). Only **collapse** is left, and it belongs with 4b |
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
  are done** — Run All, Stop and the indicator against a real kernel on the tablet
  (`evidence/step4b-run-all/`), running a single cell against a real kernel by
  `step10-run-cell-real-kernel` (`evidence/run-cell-one-gate/`). `evidence/step4b/`
  is the older half and predates the runtime: its screenshot is `answer = 42` echoed
  back by `FakeKernelGateway`, kept as history rather than as a live gate. See
  "One gate for running a cell" below. **The cell menu is done too** — insert code/markdown below,
  move up/down, duplicate, delete, clear outputs, persisted and read back off the
  device (`evidence/step4b-cell-menu/`). **Collapse is done too** — both halves, persisted
  through the nbformat DTOs; see "Collapse" below, including the correction to what this
  line used to claim the metadata keys were. **Step 4b is closed.**

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
| A1c | Draw that model in Compose, and toggle rendered ↔ source | **Done.** `MarkdownText` + `MarkdownCell`, 7 JVM + 6 instrumented tests, `evidence/audit-a1c-markdown-render/`. Left the tap opening an *unfocused* editor; fixed 2026-08-07, `evidence/audit-a1-focus/` |
| A4 | **A markdown cell being edited is barely readable** — black on black in the dark theme | **Done.** `markdownSourceStyle()`, asserted in both schemes. `evidence/audit-a4-markdown-contrast/` |
| A5a | Line numbers in the markdown editor | **Done.** Four lines of wiring; `evidence/audit-a5-markdown-editor/a5a-line-numbers.png` |
| A5b | Syntax highlighting for markdown source | **Done.** `MarkdownSyntax` off commonmark's source spans, 9 JVM tests. `evidence/audit-a5-markdown-editor/` |
| A2 | **A new notebook is a dead end** — "No cells yet" and no way to add a first cell | **Done.** `toEditorUiState` gives a cell-less notebook one code cell, `evidence/audit-a2-empty-notebook/` |
| A3 | **Creating a notebook looks like it does nothing** — the new row lands above the fold | **Done.** Recents scrolls to a newly-topmost notebook; `HomeCreateTest` seeds 30 to reproduce it. `evidence/audit-a3-create-shows-up/` |
| A6 | **Editing a notebook does not update its "last updated" time** | **Done.** `updatedAt` is the file's mtime, the way Jupyter reads it; `NotebookRepositoryContract` holds both repositories to it. `evidence/audit-a6-updated-at/` |

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

**A1c left the tap half-done, and only the tablet said so (fixed 2026-08-07).** Making rendered
the resting state meant a tap now had to *open* the editor, and it did - as an unfocused field.
The keystroke after the tap went nowhere; you had to tap a second time to get a caret. Every
Compose test passed throughout, because `performTextInput` focuses the node itself, so the one
thing the real gesture depended on was the one thing no test exercised. `a1-markdown` was the
only thing that could have caught it, and it had not been re-run since A1c landed.

`MarkdownCellField` now takes the caret on arrival (`FocusRequester` + `LaunchedEffect`), which
is safe precisely because the field only exists when someone asked to edit: `rememberMarkdownRendered`
starts at `true`, so nothing composes a field unbidden. `MarkdownCellTest.openingTheEditorPutsTheCursorInIt`
asserts focus rather than the callback, and fails without the fix. Evidence: `evidence/audit-a1-focus/`.

**Two flows had to change with it, and that is the fix being real rather than a regression.** A
caret means a keyboard, and on a 19-line cell the header — with the Run button on it — scrolls
out of view. `a1c-markdown-render` could no longer reach Run, and `a4-markdown-contrast`'s
evidence screenshot became half keyboard. Both now `hideKeyboard` and scroll back up before
doing the thing they are about, which is what a person does. `a1c` screenshots the keyboard-up
state first, deliberately: that *is* what a tap leaves you in now.

### A4 — the markdown editor's text is not themed

`MarkdownCellField` passes `LocalTextStyle.current.merge(typography.bodyMedium)` as its text
style, and **neither carries a colour**. A `BasicTextField` given `Color.Unspecified` draws
**black** - it does not fall back to `LocalContentColor` the way `Text` does. So the source is
black in both themes: readable on light, nearly invisible on dark.

Seen on the tablet in both, which is the only way this shows up: the light screenshot looks
fine, so a single-theme check passes it.

Second, smaller half: the editing state has no background, while a code cell sits on a
`surfaceVariant` panel. Nothing says "this is a field you are typing in" - and the panel belongs
to the *editor* only, since rendered prose in a box would be wrong.

### A5 — the markdown editor gets what the code editor has

Split because the two halves cost very different amounts, and only one of them has a decision
in it.

**A5a — line numbers.** Nearly free: `Modifier.lineNumbers(text: CharSequence, …)` and
`gutterWidth` are already language-agnostic, and `LineNumbersTest` already covers the maths
(`logicalLineStarts`, `gutterDigits`). Wiring is the same four lines `CodeCellField` has — a
`layout` state, `onTextLayout`, `gutterWidth`, the modifier. **No new unit test is honest here**;
nothing new is computed, so the gate is the screenshot, as it was for 5.4b.

**A5b — highlighting.** Cheaper than Python was, because **no second lexer is needed**:
commonmark already carries source offsets. `Parser.builder().includeSourceSpans(BLOCKS_AND_INLINES)`
turns them on and `SourceSpan.getInputIndex()` / `getLength()` read them — both verified against
the 0.30.0 jar. So `MarkdownSyntax` is a walk over the AST we already parse, dropped into the
existing `SyntaxHighlighter` seam.

Two costs that are real:

- **`SyntaxKind` is Python-shaped** — `PLAIN, KEYWORD, STRING, NUMBER, COMMENT, FUNCTION`. A
  heading is not a `KEYWORD`, so mapping markdown onto those names would be a lie. The enum
  grows into the shared vocabulary of every language the editor highlights, each highlighter
  emitting the subset that means something to it, and `syntaxColors()` grows with it.
- **`HighlightTransformation` is `private`** in `CodeCellField.kt` and has to be extracted to
  be shared. Mechanical, but it is that file's only real coupling.

Worth remembering while doing it: `MarkdownCellField` exists *because* code machinery is wrong
for prose (A1), and rendering is now the resting state — so this only shows while someone is
editing source. It is wanted, but it is not free of that tension.

### Step 7.2's `text/html` — rendered natively, with no WebView (2026-08-06)

The last gap in Step 7.2: a pandas DataFrame fell back to its `text/plain` ASCII grid. The plan
used to say this "needs the WebView from Step 5" — that WebView was deleted at 5.2 on memory
grounds, and A1b/A1c had since shown prose renders fine as Compose. So it is rendered the same
way markdown is, in three commits:

| # | Slice | Gate |
|---|---|---|
| 7.2c-0 | `MarkdownBlock`/`MarkdownText` → shared `RichBlock`/`RichText` in a `richtext` package; `MarkdownDocument` becomes one front end onto it | **Done.** Pure rename; the existing suites plus the A1c flow re-run on the tablet |
| 7.2c-1 | `HtmlDocument` (jsoup) → the same `RichBlock`s, and the mime order | **Done.** 19 JVM tests; `display(HTML(…))` on the tablet in both themes |
| 7.2c-2 | `RichBlock.Table` + a measured `RichTable` | **Done.** 6 JVM + 4 instrumented tests; a verbatim pandas `_repr_html_` through a real kernel |

**The mime order is JupyterLab's, read rather than guessed.** Every factory declares a
`defaultRank` and `preferredMimeType` walks them ascending, first present in the bundle wins
(`packages/rendermime/src/factories.ts`; `registry.ts:105`): `text/html` 50, `text/markdown` 60,
`text/latex` 70, `image/svg+xml` 80, images 90, javascript/stderr 110, `text/plain` 120. So
**html outranks an image**, which the old hardcoded png-then-text order would have got wrong.
Only the three we can draw are listed — a registry holds the types it has a renderer for.

One deliberate difference: ours **keeps looking** when a type is present but unusable — a
truncated image, markup that parses to nothing. JupyterLab picks by presence, not by whether the
render then worked, and the undecodable-PNG test predates this.

**jsoup 1.23.1** (MIT, 529 KB, no runtime deps, Java 8 bytecode) — the same footing as commonmark
and commons-compress. Hand-rolling a lenient HTML tokenizer is what this repo already refused for
tar and for Python lexing. Only its tree is used; the serialising half is never touched, exactly
as `org.commonmark.renderer` is never touched.

**The trap is `<style scoped>`**, which pandas wraps around every DataFrame: a walk that treats
unknown elements as text renders its CSS as prose. `<style>` and `<script>` are dropped whole.

**The table look is read off JupyterLab's stylesheet** (`packages/rendermime/style/base.css:426-476`):
no gridlines at all, a rule under `thead`, striped body rows, bold `th`, `0.5em` padding, cells
aligned right — its output columns are usually numbers. Columns are sized to their widest cell by
a `Layout`, and the table scrolls sideways rather than shrinking, matching
`.jp-OutputArea-output`'s `overflow-x: auto` and the choice a code block already makes.

**Header-ness is per cell, not per row** — pandas puts its row index in a `<th>` inside `<tbody>`,
so "is a header cell" and "is the header row" are two different questions. Bold follows the cell,
the rule follows the row.

Three things only a screenshot caught, and they are the argument for the picture gate:

- The first build **ruled every `th`**, drawing a line across the middle of the table through the
  index column.
- It **tinted the first body row** where `tbody tr:nth-child(even)` tints the second.
  `stripedRows` is extracted and unit-tested so that off-by-one is now arguable on the JVM.
- The flow **passed every assertion while screenshotting nothing but source** — the verbatim
  38-line fixture pushes the output below the fold. `scrollUntilVisible` is what makes the
  assertions and the picture agree about what is on screen; the next run then failed
  `assertVisible` on that same state, so it was luck either way.

**pandas is not in the rootfs** (`tools/rootfs/requirements.lock`), so the device gate carries a
verbatim `DataFrame._repr_html_` through `IPython.display.HTML` — same mime bundle, same code
path, no image rebuild. The markup is captured in `PandasDataFrameHtml.kt` and shared by the JVM
tests and the Maestro fixture, so neither can drift into markup written from memory.

Out of scope and deliberately: `<img>`, forms, iframes, CSS, `colspan`/`rowspan`. `image/svg+xml`
is unranked because nothing draws it — JupyterLab marks it unsafe (rank 80) and we have no trust
model, which is fine while every type we render is one of its safe ones.

### Collapse — the last piece of 4b's cell menu (2026-08-06)

A code cell can fold its **source** and its **outputs**, independently, and both folds are
written into the notebook so they survive the app being killed. Two commits, `evidence/collapse/`.

| # | Slice | Gate |
|---|---|---|
| C1 | `Cell.sourceHidden` → `metadata.jupyter.source_hidden`, menu item, placeholder | **Done.** 7 DTO + 3 edit + 5 instrumented tests; tablet, both themes |
| C2 | `Cell.outputsHidden` → `metadata.collapsed`, menu item, placeholder | **Done.** 5 DTO + 3 edit + 6 preview + 6 instrumented tests; tablet, real kernel |

**The two keys are not the symmetric pair the names suggest, and this plan had it wrong.** Step
4b's entry said `metadata.jupyter.source_hidden`/`outputs_hidden`. Only the first is right:

| Folded | Key | Why |
|---|---|---|
| source | `metadata.jupyter.source_hidden` | JupyterLab `saveCollapseState`, `packages/cells/src/widget.ts:472-492` |
| outputs | `metadata.collapsed` | `jupyter.outputs_hidden` exists in the nbformat types and is exactly what JupyterLab **refuses** to write — "Do not set jupyter.outputs_hidden since it is redundant" (`widget.ts:1437`, citing nbformat issue 137) — and never reads: `loadCollapseState` calls `collapsed` "the source of truth" (`:1452-1458`) |

Honouring the tidier-looking key would have made every notebook we save disagree with the front
end it is opened in. Found by reading, not by guessing, which is the third time in this project
that the obvious answer was the wrong one.

Both keys are written **only when true**: Jupyter *deletes* the key to un-collapse rather than
writing `false`. That falls out for free — an all-empty metadata object equals `CellFile`'s
default and kotlinx omits it — so an expanded cell writes no metadata key at all.

**Code cells only.** Every JupyterLab collapse action checks `cell.model.type === 'code'`
(`packages/notebook/src/actions.tsx:1877`, `:1957`), and a markdown cell already has two states
of its own from A1c. One toggle per fold rather than a hide and a show, which is why JupyterLab
carries a `toggleOutput` beside its two command pairs.

**Folding is not clearing.** The two sit next to each other in the menu because they read as a
pair and are not: `Clear outputs` throws the results away and resets the count, `Collapse
outputs` only hides them. The saved file proves it — `"collapsed":true` with
`"text":["alpha\n","omega\n"]` still beside it.

**The placeholder previews a line of what is folded**, which is JupyterLab's and not decoration:
a folded notebook of bare "collapsed" stubs is unreadable. Its two placeholders disagree on the
rule — the input one takes `source.split('\n')[0]` (`widget.ts:630`), the output one the first
*non-empty* line (`:1283-1289`) — and both use the non-empty rule here, deliberately, because
line zero taken literally previews a cell that opens with a blank line as nothing.

Two things the flows taught, both about writing the flow rather than the feature:

- **A flow that ends in the wrong state proves nothing.** The first collapse flow expanded last,
  and the driver's `run-as cat` — the only evidence of *which key* was written — showed a cell
  with no metadata at all while every assertion passed. Both flows now end folded.
- **A "not visible" assertion can be matching the source field.** The output fixture first
  printed `second line 120` from a line reading `print("second line 120")`, so the assertion
  could never distinguish folded from unfolded. It prints `omega` from `print("om" + "ega")`
  now — the output text appears nowhere in the source.

Collapse left one thing open — the cell metadata beside its keys was still being dropped on save.
That is fixed in the section below, and was eight losses rather than one.

### A save must not shred the file (2026-08-06)

Found while closing collapse and fixed as its own commit. Saving **rebuilt** each notebook from
the fields this app models, so everything else went. Measured with a characterization test
against a notebook carrying what real tools write, rather than assumed:

| Lost | Written by |
|---|---|
| cell `tags`, `slideshow`, `scrolled`, `nbgrader` | nbgrader, RISE, JupyterLab itself |
| other keys inside the `jupyter` namespace | anything that collapses outputs |
| a cell's `attachments` — **every pasted image** | JupyterLab's paste |
| an output's own `metadata` — a figure's rendered size | matplotlib |
| an **object-valued mime payload** — every widget view | jupyter-widgets |
| notebook `language_info`, `widgets`, `authors` | the kernel, jupyter-widgets, nbformat |
| extra `kernelspec` fields, including `display_name` | conda/venv kernels |
| unknown top-level keys | any newer tool |

**The cause is a category error about the format, and nbformat says so in its own types.** Every
interface in JupyterLab's `packages/nbformat/src/index.ts` extends `PartialJSONObject` —
`IBaseCell`, `IBaseCellMetadata`, `INotebookMetadata`, `IBaseOutput`, `IMimeBundle`. "These keys,
plus anything else" is the contract. A reader that keeps only what it recognises and a writer
that rebuilds from those keys is a shredder, however careful each field mapping is.

**So the writer stopped rebuilding.** `NotebookJsonWriter.toFileJson` lays the object as read
back down and writes the owned fields over it. Three details that are not obvious:

- **Owned keys are excluded from the copy, not overwritten after it**, because some must end up
  *absent*: an un-collapsed cell has no `collapsed` key at all, and a markdown cell has no
  `outputs`, so a stale value copied first would survive.
- **Outputs the app did not change are written back byte for byte** — `outputsJson` compares the
  file's outputs, parsed, against the domain's, and keeps the original array when they match.
  Regenerating them is what dropped output metadata and flattened widget payloads.
- **`display_name` is kept if the file had one.** Overwriting someone's "Python 3.11 (conda)"
  with our default is the same loss in miniature.

**The preserved JSON rides on the domain types** (`Cell.preserved`, `Notebook.preserved`) rather
than being merged from the file at save time, and that is the one real design decision. A
duplicated cell has a **new id**, so there would be nothing on disk to merge it with — and
JupyterLab's duplicate keeps the metadata, because it clones the cell's JSON. It is what protobuf
calls unknown-field retention, and `copy()` carries it for free.

15 round-trip tests: read-and-write untouched, through an edit, through a collapse, through a
duplicate, through a re-run, and for cells the app created itself. Device gate is the file rather
than the screen — `scripts/foreign-metadata-driver.sh` drives a real edit on the tablet and then
checks 13 foreign keys key-by-key in the file it reads back off the device
(`evidence/foreign-metadata/keys-kept.txt`).

#### A mime payload's shape, which nbformat defines twice and asymmetrically

The round-trip fix left one gap and it is now closed. A bundle was `Map<String, String>`, so a
payload that is not a string had to be flattened to reach the domain — `KernelOutputs` used
`toString()`, turning a live `application/json` display and every jupyter-widget view into a
*string* of JSON. Still valid JSON in the file, and a shape no Jupyter tool can render back.

The bundle is `Map<String, JsonElement>` now, which is JupyterLab's own model: `convertBundle`
copies each value through `extract`, returning primitives as they are and deep-cloning anything
else, stringifying nothing (`packages/rendermime/src/outputmodel.ts:334-356`).

**The two line rules come from nbformat itself** — read out of the installed library, not
guessed — and they are not symmetric:

| | Rule | Function |
|---|---|---|
| read | join a list into a string **unless** the key is a JSON mime (`application/json`, `application/…+json`) or the list holds anything but strings | `_rejoin_mimebundle` |
| write | split a string into lines **only** when the key starts with `text/`, or is `application/javascript` / `image/svg+xml` | `_split_mimebundle`, `_non_text_split_mimes` |

So `image/png` is one long base64 string on disk while `text/plain` beside it is a list of
lines, and a widget payload is an object neither rule touches. Writing an image split into
lines, or a text mime as one string, produces a file no Jupyter tool would have written.

The renderer picks a mime only if its payload is a string and falls through otherwise: every
type in the ranked list is a text mime, so an object under one is markup this app cannot draw —
and JupyterLab reaches for `String(model.data[mime])` only *after* picking a mime it has a
renderer for. Device gate is the file again: a real kernel emitting a raw bundle, and
`evidence/mime-shapes/shapes-kept.txt` checks each payload's shape in what landed on disk.

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

### A6 — every write is an update, the way Jupyter means it (closed, 2026-08-07)

**Was:** `metadata.jupydroid.updated_at`, written on create and bumped by nothing.
`saveNotebook` wrote the notebook it was handed and `EditorViewModel.persist()` did
`notebook.copy(cells = …)`, carrying the old value straight through. Home sorts
`sortedByDescending { it.updatedAt }` and labels each row from the same field, so a notebook you
had just typed in neither rose in Recents nor stopped claiming it was touched days ago.

**The product question answered itself in the source.** Jupyter's `last_modified` is
`os.lstat(os_path).st_mtime` and nothing else — read out of the `jupyter_server` this app ships
(`services/contents/filemanager.py:266-305`), not remembered. Nothing is stored in the notebook,
so there is no notion of an edit that does not count: everything that changes a notebook writes
the file, and writing the file *is* the update. JupyterLab leans on that hard — comparing
`last_modified` on disk against its own copy is how it detects a file changed underneath it
(`packages/docregistry/src/context.ts:769-776`).

**So the app stopped keeping its own.** `updatedAt` is gone from the notebook JSON and gone from
`Notebook` itself: when a notebook was last written is a fact about the stored file, not about
the cells, and a field on the domain type is one a caller can set and a writer must remember to
honour — which is precisely how this broke. It lives on `NotebookSummary`, filled in from
`file.lastModified()` when the repository lists what is on disk. Edits, runs, renames and
creation all count, and none of them has to say so.

Files written before the change still carry the old key; `ignoreUnknownKeys` reads past it and
the next save drops it, because this app owns the `jupydroid` namespace and a key it no longer
writes should not linger.

**`NotebookRepositoryContract`** holds the fake and the disk one to it together — both were
wrong in the same way, and the fake is what every ViewModel test runs against. Written as
ordering ("touch the older notebook and it must not still sort below the newer one") rather than
as a comparison against the clock, so it needs no clock to inject and no sleep to be reliable.
Four of its six tests failed before the fix.

Device gate: `a6-edit-updates-recents` on the tablet. The driver backdates the fixture with
`touch -t 202001010000`, because a fixture it has just pushed has this moment's mtime and there
would be nothing to see. One typed character turns the row from "1 Jan" into "just now", and the
driver reads the file back to show the mtime moved and the stale key is gone
(`evidence/audit-a6-updated-at/`).

### One gate for running a cell (2026-08-07)

`step4b-run-cell` is **deleted**, along with `scripts/step4b-run-cell-driver.sh`. It was written
against `FakeKernelGateway`, whose `execute` echoes the cell's source back as stdout, and its
fixture cell is `answer = 42` — an assignment a real kernel answers with nothing. So the flow
waited on `Cell output`, the bare contentDescription `CellOutputs` sets, which the echo always
satisfied and the real kernel never did. `evidence/step4b/02-cell-output.png` is the fake caught
in the act: `answer = 42` appears twice, once as source and once as its own "output" — and
`evidence/step4b/maestro.log:13` records the device as `emulator-5554`, which is x86_64, which
is where `MainActivity` falls back to the fake because the rootfs is arm64-only.

Step 10 already knew. `step10-run-cell-real-kernel.yaml` was written *because* the 4b flow could
not be reused, said so in its header, and asserts values the kernel computes — `42` from
`print(6 * 7)`, and a numpy determinant. It was run green on the tablet **before** the deletion
rather than after (`evidence/run-cell-one-gate/`), so the coverage was demonstrated, not assumed.
Repairing 4b's flow instead would have meant a third fixture doing what step10's already does;
its own fixture could not simply be edited, because `step4-cells.ipynb` is shared with
`step4-editor` and `step5-edit-cell`, which assert `answer = 42` as *source* on purpose.

**The general rule this leaves:** after a Run, never assert that an output *container* exists.
`Cell output` proves only that something rendered, and something always renders — the fake can
make it true without a kernel. Assert a value the kernel had to compute.

---

## Defects found on device (2026-08-07) — closed

Two, reported from driving the tablet. Both are about the same thing: how a notebook gets from
Recents to the editor, and what its identity is once there. Closed the same way an audit finding
is — reference research first, TDD, Maestro, evidence, one commit each.

| # | Finding | Status |
|---|---|---|
| D1 | **Opening a second notebook shows the first one** — and an edit made there would have been written into the first notebook's file | **Done.** `evidence/notebook-switch/` |
| D1b | A **double tap** pushes two back-stack entries for one notebook, which share one `ViewModelStore` | **Done.** Same evidence |
| D2 | **A notebook's title cannot be edited from the notebook**, only by long-pressing its row | **Done.** `evidence/rename-in-editor/`, `evidence/rename-dialog/` |

### D1 — the editor's ViewModel was scoped to the Activity

`NavDisplay` was given no `entryDecorators`, and its default is
`listOf(rememberSaveableStateHolderNavEntryDecorator())` and nothing else (`NavDisplay.kt:184-185`
in the `navigation3-ui:1.0.1` sources). So `LocalViewModelStoreOwner` inside an entry was
`MainActivity`, and `viewModel { … }` with no key resolves on the class name alone and only runs
its initializer on a cache miss — the second visit got notebook A's instance and dropped B's id.
`EditorViewModel` loads once, in `init`, so it could not have noticed even in principle.

The fix is the pair of decorators the **lifecycle 2.10.0** release notes publish, in that order:
saveable state first, ViewModel store inside it. `decorateEntry` folds right, so the first is the
outermost, and the ViewModel decorator reads its `SavedStateRegistryOwner` from its surroundings.
`viewModel(key = notebookId)` would have hidden the symptom while leaving every editor ViewModel
alive on the Activity for the life of the process.

**Keying per document rather than per type is what every reference does**, and it is worth stating
because it is the whole bug: JupyterLab's `_findContext` keys on `(normalize(path), factoryName)`
(`packages/docmanager/src/manager.ts:545-558`) and links widgets to contexts with instance-keyed
`AttachedProperty`s; IntelliJ hangs notebook state off the open editor under `EDITOR_NOTEBOOK_KEY`
and disposes it with that editor. Nothing anywhere is keyed by class.

**The version risk that did not fire.** `ViewModelStoreNavEntryDecorator` 2.10.0 carries
`require(lifecycle.currentState == INITIALIZED)` over `LocalSavedStateRegistryOwner.current`, and
nothing in `navigation3-runtime` 1.0.1 **or** 1.1.5 provides a per-entry `SavedStateRegistryOwner`
— checked by `javap` over both aars, and the check is gone in `androidx-main`. It was expected to
throw and never did, on device or in the instrumented test, so `lifecycleViewmodelNav3` stays at
2.10.0. Recorded so nobody re-derives the worry.

### D1b — `openOrReveal`, because two entries share one store

`MutableList<NavKey>.openNotebook` returns early when that notebook is already on top. This is
JupyterLab's `openOrReveal`, which activates the widget already showing a path rather than opening
a second (`packages/docmanager/src/manager.ts:481-503`, and the opener guards again with
`if (!widget.isAttached)`).

It is not tidiness. Both nav3 decorators key on `NavEntry.contentKey`, which defaults to
`key.toString()`, so two entries for one notebook share a single `ViewModelStore` **and** a single
`SaveableStateHolder` slot — and popping the first clears state the second still needs. A double
tap is enough, because Recents is still on screen and still hittable while the transition runs.

### Scoping the ViewModel made `onCleared` real, and two things then had to exist

The editor's ViewModel was never cleared before, so neither of these was missed:

- a save still inside its 500 ms debounce died with `viewModelScope`;
- the kernel socket was never closed, leaking one per notebook opened.

`onCleared` flushes then disconnects, in that order — JupyterLab's `DocumentWidgetManager.onClose`
awaits `context.save()` before `widget.dispose()`, and disposing the context disposes the session
(`packages/docmanager/src/widgetmanager.ts:335-388`, `packages/docregistry/src/context.ts:205-220`).
It flushes **only when something is owed**, for A6's reason: a write is what makes a notebook
"updated", so saving on the way out would push a notebook to the top of Recents for having been
looked at. JupyterLab gates its autosave on `context.model.dirty` for the same reason.

**The scope has to outlive the ViewModel**, because `ViewModel.clear()` closes the closeables —
`viewModelScope` among them — and only *then* calls `onCleared` (`ViewModel.jvm.kt:66-68`). It
lives on `AppContainer`, which is where the Android guidance puts an external scope: something
longer-lived than the screen. `saveNow()` is deleted; it was called from no production code and
`onCleared` is now the escape hatch its KDoc described.

**Closing the socket is not shutting the kernel down.** `SessionContext.dispose()` shuts a kernel
down only when `kernelPreference.shutdownOnDispose` is set, and it defaults to false
(`packages/apputils/src/sessioncontext.tsx:679-709`; the `kernelShutdown` setting in
`notebook-extension/schema/tracker.json` is `false` too). Ours disposes the client connection, the
same half. **What we do not yet have is the other half** — see the open item below.

### D2 — the title is renameable from the notebook

The anchor is the document title, which is where JupyterLab puts it: `docmanager:rename` hangs off
`"selector": "[data-type=\"document-title\"]"`
(`packages/docmanager-extension/schema/plugin.json:73-77`), labelled "Rename Notebook…". A tap is
the touch analogue of that right-click, and an `Icons.Filled.Edit` beside the title is what says
the title is a control — Step 4b's lesson, applied without a second menu.

JupyterLab's **single-document mode**, its closest thing to a phone, instead edits the title inline
in the shell bar (`packages/application/src/shell.ts:2767-2858`). Considered and not taken: its two
inline paths disagree about what a blur means — the file browser **commits** on blur
(`listing.ts:3958-3961`), the title bar **discards** (`shell.ts:2790`) — and on a touch screen a
blur is any stray tap, with no Cancel to undo it.

Home's dialog moved to `ui/RenameNotebookDialog` so both callers share one, and reading
`DocumentManagerDialogs.rename` turned up three things ours had wrong:

| | JupyterLab | Was here |
|---|---|---|
| the field takes focus on open | `focusNodeSelector: 'input'` | nothing — no caret, first keystroke lost |
| the stem is selected, the extension is not | `setSelectionRange(0, value.length - ext.length)` | nothing selected |
| an unchanged name is a silent no-op | `if (!newPath \|\| newPath === fileName) return null` | renamed anyway |

The second earns its keep on `untitled.py`, which `NEW_PYTHON_FILE_TITLE` creates. `titleStemEnd`
states the rule once — JupyterLab has three that disagree (`PathExt.extname` in the dialog,
`lastIndexOf('.')` in the file browser, `indexOf('.')` in the title bar) and we take the dialog's.

**What does not transfer, deliberately:** `isValidFileName`'s `/ \ :` rule, and the overwrite
prompt on a 409 (`dialogs.ts:202-250`). There the title *is* the filename. Here a notebook is
`<uuid>.ipynb` and its title lives in `metadata.jupydroid.title`, so a title cannot corrupt a path
and two notebooks may share one. Non-blank plus changed is the whole rule.

`renameNotebook` moves the **in-memory** copy as well as writing through the port, because
`persist()` writes `notebook.copy(cells = …)` — a rename that only reached the repository would be
overwritten by the very next keystroke. That is what `renamingSurvivesTheNextCellEdit` forces.

### Three tests that were green against the wrong thing

All caught before landing, and the reason to distrust a first green.

- `leavingWithAPendingEditStillWritesIt` passed against an `onCleared` that did nothing:
  `advanceUntilIdle` ran virtual time past the debounce and the ordinary scheduled save did the
  writing. It uses **`runCurrent`** now, which advances no time at all.
- `tappingTheSameNotebookTwiceOpensOneEditor` passed with no guard at all: `performClick` lets the
  composition settle, so by the second tap Recents was gone and the title it matched was the
  *editor's* app bar. Freezing `mainClock.autoAdvance` across both taps is the real gesture — and
  it then failed, which is how D1b was found.
- The first `notebook-session-ends` fixture waited for `42` from `carried = 6 * 7`, an assignment
  that prints nothing. `answer = 42` all over again, one section above.

Each surviving assertion was re-checked by mutation: emptying `onCleared` fails two of the teardown
tests, and deleting one line of `renameNotebook` fails `renamingSurvivesTheNextCellEdit`.

### Two things the flows taught

- **`hideKeyboard` with no keyboard up is a system BACK.** The rename dialog owns the keyboard and
  takes it with it when it closes, so `hideKeyboard` afterwards popped the editor — and the next
  `tapOn: "Back"` matched the **system navigation bar's** back button, whose contentDescription is
  also `"Back"`, and left the app for the launcher. Only reach for it when a keyboard is actually up.
- **A cell you are going to assert on has to print.** See above; this is the third sighting.

### One refactor detekt asked for, and it was right

`EditorViewModel` reached 12 functions against a max of 11. Raising the threshold would have been a
workaround, so the debounce moved out into `NotebookSaveDebounce` — which is the shape JupyterLab
already uses: `SaveHandler` is constructed per document context, owns its own timer, and is
disposed with that context (`packages/docmanager/src/savehandler.ts:18-30`, `62-69`).

## Phase U — an IntelliJ visual language, built for touch (2026-08-07)

The app had no visual identity of its own: `dynamicColor = true` meant the wallpaper chose every
colour, `theme/Color.kt` was still the Android Studio template, and there was no cell anatomy,
no selection, no modal model and one layout at every width. Phase U fixes that in seven slices —
tokens, cell mode, cell chrome, execution status, the touch action surface, IME correctness and
adaptive layout — before the file explorer (Phase X) and the terminal (Phase T) are built on it.

**IntelliJ's dp are ratios, not sizes.** `DefaultNotebookEditorAppearanceSizes` gives a 24px cell
toolbar and a 4px/2px stripe. The proportions port; the sizes do not — a 24px control is not
usable with a thumb, so every interactive size comes from Material's 48.dp minimum instead. The
same substitution applies to hover: IntelliJ shows the run gutter and the cell toolbar when
`isHovered || isSelected`, a finger never hovers, so **hover collapses into selection** — which
IntelliJ's own `showToolbarForSelectedCell` setting shows was already a supported path.

### U1 — the palette, read out of IntelliJ rather than derived from a role (done)

`EditorPalette` / `SyntaxPalette` (`theme/EditorPalette.kt`), two instances
(`theme/IntelliJPalettes.kt`), in scope through `LocalEditorPalette`. `JupyterDroidTheme` no
longer offers dynamic colour, and its Material roles now come off the same ramp as the editor
tokens so bars, menus and dialogs sit in the cells' world rather than beside it.

**This closes the `SyntaxKind.NUMBER` contrast item** under "Known residual risks", and closes it
at the cause rather than the symptom: the colours came from `MaterialTheme.colorScheme` roles, so
`NUMBER` was `secondary` — whatever the user's wallpaper made it — and *no test could ever have
asserted it*. `EditorPaletteTest` now pins every syntax colour's WCAG ratio against the cell
background it is drawn on.

**Where the values come from**, all in the reference tree, none eyeballed:

| | |
|---|---|
| Syntax + editor colours | `platform/platform-resources/src/themes/expUI/expUI_{dark,light}Scheme.xml` |
| Surface greys and accents | `platform/platform-resources/src/themes/expUI/expUI_{dark,light}.theme.json` |
| Which markdown category is which colour | `plugins/markdown/core/src/.../MarkdownHighlighterColors.java` |

These are the **New UI** schemes, not classic Darcula — what a current IntelliJ actually shows.

Four things the sources settled that guessing would have got wrong:

- **Hex is stored without leading zeros.** `value="33b3"` is `#0033B3`, `value="67d17"` is
  `#067D17`. Read literally they parse as short, wrong colours.
- **Markdown has almost no colours of its own.** `MARKDOWN_CODE_SPAN` and `MARKDOWN_BLOCK_QUOTE`
  inherit `DEFAULT_STRING`, `MARKDOWN_LIST_MARKER` inherits `DEFAULT_KEYWORD`, and
  `MARKDOWN_HEADER_LEVEL_1..6` inherit `DEFAULT_CONSTANT`. The old map put quote and marker on
  `outline` and code on `tertiary`; all three were wrong.
- **Emphasis carries no colour at all.** `MARKDOWN_BOLD` and `MARKDOWN_ITALIC` are declared with
  **no base attributes** — they are weight and slant and nothing else. That is why
  `syntaxColors(): Map<SyntaxKind, Color>` became `syntaxStyles(): Map<SyntaxKind, SpanStyle>`:
  a colour-only map cannot express two of its own twelve entries, and the change costs
  `HighlightTransformation` one line (it now adds the style it is given instead of wrapping a
  colour in one).
- **A uniqueness rule over every kind is a rule the reference breaks.** Asserting "no two syntax
  colours are equal" was planned and had to be dropped: code and quote *are* the same colour in
  IntelliJ, deliberately. What replaced it asserts what actually matters — no syntax colour
  equals the body-text colour (the shape of the NUMBER defect), and the five kinds Python emits,
  which do appear in one cell together, are mutually distinct.

**The contrast floor is 4.5:1, except for comments at 3:1**, and that exception is a decision:
IntelliJ's own comment grey measures 3.39:1 dark and 3.16:1 light, a comment is meant to recede,
and dragging it to 4.5 would make comments shout as loudly as the code they annotate. Everything
else clears AA on the cell background it is actually drawn on.

The Jupyter plugin's own `JUPYTER.*` keys are **not in the community repository** — the plugin is
closed — so the four cell surfaces are the only values not copied outright. They come from the New
UI grey ramp the rest of the IDE chrome uses.

Evidence: `evidence/u1-tokens/{dark,light}/`, driven by `scripts/u1-palette-driver.sh`, which sets
`cmd uimode night` and runs `u1-palette.yaml` once per theme. Two runs and not one is the whole
point: a single-theme screenshot cannot tell a working palette from one that has collapsed onto
whatever the scheme handed it, which is precisely how audit A4 survived.

### U1a — two things the screenshots showed, one of which was not a defect

Looking at the evidence turned up two observations. Only one survived being checked, and the
other is worth recording precisely because the obvious "fix" would have been wrong.

**A markdown cell has no surface of its own while a code cell does — and that is correct.**
IntelliJ does exactly the same thing, deliberately: `CodeCellBackgroundControllerFactory` opens
with `if (editorCell.interval.type == CellType.MARKDOWN) return null`, so markdown cells sit
straight on the editor background. Giving them a panel to "make them match" would have diverged
from the reference to fix something the reference does not consider broken. What makes every cell
read as a cell in IntelliJ is the **left stripe**, which is U3's job and which markdown gets too.

**The top app bar drew under the status bar whenever the keyboard opened, and that was real.**
Measured rather than eyeballed: `Back` sat at `[16,44][40,68]` with no keyboard and
`[16,0][40,17]` with one — shifted up by the status-bar inset, and clipped.

Three hypotheses were tested and two were wrong, which is the only reason the third was found:

1. *A screenshot caught a mid-animation frame.* No — it survives `waitForAnimationToEnd`, which
   is now in the flow anyway because it should have been.
2. *`adjustResize` is fighting edge-to-edge.* No — removing it changed the numbers not at all.
3. **`dumpsys window` said `sim={adjust=pan}` while the manifest said `adjustResize`.** The
   attribute was on `<application>`, and the platform only reads `windowSoftInputMode` on
   `<activity>`. One level up it is silently ignored, so the activity ran at the framework
   default and the window *panned*: the whole thing slides up to keep the focused field visible,
   app bar and all.

Moved onto the activity, `dumpsys` reports `adjust=resize` and the app bar's bounds are now
**identical** with and without the keyboard. `AppWindowTest` pins it, because nothing else could:
a manifest attribute in the wrong element is valid XML, Lint has no rule for it, and every
Compose test builds its own host activity rather than the app's.

**A second defect fell out of the same investigation.** `Theme.JupyterDroid` was
`android:Theme.Material.Light.NoActionBar` with **no `values-night` variant**, so the window
stayed light whatever the system theme was and a cold launch in dark mode flashed white before
the first Compose frame. There is now a night variant, and `windowBackground` is pinned to each
palette's `editorBackground` — stated twice, in Kotlin and in XML, because the window is resolved
before a composition exists. `AppWindowTest` asserts the two copies agree, so the duplication
cannot drift silently.

What is *not* fixed here, and is still U6's: with `adjust=resize` the keyboard arrives as an
inset that nothing yet consumes. That is the right failure to have — an inset the layout can
answer, rather than a shove it cannot.

**Corrected after U5c, by measuring rather than re-reading this paragraph.** It said "a focused
cell near the bottom is still covered", and that is *not* what the device does. Focusing the last
cell of an eight-cell notebook on a 320dp phone scrolls it into view: `cell_8` moves from
`[54,476][296,524]` to `[54,265][296,301]`, clear of the bar at `y=331` and the keyboard below it.
`BasicTextField` brings itself into view on focus, and `imePadding()` keeps the bar above the
keyboard. The **completion popup** was the one real defect left here, and it is fixed — see
"U6a" below.

### U2 — the notebook's two modes (done)

`CellMode { COMMAND, EDIT }` plus `selectedCellId` on `EditorUiState`, with the transitions as
pure functions in `CellSelection.kt`. It is the seam U3–U6 all hang from: the stripe reads the
mode, the action bars swap on it, and the keymap is scoped by it.

Everything here is read out of JupyterLab's `Notebook.setMode` and `_evtFocusIn`
(`packages/notebook/src/widget.ts:1897-1949`, `3541-3563`), and three of the rules are not what
you would guess:

- **Selection is set *before* the mode**, and JupyterLab says why in as many words: *"Cell index
  needs to be updated before changing mode, otherwise the previous cell may get un-rendered"*.
  Editing is done *to* the selected cell, so selecting second applies it to the wrong one.
- **Edit mode requires a cell.** `if (!activeCell) newValue = 'command'` is the first line of
  `setMode` - asking to edit with nothing selected is not an error, it simply does not happen.
- **Entering edit expands a collapsed cell** (`activeCell!.inputHidden = false`), which is
  reachable here because a cell can be collapsed from the menu and then edited from the keyboard.

**Focus is the only thing that moves the mode**, as it is in JupyterLab, and the modifier that
reports it sits on the cell's *input area* rather than the cell - focus anywhere in a cell selects
it, but only focus inside the editor is editing. On the whole cell, the header's buttons would
count as typing.

Two failure modes were designed out rather than discovered later. A cell scrolling out of a
`LazyColumn` reports losing focus, so `cellFocusChanged` ignores that report unless the cell is
still the selected one - JupyterLab's `relatedTarget` check under another name. And a deleted cell
would leave a selection naming nothing, which looks live while every action scoped to it quietly
no-ops, so `withSelectionClamped` runs after every cell transform.

**How many backs it takes was measured, not decided.** The keyboard is a window of its own and
consumes the first press before the app's dispatcher sees it, so: back #1 closes the keyboard,
back #2 leaves edit mode (caret and selection handle gone, notebook still open), back #3 leaves
the notebook. The flow screenshots after each rather than asserting a number, and **asserts the
third**, because the way a `BackHandler` fails is by staying enabled and trapping the user.
Evidence: `evidence/u2-cell-mode/`.

**A prerequisite, and detekt asked for it.** `EditorViewModel` was at 11 of 11 functions, so U2
could not add one. `NotebookKernel` now owns the `KernelSession` and everything asked over it -
run, run all, interrupt, complete - and the ViewModel keeps the document. Completion went with the
kernel despite not being execution, because the session is what all three need.

### U2a — the Material roles were half IntelliJ and half baseline purple

Found in U2's own evidence: back reached Home and **the FAB was Material's baseline purple**,
sitting next to a Darcula editor. U1 set `primary`, `surface`, `error` and a dozen more and left
the rest at `darkColorScheme()`'s defaults - and those defaults come from a different design
system. `FloatingActionButton` reaches for `primaryContainer`, which nothing had set.

Fixing the FAB alone would have left the same bug waiting in every role no screen happens to use
yet, and U5 is about to add a bottom bar and a bottom sheet. So every reachable role is set now,
and `IntelliJColorSchemeTest` enumerates them against `IntelliJRamp.all`: a role that drifts off
the ramp fails a test instead of waiting to be noticed in a screenshot. The `*Fixed` family is
deliberately excluded - nothing here reads it, and a rule about colours no component uses is a
rule about nothing.

### U3 — cell chrome, at touch scale (done)

`CellFrame` draws a stripe down each cell with `drawBehind`, so selection changes re-draw and do
not re-measure — the same reason `LineNumbers` draws its gutter. `cellStripe` decides width and
colour as a pure function, which is where the rules are argued; the drawing is the part only a
screenshot can check.

**Every cell gets a stripe, including markdown, and the reference backs both halves of that.**
`markerRenderers/README.txt` describes a *"vertical gray line for markdown cells in the gutter"*
while `CodeCellBackgroundControllerFactory` returns null for markdown — so markdown gets a line
and no panel, which is exactly what the app now does. Drawing it at rest rather than on hover is
ours: there is no hover on a phone, so the line is the only thing always on screen saying where
one cell ends.

Widths are copied rather than scaled — 4dp command, 2dp edit, from
`DefaultNotebookEditorAppearanceSizes`. A stripe is a marker nobody taps, which is the opposite
call from the buttons beside it.

Three things came out of looking at the first run's screenshots, and none was visible in a green
log:

- **The stripe sat flush at x=0**, against the window edge, where a rounded corner or a gesture
  inset would eat it. It is inset now, inside the left margin, so the content keeps equal padding
  on both sides — a 9dp left inset against a 16dp right one read as a mistake on device.
- **The resting stripe was invisible.** Gray4 on the editor background is about 1.5:1; one step up
  the ramp (Gray5 dark, Gray8 light) reads without shouting.
- The stripe is anchored on its **left** edge, so narrowing it in edit mode widens the gap rather
  than moving the line.

**The 48dp claim was wrong, and the correction matters more than the change.** The header wrapped
each `IconButton` in `Modifier.size(32.dp)`, which was written up here and in the plan as an
accessibility defect. It was not. `IconButton` applies `minimumInteractiveComponentSize()`
*outside* the caller's modifier, so the pressable area stayed 48dp and only the ripple shrank —
measured on device with the 32dp wrapper still in place: `touch target is 48.0.dp x 48.0.dp`. The
wrapper is gone anyway, for the reason that survives contact with the measurement: 32dp is below
Material's 40dp default and looked cramped. That is taste, not accessibility.

The test found this by failing for the *right* reason and being read rather than patched: asked
about `getUnclippedBoundsInRoot`, it reported 40dp for buttons that were already correct, because
layout bounds are not touch bounds. `CellTouchTargetTest` asks `touchBoundsInRoot` now and is kept
for the case that could still break — a control built from a bare `clickable`, which would have a
real 24dp target and nothing else watching it.

`LazyColumn` items also gained `contentType = cell.kind`: code and markdown cells compose very
different trees, and saying so lets the list reuse each kind against its own pool.

Evidence: `evidence/u3-cell-chrome/`, **tablet only** — the flow runs a cell that is meant to
raise, and on the emulator `FakeKernelGateway` would echo the source back, so the cell would
"succeed" and the error stripe would never appear.

### U4 — a cell says where its run got to (done)

`EditorUiState.runningCellId` was one nullable string, so it could express exactly two things:
this cell is running, or none is. A cell waiting its turn, a cell skipped because an earlier one
raised, and a cell nobody had ever run were the same absence.

`CellExecution(status, startedAt, endedAt)` per cell replaces it, with `runningCellId` derived —
which kept the change to the writers and left every reader alone. The states are the reference's
`ProgressStatus` (`visualization/ui/ProgressStatus.kt`) and the transitions its four
`ExecutionEvent`s; only the ones something here can actually produce are kept, because a status
nothing produces is a branch nobody can test.

Three of them are new behaviour rather than new names:

- **QUEUED.** Run All claims every code cell before starting any, so waiting its turn is a state
  a user can see. Done per cell as each began, it would not exist.
- **SKIPPED.** Run All stops at the first cell that raises, so the rest are skipped, not un-run.
- **CANCELLED.** The kernel reports a `KeyboardInterrupt` whether the user pressed Stop or the
  code raised one, so without remembering which cell was interrupted, the Stop button would
  report the user's own action back to them as their code being broken.

The bracket is Jupyter's — `[ ]`, `[*]`, `[7]` — replacing a label that showed `[7]` or the word
"code". `[-]` for skipped is ours, because Jupyter has no notation for it: Jupyter's Run All does
not stop. The duration rides beside it in the reference's own `EXECUTION_TIME_COLOR`, and is
absent while a cell runs rather than counting up — a ticking label recomposes the header every
frame and the spinner already says the same thing.

**Two defects came out of this step's own evidence, and both were the feature failing to reach
the screen while the state underneath was correct:**

- Every *queued* cell drew a spinner and a Stop button. A queued cell is not what the kernel is
  doing, and Stop interrupts the kernel — offered on three cells at once it reads as three things
  to cancel. Queued now gets a disabled Run, which says "claimed, waiting" and promises neither.
- A *skipped* cell drew `[ ]`, so on screen it was still exactly the state `SKIPPED` exists to
  tell apart. Hence `[-]`, and a flow assertion for it.

**A gap in the repo's own gate, introduced two steps earlier.**
`check_maestro_output_assertions.py` finds a flow's fixture by looking for `FIXTURE="$ROOT/…"` in
its driver. U2's shared `scripts/maestro-run.sh` calls `seed_notebook` instead — so for every
U-phase flow `seeded_sources` returned `""`, every assertion looked incapable of matching the
cell source, and **rule 1 silently stopped applying**. The gate matches both forms now and has a
case pinning it. Worth noting the shape: the gate did not fail, it quietly checked less.

`childOf: {text: "Cell output"}` also turns out not to survive more than one output container -
this notebook has two, and a scoped assertion failed against a screen plainly showing the
traceback. The banner `Traceback (most recent call last)` is text no cell source contains, so the
assertion is unscoped and the gate is satisfied by the fixture rather than by the scope.

Evidence: `evidence/u4-execution-status/`, **tablet only** — one Run All produces four states at
once, and on the emulator `FakeKernelGateway` would echo the source back, so the second cell would
not raise and the queue would never stop.

### U5 is four steps, not one

The plan's U5 bundled new cell transforms, a width-driven presentation split, a notebook bottom
bar and an IME symbol row — and the bar needs a `KernelGateway.restart` the port does not have.
That is four functional commits wearing one number, so it was split before any code was written:
**U5a** the transforms, **U5b** the presentation, **U5c** the bottom bar (with the port change),
**U5d** the symbol row.

### U5a — the cell actions the reference has and we did not (done)

Insert above, change cell type, merge above/below, and cut/copy/paste, as pure transforms in
`CellRestructure.kt` beside `CellEdits`. Every rule is read out of
`packages/notebook/src/actions.tsx`, and four of them are not what you would guess:

- **Merge joins with a blank line**, because `addExtraLine` defaults to true.
- **The cell you invoke merge *from* decides the type.** `cell_type` comes from the *primary*
  cell, so merging markdown into code gives code and the reverse gives markdown.
- **Change-type keeps the id** (`insertCell` is passed `id: raw.id`) while **merge mints a new
  one** (its model carries no id). The asymmetry is the reference's, not an oversight.
- **A pasted cell gets a fresh id, except a code cell that was *cut***, which keeps its own
  (`actions.tsx:1666-1677`). That exception exists so a cut-and-pasted code cell is the same cell
  moving, and a running execution can still find it.

Both merge and change-type discard outputs and the execution count — *"the outputs will be
removed"*, *"After change of type outputs are deleted"*.

**Three things went wrong, and the saved file caught two of them.**

1. **`index:` on a repeated description is not addressing.** Every cell header carries the same
   `Cell actions`, and `index: 1` tapped the **first** cell twice: the change-type landed on the
   wrong cell and the merge silently no-opped past the top of the notebook. The screenshot looked
   perfectly reasonable; the printed `.ipynb` did not. Cells are anchored by their own text now
   (`below: {text: "alpha = 1"}`), and the driver prints the file for the same reason Step 4b's
   cell menu had to be read back off the device.
2. **A merged markdown cell renders**, so its two paragraphs are two Text nodes and
   `(?s).*alpha.*beta.*` could never match either. Three separate claims replace it, including
   `assertNotVisible: "[ ]"` — the bracket only a code cell draws, which is what proves the code
   cell was folded in rather than merely hidden.
3. **The cell menu outgrew the screen.** Eight items became thirteen, so Delete no longer fits on
   a phone-height screen. `DropdownMenu` scrolls, so nothing is unreachable, and the instrumented
   tests scroll to it — but a thirteen-item dropdown *is* the argument for U5b's bottom sheet, and
   `CellHeaderTest` is where that argument is now recorded rather than asserted away.

Evidence: `evidence/u5a-cell-actions/`. Emulator or tablet — this flow runs no cell.

### U5b — the same actions, on the surface the window has room for (done)

One list of items, two presentations: a `ModalBottomSheet` under 600dp, a `DropdownMenu` at or
above it. The labels are identical on both, so one Maestro flow, one accessibility vocabulary and
one set of tests address either. `cellActionPresentationFor(widthDp)` is pure and tested on the
JVM; `currentCellActionPresentation()` reads `currentWindowAdaptiveInfo()`.

**A width rule, never a device one** — a tablet in a 480dp split-screen window is a compact
window, and the class changes at runtime. The dependency is
`androidx.compose.material3.adaptive:adaptive`, version-less because the Compose BOM already
manages that family; only `adaptive` is added, because the layout and navigation artifacts exist
for the pane scaffolds and nothing uses those yet.

**Two defects in the new surface, both found by tests rather than by looking:**

- The sheet's `Column` did not scroll, so thirteen rows were clipped — the same "on screen but
  out of reach" bug the sheet exists to fix, one layer down. A `Column` with `verticalScroll`
  rather than a `LazyColumn`: the rows are few and short, and keeping every one in the semantics
  tree is what lets a screen reader and a test walk the list.
- It opened *partially expanded*, showing about five of thirteen rows and asking the user to drag
  before reading the rest. `skipPartiallyExpanded = true`: a list of actions is not content to
  peek at, it is the reason the sheet opened.

**Two devices, and it took four attempts to stop faking one.** The evidence needs a genuinely
narrow window and a genuinely wide one, and every attempt to manufacture the narrow one on the
tablet failed differently — three of them while looking green:

1. `wm size 1233x2400` — the tablet is held in landscape, so the *larger* number became the
   width: a 1600dp window that got the dropdown, in a run labelled "compact".
2. `wm density 480` — same trap one level down. 480dpi over the 1920px landscape edge is 640dp,
   still over the boundary.
3. **Density computed from the window.** This got the width right (450dp) and the *shape* wrong:
   1920×1200px at 682dpi is a **450×281dp letterbox**, which no phone is. Assertions had to be
   weakened to survive it — "both cells visible after an insert" became "one cell" — and that
   weakening was the tell that the window was wrong, not the assertion.
4. **Run it on a phone.** `dev36` is natively 320×640dp: compact, and the right shape. No
   override at all, and the weakened assertion goes back to asserting both cells.

The width is now **asked of the framework** rather than derived: `am get-config` reports `w320dp`
on the emulator and `w1280dp` on the tablet, which is the same number `WindowSizeClass` reads.
Deriving it from `wm size` was wrong a fourth time — that reports the *natural* size, so "the
larger edge" is the width on a landscape device and the height on a portrait one, and both runs
came out classified `expanded`. The driver names its evidence directory from the measured width,
so a run cannot be mislabelled.

**Reachability of the rows below the fold is asserted by the instrumented test, not the flow.**
A `scrollUntilVisible` swipe on a bottom sheet *is* a dismiss gesture: the first attempt closed
the sheet and then failed looking for a row on the notebook behind it. `CellActionsTest` walks
the semantics tree instead, which has no such side effect, and asserts Delete — the last of
thirteen and the one that fell off when the list grew — is reachable on both surfaces.

The flow's own assertions are limited to what certainly fits at both widths; that a cell was
actually inserted is proved by the saved `.ipynb` the driver prints, for the same reason U5a
needed it.

Evidence: `evidence/u5b-action-presentation/{compact-320dp,expanded-1280dp}/`, one run per
device. Instrumented suite green on both.

### U5c — the kernel bar, and `KernelGateway.restart` (done)

A `BottomAppBar` carrying Run All, Interrupt, Restart and Restart-and-run-all, plus the port
method the last two needed: `restart`, `POST /api/kernels/<id>/restart`, contract-tested against
both the fake and the real gateway.

**The bar is short because it is the *kernel's*, not the cell's.** JupyterLab's notebook toolbar
also carries cut, copy, paste and the cell-type picker — but its *cell* toolbar has six buttons
where ours has thirteen actions. Repeating them here would be two homes for one thing on a screen
with room for neither. What a cell menu cannot offer is the kernel, and that is the whole bar.
Run All moved off the top app bar for the same reason: it is a kernel action, it belongs beside
interrupt and restart, and the bottom of a phone is where a thumb already is.

**Restart asks first, because it is silent and irreversible.** Nothing on screen changes except
that every variable the notebook built is gone, so a restart that did nothing and one that worked
look identical. JupyterLab guards it with the same sentence — *"All variables will be lost."* —
and `NotebookBarTest` pins that nothing happens before the user agrees.

Two things the port change had to get right, both read from the reference rather than assumed:

- **The socket stays open.** jupyter_server replaces the process behind the same kernel id, so
  the `KernelSession` a caller holds is still valid.
- **Outputs and counts are kept.** JupyterLab keeps them on a plain restart and offers
  "restart and clear" as a separate command, so clearing them here would make the two the same
  thing. What *is* discarded is anything in flight: a RUNNING or QUEUED cell did not finish, it
  never happened, and the events that would have ended it died with the process — left alone it
  spins for ever. That is `abandoningRun`.

**The first version of the flow proved nothing, and looked green doing it.** It ran Run All after
the restart to check that `kept` was gone — but Run All re-runs the *first* cell first, which
redefines `kept` before the second cell ever looks. The fix is to run the second cell alone.
Only the screenshot showed it: `after restart: True` where the assertion expected False.

**Two identical `Refresh` icons side by side, both destructive**, was the other defect the
evidence showed. `material-icons-core` has nothing that reads as "restart, then run", and pulling
in `material-icons-extended` for one glyph is a dependency this project already declined once —
the Stop square on a cell header is hand-drawn for exactly that reason. The button composes the
two glyphs that do exist (`↻▶`) under a single `contentDescription`.

`EditorTopBarTest.runAllIsStillThere` was the right guard while Run All lived on the app bar. It
is **inverted rather than deleted** — a kernel action drifting back onto the title bar is the
regression worth catching now, and `NotebookBarTest` covers Run All in its new home.

Evidence: `evidence/u5c-notebook-bar/`, **tablet only** — the proof is a *variable*, and on the
emulator `FakeKernelGateway` never evaluates `dir()`, so the assertion could not fail.

### U6a — the completion popup was behind the keyboard (done)

`Popup(offset = caretBottom)` is a raw offset that knows nothing about the window or the
keyboard. With the caret in the last of seven cells and `co` typed, the 220dp list ran from
y≈470 past y≈800 on the tablet: four matches readable, `%code_wrap` and `%%code_wrap` painted
over the keys, and anything below them unreachable
(`evidence/u6-completion-popup/01-behind-the-keyboard.png`).

`CompletionPopupPosition` is a `PopupPositionProvider`, which Compose hands exactly what the
decision needs — where the anchor is in the window, how big the window is, and how big the popup
turned out to be. It places the list under the caret when it fits and above it when it does not:
same notebook, same prefix, same keyboard, now y≈95 to y≈435 with every match on screen.

Three things worth stating, because each is a place this could have been done wrong:

- **Above the caret's *top*, not its bottom**, so the flipped popup never covers the characters
  being completed — the one piece of context that makes the matches mean anything.
- **The IME height is passed in.** `windowSize` does *not* shrink for the keyboard under
  edge-to-edge: the window stays full height and the keyboard sits on top of it, so a provider
  trusting `windowSize` alone would compute the same wrong answer as before.
- **Pinned to the top when it fits neither way.** Part of the list is out of reach either way,
  and the top is where the first and likeliest match is.

The rules are a pure function of four arguments, so `CompletionPopupPositionTest` argues them on
the JVM; the device's job is only to show the pixels, which is what the two screenshots are.

**Reproducing it needs a kernel**, so it is a tablet gate and a cell has to be run first —
completion deliberately never boots a runtime by itself. Both facts are in the evidence README
because they cost real time to rediscover: the first attempt produced no popup at all, and the
second a one-row popup that fitted the gap and proved nothing.

### U5d — the characters a touch keyboard hides (done)

A scrollable row above the keyboard, shown only in `CellMode.EDIT`: `Tab : ( ) [ ] { } ' " = # _`
and the two arrows. This is what edit-mode keys mean on a phone — both references hang a great
deal on keys a touch keyboard simply lacks, and the ones a *code* editor needs most are not
letters: `:` ends every block header, brackets and quotes come in pairs, `#` starts every comment.
On a soft keyboard each is two taps behind a symbols page.

**One bar at a time, chosen by the mode.** The kernel bar and the symbol row share the
`bottomBar` slot: typing wants characters, a notebook being read wants the kernel, and stacking
both over a keyboard that already owns half the screen would cost a third of the notebook.

**A key on the row must do exactly what the same key on the keyboard does, and that is not free.**
A programmatic `state.edit { }` **bypasses the field's `InputTransformation`**, so `(` from the
row would insert a lone bracket where the keyboard gives `()`. `FocusedCellInput.type` applies
[PythonTypingRules] inside the edit — the same thing `PythonTypingRulesTest` does — so the two
agree about every character. The flow proves it end to end: three taps, `(` `)` `:`, and the file
on disk reads `def area():` — the `(` auto-closed, the `)` stepped over its partner instead of
doubling.

`FocusedCellInput` is the seam that lets a row outside every cell reach the focused one: a field
registers itself on focus and unregisters on blur, which keeps `TextFieldState` inside the field
rather than hoisting it into the ViewModel to be shared. Detach is guarded by identity for the
same reason `cellFocusChanged` is — focus reports arrive in no guaranteed order, and a stale
"I lost focus" would otherwise unhook the cell that just gained it.

**The instrumented test failed first for a reason worth keeping.** It asserted on
`onSourceEdited` immediately after a tap and read an empty list — because the field **debounces
reports by 150ms**, and `runOnIdle` does not wait for that. The field had typed perfectly well.
The test waits for the report now, and the negative case advances the clock past the debounce so
that "nothing happened" means nothing happened rather than nothing had happened *yet*.

Evidence: `evidence/u5d-symbol-row/`, emulator — typing needs no kernel.

**Not in this step, and not forgotten:** the plan also listed `semantics { customActions }` per
cell, so TalkBack can reach run/move/delete without hunting for a target. That is accessibility
rather than input, and it belongs with its own evidence.

### The whole suite, run against Phase U — and what it found (done)

Phase U changed the editor's top bar, added a bottom bar, took the cell menu from 8 items to 13,
replaced the boolean spinner with seven execution states, put a stripe down every cell and a
symbol row above the keyboard. **No pre-existing flow had been run against any of it.** Each step
ran its own flow and stopped, because running the other thirty-odd meant writing a shell loop by
hand — the exact condition the residual-risk list has been naming since Step 10. `scripts/run-all-flows.sh`
makes it one command, skips `TABLET ONLY` drivers on non-arm64 rather than letting them fail
confusingly, and counts the skips.

First run: **33 passed, 4 failed**. Every failure was real.

**Two flows asserted on a label that no longer exists.** `step4-editor` and `a2-empty-notebook`
waited on the text `"code"`; U4 replaced a code cell's kind label with Jupyter's `[ ]`. The
intent held, the string did not.

**Five flows had been tapping a button they did not mean, and four were green while doing it.**
`- tapOn: "Back"`, commented *"the TopAppBar navigation icon's contentDescription"*, was resolving
to the **system navigation bar's** back button — maestro names the node it hits, and it was an
`android.widget.ImageView` at `[1713,1128][1840,1200]`, the bottom-right corner of a 1920×1200
screen. Both carry `contentDescription = "Back"`; a `uiautomator` dump carries both. It had never
mattered, because until U2 the two did the same thing. U2 made back modal — from EDIT it returns
to COMMAND rather than popping — and they stopped agreeing, which is the only reason this surfaced
at all. This hazard was *already written down*, in `rename-in-editor`'s own comment, as something
to watch for; a note is not a fix. The arrow is `"Back to notebooks"` now: unambiguous in a dump,
and a better thing for TalkBack to say than a bare "Back".

**One driver of thirty-seven had never sourced `scripts/java-home.sh`.** `step2-import` died with
`JAVA_HOME is not set` the first time anything but a hand-typed shell invoked it. It had been
green for months on the strength of the operator's environment.

**The flows are not independent, and only a second sweep could show it.** `a6-edit-updates-recents`
passed the first sweep and failed the next with a different error: it could no longer find its
notebook on Home at all. Nothing in the app had changed — the list had grown. Every run leaves
notebooks behind: `a3-create-shows-up` seeds a crowd of 25 *on purpose*, and every flow that taps
the FAB adds a UUID nothing removes, so Home gains a couple of rows per sweep forever. The device
was at **64 notebooks**. `a6` is the one that breaks first because backdating to 2020 is the whole
point of its setup, and a stale row sorts to the bottom of a list ordered by recency — where a
20-second `scrollUntilVisible` stopped reaching it. This is the F.I.R.S.T. *independent* property,
and the suite did not have it. `reset_notebooks` (`scripts/maestro-run.sh`) empties the notebooks
directory; `seed_notebook` calls it, so every U-phase driver is now isolated, and `a6` and `a3`
call it directly — `a3` because "a screenful" stops being a number it controls if it inherits a
crowd.

After: **37 passed, 0 failed** on `SM-X216B`, alongside 62/62 instrumented and the full Gradle gate.

**Two things about evidence, fixed while here.** Nine drivers promote maestro artefacts into
`evidence/`, so a sweep rewrites those directories — right in itself, but only three drivers are
marked `TABLET ONLY`, so a sweep on the emulator would overwrite tablet captures with x86_64 ones
(where `MainActivity` falls back to `FakeKernelGateway`) and nothing on disk would say so.
`record_device` now stamps every evidence directory with serial, model, ABI, the window size from
`am get-config`, and a timestamp. Separately, `a4-markdown-contrast-driver.sh` was forcing
`cmd uimode night yes` on exit, so in a whole-suite run every driver alphabetically after it
captured its screenshots in dark — appearance depending on sort order. It restores `auto` now, as
`u1-palette-driver.sh` already did.

**The device rule is now enforced for every flow it applies to, not three of them.** `TABLET ONLY`
was carried by `u3`, `u4` and `u5c` alone, which meant `step10-real-kernel` — the flow that exists
to prove a real CPython — had no marker at all. Twelve drivers carry it now: every flow that
asserts something only a kernel can produce (`10.000000000000002`, a `ZeroDivisionError`
traceback, a completion list, a Stop button that only exists while work is in flight). They were
never at risk of a silent *pass* — `check_maestro_output_assertions.py` already forces each to
assert a computed value, which `FakeKernelGateway` cannot echo — but they failed confusingly on
x86_64 instead of saying why. `ANDROID_SERIAL=emulator-5554 scripts/run-all-flows.sh` now reports
`skipped 12` with the reason on each line. The two flows that tap Run and are *not* marked are
deliberate: `a1c-markdown-render` runs a markdown cell, which renders rather than executes, and
`u5d-symbol-row` only asserts that Restart is absent.

Evidence: `evidence/u-flow-sweep/`.

### Every driver on one seeding helper, and a gate that keeps them there (done)

The sweep left one thing half-done: `reset_notebooks` reached the eight U-phase drivers through
`seed_notebook`, and the other twenty-six still carried their own copy of the force-stop /
`mkdir` / base64 / verify block. Those are the drivers that had been accumulating notebooks for
months. This finishes it — **34 drivers, −231 lines, +116** — and closes the migration
`scripts/maestro-run.sh` was written for in the first place ("the older drivers each carry their
own copy; new ones do not have to").

**`seed_notebook` no longer resets, and that is the point.** Folding the reset into it made the
helper do two things and made two notebooks impossible to seed — the second call would delete the
first, which is exactly what `notebook-switch` needs. So the helper seeds one notebook, isolation
is `reset_notebooks`, and every driver states both. Stating something in thirty-four files is
something people forget, so it is enforced rather than trusted: **`scripts/check_driver_isolation.py`**
(with `_test.py`, wired into pre-commit) fails a driver that seeds without resetting, *and* one
that resets after seeding — a reset below a seed deletes the fixture it just wrote, which is the
mistake this shape invites. It was written before the migration and started red at 34 offences.

**The gate's own first rule was a false positive worth keeping the note about.** Detecting the
hand-rolled form by the bare string `files/notebooks` matched `REMOTE="files/notebooks/x.ipynb"`,
a declaration that sits at the *top* of every driver — so all twenty-three freshly-migrated
drivers were reported as resetting too late. It matches `mkdir -p files/notebooks` now: a write,
not a name.

**And the migration walked straight into the hole the older gate had documented and not closed.**
`check_maestro_output_assertions.py` warns in its own docstring that a driver whose fixture it
cannot find loses rule 1 *silently* — `seeded_sources` returns `""`, every assertion then looks
unable to match the source, and the flow passes without the rule ever being applied. Writing
`notebook-switch`'s two fixtures as `fixtures/$name.ipynb` produced exactly that: a path the gate
can match and the filesystem cannot resolve. The gate now reports a fixture that is named but not
on disk, and caught it on the first run. The loop is two explicit lines instead.

Evidence: the same `evidence/u-flow-sweep/sweep-after.txt` — 37 flows, re-run against 34 rewritten
drivers, because a mechanical edit to every driver in the tree is exactly the change a whole-suite
run exists to check.

### The suite at phone width, which had never been run (done)

Every sweep so far had been on the tablet. Running all 39 flows on the 320×640dp emulator found
four failures, and **not one was an app defect** — every one was a flow assuming the window it
was written on.

**Two of them were not even running where they thought.** `step2-import` and `step4-editor` were
the only drivers that never passed `--device`, and `maestro-run.sh` had documented for months why
that matters: `adb` honours `ANDROID_SERIAL`, **maestro does not**, so with two devices attached
it picks one of its own accord. They seeded the emulator and drove the tablet — the failure
screenshot is a 1920×1200 Home showing a notebook from a different flow. Every tablet sweep had
been passing them by luck of which device maestro chose.

The fix is not two `--device` flags. Twenty-six drivers carried the same six-line if/else by hand,
and a per-line rule about `--device` would have passed all of them *including* the two that were
broken, because the `else` branch is legitimately bare. `maestro_test` is now the one copy of that
decision, every driver goes through it or through `run_flow`, and
`check_driver_isolation.py` gained a second rule so the twenty-seventh cannot half-copy it. The
gate's two rules are one idea: **a flow runs against the state its own driver set up** — the right
Home, on the right device.

**The other two were index-based targeting, again.** `a1c-markdown-render` tapped
`Run cell, index: 0`; at 320dp the markdown cell's header is above the fold, so index 0 was the
*code* cell below it — the flow ran the wrong cell and then failed looking for a render nobody
had asked for. `step4b-cell-menu` tapped `Cell menu Delete` with no scroll; below 600dp the
actions are a `ModalBottomSheet` and Delete is the last of thirteen rows. Both now address the
thing they mean: `rightOf: {text: "markdown"}`, and a `scrollUntilVisible` that settles instantly
on the tablet's dropdown. That is the third and fourth flow this phase to be caught counting
rendered nodes instead of naming one.

**`step5-brackets` was mis-attributed for months and is now enforced.** PLAN recorded it as an
"emulator IME artefact" — it is neither the emulator nor the IME. The flow taps a one-line cell
expecting the caret past the end of the text, which holds only while the field is wider than the
line; at 320dp the tap lands inside `sum([1, 2, 3])` and `opensAPair` **correctly** declines to
wedge a partner into existing text. The app was right every time. Maestro has no End key, this
version has no `relativePoint`, and neither scrolling nor swiping reaches the symbol row's arrow
at that width, so the caret cannot be placed from a flow — the honest answer is a width rule.
`scripts/run-all-flows.sh` now enforces **two** device rules rather than one:
`TABLET ONLY` (an ABI rule: it runs a cell and the fake would answer) and `WIDE WINDOW` (a width
rule: it taps a text field and reads the caret). Skipped and counted, never silently passed and
never failed for a reason that has nothing to do with the app.

### U5e — every cell action reachable from the cell (done)

The item U5d deferred, and the last of Phase U. TalkBack reaches the `⋮` menu in three moves —
find that button among the cell's other controls, activate it, then walk a list that has just
replaced the screen; on a compact window the list is a `ModalBottomSheet`, so the cell it belongs
to is *gone* while it is open. `Modifier.cellCustomActions` puts the same items in the local
context menu of the cell the user is already on.

**One list, now three surfaces.** `cellActionItems` stopped being private: the menu, the sheet and
the accessibility context menu are built from the same function, so a fourteenth action cannot
appear on two of them and not the third. The test asserts against that function rather than
against a copied list, which is what makes that true rather than merely intended.

Run and Stop are deliberately absent: full-size buttons on the header with their own
`contentDescription`, already one move away. This covers what is *behind* something.

**There is no flow, and that is the honest answer rather than a gap.** `customActions` lives on
`AccessibilityNodeInfo.getActionList()`, which `uiautomator dump` does not print and a screenshot
cannot show; the only surface it appears on is TalkBack's own menu. A flow asserting anything here
would be asserting something else and calling it this. The gate is the instrumented test —
`fetchSemanticsNode` is the only way to ask what a node offers, and `action()` the only way to
know a label is wired to anything. The whole-suite sweep still ran, because this adds a semantics
node to every cell and moving the semantics tree is exactly what broke four flows in U7.

Evidence: `evidence/u5e-cell-actions-for-talkback/`.

### U6 — the keyboard, and the one window where there was no notebook left (done)

**Most of what this step listed was already true, and the flow is what says so.** An eight-cell
notebook on a 320×640dp phone, the last cell focused and then typed six lines longer with the
keyboard up: the caret line stays above the symbol row the whole time, because Compose brings the
*cursor* into view and the list scrolls under it. `imeNestedScroll` and an explicit
`contentWindowInsets` were on the plan's list and **neither was added** — nothing measured wanted
them. U6a had already fixed the one real IME defect, the completion popup.

**The defect was a phone in landscape, and only a screenshot at that height could find it.** At
640×320dp the status bar, app bar, symbol row and keyboard add up to more than the window, and
`Scaffold` resolves the overflow by handing the body **zero height** — a `uiautomator` dump of a
notebook being typed into contained no cell node at all. `editorTopBarVisible` drops the app bar
below the compact/medium height breakpoint (480dp) *while a cell is being edited*, which is the
only time the keyboard is there to take the room; command mode on the same short window keeps it.
Back still leaves edit mode without the arrow, so the way out never depends on the bar.

**A correction worth keeping.** Reading that landscape screenshot at 1:1 I said the content was
drawn under the status bar and added `contentWindowInsets = WindowInsets.systemBars` to fix it.
Cropped and scaled, the content had never been under the status bar — and
`ScaffoldDefaults.contentWindowInsets` is `systemBarsForVisualComponents`, *wider* than what I
substituted. The change fixed nothing and narrowed the default; it was reverted. 640×320 is small
enough that reading it unmagnified is guesswork.

**The flow types instead of seeding a tall cell**, because Maestro addresses a whole
`BasicTextField` as one node: a 13-line cell can never be 100% visible, so `scrollUntilVisible`
can neither reach nor assert a line inside it. It also needs `waitForAnimationToEnd` after the
tap — the symbol row appears on the *mode* change, well before the keyboard has animated in, and
the first screenshot caught a focused cell and no keyboard, which is a picture of nothing.

Evidence: `evidence/u6-ime-layout/{320dp,640dp}/`.

### U7 — two panes on a tablet, and the pane scaffold we could not use (done)

At 840dp and wider the notebook list and the open notebook are side by side; below that nothing
changes. A width rule, never a device one — the tablet in a split-screen window is a compact
window and gets one pane.

**The plan said `NavigableListDetailPaneScaffold` and the plan was wrong.** The scaffold puts both
panes inside *one* `NavEntry`, which stops the editor being a navigation entry at all — and both
nav3 decorators key on `NavEntry.contentKey`. That is defect D1 twice over: `viewModel { }`
resolves on the class name, so the second notebook is handed the first one's ViewModel; and
nothing disposes the previous notebook's entry, so `EditorViewModel.onCleared` never runs and its
kernel socket is never closed — the guarantee `notebook-session-ends` exists to prove.
`viewModel(key = notebookId)` would fix identity and not lifetime.

**Any fix has to keep both entries alive, which is what a scene strategy *is*.** The library
answer is `androidx.compose.material3.adaptive:adaptive-navigation3`, which has no stable release
(`1.3.0-rc01`) and whose POM requires `adaptive-navigation` **strictly** at that version — taking
it would move the whole adaptive family, `currentWindowAdaptiveInfo()` included, off the Compose
BOM and onto pinned release candidates. So `TwoPaneSceneStrategy` is ours: ~40 lines against
`SceneStrategy`, which is public API in the released `navigation3-ui` already on the classpath.
Both entries stay entries and this only decides where they are painted, so every decorator is
exactly where it was. **If that artifact goes stable, this is the thing to delete.**

**Opening a notebook now replaces the one open instead of stacking on it.** That was unreachable
on a phone — Home is not on screen while an editor is — and so never wrong. Two panes make it
reachable, and `[Home, Editor(a), Editor(b)]` would hand the strategy the last two entries and
put a notebook where the list belongs. Replacing is also what disposes the outgoing entry, so
`onCleared` runs.

**The list had to say which row the pane beside it belongs to.** A need two panes create: nothing
was ever shown next to the list before, so there was nothing to point at. The row carries
`selected` semantics as well as a `secondaryContainer` background, so TalkBack says it too — and
the row became one merged accessible item rather than two Texts and a click target.

**A screenshot found the test's bug, not the code's.** `theListPaneMarksTheNotebookThatIsOpen`
failed on "found 2 nodes": two panes put the open notebook's name on screen twice, once in the
list and once above the editor. The row was correctly selected all along; the selector was
ambiguous. It matches on `hasText(title) and isSelectable()` now.

`twoPane` is a parameter of `MainNavigation`, not something it measures, for the reason
`CellActionsTest` already records: `currentWindowAdaptiveInfo()` reads the *activity's* window,
which a `DeviceConfigurationOverride` around a composable does not change — so a test that forced
a width would drive one branch twice while appearing to cover both.

**The sweep found four flows that had assumed a full-width editor, and one flake that was ours.**
Nothing in the app was wrong in any of them:

- `step7-html-output` and `step7-rich-output` tapped `Run cell` with `index: 1`. An index counts
  the buttons *rendered*, and with 360dp gone to the list pane the first cell's source began to
  wrap and the second cell fell below the fold — so index 1 stopped existing. They use
  `Run all cells` from the notebook bar now, which is on screen at every width. An index over a
  scrolling list was never addressing the cell it named.
- `step7-rich-output` then failed a second time on the traceback, which renders *below* the
  second cell's source: both cells had run (`[3] ran in 0.2 s`, red stripe) and
  `extendedWaitUntil` cannot scroll. `scrollUntilVisible` retries, so it is the wait as well.
- `rename-in-editor` tapped the notebook's name meaning the app-bar title and got the **list
  row**, which merely re-opened the notebook already showing — so the dialog never came. Anchored
  `rightOf: "Back to notebooks"` now; the list pane is to its left. That is the third thing this
  session broken by the open notebook's name being on screen twice, after the instrumented test
  and `openNotebook`'s stacking.
- `a4-markdown-contrast` was **a flake introduced with `record_device`'s neighbours**:
  `ls -dt ~/.maestro/tests/*/ | head -1` under `set -o pipefail`. `head` exits after one line and
  the still-writing `ls` takes SIGPIPE, failing the driver with 141. A pure race, invisible until
  the directory grew — at 629 run directories `ls` finally wrote more than a pipe buffer before
  `head` left. Three identical runs gave 141, 1 and 0. `newest_run_dir` in `scripts/maestro-run.sh`
  is now the one copy of "the directory maestro just wrote", using `sed -n 1p`, which reads its
  input to the end and so has nothing to race; the other two `head -1` pipelines became `grep -m1`.

**Not built, and not forgotten:** the plan's medium-width navigation rail. A rail needs a second
top-level destination to hold, and this app has one; it belongs with the explorer or the terminal,
whichever lands first. The 840dp boundary is chosen over 600dp for the same reason — 360dp of list
plus a divider leaves 239dp for the notebook in a 600dp window, narrower than the phone the
single-pane layout was designed for.

Evidence: `evidence/u7-adaptive/`, both widths — `u7-two-panes-1280dp/` on the tablet and
`u7-one-pane-320dp/` on the emulator, the flow chosen from the width `am get-config` reports so a
run cannot assert two panes against a window with room for one. The negative half is the half
worth having: a strategy that returned a scene unconditionally would pass the two-pane flow on any
device, and only asserting that a narrow window does *not* get a second pane catches it.

## Phase X — one filesystem, and a notebook named after itself

### X1a — notebooks live where the kernel can see them (done)

Until now a notebook was `filesDir/notebooks/<id>.ipynb` while the kernel's `HOME` was
`files/spaces/base/home/jupydroid` - two trees that never met, so a notebook could not `open()` a
file the user could see and the user could not see a file a notebook wrote. The repository roots at
the workspace now, and `jupyter_server` is finally given `--ServerApp.root_dir` to match: it had
been unset for eight steps, leaving the Contents API rooted at whatever cwd `ProcessBuilder` gave
it, which nothing noticed while nothing but the app read the files.

`notebookWorkspaceIn` is the one definition, used by `HOME`, by the server's root and by the
repository. Three components agreeing by coincidence is not agreement.

**Safe to put user files there, and checked rather than assumed:** the bundled rootfs archive
carries `home/` and `home/jupydroid/` as bare directory entries with no files beneath them, and
`TarGzExtractor` never wipes its destination - so a version bump re-extracts over the tree without
touching a notebook. Had the archive shipped files there, this design would have eaten user data on
the next release.

The migration is stamped and **proven twice on device**, which is this project's own rule about
install-time paths. Its stamp was first derived as `workspaceDir.parentFile`, which put our
bookkeeping inside the rootfs's `home` and left the location a matter of reading the code; the
driver deleted the path it *assumed*, the migration became a no-op, and the flow failed looking for
a notebook that had never moved. It is a constructor parameter now, beside `BaseSpaceInstaller`'s
version stamp.

Twenty-eight drivers were repointed with `NOTEBOOK_DIR` written down once - a driver seeding the
old path would still be green against an empty Home. The move broke 25 flows on the first sweep,
because the `REMOTE=` declarations sat *above* the `source` line that defines the variable and
`set -u` aborted; no unit test could have said anything about that.

Evidence: `evidence/x1-workspace-migration/`.

### X1b — the notebook's name is its file (done)

The other half of path identity, and the one the plan described loosely. The id was *already* the
filename stem; what was missing is that the **title** lived in `metadata.jupydroid.title` while the
file was a UUID. So the workspace the kernel shares - and the explorer X2 builds over it - would
have shown `9f3a1c02-….ipynb` for a notebook called "Analysis of sales".

Built by reading JupyterLab rather than reasoning about it, and it corrected three of my guesses:

- **A rename moves the file.** `Context._rename` calls `contents.rename(this.path, newPath)`; the
  file moves and nothing is re-created.
- **The path is mutable state on the live object.** `_updatePath` reassigns `_path`, retitles the
  session and emits `pathChanged`; the open widget carries on. So `EditorViewModel` holds the id as
  mutable state and adopts the new one - which is why a rename keeps the cells, outputs and
  execution state instead of remounting the screen. Keying the navigation entry on the path would
  have re-created it, and that would have been a *regression*: today's rename loses nothing.
- **Lookups use the current path, never a stored handle.** `_contextsForPath` filters live contexts
  by `context.path`. So the navigation key stays frozen at the path the editor was opened under, and
  the editor reports where it lives now - which is what keeps the two-pane list highlighting the
  right row after a rename.
- **Names are refused, not rewritten.** `isValidFileName` disallows `/`, `\`, `:` and empty, and the
  dialog says which characters are at fault. Quietly turning `a/b` into `a-b` hands back a notebook
  the user never named.
- **A taken name is a question, not a failure.** `renameFile` catches **only** a 409 and asks
  `"%1" already exists, overwrite?`; anything else rethrows. Hence `NotebookNameTakenException` as
  its own type - an invalid name is never offered an overwrite - and `answerOverwrite(accepted)`
  mirroring `shouldOverwrite(path): Promise<boolean>`, one method taking the answer rather than two
  named for the outcomes.
- **Overwriting moves ours aside first.** `DocumentManager.overwrite`: "Cleanly overwrite the file
  by moving it, making sure the original does not exist, and then renaming to the new path."
  Deleting the target first would open a window in which neither file exists.
- **`Untitled1`, not `Untitled-1`.** `FileContentsManager.increment_filename`, read out of the
  jupyter_server inside our own rootfs: the bare name first, then a counter with no separator. I had
  invented a hyphen in two places.

**The file now carries no title at all**, and the writer *strips* a stale `jupydroid` namespace
rather than leaving one behind - a `.ipynb` this app writes is one any other tool would write. The
X1 migration renames `<uuid>.ipynb` to `<title>.ipynb` in the same one-shot pass, so nobody's
notebook comes back called `9f3a1c02-…`.

**A defect I introduced and did not notice until asked.** Giving `renameNotebook` the power to fail
made both ViewModels' bare `viewModelScope.launch` a crash path: an uncaught exception in a
coroutine takes the process down, and typing a `/` into the rename dialog was enough. Fixed, and
pinned by a test that first died with the raw exception escaping.

**And a process failure worth recording.** The repository layer and that crash fix were test-first;
`nameTaken`, `answerOverwrite`, the live-id reporting, the overwrite dialog and the rename
validation were not - I wrote them implementation-first and was about to verify afterwards. They
were backed out and rebuilt test-first on being called on it. The revert itself then failed to
compile because the slice took `onCleared` with it, which is precisely the kind of thing test-after
hides and red-first surfaces.

**Three device-only defects it shook out**, none visible to any unit test: `seed_notebook` did not
quote paths, so `Markdown Render.ipynb` landed as a file called `Markdown`; nine drivers had the
same bug in `cat`/`ls`/`touch`; and three drivers still named the pre-rename path, which after X1b
names nothing.

**Not matched to the reference, deliberately:** we do not offer to overwrite from Home's list, only
from the editor. The list has no open document to update afterwards, and a destructive confirm on a
long-press menu is a different design question.

Evidence: `evidence/x1b-name-is-the-file/`.

### X2a — the workspace as files (done)

`WorkspaceRepository` + `DiskWorkspaceRepository` over the kernel's HOME, and a single-pane
browser with a breadcrumb - `packages/filebrowser`'s shape (BreadCrumbs + DirListing, contents
replaced rather than a tree expanded), which is also the shape a phone wants: indentation would
spend the width the names need.

Reached from a Recents bar rather than replacing Recents. The explorer could not create, rename or
delete when this landed, so swapping it in would have been a regression wearing a new feature's
clothes. Create arrived with X2d, rename with X2f and delete with X2g, so it is all three now.

**Two defects, both found by its own tests**: the explorer handed over a path with `.ipynb` on it
and the repository put the suffix on again (`Nested.ipynb.ipynb`); and renaming a notebook inside a
folder moved it to the workspace root, silently.

Evidence: `evidence/x2a-explorer-browse/`.

### X2b — the id is the path, suffix included (done)

The second defect above was a symptom. X1b made an id a path with the suffix *stripped*, so every
boundary between the workspace and the repository had to put `.ipynb` back on - and one of them did
it twice the day folders became reachable.

`Contents.IModel.path` carries the suffix, and so does ours now. What that buys, beyond removing a
class of bug:

- **The extension is visible**, in Recents, in the explorer and in the editor's title bar -
  `Untitled.ipynb`, which is what the reference lists and labels
  (`title.label = PathExt.basename(sender.localPath)`, `docregistry/src/default.ts:618-621`).
- **The extension is editable with the name.** The rename dialog is prefilled with the whole
  basename and opens with only the *stem* selected - `setSelectionRange(0, value.length -
  ext.length)` (`docmanager/src/dialogs.ts:271-274`) - so typing replaces the stem and the suffix
  survives, while a user who wants `.py` can have it.
- **A `.py` or a `.csv` is no longer a special case**, which is what X2c needs: an editor for a
  file the notebook editor cannot open has somewhere to be addressed from.

Two rules transcribed rather than guessed, from the jupyter_server in our own rootfs:

- `new_untitled` is *typed*, and the server supplies the extension: `Untitled` + `.ipynb` for a
  notebook, `untitled` + `.<ext>` for a file, `Untitled Folder` with `insert=" "` for a directory
  (`services/contents/manager.py:599-614`). So `createNotebook` adds the suffix and its callers do
  not spell it.
- `increment_filename` splits the suffix asymmetrically (`manager.py:543-549`): `rpartition` first,
  and only if the extension is not `ipynb` does it fall back to `partition`. That is what gives
  `archive.tar.gz` its *full* suffix while `data.backup.ipynb` keeps the middle dot in its stem.
  The counter goes on the stem either way - `untitled1.txt`, never `untitled.txt1`.

**A device-only finding, from the flows.** Two rename flows tapped into the field before typing.
The dialog already opens focused with the stem selected, so the tap collapsed that selection and
`eraseText` then stopped at the unselected suffix: the rename landed as
`Renamed From Editor.ipynbynb`. The tap and the erase are both gone - typing the stem *is* the
interaction, which is what the reference's selection range is for.

**Still open, and now visible**: Recents scans the workspace root only, so a notebook in a folder
is reachable through the explorer and absent from Recents. It goes when Recents becomes a section
of the explorer.

### X2c — a file that is not a notebook (done)

JupyterLab opens anything its notebook factory does not claim in the *file editor*: one CodeMirror
over the whole document, with the language taken from the file type (`packages/fileeditor`). Ours
is the **cell editor's own field** over the whole file - `CodeCellField` already takes a
`SyntaxHighlighter` and an `InputTransformation`, so line numbers, bracket matching, the caret row
and the IME symbol row are the same code and not a second implementation to drift from.

- `WorkspaceRepository` gained `readText`/`writeText`, whole-file both ways, which is what
  `Contents.IModel.content` is for a `fileFormat: 'text'` type. Writes go through the same
  `NotebookSaveDebounce` as a cell's source.
- `syntaxForFile(name)` is the reference's file-type table with the grammars we have
  (`docregistry/src/registry.ts:1356-1508`): `.py` Python, `.md` Markdown, `.json`/`.ipynb` JSON,
  everything else plain. **A type with no grammar is plain text, not a refusal** - a `.csv` opens.
  The reference has a `csv` type too, but what it gives it is a data grid, not colouring, so
  colouring commas would be our invention.
- `JsonSyntax` is hand-lexed rather than generated, because a generated parser refuses a file the
  moment it stops being valid - which is exactly when someone is editing it. It scans and never
  fails: an unterminated string is coloured to the end and the file still renders.
- Auto-closing brackets only where brackets are structure (`.py`, `.json`), because a `(` in prose
  is not the start of anything.

**This is what closes the "New Python file" defect at its cause.** That FAB item called
`createNotebook("untitled.py")`, which produced `untitled.py.ipynb` - a notebook wearing a Python
file's name. A `.py` now has somewhere real to open, so creating one can mean what it says.

Still to do: creating files and folders from the explorer (`newUntitled` with a type, `Untitled
Folder` with `insert=" "`), which is where that FAB item gets rewired; and the flow evidence.

That is now X2d and X2e below: the explorer gained the create menu, and the FAB item now creates
a real file and opens it in the file editor instead of a notebook wearing a `.py` name. Evidence:
`evidence/x2c-file-editor/`, `evidence/x2d-explorer-create/`, `evidence/x2e-home-new-python-file/`.

### X2d — create notebook, python file, and folder from the browser (done)

The reference's filebrowser toolbar has a New widget with exactly three items — New Notebook,
"New %1 File" per filetype, New Folder (`packages/filebrowser-extension`). `_createNew`
(`packages/filebrowser/src/browser.ts:529-546`) calls `newUntitled({path, type})` with the
*browsed* directory, then renames in place.

- Ours is a "+" in the Files bar opening a Material `DropdownMenu` with the same three, labelled
  `New notebook` / `New Python file` / `New folder`. On a phone the filetype menu collapses to
  the one type Home and the explorer actually create; the reference's full per-type list would be
  a menu whose items mostly create something nothing here can open.
- Creating goes through `ExplorerViewModel.create{Notebook,File,Folder}`, which reuses the Step 1
  wiring that the ViewModel tests already argued — the names come from the server
  (`untitled.py`, `Untitled Folder`, `Untitled.ipynb` written as real nbformat) via
  `WorkspaceRepository.createFile`/`createFolder` and `NotebookRepository.createNotebook`.
- Creation lands in the directory **being browsed** — `path: this.model.path`
  (`browser.ts:558-566`) — which the create-menu instrumented tests and the explorer-create flow
  prove by creating inside `reports/` and reading the file back from disk.
- **The reference renames the result in place** (`_createNew` -> `rename()`); ours deferred the
  rename box to X2f, which gave it to *every* row rather than only to a newly created one.
  Instrumented: `ExplorerScreenTest`, three menu tests.
  Maestro: `explorer-create` (`scripts/explorer-create-driver.sh`), on the 320dp emulator —
  nothing runs a cell, so the fake kernel is never consulted.

### X2e — Home's "New Python file" creates a real file (done)

The FAB's `New Python file` used to call `createNotebook("untitled.py")`, producing
`untitled.py.ipynb` — the exact confusion X2c's editor exists to resolve. Creating a Python file
is `new_untitled({type: "file", ext})`, which the server names `untitled.py`, and the file editor
is its default document factory (`packages/fileeditor`), the same split the explorer's `onOpenFile`
makes for a `.py`.

- `HomeViewModel` gained `workspaceRepository` and `createPythonFile(onCreated)`: it asks the
  workspace for `untitled.py` and reports the path back, because the name only the creation knows
  — the server's incremented series (`untitled.py`, `untitled1.py`). The screen hands it to
  `HomeScreen.onOpenFile`, which navigation routes to the `FileEditor` entry
  (`backStack.openFile`), so Home now has the same open-to-the-two-editors split as the explorer.
- Unit: `HomeViewModelTest.createPythonFile_makesTheFileAndHandsBackItsPath`; instrumented:
  `HomeNewPythonFileTest`, which taps the FAB menu on a device and asserts the created path comes
  out of the screen. Maestro: `home-new-python-file`
  (`scripts/home-new-python-file-driver.sh`) creates through the real UI, types a line, and the
  driver reads `untitled.py` back from disk — and asserts it is *not* nbformat.

### X2f — rename from the browser (done)

The other half of a file browser. X2a said the explorer "cannot create, rename or delete yet";
X2d closed create and deferred the rename box it opens on a new item. This is rename; delete is
X2g.

**Read, not assumed.** JupyterLab renames from the row itself (`listing.ts:2556` `_doRename`):
an unchanged name is kept silently, an invalid one shows an error naming the characters and
reverts, and the typed name is joined onto the directory being browsed
(`PathExt.join(this._model.path, newName)`, `:2621`). Only a **409** is caught - `renameFile`
(`docmanager/src/dialogs.ts`) turns exactly that into `"%1" already exists, overwrite?` and
rethrows everything else. The jupyter_server in our own rootfs agrees:
`rename_file` (`filemanager.py:619-647`) returns silently when the path is unchanged, answers 409
when the target exists, and is otherwise `shutil.move` - **so a directory renames like a file**.

**Adapted, and from what.** The reference edits the name inline in the row; a 48dp row with a
caret under the IME is not that interaction, so it goes through the dialog we already have -
which is the reference's *other* rename surface, stem-selected so `.py` survives being typed over
(`dialogs.ts:271-274`). The row's menu opens on a **long press**, the touch equivalent of the
right-click context menu (`filebrowser-extension`) and of Winlator's own per-row menu
(`ContainerFileManagerFragment.showListItemMenu:324`), and it is what Home's rows already use.
The same items are on the row as `customActions`, because a long press is unreachable for
TalkBack - which U5e already paid to learn.

**Two things Winlator does that we deliberately do not.** `FileInfo.renameTo:75-80` *sanitises*
the typed name (`StringUtils.clearReservedChars`) where JupyterLab refuses it - X1b settled that
one - and it discards `renameTo`'s boolean, so a failed rename silently redraws the old name.
The second is a defect, not a model: ours throws with the offending value.

**The defect this was written around, and it is the reason to read Winlator at all.**
`DiskWorkspaceRepository.resolve` returned `canonicalFile`, which **resolves symlinks**. Reading
and writing never noticed, because both follow a link anyway - but a rename built on it would
have moved the link's **target**. Our workspace lives inside the rootfs, and this is the same
defect as `TarGzExtractor` writing through the stale loader symlink, which crash-looped the app.
Both references guard it: jupyter_server joins and `normpath`s **without resolving** and stats
with `os.lstat` (`fileio.py:290-329`, `utils.py:153-162`), and Winlator checks `isSymlink` before
it walks anything (`FileUtils.delete:114-119`). Paths are now **checked canonically and acted on
as named**, and a contract test renames a symlink and asserts its target did not move. That
addressing is its own object, `WorkspaceRoot` - which is the split the server makes too, between
`fileio.py` and `filemanager.py`, and detekt asked for it independently.

**The open document follows its file.** A rename from the browser beside a notebook used to leave
the editor holding a path that no longer existed, and its next debounced save would **re-create**
the file under the old name: one document, two files, and the edits in the one nobody is looking
at. The reference does not tell the document from the browser - the *contents layer* announces it
and every open `Context` listens (`context.ts:93` -> `_onFileChanged` -> `_updatePath`). So
`WorkspaceRepository.renames` is that signal, `pathAfterRename` is the rule, and both editors
adopt. The rule is prefix-based **by whole segments**: the reference's own
`this._path.indexOf(oldPath) === 0` also matches `foobar/x` against `foo` and would retarget a
document that never moved.

**Overwrite moves ours aside first**, as `DocumentManager.overwrite` does (`manager.ts:514-528`),
and - unlike the reference, whose `finally` swallows a failed delete - puts ours *back* if the
target will not go. An overwrite that fails must not be the operation that loses the file.

Evidence: `evidence/x2f-explorer-rename/` (320dp emulator: rename a file, rename a folder with
its contents, and a taken name asking before it replaces anything) and
`evidence/x2f-explorer-rename/open-document/` (tablet, two panes: the notebook renamed from the
browser beside it keeps both edits, and the old path stays gone). Both drivers read the disk
afterwards, because a rename that copied would look identical on screen. The flow was checked
against a build with the follower removed and failed, so it is a gate and not a decoration.

Also renamed, in their own commit first: `isValidNotebookName` -> `isValidFileName` (the
reference's own name) and `NotebookNameTakenException` -> `FileNameTakenException` - one 409, one
type, now that both repositories can raise it. The X1b section above still names the old one; it
is a record of what was decided then, not a claim about today's symbols.

### X2g — delete from the browser (done)

The last of the three X2a listed. Rename was X2f; this is delete, and it is a different risk, which
is why it is a different step.

**Read, not assumed.** `DirListing.delete` (`listing.ts:451-500`) asks first - title `Delete`, body
`Are you sure you want to permanently delete: %1?`, and `defaultButton: 0` with the comment "By
default focus on 'Cancel' to protect from accidental deletion". Winlator asks too
(`ContainerFileManagerFragment:353`, `ContentDialog.confirm`), so both references agree this is a
question and not a menu item. `DocumentManager.deleteFile` (`manager.ts:351-363`) then stops the
session, deletes, and **closes every widget open on the path**.

**Two branches, and Android picks the one with no trash.** `delete_file` (`filemanager.py:569-614`)
sends the entry to the platform's trash by default (`delete_to_trash = True`, `:129`) and only
really deletes when there is none. An app's private files have no trash, so ours is the permanent
branch - which is why the wording says so. In that branch, with `always_delete_dir = False`
(`:137`), **a folder with anything in it is refused**: `400 "Directory %s not empty"`. Winlator
would delete the tree (`FileUtils.delete` recurses); the server sharing this workspace would not,
and it is the server another Jupyter client would be talking to. So the folder must be emptied
first, and the explorer says so in the error line it already had. **A symlink is unlinked, never
followed** - the one case where following would destroy a tree nobody named, and the reason
Winlator checks `isSymlink` before it walks anything.

**Adapted:** the reference's focus default has no Compose equivalent, so the protection is carried
by colour and placement instead - Delete in `colorScheme.error`, Cancel as the dismiss button,
which is also what a tap outside and a back press choose.

**The defect it closes, and the direction it comes from.** Our editor flushes its last edit when it
is torn down; the reference's `SaveHandler` is *disposed* and writes nothing again
(`savehandler.ts:29,62-69`). So deleting a notebook open in the pane beside the browser used to be
undone by that write. The document now hears `WorkspaceChange.Deleted` on the same channel X2f
added, stops writing, and reports `gone` so the screen closes itself. Reported rather than
commanded, deliberately: `deleteFile` only closes widgets for deletes *it* performs, and ours also
covers the one the reference never sees - a cell calling `os.remove` on the notebook it is running
in.

**Home's delete was unguarded and is not any more.** A long press one row off destroyed a notebook
on one tap. Same act, same question, same dialog.

**What the evidence proves, stated precisely.** `evidence/x2g-explorer-delete/` (320dp emulator):
the question is asked, Cancel keeps the file, confirming takes the row *and* the file, a full
folder is refused out loud, and an emptied one then goes.
`evidence/x2g-explorer-delete/open-document/` (tablet, two panes): the pane closes with the row and
the notebook is still gone after the app's own teardown. That second flow does **not** gate the
write-guard in `persist` - by the time the delete is confirmed the debounce has already fired, so
there is no pending write left to flush, and a build with the guard removed still passes it. What
covers the guard is `EditorRenameTest.aNotebookDeletedElsewhereIsNotWrittenBack`, which types
*after* the delete and does fail without it. Both facts came from running the mutation rather than
reasoning about it, and the driver header says so, because a flow that looks like it covers
something it does not is worse than no flow.

**One refactor detekt asked for, and it was right again.** `EditorViewModel` hit the twelfth
function exactly where the residual-risks list predicted it would. The answer was the one that
list also predicted - find the responsibility that wants to live elsewhere - and it was the pure
`CellAction` dispatch (`applying`, `newCellId`), which is state-in/state-out and now sits beside
the transforms it dispatches to in `CellEdits.kt`.

### X2h — Recents becomes a section of the explorer

X2b left one thread open and named the condition for closing it: *"Recents scans the workspace root
only, so a notebook in a folder is reachable through the explorer and absent from Recents. It goes
when Recents becomes a section of the explorer."* Two sub-steps, because the source of the list and
the surface it lives on are two functional commits: **X2h-1** changes what feeds it, **X2h-2**
changes where it lives.

#### X2h-1 — Recents is a record of what was opened (done)

**Read, not assumed, and it corrected the obvious answer.** The obvious fix was to make the scan
recursive. The reference does not have a scan at all: `RecentsManager`
(`packages/docmanager/src/recents.ts`) is a **history of documents opened**. `addRecent` (`:94-110`)
splices out any entry already holding the path and `unshift`s the new one; `_setRecents` (`:186-201`)
slices to `maxNumberRecents`, default **10** (`docmanager-extension/schema/plugin.json:153-158`). It
is persisted through `IStateDB` under `docmanager:recents` (`:288`) - **client state, not a file in
the workspace** - and every entry is validated on load, where invalid means the Contents API
answered 404 (`_isValid`, `:169-181`; `_loadRecents`, `:203-224`).

So "most recently modified" and "most recently opened" are different features that were wearing the
same name here. The scan is the reason a notebook in a folder was invisible; a history has no
opinion about where a document lives.

**Recorded once, from one seam.** `_recordAsRecentlyOpened` hangs off the widget manager's
`activate-request` hook (`widgetmanager.ts:268-285`), not off the file browser or the launcher.
Ours hangs off navigation, which Home's rows, the explorer's rows and the VIEW-intent import all
already went through - and collapsing them into one `rememberDocumentOpener` removed a duplicated
rule as a side effect: the `.ipynb`-decides-the-editor split had been written out twice, in Home and
in the explorer, and only one of the two could have recorded anything. The reference has one
`docmanager:open` for the same reason.

**The containing folder is recorded too** (`widgetmanager.ts:414-422`), so a folder someone actually
works in is one tap away rather than a descent. **One departure:** the reference records the root as
`''` for a top-level document, and we do not - the explorer already stands at the root, so that row
would navigate nowhere.

**Two references are empty here, which is worth recording so nobody re-checks.** JupyterLab's file
browser has **no** recents concept - `packages/filebrowser/src` matches "recent" only in comments
about the last-modified column - so recents lives in File > Open Recent
(`mainmenu-extension/src/recents.ts`) and in the Running panel's "Recently Closed"
(`running-extension/src/recents.ts`). **Winlator has no recents or history of any kind**: its file
manager is a pure browser over a `Stack<T> folderStack` (`BaseFileManagerFragment.java:49`), and
grepping `recent|history` across its sources returns only joystick `historyPos`. Putting the list
inside the explorer is therefore ours, and X2h-2 is where that adaptation is argued.

**Not built, deliberately:** `recentlyClosed` and "Reopen last". One document is on screen at a time
and Back is not "close a tab" - there is no tab strip for a reopen to restore into.

**The store is not in the workspace, and that is the design.** The workspace is the kernel's `HOME`
and the server's `root_dir` (X1a), shared with any other Jupyter client, so a bookkeeping file there
would appear in the explorer's own listing and in an `ls` from a cell. It lives in `filesDir`,
mirroring `IStateDB`, and `DiskRecentDocumentsRepositoryTest.keepsItsBookkeepingOutOfTheWorkspace`
asserts the workspace is untouched rather than leaving it to review.

**What the evidence proves, stated precisely.** `evidence/x2h-1-recents-source/` (320dp emulator;
nothing runs a cell, so the fake kernel is never consulted). Screenshot 01 is the defect itself:
Recents with `At Root.ipynb` alone, the notebook in `reports/` nowhere on it. The flow then opens
that notebook and a `.csv` beside it through the explorer, and **the driver reads the store off the
device** - X2h-1 changes nothing on screen until X2h-2 swaps the surface, so a screen assertion here
would prove nothing. It asserts all four facts, including the negative one that separates a history
from a scan: `At Root.ipynb` was never opened and **must not** be listed. Without that line every
other assertion would also pass against the old behaviour.

**Isolation had to grow with it.** The store survives a wipe of the workspace, so a path an earlier
run opened and this one re-seeds under the same name would still be listed - flows would stop being
repeatable in exactly the way `check_driver_isolation.py` exists to prevent. `reset_notebooks` clears
it now, and the driver refuses to run if a store survived the reset, because a stale one would carry
the flow.

**Two things checked by running them rather than reasoning about them.** Deleting the validation
filter fails `forgetsWhatIsNoLongerThere` and `forgetsADirectoryThatIsGone`, so validation is a gate.
And the driver's *negative* assertion was tested in both directions before being trusted - a
`grep -q … && { exit 1; }` under `set -e` had to be shown to survive the absent case and abort on the
present one, since a check that silently cannot fail is worse than no check.

**The whole suite, both devices**: 50 passed, 0 failed, 0 skipped
(`evidence/x2h-1-recents-source/sharded-suite.txt`). The emulator shard's first pass failed all 25
flows to a harness trap rather than to a regression - `connectedAndroidTest` had uninstalled the app
- which is written up under "Harness notes" and recorded in that file rather than deleted.

**Not done here, and it is a departure from the step's own plan.** `HomeViewModel` still reads
`observeNotebooks()`. Switching it would have meant rewriting rows, timestamps and the long-press
menu on a screen X2h-2 deletes, and would have broken `a6-edit-updates-recents` a step early for no
gain. X2h-1 therefore lands the source of truth with no visible change; the surface swap and the
timestamp's new home are X2h-2's.

#### X2h-2 — the explorer is the screen, and Recents is a section of it

**Home is gone.** `rememberNavBackStack(Explorer)`, and `HomeScreen`, `HomeViewModel`, the `Home`
nav key and `HOME_PANE_METADATA` with it. That is the reference's arrangement rather than a
simplification of ours: JupyterLab has one file browser and no separate recents screen.

**The adaptation, and what it departs from.** The reference surfaces recents in a **File > Open
Recent submenu** (`mainmenu-extension/src/recents.ts`) and in the **Running panel**
(`running-extension/src/recents.ts`); its file browser has none. A phone has neither a menu bar nor
a second sidebar, and the browser is the only always-present surface, so the submenu's contents
become a section at the top of it. Shown at the **root only** - a recents list is not a property of
the folder you are standing in, and neither reference surface is scoped to a directory. Ordered
directories first (`RecentsMenu.updateItems`), each row carrying the **whole path**, because the
list is flat and global and two `summary.ipynb` in different folders would otherwise be one row
repeated - which is why the reference labels its own by path.

**Where the timestamp went, and why A6 survives.** Recents rows carry no time, because neither
reference surface does. It moved to the **explorer's rows**, which is where `DirListing` keeps it:
a `last_modified` column, shown by default - only `file_size` and `date_created` start in
`_hiddenColumns` (`listing.ts:259-261`) - humanized, and narrowing as the space shrinks
(`:1061-1073`). A 320dp phone is the end of that scale, so the name and its age stack instead of
sitting in two columns. `WorkspaceEntry` carries `updatedAt` now, `relativeTime` moved out of
`ui/home` ahead of its deletion, and `a6-edit-updates-recents` was rewritten against the new label -
it is the flow that proves the rule survived the move rather than being dropped with the screen.

**The open document is still marked, and the rule is now the reference's.** Home's two-pane
highlight would have died with Home. `DirListing` does the same thing from `_onActivateRequested`:
it selects the row for the document just activated, **and only when that document is in the
directory being browsed** (`listing.ts:2761-2770`, which returns early on
`dirname !== this._model.path`). Comparing whole paths says that once, and it covers the file
editor too, which Home's version never did.

**Two refactors the change asked for, both taken rather than worked around.**

- `ExplorerViewModel` hit detekt's function limit at 13, exactly where the residual-risks list
  predicted the next one would land. The answer was not the recents half: it was that
  `createNotebook`/`createFile`/`createFolder` were **three spellings of one act**, and the
  reference has one - `_createNew({path, type})` over a typed `new_untitled`
  (`browser.ts:529-566`, `manager.py:599-614`). One `create(NewItem)` now, and the menu is a list
  rather than three wired callbacks.
- `observeNotebooks`, `NotebookSummary`, `scanSummaries` and `toSummary` lost their last reader and
  are gone. That is not only dead weight removed: the scan ran on **every** notebook write, parsing
  every other notebook in the root to rebuild a list nothing was showing.

**A defect the section created and the tests caught.** At the root a document's name is now on
screen twice - once in Recents, once in the listing - so any selector matching by name alone is
ambiguous. `NotebookNavigationTest` failed on exactly that (`Expected exactly '1' node but found
'2'`), and the fix is to name which row: the icons differ, `File` against `Recent file`, which is
also what a screen reader hears. The Maestro flows have the same hazard wherever a long press must
reach a listing row.

**Four things only the flows could find, and two of them were defects rather than drift.** The
first sweep failed eight flows; every one was run down rather than re-run.

- **The browser never re-listed.** Coming back from an editor left the row's time stale
  (`a6-edit-updates-recents`), and the VIEW-intent import raced the initial listing and never
  appeared at all (`step2-import`). `refresh()` on resume now, which is the reference's concern
  solved at the moment a phone actually has: it shows one thing at a time, so coming back *is* the
  refresh. The import also opens what it imported - `ACTION_VIEW` asks to view a document, and it
  removes the race outright rather than papering over it.
- **A3's defect resurfaced on the new surface.** A created row lands in name order, so among
  twenty-five notebooks it fell below the fold and the screen looked unchanged - exactly what audit
  A3 was written for. Home answered by prepending and scrolling to the top; the browser scrolls to
  the created row, which is where the reference arrives from the other direction (`_createNew`
  hands its result to `rename()`, which selects and scrolls to it).
  **And the first fix was wrong in a way only the flow could show:** `justCreated` was set *after*
  `goTo`, which launches its own coroutine, so the fresh state the listing built overwrote it. Every
  unit test stayed green. It is carried into the listing now.
- **A renamed file left a dead recent on screen** (`rename-in-editor`). The reference does not make
  a recent follow a rename - it drops what stops resolving, with `_isValid` treating a 404 as
  invalid and `RecentsMenu.onBeforeAttach` re-checking every time the list is about to be shown. A
  surface that keeps recents permanently on screen has to ask when it refreshes, so `revalidate()`
  does, and only writes when something actually went.
- One failure was **the flow's fault and mine**: `explorer-create` asserted `untitled.py` at the
  root when the flow had deliberately created it inside `reports/`, which is the very thing that
  step proves.
- **An open document is on screen twice, and only one of the two has a menu.** The tablet sweep
  found it where the emulator could not: with the notebook open *beside* the browser,
  `longPressOn: "Switch Alpha.ipynb"` reached the Recents row - which is deliberately not
  long-pressable, since renaming and deleting belong to the file rather than to the memory of
  having opened it - and the row menu never came up. **Selecting by index was tried and rejected
  on evidence:** a live `uiautomator` dump shows three nodes carrying that text (the recent row,
  the listing row, and the editor's own app-bar title), all of them plain text with the clickable
  on the merged parent, and maestro's index order is its own rather than the visual one. The two
  flows empty the section first instead, which is deterministic, uses a real feature, and leaves
  the browser in the state they were written against. The duplication itself is kept: it is the
  cost of putting a global list above a directory listing, which the reference avoids only by
  having a menu to put recents in.

**Flows reworked, not patched.** Nine lost their `Browse files` hop, and several changed subject
rather than wording: `step2-home`'s empty state is now an empty listing (the reference has no
empty-state copy either), `a3-create-shows-up` asserts the created row rather than a timestamp at
the top (the browser orders by name, where Home prepended by recency), and `home-new-python-file`
became `new-python-file` - its FAB is the browser's "+", and creating no longer opens what it made,
because `_createNew` creates and renames in place. `x2b-panes-repro` was **deleted**: it captured
Recents being drawn beside the browser, and with one surface that shape is structurally
unreachable. `TwoPaneSceneStrategyTest` keeps the rule that guarded it, driven with a synthetic
surface, so `PANE_SURFACE_OPENS` is not left doing something nothing checks.

#### X2h-3 — the browser re-reads the workspace, and the kernel can finally write into it

X2h-2 left one thing open and named the reference's answer to it: the browser refreshed when it
came back, so a file written *while it was on screen* never appeared. Closing it turned up a much
older defect underneath.

**The reference answers this with a poll, and the distinction from its signal is the whole point.**
`FileBrowserModel` runs `cd('.')` on a `Poll` at `DEFAULT_REFRESH_INTERVAL = 10000` with
`standby: 'when-hidden'` (`packages/filebrowser/src/model.ts:26,97-106`). It *also* listens to
`contents.fileChanged` and calls `_poll.refresh()` when the change is in the directory being shown
(`:655-680`) - but that signal only ever reports changes the **client** made, which is why the poll
exists beside it. Our equivalent of the signal is `relistAfter`, which has re-listed after this
app's own create, rename and delete since X2a. The poll is what covers a kernel.

**`standby: 'when-hidden'` is `RESUMED` here**, which is the mobile half: `repeatOnLifecycle`
cancels the loop at `ON_PAUSE` and starts a fresh one at `ON_RESUME`, so a backgrounded app spends
no wakeups and coming back still refreshes immediately - the case `a6` and `step2-import` needed,
now covered by the same effect rather than a second one. **Not adapted:** the reference's
`backoff: true, max: 300000`, which backs off a failing *HTTP* poll against a server that may be
down. Ours is a `listFiles` on this app's own storage; the one way it fails is the directory being
removed underneath, which `goTo` already reports while leaving the view where it was.

**Winlator was checked and has nothing here**: no `FileObserver`, no inotify, no poll - it refreshes
on its own operations and on view lifecycle only (`BaseFileManagerFragment`). So there was no
Android-native watcher to copy and no rootfs wiring to do; the workspace is plain files under
`filesDir`, which is how Winlator browses its rootfs too.

**And then the flow found something much worse.** Written to prove the poll end to end - a cell
writes a file, the browser beside it notices - it failed on the *cell*:

    OSError: [Errno 30] Read-only file system: 'written_by_a_cell.csv'

**The kernel had no working directory, and never had.** `jupyter_server` sets one only when it is
given a path - `if path is not None: kwargs["cwd"] = self.cwd_for_path(path, ...)`
(`services/kernels/kernelmanager.py`, read out of the server in our own rootfs) - and
`OkHttpKernelLifecycle.start()` was posting `{}` to `api/kernels`. With no path there is no `cwd`
at all and the kernel inherits the server process's, which on the device is read-only. So **X1a's
promise that a notebook can `open()` a file the user can see had never held for a relative path**,
and nothing noticed because no flow had ever asked a cell to write one.

JupyterLab gets its working directory by creating a *session* -
`SessionContext({path: this._path, type: 'notebook', ...})` (`packages/docregistry/src/context.ts:82-90`)
- whose path is handed to the same `start_kernel(path=...)`. `POST /api/kernels` accepts the path
directly (`services/kernels/handlers.py:56`), which reaches the identical server code. So
`connect(space, documentPath)` now, sourced from `EditorUiState.notebookId` - which since X1b *is*
the path and follows a rename. **This is parity for the working directory and is deliberately not
the sessions work**: reconnecting to a running kernel by path is still Steps 17/18's, and is still
listed as open.

**What the evidence proves, stated precisely.**
`evidence/x2h-3-browser-notices-the-workspace/` (tablet, two panes, real kernel - it runs a cell,
so the emulator's `FakeKernelGateway` would have echoed the source back and written nothing).
Screenshot 01 is the root with only the notebook on it; 04 is the same browser, untouched since,
now listing `written_by_a_cell.csv  just now` beside the notebook whose cell wrote it. The driver
then reads the file off the device, because a row appearing and a kernel writing a file are
different claims.

**The cell assembles its filename** (`'written_by_a' + '_cell'`) rather than spelling it, and that
is structural rather than fussy: maestro matches substrings, the source is on screen beside the
browser throughout, and a source containing the name would satisfy the closing wait on its own -
the exact trap `check_maestro_output_assertions.py` exists for.

Unit side, red first: `ExplorerScreenTest` writes a file behind the screen's back and touches
nothing (and deletes one, for the other half); both timed out at 30 s before the poll existed.
`OkHttpKernelLifecycleTest` pins the request body to `{"path": …}`. `EXPLORER_REFRESH_INTERVAL` is
pinned to the reference's 10 s, so changing it is an argument with the reference rather than a
quiet edit.

## Harness notes

### Never `kill -9` maestro

It leaves the device-side driver behind, and the symptom points nowhere near the cause: **every**
subsequent flow on that device dies with

```
maestro.android.DeviceServerDiedException: Device server died during 'deviceInfo'
Caused by: io.grpc.StatusRuntimeException: UNAVAILABLE
```

Maestro drives a device through a gRPC server it installs and runs there (`dev.mobile.maestro` and
`dev.mobile.maestro.test`); a hard kill of the CLI orphans it. This cost an hour and nearly cost a
correct conclusion: a sharded run failed 8 of 20 flows and the obvious reading was "concurrent
maestro processes are unsafe, sharding cannot be used". Running **one flow alone** failed
identically, which is what separated the two. The fix is to uninstall the driver so maestro
reinstalls it:

```
adb -s <device> uninstall dev.mobile.maestro.test
adb -s <device> uninstall dev.mobile.maestro
```

`adb uninstall` may answer `Failure [DELETE_FAILED_INTERNAL_ERROR]`, which leaves the device still
broken while looking handled - check `pm list packages | grep maestro` afterwards rather than
trusting the exit. Stop a run with `pkill -f run-all-flows` and let maestro exit on its own.

### The server's own log, and why it is at DEBUG

`jupyter_server` runs at `--ServerApp.log_level=DEBUG` and will stay there until the app is
feature-complete. Read it with:

```
adb -s <serial> shell run-as com.jupydroid.app cat files/spaces/base/run/jupyter_server.log
```

**It is the half of every kernel investigation we could not see.** The execution stall cost six
sightings partly for that reason: our own `kernel.*` lines could say "nothing came back" and never
which of `Nudge: attempt N on kernel X`, `No such channel` or `Received message on closed websocket`
the server was reporting at that moment - three mutually exclusive causes with three different
fixes, and all three invisible at INFO.

It costs nothing on screen. `JupyterServerLauncher` uses `redirectErrorStream(true)` and
`redirectOutput(logFile())`, so it is one file, truncated at every server start, and it never
reaches logcat. A driver capturing a failure should take **both** accounts of the same run - ours
from logcat and the server's from that file - because either alone has been enough to mislead.

### `connectedAndroidTest` uninstalls the app, so it cannot precede a sweep

Running `./gradlew :app:connectedDebugAndroidTest` and then a Maestro run on the **same device**
fails every flow on it, and the symptom does not name the cause:

```
run-as: unknown package: com.jupydroid.app
```

AGP uninstalls both the app and the test APK when the instrumented run finishes, so the sweep drives
a device with no app on it. Twenty-five flows failed identically in the X2h-1 sweep, including one
that had passed on that device minutes earlier - which is what separated it from a real regression,
along with `adb shell pm list packages | grep jupydroid` coming back empty.

It reads exactly like the `DeviceServerDiedException` entry above (everything on one device fails at
once) and is a different cause with a different fix: **`installDebug` again**, then re-run that
shard. The order that avoids it is instrumented tests first on one device, install, then sweep -
never instrumented tests *between* an install and a sweep.

### Running the suite across both devices

`scripts/run-flows-sharded.sh` runs every flow across every attached device at once: measured
**14m54s against ~40 minutes** sequential, with no flow, timeout or assertion changed.

**Maestro's own `--shard-split` cannot be used here.** It splits a suite "evenly across N connected
devices" and knows nothing about our rules, so the twelve `TABLET ONLY` flows would land on the
x86_64 emulator where `FakeKernelGateway` answers from the cell's own source - green, and proving
nothing. The rules live in the drivers, so the partition is made here and each shard is an ordinary
`run-all-flows.sh`.

Two passes, and the second one matters: constrained flows go where their rule allows, then free
flows go to **whichever device is carrying least**. Round-robin looked fair and was not - the tablet
is the only device that can run the kernel flows, so an even deal of the rest gave it 27 against the
emulator's 13 and left the emulator idle for half the run. Least-loaded gives 20/20.

Every driver runs on exactly one device, which is what makes this safe rather than merely fast:
nine drivers write into a fixed `evidence/<step>/`, and two devices filling one directory at once
would interleave two runs' screenshots. It is also why maestro's docs warn about sharding *the same
flow* across devices - a hazard this never creates.

`FLOW_NAMES` selects flows by exact name, because the human-facing substring filter matched
`step5-completion` when asked for `step5-completion-repeat`.

### Running a Maestro flow

Most flows need a notebook seeded first, so they have a driver beside them — the driver is the
entry point, not the yaml:

```
ANDROID_SERIAL=<serial> ./scripts/<name>-driver.sh
```

A driver pushes its fixture into the app's own storage with `run-as`, then runs the flow. The
naming is not perfectly mechanical (`step5-edit-cell.yaml` is driven by
`scripts/step5-edit-driver.sh`), so grep for the yaml's name in `scripts/` rather than guessing.

Only three have no driver — `step2-create`, `step2-home` and `step2-manage`, which all
`clearState` because they are about Home with *nothing* in it — plus `step3-persistence`, which
creates what it needs through the UI. They run directly:

```
. scripts/java-home.sh                       # sourced, not executed; that is why it is not +x
~/.maestro/bin/maestro --device <serial> test .maestro/<flow>.yaml
```

`ANDROID_SERIAL` is **not** honoured by Maestro itself — always pass `--device`. Screenshots
land in `~/.maestro/tests/<timestamp>/<flow>/takeScreenshot/`; copy them into `evidence/<step>/`
and **look at them**, because more than one flow in this repo's history has gone green against
the wrong thing.


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
- **Check both themes, or ship A4 again.** `adb shell cmd uimode night yes|no` flips the
  device; `scripts/a4-markdown-contrast-driver.sh` shows the shape — run the same flow twice and
  keep a screenshot of each. A4 was black text in *both* themes, which reads perfectly on light,
  so a one-theme check passed it. Anything that draws text is worth seeing in both.
- **Instrumented tests run with `./gradlew :app:connectedDebugAndroidTest`** and the emulator is
  enough — none of them needs a Space runtime. They exist as of Step 4b; before that
  `app/src/androidTest` was empty despite the Compose test dependencies being declared since
  Step 1. Reach for one when the question is *whether something is visible or reachable*, which
  no JVM test can answer.
- **A flow must prove the kernel produced what it waits for — and this is now enforced.**
  `scripts/check_maestro_output_assertions.py` (pre-commit) fails a commit on either of the two
  ways a post-Run wait can pass without a kernel, and has its own tests in
  `check_maestro_output_assertions_test.py` — a gate is only worth having while it still catches
  things, and this one shipped once without them.

  **Waiting for text the source already shows.** Maestro matches text anywhere on screen, the
  cell's source included, so waiting for `omega` when the source reads `print("omega")` passes
  whether or not the kernel ever ran. Three incidents. Two ways to satisfy the gate:
    - **Scope it**: `childOf: {text: "Cell output"}`, the contentDescription `CellOutputs` sets.
      Probed on device both ways — source-only text is visible unscoped and not visible scoped.
      It resolves against the *first* matching ancestor, so it only works while one `Cell output`
      is on screen; with two code cells visible it silently checks the wrong one.
    - **Or make the text unconfusable**: have the fixture assemble what it prints
      (`'sec' + 'ond'`) so the word exists only in the output. This is the one to reach for when
      several cells are on screen.
  Assertions *before* a Run read the source deliberately and are exempt; a deliberate one after
  a Run takes `# matches-source: <why>`. The narrowing is what makes the check usable — a blanket
  rule fires on 38 legitimate assertions, most in markdown flows where the rendered text *is* the
  source text. It found a live defect the day it was written: `step4b-run-all-and-stop` had never
  checked Run All's second result.

  **Waiting for the output container instead of a result.** `Cell output` wraps every output
  kind, so it appears the moment anything renders — and `FakeKernelGateway` renders the cell's
  own source on every run, so the wait is satisfied on any x86_64 device with no Space. Assert a
  value the kernel had to compute; waive with `# container-only: <why>`. `Cell image output` is
  deliberately *not* covered — the fake emits a stream and never an image, so that container does
  prove a kernel made one — and asserting a container is *gone* is a real claim, which is how
  Clear outputs is checked. This rule is retroactive proof rather than theory: run against the
  tree as it stood before `step4b-run-cell` was deleted, it fails on exactly that flow
  (`evidence/maestro-gate-tested/catches-the-deleted-flow.txt`).
- **Open a seeded notebook by scrolling to it, after a settle.** Home is sorted by recency, so a
  seeded notebook sinks below the fold as a device accumulates notebooks — two flows began
  failing at their first assertion with the row present the whole time. Every driver-seeded flow
  now does `launchApp` → `waitForAnimationToEnd` → `scrollUntilVisible` → `tapOn`, **at every
  `launchApp` it makes**, not just the first. Flows that relaunch mid-flow need it twice. The
  settle is load-bearing: a scroll started before the list has rendered *ends* rather than
  retrying. Keep fixture titles distinct, too — "Collapse" is a prefix of "Collapse Outputs".
  **The settle has an in-app twin.** Anything that re-orders Recents needs one before the next
  tap: A3's fix scrolls to a newly-topmost notebook, and a tap dispatched mid-animation lands
  where the row *was*. `a2-empty-notebook` failed exactly there once in thirty flows and was
  green on the retry — a flake, which is worse than a failure, because a suite that is green
  four times in five reads as green.
- **A sweep across N flows is not finished until N flows have been re-run.** 62352d7 edited 22
  and re-ran 10, which read as thorough and was not: of the twelve it left alone, one was red the
  whole time (`a1-markdown`, and for a defect in the app rather than in the flow), one relaunch
  had been missed in a flow that *was* on the list, and two more seeded flows had been skipped by
  the sweep entirely. Closed 2026-08-07 — every flow re-run, all green,
  `evidence/maestro-observability/flows-reverified.txt`. If a change touches a flow, that flow is
  part of the evidence; a partial list is worse than none, because it looks like coverage.
- **`assertVisible` means on screen, so assert what the screenshot shows.** A node below the
  fold passed `extendedWaitUntil` on one run of `step7-html-output` and failed it on the next,
  against an identical state — and the screenshot in between showed only source. Reach for
  `scrollUntilVisible` when the thing being proven can be pushed off screen by a long cell; it
  retries, so it is also the wait.
- **A popup's position is not observable from a Compose test.** It gets its own window, so
  `getUnclippedBoundsInRoot` reports coordinates inside that window — a menu pinned across the
  screen and one under its button read identically. Anchoring is a screenshot gate.
- **`hideKeyboard` with no keyboard up is a system BACK, and it will pop a screen.** A dialog owns
  the keyboard and takes it with it when it closes, so `hideKeyboard` afterwards has none to hide
  and pops the screen underneath instead. Worse, the `tapOn: "Back"` that usually follows then
  matches the **system navigation bar's** back button — whose contentDescription is also `"Back"` —
  and leaves the app for the launcher, which reads as "the flow's assertion is wrong" rather than
  "the flow navigated twice". Only reach for `hideKeyboard` when a keyboard is actually up.
- **Maestro regex-matches the *whole* node string, so a multi-line cell cannot be matched by one of
  its lines.** `assertVisible: "carried = 6 * 7"` fails against a cell whose source is
  `carried = 6 * 7\nprint(carried)`. Assert on something single-line, or on what the run produced.
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

## Where we are, and what to do next (2026-08-13)

> **Read this section first.** It is the one place kept current; everything below it is the record
> of how each step was decided, not a statement about today. If it disagrees with a step write-up,
> this section is right and the write-up is history.

### Step 17 is done: kernels start through sessions

**`POST /api/sessions`, not `POST /api/kernels`.** `OkHttpKernelLifecycle.start` sends
`{path, type, name}` - the reference's own model, where `type` is
`ext === '.ipynb' ? 'notebook' : 'file'` and `name` is the basename
(`packages/docregistry/src/context.ts:82-90`) - and reads the kernel id out of `kernel.id` in the
session that comes back (`packages/services/src/session/session.ts:381-390`).

**Reattaching needed no lookup of ours, and that is the finding worth keeping.** `startSession`'s
docstring says it will "return an existing session if the session path already exists", and the
server is where that happens:

    exists = await ensure_async(sm.session_exists(path=path))
    if exists:  s_model = await sm.get_session(path=path)
    else:       s_model = await sm.create_session(path=path, ...)

(`services/sessions/handlers.py`). So a client-side `listRunning()`-and-find would have been
duplicated logic, not parity. One POST does it.

**Measured on the device** (`evidence/step17-sessions/`): two visits to one notebook produce two
client session ids and **one kernel id**, the two `POST /api/sessions` take 854 ms and then 2 ms -
`create_session` against `get_session` - and the reopened notebook's second cell runs as `[3]`,
continuing the kernel's own execution counter. `notebook-session-ends` was the flow that pinned the
old behaviour and is now `notebook-session-reattaches`, asserting `44` from a cell that increments:
a fresh kernel raises `NameError`, a stale saved output reads `43`, and only a reattached kernel
says `44`.

`interrupt` and `restart` stay on `api/kernels/<id>`, which is where JupyterLab leaves them too.
`KernelSession.id` stays our own client id - JupyterLab's `clientId`, distinct from the server's
session id, which nothing needs until Step 18's `DELETE`.

### Start here: Step 18 — the server's lifecycle

**`BaseSpaceKernelGateway` starts `jupyter_server` on first use and nothing ever stops it**
(~143 MB RSS held while backgrounded). Sessions are what a shutdown accounts for now:
`DELETE api/sessions/<id>` shuts the session's kernel down, and `shutdownSession`
(`packages/services/src/session/restapi.ts`) has the status handling - 404 warn, 410 "the kernel was
deleted but the session was not", 204 ok.

**The open question is *when*, and the reference cannot answer it**: a browser tab never owns the
server's process, so there is no JupyterLab behaviour to copy. On the last session closed? On
`ON_STOP`? On a memory-trim callback? That is decided against Android's process lifecycle, on
measurement, and it is the reason this is a step rather than a patch.

**Also open, after that**, in rough priority order: **packaging** (debug APK is 116 MB, most of it
the rootfs at 278 MB unpacked; asset pack versus first-run download is undecided); **LSP**, which
would supersede 5.5's completion rather than duplicate it; **Phase T**, a real shell in the rootfs,
which supersedes Step 11's Console REPL; and the cheap chore of **29 drivers calling bare `adb`**
rather than `$ADB`, which is why the suite only runs from a shell with `platform-tools` on PATH.

**Do not start from the older text below without checking it.** The paragraphs that follow were
written on 2026-08-06 and are kept because their *reasoning* is still the record; their "next" is
not. Phases U and X have both closed since.

### The state on 2026-08-13, in one place

- **Phases U and X are done.** U closed the visual language; X put every document on one filesystem
  (X1a/X1b), built the file browser (X2a-X2g), and X2h made the browser the screen the app opens
  on, with Recents as a section of it.
- **Recents is a history of what was opened**, not a scan of a directory - `RecentsManager`'s
  behaviour, persisted outside the workspace and validated against it.
- **The kernel has a working directory**, as of X2h-3. It never did before, so a cell's
  `open('data.csv', 'w')` raised `OSError: [Errno 30]` and X1a's promise had never actually held.
- **The browser re-reads the workspace on the reference's own poll**, so a file a cell writes
  appears without anyone touching the screen.
- **The execution stall is still open, and now has a seventh sighting.** The window is understood -
  `jupyter_server` completes the websocket handshake before the nudge that establishes the zmq
  subscriptions (`services/kernels/websocket.py`) - but retrying the greeting on the server's own
  cadence was measured over 80 tablet runs and stalled twice, asking 120 times for a reply that
  never came while iopub delivered. The client is back at reference parity. See "Known residual
  risks" and `evidence/kernel-observability/seventh-sighting/`.
- **Suite:** 51 Maestro flows across a tablet and a 320dp emulator, ~101 instrumented tests, and the
  gradle + script gates. `scripts/run-flows-sharded.sh` runs the lot across both devices.

### The record from 2026-08-06 (kept for its reasoning, not for its "next")

**Working end to end on a Galaxy Tab A9+ (Android 16, arm64):** bundled Alpine/musl rootfs →
interpreter exec'd from `nativeLibraryDir` → stock `jupyter_server` on loopback → kernel as a
subprocess → tap Run in the app → `print(6 * 7)` shows `42` and a numpy determinant shows
`np.float64(10.000000000000002)`. Outputs persist across app restarts, images and tracebacks
render, cells are editable with syntax highlighting.

**Done:** Steps 0–3, 4a, 4b, 7.1, 7.2 (bar `text/html`), 8 (all sub-steps), 10 (all sub-steps),
5.1–5.5b. **Step 5 is closed**, and with it the last fake behind the `KernelGateway` port.
**Step 6 is already satisfied** by 4b and Step 10 — run → outputs, Running/Idle, interrupt and
execution count, all against a real kernel.

**Markdown cells are editable and rendered** (audit A1, A1b, A1c): parsed with commonmark-java,
drawn with recursive Compose, rendered by default with a tap to edit and Run to render again.
No WebView and no HTML.

**All five of the 2026-08-06 audit findings are closed** — see "Audit findings" above for what
each was and how it was fixed. They came from driving the built app rather than reading it, and
four of the five were invisible to every test that existed at the time. **A6 is closed too**:
`updatedAt` is now the file's mtime, the way Jupyter's `last_modified` is, so an edit, a run and
a rename all move a notebook to the top of Recents.

**`text/html` outputs render natively** (Step 7.2, closed) and **cells collapse** (Step 4b's
last piece, closed) — both written up below. **Steps 4b and 7.2 are done**, so every cell
behaviour the plan set out is on the device.

**Saving no longer shreds the file.** A notebook another tool wrote keeps its `tags`,
`attachments`, `language_info` and the rest through an edit here — eight losses, found by
measuring rather than by reading. See "A save must not shred the file".

**All 33 Maestro flows are green on the tablet as of 2026-08-07** — re-run in full after the two
device defects below, because `Navigation.kt` and the editor's app bar are on every flow's path.
One, `step5-completion`, failed in the sweep and passed on an immediate re-run: it is a second
sighting of a stall recorded before these changes, in that same flow, and it is written up under
"Known residual risks". Record: `evidence/notebook-defects-sweep/flows-reverified.txt`.

The earlier full sweep (30 flows) is `evidence/maestro-observability/flows-reverified.txt`.
Closing 62352d7's half-finished version turned up five things a partial re-run had hidden: a flow
that could only ever be green on a fake kernel (deleted), a relaunch with no settle (fixed),
three seeded flows the sweep skipped entirely (fixed), a real app defect — tapping a rendered
markdown cell opened an editor with no caret (fixed, A1c above) — and a flake only a full run
could surface.

**Two device defects were found and closed on 2026-08-07**, both about how a notebook gets from
Recents to the editor: opening a second notebook showed the first (and would have saved edits into
its file), and a title could not be changed from the notebook itself. See "Defects found on device"
above. The editor's ViewModel is scoped per back-stack entry now, which also means it is *cleared*
for the first time — so leaving a notebook flushes its last edit and closes its kernel socket.

**What this section said to do next was Phase U**, which is done, as is Phase X after it. The
current direction is at the top of this section. Two of the items it listed have since closed and
are recorded here so nobody re-opens them: **adaptive layout** landed as U7 (`TwoPaneSceneStrategy`,
which pairs a surface with the document it opened rather than using `ListDetailPaneScaffold` - see
U7 for why the pane scaffold could not be used), and **reconnecting to a running kernel** is now the
next step rather than a background item.

### Which reference answers which question (settled, 2026-08-11)

This app is a **JupyterLab client for a phone**, so the references are not interchangeable and
each is authoritative for one thing:

| reference | authoritative for |
|---|---|
| **JupyterLab** (`reference/research_repos/jupyterlab`) | **behaviour and features** - what the app *does*. When in doubt, read it. |
| **Winlator** (`reference/research_repos/winlator`) | the rootfs and container problem: how a Space is installed, run and browsed from an Android app |
| **IntelliJ** (`reference/research_repos/intellij-community`) | the editor: syntax, LSP, autocomplete, markdown rendering, and the notebook's visual layout |
| **Compose docs** (developer.android.com) | which Material/Compose component exists, and how it should be used on a phone or a tablet |

Two of these have already been checked and found **empty** for a question that looked like theirs,
which is worth recording so nobody re-checks: IntelliJ Community ships **no Jupyter kernel-protocol
client** and **no Jupyter file browser**. Where it has nothing, JupyterLab is the answer, and a
statement that "the reference does X" must name which reference.

A behaviour we cannot reach on a phone is adapted, never invented: the adaptation is written down
with what it departs from (as U5b did for the cell-actions surface, and U3 for hover).

### Reference studies, and what each settled

`reference/` is **gitignored**, so none of these travel with a fresh clone — the summaries here
are the durable record, and `reference/*/FINDINGS.md` has the detail on this machine. What is
worth re-cloning, and why it is not vendored:

```
cd reference/research_repos
git clone --depth 1 https://github.com/jupyterlab/jupyterlab.git          # protocol + cell semantics
git clone --depth 1 https://github.com/JetBrains/intellij-community.git   # 2.4 GB; platform/jewel/markdown
```

JupyterLab is the one consulted most: the kernel protocol's message shapes (5.5a), the cell
actions' semantics (4b) and the never-empty-notebook rule (A2) were all read out of it rather
than guessed, and each time it contradicted the obvious answer.

| Reference | Shape | What it does | What it settled |
|---|---|---|---|
| **Callisto** | notebook, N cells | CodeMirror 6, one WebView **per cell** | The exec model and the bridge protocol; also the memory cost we later rejected |
| **Acode** (6.4k★, MIT) | one file | CodeMirror 6 in a WebView, 23 langs, own LSP client fork | WebView editing is viable at scale — for *one* editor |
| **JupyterLab** | notebook, N cells | CodeMirror 6, **one page**, many instances | The architecture we would have taken had we stayed on WebViews — and, still, the reference for *behaviour*: the kernel protocol's message shapes (5.5a) and the cell actions' semantics (4b) were both read out of it rather than guessed |
| **GitHub Mobile** | code browser | **No editor at all** — server-highlighted HTML (primer `.pl-*`) in a WebView, plain `EditText` for edits | Not a model: it moves tokenising to a server, which offline forbids |
| **sora-editor** (LGPL-2.1) | IDE, one file | native view + tree-sitter via an **archived LGPL** binding | Ruled out on licence; its tree-sitter route ruled out on NDK |
| **JetBrains Jewel** (Apache-2.0), in `intellij-community/platform/jewel/markdown` | markdown in Compose | commonmark-java AST → recursive composables | How to render markdown without a WebView (A1b/A1c): blocks recurse, leaf blocks hold an inline **model** and the `AnnotatedString` is built at draw time, inline code takes `SpanStyle.background`, a quote's rule is `drawBehind`. Its `gfm-tables` extension gave 7.2c-2 its shape too: a table is rows of cells, cells hold **inlines only**, and column widths come from `maxIntrinsicWidth` in a custom `Layout` (`BasicTableLayout.kt`) |

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
- **An unexplained stall in cell execution — now seen twice, both times in `step5-completion`.**
  First sighting: the kernel came up in 11 s and the cell never finished within 169 s. Not
  reproduced then — step10 passed on the same build minutes later, and the flow passed three
  times after, once directly following a reinstall, which was the hypothesis.

  **Second sighting, 2026-08-07**, in the full sweep that closed the two device defects: the
  flow's first `Run` on `1 + 1` never produced `2`, and the failure screenshot
  (`evidence/notebook-defects-sweep/step5-completion-stall.png`) shows the cell **still
  spinning** with the Stop button up and no output — the same symptom, not a different one. It
  passed on an immediate re-run of the same build.

  **Not caused by that day's changes**, and this is worth stating rather than assuming: the
  changes were to *teardown* (`onCleared`) and to how the ViewModel is *obtained*, and the stall
  is in the first `execute` of a freshly launched app, before anything is torn down. Every driver
  `am force-stop`s first, which takes the server and its kernels with it, so nothing carries over
  between flows either.

  **Third sighting, 2026-08-11**, in `u5c-notebook-bar` during the tablet sweep that closed Phase
  U — and the first one that is not `step5-completion`, so the flow is not the variable. Same
  shape: first `Run` after a fresh launch, `[*]` with the spinner and Stop up, the second cell
  queued behind it, nothing after 120 s, and it passed on an immediate re-run of the same build.

  **This one has the `logcat` the previous two lacked, and what it says is that we cannot see.**
  Out of 33,279 lines, five mention the runtime at all:

      13:48:04.032  JupydroidSpace: starting jupyter_server
      13:48:07.946  JupydroidSpace: jupyter_server listening on http://127.0.0.1:39771/
      13:48:11.061  io_stats: a second libpython3.so (pid 14832)   <- the kernel process starts

  Then silence until the flow gave up at 13:50:07. So the server came up in 3.9 s and a kernel
  process exists; the stall is *after* that. Beyond it the app logs nothing at all - not the
  kernel session, not the websocket, not the `execute_request`, not a single received message -
  so the capture cannot say whether the request was sent, whether the socket ever connected, or
  whether the kernel replied and nobody read it. Three sightings and still no cause, because
  **the execute path has no observability**, which `CLAUDE.md` asks for in as many words.

  **The execute path says what it is doing now** (`KernelLog`, `evidence/kernel-observability/`).
  Eight stages as structured JSON under the existing `JupydroidSpace` tag: session opened, socket
  opened and connected and ended, every frame received *with the subscriber count*, frames
  dropped, request enqueued, execution complete. A stall now reads as one of three distinct
  things rather than as silence - nothing came back, something came back to nobody
  (`subscribers: 0`, which `MutableSharedFlow(replay = 0)` discards without a word), or the
  handshake never finished.

  **And the first healthy capture already found the prime suspect.** On every device run,
  `kernel.execute.request` precedes `kernel.socket.connected`: the app hands the frame to OkHttp
  before the handshake completes, and OkHttp queues it. `send()` returns true on *enqueue* - which
  is why the field is `enqueued` and not `sent` - so a request against a socket that never
  connects is waited on forever with `enqueued: true` and no error raised anywhere. That matches
  the stall's shape exactly: first Run after a fresh launch, no output, no failure, and fine on
  the retry once the connection is warm.

  **Fourth sighting, and the logging diagnosed it.** `step5-completion`, the X1a sweep. The log:

      kernel.execute.request   enqueued: true
      kernel.socket.connected  subscribers: 1
      kernel.frame.received    subscribers: 1  msgType: status   bytes: 632   (x3)
      <nothing further, no kernel.execute.complete>

  A healthy run of the same cell gets eight frames: `status` x3 at 632 bytes, then `status` 621,
  `execute_input`, `execute_result`, `execute_reply`, `status`. So the three frames the stalled run
  received are the kernel's **startup** statuses, and it never saw `execute_input` - which is the
  kernel echoing back the code it is about to run. **The `execute_request` was lost.**

  That rules out two of the three hypotheses outright: something did come back, and somebody was
  listening (`subscribers: 1`). It leaves the one the first capture flagged: the app enqueues the
  request *before* the handshake completes, OkHttp flushes it the moment the socket opens, and it
  reaches `jupyter_server` before its `open()` has finished associating the session - which drops
  it. JupyterLab does not send before the connection is up.

  `msgType` was added to the frame log for exactly this: the third sighting could say "three
  frames and then silence" and not which three, which is the difference between a symptom and a
  cause.

  **Not fixed - and the correction matters more than the change.** Holding the request until the
  socket connects was applied, and **run 24 of 50 stalled again with the ordering correct**:
  `socket.connected`, then `execute.request`, then three startup statuses and silence. So sending
  before the handshake was a real race worth removing - JupyterLab does not do it either - but it
  is not the mechanism. One failure in 75 runs is also not evidence of improvement: that is what a
  one-in-fifty defect looks like.

  What remains unknown is whether those three `status` frames answer our request or are the
  kernel's own startup chatter - indistinguishable without `parent_header.msg_id`, which is now
  logged on every frame. The next candidate is the one `jupyter_client` and JupyterLab both handle:
  `jupyter_server` completes the websocket handshake before its `ZMQChannelsWebsocketConnection`
  has subscribed the session to the kernel's channels, so a request sent straight after `Opened`
  can still be dropped; the standard answer is `kernel_info_request` / `kernel_info_reply` before
  any user request.

  The change that was made: `SessionSocket` carries a `CompletableDeferred` completed on `Opened`, and `execute`
  awaits it before sending; a cell on an already-open socket returns from that await at once.
  `settle` completes it *exceptionally* when the socket ends first - without that, waiting for a
  handshake that never lands turns a lost frame into a hang, which is a worse defect than the one
  being fixed and an easy one to introduce here.

  **Reproduced in a unit test, which is worth more than the field capture.**
  `ScriptedKernelSocketOpener` can now withhold `Opened` the way a real socket does and records
  anything sent before it as `sentBeforeOpen`; that list is non-empty before the fix. So the
  mechanism is not inferred from a log any more - it is driven.

  **Fifth sighting, 2026-08-12**, in the sharded sweep that closed X2f/X2g - `step7-rich-output`
  this time, so that is three different flows now and the flow is certainly not the variable. It
  passed on an immediate re-run of the same build, as every previous one has. The trail is the
  cleanest yet, and it says two useful things:

      kernel.socket.connected  subscribers: 1
      kernel.frame.received    msgType: status  parent: …_24843_1  bytes: 632
      kernel.frame.received    msgType: status  parent: …_24843_2  bytes: 632
      kernel.frame.received    msgType: status  parent: …_24843_1  bytes: 632
      kernel.execute.request   msgId: 80b86798-…  enqueued: true
      <nothing further>

  The ordering fix is holding - `socket.connected` precedes the request, which the first capture
  said it did not. And `parent` settles the question the third sighting could not: the three
  frames carry parent ids the app never sent (`…_24843_*` is the kernel's own session counter,
  not our `msgId`), so they are the kernel's startup chatter and **no frame answering our request
  ever arrived**. Same conclusion as the fourth sighting, now proven from the frame rather than
  inferred from its absence: the request is lost after `send()` reports it enqueued.

  So the remaining candidate is the one already named above - `jupyter_server` completing the
  websocket handshake before `ZMQChannelsWebsocketConnection` has subscribed the session to the
  kernel's channels.

  **Correction (2026-08-12, found while planning X2h): the round trip was already written, and
  this entry was wrong to call it unwritten.** `SessionSocket.greetKernel`
  (`RealKernelGateway.kt:264`) has sent `kernel_info_request` and awaited the reply since `f2363c5`,
  covered by `KernelInfoHandshakeTest`. So the fifth sighting happened **on a build that already had
  it** - which changes what the trail means. A healthy run answers within 70 ms:
  `socket.connected` → `kernel_info_reply` (parent = our msgId) → `execute.request` → complete
  (`evidence/u5c-notebook-bar/logcat.txt:3801-3821`). The fifth sighting has **no
  `kernel_info_reply` at all**, three startup statuses in the gap where it belongs, and then the
  request. So the greeting itself went unanswered, the 3 s failsafe released, and the request was
  sent into the socket anyway.

  **Sixth sighting, 2026-08-13**, in the X2h-3 sweep - `step7-html-output`, a fourth distinct flow -
  and it is the first one captured *after* this entry was corrected, so it was read against the
  right expectation. It confirms the reading rather than adding to it
  (`evidence/kernel-observability/sixth-sighting/`):

      kernel.socket.connected  subscribers: 1
      kernel.frame.received    msgType: status  parent: …_22495_2  bytes: 632
      kernel.frame.received    msgType: status  parent: …_22495_1  bytes: 632
      kernel.frame.received    msgType: status  parent: …_22495_1  bytes: 632
      kernel.execute.request   msgId: 7251f2f0-…  enqueued: true
      <nothing further>

  **No `kernel_info_reply` at all**, where a healthy run gets one within 70 ms. So the greeting
  itself went unanswered, the 3 s failsafe released, and the request was then sent into a socket
  the server had not finished wiring - which is precisely what the correction above predicted the
  trail would look like. Passed on an immediate re-run of the same build, as all five before it.

  **Not caused by X2h-3, and this is worth stating rather than assuming**: that step's changes are
  a `listFiles` on a timer and a `path` field in the kernel-start body. Neither touches the socket,
  and the stall's shape is the one recorded since 2026-08-07.

  That is JupyterLab's own standing FIXME, sitting three lines below the one already quoted here:
  *"It may be preferable to retry kernelInfo rather than give up after one timeout"*
  (`packages/services/src/kernel/default.ts:1589-1591`). We copied the failsafe including its known
  weakness.

  **The mechanism, finally read rather than inferred (2026-08-13).** It is in `jupyter_server`, and
  it is not subtle once looked at. `services/kernels/websocket.py`:

      async def open(self, kernel_id):
          super().open()                   # the websocket handshake completes HERE
          await self.connection.connect()  # the nudge happens AFTER

  `super().open()` completes the handshake - the client is told "connected" - and only *then* does
  the server await `connect()`, whose own docstring says callers "should `await` the returned Future
  before relying on the connection being live". `on_message` is wired the whole time. So there is a
  window in which a client is connected, can send, and whose request reaches a kernel whose zmq
  subscriptions are not established. That is the stall, and it is why `enqueued: true` was never
  evidence of anything.

  **The server's own way out of that window is to keep asking.**
  `ZMQChannelsWebsocketConnection.nudge()` (`services/kernels/connection/channels.py`) "keeps
  retrying kernel_info_request until" it has both a shell reply and an iopub message, "ensuring that
  zmq subscriptions are established" - every **0.5 s**, bounded by `kernel_info_timeout`, whose
  default is **60 s** and whose help text calls it "how long the kernel can take before being
  presumed dead" (`services/kernels/kernelmanager.py:152-162`). The three unexplained `status`
  frames in the fourth, fifth and sixth sightings were replies to *those* nudges: their parents are
  `<our session id>_<pid>_<n>`, minted by the server's `Session`, never by us.

  **The retry that mechanism suggests was tried, and it is not the fix — the seventh sighting.**
  `greetKernel` was put on the server's own cadence: ask again every 0.5 s, up to the server's 60 s
  rather than JupyterLab's client-side 3 s. Measured on the tablet over 80 runs, it stalled twice
  (`evidence/kernel-observability/seventh-sighting/`), and both trails say why asking again cannot
  work:

      kernel.greet.sent
      kernel.frame.received  msgType:"status"  parent:"<sessionId>_13002_2"   (x3)
      kernel.greet.timedout  asked:"120"

  **120 asks over 60 s and not one `kernel_info_reply`**, while iopub *was* delivering - those three
  `status` frames are replies to the server's own nudges. On a stalled connection iopub carries and
  the shell channel carries nothing at all, so asking that channel again is asking a channel that is
  not delivering replies. Reverted to the reference's shape (one ask, 3 s failsafe, matched on
  parent `msg_id`); the 60 s only bought a stalled cell a minute in Queued ahead of a request it
  lost anyway.

  **`kernel.greet.sent` / `.answered` / `.timedout` now exist**, which is what made that sighting
  readable at all: a stall used to be inferred from an *absence*, and an absence cannot tell "the
  greeting was never sent" from "it was sent and ignored". Inferring from absence is what cost this
  investigation its third sighting.

  **What the eighth sighting needs, and does not yet have.** The server's own account. At
  `--ServerApp.log_level=DEBUG` it distinguishes `Nudge: attempt N on kernel X`, `No such channel`
  and `Received message on closed websocket` - three mutually exclusive causes with three different
  fixes, all invisible at INFO. The flag is in `jupyterServerArgs` now and
  `step5-completion-repeat-driver.sh` pulls `run/jupyter_server.log` on every failure, but **it has
  not yet been built and run**: the APK behind the seventh sighting predates the flag. Next
  recurrence, capture both halves before theorising about a fix.

  **On the repeat runs, and what they are worth.** `scripts/step5-completion-repeat-driver.sh`
  runs the flow N times, each a fresh launch with a fresh kernel - the state all four sightings
  were in. Absence is not provable this way: at roughly one in twenty, 25 clean runs happen by
  chance about 27% of the time even with nothing fixed, and 75 runs about 2%. The claim is
  therefore that the mechanism is identified, reproduced and removed, with no recurrence in the
  runs recorded - not that the stall is proven gone. A recurrence arrives pre-diagnosed now: the
  driver dumps that run's `kernel.*` lines and the frame sequence says at once whether the request
  was lost again. See `evidence/kernel-observability/stall-fixed.md`.

  **A harness defect found while chasing it, and fixed:** `run_flow` copied screenshots and
  logcat into `evidence/` only when the flow *passed* - `set -e` killed the driver at the failed
  assertion first. The evidence for a failure is the evidence that matters, and it was the one
  thing being discarded; the logcat sitting in `evidence/u5c-notebook-bar/` was from a run 84
  minutes earlier. It captures either way now, and propagates the failure afterwards.
- **`PythonTypingRules` reads the line as text, not as tokens**, so a block opener with a
  trailing comment (`if x:  # note`) gets no extra level. Fixing it means asking the lexer
  where the comment starts, which is a dependency the transformation does not otherwise have;
  cheap enough to add if it turns out to grate, deliberately not paid for up front.
  Replacing a selection is likewise left alone rather than wrapped in the typed bracket -
  wrapping is a separate feature, and half-doing it would leave an unmatched closer.
- ~~**`SyntaxKind.NUMBER` has the weakest contrast of the five colours.**~~ **Closed by U1.**
  It mapped to `colorScheme.secondary`, so on the dark scheme it sat close to the body text
  (`evidence/step5.2/tablet/04-syntax-colours.png`) - and, more to the point, the value came from
  the user's wallpaper, so nothing could assert it. The colours now come from a palette read out
  of IntelliJ's own schemes and every one of them has its WCAG ratio pinned by
  `EditorPaletteTest`. See "Phase U / U1".
- **The server has no lifecycle.** `BaseSpaceKernelGateway` starts `jupyter_server` on
  first use and nothing ever stops it (~143 MB RSS held while backgrounded).
  `disconnect()` closes the socket only. Step 18 owns this. The old `SpaceProbe`
  that made it worse - starting a server on every launch - is gone as of 10.5.
- **`dirty` is tracked and shown nowhere.** `EditorUiState.dirty` is set by every edit and by
  every `CellEdits` transform, cleared by `persist()`, and asserted in unit tests — and no
  composable reads it. Step 4's row in the table above still lists "dirty state" as a
  deliverable, so read that as *modelled*, not *visible*. It is arguably right that it stays
  invisible: saving is automatic and debounced, so a "•" that appears for 500 ms after every
  keystroke would be noise. The gap is the claim, not the behaviour — decide whether anything
  should render it, or drop it from the deliverable.
- **`openNotebook` guards only the top of the back stack.** `lastOrNull() == Editor(id)` is
  enough today because a notebook can only be opened from Recents, which is always the entry
  directly beneath it. A future screen that opens a notebook from *inside* another one — a link
  between notebooks, a Console attached to one — could push a duplicate `contentKey` deeper in
  the stack, and two entries sharing a `contentKey` share a `ViewModelStore` and a
  `SaveableStateHolder` slot. JupyterLab's `findWidget` searches every context for the path
  rather than only the active one (`packages/docmanager/src/manager.ts:390-418`), which is the
  shape to grow into. Not paid for now, because there is no second caller to justify it.
- ~~**`EditorViewModel` sits exactly on detekt's function limit**~~ **Spent, as predicted, by X2g.**
  Adding `stopWriting` made it twelve, and the answer was the one this entry called for: the pure
  `CellAction` dispatch (`applying`, `newCellId`) moved to `CellEdits.kt`, beside the transforms it
  dispatches to. It is back to ten, so there is room for one more before the next split - and the
  run/kernel half named here is still the candidate.
- ~~**The browser only refreshes when it comes back.**~~ **Closed by X2h-3**, with the reference's
  own poll (`DEFAULT_REFRESH_INTERVAL = 10000`, `standby: 'when-hidden'` -> `RESUMED`). Closing it
  turned up the defect underneath: the kernel had no working directory at all, so X1a's promise
  that a notebook can `open()` a file the user can see had never held for a relative path. Both
  are written up under X2h-3.
- **29 drivers call bare `adb` rather than `$ADB`**, so the suite only runs from a shell that has
  `platform-tools` on its PATH - `maestro-run.sh` already computes `$ADB` with a fallback for
  exactly this, and half the drivers use it. It surfaces as a flow that passes every step and then
  fails in its own disk check (`adb: command not found`), which reads like a defect and is not.
  Cheap to fix, deliberately not folded into X2f/X2g: it is 29 files of churn in a step about the
  file browser.
- ~~**Nothing in the repo runs every Maestro flow.**~~ **Closed.** `scripts/run-all-flows.sh` runs
  every driver on one device and prints a pass/fail line each; `scripts/run-flows-sharded.sh`
  splits the suite across every attached device at once, respecting each flow's `TABLET ONLY` and
  `WIDE WINDOW` rule rather than dealing them blindly. The X2f/X2g sweep went through the sharded
  one: 47 passed, 2 failed, 0 skipped, with both devices attached so every rule was satisfiable -
  and both failures were explained rather than waved through (the bare-`adb` PATH entry above, and
  the fifth sighting of the execution stall).
- **`step5-brackets` is tablet-only too, and nothing said so at all.** Found while re-running the
  keyboard-touching flows after the `adjust=pan` fix: it fails on the dev36 emulator at
  `Input text [` → expect `[]`, and passes on the tablet on the same build. Attributed rather
  than assumed — it fails on the emulator against the U1a build, the U1 build **and** c373b74,
  so it predates all of that and is an emulator IME artefact, not an app defect and not a
  regression. It is not even in the group below, because it runs no cell: the device rule this
  one needs is about the *keyboard*, which is a second reason a flow can be device-specific.
  Whatever runs the whole suite has to model both.
- **`notebook-session-reattaches` is tablet-only, and only a comment says so.** It runs cells, so on
  the x86_64 emulator `MainActivity` falls back to `FakeKernelGateway` and the flow fails rather
  than skipping. The same is true of every other cell-running flow; the device rule lives in
  `CLAUDE.md` §2 and in each flow's header, and nothing enforces it. Whatever runs the whole
  suite (above) is where that belongs.

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
