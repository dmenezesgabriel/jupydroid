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

Licensing: Winlator is LGPL-2.1 and Callisto is proprietary, so per
`STUDY_CASES.md` this is clean-room — techniques studied, no code vendored.

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
| 5 | CodeMirror 6 cell editor | per-cell WebView: self-sizing + keyboard-aware height, theme via CSS vars, id-based async completion, `Mod-Enter`, debounced edit, `?mode=`; bridges as JVM-testable core |
| 6 | Execute behaviour | Run → `KernelGateway.execute` → outputs; Running/Idle; Stop/interrupt; execution count (FakeKernel) |
| 7 | Output rendering | stream/error/display_data renderers, themed output area, collapse, clear outputs |
| 8 | **Risk gate** (base Space) | x86_64 rootfs → `files/spaces/base/`, loader launch, `jupyter_server` on 127.0.0.1, kernel started, cell run — broken into 8.1–8.6 below |
| 9 | Phase 1: JupyterLab-in-WebView | WebView → `/lab`, cleartext config |
| 10 | Real KernelGateway (WebSocket) | swap fake → real behind the port — broken into 10.1–10.5 below |
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
- **4b** Editing + execution shell — cell header Run/Stop, long-press context
  menu, Run All, busy indicator, dirty state. Deferred until after the Step 8
  gate, because it is written against a real kernel.

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
| 10.1 | `JupyterMessage` readers for the rest of iopub: `stream`, `display_data`, `execute_input`, `status` | pure JVM, frames as strings | unit tests only — no device, no socket. Round-trips into the existing `CellOutput` types |
| 10.2 | `KernelSocket`: open, keep open, emit frames, close. No Jupyter semantics | fake `WebSocket` behind an interface owned by us | unit tests for open/frame/close/failure; no real server |
| 10.3 | `RealKernelGateway.execute` as a `Flow<KernelEvent>`: correlate by `msg_id`, map frames to `Output`/`ExecutionCount`, complete on `status: idle` | 10.1 + 10.2 fakes | contract-tested against the same suite as `FakeKernelGateway` — that is what the port is for |
| 10.4 | `connect`/`disconnect` lifecycle, one socket per session, and `interrupt` via `POST /api/kernels/<id>/interrupt` | fakes, then device | device: a long cell interrupted mid-run actually stops |
| 10.5 | Swap it into `AppContainer` behind the port and delete `SpaceProbe`'s kernel exercise | — | **Maestro on the tablet**: open a notebook, run a cell, see output. The first flow that proves the product rather than the runtime |

Notes that keep this honest:

- **`complete` is not in Step 10.** The port declares it, `FakeKernelGateway` answers it,
  and nothing calls it until the CodeMirror editor lands in Step 5. Implementing it here
  would be untested-by-use code. It stays fake until Step 5 needs it, and that is a
  deliberate deferral rather than an oversight.
- **Each slice keeps the app working.** `FakeKernelGateway` stays wired in `AppContainer`
  until 10.5, so 10.1–10.4 cannot break a running app.
- **Maestro lands at 10.5, not before.** 10.1–10.3 have no UI to drive; claiming a
  Maestro gate on them would be theatre. Device evidence at 10.4, real flow at 10.5.
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
- Emulator handwriting IME steals focus from text fields and swallows Maestro
  `inputText`; disable it once per boot: `adb shell settings put secure
  stylus_handwriting_enabled 0` (+ `settings put secure stylus_handwriting 0`).
- VIEW-intent evidence (2.4): `scripts/step2-import-driver.sh` pushes
  `.maestro/fixtures/Intro to NumPy.ipynb`, scans it into MediaStore, and starts
  the app with `am start -a VIEW -d content://media/external/file/<id> -t '*/*'
  -f 0x1` (shell can grant MediaStore URIs but not DocumentsUI ones, whose path
  also does not carry the extension).

---

## Where we are, and what to do next (2026-08-04)

**Step 8 is closed, 8.7 included.** On a Galaxy Tab A9+ (Android 16, arm64) the
whole runtime chain works: bundled Alpine/musl rootfs → extracted to app storage →
interpreter exec'd from `nativeLibraryDir` → stock `jupyter_server` on loopback →
kernel spawned as a subprocess → `2 + 2` over the WebSocket returns `4`. All
`ExecutionOutcome` branches are verified against a real kernel. Evidence per step
in `evidence/step8.*/`.

**8.7 is unblocked. Step 8 is closed.** The syscall that killed numpy is
**`mbind` (235)**, numpy does not need it, and the process died only because
nothing handled the SIGSYS that Android's `SECCOMP_RET_TRAP` raises. With a
handler that reports and returns, numpy and scipy import *and compute correctly*
inside the app process - including a 4-thread 512x512 matmul and an FFT. Exit 0
across the board. `evidence/step8.7/sigsys-syscall-named.txt`.

This retires the guest-kernel question without measuring it: no VM, no QEMU, no
bionic build farm. The runtime keeps the architecture Step 8 proved - musl
rootfs, interpreter exec'd from `nativeLibraryDir`, stock musllinux wheels. See
the "The correction" section of **`docs/environment-fidelity.md`** for why this
is not the per-gap libc patching that document rejects.

**The handler is runtime behaviour, not a diagnostic.** It lives in
`SpaceRuntime.environment()`, so the server, the kernel and every future Space
subprocess inherit it by construction rather than by each launcher remembering.
That mattered: the kernel is spawned by `jupyter_server`, not by us, and
`import numpy; numpy.linalg.det(...)` returning `10.0` through the WebSocket is
what proves the preload survived that hop
(`evidence/step8.7/numpy-in-a-notebook-cell.txt`). It reports every distinct
refusal to stderr, which the server log captures - a handler that swallowed them
would trade loud deaths for quiet wrong answers.

**The runtime is arm64-only, and `SpaceAbi` now says so.** x86_64 cannot run a
Space at all, so listing it promised something no build could deliver. The
x86_64 *native libs* still ship so the APK installs on emulators for UI work;
`SpaceProbe` treats a missing Space as a logged no-op rather than a crash.

### Small items to clear

- numpy and scipy work, so they stay in `tools/rootfs/requirements.lock`.
- Packaging is the open decision: debug APK is **115 MB**, of which **79 MB** is
  the rootfs (278 MB unpacked). Asset pack versus first-run download. Measure
  from a clean build - an incremental one reports ~175 MB because zipflinger
  leaves gaps where replaced entries were.
- Restart the `dev36` emulator; it died mid-session. Fine for UI and Maestro,
  never for the runtime.

### What can proceed in parallel, safely

Steps 5-7 (CodeMirror 6 cell editor, execute behaviour, output rendering) and
Step 4b (cell Run/Stop, long-press menu, Run All, dirty state). `2 + 2 = 4`
already works end to end, and the userland question is now settled, so there is
no longer a pending decision for the UI to be built against.

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
- `SpaceProbe` starts a server and a kernel on every app launch and never stops
  them (~143 MB RSS held while backgrounded). Scaffolding by intent, and Step 18
  owns the lifecycle - but it must not outlive Step 10's UI.

### Closed by measurement, so nobody re-investigates them

- **Orphaned Space processes: not a problem.** The server and kernel are ordinary
  children of the app process, and both `am force-stop` and `am kill` take them
  with it - Android kills the whole process group. Nothing leaks across app death.
- **`ExecutionOutcome.TimedOut`: exercised on device.** Driven with a live kernel
  running `time.sleep(5)` against a 2 s channel - alive and merely slow, which is
  the case the branch exists for, not a kernel that died or dropped. Returned
  `TimedOut(after=2s)`.
- **x86_64 degrades cleanly.** The app starts on the emulator, logs
  `no Space runtime here: no rootfs is built for ABI 'x86_64'; expected one of
  [arm64-v8a]`, and runs. No crash, and no stack trace for a designed outcome.
