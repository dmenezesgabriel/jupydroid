# JupyterDroid — Build Plan

A JupyterLab client for Android that runs the **real** stack on the device: stock
`jupyter_server` and `ipykernel` in a bundled musl rootfs on loopback, driven by a
Compose-native UI. No remote server, ever.

**This file is forward-looking only.** What is already settled — and what that forbids — is
`docs/DECISIONS.md`. How work is done is `CLAUDE.md`. How each step was reached is `git log` and
`evidence/`.

**Parity is not a matter of taste.** JupyterLab's `packages/notebook-extension/src/index.ts`
declares 99 command ids, 96 of them `notebook:*`; `filebrowser-extension` 36,
`console-extension` 21, `terminal-extension` 11, `documentsearch-extension` 5,
`toc-extension` 5. Those lists are the specification. A behaviour we cannot reach on a phone is
**adapted, never invented**, and the adaptation is written down beside what it departs from.

Every citation below is either a path in `reference/research_repos/…` or a path **inside the
shipped rootfs**, which is readable without extracting:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tgz
tar -xzOf $T usr/lib/python3.12/site-packages/jupyter_server/serverapp.py | sed -n '1969,1980p'
```

---

## Where we are

Working end to end on a Galaxy Tab A9+ (`SM-X216B`, Android 16, arm64): bundled Alpine/musl
rootfs → interpreter exec'd from `nativeLibraryDir` → stock `jupyter_server` on loopback →
kernel as a subprocess through `POST /api/sessions` → tap Run → output. Notebooks live on one
filesystem the kernel, the server and the app all share.

| Area | State |
|---|---|
| Runtime | rootfs installs, server starts, kernels start and **reattach**; numpy/scipy import; the server stops when it is idle or the app is left, and comes back on the next run |
| Notebook | cells editable, syntax-highlighted, executed; outputs (stream, error+ANSI, PNG, `text/html`, tables) render natively; collapse; nbformat round-trip preserves unknown fields |
| Editor | `BasicTextField` + generated ANTLR lexer, auto-indent, bracket match, line numbers, kernel-backed completion, symbol row |
| Files | explorer with create / rename / delete / browse, Recents as a section, polling on the reference's own cadence (`packages/filebrowser/src/model.ts:26`, `DEFAULT_REFRESH_INTERVAL = 10000`, `standby: 'when-hidden'` at `:106`) |
| Shell | two panes ≥ 840dp, IntelliJ-derived palette, per-notebook ViewModel scoping |
| Tests | 101 instrumented, 737 unit, 62 Maestro flows, `run-flows-sharded.sh` across both devices, gradle + script gates |

**Not built at all:** the Space system, a terminal, LSP, the debugger, the console, search, ToC,
undo, settings, raw cells, headings, multi-select, trust, export.

---

## How to read this

Phases run in order. Each sub-step is **one commit** and closes on one gate. `CLAUDE.md` owns the
rules that gate every commit (TDD, Maestro evidence, the device rule, formatting, hooks); nothing
here repeats them. What each sub-step adds is the citation that settles it, and the gate that
closes it.

If a sub-step cannot be closed in one functional commit, split it before writing code.

### Which reference answers which question

| Reference | Authoritative for | Already checked and **empty** for |
|---|---|---|
| **JupyterLab** `reference/research_repos/jupyterlab` | behaviour, the kernel protocol, the command surface | — |
| **The shipped rootfs** | what the server and kernel on *this device* actually do | — |
| **Winlator** `reference/research_repos/winlator` | container lifecycle: install, version-stamp, create/duplicate/remove, browse | its exec model — `app/app/build.gradle:9` is `targetSdkVersion 28` |
| **IntelliJ** `reference/research_repos/intellij-community` | the editor: syntax, LSP client shape (`platform/lsp`), markdown (`platform/jewel/markdown`), notebook layout (`notebooks/visualization`), terminal **integration** (`plugins/terminal`) | Jupyter kernel-protocol client; Jupyter file browser; a terminal **emulator** — that is JediTerm, an external library (`plugins/terminal/BUILD.bazel:55-56`), not in this tree |
| **Compose docs** developer.android.com | which component exists and how it behaves on a phone or tablet | — |

A statement that "the reference does X" must name **which** reference.

---

# P0 — the execution stall

One defect, nine sightings across four distinct flows since 2026-08-07, every one passing on an
immediate re-run of the same build. It **used** to be the only thing in the app that could hang
forever with no error; since P0b it costs about 1.4 s and a replaced kernel. What remains is that
it invalidates any measurement taken above it, and that its cause is unknown.

## What is established from the frames

On a stalled connection the socket connects, **iopub delivers**, and the shell channel answers
nothing — not the app's `kernel_info_request`, and not 120 asks over 60 s (seventh sighting,
`evidence/kernel-observability/seventh-sighting/`). Three `status` frames arrive carrying parent
ids the app never sent (`<sessionId>_<pid>_<n>`, minted by the server's `Session`), then silence.

## What is new, and it re-frames the whole investigation

Read out of the shipped `jupyter_server` and `ipykernel`, not inferred:

```
jupyter_server/services/kernels/connection/channels.py:291-292
    self.session.send(shell_channel,   "kernel_info_request")
    self.session.send(control_channel, "kernel_info_request")

jupyter_server/services/kernels/connection/channels.py:158-164   (nudge docstring)
    "Returns a Future that will resolve when we have received
     a shell OR control reply and at least one iopub message"

ipykernel/kernelbase.py:365   (process_control)
    self._publish_status("busy", "control")
```

The server's nudge asks **both** channels and is satisfied by **either**. A kernel whose shell
channel alone is dead therefore still passes the nudge: control replies, the control handler
publishes `status` on iopub parented to that request, both futures resolve, the server declares
the connection live and logs nothing.

Two consequences:

1. **The three unexplained `status` frames are the kernel answering the *control* nudge.** The
   trail is fully accounted for; only the cause of the dead shell channel is open.
2. **The server is structurally unable to notice this failure.** That is why six sightings
   produced no server-side complaint — and it retires the previous plan of waiting for the
   server's own account at DEBUG. It will not have one.

**Both are now confirmed on device rather than read out of source.** With the kernel at DEBUG
(P0a, shipped), a healthy run logs `Nudge: attempt 1`, then a shell dispatch **and** a
`Control received:` for a second request, then `Nudge: shell info reply received`
(`evidence/kernel-observability/shell-dispatch-logged/`).

## The eighth sighting answered it: the request never reached the kernel

Read with both halves of the trail for the first time
(`evidence/kernel-observability/eighth-sighting/`). Nine milliseconds after the kernel dispatched
the server's shell nudge and replied to it, the app's `kernel_info_request` produced **no dispatch
line at all** — and then three seconds in which neither process logged anything, ended by the app's
restart. The same kernel handled a `shutdown_request` on **control** immediately afterwards, so the
process was healthy; its shell channel had simply stopped delivering inbound.

So of P0's two candidate causes it is the first — *the request never reached the kernel* — and not
*the kernel received it and never replied*. What that still does not say is **where** it was lost:
the server logs no line per relayed websocket message, so the server's write and the kernel's read
remain indistinguishable.

**This sharpens the open question rather than closing it.** Inbound shell delivery stopped while
control and iopub, each on their own thread and loop, kept working — which is the shape
ipykernel 7's `ShellChannelThread` would produce, and it gives P0c a specific prediction to
falsify whenever P0c is picked up.

## Where the cause has to be

ipykernel 7 moved the shell channel into its own thread. `ipykernel/shellchannel.py` defines
`ShellChannelThread`; `ipykernel/socket_pair.py` routes to subshells over inproc `PAIR` sockets
and is marked `.. versionadded:: 7`. `ipykernel/kernelapp.py:610-618` binds `shell_stream` to
that thread's `IOLoop` and **starts the thread**, and `Kernel.start()`
(`kernelbase.py:546-570`) registers `shell_stream.on_recv(...)` afterwards, from the main thread.
None of this existed in ipykernel 6. The control channel — the one that has never stalled — is
the older path.

## P0c — parked, deliberately, with everything needed to restart it

**Decided 2026-08-14: not scheduled.** P0's user-visible damage is fixed — a stall costs ~1.4 s and
a replaced kernel — so what is left is diagnosis, and it is worth less per hour than P1. Parked
rather than dropped, because the stall still invalidates any timing measured above it. Everything
below was established while sizing it; none of it needs re-deriving.

**The experiment is cheaper than this plan used to say, and sharper.** The variable to remove is
the shell channel *thread*, not the ipykernel version — and **7.3.0 still ships the pre-7 topology
behind it**: `kernelapp.py:609,617,465` each guard on `if self.shell_channel_thread`,
`kernelbase.py:562-568` wires `shell_stream.on_recv` straight onto the main loop when it is
absent, and `_supports_kernel_subshells` (`:1546`) then returns False so a subshell request is
refused rather than crashing. `JupydroidKernelApp` already overrides `init_iopub` for a comparable
reason, so `shell_channel_thread = None` is a supported configuration of the shipped kernel and
changes exactly one variable. The ipykernel 6 pin stays the fallback, since it also changes zmq
usage.

**One prerequisite, and it is the actual cost.** `jupydroid_kernel.py` is baked into the image
(`tools/rootfs/Dockerfile:44`), so a six-line change to *our own* kernel entry point needs an
emulated arm64 rebuild — and this host has no `binfmt` registered (`docker run --platform
linux/arm64` fails `exec format error`; `docker run --privileged --rm tonistiigi/binfmt --install
arm64` is how the shipped tarball was evidently produced). Traitlets config cannot substitute:
`init_sockets` assigns the attribute unconditionally *after* config load, so only a subclass
override works. The clean unblock is to ship `jupydroid_kernel.py` as an **app asset written into
the Space on install**, exactly as `SpaceKernelSpec` writes `kernel.json` — which P4's busybox and
P5's language server will want anyway.

**The arithmetic was wrong and is now measured.** Since P0b a stall shows as `R` on a *passing* run
costing ~1.4 s rather than a failure costing a 180 s timeout, so runs are cheap and every stall is
recorded. But the rate is **2 recoveries in 81 valid tablet runs, ≈ 2.5%**, not the 1-in-20 this
plan assumed. Zero in 50 runs is then a **29%** event even if nothing changed — no evidence at all,
where the earlier note claimed 8% by using the wrong rate. p < 0.05 needs roughly **120 runs per
arm** (~2 h at ~55 s/run), with the ipykernel-7 baseline topped up to match.

**P0a and P0b have shipped.** A stall is now recovered from rather than waited on: an unanswered
greeting restarts the kernel and greets the replacement once, and if that is no better the cell
fails instead of spinning (`docs/DECISIONS.md`). Measured over 50 tablet runs — 50 passed, 0
failed, and **1 was a real stall recovered in 1.4 s**
(`evidence/kernel-observability/recovery-fifty-runs/`).

**P0 is therefore closed as a repair and parked as a diagnosis.** Work moves to P1; P0c's state is
recorded below so picking it up costs nothing.

---

# P1 — lifecycle, and the infrastructure debt around it

The server used to run until the app was killed - **~130 MB** across a server and one kernel, held
by an app nobody was using (`evidence/step18-lifecycle/`). **P1a, P1b and P1c have shipped**, and
between them that is closed: the gateway survives a stopped server, the server culls an idle kernel
and stops itself after ~12 minutes of nothing, and an app that is left stops it after 30 s of
continuous idleness - never under a running cell, and the next run brings a fresh one back in
about 7 s. What each cost and what it forbids is `docs/DECISIONS.md` and
`evidence/p1c-app-death-backstop/`.

What is left of P1 is the per-document half of the same question, and two pieces of debt beside it.

| # | Sub-step | Gate |
|---|---|---|
| **P1d** | **`DELETE /api/sessions/<id>` when a document closes.** `shutdownSession` in `packages/services/src/session/restapi.ts` (404 → `console.warn`, 410, else expect 204 — lines 65-75). Belongs to closing **one** document, not to the server's lifetime, which is why it is not folded into P1a. `KernelSession` carries our client id today; this needs the server's session id too. | Contract test on the lifecycle port; Maestro: close a notebook, assert the kernel is gone from `GET /api/sessions`. |
| **P1e** | **Replace `TwoPaneSceneStrategy` with the library.** U7 hand-wrote it (`TwoPaneSceneStrategy.kt` + `TwoPaneScene.kt`, 165 lines) and said in as many words: *"If that artifact goes stable, this is the thing to delete."* **It has.** `androidx.compose.material3.adaptive:adaptive-navigation3` **1.3.0 is the published release version** (maven-metadata.xml), dated 2026-08-12, and it ships `ListDetailSceneStrategy` / `rememberListDetailSceneStrategy` and `SupportingPaneSceneStrategy` / `rememberSupportingPaneSceneStrategy` — scene strategies, which is exactly the shape our hand-written one is. **The cost has not gone away and must be measured, not assumed:** its POM still pins `adaptive-navigation` **strictly** at `[1.3.0]`, taking the adaptive family off the Compose BOM, and it wants `androidx.navigation3:navigation3-ui:1.0.0` where the project is on `nav3Core = "1.0.1"`. And the thing that actually mattered in U7 was that **both panes stay separate `NavEntry`s** — verify that of `ListDetailSceneStrategy` before deleting anything. Scheduled here rather than later because P2h wants a third pane. | The U7 evidence re-run unchanged: `u7-two-panes` on the tablet **and** `u7-one-pane` at 320dp, plus `notebook-switch` (which is what a shared `ViewModelStore` would break). If the library keeps entries separate, delete both files; if not, keep them and record why here. |
| **P1f** | **Chore: 29 drivers call bare `adb` rather than `$ADB`.** `scripts/maestro-run.sh` already computes `$ADB` with a fallback and half the drivers use it. It surfaces as a flow that passes every step and then fails its own disk check with `adb: command not found`, which reads like a defect and is not. | `scripts/run-all-flows.sh` green from a shell **without** `platform-tools` on PATH. |

---

# P2 — notebook parity

The command surface is the specification. Semantics come from `packages/notebook/src/actions.tsx`,
read per command — three of them have already contradicted the obvious answer
(`docs/DECISIONS.md`), so none is assumed.

**Built today:** run, run-all, interrupt, restart, insert above/below, change type
(code ↔ markdown), merge, move up/down, duplicate, delete, clear-outputs, cut/copy/paste,
hide-cell-code, hide-cell-outputs.

| # | Sub-step | Commands, and what the reference settles |
|---|---|---|
| **P2a** | **Undo / redo of cell operations** | `notebook:undo-cell-action`, `notebook:redo-cell-action`. **Distinct from text undo** — this is structural (delete a cell, then get it back with its outputs and count). The reference's mechanism is a **Yjs undo manager** over the shared model — `(notebook.model.sharedModel as YNotebook).undoManager` (`actions.tsx:1704`) — so **one undoable step is one `sharedModel.transact { }` block**. That boundary is the whole design, and `deleteCells` shows why: it inserts the replacement cell *inside* the transaction "to make the deletion of a notebook's last cell undoable". We have no Yjs, so P2a owns its own stack — and `CellEdits.kt` already isolates every structural transform, which is the same boundary by another name. |
| **P2b** | **Split and directional merge** | `notebook:split-cell-at-cursor`, `merge-cell-above`, `merge-cell-below`. `actions.tsx:187` `splitCell`, read rather than assumed, and it contradicts the obvious answer twice. It splits at **every** selection, not just the caret. And the **original cell becomes the *last* fragment**, not the first — "the last piece will remain in the original cell to preserve kernel connection" — so **outputs stay with the last fragment** and every new cell is created with `outputs: undefined`. It also strips leading/trailing newlines from each piece, and drops stale `execution` metadata when the cell is mid-run. |
| **P2c** | **Multi-select** | `select-all`, `deselect-all`, `extend-marked-cells-{above,below,top,bottom}`. The prerequisite for every batch command after it. **Touch adaptation:** a selection mode entered from the cell header — long-press is `BasicTextField`'s text selection and is not available, which is the same finding that produced the `⋮` button in 4b. |
| **P2d** | **Ranged and compound runs** | `run-all-above`, `run-all-below`, `restart-run-all`, `restart-clear-output`, `restart-and-run-to-selected`. Pure orchestration over the existing gateway once P2c lands. `restart-clear-output` is why `KernelGateway.restart` deliberately does **not** clear outputs. |
| **P2e** | **Raw cells** | `change-cell-to-raw`. The third `CellKind`: round-trips through nbformat, renders as plain text, never executes. Small, and it unblocks nbconvert round-trips in P2j. |
| **P2f** | **Headings and heading collapse** | `change-cell-to-heading-1..6`, `insert-heading-{above,below}`, `toggle-heading-collapse`, `collapse-all-headings`, `expand-all-headings`. Heading collapse hides the cells **under** a heading — `setHeadingCollapse` (`actions.tsx:2348`) walks forward until it meets a heading of equal or higher level, so it is a document-structure feature, not a cell one, and it shares its model with P2h. **A collapsed heading then behaves as its whole section** everywhere else, which is the part that will bite if it is missed: `selectedCells` expands it so duplicating a collapsed heading duplicates the section; `addCells` inserts *after* its children rather than directly after it; and `deleteCells` takes the children with it. |
| **P2g** | **View toggles** | `toggle-all-cell-line-numbers`, `enable/disable/toggle-output-scrolling`, `toggle-autoclosing-brackets`, `toggle-virtual-scrollbar` (engine in `packages/notebook/src/windowing.ts`). Several already have engines and no command; this is the surface, plus the persistence P6c provides. |
| **P2h** | **Table of contents** | `toc:{show-panel,toggle-collapse,display-numbering,display-h1-numbering,display-outputs-numbering}`; the model is `packages/notebook/src/toc.ts`. **Adaptation:** a bottom sheet on a phone, a third pane at ≥ 840dp — a side panel is not reachable at 360dp. Depends on P1e. |
| **P2i** | **Find and replace** | `documentsearch:{start,startWithReplace,highlightNext,highlightPrevious,end}`; the notebook's provider is `packages/notebook/src/searchprovider.ts` and it defines the ordering, including whether collapsed cells participate. Read it rather than iterating cells. |
| **P2j** | **Trust and export** | `notebook:trust`, `notebook:export-to-format`. Export is `nbconvert`, already in the rootfs (`nbconvert==7.17.1`, with `mistune`, `bleach`, `pandocfilters`), and lands as a file in the workspace plus a share intent. **Trust is two halves and only one is the server's.** The client half is a *dialog* that warns and then sets `trusted: true` on each cell in the model (`actions.tsx` `trustNotebook`); the signing half is the server's — `ContentsManager.notary = sign.NotebookNotary` and `trust_notebook()` → `mark_cells(nb, True)` + `check_and_sign(nb, path)` (`services/contents/manager.py:116-120,694-706`, over `nbformat/sign.py`). **`check_and_sign` runs on save through the Contents API, which this app bypasses** by writing files directly — so a notebook we write is unsigned and would open untrusted. Decide that before building the dialog: route notebook *saves* through the Contents API, sign in the Space via `nbformat.sign`, or state that trust is inert here. Do **not** re-derive HMAC signing in Kotlin. |
| **P2k** | **Execution history** | `access-previous-history-entry`, `access-next-history-entry`. Needs `history_request` on the shell channel — a protocol message the gateway does not send yet, so it is a `KernelGateway` addition, not just UI. |

**Deliberately out of this phase:** `create-output-view`, `set-side-by-side-ratio`, the windowing
engine behind `toggle-virtual-scrollbar`, and the full `export-to-format` matrix. Each is either a
desktop-window idea with no phone reading, or work that belongs after the Space system decides
where files go.

---

# P3 — Spaces

The product's headline concept and entirely unbuilt. A **Space** is a project's environment
(the SageMaker Studio term — "environment" would collide with the Python environments inside one).
Winlator is the shape; the exec constraint in `docs/DECISIONS.md` is ours.

## What Winlator settles

`container/ContainerManager.java`, `container/Container.java`, `xenvironment/RootFS.java`:

- Containers are **directories enumerated off disk** (`loadContainers`), each with its own JSON
  config; `maxContainerId` is derived from the scan rather than stored, so nothing can drift.
- `Container` is a flat config object with defaults as constants **plus an `extraData` JSONObject**
  for anything not modelled — the same unknown-field retention as our `PreservedJson`.
- create / duplicate / remove are async with a callback; `activateContainer` swaps a symlink.
- Install is stamped by version — `RootFS.java:70-76`, `.winlator/.rfs_version` — and re-running
  the installer is free. `BaseSpaceInstaller` already copies this with `.jupydroid/rootfs-version`.

## What is ours, and why

**One immutable base rootfs, shared.** `files/spaces/base/` stays as it is (268 MiB unpacked, one
copy). A Space is `files/spaces/<id>/` holding `config.json`, `pkgs/` and its documents. N Spaces
cost N × packages, not N × 268 MiB.

**Isolation without a venv.** `PYTHONUSERBASE=<space>/pkgs`. Verified in the shipped stdlib —
`site.py:285-289`, `_getuserbase()` reads the env var, and the `USER_SITE` derived from it lands
on `sys.path` (`site.py:341-350`). A venv is not an option: it keys off `pyvenv.cfg` beside
`sys.executable`, which for us is a per-install path inside `nativeLibraryDir`.

**pip, and only pip.** pip is already in the rootfs and runs *inside* the interpreter, so it costs
no new executable. `pip install --user --only-binary=:all:` — the binary-only flag is a **gate,
not an optimisation**: a package with no musllinux wheel must fail loudly rather than start
compiling on a phone, which is the same gate `tools/rootfs/Dockerfile` already applies.

- **conda is impossible.** conda-forge publishes no musl builds, and glibc dies under Android's
  seccomp (`docs/DECISIONS.md`, `evidence/step8.3/`). This corrects the earlier plan, which
  offered it as a choice.
- **uv is deferred.** It is a native binary, so at targetSdk 36 it would have to ship in
  `jniLibs` per ABI. Worth revisiting only if pip's resolver proves too slow to use; costed at
  P3g and not scheduled.

| # | Sub-step | Gate |
|---|---|---|
| **P3a** | `SpaceConfig` (name, created, python version, requested packages, `extra: JsonObject`) + `SpaceRepository` port + `DiskSpaceRepository` scanning `files/spaces/`. Ids derived from the scan, Winlator-style. | Port contract test + JVM repository tests against a temp tree. |
| **P3b** | `SpaceRuntime` takes a `Space`: per-Space `PYTHONUSERBASE`, `HOME`, `JUPYTER_RUNTIME_DIR`. | Regression test that two Spaces produce disjoint environments and disjoint runtime dirs. |
| **P3c** | Space list, create, rename, delete. Async with progress, as Winlator's are; delete asks first, like the explorer's. | Maestro on the tablet: create → it appears → delete → it is gone, with the directory checked over `run-as`. |
| **P3d** | Packages: list installed, add, remove, over `pip --user`. Failures surface pip's own message **including the offending package name and the wheel tag it wanted**. | Tablet: install a pure-Python package into Space A, import it there, and show `ImportError` in Space B. That negative half is the point of the phase. |
| **P3e** | **One server per Space, or one server with per-Space kernelspecs — decided by measurement.** A second `jupyter_server` costs about what the first does: RSS 77,156 KB ≈ 75 MiB, measured (`evidence/step18-lifecycle/README.md:12`). Kernelspecs are JSON we already write (`SpaceKernelSpec`), so one server advertising one kernelspec per Space is the cheaper candidate and is tried first. | RSS measured both ways on the tablet, into `evidence/p3-spaces/`. Whichever wins, the loser's numbers stay in the record. |
| **P3f** | Switching: the active Space roots the explorer and binds new notebooks. The binding persists in the notebook's own `metadata.kernelspec.name` — where Jupyter keeps it — not in an app-side table. | Maestro: a notebook created in Space A reopens against Space A after switching away and back. |
| **P3g** | *(costed, not scheduled)* uv in `jniLibs`, patchelf'd like `libpython3.so`, selectable per Space. | — |

---

# P4 — Terminal

Everything the server side needs is **already shipped**: `jupyter_server_terminals==0.5.4`,
`terminado==0.18.1`, `ptyprocess==0.7.0`, and `ServerApp.terminals_enabled` defaults to **True**
(`serverapp.py:1997-1999`).

## The blocker, verified in the tarball rather than assumed

Alpine's `bin/sh`, `bin/ash` and every other applet is an **absolute** symlink `-> /bin/busybox`
(`tar -tvzf $T | grep '^l'`). On device that resolves against Android's root, not the rootfs, so
**every one of them is dangling** — and `jupyter_server_terminals/app.py` picks its shell with
`which("sh")`. Even repaired, a rootfs file cannot be `execve`'d at targetSdk 36.

**The fix is the pattern the loader already proves.** Stage busybox as
`jniLibs/<abi>/libbusybox.so`, patchelf'd `--set-interpreter` to the rootfs loader path exactly as
`libpython3.so` is in `tools/rootfs/stage-app-artifacts.sh`, and have `BaseSpaceInstaller` relink
`bin/busybox` at it on every launch. Applet dispatch is by `argv[0]`, which the caller sets
independently of the path, so `bin/sh` dispatches the `sh` applet while the SELinux check lands
on the symlink **target** in `nativeLibraryDir`.

## The protocol

`packages/services/src/terminal/`:

- REST `api/terminals` (`restapi.ts`, `TERMINAL_SERVICE_URL`) — `POST {name, cwd}` → `{name}`;
  `GET` lists; `DELETE` warns on 404 and otherwise expects 204.
- WebSocket `/terminals/websocket/<name>` — the route is registered in
  `jupyter_server_terminals/app.py` as `r"/terminals/websocket/(\w+)"`. Messages are JSON arrays
  `[type, ...content]` (`default.ts`: `const msg = [message.type, ...message.content]`) with
  `type ∈ {'stdout','stdin','set_size','disconnect'}` (`terminal.ts:124`).
- `ServerApp.terminado_settings` (`serverapp.py:1492`) carries `shell_command`, which
  `app.py` prefers over `which`; `app.py` also appends `-l` when stdout is not a tty, which on
  device it never is.

| # | Sub-step | Gate |
|---|---|---|
| **P4a** | Stage busybox into `jniLibs`; relink `bin/busybox` on install, on every launch (`nativeLibraryDir` moves). | `adb shell run-as com.jupydroid.app files/spaces/base/bin/sh -c 'echo ok'` **and** a `ls -l` of the link, both in `evidence/p4-terminal/`. |
| **P4b** | **Prove the PTY before any UI.** terminado reaches a shell through `ptyprocess`, i.e. `/dev/ptmx` and `pty.fork()`. Whether that is permitted under Android's app seccomp filter is **unverified**, and if it fails the whole phase changes shape. One-shot device probe through `PythonRunner`. | A device capture showing a `sh` child and its pty, or the failure and its errno. Either outcome closes the sub-step. |
| **P4c** | `TerminalGateway` port + `OkHttpTerminalGateway` (REST + WS), contract-tested against a scripted socket the way `KernelGateway` is. Pin the shell with `--ServerApp.terminado_settings` rather than trusting `which`. | Contract tests; `GET /api/terminals` non-empty on the tablet. |
| **P4d** | **A terminal emulator in Compose.** The real cost of this phase: a screen buffer, scrollback and selection, and enough ANSI/VT to run `ash`, `vi` and `top`. Nothing existing is reusable — `RenderedOutput.withoutAnsiColours` (`RenderedOutput.kt:96-102`) is a regex **stripper** for SGR escapes in tracebacks, not a parser, and a terminal needs a grid with cursor addressing. **IntelliJ does not answer this either:** its emulator is JediTerm, an external library (`plugins/terminal/BUILD.bazel:55-56`), so `plugins/terminal` shows how a terminal is *integrated* (`ObservableJediTerminal.kt`, `JediTerminalListener.kt`, search and hyperlink filters) and not how the buffer is built. The buffer is written against the VT spec, and that gap is stated here so nobody looks for a reference that is not there. | Instrumented tests over the buffer (cursor addressing, erase, scroll region, wrap) plus a tablet screenshot of `vi`. |
| **P4e** | Mobile input. **Two things already exist and neither should be reinvented.** The app has `SymbolRow` (U5d) — a scrolling key row with `imePadding` + `navigationBarsPadding`, written for exactly this problem in the cell editor — so this generalises it rather than adding a second one. And Termux answers what a *terminal* row needs, which the editor's does not: `ExtraKeysView` / `ExtraKeysInfo` / `ExtraKeysConstants` (`termux-shared/src/main/java/com/termux/shared/termux/extrakeys/`), including modifier keys that **latch** (Ctrl/Alt held for the next keystroke) rather than emitting a character. Read that before choosing the key set. Plus gesture scrollback, font size, and `set_size` on every resize including when the IME opens. | Maestro on tablet and phone: type a command, read its output, send Ctrl-C to a running one, resize with the keyboard and show the shell reflowed. |

**This supersedes Step 11's Console REPL for shell work but does not replace the Console** (P6b),
which shares the notebook's kernel namespace — something a shell cannot do.

---

# P5 — LSP

`jupyter-lsp>=2.0.0` is a **core JupyterLab dependency** (`pyproject.toml:45`), so this is parity,
not an extra. `jedi==0.20.0` and `parso` are already in the rootfs; `python-lsp-server` and
`jedi-language-server` both publish pure-Python `py3-none-any` wheels, so they install into musl
exactly as `jupyter_server` did. `pyright` is out — it needs Node, which the Space does not have.

**Two routes, and this plan takes the second:**

1. The `jupyter-lsp` server extension — websocket at `<wsBase>/lsp/ws/<languageServerId>`, status
   at `<baseUrl>/lsp/status` (`packages/lsp/src/connection_manager.ts:661-662`,
   `packages/lsp/src/manager.ts:71`). Parity-exact, one more hop, one more moving part.
2. **`pylsp` as a direct stdio subprocess** off the same `nativeLibraryDir` interpreter the kernel
   already runs from. No new executable, no websocket, and the wire format — LSP over
   `Content-Length` framing — is identical either way.

Route 2 is chosen for the smaller surface; recorded as a departure because the reference uses
route 1, and the port is shaped so route 1 stays a swap behind it.

This **supersedes** 5.5's `complete_request` completion rather than duplicating it, and adds
diagnostics, hover, signature help, document symbols and folding over one protocol. IntelliJ's
`platform/lsp` is the reference for the client's shape: capability negotiation, document sync,
cancellation.

| # | Sub-step |
|---|---|
| **P5a** | `python-lsp-server` into `tools/rootfs/requirements.lock`; the image build's existing import gate proves the wheel is `py3-none-any` and resolves under `--only-binary=:all:`. |
| **P5b** | `LanguageServerPort` + a process/stdio adapter with `Content-Length` framing, contract-tested against a scripted server. Lifecycle: one server per Space, started lazily, stopped with the Space (P1's rules apply). |
| **P5c** | Completion swapped over behind the existing popup. Same UI, better answers; the kernel-backed path stays as the fallback when no server is running. |
| **P5d** | Diagnostics as cell gutter marks. A cell is a fragment, not a module — the virtual-document mapping in `packages/lsp/src/virtual/document.ts` is what makes `NameError`-shaped false positives avoidable, and it is read rather than approximated. |
| **P5e** | Document symbols, feeding P2h's ToC for `.py` files and folding for both. |

---

# P6 — Debugger, Console, Settings, Launcher

| # | Sub-step | What settles it |
|---|---|---|
| **P6a** | **Debugger.** The kernelspec already declares `"metadata": {"debugger": true}` (`SpaceKernelSpec.kt`) and `debugpy==1.8.21` is in the lock file. Transport is `debug_request` / `debug_reply` on the **control** channel (`packages/services/src/kernel/messages.ts:1244`) carrying DAP; `packages/debugger/src/service.ts` is the protocol layer. The control channel is the one that has never stalled. **Phone scope:** breakpoints in a cell, step / continue, variables, call stack. Not the full DAP surface. |
| **P6b** | **Console.** `packages/console-extension` — 21 commands. A REPL sharing the open notebook's kernel namespace; Enter runs, Shift+Enter newlines; `console:linebreak`, `console:inject`, `console:interaction-mode`, input history, clear transcript. **This is the sub-step that makes the `openNotebook` back-stack risk real** (see Open risks). |
| **P6c** | **Settings.** `packages/settingregistry` is the model. Theme, accent, code font size, wrap, completion on/off, symbol row, autosave interval — and the persistence P2g's toggles need. |
| **P6d** | **Launcher and onboarding.** `packages/launcher` — the surface that offers notebook / console / terminal / file per Space. Also the second top-level destination a navigation rail has been waiting for since U7. |

---

## Open risks

Measured, not speculative. Anything closed moves to `docs/DECISIONS.md`.

- **A refused syscall that a package genuinely needs would still be fatal.** `mbind` was tolerable;
  nothing guarantees the next one is. Bounded and observable, because refusals are reported.
- **`PythonTypingRules` reads the line as text, not as tokens**, so a block opener with a trailing
  comment (`if x:  # note`) gets no extra level. Fixing it means asking the lexer where the comment
  starts — a dependency the transformation does not otherwise have. Deliberately not paid for up
  front. Replacing a selection is likewise left alone rather than wrapped in the typed bracket:
  wrapping is a separate feature and half-doing it leaves an unmatched closer.
- **`dirty` is tracked and rendered nowhere.** `EditorUiState.dirty` is set by every edit and
  cleared by `persist()`, and no composable reads it. It is arguably right that it stays invisible —
  saving is automatic and debounced, so a "•" that appears for 500 ms after every keystroke is
  noise. **The gap is the claim, not the behaviour:** decide in P6c whether anything renders it, or
  drop it from the deliverable.
- **`openNotebook` guards only the top of the back stack.** `lastOrNull() == Editor(id)` is enough
  while a notebook can only be opened from a file surface. A screen that opens a notebook from
  *inside* another one — a link between notebooks, a Console attached to one — could push a
  duplicate `contentKey` deeper, and two entries sharing one share a `ViewModelStore` and a
  `SaveableStateHolder` slot. JupyterLab's `findWidget` searches every context for the path rather
  than only the active one (`packages/docmanager/src/manager.ts:390`). **P6b is the sub-step that
  makes this real.**
- **Packaging is undecided.** Measured, not quoted from an older note: the debug APK is **149 MiB**
  (`app/build/outputs/apk/debug/app-debug.apk`), of which the rootfs asset is **75 MiB**
  compressed and **268 MiB** unpacked — so one Space costs roughly 340 MiB of device storage
  once installed and extracted. P3's shared-base design keeps additional Spaces
  cheap, which is what makes deferring safe. Revisit — asset pack versus first-run download —
  when the app is feature-complete, not before.
- **Three of the five architecture rules are true but unguarded.** `ArchitectureGuardTest`
  enforces the domain layer's purity and nothing else. `ui ↛ data`, `data ↛ ui` and "only
  `AppContainer` constructs implementations" hold today — checked by grep, zero hits each — with
  nothing keeping them true. Extending the guard would go green immediately, which is the point
  of a regression gate. See `docs/architecture.md` §5.
- **`scripts/gradlew-jbr` hardcodes this machine's JBR path, and there is no CI.**
  Dev-environment debt: it slows a new machine down, it cannot corrupt a build.
- **`--ServerApp.log_level=DEBUG` is on.** It costs nothing on screen (`redirectOutput` sends it to
  `run/jupyter_server.log`, truncated at every start). **Drop it to INFO when the app is
  feature-complete**, not before — and note P0's finding that the server cannot see a dead shell
  channel, so this log is not the oracle it was once hoped to be.

---

## Beyond P6

Not scheduled, listed so nobody re-derives them: the remaining file viewers JupyterLab ships
(`csvviewer`, `imageviewer`, `htmlviewer`, `markdownviewer`, `json-extension`, `pdf-extension`,
`vega5`, `mermaid`, `mathjax`); `metadataform` / `celltags`; the running-sessions panel;
`workspaces`; `logconsole`; and multi-kernel support beyond Python once a second kernel is worth
installing into a Space.
