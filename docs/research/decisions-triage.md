# Migration audit — where each `DECISIONS.md` entry went

`docs/DECISIONS.md` held 228 entries under 7 themes, with no ids, no dates and no status field. This
file is the audit trail for splitting it, so that nothing was dropped silently and the pass is
reviewable. `docs/DECISIONS.md` is now gone, and this file is the record of where its 228 entries went. It is kept
until the code sweep that removes the last citations of it is finished, then deleted with them.

The triage rule, applied entry by entry
([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)):

| If the entry… | Destination |
|---|---|
| states a rule that forbids a future alternative | an `ADR-NNNN` |
| describes a mechanism several files share | `architecture/cross-cutting.md` §8 |
| states where code lives | `architecture/building-blocks.md` §5 |
| is a negative result — we looked, there is nothing there | `research/closed-questions.md` as `CLOSED-NNN` |
| is a measurement with no decision attached | the Context of the ADR that consumed it, else `research/` |
| is an erratum on `PLAN.md` prose | deleted; `PLAN.md` is going away |

## Progress

| Section | Entries | Migrated |
|---|---|---|
| Runtime | 14 | **yes** |
| Protocol and behaviour | 28 | **yes** |
| Editor | 26 | **yes** |
| Architecture and UI | 107 | **yes** |
| Harness | 11 | **yes** |
| Licences | prose | **yes** |
| Closed by measurement | 41 + 5 bullets | **yes** |

## Runtime — 14 entries

| Source entry | Destination |
|---|---|
| Exec model | [ADR-0002](../adr/0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md) |
| `nativeLibraryDir` moves on every app update | folded into ADR-0002 Context — it is the constraint that forces the per-launch relink, not a separate decision |
| musl, not glibc; and arm64 ships, x86_64 does not (yet) | [ADR-0003](../adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md) |
| SIGSYS is a signal to handle, not a verdict | [ADR-0004](../adr/0004-handle-sigsys-and-return.md) |
| DNS cannot be fixed by redirecting a file, so `getaddrinfo()` is interposed instead | [ADR-0005](../adr/0005-interpose-getaddrinfo-rather-than-redirect-resolv-conf.md) |
| busybox is built here, not taken from Alpine, and one flag is why | [ADR-0006](../adr/0006-build-busybox-here-with-suid-compiled-out.md) |
| The Space's shell is named once, in `SHELL`, and three things read it | [ADR-0007](../adr/0007-name-the-spaces-shell-once-in-shell.md) |
| Two Android hazards inside CPython, both worked around, both still true | [ADR-0008](../adr/0008-work-around-two-android-hazards-inside-cpython.md) |
| Kernel logging cannot leak into cell output | [ADR-0009](../adr/0009-keep-kernel-logging-out-of-cell-output.md) |
| A Space is a directory, and its id comes from the scan | [ADR-0010](../adr/0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md) |
| Packages land in the Space's own site, and pip is steered by environment, not flags | [ADR-0011](../adr/0011-install-packages-into-the-spaces-user-site-via-environment.md) |
| The rootfs archive is zstd, because the decoder was the floor | [ADR-0012](../adr/0012-ship-the-rootfs-as-zstd.md) |
| Bytecode ships in the image, `checked-hash` because extraction keeps no mtimes | [ADR-0013](../adr/0013-ship-bytecode-invalidated-by-checked-hash.md) |
| `rfc3987-syntax` is uninstalled from the image, not removed from the lock | [ADR-0014](../adr/0014-uninstall-rfc3987-syntax-from-the-image.md) |

Dates on these ADRs are the date the decision first appeared in `DECISIONS.md`, recovered with
`git log --format=%cs -S'<phrase>' --reverse -- docs/DECISIONS.md`, not the date of the migration.

## Harness — 11 entries

Mostly testing lessons rather than decisions, so most consolidate into one place instead of becoming
eleven small files.

| Source entry | Destination |
|---|---|
| History was rewritten once, before the first push, to take the device out of it | [ADR-0015](../adr/0015-rewrite-history-once-to-take-the-device-out-of-it.md) |
| `ktlintCheck` never checked a `.kt` file, because the plugin was waiting for a plugin AGP 9 made unnecessary | [ADR-0016](../adr/0016-name-every-gate-task-explicitly.md) |
| Evidence is a directory, and `run_flow` used to only ever add to it | [ADR-0017](../adr/0017-prune-stale-evidence-frames-from-a-per-flow-manifest.md) |
| The local gate does not compile `androidTest`, and it went green over a file that would not build | `GAP-008` in `architecture.md` §11 — it records a gap since repaired, not a decision taken |
| A `LazyColumn` composes past its own edge, so "visible" is not "on screen" | `cross-cutting.md` §8.6, *What a test here cannot do* |
| Where the editor is scrolled to is a thing no Maestro flow can assert | same |
| A lazy list is what a two-pane test cannot lay out, and a `Column` was hiding it | same |
| A scroll action inside a `waitUntil` hangs the whole instrumentation | same |
| A layout assertion in dp measures the component's padding by accident | same |
| A one-pane defect can need the tablet, not the phone | same |
| The emulator the suite runs on is 320dp wide **and** density 160 | same |

## Licences — prose, no entries

| Source | Destination |
|---|---|
| the whole section | [ADR-0018](../adr/0018-transcribe-under-permissive-licences-clean-room-otherwise.md), summarised in `cross-cutting.md` §8.4. The clone commands moved to `docs/README.md` with the nine pinned SHAs |

## Closed by measurement — 41 entries and 5 preamble bullets

**The section was misnamed.** Five preamble bullets were genuine negative results; the 41 `###`
entries below them were a mix of testing lessons, Compose gotchas, terminal-emulator implementation
notes and a few real decisions.

**The implementation notes were deleted rather than converted, and that is the rule working rather
than a shortcut.** [ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)
says a shipped mechanism's durable residue is an ADR *or a code comment*. Each of these was checked
against the tree first and found already stated, usually in more detail, in the KDoc of the file it
describes — for example `terminal/RowRing.kt`'s `resize` KDoc carries the transcript-clamp reasoning
in full. Deleting the copy removes a second, staler statement of one fact.

| Source entries | Destination |
|---|---|
| the 5 preamble bullets | `CLOSED-001`–`005` |
| `stty size` answers from a fixed default in this rootfs | `CLOSED-006` |
| terminado's `read_buffer` replays the whole session | `CLOSED-007` |
| A live terminal's count cannot live inside the gateway that opened it | [ADR-0019](../adr/0019-an-activity-counter-lives-where-its-lifetime-matches.md) |
| The ramp lives in `terminal/`, beside `REFERENCE`, not in `theme/EditorPalette` | [ADR-0020](../adr/0020-the-terminals-colour-ramp-lives-in-terminal.md) |
| Restart Kernel stays off the Console for now | `GAP-009` in `architecture.md` §11 — an open gap, not a decision taken |
| A giant multi-line `Text` cannot be asserted on · A `Canvas` carries no semantics of its own · Maestro cannot read the terminal's text · a pixel oracle reads the wrong pane · a seeding driver must not `clearState` · the first `inputText` races the IME connection · `assertNotVisible` on a generic label · a swipe starting on the keyboard · a differential that prints deltas | `cross-cutting.md` §8.6, *What a test here cannot do* |
| An assertion that cannot fail · A caption is a claim · A change to shared chrome owes a re-run · A gate that reads prose | `cross-cutting.md` §8.6, the four standing rules |
| The device gate needed a third kind of evidence, twice over | same — it is the "a green log is only half the evidence" rule |
| `imePadding` pulls a sibling's height budget negative · `RowScope.weight` reads the direct child · `key(state.redraw)` was load-bearing · the IME inset was spent twice | `cross-cutting.md` §8.7, *Compose and Android gotchas* |
| `RowRing.resize` clamps the transcript · a resize's cursor left unclamped · the dispatch guard decides when the alternate screen reflows · `GridCursor.clamped` is the shared tail · the resize-then-switch branch the differential excludes · a re-entry to the alternate screen was already blanking | **deleted** — already stated in `terminal/RowRing.kt`, `terminal/ScreenBuffers.kt` and `terminal/GridCursor.kt` |
| The termcap table stops at what `DeviceControlStringTest` exercises · a malformed hex pair is skipped, not thrown · an anchor moved by a refactor is re-pointed | **deleted** — already stated in `terminal/DeviceControlString.kt` |
| The terminal's own foreground/background/cursor are resolved | **deleted** — already stated in `terminal/TerminalPalette.kt`; the rule is ADR-0020 |
| The reach point is a second icon, not a fourth item in New · the corner scale is transcribed · the plain file editor takes the notebook's breadcrumb · `lineNumbersShown` moves into persisted settings · a Settings screen's toggles are `Switch` rows · the launcher's cards carry their own surface | **deleted** — each already stated in the file it describes (`ui/shell/SearchOptionToggles.kt`, `ui/Breadcrumb.kt`, `domain/model/EditorSettings.kt`, and the launcher's own composables) |

## Protocol and behaviour — 28 entries

Notebook and kernel *behaviour*, almost all of it read out of JupyterLab's `actions.tsx` rather than
designed. Tightly related entries are one ADR each rather than one per entry: five heading entries are
ADR-0033 and four table-of-contents entries are ADR-0037, because splitting them would put one rule
across five files that all cite each other.

| Source entry | Destination |
|---|---|
| nbformat is an open format, so a writer that rebuilds is a shredder | [ADR-0021](../adr/0021-nbformat-is-an-open-format-so-unknown-fields-round-trip.md) |
| Kernels start through sessions · The kernel's working directory is the session's path · The server's own cwd is that tree as well | [ADR-0022](../adr/0022-kernels-start-through-sessions-and-one-tree-is-the-root.md) |
| An unanswered greeting replaces the kernel; it is never asked twice | [ADR-0023](../adr/0023-an-unanswered-greeting-replaces-the-kernel-once.md) |
| The app stops its own server, so the app repairs what that breaks | [ADR-0024](../adr/0024-the-app-stops-its-own-server-so-the-app-repairs-what-that-breaks.md) |
| A kernel is ended by a command, never by closing the document | [ADR-0025](../adr/0025-a-kernel-is-ended-by-a-command-never-by-closing-the-document.md) |
| A terminal is ready when `setup` arrives · The terminal socket is opened by the attach | [ADR-0026](../adr/0026-a-terminal-is-ready-when-setup-arrives.md) |
| Cell semantics come from `NotebookActions` | [ADR-0027](../adr/0027-cell-semantics-come-from-notebookactions.md) |
| There are two undos, and a cell operation never restores text | [ADR-0028](../adr/0028-there-are-two-undos-and-a-cell-operation-never-restores-text.md) |
| A selection is a contiguous run | [ADR-0029](../adr/0029-a-selection-is-a-contiguous-run.md) |
| A run has a range, and the range is not the only thing that separates the run commands | [ADR-0030](../adr/0030-a-run-has-a-range-and-the-command-owns-the-caret.md) |
| Splitting is the one cell operation that reads the editor | [ADR-0031](../adr/0031-splitting-reads-the-editor-so-a-cell-owns-one-text-state.md) |
| A raw cell is an editor and nothing else | [ADR-0032](../adr/0032-a-raw-cell-is-an-editor-and-nothing-else.md) |
| A heading owns a section · A heading level is a change of type · An inserted heading takes the section's level · A collapsed heading is its whole section, for four commands · The three folds are a category | [ADR-0033](../adr/0033-a-heading-owns-a-section-derived-not-stored.md) |
| Adopting a rewritten source belongs to the cell | [ADR-0034](../adr/0034-adopting-a-rewrite-belongs-to-the-cell-reporting-to-the-field.md) |
| Unrendered is not the same as asked for, and only a tap asks | [ADR-0035](../adr/0035-unrendered-is-not-the-same-as-asked-for.md) |
| view toggles, and two defaults kept against the reference on purpose | [ADR-0036](../adr/0036-three-view-toggles-and-two-defaults-kept-against-the-reference.md) |
| The table of contents is a panel where there is room · folds its own tree · Heading numbers go in the document · A heading a cell printed is an entry | [ADR-0037](../adr/0037-the-table-of-contents-is-two-presentations-of-one-derived-tree.md) |
| A pty echoes, so "type X, read X back" proves nothing | `cross-cutting.md` §8.6, *What a test here cannot do* |
| One `jupyter_server` per instrumentation run | same |

## Editor — 26 entries

The palette cluster is nine entries about one thing and becomes three ADRs; the search cluster is eight
and becomes two. One entry was found **superseded** and is recorded as such rather than carried.

| Source entry | Destination |
|---|---|
| The lexer is generated, never hand-written | [ADR-0038](../adr/0038-the-lexer-is-generated-never-hand-written.md) |
| A Compose-native cell editor, not a WebView · Markdown renders natively — no WebView, no HTML · ANSI handling today is a stripper, not an emulator | [ADR-0039](../adr/0039-a-compose-native-cell-editor-not-a-webview.md) — the third becomes its Consequences: there are two ANSI paths on purpose, a regex stripper for tracebacks and a full VT emulator for the terminal; amended by [ADR-0083](../adr/0083-output-colour-reuses-the-terminals-sgr-walk.md), which colours outputs through the emulator's SGR walk |
| The presentation reference is VS Code; the behaviour reference stays JupyterLab | [ADR-0040](../adr/0040-the-presentation-reference-is-vs-code-behaviour-stays-jupyterlab.md) |
| The default VS Code theme is 2026 Dark / 2026 Light, not Dark Modern · The palette swap must be measured against the **cell** · Parity with the 2026 themes outranks the contrast floor | [ADR-0041](../adr/0041-parity-with-the-resolved-themes-outranks-the-contrast-floor.md) |
| A search match is a wash · A link takes the string colour · The palette states its colours · One editor font, whatever the cell holds | [ADR-0042](../adr/0042-a-search-match-is-a-wash-and-the-palette-states-its-colours.md) |
| **A match is drawn with its own foreground** | **superseded** by ADR-0042. It decided an opaque fill with a restated foreground — IntelliJ's backgrounds, JupyterLab's structure — and the wash replaced it. Its measurement (a keyword at 3.16:1 inside a match) is kept as ADR-0042's Context, because it is why the superseded decision was reasonable at the time |
| A search query is a regular expression · Typing a query finds and counts · A match in rendered prose is counted and not lit · A replacement is two steps · The `output` filter searches what it can also show | [ADR-0043](../adr/0043-every-search-is-a-regex-and-typing-finds-without-moving.md) |
| The find bar takes the notebook's bar · Reaching a cell reveals it - in two strengths · A `LazyColumn` needs the IME inset | [ADR-0044](../adr/0044-reaching-a-cell-reveals-it-in-two-strengths.md) |
| A shell is reached through a path named `sh` | [ADR-0045](../adr/0045-a-shell-is-reached-through-a-path-named-sh.md) |
| Staging needs no emulation, and patchelf never cared about the target architecture | `CLOSED-008` |
| A pseudoterminal works on the device | `CLOSED-009` |
| An emulator with a hardware keyboard cannot prove a phone's layout (and `wm size` is not a geometry) · A one-second `assembleDebug` looks exactly like a stale APK | `cross-cutting.md` §8.6, *What a test here cannot do* |

## Architecture and UI — 107 entries

The largest section, and the one where clustering matters most. Twenty ADRs, two gaps, five testing limits,
one supersession, and the rest deleted as duplicates of code comments — each verified against the tree first.

| Source entries | Destination |
|---|---|
| The two-pane layout is `ListDetailSceneStrategy` · The breakpoints, so 840dp is not a magic number · Window width is a parameter | [ADR-0047](../adr/0047-two-panes-start-at-expanded-and-the-scene-strategy-keeps-every-entry.md) |
| Touch rules, and the two that cost something to learn · A touch target is reserved space | [ADR-0048](../adr/0048-touch-rules-measured-on-device.md) |
| A gap has a name · Material's type scale is taken whole · Code is drawn at one size | [ADR-0049](../adr/0049-a-gap-has-a-name-and-the-type-scale-is-taken-whole.md) |
| Reads may hit the repository · `notebook:trust` is inert here | [ADR-0050](../adr/0050-reads-hit-the-repository-server-side-algorithms-do-not.md) |
| An export is named by the server · The export menu offers what was measured · The format list is fetched when the picker opens · An export lands beside the notebook · A file leaves the app through a grant | [ADR-0051](../adr/0051-an-export-is-named-by-the-server-and-the-menu-offers-what-was-measured.md) |
| Kernel history is the kernel's · access is off by default · the history keys go on the symbol row · a history walk belongs to the cell it started in | [ADR-0052](../adr/0052-kernel-history-is-the-kernels-and-a-walk-belongs-to-its-cell.md) |
| A cell at rest carries no mark · The editor's border is the cell's boundary · The prompt is the reference's · The document's name has a row of its own · The heading caret belongs to the heading | [ADR-0053](../adr/0053-a-cell-at-rest-carries-no-mark-and-the-editor-border-is-the-boundary.md) |
| The side panel reads a model the document publishes · The table of contents is a sidebar panel of the shell · 840dp stays, and the rail is what settles it | [ADR-0054](../adr/0054-the-shell-has-one-side-area-and-the-activity-bar-sits-beside-it.md) |
| **One pane grows no navigation bar (P2o-4)** | **superseded** by ADR-0055. Its reasoning — two destinations is below Material's floor — is kept as that ADR's Context, because it is why the earlier decision was reasonable. What overturned it was a measured gap: the 600–840dp band would have had neither control |
| The bar is drawn wherever the shell draws one pane · two destinations · drawn over the panel · items carry no visible text · does not collapse · a one-pane window keeps a panel · Back reaches the start destination · the bar's arrangement | [ADR-0055](../adr/0055-the-bottom-bar-is-drawn-wherever-the-shell-draws-one-pane.md) |
| The bar names the active cell's type · The overflow is the toolbar's popup opener · The kernel indicator is right of Find | [ADR-0056](../adr/0056-the-notebooks-action-row-is-a-reactive-toolbar.md) |
| The cell keeps its header strip, and VS Code's run gutter is declined | [ADR-0057](../adr/0057-the-cell-keeps-its-header-strip-and-the-run-gutter-is-declined.md) |
| The chrome ramp is VS Code's · The notebook's breadcrumb is ancestors only | [ADR-0058](../adr/0058-the-breadcrumb-is-ancestors-only-and-the-chrome-ramp-is-vs-codes.md) |
| Many documents live in the stack · The strip owns the name · The Launcher is a document-area destination | [ADR-0059](../adr/0059-many-documents-live-in-the-stack-and-a-tab-is-an-ordering.md) |
| The glob language is transcribed · The three things a workspace walk declines · A notebook is searched as its cells · The fourth kind of file · VS Code searches a closed notebook as JSON | [ADR-0060](../adr/0060-workspace-search-transcribes-the-glob-language-and-declines-three-files.md) |
| Replace across files goes through the editor's model · The open-document registry is one document · A replace-all empties the tree · An undo brings the results back · The undo sits beside the report · `truncateValue` is declined · `find_replace` reads as refresh · Replace All is drawn disabled | [ADR-0061](../adr/0061-replace-across-files-goes-through-the-open-documents-model.md) |
| The activity bar's order takes both references · A one-pane window draws the browser | [ADR-0062](../adr/0062-the-activity-bars-order-takes-both-references.md) |
| Kernel choice is `selectKernel`'s dialog and a `PATCH` | [ADR-0063](../adr/0063-kernel-choice-is-a-dialog-and-a-patch-on-the-session.md) |
| A file's name is its identity | [ADR-0064](../adr/0064-a-files-name-is-its-identity.md) |
| The kernel's name goes before its status does · Active kernel states are solid · The indicator can read *Idle* for a stopped kernel | [ADR-0065](../adr/0065-the-kernel-indicator-shows-status-first-and-solid-means-active.md) |
| A transcription of an algorithm is diffed against it, not reviewed against it | [ADR-0046](../adr/0046-a-transcription-is-diffed-against-its-source-not-reviewed.md) |
| Cell outputs are not searched from the workspace panel | `GAP-001` |
| The indicator can read *Idle* — the server-generation signal | `GAP-010` |
| The running panel is JupyterLab's "Sessions and Tabs" | **retired** — `GAP-011`'s premise predated the panel: `KernelSessions.running()`/`shutdown()` and the Kernel Sessions section shipped under it. What is still declined is stated in `app/src/main/java/com/jupydroid/app/ui/shell/RunningSidePanel.kt` and verdicted in `research/parity-ledger.md` §AUD-006 |
| A dialog row can draw fully and still be unreachable — the `AlertDialog` scroll range and `minimumInteractiveComponentSize` inflating semantics bounds | `cross-cutting.md` §8.7, *Compose and Android gotchas* |
| Maestro text selectors are `Regex.matches`, full-string | `cross-cutting.md` §8.6, *What a test here cannot do* |
| One object owns the Space's server · `SearchQuery` lives in `domain/model` | `architecture/building-blocks.md` §5.3, which already states both |
| A listing in flight could overwrite where the browser had just gone · A `LaunchedEffect` must not be keyed on what it changes | **deleted** — already stated in `Navigation.kt` |
| The option toggles are drawn as type · A toggle's on state is a wash · A toggle is walked at once · The replace toggle · The search panel is one scrolling surface · A glob heading is drawn and cleared · The details open focused · Opening a result lands on the match · The cell is named in the row's label | **deleted** — already stated in `ui/shell/SearchSidePanel.kt`, `ui/shell/SearchScopeBoxes.kt`, `ui/shell/SearchResults.kt` (lines 202-216 carry the rule with its `searchResultsView.ts` citation and the one-based/zero-based note) and `domain/model/MatchedFile.kt` |
| the 26-entry terminal-emulator cluster — storage and parser are two steps · a selection takes a wide character whole · `TerminalGrid` is three classes · `RowText` is the seam · `decodeEffect` reads two bits · `CharacterWidthRanges` is packed · DEC Special Graphics `d` and `h` · SGR is P4f-2's · `58` and `59` are parsed and thrown away · a final byte is swallowed · the differential's state dump · the screen is three classes · two `ESC` sequences · two P4f-3b behaviours · an erase blanks to the colours in force · a mode "save" · the scrollback belongs to the main screen · backspace tests `>` · exactly two C0 controls · `RefScreen` reports what the notices carried · the colour defaults are a constructor argument · `OSC 104` is fixed · a wrapped clipboard payload · a part draws its own border · a palette-consumer gate that reads comments | **deleted** — each verified present, usually in more detail, in the KDoc of the file it describes: `terminal/EscapeParser.kt`, `terminal/GridReflow.kt`, `terminal/TerminalScreen.kt`, `terminal/ScreenBuffers.kt`, `terminal/SelectiveErasing.kt`, `terminal/SavedCursor.kt`, `terminal/TerminalPalette.kt`, `terminal/TerminalColors.kt`, `terminal/OperatingSystemCommand.kt`, `terminal/CharacterWidthRanges.kt`, `terminal/TerminalRow.kt`, `terminal/LineDrawingCharset.kt` and `test/.../CellStyleTest.kt`. The gate-reads-comments entry is already `cross-cutting.md` §8.6's fourth standing rule |
