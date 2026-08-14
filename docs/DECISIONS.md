# Decisions — what is settled, and what it forbids

Every entry here is a constraint on future work, not a history entry. Each was measured or read
out of a source, and each names where. If a decision here disagrees with code, the code is wrong.

How things get built is `CLAUDE.md`. What gets built next is `docs/PLAN.md`. This file is why
neither may be re-litigated without new evidence.

Citations are either a path in `reference/research_repos/…` or a path **inside the shipped
rootfs**, readable without extracting:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tgz
tar -xzOf $T usr/lib/python3.12/site-packages/ipykernel/kernelapp.py | sed -n '605,620p'
```

---

## Runtime

### Exec model

**A `targetSdk 36` app cannot `execve` anything under `filesDir`.** AOSP's SELinux change removed
`execute_no_trans` from app data files and kept `execute` (commit `0dd738d8`, recorded from the
original Step 8 research):

| Operation on a file in `filesDir`, targetSdk ≥ 29 | Allowed? |
|---|---|
| `execve()` — needs `execute_no_trans` | **blocked** |
| `dlopen()` / `mmap(PROT_EXEC)` — needs `execute` | allowed |

The behaviour stands on the device evidence regardless of the commit id: the app works only
because it is arranged this way (`evidence/step8.2*`, `evidence/step8.3/`).

Winlator `ProcessBuilder`-execs out of `filesDir` only because it sets `targetSdkVersion 28`
(`reference/research_repos/winlator/app/app/build.gradle:9`; Termux does the same). **We do not
follow it there.**

**So: anything that must be executed ships in `jniLibs/<abi>/` and is reached through a symlink
from the rootfs.** Android extracts `jniLibs` to `/data/app/…/lib/<abi>/` with SELinux context
`apk_data_file`, where execution is permitted; the kernel resolves the symlink and checks the
*target's* context. Two files use this today — `libloader.so` (musl's loader) and `libpython3.so`
(CPython) — patched at build time by `tools/rootfs/stage-app-artifacts.sh`:

```
patchelf --set-interpreter /data/data/com.jupydroid.app/files/spaces/base/lib/ld-musl-aarch64.so.1 \
         --set-rpath      /data/data/com.jupydroid.app/files/spaces/base/usr/lib:…/lib \
         libpython3.so
```

`PT_INTERP` names a **rootfs** path because `/data/data/<pkg>` is stable and `nativeLibraryDir`
is not; `BaseSpaceInstaller.linkLoaderToNativeLibrary` points that path at the native library on
every launch.

**This forbids:** proot, chroot, `LD_PRELOAD` exec interceptors, `/system/bin/linker64` tricks, a
targetSdk downgrade, and the NDK (nothing is compiled — we ship prebuilt ELFs). It also means
**every new tool costs an ABI-sized ELF in `jniLibs`** — which is what defers uv and what the
terminal must pay for busybox.

`useLegacyPackaging = true` is load-bearing: without it the libs stay inside the APK.

### musl, not glibc; arm64, not x86_64

Android 16's app seccomp filter kills glibc's libc startup with SIGSYS on both ABIs; musl starts
cleanly under the same filter with everything else held constant. The Jupyter stack still comes
from ordinary wheels — musllinux rather than manylinux — so nothing is hand-cross-compiled.
`evidence/step8.3/`.

x86_64 has no rootfs at all: the same filter kills musl there too. The app logs
`no Space runtime here: no rootfs is built for ABI 'x86_64'` and falls back to
`FakeKernelGateway`, which echoes a cell's source back as its output. **No emulator flow may run
a cell** — it would go green against the fake. `evidence/step8.3/musl-is-arm64-only.txt`.

### SIGSYS is a signal to handle, not a verdict

Android refuses syscalls outside bionic's allowlist with `SECCOMP_RET_TRAP`, and an unhandled
SIGSYS terminates the process — which is why `import numpy` used to die.
`tools/rootfs/sigsys_report.c` installs a handler that reports the syscall and returns, leaving
the `ENOSYS` the kernel already set. numpy and scipy then work in-process; no VM, no emulation.
`evidence/step8.7/`.

Reports go to a log file, never stderr: ipykernel forwards a kernel's stderr as cell output.

**Residual:** a refused syscall a package genuinely *needs* is still fatal. The set is discovered
by running software and reading the reported refusals — bounded and observable, not open-ended.

### Two Android hazards inside CPython, both worked around, both still true

- **`psutil.net_if_addrs()` segfaults.** `getifaddrs()` gets EACCES from Android's network
  restrictions and psutil does not check the return — `psutil/__init__.py:2246`,
  `rawlist = _psplatform.net_if_addrs()`, `exit=139`. The SIGSYS handler cannot help: this is a
  SIGSEGV. psutil cannot be dropped (an `ipykernel` dependency), but ipykernel only uses it for
  process management, so the hazard is one function reachable only from a user's own notebook.
  `--KernelManager.transport=ipc` exists partly to keep `jupyter_client` off it.
- **IOPub's forked-subprocess pipe binds TCP and aborts.** `IOPubThread._setup_pipe_in` binds
  `tcp://127.0.0.1` regardless of transport; on Android libzmq turns the EACCES into an
  `errno_assert` → SIGABRT that ipykernel's `except zmq.ZMQError` never reaches.
  `tools/rootfs/jupydroid_kernel.py` selects the library's own default `IOPubThread(pipe=False)`.
  **Cost:** output from processes the kernel *forks* is not captured; cell output, tracebacks and
  display data are unaffected. `evidence/step8.5/getifaddrs-root-cause.txt`.

### `nativeLibraryDir` moves on every app update

The extracted Space survives an update; the native library directory does not (per-install random
path segments). So the loader symlink **and** the kernelspec `argv` are rewritten on every launch,
not just on a fresh install. Found the hard way: `TarGzExtractor` once wrote *through* the stale
loader symlink and crash-looped the app.

**Every install-time path needs a second install to be tested at all.**

### Kernel logging cannot leak into cell output

Three links, all verified in the shipped tree, and worth knowing before turning on any kernel
logging: `jupyter_client/launcher.py:68-76` leaves `stdout`/`stderr` as `None` off Windows, so the
kernel **inherits** the server's; `JupyterServerLauncher` redirects the server's output to
`run/jupyter_server.log`; and `ipykernel/kernelapp.py:546-554` detects a log handler bound to fd 2
and reroutes it to the raw file descriptor (*"Seeing logger to stderr, rerouting to raw
filedescriptor"*). So kernel logs land in the server's log file, not in the notebook.

**Measured, once the kernel was actually put at DEBUG** (P0a): 29 KB of kernel DEBUG in
`jupyter_server.log` and nothing on screen, `evidence/kernel-observability/shell-dispatch-logged/`.
It is upstream behaviour rather than ours, so `step10-run-cell-real-kernel` asserts it —
`assertNotVisible: ".*MESSAGE TYPE.*"` after the run — instead of trusting that an ipykernel
upgrade will keep it.

---

## Protocol and behaviour

### nbformat is an open format, so a writer that rebuilds is a shredder

Every interface in `packages/nbformat/src/index.ts` extends `PartialJSONObject` — "these keys, plus
anything else" is the contract. Unknown fields ride on the domain type as `PreservedJson` and are
written back verbatim; a duplicated cell keeps them, because there is nothing on disk to merge a
new id with. Eight distinct losses (`tags`, `attachments`, `language_info`, …) were found by
measuring rather than reading.

### Kernels start through sessions

`POST /api/sessions` with `{path, type, name}` — `type` is `ext === '.ipynb' ? 'notebook' : 'file'`
and `name` is the basename (`packages/docregistry/src/context.ts:82-90`); the session model carries
the kernel (`packages/services/src/session/session.ts:381`, `IModel.kernel`).

**Reattach needs no client-side lookup.** `jupyter_server/services/sessions/handlers.py:85-90`
returns the existing session when the path already has one
(`session_exists` → `get_session`, else `create_session`), so a `listRunning()`-and-find would be
duplicated logic, not parity. Measured: two visits to one notebook produce two client session ids
and **one** kernel id, and the second POST takes 2 ms against the first's 854 ms
(`evidence/step17-sessions/`). `interrupt` and `restart` stay on `api/kernels/<id>`, where
JupyterLab leaves them too.

### An unanswered greeting replaces the kernel; it is never asked twice

`kernel_info_request` going unanswered means the shell channel is not carrying replies, and
asking it again does not change that — measured, 2 stalls in 80 runs with 120 asks and no reply
(`evidence/kernel-observability/seventh-sighting/`). Every sighting cleared on a fresh kernel, so
the recovery is a **restart followed by one more greeting**, and if that is no better the cell
**fails** rather than being handed to a channel known to be silent.

Three consequences, each deliberate:

- **The socket is not reopened with it.** JupyterLab's `restart()` follows the REST call with
  `reconnect()` for "cases where kernel ports have changed" (`default.ts:547-563`), but
  `jupyter_server` calls `km.restart_kernel(kernel_id)` with `newports` defaulting to False, and
  its `on_kernel_restarted` only publishes a status. The endpoints do not move.
- **This departs from the reference, which sends the pending messages anyway after the timeout.**
  That failsafe is what produced the stall: a request enqueued against a channel carrying nothing,
  waited on for ever with no error raised anywhere.
- **Typing never triggers it.** `complete` waits on the same handshake, so recovery would
  otherwise discard the variables of a kernel someone had merely opened a notebook onto. A
  completion has an answer for "the kernel cannot say"; a cell does not.

### The kernel's working directory is the session's path

Without it a cell's `open('data.csv','w')` raises `OSError: [Errno 30]` on a read-only filesystem.
The server root (`--ServerApp.root_dir`), the kernel's `HOME` and the repository root are **one
tree**.

### Cell semantics come from `NotebookActions`, and most are not the obvious answer

Read out of `packages/notebook/src/actions.tsx` and `packages/cells/src/model.ts`:

- Deleting the **last** cell leaves an empty cell, not an empty notebook — of
  `notebookConfig.defaultCell`, not hardcoded "code" — and the replacement is inserted **inside
  the same transaction**, "to make the deletion of a notebook's last cell undoable". A cell with
  `deletable: false` is skipped, and deleting a collapsed heading takes its children.
- Clearing outputs also clears the **execution count** — `clearExecution` at
  `packages/cells/src/model.ts:706` sets `executionCount = null`.
- A **duplicate keeps** the original's outputs and count; only its id is renewed — and the one
  exception is deliberate: a code cell pasted after a **cut** keeps its id (`addCells`, the
  `lastClipboardInteraction === 'cut'` branch).
- **Splitting keeps the outputs on the *last* fragment**, not the first: the original cell object
  becomes the final piece "to preserve kernel connection", and every new cell is created with
  `outputs: undefined`. It splits at every selection, not only at the caret.
- **The undoable unit is one `sharedModel.transact { }` block**, over a Yjs `undoManager`
  (`actions.tsx:1704`). That is why `deleteCells` inserts its replacement cell inside the
  transaction rather than after it.

Mime-bundle preference order is JupyterLab's `defaultRank`, not taste.

---

## Editor

### The lexer is generated, never hand-written

A rule, not a preference. The grammar is `antlr/grammars-v4` at
`42b1ea521705c126d84331186b221c9819db1058`, path `python/python3_14`
(`tools/grammars/python/README.md:3`), turned into a lexer by ANTLR 4.13.2
(`gradle/libs.versions.toml:19`). Both licences are permissive and neither is native — so no NDK,
no CMake, no 16 KB page-size obligation, and no crash surface that debugs like C.

`python3_14`, **not** `python3`: the older grammar discards comments
(`SKIP_: (SPACES | COMMENT | LINE_JOINING) -> skip`) so they can never be coloured. f-strings
tokenise structurally (`FSTRING_START` / `FSTRING_MIDDLE` / `LBRACE` / …), so `{name}` can be
coloured as an expression.

Two costs, measured: the **parser** is generated too (`PythonLexerBase.java` constructs a
`PythonParser`), and `INDENT`/`DEDENT` are synthetic, zero-width and carry inverted ranges — they
must be filtered or they produce negative-length spans.

### A Compose-native cell editor, not a WebView

Changed on measurement, 2026-08-05. Per-cell CodeMirror WebViews cost **+56.7 MB** for one 30-cell
notebook plus a **93.4 MB** renderer process; `BasicTextField` + `TextFieldState` with our own
highlighting costs **+10.5 MB** and no renderer — about **139 MB saved**, on the same tablet with
everything else held constant. Compose owns IME composing regions, autocorrect, gesture typing,
selection handles and the magnifier; we own only the language layer. `evidence/step5.2/tablet/`.

### The palette is read out of IntelliJ, and its contrast is pinned

Syntax colours used to come from `colorScheme` roles — so `NUMBER` sat close to the body text and
the value ultimately came from the user's wallpaper, which nothing could assert. Colours now come
from IntelliJ's own schemes with every WCAG ratio pinned by `EditorPaletteTest`.

### Markdown renders natively — no WebView, no HTML

commonmark-java AST → recursive composables, following JetBrains Jewel
(`reference/research_repos/intellij-community/platform/jewel/markdown`, Apache-2.0): blocks recurse,
leaf blocks hold an inline *model* and the `AnnotatedString` is built at draw time, inline code
takes `SpanStyle.background`, a quote's rule is `drawBehind`. Its `gfm-tables` extension gave
`RichTable` its shape: cells hold inlines only, and column widths come from `maxIntrinsicWidth` in
a custom `Layout`. The same applies to `text/html` outputs — parsed with jsoup, drawn as Compose.

**Audit A1c:** a rendered markdown cell renders by default and a tap opens the editor **with the
caret placed**. The first version opened an editor with no caret, which no JVM test could see.

### ANSI handling today is a stripper, not an emulator

`RenderedOutput.kt:96-102` regex-replaces SGR escapes out of tracebacks
(`withoutAnsiColours()`). It is not reusable for a terminal, which needs a grid with cursor
addressing — see `docs/PLAN.md` P4d.

---

## Architecture and UI

### `TwoPaneSceneStrategy` was hand-written, and its reason has now expired

U7 wrote `TwoPaneSceneStrategy.kt` + `TwoPaneScene.kt` (165 lines) because
`NavigableListDetailPaneScaffold` puts both panes inside **one** `NavEntry`, which stops the editor
being a navigation entry at all — and both nav3 decorators key on `NavEntry.contentKey`. That
breaks ViewModel *identity* (`viewModel { }` resolves on class name, so notebook B is handed
notebook A's ViewModel) **and** lifetime (`onCleared` never runs, so the kernel socket is never
closed). The library answer was rejected for a second reason: `adaptive-navigation3` had no stable
release.

**That second reason no longer holds.** `androidx.compose.material3.adaptive:adaptive-navigation3`
**1.3.0 is the published release version** (maven-metadata.xml, dated 2026-08-12), and it ships
`ListDetailSceneStrategy` / `rememberListDetailSceneStrategy` and `SupportingPaneSceneStrategy` /
`rememberSupportingPaneSceneStrategy` — scene strategies, the same shape as ours. U7 said in as
many words: *"If that artifact goes stable, this is the thing to delete."*

**What has not changed, and must be checked rather than assumed:** its POM still pins
`adaptive-navigation` **strictly** at `[1.3.0]`, taking the adaptive family off the Compose BOM,
and it wants `androidx.navigation3:navigation3-ui:1.0.0` where the project is on
`nav3Core = "1.0.1"`. And the property that actually mattered is that **both panes stay separate
`NavEntry`s**. Scheduled as `docs/PLAN.md` **P1e**, gated on the U7 evidence re-run.

Two panes at **840dp**, not 600dp: 360dp of list plus a divider leaves 239dp for the notebook in a
600dp window, narrower than the phone the single-pane layout was designed for. A width rule, never
a device one.

### Touch rules, and the two that cost something to learn

- **48dp is the minimum touch target** (Material), pinned by `CellTouchTargetTest`. The nuance is
  worth keeping because it once produced a false accessibility bug report: `IconButton` applies
  `minimumInteractiveComponentSize()` **outside** the caller's modifier, so wrapping one in
  `Modifier.size(32.dp)` shrinks the ripple and *not* the pressable area — measured on device at
  48×48dp with the wrapper in place. What the test is really guarding is a control built from a
  bare `clickable`, which would have a real 24dp target and nothing else to catch it.
- **A long press on a cell is not available.** `BasicTextField` owns that gesture for text
  selection, so cell actions need a visible control — which is why the cell header carries a `⋮`
  button and not a hidden gesture. It took three attempts to learn: a press on the cell produced
  Android's Select all / Autofill toolbar, and a press on the header worked but was
  undiscoverable. **Nothing on screen announcing an interaction is not an interaction.**
- **Anything drawn above the soft keyboard needs `imePadding()` *and* `navigationBarsPadding()`** —
  the second for the moment between the keyboard closing and the layout catching up (`SymbolRow`).
- **A behaviour that cannot be reached on a phone is adapted, never invented**, and the adaptation
  is written down beside what it departs from.

### The breakpoints, so 840dp is not a magic number

`WindowSizeClass` (official): compact `< 600dp`, medium `600–840`, expanded `840–1200`, large
`1200–1600`, extra-large `≥ 1600`. The app reads
`WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND` rather than a literal.

Two panes start at **expanded**, not medium, and the arithmetic is the reason: the list pane is
360dp, so a 600dp window leaves the notebook 239dp — narrower than the phone the single-pane
layout was designed for — where 840dp leaves it 479dp.

### Window width is a parameter, never measured inside a composable

`currentWindowAdaptiveInfo()` reads the *activity's* window, which `DeviceConfigurationOverride`
does not change — so a test that "forces a width" drives one branch twice while appearing to cover
both. `MainNavigation` takes `twoPane` as a parameter.

### A file's name is its identity (X1b)

A notebook's id is its path, suffix included; renaming the title renames the file. The rule and the
409-on-collision belong to **files**, not to notebooks — the same rule governs `.py` and anything
else in the workspace.

### Reads may hit the repository; server-side algorithms may not be re-implemented

The workspace is read from disk directly (`DiskWorkspaceRepository`) rather than through
`/api/contents`: it is faster and works before the server is up. But checkpoints, notebook
trust/signing and nbconvert export are **server-side algorithms** and go through the server's API.
Re-deriving HMAC notebook signing in Kotlin would be re-implementing a security mechanism.

**Writing directly has one known consequence, and it is not free.** `ContentsManager` signs a
notebook on save — `check_and_sign` over `nbformat.sign.NotebookNotary`
(`services/contents/manager.py:116-120,694-706`) — so a notebook this app writes is **unsigned**
and would open untrusted. That is inert today because nothing renders trust, and it is a decision
P2j has to make rather than discover.

---

## Harness

- **Never `kill -9` maestro.**
- **A flow against a lock screen is not a red result, it is no result.** A 50-run sweep passed 31
  and then failed 19 in a row with the app blameless: `stay_on_while_plugged_in` is 0 on the
  tablet, the screen went off, and every run after that drove the lock screen — the failing steps'
  screen-hierarchy captures contain clock digits and nothing else. `maestro_test` now sets
  `svc power stayon usb` and refuses to run unless **both** `mWakefulness=Awake` and
  `mDreamingLockscreen=false`. Two signals because they fail apart: a device woken by
  `KEYCODE_WAKEUP` reports `Awake` while still behind its PIN, which is how the first repair of
  this still lost two runs. A secure keyguard cannot be dismissed over adb, so the harness says so
  and stops.
- **Evidence must be captured on failure, not only on pass.** `run_flow` once copied screenshots
  and logcat only when the flow passed — `set -e` killed the driver at the failed assertion first,
  discarding the one capture that mattered.
- **Cell-running flows are tablet-only**, because on x86_64 they would assert against
  `FakeKernelGateway`. `step5-brackets` is tablet-only for a second reason: an emulator IME
  artefact. `scripts/run-flows-sharded.sh` respects each flow's `TABLET ONLY` / `WIDE WINDOW`
  header.
- **A popup's position is not observable from a Compose test** — a popup has its own window, so
  `getUnclippedBoundsInRoot` reports coordinates inside it. Anchoring is a screenshot gate.
- **An index over a scrolling list does not address the cell it names.** `index: 1` counts the
  buttons *rendered*; a narrower window changes which those are.

---

## Licences

Winlator (LGPL-2.1), sora-editor (LGPL-2.1) and Callisto (proprietary) are **clean-room**:
techniques studied, no code vendored. For sora this is load-bearing rather than tidy — it is the
reference for the editor we write ourselves, and a fork would stay LGPL-2.1, so forking answers the
licence question with "no".

`reference/` is gitignored and travels with no clone. The three consulted here, at the remotes the
local clones actually point at:

```sh
cd reference/research_repos
git clone --depth 1 https://github.com/jupyterlab/jupyterlab
git clone --depth 1 https://github.com/JetBrains/intellij-community.git   # 2.4 GB
git clone --depth 1 https://github.com/brunodev85/winlator
```

---

## Closed by measurement, so nobody re-investigates them

- **Orphaned Space processes are not a problem.** The server and kernel are ordinary children of
  the app process; `am force-stop` and `am kill` take them with it. Nothing leaks across app death.
- **`ExecutionOutcome.TimedOut` was exercised and retired.** A cell that runs for an hour is a
  cell, not a failure. Stopping one is the user's job, via interrupt.
- **Sending an `execute_request` before the socket connects was a real race and not the stall.**
  Removed anyway (JupyterLab does not do it either); run 24 of 50 stalled with the ordering
  correct.
- **Retrying `kernel_info_request` on the server's own 0.5 s cadence does not fix the stall.**
  Measured over 80 tablet runs: 120 asks over 60 s, not one reply, while iopub delivered. Reverted
  to the reference's shape. `evidence/kernel-observability/seventh-sighting/`.
- **Waiting for the server's DEBUG log to explain the stall will not work.** The server's nudge is
  satisfied by a **control** reply alone (`jupyter_server/services/kernels/connection/channels.py:158-164`
  and `:291-292`), so a kernel with a dead shell channel looks healthy to it. See `docs/PLAN.md` P0.
