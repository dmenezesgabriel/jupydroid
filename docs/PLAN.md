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
| 10 | Real KernelGateway (WebSocket) | swap fake → real behind the port |
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

Not yet tested, and material before concluding anything: **real arm64 hardware.**
Only an x86_64 emulator has been measured, a published write-up reports this same
patchelf approach working on Android 15 arm64, and `FEASIBILITY.md` §9.1 asked for
the spike on real devices specifically. Emulator kernels differ from shipping ones.

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
