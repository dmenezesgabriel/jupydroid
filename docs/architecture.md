# JupyterDroid — Architecture

An [arc42](https://arc42.org/overview) description of the system. Sections 5, 6 and 8 are large
enough to live in their own files under `architecture/`.

This document and everything under [`adr/`](adr/) are **durable**: they describe decisions and
non-trivial knowledge, and they hold true with `reference/` absent from disk — which, in every clone,
it is. Ephemeral work lives in [`backlog.md`](backlog.md) and `prd/` and is deleted when it ships; see
[ADR-0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md).

| Section | Where |
|---|---|
| 1–4, 7, 9–11 | this file |
| 5 Building block view | [`architecture/building-blocks.md`](architecture/building-blocks.md) |
| 6 Runtime view | [`architecture/runtime-views.md`](architecture/runtime-views.md) |
| 8 Crosscutting concepts, 12 Glossary | [`architecture/cross-cutting.md`](architecture/cross-cutting.md) |

---

## 1. Introduction and goals

JupyterDroid is an **offline JupyterLab client for Android that runs the real stack on the device**:
stock `jupyter_server` and `ipykernel` in a bundled musl (Alpine) rootfs on loopback, driven by a
Compose-native UI. No remote server, ever.

A user opens a notebook, taps Run, and a real CPython kernel executes the cell on the tablet. The
same filesystem is the kernel's `HOME`, the server's `--ServerApp.root_dir` and the app's workspace,
so nothing has to be synchronised.

**Parity is not a matter of taste.** JupyterLab's `packages/notebook-extension/src/index.ts` declares
99 command ids, 96 of them `notebook:*`; `filebrowser-extension` 36, `console-extension` 21,
`terminal-extension` 11, `documentsearch-extension` 5, `toc-extension` 5. Those lists are the
specification. A behaviour that cannot be reached on a phone is **adapted, never invented**, and the
adaptation is written down beside what it departs from.

### Quality goals

| Goal | What it means here |
|---|---|
| **Fidelity** | The real `jupyter_server` and the real `ipykernel`, not a re-implementation. What the notebook shows is what the kernel actually sent |
| **Offline** | The whole product runs on the device. No account, no service, no network dependency for anything but installing a package |
| **Parity** | JupyterLab's behaviour and VS Code's presentation, adapted to touch — with every adaptation stated |
| **Mobile-native** | A Compose UI that is a phone and tablet app, not a webview of a desktop one. Every tap target reserves 48dp |
| **Openness of the format** | nbformat is an open format, so a writer that rebuilds a notebook is a shredder. Unknown fields round-trip verbatim |

### Out of scope

Listed so nobody re-derives them, with no commitment to build them: the remaining file viewers
JupyterLab ships (`csvviewer`, `imageviewer`, `htmlviewer`, `markdownviewer`, `json-extension`,
`pdf-extension`, `vega5`, `mermaid`, `mathjax`); `metadataform` / `celltags`; `workspaces`;
`logconsole`; and multi-kernel support beyond Python, until a second kernel is worth installing into
a Space.

A remote-server mode is not deferred — it is refused. It would make every constraint in
[§8.1](architecture/cross-cutting.md) optional and the product a different one.

## 2. Constraints

### Technical

- **Nothing under `filesDir` can be executed.** Everything executable ships in `jniLibs/<abi>/` and
  is reached through a symlink. This is the constraint the whole runtime is shaped by —
  [§8.1](architecture/cross-cutting.md). It forbids proot, chroot, `LD_PRELOAD` exec interceptors, a
  targetSdk downgrade, and the NDK.
- **arm64 only, at runtime.** Only arm64 ships a rootfs; on x86_64 the app falls back to
  `FakeKernelGateway`, which echoes a cell's source back as its output. Anything that runs a cell is
  verified on the arm64 tablet.
- **Kotlin + Jetpack Compose**, `com.jupydroid.app`, AGP 9, compileSdk 37, minSdk 32, targetSdk 36.
- **Single `:app` module**, manual DI through `di/AppContainer`. No DI framework.
- **Domain is pure Kotlin**, zero `android.*` — enforced by `ArchitectureGuardTest`.
- **Nothing is compiled from source at build time**; the rootfs and the four ELFs are prebuilt
  artifacts, built by `tools/rootfs/build.sh` and `tools/rootfs/stage-app-artifacts.sh`.
- **No code is vendored from any reference.** What may be transcribed and what is clean-room only is
  decided per licence — [§8.4](architecture/cross-cutting.md).
- **Strict test-first development** with a per-step evidence gate — `AGENTS.md`.

### Organisational

- Study material lives in `reference/`, which is **gitignored**. No durable document and no code
  comment may depend on it ([ADR-0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).
- Every step closes with Maestro flows on a real device, with screenshots and logs committed under
  `evidence/<step>/`.

### Tooling and gates

ktlint · detekt · Android Lint · pre-commit · JUnit · Compose UI test · Maestro · plus the project's
own Python gates in `scripts/` (file length, Maestro output assertions, device secrets, monospace
home, busybox seccomp, shebang exec, palette consumers, driver isolation, doc citations).

## 3. Context and scope

```
┌────────────────────────────── Android device ──────────────────────────────┐
│                                                                            │
│  ┌──────────────────────┐        ┌──────────────────────────────────────┐  │
│  │ JupyterDroid (Compose)│       │ Space — musl rootfs in filesDir      │  │
│  │  Explorer · Editor    │       │                                      │  │
│  │  FileEditor · Terminal│◄─────►│  jupyter_server  (127.0.0.1, token)  │  │
│  └──────────┬───────────┘  HTTP  │        │                             │  │
│             │             + WS   │        ├─ ipykernel   (subprocess)   │  │
│             │                    │        ├─ terminado → busybox ash    │  │
│             │                    │        └─ pylsp      (stdio)         │  │
│             │                    └──────────────────┬───────────────────┘  │
│             │   one shared filesystem               │                      │
│             └───────────────────────────────────────┘                      │
│                  notebooks · data · outputs                                │
└────────────────────────────────────────────────────────────────────────────┘
                                     │
                        only for installing packages
                                     ▼
                                  PyPI
```

| Partner | Interface | Why |
|---|---|---|
| `jupyter_server` | HTTP + WebSocket on loopback, token-authenticated | sessions, kernels, nbconvert, terminado |
| `ipykernel` | the Jupyter wire protocol over the session's WebSocket | executes cells; DAP rides the control channel |
| `pylsp` | LSP over stdio | diagnostics, completion, hover, definition, format, rename |
| Android platform | `FileProvider` + `ACTION_SEND`, `ProcessLifecycleOwner` | sharing a document out; knowing the app was left |
| PyPI | HTTPS, through the rootfs's own CA bundle and a `getaddrinfo` shim | `%pip install` |

## 4. Solution strategy

| Problem | Approach | Where |
|---|---|---|
| Android forbids executing files in `filesDir` | ship executables as `jniLibs` ELFs; relink four rootfs paths into `nativeLibraryDir` on every launch | [§8.1](architecture/cross-cutting.md) |
| A `#!` script has no ELF to point a symlink at | a static trampoline reads the shebang and execs the interpreter | [§8.1](architecture/cross-cutting.md) |
| Android's seccomp filter kills a musl process on a refused syscall | handle SIGSYS and return; the kernel has already made the call return `-ENOSYS` | [§8.2](architecture/cross-cutting.md) |
| Re-implementing Jupyter would lose fidelity | run the **real** server and kernel, and speak their protocol | [§6.3](architecture/runtime-views.md) |
| A device cannot run a Space at all (x86_64), and tests need no process | every port has a `Fake…`, which is also what the container defaults to | [§5.3](architecture/building-blocks.md) |
| Two clients would each start the server, and starting it stops it first | one `BaseSpaceServer`, and `PerRunningServer` per client | [§5.3](architecture/building-blocks.md) |
| A rotation rebuilt the whole object graph, and with it a second server | the container is the **process's**, not the activity's | [§6.2](architecture/runtime-views.md) |
| A phone has one pane and a tablet two | `ListDetailSceneStrategy` at ≥ 840dp; panes pair on metadata, never on position | [§6.1](architecture/runtime-views.md) |

## 7. Deployment view

One APK. Three kinds of artifact reach the device inside it.

| Artifact | Where in the APK | Where at runtime |
|---|---|---|
| Compose app | `classes.dex` | — |
| `rootfs-<abi>.tzst` | `assets/spaces/base/` | unpacked once into `filesDir/spaces/base/` by `BaseSpaceInstaller` |
| `libloader.so`, `libpython3.so`, `libbusybox.so`, `libshebang.so` | `jniLibs/<abi>/` | extracted by the platform to `/data/app/…/lib/<abi>/`, the only place execution is permitted |

The rootfs asset and the ELFs are **build artifacts, not committed files**:

```sh
tools/rootfs/build.sh arm64-v8a
tools/rootfs/stage-app-artifacts.sh arm64-v8a
./gradlew :app:assembleDebug
```

`nativeLibraryDir` moves on every app update, so `BaseSpaceInstaller` repairs the four rootfs
symlinks into it on **every launch**, not only on install.

x86_64 ships three of the four ELFs and no rootfs, so the app installs and the UI runs against fakes.

## 9. Architectural decisions

One decision per file in [`adr/`](adr/), MADR-named `NNNN-title-with-dashes.md`. This section is the
index and nothing more; the decision itself is always in the ADR.

```sh
grep -rn "ADR-0001" docs/ app/src/ tools/     # everything about one decision
grep -rln "^Status: superseded" docs/adr/     # decisions that have been replaced
```

| ADR | Title | Status |
|---|---|---|
| [0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md) | Separate documents by lifetime, not by topic | accepted |
| [0002](adr/0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md) | Ship every executable in `jniLibs/` and reach it through a symlink | accepted |
| [0003](adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md) | Use musl, not glibc, and ship a runtime for arm64 only | accepted |
| [0004](adr/0004-handle-sigsys-and-return.md) | Handle SIGSYS and return, rather than avoiding refused syscalls | accepted |
| [0005](adr/0005-interpose-getaddrinfo-rather-than-redirect-resolv-conf.md) | Interpose `getaddrinfo()` rather than trying to redirect `/etc/resolv.conf` | accepted |
| [0006](adr/0006-build-busybox-here-with-suid-compiled-out.md) | Build busybox here, with `CONFIG_FEATURE_SUID` compiled out | accepted |
| [0007](adr/0007-name-the-spaces-shell-once-in-shell.md) | Name the Space's shell once, in `SHELL` | accepted |
| [0008](adr/0008-work-around-two-android-hazards-inside-cpython.md) | Work around two Android hazards inside CPython, and keep them named | accepted |
| [0009](adr/0009-keep-kernel-logging-out-of-cell-output.md) | Keep kernel logging out of cell output, and assert it | accepted |
| [0010](adr/0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md) | A Space is a directory, and its id comes from the scan | accepted |
| [0011](adr/0011-install-packages-into-the-spaces-user-site-via-environment.md) | Install packages into the Space's own user site, steered by environment | accepted |
| [0012](adr/0012-ship-the-rootfs-as-zstd.md) | Ship the rootfs as zstd, because the decoder was the floor | accepted |
| [0013](adr/0013-ship-bytecode-invalidated-by-checked-hash.md) | Ship bytecode in the image, invalidated by `checked-hash` | accepted |
| [0014](adr/0014-uninstall-rfc3987-syntax-from-the-image.md) | Uninstall `rfc3987-syntax` from the image, rather than removing it from the lock | accepted |
| [0015](adr/0015-rewrite-history-once-to-take-the-device-out-of-it.md) | Rewrite history once, before the first push, to take the device out of it | accepted |
| [0016](adr/0016-name-every-gate-task-explicitly.md) | Name every gate task explicitly; never rely on an aggregate | accepted |
| [0017](adr/0017-prune-stale-evidence-frames-from-a-per-flow-manifest.md) | Prune stale evidence frames from a per-flow manifest | accepted |
| [0018](adr/0018-transcribe-under-permissive-licences-clean-room-otherwise.md) | Transcribe under permissive licences; clean-room everything else | accepted |
| [0019](adr/0019-an-activity-counter-lives-where-its-lifetime-matches.md) | An activity counter lives wherever its lifetime matches the question | accepted |
| [0020](adr/0020-the-terminals-colour-ramp-lives-in-terminal.md) | The terminal's colour ramp lives in `terminal/`, beside the code it serves | accepted |
| [0021](adr/0021-nbformat-is-an-open-format-so-unknown-fields-round-trip.md) | nbformat is an open format, so unknown fields round-trip verbatim | accepted |
| [0022](adr/0022-kernels-start-through-sessions-and-one-tree-is-the-root.md) | Kernels start through sessions, and one tree is everybody's root | accepted |
| [0023](adr/0023-an-unanswered-greeting-replaces-the-kernel-once.md) | An unanswered greeting replaces the kernel, and is never asked twice | accepted |
| [0024](adr/0024-the-app-stops-its-own-server-so-the-app-repairs-what-that-breaks.md) | The app stops its own server, so the app repairs what that breaks | accepted |
| [0025](adr/0025-a-kernel-is-ended-by-a-command-never-by-closing-the-document.md) | A kernel is ended by a command, never by closing the document | accepted |
| [0026](adr/0026-a-terminal-is-ready-when-setup-arrives.md) | A terminal is ready when `setup` arrives, and its socket belongs to the attach | accepted |
| [0027](adr/0027-cell-semantics-come-from-notebookactions.md) | Cell semantics come from `NotebookActions`, not from what looks reasonable | accepted |
| [0028](adr/0028-there-are-two-undos-and-a-cell-operation-never-restores-text.md) | There are two undos, and a cell operation never restores text | accepted |
| [0029](adr/0029-a-selection-is-a-contiguous-run.md) | A selection is a contiguous run, and it is the operand of almost every cell command | accepted |
| [0030](adr/0030-a-run-has-a-range-and-the-command-owns-the-caret.md) | A run has a range, and the command rather than the range owns the caret | accepted |
| [0031](adr/0031-splitting-reads-the-editor-so-a-cell-owns-one-text-state.md) | Splitting reads the editor, so a cell owns one text state | accepted |
| [0032](adr/0032-a-raw-cell-is-an-editor-and-nothing-else.md) | A raw cell is an editor and nothing else, and running one does nothing | accepted |
| [0033](adr/0033-a-heading-owns-a-section-derived-not-stored.md) | A heading owns a section, and what a collapsed one hides is derived | accepted |
| [0034](adr/0034-adopting-a-rewrite-belongs-to-the-cell-reporting-to-the-field.md) | Adopting a rewritten source belongs to the cell; reporting an edit to the field | accepted |
| [0035](adr/0035-unrendered-is-not-the-same-as-asked-for.md) | Unrendered is not the same as asked for, and only a tap asks | accepted |
| [0036](adr/0036-three-view-toggles-and-two-defaults-kept-against-the-reference.md) | Three view toggles, and two defaults kept against the reference on purpose | accepted |
| [0037](adr/0037-the-table-of-contents-is-two-presentations-of-one-derived-tree.md) | The table of contents is two presentations of one derived tree | accepted |
| [0038](adr/0038-the-lexer-is-generated-never-hand-written.md) | The Python lexer is generated, never hand-written | accepted |
| [0039](adr/0039-a-compose-native-cell-editor-not-a-webview.md) | A Compose-native cell editor, not a WebView | accepted |
| [0040](adr/0040-the-presentation-reference-is-vs-code-behaviour-stays-jupyterlab.md) | The presentation reference is VS Code; the behaviour reference stays JupyterLab | accepted |
| [0041](adr/0041-parity-with-the-resolved-themes-outranks-the-contrast-floor.md) | Parity with the resolved VS Code themes outranks the contrast floor | accepted |
| [0042](adr/0042-a-search-match-is-a-wash-and-the-palette-states-its-colours.md) | A search match is a wash, a link is told apart by its underline, and the palette states its colours | accepted |
| [0043](adr/0043-every-search-is-a-regex-and-typing-finds-without-moving.md) | Every search is a regex, and typing finds without moving the notebook | accepted |
| [0044](adr/0044-reaching-a-cell-reveals-it-in-two-strengths.md) | Reaching a cell reveals it, in two strengths, and the search bar takes the notebook's | accepted |
| [0045](adr/0045-a-shell-is-reached-through-a-path-named-sh.md) | A shell is reached through a path named `sh`, and all 304 applets with it | accepted |
| [0046](adr/0046-a-transcription-is-diffed-against-its-source-not-reviewed.md) | A transcription of an algorithm is diffed against its source, not reviewed | accepted |
| [0047](adr/0047-two-panes-start-at-expanded-and-the-scene-strategy-keeps-every-entry.md) | Two panes start at expanded, and the scene strategy keeps every entry an entry | accepted |
| [0048](adr/0048-touch-rules-measured-on-device.md) | Touch rules, and the four that cost something to learn | accepted |
| [0049](adr/0049-a-gap-has-a-name-and-the-type-scale-is-taken-whole.md) | A gap has a name, and Material's type scale is taken whole where its colours are not | accepted |
| [0050](adr/0050-reads-hit-the-repository-server-side-algorithms-do-not.md) | Reads may hit the repository; server-side algorithms may not be re-implemented | accepted |
| [0051](adr/0051-an-export-is-named-by-the-server-and-the-menu-offers-what-was-measured.md) | An export is named by the server, and the menu offers what was measured | accepted |
| [0052](adr/0052-kernel-history-is-the-kernels-and-a-walk-belongs-to-its-cell.md) | Kernel history is the kernel's, and a walk belongs to the cell it started in | accepted |
| [0053](adr/0053-a-cell-at-rest-carries-no-mark-and-the-editor-border-is-the-boundary.md) | A cell at rest carries no mark, and the editor's border is the cell's boundary | accepted |
| [0054](adr/0054-the-shell-has-one-side-area-and-the-activity-bar-sits-beside-it.md) | The shell has one side area, and the activity bar sits beside the whole thing | accepted |
| [0055](adr/0055-the-bottom-bar-is-drawn-wherever-the-shell-draws-one-pane.md) | The bottom bar is drawn wherever the shell draws one pane | accepted |
| [0056](adr/0056-the-notebooks-action-row-is-a-reactive-toolbar.md) | The notebook's action row is a `ReactiveToolbar`, and the overflow is its popup opener | accepted |
| [0057](adr/0057-the-cell-keeps-its-header-strip-and-the-run-gutter-is-declined.md) | The cell keeps its header strip, and VS Code's run gutter is declined | accepted |
| [0058](adr/0058-the-breadcrumb-is-ancestors-only-and-the-chrome-ramp-is-vs-codes.md) | The breadcrumb is ancestors only, and the chrome ramp is VS Code's | accepted |
| [0059](adr/0059-many-documents-live-in-the-stack-and-a-tab-is-an-ordering.md) | Many documents live in the back stack; a tab is an ordering, not a screen | accepted |
| [0060](adr/0060-workspace-search-transcribes-the-glob-language-and-declines-three-files.md) | Workspace search transcribes the glob language and declines three kinds of file | accepted |
| [0061](adr/0061-replace-across-files-goes-through-the-open-documents-model.md) | Replace across files goes through the open document's model, not the filesystem | accepted |
| [0062](adr/0062-the-activity-bars-order-takes-both-references.md) | The activity bar's order takes both references, and neither could state it alone | accepted |
| [0063](adr/0063-kernel-choice-is-a-dialog-and-a-patch-on-the-session.md) | Kernel choice is `selectKernel`'s dialog and a `PATCH` on the session | accepted |
| [0064](adr/0064-a-files-name-is-its-identity.md) | A file's name is its identity | accepted |
| [0065](adr/0065-the-kernel-indicator-shows-status-first-and-solid-means-active.md) | The kernel indicator keeps its status when the name goes, and solid means active | accepted |
| [0066](adr/0066-a-retired-attribution-is-deleted-not-rewritten.md) | Delete a retired attribution; do not rewrite it as a commit SHA | accepted |
| [0067](adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md) | Store a parity verdict; produce every count beside it | accepted |

## 10. Quality requirements

| Quality | Scenario | How it is held |
|---|---|---|
| Fidelity | A cell's output renders as the kernel sent it — stream, traceback with ANSI, PNG, `text/html`, tables | `KernelOutputs` maps the wire shapes; instrumented tests render each |
| Format safety | A notebook written by another tool round-trips without loss | unknown fields ride as `PreservedJson`; a duplicated cell keeps them |
| Responsiveness | The server is not started until something needs it, and stops when the app is left | `BaseSpaceServer` is lazy; `IdleServerStopper` reads `ActiveCellRuns` |
| Layer purity | `domain` never gains an `android.*` import | `ArchitectureGuardTest`, per commit |
| Contrast | Every foreground/background pair meets its floor | `EditorPaletteTest` |
| Reachability | Every action is reachable at phone width, not only on a tablet | Maestro flows run narrow and wide |
| Documentation integrity | No code comment cites something that will be deleted | `scripts/check_doc_citations.py` |

## 11. Risks and technical debt

Measured, not speculative. A gap closes by being fixed and re-measured, not by being re-described.

### GAP-001 — Workspace search does not reach a notebook's outputs

`webviewResults` is the third field of `INotebookCellMatchNoModel` and only the other two are built.
Three things hold it shut, and only the first is a preference: JupyterLab's own `output` filter is off
by default while VS Code's is on but hidden unless a notebook is open; `SearchHit.outputAt` indexes
the **rendered** outputs (`renderedOutputsOf`), which drop `ClearOutput`, so a disk-side raw index
would name a different output from the one drawn; and the rule for *which* outputs have searchable
text lives in `ui/editor/`, which `data/` may not import. The prerequisite is one home for that rule,
and it carries a real design question with it — the mime ranking that makes `text/plain` lose to
`text/html`.

### GAP-002 — Workspace search matches line by line, so a pattern spanning lines finds nothing

The reference decides multiline from the pattern itself (`isMultilineRegexSource` onto
`IPatternInfo.isMultiline`). Ours is bounded on purpose: a result row *is* a line, so matching over
the whole file would count hits no row could draw, and `matchedLinesIn` says so. The cost is a real
parity gap — `def foo\n    return` is a search a user can reasonably type. Closing it means a result
row that spans lines, which is a presentation problem before it is a matching one.

### GAP-003 — A refused syscall that a package genuinely needs is still fatal

`mbind` was tolerable; nothing guarantees the next one is. Bounded and observable, because refusals
are reported — [§8.2](architecture/cross-cutting.md).

### GAP-004 — `PythonTypingRules` reads the line as text, not as tokens

A block opener with a trailing comment (`if x:  # note`) gets no extra indent level. Fixing it means
asking the lexer where the comment starts, a dependency the transformation does not otherwise have.
Replacing a selection is likewise left alone rather than wrapped in the typed bracket: wrapping is a
separate feature, and half-doing it leaves an unmatched closer.

### GAP-005 — There is no command-mode way to move the selection head

Focus is what selects a cell here (`EditorViewModel.cellFocusChanged`), so aiming a head-relative
command — `run-all-above`, `run-all-below`, `restart-and-run-to-selected`, and every heading command —
means tapping into a cell, taking the keyboard, and dismissing it again. JupyterLab has both halves:
`_evtMouseDown` sets `activeCellIndex` on a press anywhere in a cell and enters edit mode only for a
press *in the editor*. Ours has only the second.

### GAP-006 — Three dependency rules hold only by convention

`ui ↛ data`, `data ↛ ui`, and "`AppContainer` is the only place that constructs implementations" are
true today by grep and unguarded by any test —
[§5.4](architecture/building-blocks.md). Extending `ArchitectureGuardTest` would go green immediately,
which is exactly what a regression gate is for.

### GAP-010 — No server-generation signal reaches the editor

The kernel indicator can read *Idle* for a kernel the idle stopper has already stopped, for exactly one
window: backgrounded past the grace, returned, nothing run yet. It corrects itself on the next run — the
stale session is refused, `NotebookKernelSession.reopeningIfStale` drops it, and the status falls to
`STARTING` ([ADR-0065](adr/0065-the-kernel-indicator-shows-status-first-and-solid-means-active.md)).

The honest fix is a **server-generation signal reaching the editor**, which `BaseSpaceServer` already
counts. That is a new port surface rather than a UI patch, which is why it is recorded here.

### GAP-011 — The running panel has no kernel half

JupyterLab's `running-extension` registers one left-area panel at **rank 200** — between File Browser 100
and Command Palette 300, which is where `SidePanel.RUNNING` sits. Its stock sections are **Open Tabs**
(`labShell.widgets('main')`), **Kernel Sessions** (`GET /api/sessions`, per-row shutdown, "Shut Down All",
"Shut Down Unused" for kernels with no connections), and **Terminals** (`GET /api/terminals`).

`TerminalGateway.running()` already exists; the kernel half needs `GET /api/sessions`, which the kernel
lifecycle does not yet expose. A kernel shut down under an open notebook leaves the notebook showing "No
Kernel", exactly as the reference's own `selectKernel`-adjacent state does.

### GAP-009 — Restart Kernel is not reachable from the Console

Both `vscode-jupyter` and JupyterLab keep Restart on the console's own primary toolbar — unlike
Interrupt, which neither exposes outside a menu or the palette — so its absence here is a real parity
gap.

It is not simply added because of cross-object staleness, not effort. `ConsoleViewModel` holds a
`NotebookKernelSession` built by `NotebookKernel.attachConsole()`, which is a **second object**,
merely constructed with the notebook's own live session *value* at that moment rather than a shared
reference to it. A Console-side `restart` would genuinely restart the kernel process — both objects
resolve the same session id — but the notebook's own `kernelRestarting`/`kernelConnected` state would
never hear about it: no spinner, no "variables will be lost" warning, no staleness signal at all on
the screen that owns the kernel's lifecycle. Restart is also meant to sit behind a confirmation,
which a Console-side call would need its own copy of.

Both are solvable; neither is a control-placement change.

### GAP-008 — The local gate does not compile `androidTest`

Measured, not reasoned about: an instrumented test file with an unresolved reference survived the
whole documented gate — `ktlintCheck`, the three named source-set checks, `detekt`, `lintDebug`,
`testDebugUnitTest`, `assembleDebug` — with `BUILD SUCCESSFUL`. The next `connectedDebugAndroidTest`
failed at `compileDebugAndroidTestKotlin` with `Unresolved reference`.

**Every task in that list stops short of the source set.** `assembleDebug` compiles `main`;
`testDebugUnitTest` compiles `test`; `ktlintAndroidTestSourceSetCheck` is a *formatter*, and a
formatter parses without resolving. The pre-commit hooks inherit the gap, since they run the same
tasks.

The cost is bounded: a broken instrumented test cannot reach a device, so it fails loudly at the next
`connectedDebugAndroidTest` rather than passing quietly. But it fails **after** a 7-minute device run
has been queued, and a commit can carry it. `compileDebugAndroidTestKotlin` is the one task that
closes it, and it takes seconds.

### GAP-012 — `is_complete_request` is never asked, so the Console cannot know a block is unfinished

The shipped kernel dispatches it (`ipykernel`'s own `msg_types`, `kernelbase.py` in the rootfs) and
nothing here sends it. The reference asks it where a user submits a block that may continue —
`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/console/src/widget.ts:979` and `packages/notebook-extension/src/index.ts:1962`. Ours has a
Console (`ui/console/`), so the absence is reachable: submitting `for i in range(3):` executes it
and takes the `SyntaxError` rather than waiting for the body.

Bounded by what the kernel does on its own: an incomplete block is an ordinary Python error, not a
hang.

### GAP-013 — There is no comm channel, so no `ipywidgets`

`comm_info_request` is one of the ten shell messages the shipped kernel answers, and `comm_open` /
`comm_msg` are how a kernel-side object and a client-side view talk. None is sent or read
(`data/space/JupyterMessage.kt` builds five message types; `KernelOutputs` maps none of the three).

Not in [§1](architecture.md)'s out-of-scope list, which names the file viewers, `metadataform`,
`celltags`, `workspaces` and `logconsole` — so this is an unrecorded absence rather than a declined
one. It is also the largest of the three: a widget is a renderer, a protocol and a state model, and
the reference itself has no caller for `comm_info_request` inside `packages/` because the feature
lives in a separate project.

### GAP-014 — `allow_stdin` is false, so `input()` raises instead of prompting

`data/space/JupyterMessage.kt:106` sends `allow_stdin: false` on every `execute_request`, with the
reason given as "there is no terminal behind this kernel". The consequence is exact and measured in
the shipped image: `raw_input` and `getpass` check the flag and raise
`StdinNotImplementedError("raw_input was called, but this frontend does not support input requests.")`
(`usr/lib/python3.12/site-packages/ipykernel/kernelbase.py:1400-1402`, and `:1375-1377` for
`getpass`). A cell calling `input()` therefore fails with a traceback rather than hanging — the
damage is bounded, and `input()` is ordinary teaching Python.

The reference offers the opposite default — `allow_stdin: true`
(`packages/services/src/kernel/default.ts:801`) — and draws the prompt inside the output area
(`packages/outputarea/src/widget.ts`), which is the shape to take: the flag is one line, and the
feature is a prompt, a reply and a cell that stays busy while it waits.

### GAP-007 — The execution stall is parked, not closed

Reproducible and understood as far as the frames go; the request never reached the kernel, and the
server's own log cannot say why ([CLOSED-005](research/closed-questions.md)). Everything needed to
restart the investigation is in [`research/execution-stall.md`](research/execution-stall.md).

---

## 12. Glossary

In [`architecture/cross-cutting.md`](architecture/cross-cutting.md).
