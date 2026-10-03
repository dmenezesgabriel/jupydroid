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
102 command ids, 99 of them `notebook:*`; `filebrowser-extension` 38, `console-extension` 21,
`terminal-extension` 11, `documentsearch-extension` 6, `toc-extension` 5. Those lists are the
specification. A behaviour that cannot be reached on a phone is **adapted, never invented**, and the
adaptation is written down beside what it departs from.

Those counts are **produced**, and asserted rather than trusted:

```sh
python3 scripts/read_reference_commands.py --all
python3 -m unittest discover -s scripts -p 'read_reference_commands_test.py'   # PinnedSurfaceTest
```

Every number above holds at the SHA `README.md` pins, and `PinnedSurfaceTest` fails if a bumped pin
changes one — because a count is only a fact together with the pattern that produced it, and two
reasonable patterns for a command-id constant differ here by seven real ids
([ADR-0067](adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)). These numbers
were each nine and three lower until the pattern was made to read a declaration the reference's own
formatter wrapped onto a second line; the test that holds them now is the one that would have caught
it, which is the point of having one. What we have of
that surface, and why each difference exists, is
[`research/parity-ledger.md`](research/parity-ledger.md).

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

Two misses share one cause, and one fix. A block opener with a trailing comment (`if x:  # note`)
gets no extra indent level, and the line being typed is never re-indented: the reference's
`smartIndent` key runs `indentOnInput`, which dedents `else:`/`elif`/`except`/`finally:`/`case` and
reindents on a typed `)`/`]`/`}` (`@codemirror/lang-python@6.2.1`'s own regexp, gated at
`packages/codemirror/src/extension.ts:918-925`), and nothing here does either direction. Fixing
both means asking the lexer where the tokens are, a dependency the transformation does not
otherwise have. Replacing a selection is likewise left alone rather than wrapped in the typed
bracket — that half is a decline, not a miss: wrapping is a separate feature, and half-doing it
leaves an unmatched closer.

### GAP-005 — Nothing selects a cell without editing it

Focus is what selects a cell here (`EditorViewModel.cellFocusChanged`), so the head moves only with
the caret, or as a side effect of something else: a menu opened on a cell
(`CellOperation.kt`'s `applyingFrom`), landing on a definition or a match, or a run. JupyterLab has
both halves. `_evtMouseDown` sets `activeCellIndex` on a press anywhere in a cell, and asks for edit
mode only for a press *in the editor* (`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/notebook/src/widget.ts:3160-3230`). Ours has only the second.

Four reference commands are missing for this one reason, measured against the code rather than a
grep. `notebook:move-cursor-up` / `-down` are `ArrowUp`/`K` and `ArrowDown`/`J` in command mode, and
`notebook:move-cursor-heading-above-or-collapse` / `-below-or-expand` are `ArrowLeft`/`ArrowRight`
(`packages/notebook-extension/schema/tracker.json:523-550`). No composable in `ui/editor/` reads a
key, so none of the four has a surface. It also affects `notebook:run-cell`: the cell header's Run
runs its cell and leaves the head where it was, while VS Code focuses the cell first
(`vscode@3f3380c668d07649b953eb8278354b2f263ea6ff`
`src/vs/workbench/contrib/notebook/browser/controller/executeActions.ts:295-297`). So after
running cell 5 with the head on cell 2, *Run Selected Cell and All Below* still means cell 2.

Every head-relative command pays for it: `run-all-above`, `run-all-below`,
`restart-and-run-to-selected` and the heading inserts are aimed by tapping into a cell, taking the
keyboard, and dismissing it again. Closing it means a press on a cell's chrome selects it in
command mode, as `applyingFrom` already does for the menu. The four key bindings, and the header's
Run, then sit on that one primitive. `withCellSelected` already exists and leaves the mode alone.

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

### GAP-015 — A cell clipboard pastes below, and only below

The reference pastes three ways — `notebook:paste-cell-below`, `paste-cell-above` and
`paste-and-replace-cell` — all three in its Edit menu
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/notebook-extension/schema/tracker.json:51-60`), while its cell context menu offers below
alone, which is the one placement ours has (`CellActionItems.kt`'s `clipboardItems`). With no Edit
menu, the other two have no surface here at all. `withClipboardPasted` already takes the cell it
pastes from and computes where a collapsed section ends, so the transform is a placement parameter
rather than a new function; what is missing is where the rows go.

### GAP-016 — Code and outputs fold one selection at a time, never the whole notebook

`notebook:hide-all-cell-code`, `show-all-cell-code`, `hide-all-cell-outputs` and
`show-all-cell-outputs` are the reference's View menu (`tracker.json:125-165`); none exists here.
Headings already have their notebook-wide pair (`NotebookCellEdits.setAllHeadingsCollapsed`, in
`NotebookActionsMenu`), and `CellFolds.withSourceHiddenSet` / `withOutputsHiddenSet` take a list of
cell ids, so passing every code cell is the transform. The cost is four rows in a menu that is
already the reference's View menu, and a decision about whether they are four rows or two toggles.

### GAP-017 — Output text cannot be selected, so it cannot be copied

Outputs draw through a plain `Text` (`ui/editor/CellOutputsView.kt`'s `OutputText`) and nothing in
the app wraps them in a `SelectionContainer`. A long press on an output selects nothing — measured
on the tablet, no toolbar appears. The reference's `notebook:copy-selected-text` falls back to the
page's selection when the editor has none, which is how text is copied *out* of a result there
(`packages/notebook-extension/src/index.ts:3786-3820`). Text inside a cell's source is the platform
field's and works ([ADR-0068](adr/0068-text-inside-a-cell-is-its-fields.md)); this is the half with
no field behind it. `notebook:copy-to-clipboard`, which copies a whole output, is the rendering
audit's, and closing either changes what the other has to do.

### GAP-018 — A cell's text undo has no touch control

The history exists and works: Compose's `TextFieldState` answers `Ctrl+Z` and `Ctrl+Shift+Z`, one
entry per run of typing, measured on the tablet
([ADR-0068](adr/0068-text-inside-a-cell-is-its-fields.md)). What is missing is a way to reach it
without a hardware keyboard. The reference puts `notebook:undo` / `notebook:redo` in Edit → Undo /
Redo (`packages/notebook-extension/src/index.ts:5365-5372`); the platform text toolbar has no Undo
item; and the undo pair on this app's bar is the **cell-operation** history, which is a different
scope by design (`CellUndo.kt`). Closing it means deciding which history a single visible Undo
reaches while a cell has the caret — VS Code answers that by focus — rather than adding a second
pair of arrows.

### GAP-019 — Markdown cells cannot be rendered together without running the notebook

`notebook:render-all-markdown` (*"Render All Markdown Cells"*, the reference's Run menu,
`tracker.json:205-215`) renders every markdown cell and runs nothing
(`packages/notebook/src/actions.tsx:968-990`). Here the only notebook-wide render is Run All's
markdown branch (`NotebookCellRuns`, through `MarkdownEditing.withMarkdownRendered`), so closing
every open markdown editor costs executing every code cell. `withMarkdownRendered` already takes a
list of cell ids; the gap is a surface.

### GAP-020 — A run never advances, so running a notebook cell by cell is a tap per cell and a scroll

`notebook:run-cell-and-select-next` is `Shift Enter` and the notebook toolbar's own Run button
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/notebook-extension/schema/panel.json:17-19`). `notebook:run-cell-and-insert-below` is
`Alt Enter` and the Run menu (`schema/tracker.json:185`, `:605-622`). Both run the selection, and
then move the head on: to the next cell, or to a new one inserted below, entering edit mode on a cell
they had to create (`packages/notebook/src/actions.tsx:814-924`). Here, every Run leaves the head
where it was: the cell header's `NotebookCellRuns.runCell`, and `run(RunRange.MARKED)` on the
selection bar, which puts the head on the last cell it ran and stops. Stepping through a notebook
is therefore a Run and a scroll per cell. Closing it is a third run command over the existing
`cellsToRun(RunRange.MARKED)` and `withCellInserted`. The decision it carries is where the control
goes, since this app's bar already holds Run All and the cell's own Run means *this cell*.

### GAP-021 — There is no way back to the cell that is running

`notebook:select-last-run-cell` (*"Select current running or last run cell"*) is what clicking the
notebook's execution indicator does
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/notebook/src/executionindicator.tsx:93-99`). It picks the latest
`metadata.execution['iopub.status.busy']`, so it does nothing unless `recordTiming` is on
(`packages/notebook/src/actions.tsx:2117-2151`). Ours records a start for every run without asking
(`CellExecution.startedAt`, `ui/editor/CellExecution.kt`), so the data is already there, but the
kernel indicator jumps nowhere. A Run All that has scrolled away from the cell it is executing
leaves no way to find it except scrolling.

### GAP-022 — Edited cells have no back and forward

`notebook:select-last-modified-cell` and `select-next-modified-cell` walk a back/forward stack of
the cells most recently edited (`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/notebook/src/widget.ts:2538-2616`). They are the table of contents' two toolbar arrows
(`packages/notebook/src/toc.ts:846-868`). Nothing here records which cell was edited, and the table
of contents has no toolbar. The stack is the reference's `@experimental` API. The table of contents
itself belongs to the search audit, so the gap is recorded rather than costed.

### GAP-023 — A save is not atomic, so an interrupted write can leave half a notebook

`DiskNotebookRepository.saveNotebook` ends in a bare `File.writeText`
(`app/src/main/java/com/jupydroid/app/data/disk/DiskNotebookRepository.kt`): the file is
truncated where it sits and rewritten in place. The reference stack's save is atomic —
jupyter_server's contents manager routes every save through `atomic_writing`, a temporary file
moved over the target by `os.replace`
(`usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py:103`, `:528-530`,
in the shipped rootfs). The window is narrow — a process kill or a dead battery mid-write — and
the failure it leaves is the worst kind there is: a notebook that opens as invalid JSON.
Closing it is a write to a sibling file and a rename, and nothing more.

### GAP-024 — A completion row's documentation is never fetched

`completionItem/resolve` is the ask that turns a completion row into its documentation, and the
reference routes it (`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/lsp/src/tokens.ts:866`). The shipped `pylsp 1.15.0` advertises `resolveProvider: true`
on its `completionProvider`, and a probe on the device measured the resolve reply carrying
`detail` plus a markdown `documentation` where the completion reply carries neither. Nothing
here sends it — `LspCompletion.kt` decodes the completion reply's own fields and stops — so a
popup row shows its label and kind and never its docstring. The kernel-side twin the reference
uses for the same resolve (`requestInspect` in `completer/default/kernelprovider.ts:113`) is the
unasked `inspect_request` of [ADR-0069](adr/0069-a-cells-hover-asks-the-source-not-the-live-object.md).

### GAP-025 — `textDocument/references` is advertised and never asked

The shipped `pylsp` advertises `referencesProvider`, and the reference routes the ask
(`packages/lsp/src/tokens.ts:875`) — its Find All References surface. Nothing here sends it:
`LanguageServerPort` carries no operation for it and no editor surface would drive one. The
probe measured the reply shape (`Location[]` into the same document) and one wire rule an
implementation owes: `pylsp` rejects the ask with `-32602` unless `context.includeDeclaration`
is set. The gap is a surface, not a transport.

### GAP-026 — `textDocument/documentHighlight` is advertised and never asked

The shipped `pylsp` advertises `documentHighlightProvider`, and a probe measured the reply —
`[{range, kind}]` with `kind` splitting reads from writes. The reference routes it
(`packages/lsp/src/tokens.ts:870`) as the occurrences highlight a caret on a symbol paints on
its other uses. Nothing here sends it, and nothing in the cell editor would draw it. It is the
visible half of GAP-025's question.

### GAP-027 — `textDocument/documentSymbol` is advertised and never asked

The shipped `pylsp` answers `documentSymbol` with a flat `SymbolInformation[]` —
`location`/`containerName`/`kind`, probe-measured — and the reference routes it
(`packages/lsp/src/tokens.ts:871`) as the outline a symbol list is drawn from. Nothing here
sends it; the notebook's table of contents is a different surface, fed by markdown headings
rather than symbols. What is missing is the outline and the pane it would live in.

### GAP-028 — `textDocument/typeDefinition` is advertised and never asked

The shipped `pylsp` replies `typeDefinitionProvider: true`, and the reference routes the ask
(`packages/lsp/src/tokens.ts:878`). Ours asks `definition` alone (`NotebookDefinition`): the
jump lands on the symbol's own site and never on its *type's* site — the half of the jump a
`None`-or-`Any`-shaped definition needs. No surface offers it.

### GAP-029 — The file editor asks a language server nothing

`ui/fileeditor/` opens, edits and saves a text file with the notebook's own typing rules and
highlighting, and never names `LanguageServerPort` — no completion, no diagnostics, no
`textDocument/formatting`, which the shipped `pylsp` answers through black and which only the
notebook's `rangeFormatting` ever approaches (`NotebookFormat`). The reference's file editor
rides the same document adapter as its notebook (`packages/lsp/src/adapters/`), so a `.py` file
opened here is the one place a language feature is expected and unreachable.

### GAP-030 — No configuration route reaches `pylsp.plugins.*`

The shipped server reads plugin configuration from three routes and nothing here writes any of
them: `initialize`'s `initializationOptions`, sent `null` (`LspWire.initializeParams`);
`workspace/didChangeConfiguration`, never sent — pylsp's own
`m_workspace__did_change_configuration` merges `settings["pylsp"]` and re-lints every open
document (`usr/lib/python3.12/site-packages/pylsp/python_lsp.py:832` in the shipped rootfs); and
`workspace/configuration`, which `JsonRpcChannel.answerServerRequest` declines with
`result: null`. With `rootUri` also `null`, even its `configurationSources` files have no
project to be read from. The server therefore runs factory defaults: `pylint` stays disabled
(`plugins/pylint_lint.py`'s own `"enabled": False`), and autopep8 sees no `pycodestyle` keys —
the reason `LanguageFormatOptions`' docstring already names for an editor indent unit that
cannot reach the formatter.

### GAP-031 — Completion waits on a session, so a document's own tokens are never offered

The reference's completer merges providers by rank, and the always-applicable one —
`CompletionProvider:context` at rank 500 (`packages/completer/src/default/contextprovider.ts`,
`completer-extension/schema/manager.json`'s `availableProviders`) — answers from the tokens
already in the document, needing no kernel and no server. Ours asks only behind
`session.opened` (`NotebookCompletion`), and the ask rows hide until `kernelConnected`
(`EditorContent.kt`): a notebook that has never run completes nothing, and a word that exists
only in a comment or a not-yet-bound line is offered by nobody even when it has. The token
source is a cell-side read, so closing this wants no new wire — a provider that reads the
cells' own text.

### GAP-032 — There is no inline completion channel, so history never suggests

The reference's second completer channel is `IInlineCompletionProvider` — grey continuation
text accepted in place — and the one provider riding it is `HistoryInlineCompletionProvider`
(`packages/completer/src/default/inlinehistoryprovider.ts`), which turns `history_request`
replies into frequency-ranked suggestions. Ours holds the wire (`history_request` is `present`
in the ledger's `AUD-001` table) but the channel is absent whole: `CompletionPopup` is a list a
tap accepts, and no field renders a suggestion ahead of the caret.

### GAP-033 — A cell's editor carries exactly one selection

`TextFieldState` holds a single `TextRange`, so the reference's three multi-selection keys have
no shape here: `rectangularSelection` (the Alt-drag block), `extendSelection` (`Mod-Shift-L`,
select-all-matches) and `allowMultipleSelections`
(`packages/codemirror/src/extension.ts:821-835`, `:864-876`, `:988-998`). It is the same bound
`notebook:split-cell-at-cursor` already met — one selection, so two or three fragments, never
more (`CellSplit.kt`). A fix is
platform-shaped rather than local: Compose would have to grow multi-cursor state, or the feature
stays declined.

### GAP-034 — A `!pip` cell gets no static diagnostic

`vscode-jupyter` runs a provider that is no language feature at all — a static scan turning
`!pip install` / `!conda` / `%matplotlib`-outside-a-notebook cells into diagnostics with a
`%pip` quick fix
(`vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155`
`src/standalone/intellisense/diagnosticsProvider.ts`). Ours flags nothing outside what `pylsp`
pushes, and pyflakes has no such rule. JupyterLab itself ships nothing like it, so the gap is
against the extension reference alone.

### GAP-035 — The diagnostics watch gates on the kernel session, though `pylsp`'s lifetime is the Space's

`NotebookDiagnostics.keepSynced` sends the virtual document's `didChange`s only while
`kernelConnected` holds (`app/src/main/java/com/jupydroid/app/ui/editor/NotebookDiagnostics.kt`)
— the asks' own "a runtime was paid for" gate borrowed for the watch, since a sync lazily spawns
the server. But `pylsp` is the app's own subprocess: spawned by any sync, stopped only by the
idle stopper's `stopServer` (`app/src/main/java/com/jupydroid/app/di/AppContainer.kt`). The gate
is wrong in both directions. A session ended elsewhere — `NotebookKernelSession.endIfMatches`,
the Running panel's shutdown — stops the syncs while the server and its document live on, and the
marks freeze in `diagnosticsByCellId` on whatever lines they last mapped, drifting wrong under
fresh typing until a reconnect resyncs. The other way, a session the idle stopper left stale
still reads `kernelConnected`, so typing respawns ~60 MB of `pylsp` while `jupyter_server` is
still down — the boot a gate exists to prevent, paid anyway. The repair is a watch keyed on the
server's existence rather than the session's — a "is there a server" the port does not answer
today — or clearing the marks at the drop; either is a build item, not an audit's.

### GAP-036 — Find is the notebook's alone: no in-document search reaches the file editor or the terminal

JupyterLab gives every document kind a search surface: `fileeditor:find` drives CodeMirror's own
panel (`execCommand(findNext)`, `packages/fileeditor-extension/src/commands.ts:624-641`), mounted
by the `searchWithCM` editorConfig key (`packages/codemirror/src/factory.ts:28`), and the
documentsearch overlay reaches the rest through `ISearchProviderRegistry` — file editor, csv,
tsv and terminal registrations beside the notebook's
(`packages/fileeditor-extension/src/index.ts:288`,
`packages/terminal-extension/src/index.ts:411`). Ours has the notebook's find bar and nothing
else: `FileEditorScreen` can only *land* a workspace-search result (`reveal`), not find inside
the file, csv/tsv documents open there too, and `TerminalPaneScreen` has no find at all. The
shape a fix takes is a find surface on the file editor's field plus a terminal one — both
borrowed from `SearchQuery`/`searchHits`, which are already document-order walks over plain text.

### GAP-037 — The `selection` search filter cannot see text inside a cell

The reference's filter has two modes and ours carries one. `getSelectionState` answers
`"single"` when a single edit-mode cell holds a text selection, and the provider then searches
just the selected *lines*
(`packages/notebook/src/searchprovider.ts:166-174`, `:264-276`); with several cells marked it is
the marked run, which is the half `searchHits`' `onlySelected` reproduces. A narrower ask — the
lines inside one cell — has no shape here: `EditorUiState` keeps no per-field selection, which
is ADR-0068's split, so the find bar would need the focused field's `TextFieldState.selection`
handed up before the filter could mean it.

### GAP-038 — Workspace search reads no ignore files

`search.useIgnoreFiles` is **true** by default upstream — `.gitignore` and `.ignore` shape every
walk before a glob box is typed, with `useGlobalIgnoreFiles` and `useParentIgnoreFiles` beside it,
all three feeding `disregardIgnoreFiles` into the query
(`vscode/src/vs/workbench/contrib/search/browser/searchView.ts:1666-1671`,
`search.contribution.ts:142-158`). `SearchFilter` knows `include`/`exclude` globs and the eight
registered defaults and nothing else: a workspace holding a git checkout is searched through
`build/` and `node_modules`-adjacent noise the user's own repository already excluded. The walk
itself has the right seam — `SearchFilter.searches`/`descendsInto` is asked of every path — so the
gap is a `.gitignore` reader feeding it, per directory.

### GAP-039 — The search box cannot be scoped to open editors or changed files

The include input mounts two toggles beside it — `onlySearchInOpenEditors` and
`onlySearchInChangedFiles` (`patternInputWidget.ts:210-227`) — and `commonQueryFromFileList`
rewrites the first into a query over exactly those resources (`queryBuilder.ts:258-263`); the
second rides the SCM provider's changed-file list. `SearchScope` carries the two glob boxes alone.
On this device the first one is the one that matters: the workspace is the whole Space, so
"narrow it to what I have open" is the small-screen scope a glob cannot express — and the answer
shape already exists, since `OpenDocuments` is the registry a scope like that would read.

### GAP-040 — The search panel's results take no action past a tap

A result row there can be opened and a file folded — that is all. The reference's tree answers to
**Dismiss** per element (which is also how a replace is narrowed without re-querying), **Replace**
per match, file and folder (`searchActionsRemoveReplace.ts:54-243`), and the view itself carries
Refresh, Clear Results, Cancel Search, collapse/expand-all and the copy actions
(`searchActionsTopBar.ts:42-157`, `searchActionsCopy.ts`), plus a query history on the input box.
None has a surface here — and Dismiss is not cosmetic: it is the difference between "replace
everywhere" and "replace everywhere *except* this".

### GAP-041 — Workspace results are a snapshot; the reference keeps them live

Two halves of one property. At query time the reference merges `getOpenEditorResults` — each open
editor's model searched as it stands, beating the disk hit for the same file
(`searchService.ts:127-145`, `:497-575`) — and asks an open *notebook* through its widget
(`notebookSearchService.ts:230-268`); ours walks the disk alone, with `DocumentAutosave` narrowing
the window to a debounce. Afterwards, `bindModel` re-matches a bound model on every
`onDidChangeContent` (`fileMatch.ts:135-175`, 250 ms) and `onFilesChanged` removes deleted
resources (`searchView.ts:2354-2370`); ours answers staleness only at the tap
(`MatchedLine.rangeIn`, `hitFor`) and at the replace (`editsAgainst` refuses). The seams to close
it already exist — `OpenDocuments` for the live content, `WorkspaceRepository.changes` for the
file events.

### GAP-042 — Selecting a match never shows what a replace would do

`search.useReplacePreview` defaults true: while the replace box is armed and non-empty, tapping a
match opens a diff editor of the file *as the replace would write it*
(`searchView.ts:2229-2237` → `replaceService.ts:140-167`, `toReplaceResource`). Ours always opens
the document itself — there is no second editor shape, and a user checking a rename's blast radius
gets only the counted confirm. The diff half of the machinery is what is missing; the edits
themselves (`editsAgainst` → `withEditsApplied`) are already computed before anything writes.

### GAP-043 — The outline lists every heading it finds

`maximalDepth` bounds the reference's list at 4 (`toc-extension/schema/registry.json`,
`filterHeadings` keeps `level <= config.maximalDepth` at `toc/utils/common.ts:50`), and an author
opts a heading out with `jp-toc-ignore`/`tocSkip` (`common.ts:104-106`, `markdown.ts:351-352` —
HTML headings and markdown tokens alike). `tableOfContentsHeadings` keeps both readings unbounded:
a `#####` five levels down and a heading marked skippable are listed exactly alike. The repair seam
is a filter over `tableOfContentsHeadings` for the depth cap; the opt-out wants a marker convention
read in `MarkdownDocument`'s parse and in `RenderedOutput`'s blocks.

### GAP-044 — A table-of-contents row is jump-only

`toc:run-cells` is *"Select and Run Cell(s) for this Heading"* — the heading's section marked and
run (`notebook-extension/index.ts:5124-5160`), mounted on the row's context menu
(`toc-extension/schema/registry.json:18-24`). VS Code mounts the same menu on an outline entry:
`notebook.section.runSingleCell`/`runCells` plus `foldSection`/`expandSection`
(`notebook/browser/controller/sectionActions.ts:30-248`). Here a row answers a tap and nothing
else — `TableOfContentsRows`' `onChoose` is the whole surface, so "run this section" and
"fold this section from the outline" have no shape. The pieces exist: `headingSectionOf` is the
same section the reference walks, and `RunRange`/`CellRangeSelection` are a long-press menu away.

### GAP-045 — The outline reads the notebook alone

`ITableOfContentsRegistry` gets five factories — the notebook, the LaTeX/Markdown/Python file
editors (`fileeditor-extension/index.ts:585-587`), the markdown viewer
(`markdownviewer-extension/index.ts:233`) — and VS Code's `OutlineModel` is `DocumentSymbol`-driven
for any provider (`documentSymbols/outlineModel.ts:196-255`), its view adding `followCursor`, sort
and filter (`outlineViewState.ts:12-53`). `FileEditorScreen` publishes no `DocumentOutline`, so a
`.md` or `.py` open in the file editor has structure nothing lists. `PythonTableOfContentsModel`
shows no language server is needed — it is a regex over `class`/`def`/`import` lines
(`fileeditor/toc/python.ts:21-111`) — so `GAP-029`'s LSP hole is adjacent but not the blocker. The
publish seam is the one `OpenDocumentOutline` already is.

### GAP-046 — The outline knows neither where the document is nor what it is doing

`onActiveCellChanged` → `setActiveHeading` draws the row owning the active cell `selected`
(`notebook/toc.ts:379-395`, `toc/tocitem.tsx:60-63`); `isRunning`/`data-running` marks each heading
idle/scheduled/running/error, a folded heading aggregating its subtree's worst (`toc.ts:397-556`).
VS Code's outline does both the other way — `followCursor` (`outlineViewState.ts:12-33`) and a
per-entry `exeState` bubble (`notebookOutlineEntryFactory.ts:95-117`). `TocRow` carries the heading,
its fold and its prefix — no current mark, no run state. The notebook's own run state already
exists per cell (`EditorUiState.executionOf`); the gap is carrying it onto the row.

### GAP-047 — A notebook can carry its own ToC config, and nothing reads it

`configMetadataMap` maps `toc-autonumbering`, `toc/number_sections`, `!toc/skip_h1_title` and
`toc/base_numbering` out of the notebook's metadata into the model's configuration
(`notebook/toc.ts:565-571`), applied on load and on `metadataChanged` (`toc.ts:347-377`,
`:467-469`) — a JupyterBook-style file keeps its own numbering rules. `EditorSettings.toc*` is
app-wide and the only layer here: a notebook that carries `toc/skip_h1_title` loses it on open.
The repair reads metadata where the notebook is read, not beside the app-wide settings.

### GAP-048 — A replace-all validates by re-splitting each resource once per match

`List<MatchedLine>.editsAgainst` is the staleness check a replace-all pays before a single write:
`stillAt` → `rangeIn` → `text.split("\n")`, **per match**
(`app/src/main/java/com/jupydroid/app/domain/model/TextEdit.kt`,
`app/src/main/java/com/jupydroid/app/ui/shell/WorkspaceReplace.kt`). The reference's version of the
check is a `versionId` compare per resource — O(matches) for the file
(`vscode/src/vs/workbench/contrib/bulkEdit/browser/bulkTextEdits.ts:213-216`). Ours is
O(matches × lines): a file holding a thousand matches re-splits its line list a thousand times, and
the same `rangeIn` is what a tapped match pays once on landing, where it costs nothing. The repair
splits each resource once and answers every match's range by index — the arithmetic `startOf`
already does — and changes no rule the refusal is built on.

### GAP-049 — A search reads each file whole into memory, and nothing bounds it

`DiskWorkspaceSearcher.readableTextAt` calls `File.readBytes()` — the entire file on the heap
*before* the NUL-byte check decides it was even text
(`app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceSearcher.kt`), and `matchedCellsAt`
reads a notebook the same whole way. The reference delegates the read to ripgrep, which streams
the file and bounds it at `search.maxFileSize` (16 GB,
`vscode/src/vs/workbench/services/search/node/rawSearchService.ts:85-87`). A workspace here shares
a device: a dataset-sized file walked by a search is a read that can out-allocate the app before
one byte is weighed. The repair is the bound first — a size cap the walk reports rather than
swallows — and a streamed or mapped read second; the NUL rule stands either way and only needs a
window to look through.

### GAP-050 — The listing has no selection, so nothing acts on more than one row

`DirListing` keeps a `Set<string>` of selected paths and `selectAll()` fills it
(`jupyterlab/packages/filebrowser/src/listing.ts:543`); the `showFileCheckboxes`
column renders it, and `filebrowser:select-all`, the context menu's copy/cut/delete
over every selected item, and `copyShareableLink`'s `selectedItems().length === 1`
all ride it. Ours gives a row two gestures — a tap opens, a long-press opens that
row's own actions (`app/src/main/java/com/jupydroid/app/ui/explorer/ExplorerScreen.kt`)
— so a batch act has no selection to consume and a single-item act hidden behind
`isVisible` has no way to appear. The repair starts with selection state on
`ExplorerUiState` — a long-press that selects first is the checkbox column's own
answer.

### GAP-051 — The browser cannot be aimed at a typed path

`filebrowser:open-path` runs an `InputDialog` that resolves the path against
`services.contents` and navigates or opens it
(`jupyterlab/packages/filebrowser-extension/src/index.ts:1476-1530`), and
`filebrowser:edit-path` swaps the crumbs for a `PathNavigator` inline
(`jupyterlab/packages/filebrowser/src/crumbs.ts:847`). Ours' crumbs are targets and
nothing else takes a path, so a deep directory is reachable only by tapping through
every level. The seam already exists — `browseRequest` navigates the browser from a
path (`app/src/main/java/com/jupydroid/app/Navigation.kt`); the missing piece is the
field that asks for one.

### GAP-052 — The listing shows every entry; nothing filters it

`showFileFilter` mounts a `FilenameSearcher` that narrows the listed rows by
substring — fuzzy-matched when `useFuzzyFilter`, scoped by `filterDirectories`,
cleared on navigation
(`jupyterlab/packages/ui-components/src/components/search.tsx:273`). Ours has no
listing filter; workspace search is a different act — it reads file *contents*
(`app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceSearcher.kt`) — so a
long directory offers only scroll. The repair is a field over the loaded listing,
not a searcher: the entries are already in memory.

### GAP-053 — The listing's order and fields are fixed

A `DirListing` header click sets `sortState` — a key and a direction across name,
last_modified, file_size, date_created
(`jupyterlab/packages/filebrowser/src/listing.ts:322,1585`) — and VS Code exposes
`explorer.sortOrder`'s six modes plus lexicographic and reverse
(`vscode/src/vs/workbench/contrib/files/browser/files.contribution.ts:520-562`).
Ours holds one order — directories first, `compareNaturally` — and `WorkspaceEntry`
reads neither `size` nor `created`, so two of the reference's fields cannot be drawn
or sorted on even if a header existed. The model fields come first; the header is
cheap once they do.

### GAP-054 — Nothing puts a file into the workspace

`allowFileUploads`, `autoOpenUploads`, `maxAutoOpenSizeMB` gate
`jupyterlab/packages/filebrowser/src/upload.ts` and the listing's drag-drop; VS
Code mounts an upload act on the same surface. Ours has no path in: the VIEW intent
reaches `createNotebook` as a display *name* only — the shared file's content is
never copied (`app/src/main/java/com/jupydroid/app/Navigation.kt`) — and no picker
exists. The repair's shape is SAF (`OpenDocumentTree`) into `WorkspaceRepository`;
the reference's auto-open pair then maps onto the open-document act.

### GAP-055 — A file cannot leave the workspace from the tree

`filebrowser:share-main` (`copyShareableLink`) copies
`PageConfig.getUrl({treePath, toShare: true})` — a URL that presupposes a server the
recipient can reach, JupyterHub's shape
(`jupyterlab/packages/filebrowser-extension/src/index.ts:862-880`); VS Code's
download and copy-path sit beside it. Ours already has the seam — `DocumentSharer`
hands a document to other apps — but only an *open* document reaches it
(`app/src/main/java/com/jupydroid/app/domain/port/DocumentSharer.kt`); a row has no
act that shares, downloads, or copies a path. The repair is a row action, not new
plumbing.

### GAP-056 — A fresh listing is only ever the poll's

`filebrowser:refresh` forces `FileBrowserModel.refresh()` outside the 10 s tick —
a toolbar button and a palette entry
(`jupyterlab/packages/filebrowser-extension/src/index.ts:1604`) — and VS Code
mounts `RefreshExplorerAction` beside its refresh-on-visible. Ours polls at the
same interval and re-lists after every act of its own, but a change nobody polled
yet — a file a cell just wrote, a push from outside — waits up to the tick and
cannot be pulled forward. `ExplorerViewModel.refresh()` already re-lists
(`app/src/main/java/com/jupydroid/app/ui/explorer/ExplorerViewModel.kt`); the
repair is a gesture that reaches it — a pull-down on the listing is the shape the
surface already suggests — not new plumbing.

### GAP-057 — Nothing creates a text file that is not Python

`filebrowser:create-new-file` takes an `ext` per kernelspec on offer and `create-new-markdown-file`
a `.md`, and the carried `fileeditor:create-new*` pair goes further — `docmanager:new-untitled`
then `docmanager:open`, so the file lands already open
(`jupyterlab/packages/fileeditor-extension/src/commands.ts:1383-1400`). Ours' New menu is exactly
three — notebook, `.py`, folder
(`app/src/main/java/com/jupydroid/app/ui/explorer/ExplorerBar.kt`) — while
`WorkspaceRepository.createFile` already takes any extension and `DocumentOpeners` sends anything
that is not `.ipynb` to the plain file editor
(`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`): a `.md` the editor would happily open
cannot be made. The repair is a menu row, not a new op — the editor is already the fallback.

### GAP-058 — Deleting a file never stops the sessions it was running

`DocumentManager.deleteFile` runs `services.sessions.stopIfNeeded(path)` *first*
(`jupyterlab/packages/docmanager/src/manager.ts:351-363`), so deleting the notebook a kernel is
running ends the session — and `overwrite` gets the same half free, since it deletes the target
through that call (`manager.ts:522`). Ours' `WorkspaceRepository.delete` announces `Deleted` and
an open document goes `gone`, but the kernel it was running on is left running; only the Running
panel's own row can stop it
(`app/src/main/java/com/jupydroid/app/ui/shell/RunningSidePanel.kt`). The repair composes the two
ports at the caller — the workspace port cannot look a session up, and the reference puts the
pairing in its document manager for the same reason.

### GAP-059 — The open document's own surface answers only rename

`docmanager:delete`, `docmanager:duplicate` and the tab context menu they sit on
(`data-type="document-title"`, `jupyterlab/packages/docmanager-extension/src/index.tsx`) act on
the *open document* — the tab asks, and the answer deletes its file or copies it beside itself.
Ours' tab long-press opens the rename dialog and nothing else: deliberate for one item, "a touch
context menu would be one item deep"
(`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt`), but the reasoning was
written for rename alone, and the file acts have no home on the document they act on — the
explorer row's own menu is the only way to delete or would-be-duplicate a file. The repair is
the menu that comment declined the moment it would be two items deep.

### GAP-060 — A document cannot be viewed twice

`docmanager:clone` → `cloneWidget` opens a second view on one context, split-right by default —
two scroll positions over one file
(`jupyterlab/packages/docmanager-extension/src/index.tsx:1397`); VS Code's `openToSide` answers
the same ask with a second editor group. Ours opens reveal-don't-reopen over one entry per path
(`app/src/main/java/com/jupydroid/app/NotebookBackStack.kt`), so a second view is not merely
unoffered — the stack's own rule refuses it. The repair is a clone act on the surface GAP-059
names plus a per-key tab that tolerates two keys for one path.

### GAP-061 — A document cannot be saved on demand

`docmanager:save` runs `context.save()` then `createCheckpoint()`, bound on Accel S with a menu
item, a palette entry and the notebook toolbar's save button
(`jupyterlab/packages/docmanager-extension/src/index.tsx:1105-1235`), and `save-all` sweeps every
writable context (`:1237-1268`); `renameUntitledFileOnSave` hangs a rename prompt on the first
manual save of an untitled file (`:1155-1190`). Ours writes on a debounce and an interval poll
(`app/src/main/java/com/jupydroid/app/ui/editor/DocumentAutosave.kt`) and flushes owed work on the
way out (`saveNow` in `app/src/main/java/com/jupydroid/app/OpenDocuments.kt`), so a save is never
an act — and with `autosaveEnabled` off, an edit is written only when the document is closed. The
repair is a save act on the document's own surface — the actions menu, or the surface GAP-059
names — over `OpenDocuments.saveNow`, which already does the flush-and-check.

### GAP-062 — There are no checkpoints

`context.save()` ends in `createCheckpoint()`
(`jupyterlab/packages/docregistry/src/context.ts:528-540`), and `_maybeCheckpoint` creates one at
first open when a writable file has none (`:841-860`), so `docmanager:restore-checkpoint` always
has a point to return to
(`jupyterlab/packages/docmanager-extension/src/index.tsx:1020-1084`). The shipped backend serves
the API — `jupyter_server/services/contents/filecheckpoints.py` is in the rootfs — but no port
operation names it and nothing ever creates one. The repair starts with the checkpoint calls on
the repository an editor writes through; the picker the command offers is a dialog over
`listCheckpoints`.

### GAP-063 — A document cannot be saved under a new name

`docmanager:save-as` asks a path, refuses a taken one through `_maybeOverWrite`, writes, then
closes the old context and opens the new one off `fileChanged`
(`jupyterlab/packages/docregistry/src/context.ts:295-333`,
`jupyterlab/packages/docmanager-extension/src/index.tsx:1270-1318`); VS Code's `files.saveAs` is
the same act. Ours' `rename` moves a file rather than copying it — the same-shape answer to a
different ask — and no write-a-copy exists; GAP-050 already notes there is no `copy` op for one
to ride. The repair is a path prompt writing the document's bytes through the repository the
editor already writes, then opening the result.

### GAP-064 — An open document never re-reads its file

`docmanager:reload` is `context.revert()` — the file read back and the model reinitialised, asked
first when dirty (`jupyterlab/packages/docmanager-extension/src/index.tsx:968-1018`,
`jupyterlab/packages/docregistry/src/context.ts:676`) — and VS Code's `files.revert` does the
same. Ours opens a file once and never reads it again: `WorkspaceRepository.changes` announces
only what this app moved
(`app/src/main/java/com/jupydroid/app/domain/port/WorkspaceRepository.kt`), so a file a cell
rewrote — or a second Jupyter client — sits stale until the next autosave overwrites it. The
repair is a re-read act on the document; whether it merges or replaces is the question `revert`'s
own dirty-guard already answers.

### GAP-065 — A save never checks what it is overwriting

`Context._maybeSave` re-reads the model (`hash: true`) before every write and raises a conflict
dialog when the disk's hash or `last_modified` is newer than the client's by more than
`lastModifiedCheckMargin` — 500 ms
(`jupyterlab/packages/docregistry/src/context.ts:741-790`) — and the same `_maybeOverWrite` ask
guards `saveAs`. Ours' `writeText` and `saveNotebook` write blind: the workspace is shared with
the kernel, so a cell that rewrites an open file — or any second client on the same directory —
is silently overwritten at the next debounce. The repair is a compare in the write path; the
machinery is the `last_modified` a document already records at read, not a second channel.

### GAP-066 — Nothing remembers a closed document

`RecentsManager` keeps two lists, `opened` and `closed`
(`jupyterlab/packages/docmanager/src/recents.ts:39-50`), and `widgetmanager` records a close on
`'close-request'` the way it records an open on `'activate-request'`
(`jupyterlab/packages/docmanager/src/widgetmanager.ts:273-286,336-403,428-441`): the running
panel's "Recently Closed" section and `recentmenu:reopen-last` both read the list. Ours records
opens alone (`app/src/main/java/com/jupydroid/app/RecordOpening.kt`) — a closed document is only
ever still in `opened`, or gone. The repair is the second list on `RecentDocumentsRepository`,
written where a document closes; the surfaces that would read it are AUD-006's and AUD-009's to
draw.

### GAP-067 — A recent is kept or cleared, never removed alone

`RecentsManager.removeRecent` drops one entry
(`jupyterlab/packages/docmanager/src/recents.ts:124-129`), the running panel's closed rows carry
it as Forget (`jupyterlab/packages/running-extension/src/recents.ts:71-75`), and VS Code's
open-Recent pick mounts a remove button on every row
(`vscode/src/vs/workbench/browser/actions/windowActions.ts:48-56`). Ours offers only
`docmanager:clear-recents`
(`app/src/main/java/com/jupydroid/app/ui/explorer/RecentsActionsMenu.kt`): one dead row in the
list cannot be dropped without dropping all ten. `RecentRow`'s stated reason — a recent carries
no per-row act because rename and delete belong to the file — was written before Forget was
weighed, and the repair is the row act it declined.

### GAP-068 — A session's record does not follow its file's rename

`Context._onFileChanged` answers a rename by pointing the session at the new path —
`sessionContext.session?.setPath(newPath)`
(`jupyterlab/packages/docregistry/src/context.ts:540-555`) — so the running panel's row keeps
naming a live file. Ours' `KernelSessions` has no path op — `running()` lists and `shutdown()`
ends, and that is the whole surface
(`app/src/main/java/com/jupydroid/app/domain/port/KernelSessions.kt`) — so a rename moves the
open document (its `Renamed` adoption is the same event) while the server's session record keeps
the old path: the Running row built from `running()` goes stale, and its tap opens the dead
path as a fresh editor. The repair is a `setPath` on the port PATCHing the session, asked from
the same `changes` collector that already adopts the rename — which is where the reference puts
it, inside the context that heard `fileChanged`.

### GAP-069 — A file act on a tab fails without saying so

`renameFile`'s catch rethrows every refusal that is not a 409
(`jupyterlab/packages/docmanager/src/dialogs.ts`), so a rename refused for another reason at
least reaches the console, and the browser's own row reports it on the listing's error line.
Ours' tab-strip rename keeps only the `FileNameTakenException` arm and the overwrite that follows
it has no failure arm at all
(`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt`) — so a refusal by the disk
answers with silence: the dialog is gone, the tab is unchanged, and nothing learns why. The
strip has no error surface to put it on; the repair is the one a kept-open dialog or the
document's own error line would provide.

### GAP-070 — Every disk-backed state file is read on the main thread at launch

`MutableStateFlow(loadValidated())` in each `Disk*SettingsRepository` — and in
`DiskRecentDocumentsRepository`, which adds a directory `lstat` per stored row — runs inside
`AppContainer` construction on `JupydroidApplication.onCreate`
(`app/src/main/java/com/jupydroid/app/di/AppContainer.kt`,
`app/src/main/java/com/jupydroid/app/data/disk/DiskRecentDocumentsRepository.kt`): a handful of
small `readText`s plus up to `MAX_RECENTS` stats, all before the first frame, on the thread
startup is measured on. Bounded today and never yet a measured problem — the repair the day a
cold start shows up in a profile is a first read deferred to first collection.

### GAP-071 — The console prompt keeps no undo of its own

`console:undo`/`console:redo` call `editor.undo()`/`editor.redo()` on the prompt cell's editor,
enabled while its `sharedModel.canUndo()`/`canRedo()` says there is something to walk
(`jupyterlab/packages/console-extension/src/index.ts:910-985`) — the prompt's own edit history,
independent of the transcript above it. Ours' prompt is a bare `TextFieldState`
(`app/src/main/java/com/jupydroid/app/ui/console/ConsoleInputRow.kt`) — `edit { }` writes and
nothing is remembered, which is the same shape every `CodeCellField` in the notebook already has,
so the repair is editor-wide rather than console-local: an undo stack on the field both surfaces
then share.

### GAP-072 — The console's prompt has no completion

`completer:invoke-console`/`completer:select-console` are registered *inside* `console-extension`
(`jupyterlab/packages/console-extension/src/index.ts:1519-1560`): `manager.invoke` on the current
console's prompt, and the pick written back through `console:replace-selection`'s
`replaceSelection(text)` (`:1104-1130`). Ours' field already carries the machinery —
`CodeCellField.completionsAt` is a parameter with a `NO_COMPLETIONS` default, which
`ConsoleInputRow` never overrides — so the absence is the wiring alone: nothing hands the prompt
the console session's `complete_request` the way `NotebookCompletion` hands it the notebook's
(`app/src/main/java/com/jupydroid/app/ui/console/`, `ui/editor/NotebookCompletion.kt`).

### GAP-073 — Nothing runs one surface's code inside a console

`console:inject` finds a console by session path — minting one if a running session has none —
and executes the given `code` in it (`jupyterlab/packages/console-extension/src/index.ts:1294-1337`);
`notebook:run-in-console` sends the notebook's selection the same way, through the `ForeignHandler`
and `ConsoleCellExecutor` that land a foreign run in the console's transcript
(`jupyterlab/packages/notebook-extension/src/index.ts:1900-1990`,
`jupyterlab/packages/console/src/foreign.ts`, `cellexecutor.ts`); and the reply's `set_next_input`
payload writes the console's prompt. Ours has none of the three: `ConsoleViewModel.run` runs only
what `submit` typed, `KernelOutputs` maps no reply payload, and `attachConsole` shares the session
but no act reaches across to it (`app/src/main/java/com/jupydroid/app/ui/console/ConsoleViewModel.kt`).

### GAP-074 — The console carries no kernel surface of its own

The reference's console toolbar draws the kernel's name and a live status from contributed
factories, `showBanner` can paint the kernel's banner, `console:change-kernel` runs
`sessionDialogs.selectKernel`, and `console:get-kernel` is the invisible delegate that answers
`session?.kernel` for those widgets
(`jupyterlab/packages/console-extension/src/index.ts:1338-1380`). Ours' console bar carries only
the actions menu (`app/src/main/java/com/jupydroid/app/ui/console/ConsoleScreen.kt`): the session
object already answers `changeKernel` — the notebook's own picker calls it — but nothing on a
console reaches it, and an attached console only follows the notebook's rebound through
`rebindIfMatches`.

### GAP-075 — A console's end frees nothing

`console:shutdown` ends the session and leaves the console open; `console:close-and-shutdown`
asks, then shuts and disposes (`jupyterlab/packages/console-extension/src/index.ts:1186-1255`) —
and `SessionContext.dispose` releases the client connection either way
(`jupyterlab/packages/apputils/src/sessioncontext.tsx:697`). Ours' console surface has no end act
at all — a minted console's kernel can only die from a Running row — and removing the `Console`
entry releases neither socket nor session: `disconnect` is only `NotebookKernel.close`'s, so the
`consoleSession` holder keeps the orphaned session — and its kernel — alive past the tab
(`app/src/main/java/com/jupydroid/app/Navigation.kt:146`). The repair must tell minted from
attached: a naive close-time `disconnect` would close an attached console's *shared* socket out
from under its notebook, which is why this is recorded rather than patched.

### GAP-076 — A console's session cannot be renamed

`console:rename` is `InputDialog.getText` → `session.setName` — a `PATCH api/sessions` `name`
write that the running panel then shows
(`jupyterlab/packages/console-extension/src/index.ts:1257-1292`). Ours' `KernelSessions` has no
name op — `running`, `shutdown`, `ended`, `rebound` are the whole surface — and a `Console` key's
`title` is minted once (`app/src/main/java/com/jupydroid/app/domain/port/KernelSessions.kt`,
`app/src/main/java/com/jupydroid/app/Console.kt`). The repair is a `setName` on the port first;
the act has somewhere to land only after.

### GAP-077 — No console on a subshell

`notebook:create-subshell-console` builds a console whose session talks on a *subshell* the
kernel spawns — so a run lands while the main shell is busy — gated by `commsOverSubshells`
(`jupyterlab/packages/notebook-extension/src/index.ts:5461-5473`,
`jupyterlab/packages/apputils-extension/schema/kernels-settings.json`). Nothing on ours' wire or
in `KernelGateway` names a subshell — `execute` addresses the session's one shell channel — so
the command has nothing to mint on.

### GAP-078 — The restart dialog always asks

`sessionDialogs.restart`'s confirm carries a "Do not ask me again." checkbox that writes
`kernelPreference.skipKernelRestartDialog`, and `sessionDialogs.json`'s setting of the same name
skips the confirm outright
(`jupyterlab/packages/apputils/src/sessioncontext.tsx:1493-1540`,
`jupyterlab/packages/apputils-extension/schema/sessionDialogs.json`). `RestartDialog` has no
checkbox and no preference stands behind it — a restart agreed to once is asked about again every
time.

### GAP-079 — No searchable surface over the running lists

`running:show-modal` opens a `Dialog` holding `SearchableSessions` — a `FilterBox` that flattens
every running section (open tabs, kernels, recently closed, terminals) into one filtered list,
arrow keys walk the matches and `getValue` clicks the active row
(`jupyterlab/packages/running-extension/src/index.ts:190-209`,
`jupyterlab/packages/running/src/index.tsx:999-1206`). Ours' `RunningSidePanel` lists the same
sections but offers no filter, and the shell carries no command palette or quick-open for the
equivalent to hang on
(`app/src/main/java/com/jupydroid/app/ui/shell/RunningSidePanel.kt`). The repair is a filter
field over the two server lists plus the back stack — `RunningPanelState` and `openTabs`
already hold everything it would match.

### GAP-080 — A new document cannot join a running kernel

`running:kernel-new-console` mints a console whose `SessionContext` opens on
`kernelPreference: {id}` — the already-running kernel, not a fresh one — and
`running:kernel-new-notebook` hands `notebook:create-new` the same id
(`jupyterlab/packages/running-extension/src/kernels.tsx:204-247`). The wire half already exists:
`KernelChoice.Running(kernelId)` writes `{"kernel": {"id": …}}` on `POST api/sessions`, and the
notebook's own kernel picker reaches it for a *change*
(`app/src/main/java/com/jupydroid/app/domain/port/KernelChoice.kt`,
`app/src/main/java/com/jupydroid/app/domain/model/KernelPicker.kt`). What nothing offers is the
mint: `mintConsole` and `createNotebook` always open a session on a new kernel, and no Kernel
Sessions row carries "New Console for Kernel" or "New Notebook for Kernel"
(`app/src/main/java/com/jupydroid/app/ui/launcher/LauncherViewModel.kt`,
`app/src/main/java/com/jupydroid/app/ui/shell/RunningSidePanel.kt`). The repair is a per-row
affordance feeding the session's `kernelId` into session start — which `attachConsole` is not:
it binds to the notebook's *own* session, not an arbitrary running kernel.

### GAP-081 — A terminal has no clipboard of its own

`terminal:copy` copies `getSelection()` and `terminal:paste` hands the system clipboard to
`widget.paste` (`jupyterlab/packages/terminal-extension/src/index.ts:623-746`), with
`pasteWithCtrlV` as the keybind reach. Ours holds the machinery and none of the reach:
`TerminalGrid.selectedText`/`GridSelection` turn a *span* into text, but nothing anywhere creates
a selection to pass one — no drag-select, no anchors, no `hasSelection` — and
`TerminalEmulator.paste`/`PasteCommand` (sanitizing, bracketed under DECSET 2004) is callable by
nobody (`app/src/main/java/com/jupydroid/app/terminal/`,
`app/src/main/java/com/jupydroid/app/ui/terminal/`). The repair is two acts, not one wire: a
selection gesture plus a Copy reach (Termux's `textselection/` handles are the reference's own
mobile shape — AUD-007 `T4`'s read), and a Paste row/keybind landing text on `TerminalGateway.send`.

### GAP-082 — The terminal never holds a link

`terminal:copy-link` reads `content.contextMenuLink` — the OSC-8 link under the pointer, tracked
by xterm.js's `addon-web-links` (`jupyterlab/packages/terminal-extension/src/index.ts:693-716`).
Ours parses no OSC 8 at all: `EscapeDispatch`'s OSC handler covers colours, title and clipboard
(`app/src/main/java/com/jupydroid/app/terminal/`), so a `https://…` a program prints is drawn as
text and there is nothing under a pointer for a menu to name. The repair starts in the emulator —
store the link span the way JediTerm's `model/hyperlinks/` does — before any surface can offer
copy/open for it.

### GAP-083 — A folder cannot open a terminal at itself

`terminal:open-folder-in-terminal` is a directory's context-menu item delegating to
`terminal:create-new` with `cwd` (`jupyterlab/packages/terminal-extension/src/index.ts:128-163`).
The wire half already exists here — `TerminalGateway.open(cwd)` takes the directory and the bar's
"+" already passes `browsedDirectory` (`Navigation.kt`) — but `ExplorerScreen`'s long-press menu
on a directory row offers only Rename and Delete. The repair is the row's own item feeding its
`cwd` to `openNewTerminal`.

### GAP-084 — A shell's end goes unmarked

`closeOnExit` (schema default true) closes the tab when the pty exits
(`jupyterlab/packages/terminal/src/widget.ts`); ours keeps the pane standing on the last screen —
defensible on touch — but `TerminalPaneUiState.endedReason` is written on every `Ended` and
*nothing reads it*: no banner, no status, no difference a user can see between a shell at a prompt
and a dead one (`app/src/main/java/com/jupydroid/app/ui/terminal/TerminalPaneViewModel.kt`,
`TerminalPaneScreen.kt`). The repair is the marked end — drawing the reason the way the
reference's auto-close makes the end obvious — before `closeOnExit` itself is even a question.

### GAP-085 — A soft reset lands nowhere

`CSI ! p` is DECSTR, the soft reset: Termux routes `ESC_CSI_EXCLAMATION` + `p` to `reset()`
(`terminal-emulator/TerminalEmulator.java:669-675`) and JediTerm's `'p'` arm calls
`reset(false)` - terminfo's `rs2`, which `reset` and `tput reset` emit, is this sequence. Ours
routes `!` into `EscapeParser`'s `UnsupportedIntermediateByte` state, which consumes the
sequence and does nothing (`app/src/main/java/com/jupydroid/app/terminal/`). The machinery is
not missing - `TerminalScreen.reset` exists and `ESC c` reaches it - only the `!` entry point
is. The repair is one route and one test, not a feature.

### GAP-086 — The cursor's shape never changes

`CSI Ps SP q` is DECSCUSR, cursor shape (block/underline/bar, blinking/steady): Termux keeps
`mCursorStyle` for the renderer to read (`TerminalEmulator.java:871-893`) and JediTerm carries
`CursorShape` end to end. Ours swallows the sequence with every other unclaimed intermediate
byte, and no cursor-shape state exists anywhere - neovim's `guicursor` and readline's vi-mode
send it per mode and ours always draws the block. The repair is a field on the emulator plus
the canvas reading it - the draw half belongs with the view's cursor work.

### GAP-087 — A program's mouse reporting is set and never read

`CSI ? 1000`/`1002`/`1004`/`1006` turn mouse tracking on, and both references own the encoder
that follows: Termux's `sendMouseEvent` (X10-style and SGR replies, behind
`isMouseTrackingActive`) and JediTerm's `emulator/mouse/` package (modes 1000-1003, formats
XTERM/XTERM_EXT/URXVT/SGR). Ours stores the four mode bits (`DecMode`) exactly as upstream -
and nothing reads them: no encoder, no consumer, no route to `TerminalGateway.send`. A TUI
asking for mouse events gets silence. The repair is two halves: the protocol encoder this
package lacks, and the pointer-event reach that feeds it, which is the view task's read.

### GAP-088 — The key table encodes a fraction of itself

`KeyHandler.getCode`'s table is F1-F12, Insert/ForwardDel, SysRq/Break, the numpad with its
application-keypad variants, `transformForModifiers`'s `CSI 1;Psm` modified keys, Shift+Tab's
`ESC [ Z`, Ctrl+Space's NUL - and `getCodeFromTermcap`, the ~40-entry reverse map `+q` DCS
replies consult. JediTerm's `TerminalKeyEncoder` carries the same breadth. Ours'
`terminalKeystroke`/`applyModifierLatches` are byte-exact for what they cover - arrows and
Home/End in both DECCKM states, PageUp/PageDown, Tab, Escape, Ctrl+A-Z, the ctrl/alt latches -
and cover nothing else (`app/src/main/java/com/jupydroid/app/ui/terminal/`). The repair widens
the table; the extra-keys row already lands its taps here, so nothing downstream changes.

### GAP-089 — A batch-redraw hint is ignored

JediTerm's `2026` is synchronized output: `SynchronizedOutput` buffers the stream between the
`CSI ? 2026 h` begin and the `l` end, then flushes - contour's BSU/ESU spec, written so a
full-screen app repaints without tearing. Neither Termux nor ours carries the mode; output
paints as it arrives, which degrades gracefully. The repair is the mode bit plus a bounded
buffer - worth doing only if a TUI's flicker is ever seen, since the ignore is already what
xterm-family terminals without 2026 do.

### GAP-090 — A scrolled-back view has no momentum, wheel, keys or thumb

Termux's `GestureAndScaleRecognizer` turns a fast drag into `onFling` -> `Scroller.fling` with
`computeScroll` decay, `onGenericMotionEvent` scrolls a mouse wheel three rows a notch, its key
dispatch answers Shift+PageUp/PageDown with full-screen scrolls, and `awakenScrollBars` draws a
thumb while the transcript is long (`termux-app/terminal-view/`); JediTerm's `TerminalPanel`
carries the same chrome as a real `JScrollBar` plus wheel. Ours has the drag and nothing else:
`detectTerminalScrollDrag` -> `TerminalPaneViewModel.scrollBy` moves whole rows and release
stops dead, so a 1000-line transcript is a hundred finger strokes away
(`app/src/main/java/com/jupydroid/app/ui/terminal/TerminalPaneScreen.kt`,
`TerminalPaneViewModel.kt`). The repair is the fling driver feeding `scrollBy` - whose return
contract already reserves it ("so a caller driving this from a fling... can stop its own
deceleration cleanly at a clamped boundary") - before the wheel, key and thumb flavours.

### GAP-091 — Reverse video is stored and never drawn

`CSI ? 5 h/l` is DECSET 5: Termux's `isReverseVideo` feeds `TerminalRenderer.render`'s
`reverseVideoHere` XOR on every run plus a foreground-coloured canvas fill, so the whole screen
inverts. Ours stores the same bit in `DecMode.REVERSE_VIDEO` and `TerminalCanvas` never reads
it - `GAP-087`'s stored-never-read class again, and one JediTerm shares (its
`TerminalMode.ReverseVideo` is set by `JediEmulator` and `TerminalPanel` has no branch either).
A program emitting `? 5 h` repaints nothing. The repair is one resolved-colour swap at
`GlyphStyle`'s boundary, read off the emulator's mode bit.

### GAP-092 — A run is drawn at cell pitch even when the font disagrees

Termux's `drawTextRun` measures each run's ink and, when it disagrees with `mFontWidth *
columns` by more than 1%, `canvas.scale`s the run into the cells it was measured for - the
run-boundary condition itself widens on `fontWidthMismatch`
(`termux-app/terminal-view/.../TerminalRenderer.java`). Ours draws every run at the grid's cell
pitch unscaled (`app/src/main/java/com/jupydroid/app/ui/terminal/TerminalCanvas.kt`): a font
whose glyphs do not match the "X" `terminalCellMetrics` measured - ligature-prone or
mis-declared monospace - drifts ink and cells apart. `CodeText` measures honestly today, so
this is a dormant rendering risk rather than a visible wrongness; the repair is the same
per-run measure-and-scale if a font ever lies.

### GAP-093 — A rendered link's destination is stored and never followed

`rendermime:handle-local-link` mounts a click listener on every `<a>` a renderer draws:
`contents.get(path)` then `openOrReveal` in the `defaultRendered` factory, with `setFragment`
for the `#id` and a `kernel` scope delegated to the debugger's source viewer
(`jupyterlab/packages/rendermime-extension/src/index.ts:68-166`). Ours keeps the destination
and loses the reach: `RichInline.Link` carries it, `appendLink` writes it under `RICH_LINK_TAG`,
and `RichInlineStringTest` is the annotation's only reader
(`app/src/main/java/com/jupydroid/app/richtext/RichInlineString.kt`) — a tap on a link in a
rendered markdown cell or an HTML output does nothing. The repair is the consumer: a tap
reading the annotation and routing the destination the way `handle-local-link` does — a
workspace path to `DocumentOpeners.openDocument`, everything else to a browser intent — before
any relative-path rule can exist.

### GAP-094 — A markdown file has no rendered preview

`markdownviewer:open` opens a `.md` in the 'Markdown Preview' factory — the same pipeline a
markdown *cell* renders through, mounted as a document (`markdownviewer-extension/src/index.ts:122-230`).
JupyterLab's file editor still wins a plain tap (`fileeditor-extension` registers `defaultFor:
['markdown']` "so it outranks the defaultRendered viewer"), but the preview exists to be asked
for — and `handle-local-link` lands on it. Ours renders `MarkdownDocument` only inside a
notebook cell; a `.md` in the explorer opens `FileEditorScreen`'s text field, the same as every
other suffix (`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`). `markdownviewer:trust`
has nothing to mark without it. The repair is a rendered view — `RichText` over the file's own
`MarkdownDocument` parse is already the whole pipeline — mounted as a second way to look at the
document, with the settings (`fontFamily`/`fontSize`/`lineWidth`/`hideFrontMatter`) decided
when it is built.

### GAP-095 — An HTML file never renders: no viewer, nothing to trust

`htmlviewer:trust-html` toggles `allow-scripts` on a deliberately weak sandbox — the
`HTMLViewer` iframe carries `allow-same-origin` and nothing else — and the viewer is
`defaultFor: ['html']`, so a `.html` tap in the reference renders the file
(`htmlviewer-extension/src/index.tsx:113-208`). Ours opens it as editable source text: no
iframe, no sandbox, no scripts — `HtmlDocument`'s dropping subset is what `text/html` *outputs*
parse through, and no document ever reaches it (`app/src/main/java/com/jupydroid/app/html/`).
The trust question itself is answered by construction — nothing rendered can execute — so the
repair is the viewer decision first: whether a document surface that draws rendered HTML (under
ADR-0039's no-WebView rule, that means the `HtmlDocument` subset, not an iframe) is wanted at
all; only if it can run something does a trust act exist to gate it.

### GAP-096 — Math never typesets, so there is no equation to copy or scale

`mathjax:clipboard` and `mathjax:scale` sit on a context menu `schema/plugin.json` mounts on a
`.MathJax` node — a node only `MathJaxTypesetter.typeset` creates
(`mathjax-extension/src/index.ts:135-196`). Ours typesets nothing: `MarkdownDocument` has no
math node to map, so `$…$` draws as literal text in cells, and `text/latex` outputs fall
through `RenderedOutput`'s ranking to the bundle-keys line
(`app/src/main/java/com/jupydroid/app/markdown/MarkdownDocument.kt`,
`app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt`). The repair is the
typesetter itself — the `ILatexTypesetter` plug and the `text:latex` renderer are the rendermime
half's read — and the two commands are context-menu rows once a rendered equation exists.

### GAP-097 — An image file opens as editable text, and an edit corrupts it

`imageviewer:*` is eight transform acts — zoom, rotate, flip, invert, reset — over an
`ImageViewer` that `defaultFor` claims `.png`/`.jpeg`/`.gif`/`.bmp`/`.webp`
(`imageviewer-extension/src/index.ts:203-304`). Ours has no image document surface: a `.png`
tap lands in `FileEditorScreen` via `readText`, which decodes the bytes leniently into mojibake
in an *editable* field (`app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceRepository.kt`).
The reference's own text editor shares that failure mode, but there a factory claims the file
first — here nothing stands between the bytes and a field whose save writes the lossy decoding
back over the image. The repair is two steps: refuse or read-only the payloads a text field
cannot represent, then the viewer the eight acts mutate.

### GAP-098 — A CSV/TSV file has no grid viewer, and no line to jump to

`csv:go-to-line`/`tsv:go-to-line` open a number dialog into `CSVViewer.goToLine`, one per
delimiter, beside a `CSVDelimiter` toolbar pick and a `searchprovider.ts` registration that
makes the grid searchable (`csvviewer-extension/src/index.ts:219-412`). Ours opens both as
plain text in `FileEditorScreen` — which *can* land on a line: `reveal: MatchedLine` scrolls a
search result to it (`app/src/main/java/com/jupydroid/app/ui/fileeditor/`). What is missing is
the reach — nothing asks for a line — and the grid viewer itself. The search-registration half
is `GAP-036`'s family. The repair splits the same way: a go-to-line reach is small and could
serve every text file, while the parsed grid is a bigger, separable decision.

### GAP-099 — A mermaid diagram never renders, so its source has nothing to be copied from

`mermaid:copy-source` copies the diagram source a `MERMAID_CODE_CLASS` node keeps, off a
context menu on a rendered `RenderedMermaid` (`mermaid-extension/src/index.ts:80-102`). Ours
has no rendered mermaid anywhere: a ` ```mermaid ` fence in a markdown cell draws as a plain
code block, and `text/vnd.mermaid` outputs fall through `RenderedOutput` like every unlisted
mime (`app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt`). The source *is* on
screen in the fence — but verbatim, not as a diagram with a menu on it. The diagram surface —
the mime factory, the markdown-fence processor, the `.mmd` document widget — is the rendermime
half's read; the copy reach itself would still need `GAP-017`'s selection story even then.

### GAP-100 — A cell's output cannot be viewed twice

`notebook:create-output-view` mounts `ClonedOutputArea` — a second widget over the same cell's
output *model*, split-bottom, live-updating, disposed with the notebook
(`notebook-extension/src/index.ts:1751-1824`, `:5603-5630`). Ours draws outputs inline and only
inline: `CellOutput` is a domain list `CellOutputsView` renders in place, with no
model/surface split a second view could share (`app/src/main/java/com/jupydroid/app/ui/editor/CellOutputsView.kt`).
The repair is the model split first — an output area object a second pane could subscribe to —
before any "view for this output" reach exists.

### GAP-101 — A `text/markdown` output never renders

`markdownRendererFactory` ranks `text/markdown` at 60 — below `text/html`, above `text/latex` —
and `renderMarkdown` parses through `IMarkdownParser`, sanitizes, anchors headings and typesets
(`jupyterlab/packages/rendermime/src/factories.ts:48-50`, `renderers.ts`). Ours has the parser —
`MarkdownDocument.parse` maps commonmark-java's AST onto `RichBlock`, and `MarkdownCell` renders
with it — but `RenderedOutput.RANKED_MIME_TYPES` never routes to it, so a kernel's
`display(Markdown("**bold**"))` falls to the bundle's `text/plain`, which is the markdown source
verbatim (`app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt:67`). The repair is a
rank entry wiring `text/markdown` to `MarkdownDocument.parse` → `RenderedOutput.Document` — the
machinery exists, the routing does not.

### GAP-102 — `image/svg+xml` never draws, trusted or not

The reference's only unsafe standard renderer besides JavaScript: `svgRendererFactory` rank 80,
`safe: false`, and `renderSVG` inlines the sanitized `<svg>` — or draws "Cannot display an
untrusted SVG" where the bundle is not trusted
(`jupyterlab/packages/rendermime/src/factories.ts:58-60`, `renderers.ts`). Ours has no SVG arm at
all: the mime falls through `RANKED_MIME_TYPES` to the bundle's `text/plain` or the keys line
(`app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt`). The repair needs a decode
path Compose can draw — a vector-drawable or coil-svg decoder — and an answer to the trust
question the `safe: false` marks, since an inlined foreign `<svg>` is script-carrying markup.

### GAP-103 — Only PNG of the five image mimes decodes

`imageRendererFactory` covers `image/bmp`, `image/png`, `image/jpeg`, `image/gif` and
`image/webp` at rank 90 — `renderImage` draws an `<img>` and reads width/height/`needs_background`
from the mime's metadata (`jupyterlab/packages/rendermime/src/factories.ts:22-30`, `renderers.ts`).
Ours decodes `image/png` alone (`app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt:82`);
the other four fall to `text/plain` or the mime-keys line though `BitmapFactory` decodes every
one of them. The repair widens the `image/png` arm to the decode-capable set — one decoder, four
mime keys — with the metadata half recorded under `GAP-104`.

### GAP-104 — An output's metadata is dropped at the wire

`OutputModel` keeps the message's `metadata` as observable JSON and serializes it back
(`jupyterlab/packages/rendermime/src/outputmodel.ts:92-213`), and the renderers spend it —
`renderImage`'s width/height, `isolated`/`needs_background`/`unconfined` classes, JSON's
`expanded`/`root`, mermaid's theme overrides, a `fragment` to scroll to. Ours reads `data` and
nothing else: `KernelOutputs.mimeBundle()`
(`app/src/main/java/com/jupydroid/app/data/space/KernelOutputs.kt:101`) drops `metadata` and
`transient` at the port, and `MimeBundle`/`CellOutput` have no carrier for either. The repair
carries `metadata` through the bundle; `transient`/`display_id` belongs to the display-update
read and drops on the same line.

### GAP-105 — Text output never linkifies

`renderTextual` runs `autolink` over every text and error render — `checkWeb` wraps URLs in
`<a>`, `checkPaths` wraps file paths in `data-path` links a file browser or debugger can follow
(`jupyterlab/packages/rendermime/src/renderers.ts:648-920`). Ours draws `RenderedOutput.Text` as
one plain string: a printed URL is text, a traceback's `File "<path>", line 3` is text — and
even an authored markdown link's destination goes nowhere (`GAP-093`). The repair is the
detection — URLs and `file:line` spans becoming `RichInline.Link`s — before the consumer
`GAP-093` names has anything new to follow.

### GAP-106 — ANSI colour never renders, only strips

`renderText` and `renderError` pass their source through `ansiSpan`, which maps SGR codes — the
16, 256 and true-colour forms — to styled spans the sanitizer then permits
(`jupyterlab/packages/rendermime/src/renderers.ts:852-864`). Ours chose the documented fallback:
`withoutAnsiColours` strips the escapes so a stream, a `text/plain` repr or a traceback reads
clean — and monochrome (`app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt`). The
capability was never built: `RenderedOutput.Text` carries a flat `String`, so the repair is a
styled carrier — spans on `RichInline`, or a styled-string output type — before any colour can
land.

### GAP-107 — An embedded image or attachment never draws

Three surfaces feed one machinery: markdown `![alt](src)`, HTML `<img src>`, and the
`attachment:name` scheme `AttachmentsResolver` answers from the cell's own attachment bundle —
all resolved against the workspace by `IResolver.resolveUrl`/`getDownloadUrl`
(`jupyterlab/packages/attachments/src/model.ts:328-390`; Jewel's `ImageRendererExtension` +
`ImageSourceResolver` + Coil3 is the in-idiom precedent). Ours drops all three at the parse:
`MarkdownDocument`'s `else -> null` eats the `Image` node and its alt text, `HtmlInlines`
flattens `<img>` to nothing, and `RichBlock`/`RichInline` have no image node to draw;
`PreservedJson` round-trips `attachments` but no resolver answers the scheme
(`app/src/main/java/com/jupydroid/app/markdown/MarkdownDocument.kt:96-98`). The repair is the
resolver and the model node together — attachment bundle, workspace path, then the decode
surface `GAP-103` already names.

### GAP-108 — A JSON payload prints its mime key, not a tree

`json-extension`'s `RenderedJSON` ranks `application/json` and the three json-lines mimes at 0 —
outranking `text/html` — into a collapsible tree honouring `metadata.expanded` and `root`
(`jupyterlab/packages/json-extension/src/index.tsx:29-136`). Ours keeps the structured value —
`MimeBundle` holds the `JsonObject` — and never looks at it: `RANKED_MIME_TYPES` misses it and
the last resort prints the keys
(`app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt:65`). The repair is a
`RenderedOutput` variant over the `JsonObject` and a lazy expandable tree; `CellOutputsView`
already scrolls, so the payload has somewhere to grow.

### GAP-109 — Media has no surface — pdf, audio, video

`pdf-extension` renders `application/pdf` at rank 100
(`jupyterlab/packages/pdf-extension/src/index.ts:132-133`), the sanitizer's whitelist admits
`<audio>`/`<video>` tags, and the media extensions mount document widgets for each format. Ours
answers each the same way it answers a `.png`: an `application/pdf` bundle prints its keys, the
tags flatten out of `HtmlInlines`'s subset, and `.pdf`/`.mp3`/`.mp4` files open as editable
mojibake — `GAP-097`'s failure mode with no factory to claim the file first
(`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`). The repair is a per-format surface
decision — viewer, external-intent hand-off, or read-only refusal — before the
binary-corrupting edit can be closed for media the way `GAP-097` names it for images.

### GAP-110 — A Vega or Vega-Lite spec never draws

`vega5-extension` ranks its four mimes — `application/vnd.vega.v5+json` and vegalite v3 through
v6 — at 57, above `text/markdown`, and `RenderedVega` draws the spec through vega-embed
(`jupyterlab/packages/vega5-extension/src/index.ts:175-224`). Ours prints the keys; `altair`'s
`chart.show()` lands as `application/vnd.vegalite.v5+json` beside a `text/plain` that says only
`VegaLiteChart(...)`. The repair has no in-idiom precedent — there is no Compose Vega — and
likely resolves to a spec-to-canvas mapper for the common marks or an ADR declining the family.

### GAP-111 — GitHub-flavoured markdown never parses

JupyterLab's marked runs `gfm: true`, and Jewel ships the same surface as extension modules —
`gfm-tables`, `gfm-strikethrough`, `gfm-alerts`, `autolink` — a processor and a renderer
extension per feature (`jupyterlab/packages/markedparser-extension/src/index.ts`;
`intellij-community/platform/jewel/markdown/extensions/`). Ours builds the bare core:
`Parser.builder().build()` with no `commonmark-ext-*` on the classpath
(`app/src/main/java/com/jupydroid/app/markdown/MarkdownDocument.kt:37`,
`gradle/libs.versions.toml`), so a `| a | b |` table is a paragraph of pipe characters, a
`> [!NOTE]` is an ordinary blockquote, `~~del~~` and bare URLs are plain text. The repair adds
the extension dependencies and maps their AST nodes — commonmark's `TableBlock` already has a
home in `RichBlock.Table`, which `HtmlTable` fills today.

### GAP-112 — Stream output never merges, and `\r`/`\b` draw literally

`OutputAreaModel.add` folds a `stream` into the previous output when the `name`s match, and
`processText`/`fixBackspace`/`fixCarriageReturn` interpret `\b` and `\r` so a progress bar
rewrites its own line (`jupyterlab/packages/outputarea/src/model.ts:279-389`, `:545-659`). Ours
appends each `stream` message as its own `CellOutput.Stream` in `EditorUiState.afterOutput`, and
`withoutAnsiColours` touches ANSI escapes only — a `tqdm` loop lands as one output per repaint,
carriage returns and all. The repair is the model-side accumulator — combine by name, then run
the control-character pass — and `afterOutput` is already the one place the list changes.

### GAP-113 — `update_display_data` appends instead of updating

The reference registers every `display_data`/`execute_result` under its `transient.display_id`,
and an `update_display_data` looks the id up and `set()`s the stored bundle in place — every view
of that output redraws with the new data
(`jupyterlab/packages/outputarea/src/widget.ts:804-850`). Ours folds the message into
`CellOutput.DisplayData` at `KernelOutputs.outputFrom`
(`app/src/main/java/com/jupydroid/app/data/space/KernelOutputs.kt:64`) and `afterOutput` appends
it — a `display(..., display_id=)` + `update_display()` pair draws the stale frame above the new
one. The carrier it needs is `GAP-104`'s `transient`; the repair beyond it is an id-indexed
replace in `afterOutput`.

### GAP-114 — An execute result gets no `Out[n]` prompt

Every `execute_result` is wrapped by `OutputPrompt` bound to the model's `executionCount`, so the
left column carries `Out[7]` beside what the cell produced
(`jupyterlab/packages/outputarea/src/widget.ts:895-909`, `tokens.ts`). Ours puts the count on the
input side only — `executionLabel`'s `[n]:` — while the result's own number round-trips through
`execution_count` without ever drawing (`CellOutput.ExecuteResult` holds it;
`app/src/main/java/com/jupydroid/app/ui/editor/CellOutputsView.kt` never reads it). The repair
is a prompt beside each execute-result row; the count already arrives.

### GAP-115 — A runaway cell's output is unbounded

`maxNumberOutputs` (500 by default) wraps a newly added output in `TrimmedOutputs` once the model
is past the cap, and `setMaxNumberOutputs` re-trims on change
(`jupyterlab/packages/outputarea/src/{model,widget}.ts`). Ours has no bound: `afterOutput`
appends for as long as the kernel sends, and `CellOutputsView`'s scroll container grows with it —
`while True: print()` fills the editor with one row per line until the device says otherwise. The
repair is a cap in `afterOutput` — the same place `GAP-112`'s accumulator lives — plus the
"outputs exceeded" surface the cap needs.

### GAP-116 — An execute reply's `payload` never lands

A reply carrying `payload` entries routes each by `source` — `page` opens the pager for `%page`
and `?`-help, `set_next_input` rewrites the cell, `ask_exit`/`edit` round out the list
(`jupyterlab/packages/outputarea/src/widget.ts:_onExecuteReply`). Ours never reads `payload`:
`KernelOutputs.isReplyFor` checks the message's identity and the reply's content is dropped — an
IPython `?`-help or `%page` payload vanishes entirely. The repair reads the payload list off the
reply and routes `page` to a surface — the console transcript being the in-app precedent for
where long help text lives.

### GAP-117 — A `raises-exception` tag never reaches `stop_on_error`

`OutputArea.execute` reads the cell's `tags` metadata and sends `stop_on_error: false` when
`raises-exception` is among them, so a notebook whose cells are expected to raise — a test suite
— runs to the end (`jupyterlab/packages/outputarea/src/widget.ts`; the tag is nbformat's
convention). Ours sends `stop_on_error: true` unconditionally
(`app/src/main/java/com/jupydroid/app/data/space/JupyterMessage.kt:107`), and `runCells` breaks
on the first raise — the tag is preserved by `PreservedJson` but never read. The repair reads
the tag list where the execute request is built.

### GAP-118 — A malformed notebook is never told apart

The server validates every notebook twice — `nbformat.validate` on the read path and again on the
save path, `capture_validation_error` catching the exception so `validate_notebook_model` can put
`"Notebook validation failed: …"` into the response, and a read that cannot parse is renamed
`.invalid` rather than lost (rootfs `jupyter_server/services/contents/manager.py`,
`filemanager.py`; `nbformat/v4/nbbase.py`). Ours has no check: a notebook missing `cells` fails
the decode outright, an output without `output_type` drops silently at `toDomainOutput`, and a
structural break is indistinguishable from an empty document. The repair is a read-side
validation pass whose finding surfaces the way `model["message"]` does.

### GAP-119 — An opened `.ipynb` is a name with no content

`DocumentManager.open` on a notebook path reads the file's bytes through the contents API and
builds the model from them — a document is its content
(`jupyterlab/packages/docmanager/src/manager.ts`). Ours answers a VIEW intent by taking the
display name only: `importTitleFrom` strips the `.ipynb` suffix into a title and `MainActivity`
creates an empty notebook under it, so a shared file's cells never arrive
(`app/src/main/java/com/jupydroid/app/import/ImportTitles.kt`,
`app/src/main/java/com/jupydroid/app/MainActivity.kt`). The repair reads the intent URI's bytes
through `NotebookFile.readNotebook` and stores the result under the imported title.

### GAP-007 — The execution stall is parked, not closed

Reproducible and understood as far as the frames go; the request never reached the kernel, and the
server's own log cannot say why ([CLOSED-005](research/closed-questions.md)). Everything needed to
restart the investigation is in [`research/execution-stall.md`](research/execution-stall.md).

---

## 12. Glossary

In [`architecture/cross-cutting.md`](architecture/cross-cutting.md).
