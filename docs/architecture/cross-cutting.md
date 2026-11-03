# 8. Crosscutting concepts

arc42 §8. Concerns that appear in more than one building block. A decision that forbids an
alternative is an [ADR](../adr/); this file describes the mechanism as it stands.

**Cite symbols, not line numbers.** Every line number in the previous revision of this documentation
had moved by the time it was next read. A symbol name survives a refactor; `SpaceRuntime:42` did not.

## 8.1 The exec model

**The single most load-bearing constraint in the app.** A `targetSdk 36` app cannot `execve` anything
under `filesDir`: AOSP's SELinux policy removed `execute_no_trans` from app data files and kept
`execute`, so `dlopen()` and `mmap(PROT_EXEC)` are allowed and `execve()` is not.

So **every Space process runs under proot**, with the Space's own directory as `/`. Inside a
translated root the image runs as it was built — `/usr/bin/python3` is simply itself, and a `#!`
console script execs through its own shebang line, which proot honours the way the kernel would.
proot is a ptrace supervisor, so it sees every exec and every path its tracees issue and rewrites
them into the root.

Two files ship in `jniLibs/arm64-v8a/` and are extracted to `nativeLibraryDir`, the only place the
context above lets anything exec: `libproot.so` and `libproot-loader.so`. The loader is carved out
beside it and named in `PROOT_LOADER` because unset, proot writes its embedded loader under
`filesDir` and execs it from there — which the same rule refuses (`scripts/stage_proot.py`). These
are also the only things execve'd from `nativeLibraryDir`: it moves on every app update, and nothing
inside a Space may carry a path into it, because proot translates that path into the root, where it
does not exist.

`SpaceRuntime` sets `PATH` to `pkgs/bin` followed by the image's own `etc/profile` list under the
Space root, so a console script the user installs into `pkgs/bin` shadows the image's own names and
`/sbin/apk` resolves by name - the terminal shell is non-login, so this env is the only PATH it sees.

The retired mechanism — four patched ELFs relinked into `nativeLibraryDir` on every launch, and a
static `shebang-exec` trampoline for `#!` scripts — is recorded in
[ADR-0002](../adr/0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md) (superseded). What
it forbids stands: chroot, `LD_PRELOAD` exec interceptors, a `targetSdk` downgrade and the NDK. Why
the userland is musl and why only arm64 ships a runtime is
[ADR-0003](../adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md). The decision is
[ADR-0073](../adr/0073-a-space-owns-its-rootfs-and-runs-under-proot.md).

## 8.2 Sandbox fidelity — SIGSYS

Android's app seccomp filter refuses a syscall with `SECCOMP_RET_TRAP`, which raises **SIGSYS**, and
an unhandled SIGSYS terminates the process. The kernel has *already* made the refused call return
`-ENOSYS` by the time the signal is delivered, so `tools/rootfs/sigsys_report.c` installs a handler
that reports and **returns** — and numpy and scipy then import and compute correctly, in-process,
under threads and BLAS.

Three preloads ship, wired through `SpaceRuntime.PRELOADS`: `libsigsys_report.so`,
`libmusl_at_shim.so` (arm64-absent syscalls musl still issues) and `libgetaddrinfo_shim.so`
([ADR-0005](../adr/0005-interpose-getaddrinfo-rather-than-redirect-resolv-conf.md)).

The decision is [ADR-0004](../adr/0004-handle-sigsys-and-return.md); the full reasoning, including
the argument it overturned, is [`../research/android-sandbox-fidelity.md`](../research/android-sandbox-fidelity.md).
Residual risk is `GAP-003`.

## 8.3 Which reference answers which question

A statement that "the reference does X" must name **which** reference. `reference/` is gitignored and
travels with no clone; the pinned SHAs and clone commands are in [`../README.md`](../README.md).

| Reference | Authoritative for | Already checked and **empty** for |
|---|---|---|
| **the shipped rootfs** | what the server and kernel on *this device* actually do | — |
| **JupyterLab** | behaviour, the kernel protocol, the command surface | — |
| **VS Code** | presentation: notebook layout and cell chrome, the palette, the search viewlet | the kernel wire protocol — it reaches a Jupyter server through an extension and speaks no ZMQ |
| **vscode-jupyter** | kernel selection and status **as an extension contributes them** (`src/kernels`, `src/notebooks/controllers`) | **notebook chrome** — every cell part is core VS Code, not this repo |
| **IntelliJ** | the editor: syntax, LSP client shape (`platform/lsp`), markdown (`platform/jewel/markdown`), notebook layout (`notebooks/visualization`), terminal **integration** (`plugins/terminal`) | the Jupyter kernel-protocol client; the Jupyter file browser; a terminal **emulator** — that is JediTerm, an external library |
| **Termux** | the **terminal**: a VT emulator (`terminal-emulator/`), drawing a buffer with a monospace font (`terminal-view/TerminalRenderer.java`), the extra-keys row with its latching modifiers | its exec model — it sets `targetSdkVersion=28` |
| **Winlator** | container lifecycle: install, version-stamp, create/duplicate/remove, browse | its exec model — it sets `targetSdkVersion 28` |
| **jupyterlab-git** | the Git extension: its REST surface (`specification/Git_REST_API.yml`), what each endpoint answers, and its panel | — |
| **jupyter-ai** | the AI extension: its chat, personas and providers, across the packages its `.gitmodules` names | — |
| **colab-vscode** | reaching a Colab runtime: the sign-in (`src/auth/`) and the runtime API (`src/colab/client/`) | — |
| **Compose docs**, developer.android.com | which component exists and how it behaves on a phone or tablet | — |

**Two references answer for presentation, and the split is deliberate.** VS Code decides how the
notebook *looks*; JupyterLab decides how it *behaves*. IntelliJ stays the editor reference for
everything below the palette.

`box64` and `box86` are x86 userspace emulators, relevant only if this app ever had to run a
non-native binary — which the exec model does not do: proot translates paths, it does not emulate an
ISA. `reference/xeus-android/` is two prose documents, not source.

## 8.4 Licences — clean-room versus transcription

What may be copied is decided per reference, and it is load-bearing rather than tidy.

| Reference | Licence | What we may do |
|---|---|---|
| VS Code, vscode-jupyter | MIT | **transcribe** with attribution — every transcribed colour carries its colour id in a doc comment |
| Termux `terminal-emulator`, `terminal-view` | Apache 2.0 — carved out of the repo's GPLv3, inherited from Jack Palevich's Android Terminal Emulator | **transcribe**, naming the upstream path and the pinned SHA in the file's KDoc |
| JediTerm | Apache 2.0 at the reader's option | transcribe with attribution |
| jupyterlab-git, jupyter-ai | BSD-3-Clause | **transcribe** with attribution; their Python server halves are installed into the image, not vendored |
| colab-vscode | Apache 2.0 | transcribe with attribution |
| Jupyter Server | BSD-3-Clause | **transcribe** with attribution, credited at its version (no SHA is pinned) |
| Winlator | LGPL-2.1 | **clean-room only** — techniques studied, no code vendored |
| sora-editor | LGPL-2.1 | **clean-room only**; a fork would stay LGPL-2.1, which answers the fork question with "no" |
| Callisto | proprietary | **clean-room only** |

One attribution point per file is the form this repo uses, and a transcribed file names its upstream
project and **pinned SHA** rather than a `reference/…` path. The repository carries an Apache-2.0
`LICENSE` and a `NOTICE`; which projects the `NOTICE` names is
[ADR-0134](../adr/0134-notice-credits-what-was-copied-and-a-person-decides-the-edge.md).

The decision and what it forbids are
[ADR-0018](../adr/0018-transcribe-under-permissive-licences-clean-room-otherwise.md).

busybox is built here rather than taken from Alpine, for a seccomp reason rather than a licence one —
[ADR-0006](../adr/0006-build-busybox-here-with-suid-compiled-out.md).

## 8.5 Design tokens

`theme/ColorRamp.kt` holds VS Code's resolved Dark/Light palette; `theme/EditorPalette.kt` is entirely
VS Code's. Every Material role is mapped onto that ramp and each pair is held to a contrast floor,
pinned by `EditorPaletteTest`.

Three scales, one gate each: Material's type scale stated rather than hand-built, `theme/Spacing.kt`
on the 4dp grid, and `theme/CodeText.kt` at 14sp/18sp with `FontFamily.Monospace` confined to
`EditorTheme.kt` — `scripts/check_monospace_home.py` is the gate that keeps it there. Every tap target
reserves Material's 48dp.

The workbench is drawn as **parts with edges**: each part paints its own border on the side facing the
next, and the document pane *states* `editor.background` rather than inheriting it from a Material
role that could be re-mapped. `scripts/check_palette_consumers.py` refuses a palette entry nothing
draws.

## 8.6 Testing strategy

Four tiers, and each answers a question the tier below it cannot.

| Tier | Runs with | Answers |
|---|---|---|
| JVM unit | `:app:testDebugUnitTest` | logic, parsers, the emulator, port contracts |
| Port contract | the same task, once per implementation | that a fake and its real adapter agree |
| Instrumented | `:app:connectedDebugAndroidTest` | whether there is something *visible to press* |
| Maestro on device | `scripts/maestro-run.sh` | that the real app, on the device `AGENTS.md` rule 2 names, does the thing |

A port contract is written once and run per implementation, which is why the unit-test *execution*
count exceeds the `@Test` site count. Any figure of this kind is **produced, never stored** — a
stored count in this repo's documentation drifted at every phase that did not re-count it.

Four rules earned the hard way, each of which has silently passed a gate here before:

- **An assertion that cannot fail is not an assertion.** A wait that any state satisfies, or a
  `assertVisible` on a label present before the action, reports green against nothing.
  `scripts/check_maestro_output_assertions.py` gates the kernel-output case.
- **A caption is a claim, and it goes stale like any other.** Read the screenshots; a green log is
  only half the evidence.
- **A gate that reads prose is a gate that reads the wrong file.** A comment-blind scanner is the
  difference between checking code and checking what someone wrote about it — `scripts/kotlin_source.py`
  exists so every gate strips comments the same way.
- **A change to shared chrome owes the flows that draw it a re-run.** One panel row added 48dp above
  two others, and a flow that had been red for weeks went unnoticed because nothing re-asked.
- **A gate whose task list is discovered rather than declared can stop covering things silently** —
  [ADR-0016](../adr/0016-name-every-gate-task-explicitly.md).
- **Evidence is a directory, and a renamed screenshot leaves its predecessor behind** —
  [ADR-0017](../adr/0017-prune-stale-evidence-frames-from-a-per-flow-manifest.md).

### What a test here cannot do

Each of these passed against the wrong thing before it was written down.

| Limit | Why, and what sees it instead |
|---|---|
| **`assertVisible` on a matched cell does not prove the notebook scrolled to it** | A `LazyColumn` composes and keeps a margin beyond its own viewport, and a composed-but-clipped item is still in the semantics tree. What sees it is the **other end** of the notebook: `assertNotVisible` on a cell that is on screen exactly when the landing did not happen |
| **Where the editor is scrolled to is not assertable by Maestro at all** | The editor is one node carrying the whole file, so any text in it matches at any scroll position. The gutter is not an escape either — line numbers are *drawn* by `Modifier.lineNumbers`, not composed as nodes. What sees it is the screenshot, and an instrumented test reading the node's own `VerticalScrollAxisRange` |
| **Whether a Material text button is enabled is not readable by Maestro** | A `TextButton`'s nodes all report `enabled=true` in the accessibility dump, disabled or not, with its name on the button or on its label: measured on Settings' Register with `uiautomator dump`, which read the live and the dead one alike. An `IconButton` named on the button itself does carry its state (`EditorTopBar.kt`). What sees it is an instrumented `assertIsNotEnabled`, the screenshot, and a tap that changes nothing |
| **A lazy list in a forced-size two-pane test lays out 0×0** | `currentWindowAdaptiveInfo()` reads the *activity's* window, which `DeviceConfigurationOverride` does not change. A `Column` overflows a parent with no room and its children still exist; a `LazyColumn` composes only what fits, so the rows stop existing. Host the panel on its own where it has a window to be laid out in |
| **A scroll action inside a `waitUntil` hangs the whole instrumentation** | `performScrollToNode` synchronises on idleness, and while a walk is in flight a `LinearProgressIndicator` is animating, so idle never arrives. The hazard is the action, not the loop. `ui/ScrollUntilFound.kt` is the shape that works |
| **A layout assertion in dp measures the component's padding by accident** | "Two items fill the bar" read 312dp of 320dp and failed, the missing 8dp being `NavigationBar`'s own item gap. Assert the *relationship* the rule is about — the fraction of the bar between the two items' centres is 0.500 and 0.250, scale-free and padding-free |
| **The 320dp emulator is not the only narrow device** | The one-pane windows here are two: the phone emulator at 320dp, and **the tablet rotated to portrait** at 800dp, which is above Material's 600dp boundary and which the phone cannot reach. A one-pane feature owes its second width a run before it is called shipped, exactly as a two-pane one owes the tablet |
| **The emulator is 320dp wide *and* density 160**, and only the first is deliberate | A density-dependent assertion that passes there may not pass anywhere else |
| **A giant multi-line `Text` cannot be asserted on** | Maestro full-matches a node's text, so a node carrying many lines matches nothing you can write |
| **A `Canvas` carries no semantics of its own** | Nothing drawn on a canvas is in the semantics tree. A transparent `Text` per row is what makes the terminal readable to a test — and to TalkBack |
| **Maestro's `visible:` and `extendedWaitUntil` cannot reliably read the terminal's text** | It is drawn, not composed. A pixel oracle reads it instead — and a pixel oracle tuned against one pane layout reads the wrong pane in another, so it takes the layout as a parameter |
| **A seeding driver must not `clearState` after it seeds** | It throws away exactly what it just set up. The seed goes last, or the state goes with it |
| **The first `inputText` after a terminal opens races the field's IME connection** | `waitForAnimationToEnd` does not cover it — the animation is over before the connection exists |
| **`assertNotVisible` on a generic label is wrong once the label's owner moves** | The label reappears somewhere legitimate and the assertion fails, or it never appears and the assertion proves nothing. Assert on something the owner uniquely carries |
| **A swipe that starts on the soft keyboard never reaches this app at all** | The gesture is consumed before it arrives. Start it above the keyboard |
| **A differential that prints deltas measures neither side's baseline** | Two implementations can differ from each other identically at every step and both be wrong. Print both sides, not the gap |
| **A pty echoes, so "type X, read X back" proves nothing** | A terminal in canonical mode echoes the typed line before any program sees it, so `echo ok` → `ok` passes whether or not a shell ever ran. Worse for the cwd: busybox ash's default `PS1` is `\w \$`, so `pwd` matches the **prompt**, which is replayed the instant an attach connects. An assertion must name something that cannot appear in the command text or the prompt — `echo $((6*7))` → `42`, `echo cwd=$(pwd)` → `cwd=<path>` |
| **Instrumentation runs every test class in one process** | So a second `JupyterServerLauncher` starts a second server against the same `run/` directory, where the runtime file is cleared and then read by name: one server wipes the other's file, either may read the other's port, and both truncate the one log every Space investigation reads. `BaseSpaceUnderTest` is the suite's composition root, holding the one server, the way `JupydroidApplication` holds the one container per process. **No device test may call `spaceServerLauncher` for itself** |
| **An emulator with a hardware keyboard cannot prove a phone's layout** | A flow green on `dev36` failed on the tablet, and the assertion was not what was wrong: opening a markdown editor takes the caret, the soft keyboard comes up, and it covered the cell the frame compared against. `dev36` has a hardware keyboard, so nothing ever came up. Any flow that opens an editor and then looks *below* it must `hideKeyboard` |
| **`wm size` is not a geometry on its own** | It sets the *natural* orientation's resolution, so `1080x2400` on an auto-rotated tablet came out `2400x1080` — a landscape two-pane shell, which is not the layout being measured. A driver that overrides the window must also lock rotation and restore it on exit |
| **A one-second `assembleDebug` looks exactly like a stale APK** | Gradle's build cache returns a previously-compiled variant in about a second, indistinguishable from "nothing was rebuilt" in the log. For a **before/after** pair that difference is the whole evidence: a `before` frame captured from an `after` APK is green, identical to its neighbour, and worthless. A paired capture **verifies the artefact first** — `unzip -p app-debug.apk classesN.dex \| strings \| grep -c <symbol the change deletes>`, which is the only thing standing between a cached build and a fabricated pair |
| **Maestro text selectors are `Regex.matches` — full-string, never substring** | `maestro/Filters.textMatches`. A row titled `Provider.ipynb (28d1cb31)` is not matched by `text: "Provider.ipynb"`, and the tap lands on whatever *is* an exact match — in one case the file-list row behind the dialog. A row carrying a generated suffix needs the trailing `.*`. **An assertion that goes red on a row visible in the screenshot should have its selector suspicion-checked before the layout is** |
| **`childOf` scopes to the first node its selector matches, never to all of them** | Measured on the Console, where every entry carries its own `Cell output` node: `childOf: {text: "Cell output"}` matched entry 1's output and never entry 2's, failing three runs in a row with entry 2's text on screen and in Maestro's own failure dump; unscoped, the same text matched. Turned around, an `assertNotVisible` scoped this way on a later output cannot fail. Scope by `childOf` only to reach the first such node; otherwise assert a kernel-built value no typed source holds, which needs no scope |

Each flow that cannot assert its own landing **says so in its own header** and names the two things
that can. That is the working agreement's *"a green log is only half the evidence"* made concrete.

## 8.7 Compose and Android gotchas, measured here

Each of these cost a debugging session and none is obvious from the API.

- **`imePadding` on a shared ancestor pulls a sibling's own height budget negative.** Two children
  cannot each ask the same ancestor for the keyboard's height; the second one gets a negative
  constraint. Spend the inset once, at the node that owns it.
- **`RowScope.weight` reads parent data off the *direct* child**, not off whatever modifier a nested
  composable was handed. A weight passed down through a wrapper silently does nothing.
- **`key(...)` around a value that looks like churn can be load-bearing.** Removing a `key` on a
  redraw token stopped a `Canvas` from recomposing at all; it looked like a pointless allocation and
  was the only thing forcing the frame.
- **An `AlertDialog`'s text slot clips at its own bound, so a `Column` + `verticalScroll` inside it produces
  no scroll range.** The overflow draws inside the dialog surface, the node reports `scrollable=false`, and no
  gesture — nor `scrollUntilVisible` — can move it. A `LazyColumn` bounded below the slot turns overflow into a
  real scroll range and lets off-edge items compose by scrolling.
- **`minimumInteractiveComponentSize` inflates the *semantics* bounds, not the layout.** A `selectable` row
  shorter than 48dp is padded to the touch minimum **inside its semantics node only**. Interior rows inflate
  into a neighbour unnoticed; the last row's inflation crosses the list's clip and is reported **not visible to
  accessibility while every pixel shows**. `heightIn(min = 48.dp)` on the row makes the layout bounds the same
  as the announced ones. **This forbids** any `selectable`/`clickable` row in a scrollable dialog that relies on
  the semantic padding to look tappable.
- **`currentWindowAdaptiveInfo()` reads the *activity's* window**, which `DeviceConfigurationOverride`
  does not change. Window width is a **parameter**, never measured inside a composable, or a test
  that "forces a width" drives one branch twice while appearing to cover both.

## 8.8 Logging and observability

Structured JSON when logging for debugging or observability; plain text only for user-facing CLI
output. `logcat` is captured as evidence but **never committed** — it is the whole device's log, and
`scripts/check_device_secrets.py` refuses it. A README quotes the lines it cites.

---

# 12. Glossary

| Term | Means here |
|---|---|
| **Space** | a self-contained Python environment on the device, `files/spaces/<id>/`. A first-class domain type, named after SageMaker Studio rather than "environment", which would collide with the Python environments inside one |
| **rootfs** | the bundled musl/Alpine filesystem shipped as `rootfs-<abi>.tzst` and unpacked into a Space |
| **base Space** | the Space bundled with the app, id `base` |
| **port** | an interface in `domain/port/`, contract-tested, with one `Fake…` and one real adapter |
| **adapter** | an implementation of a port, in `data/<mechanism>/`, named `<Mechanism><PortConcept>` |
| **composition root** | `di/AppContainer` — the only place that constructs implementations, built once per *process* |
| **console script** | a `#!`-headed executable the image ships in `usr/bin`/`usr/local/bin` (and pip installs into `pkgs/bin`), exec'd through its own shebang line under proot |
| **nbformat** | the notebook file format. An **open** format: unknown fields ride on the domain type as `PreservedJson` and are written back verbatim |
| **flow** | a Maestro script in `.maestro/`, driven by a `scripts/*-driver.sh`, with its output in the local `evidence/<step>/` |
| **alternate screen** | the VT buffer `vi`, `less` and `htop` switch to; it has no scrollback |
