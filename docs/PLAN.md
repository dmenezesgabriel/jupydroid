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
| Runtime | rootfs installs, server starts, kernels start and **reattach**; numpy/scipy import; the server stops when it is idle or the app is left, and comes back on the next run; a kernel can be ended on purpose from the kernel menu |
| Notebook | **kernel history** walked into the cell being edited; **find and replace** across the document, with the reference's own filters; all three `cell_type`s - code, markdown and **raw**; cells editable, syntax-highlighted, executed; outputs (stream, error+ANSI, PNG, `text/html`, tables) render natively; collapse - of a cell's source, of its outputs, of the **section under a heading**, and outputs can be **bounded to a scrollable strip**; a contiguous run of cells can be marked and every cell **and run** command acts on it — ranged runs, the compound restarts and clear-all included; nbformat round-trip preserves unknown fields |
| Editor | `BasicTextField` + generated ANTLR lexer, auto-indent, bracket match (**toggleable, persisted, universal** across a cell and the plain file editor), line numbers (**toggleable**), kernel-backed completion, symbol row |
| Files | explorer with create / rename / delete / browse, Recents as a section, **export** beside the document and out through a share sheet, polling on the reference's own cadence (`packages/filebrowser/src/model.ts:26`, `DEFAULT_REFRESH_INTERVAL = 10000`, `standby: 'when-hidden'` at `:106`) |
| Shell | two panes ≥ 840dp on `ListDetailSceneStrategy`, IntelliJ-derived palette, Material's own type scale, per-notebook ViewModel scoping |
| Tests | 255 instrumented, 1261 unit, 99 Maestro drivers over 113 flows - 94 of them in the sweep, the other five being tools rather than checks (`NOT A SUITE FLOW`) - `run-flows-sharded.sh` **balanced by measured time** across both devices, gradle + script gates |

**Not built at all:** the Space system, a terminal, LSP, the debugger, the console, and a
**settings screen** - the five persisted editor settings live in the notebook's View menu until P6c
gives them one. Two entries this line used to carry are gone rather than outstanding: **export**
shipped in P2j, and **trust** is a decision rather than a gap (`docs/DECISIONS.md` names the
trigger that would make it real).

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

P1f is done too, and the plan had it wrong: **one** driver called bare `adb`, not twenty-nine.
The other twenty-eight hits were the `ADB="${ADB:-$(command -v adb …)}"` fallback, which is the
fix rather than the offence. `check_driver_isolation.py` now refuses the call, so the counting
does not have to be repeated.

**P1d and P1e have shipped too, and P1 is closed.** Both departed from what this file said, and
both departures are `docs/DECISIONS.md` now rather than a line here:

- **P1d shipped the `DELETE` and not the trigger this plan named.** "When a document closes" is
  wrong twice over — JupyterLab's `kernelShutdown` defaults to **false**, and shutting down on
  close would have deleted Step 17's reattach, which is a shipped, evidenced feature. So the wire
  is reached from `notebook:shutdown-kernel` and `notebook:close-and-shutdown` instead, and both
  behaviours are green on one build: 44 after a leave, `NameError` after a shutdown
  (`evidence/p1d-shutdown-kernel/`).
- **P1e cost a toolchain, not just a BOM.** `adaptive-navigation3:1.3.0` refuses to be consumed
  below **compileSdk 37 and AGP 9.1.0**, and AGP 9.1.1 wants **Gradle 9.3.1** — so all three moved
  with it (`targetSdk` did not). `ListDetailSceneStrategy` does keep both panes as separate
  `NavEntry`s, which is what the gate turned on, so both hand-written files are gone. Its default
  `BackNavigationBehavior` had to be overridden: with two panes expanded it popped the whole back
  stack and left the app (`evidence/p1e-list-detail-scene/`).

---

# P2 — notebook parity

The command surface is the specification. Semantics come from `packages/notebook/src/actions.tsx`,
read per command — three of them have already contradicted the obvious answer
(`docs/DECISIONS.md`), so none is assumed.

**Built today:** run, run-all, run-all-above, run-all-below, interrupt, restart, restart-run-all,
restart-clear-output, restart-and-run-to-selected, insert above/below, change type
(code ↔ markdown ↔ raw), merge above/below, merge-cells, split at the cursor, move up/down, duplicate,
delete, clear-outputs, clear-all-cell-outputs, cut/copy/paste, hide-cell-code, hide-cell-outputs,
select-all, deselect-all, extend-marked-cells-{above,below,top,bottom}, and undo/redo of every one
of them — each acting on the marked run of cells rather than on one.

**P2a has shipped.** Undo and redo of cell operations are on the notebook's app bar, green on both
devices from one build (`evidence/p2a-undo-cell-operation/`). What it settled — the two undos, the
rule that a cell operation never restores text, and the one adaptation (Clear Outputs) — is
`docs/DECISIONS.md` rather than a line here. It cost one refactor the plan did not name: detekt's
`TooManyFunctions` again, and again the answer was the object that wanted to exist, so the cell
menu and its history are `NotebookCellEdits` beside `NotebookKernel`.

**P2b has shipped, and this plan had it half-built already.** "Split and directional merge" was one
sub-step too wide: `merge-cell-above` and `merge-cell-below` were already in the menu and already
cited against `actions.tsx:345`, so what was left was `notebook:split-cell-at-cursor` alone. It took
three commits rather than one, and the split was decided before any code was written: the text
state came out of `CodeCellField` first as a pure refactor with the existing tests as its gate,
then the command, then markdown. What it settled - the two counter-intuitive rules, the one
exception P2a's undo rule needed, and why a field must be *told* its text was rewritten rather than
left to notice - is `docs/DECISIONS.md`. It cost three harness findings that had been invisible
while the cell menu was thirteen rows long, and one real defect the markdown half turned up: the
symbol row was **inert in a markdown cell**, because that field never registered itself with
`FocusedCellInput`. All four are recorded there.

**P2c has shipped, and this plan had its size wrong.** "The prerequisite for every batch command
after it" understated what reading `actions.tsx` settled: **the operand of almost every cell
command there is already `isSelectedOrActive`**, so a selection the existing menu ignored would have
been a bar reading `3 selected` over a Delete that removed one. Changing the operand was part of
P2c, not a follow-on, and it took three commits: the contiguous model (one nullable `anchorCellId`,
because the reference's selection is a run with two ends), the mode on screen, and the operand.
What it settled — contiguity, edit-deselects, which three operations keep the run, merge losing its
direction, the `selectAll` departure and the two touch adaptations — is `docs/DECISIONS.md`.

It cost two findings worth having and one defect of its own. The defect was the bar: four reach
arrows left the count wrapped to three lines at 360dp, so all four `extend-marked-cells-*` are
overflow rows and only Select all and the overflow are on it. The findings are both harness ones
and both in `docs/DECISIONS.md`: a Maestro assertion naming a prefix of a node cannot fail, and a
document's title is the wrong handle on a tablet where the file list shows the same name.

**P2d has shipped, in six sub-steps, and it closed both gaps this section was carrying.**
`run-all-above` / `run-all-below` / `restart-clear-output` / `restart-and-run-to-selected` and `run`
over the marked run are all in, plus `clear-all-cell-outputs`, which the plan never named and which
`restart-clear-output` is built on. What it settled is `docs/DECISIONS.md`.

The **`clearOutputs` unhide** gap went with the command that shares its rule rather than on its own,
and reading `clearExecution` for it turned up two more effects (`packages/cells/src/model.ts:706-714`):
the run is dropped as well as the outputs — `executionState = 'idle'` plus the deleted `execution`
metadata, which is where our stripe and *"ran in"* live — so `NotebookRevision` grew a fourth field
to put it back. The **markdown-render** gap is P2d-6.

Three costs are worth naming here, because none was visible from the plan:

- **A notebook had no active cell until something was tapped.** The two ranged runs are
  head-relative, so both were greyed on a freshly opened notebook with nothing to say why. The
  reference's `activeCellIndex` clamps to 0 on load (`widget.ts:1965-1974`); ours does now.
- **Adding Run to the selection bar put two identical `PlayArrow` triangles on one screen.**
  Introduced and fixed inside P2d-3: the kernel bar goes with the notebook's own bar while a run is
  marked, which is P2c's "one surface acts on the run" followed through.
- **Markdown rendered-ness had to leave the composable** before a range run could render it
  (`cellexecutor.ts`, `case 'markdown'`), which is P2d-6 and closes the second gap this section
  carried.

**P2e has shipped, and "small" was right about the command and wrong about everything else.**
`change-cell-to-raw` cost the two lines it looked like it would; what it actually bought was a
notebook that **could not be opened at all** - `CellFile.toDomain` threw on `cell_type: "raw"` and
the editor showed the exception as the whole screen, for a file JupyterLab opens without comment
(`evidence/p2e-1-raw-cells/before/`). What raw settled - an editor with no second state, no Run
control, and `outputs`/`execution_count` **forbidden** rather than merely absent - is
`docs/DECISIONS.md`.

Three costs the plan did not name, each recorded there rather than here:

- **The change-type row had to become two.** "The type this cell is not" is a complete answer only
  while there are two types. The reference has a command per type, so each cell offers both; a code
  cell's menu is eighteen rows now.
- **P2e-3 was a parity gap sitting inside the function P2e-2 edited.** A cell that becomes markdown
  is left showing its source (`actions.tsx:3308-3312`), and that `if` is outside the type-changed
  branch, so it reaches cells that were already markdown.
- **And fixing it exposed one of ours.** `MarkdownCellField` took the caret for having been composed
  at all, so a run changed to markdown put the soft keyboard up over a notebook nobody was typing
  in. Both of the flow's assertions were green; the screenshot is what said otherwise. *Unrendered*
  and *asked for* are separate state now.

One harness defect went with them, and it had been silent since the ranged runs arrived: the gate on
post-Run assertions held `"Run All"` while the bar reads **"Run all cells"**, so it had never fired
on any of the seven flows that tap it.

**P2f has shipped, in five sub-steps, and its central decision is one the reference makes about its
own code.** All eleven commands are in - `toggle-heading-collapse`, `collapse-all-headings`,
`expand-all-headings`, `change-cell-to-heading-1..6` and `insert-heading-{above,below}` - plus the
four places a collapsed heading behaves as its whole section. What each settled is
`docs/DECISIONS.md`; the evidence is `evidence/p2f-1-heading-collapse/` through
`evidence/p2f-5-collapsed-section/`.

**What is hidden is derived, never stored**, and that is `_refreshCollapsedHeadingVisibility`'s own
description of itself - *"Recompute hidden cells from the current collapsed heading structure"*
(`widget.ts:916-940`), called after `moveCells` reorders (`:462`). Deriving it instead of re-running
it made every insert, delete, move and undo correct without any of them knowing the feature exists,
and gave the `localCollapsed` rule for free.

Four costs the plan did not name, each recorded in `docs/DECISIONS.md` rather than here:

- **A menu row that opened a picker closed it in the same frame.** `CellActionSurface` dismisses
  after *every* choice, so the second step opened and vanished. Found by the flow.
- **A replaced field adopted nothing.** `change-cell-to-heading` is the first command that rewrites
  a cell's source *and* changes its type, so a `CodeCellField` became a `MarkdownCellField` over one
  `TextFieldState` and the adoption counter - seeded per field - started already up to date. The
  model held `## title` and the editor showed the text from before. Adoption belongs to the cell now
  and reporting to the field. **Both of the flow's earlier assertions were green; the screenshot is
  what said otherwise**, for the second time in this project.
- **`stopApp` is a force-stop, so a flow that kills the app inside the 500 ms save debounce tests
  the timer rather than the feature.** The fix is what a user does: leave the document first.
- **`CellAction.Fold` is a category detekt asked for three times.** The three collapses share both
  things the dispatch cares about, and `CellFolds` had already named it in prose.

**P2g has shipped, in four sub-steps, and two of them are defaults kept against the reference
rather than inherited from it.** `toggle-all-cell-line-numbers`, `enable/disable/toggle-output-scrolling`
and `toggle-autoclosing-brackets-universal` are all in; `toggle-virtual-scrollbar` (Show Minimap)
moved to the deferred list below, its own sub-step. What each settled — the gutter shown by
default against the reference's `lineNumbers: false`, `'auto'` read as unscrolled, the all-of
operand `toggle-output-scrolling` reads where every other fold reads the head, and
auto-closing brackets on by default and universal across a cell and the plain file editor because
both already share `PythonTypingRules` — is `docs/DECISIONS.md`. The evidence is
`evidence/p2g-1-line-numbers/` through `evidence/p2g-3-autoclosing-brackets/`.

**The auto-closing-brackets toggle is the app's first persisted, app-wide setting**, and it forced
the one piece of infrastructure this phase actually added: `EditorSettingsRepository`, disk-backed
like `RecentDocumentsRepository` beside it, with `AppContainer` defaulting to a fake like every
other port. P6c grows it rather than replacing it.

One cost the plan did not name: **a persisted setting is state no flow's `reset_notebooks` had
ever had to clear**, because every reset before it emptied *documents* — the workspace, the
recents list — and this is scoped to the *app*. A flow that left the toggle off would leave it off
for every flow run after it on the same device. `reset_notebooks` clears
`editor-settings.json` now, beside `recent-documents.json`, on the same repeatability reasoning
`check_driver_isolation.py` already enforces for notebooks.

**P2h has shipped, in six sub-steps, and two of them departed from what this file said.**
`toc:show-panel` is in, on two surfaces: a `TableOfContentsPane` beside the notebook where the
**editor** is at least 840dp wide, and the `TableOfContentsPanel` menu-or-sheet everywhere else.
Tapping a heading jumps the notebook to that cell; the pane stays open across the jump, which is
the whole of what it buys over the overlay. `toc:toggle-collapse` is in too - an entry folds the
subtree it owns, one control does the whole tree, and neither touches the notebook's own section
folds. `toc:display-numbering` and `toc:display-h1-numbering` are in as well, and they number the
**notebook's own headings** as well as the panel's rows, which is what the reference's own label
promises, and `toc:display-outputs-numbering` lists the headings a code cell **printed** beside
the ones its neighbours wrote. That is the whole command surface this row named. Evidence is
`evidence/p2h-2-table-of-contents/` through `evidence/p2h-6-output-headings/`.

**The third pane this file promised does not exist, and reading the library is what settled it.**
`ListDetailSceneStrategy.extraPane()` is a separate `NavEntry`, and every entry here owns its own
`ViewModelStore` — so it could reach neither the notebook's `EditorViewModel` for the headings nor
its `LazyListState` to scroll, and would have forced per-document state up into a shared owner. The
pane is a column inside the editor's own scene instead. Two more things were measured rather than
assumed and are `docs/DECISIONS.md` now: three partitions only arrive at 1200dp+ (and at two, an
extra pane displaces the **explorer**), and the width that decides is the *editor's* rather than
the window's — in two panes an 840dp window leaves the editor about 456dp.

Five costs the plan did not name, each recorded there rather than here:

- **A ToC row and the heading it names are the same words**, so a flow meaning "the notebook moved"
  cannot say so by naming a heading — the pane is still showing that text. It cost two red runs,
  and the notebook's *last* cell is what settles both halves of the claim.
- **`NARROW WINDOW` is a new driver rule**, the mirror of `WIDE WINDOW`: a flow asserting the
  compact-width adaptation is asking the wrong question on a tablet rather than failing there.
- **Two of P2h-4's rules are narrowings of the reference rather than copies of it**, and both come
  from the same place: a control that cannot change anything is one this app does not draw. Collapse
  All marks only the entries that own something, and its control is absent entirely in a notebook
  with no nesting.
- **P2h-5 was two commits, because its label made a promise the panel alone could not keep.**
  `numberHeaders` numbers the notebook's rendered headings too, so the counter shipped first with no
  surface and the toggles only once both renderings existed. Two pieces of the reference's own
  arithmetic turned out to be unobservable here and were dropped on the evidence of a mutation test
  rather than kept for symmetry; `maximalDepth` is deliberately still not implemented.
- **P2h-6 needed no kernel, where this file said it would.** nbformat stores a cell's outputs, so a
  fixture carries the `text/html` the entry is read from and the flow is a width rule rather than an
  ABI one. It also forced the first entry identity that is not a cell id, since one code cell can
  print several headings.

**P2i has shipped, in eight sub-steps, and reading `searchprovider.ts` re-cut it before any code
was written.** All six of the reference's `documentsearch:*` commands are in - `start`,
`startWithReplace`, `highlightNext`, `highlightPrevious`, `end` and `toggleSearchInSelection` -
over a query model and a hit list that shipped first with no surface (P2i-1, P2i-2), which is the
shape P2h-5a/P2h-5b already used. What each settled is `docs/DECISIONS.md`; the evidence is
`evidence/p2i-3-find/` through `evidence/p2i-7-search-filters/`.

The re-cut is the one worth naming here: **typing a query finds and counts, and only the arrows
move the notebook.** `startQuery` passes `scroll: false` and `activateNewMatch` returns on its first
line when it sees that (`:389-401`, `:604-610`), so the reveal - scroll, unhide, unrender, expand a
collapsed parent - belongs entirely to `highlightNext` / `highlightPrevious`. That is P2i-4's seam
rather than a convenience: a box that scrolled the document on every keystroke would move the
notebook out from under whoever was typing.

Eight costs the plan did not name, each recorded in `docs/DECISIONS.md` rather than here:

- **The reference's own empty-match guard cannot be transcribed.** Java's multiline `^` refuses the
  empty subject where JavaScript's accepts it, so `ret.test('')` read literally would let a bare `^`
  through and report a zero-width hit at every line start.
- **IntelliJ's search background fails this app's own contrast floor**, at 3.16:1 under
  `DEFAULT_KEYWORD` on dark - and every background dark enough to fix it measures 1.08:1 against the
  cell. A match states its own foreground now, which is what JupyterLab does.
- **U6's defect came back through a door nothing before could open.** The find box holds the caret
  while the notebook is in *command* mode, so the kernel bar would sit under a soft keyboard;
  `editorBottomBar` drops it, the way it already drops it for a marked run.
- **The cell list had no `imePadding`** once P2i-3 took the bottom bar away, so the notebook was
  drawing behind the keyboard and `visibleItemsInfo` was answering about a viewport nobody could
  see. With it, **`inViewport` alone is still not the reference's guard** - `activateNewMatch`
  follows it with `revealPosition`.
- **A jump to a heading and a jump to a match are two helpers, not one.** P2i-4b corrected P2h's
  reading: an ordinary jump expands sections and nothing else, where landing on a *match* also
  opens a collapsed source and unrenders prose. The reference draws that line in a comment - a ToC
  jump forces command mode *"to avoid entering Markdown cell in edit mode"*
  (`packages/notebook/src/toc.ts:681-684`) - and one helper for both was green against every
  assertion while putting `## Part A` on screen as an editor.
- **The three query options are behind the bar's overflow rather than on it.** The reference puts
  them on the box as toggle buttons (`searchview.tsx:185-210`), which with a close, a counter and
  two arrows is more than 320dp holds. Green first run on the **emulator at 320dp**, which is the
  width that decides where they go, so that is the evidence kept.
- **detekt split the collaborator in two and the reference agrees.** `ISearchProvider` declares
  finding and replacing as two groups and only one of them writes, so `NotebookSearch` /
  `NotebookReplace` is its shape rather than a concession. Undo came free with it - `recording`
  derives the rewritten cells from the diff, which is the rule Split wrote and this file named
  this step as the next user of.
- **`supportReplace: false` is one rule rather than a disabled control.** Both filters are off by
  default as the reference's are; `output` is narrower here - what `RenderedOutput.Text` draws, so
  `text/html` and tables are out - and with the replace row open there are no output hits at all.
  `SearchHit` carries *where* it is now, since only a source match can be replaced or drawn by a
  field.

**P2j has shipped, in six sub-steps, and its first decision was to build half of it.**
`notebook:export-to-format` is in - a row in the notebook's overflow, a picker of the formats this
device can actually convert, the file beside the notebook and a share sheet from the report that
says it was written. `notebook:trust` is **not** built, and that is a decision rather than a gap:
trust gates exactly three renderers in the reference and this app draws none of them, so the
command would gate nothing. What each settled is `docs/DECISIONS.md`, which also names the trigger
that would make trust real; the evidence is `evidence/p2j-2-export-formats/` through
`evidence/p2j-5-share-export/`.

**The format list was measured before any of it was written, and it is why the menu has four rows
rather than eleven.** `GET /api/nbconvert` named fourteen exporters on the tablet and **six**
converted: `asciidoc`, `latex`, `pdf` and `rst` route every markdown cell through `convert_pandoc`
and there is no pandoc in the rootfs, `webpdf` wants playwright, `qtpdf`/`qtpng` want PyQtWebEngine.
The reference offers all of them and lets a conversion fail; a row that can only fail is a control
that lies.

Six costs the plan did not name, each recorded in `docs/DECISIONS.md` rather than here:

- **A second client of the Space would have killed the kernel's server.**
  `JupyterServerLauncher.start` begins with `stop()`, so the lazy start had to be extracted into
  one `BaseSpaceServer` that every client asks before an exporter could exist at all. Five device
  gates were re-run on the refactor (`evidence/p2j-3-space-server/`), because its whole risk is in
  behaviours no unit test reaches.
- **Nothing about an export may be derived from the format.** A `markdown` export of a notebook
  with an image comes back as a **zip**, the `Content-Disposition` is RFC 5987 rather than a plain
  filename, and `WorkspaceRepository` grew `writeBytes` because a zip written through a `String` is
  a corrupt zip.
- **`script` is not a Python file for a notebook this app wrote.** `ScriptExporter` reads
  `metadata.language_info` and this app writes `kernelspec` alone, so it answered `.txt` with the
  sources concatenated. *"Executable Script"* is served by `python` instead, and **writing
  `language_info` is the fidelity fix** - not built, and the trigger that would let `script` be
  offered as itself.
- **`EditorTopBar.kt` was 438 lines against a 500-line gate**, and the button said *"View
  actions"* over a menu about to hold an export. Both went first, as their own commit: the overflow
  is `NotebookActionsMenu.kt` now, and folding the reference's File menu into its View menu is the
  same adaptation `NotebookBar` already makes for Run + Kernel.
- **A menu cannot show a spinner that a flow can read.** Android's accessibility tree carries a
  `DropdownMenuItem`'s label and nothing else, so *"Finding export formats…"* is a row of text -
  the same reading that made every toggle in this app two labels rather than one and a check mark.
- **Two driver checks that could not fail, both in the share step.** `adb shell content read`
  against an `exported="false"` provider is denied for the shell user whatever the configuration
  says, and `content` exits 0 when it is denied. The refusal is the assertion now, and whether a
  recipient can read the bytes is an instrumented test.

**P2k has shipped, in four sub-steps, and with it P2's command surface is closed** - the presentation is P2l-P2p below. Both
`access-*-history-entry` commands are in, on two keys the symbol row grows once the setting is on -
`Hist ↑` and `Hist ↓`, right after `Tab`. `history_request` on the shell channel is the
`KernelGateway` addition this row predicted, and the shipped kernel answers it out of IPython's own
sqlite input history (`ipykernel/kernelbase.py:947-965` -> `ipkernel.py:612-645` ->
`history_manager.get_tail`, which flushes its cache before it queries). What each settled is
`docs/DECISIONS.md`; the evidence is `evidence/p2k-3-kernel-history-setting/` and
`evidence/p2k-4-kernel-history/`.

**The surface follows from the binding rather than from taste.** `Alt ArrowUp`/`Alt ArrowDown`
under a `.jp-Notebook.jp-mod-editMode` selector (`schema/tracker.json:452-462`), and `SymbolRow` is
the only thing on screen exactly when that selector would match - so it is the row and not the cell
menu, which is eighteen deep and reachable in command mode besides.

Six costs the plan did not name, each recorded in `docs/DECISIONS.md` rather than here:

- **`accessKernelHistory` is the first `EditorSettings` field that *keeps* a reference default.**
  The two beside it were each bought with a reason a phone supplies; nothing about a touch screen
  makes a cell's text safer to overwrite. The adaptation is *where* - View, until P6c has a
  Settings screen.
- **The reference's empty-entry guard is a JavaScript falsiness accident, not a decision.**
  `!content` reads as a null guard in its own neighbouring comment; transcribed it would mean
  walking back out of an empty cell with no way forward into it again.
- **`fetchBatch` re-seats the cursor one entry older than the oldest it had**, so a caller that
  steps again after it walks past an entry - shipped that way for exactly one test run.
- **detekt found the seam twice, and both times it was real.** `complete` and `executionHistory`
  are one round trip written twice, which is `SessionSocket.askOnce` now; and `EditorScreen`'s
  branches produced `EditorBottomBars`, the mirror `EditorTopBars`' own docstring predicted.
- **One defect introduced and fixed inside the step, and the screenshot is what said so** - for the
  third time in this project. Appended after `←`/`→`, both keys fell off the right edge of the
  editor pane on the tablet, so a feature reached by repeated taps cost a swipe before each one.
- **`reset_notebooks` cannot reach a dotfile**, and the kernel's history is `.ipython/…/history.sqlite`.
  Cleared in P2k-4's own driver rather than in the shared helper, because no other flow cares what
  that file holds.

**Named and not scheduled: the `search` and `range` history requests.** `hist_access_type` has three
values (`packages/services/src/kernel/messages.ts:984-1030`) and this app sends only `tail`, which
is the only one the two commands use. `search` is what an inline-completion history provider would
want (`packages/completer/src/default/inlinehistoryprovider.ts`), and that is a P5/P6 question.

**Named and not scheduled: `metadata.language_info` is never written.** A real Jupyter fills it in
on save from the kernel's `kernel_info_reply`; this app writes `kernelspec` alone.
`Notebook.PreservedJson` keeps a foreign notebook's copy and `NotebookRoundTripTest` proves it, but
nothing produces one. It is a nbformat-fidelity gap rather than an export one, it is what makes
`script` answer `.txt` here (`docs/DECISIONS.md`), and whoever closes it can offer *"Executable
Script"* as `script` rather than as `python`.

**Deliberately out of this phase:** `create-output-view`, `set-side-by-side-ratio`, the rest of the
`export-to-format` matrix, and — moved here from P2g rather than built there —
**`toggle-virtual-scrollbar`**. It is a cell-preview strip with a viewport indicator and
click-to-jump, engine in `packages/notebook/src/windowing.ts` — not a touch target at 360dp, and
P2h's table of contents both answers "jump around a long notebook" on a phone and has already paid
for the side panel a minimap would want. Re-costed now that the ToC exists rather than built
twice. The rest is either a desktop-window idea with no phone reading, or
work that belongs after the Space system decides where files go.

---

# P2l–P2p — presentation

P2 closed the **command surface**; this phase closes the **presentation**. The distinction is
real and it is what makes this a phase rather than a tidy-up: every row below was measured against
the shipped build before it was written, and two of them are defects the whole suite is green
against today - a ragged cell gutter and four different sizes for code.

Two of the four decisions here were taken by the reader of this file rather than derived, and are
recorded as such: **full JupyterLab parity for cell chrome** (P2m) and **the mediator** for the
side panel (P2o). What follows from each is `docs/DECISIONS.md`.

## What was measured

- **There is a colour system and no dimension or type system.** Colour states every reachable
  Material role and `ColorSchemeTest` enumerates them; against that, `theme/Type.kt` was the
  Android Studio template and nothing named a gap at all - 63 `.dp` literals across 20 files in 16
  distinct values, **13 of them off Material's 4dp grid** (`14.dp` three times in the explorer and
  its recents, plus `6`, `3`, `2`, `5`, `18`, `1`). And **9 `.sp` literals, every one of them
  inside `Type.kt`**, six in a commented-out block - so nothing else in the app named a size, it
  inherited one.
- **The cell took IntelliJ's gutter and JupyterLab's nothing-else.** `cellStripe` draws a resting
  mark the reference does not have - `.jp-Collapser` is `background: transparent` until
  `.jp-mod-active` (`packages/cells/style/collapser.css:6-15`,
  `packages/notebook/style/base.css:108-110`) - and no cell here has the border the reference
  always has: `.jp-InputArea-editor` carries `1px solid var(--jp-cell-editor-border-color)`
  unconditionally and takes the brand colour plus a box-shadow in edit mode
  (`packages/cells/style/inputarea.css:18-26`, `base.css:160-164`).
- **The cell gutter is ragged, and committed evidence shows it.** `CellHeader` adds the selection
  checkbox and the heading collapser as ordinary `Row` children before the execution label, each
  present only for some cells, so the label's x depends on which controls a cell happens to carry.
  Two markdown headers start at different x in `evidence/p2h-3-table-of-contents-pane/02-the-pane.png`.
  The reference makes both fixed columns and never reflows - `flex: 0 0` on `.jp-Collapser` and
  `.jp-InputPrompt` (`inputarea.css:29-31`).
- **The reference has one side area; this app has two half-answers.** Its left sidebar holds File
  Browser (rank 100), Sessions (200), Command Palette (300), **Table of Contents (400)** and
  Extension Manager (1000) - `filebrowser-extension/src/index.ts:676`,
  `running-extension/src/index.ts:121`, `apputils-extension/src/palette.ts:115`,
  `toc-extension/src/index.ts:309`, `extensionmanager-extension/src/index.ts:78`. Ours puts the
  ToC on the **right of the notebook** and the explorer in a pane with no panel chrome. The
  Launcher is not a sidebar panel there - `shell.add(main, 'main', …)`
  (`launcher-extension/src/index.ts:118`) - so P6d and this rail do not collide.
- **The rail/bar switch is the reference's own, not only Material's.**
  `ILabShell.ActivityBarPosition = 'side' | 'top' | 'bottom'` (`packages/application/src/shell.ts:172`),
  user-settable from `application-extension/schema/shell.json:68`, with `SideBarHandler.position`
  reparenting the bar and flipping its orientation (`shell.ts:2263-2290`); 32px vertical, 28px
  horizontal (`packages/application/style/sidepanel.css:11-14`). Compose agrees and is more precise
  than "small versus medium": `NavigationSuiteScaffold` shows a bar when the width **or the
  height** is compact, and a rail otherwise.
- **The medium window is the least-served class, and it is a real orientation of the real device.**
  Widths this project drives, from committed `device.txt` files: tablet **1280×800dp**, emulator
  **320×640dp**, and tablet portrait **800×1280dp**. Two panes start at 840dp, so the tablet in
  portrait gets the phone layout.

## The sub-steps

| # | Sub-step | Gate |
|---|---|---|
| **P2l-1** | *(shipped)* Material's type scale, stated rather than hand-built. | — |
| **P2l-2** | *(shipped)* `theme/Spacing.kt` on the 4dp grid, the 63 literals migrated onto it, and the gate that keeps it the only place a gap is spelled out. | — |
| **P2l-4** | *(shipped)* The two bare `clickable` rows reserve Material's 48dp rather than borrowing it from their neighbours. | — |
| **P2l-3a** | *(shipped)* **One size for code.** Measured: the cell's source states none at all (`codeSourceStyle` reads `LocalTextStyle`, so it resolves to Compose's `DefaultFontSize = 14.sp` with the font's own line height), outputs and the folded-cell placeholder are `bodySmall`, the completion popup `bodyMedium` - where the reference has one `--jp-code-font-size` and one `--jp-code-line-height` (`variables.css:196-197`) read by the editor, the input area, `.jp-RenderedText pre` and **both** completer selectors (`codemirror/base.css:8`, `inputarea.css:37`, `rendermime/base.css:28`, `completer/base.css:75,118`). `theme/CodeText.kt` at **14sp/18sp**, and the four surfaces that take it. | JVM over the scale; instrumented that an output line measures the code line height; a screenshot of a cell, its output and its completions in one frame. |
| **P2l-3b** | **Rendered markdown's code is a different rule, and that is why this is two commits.** `.jp-RenderedHTMLCommon pre, code` takes the code family and the code line height but **`font-size: inherit`** (`rendermime/base.css:405-417`) - the prose size, not the code size. Ours draws both at `bodySmall`. Plus the gate that keeps `CodeText` the only place a code size is spelled out. | Instrumented: a fenced block in a markdown cell measures the prose size, not the code size. The gate red before, clean after. |
| **P2m-1** | The collapser: transparent at rest, brand blue when active. `stripeResting` leaves `EditorPalette`. **`docs/DECISIONS.md` "Every cell gets one" is rewritten in the same commit** - it argues the opposite today, and a decision record that contradicts the code is worse than none. | `CellStripeTest` red-first; `CellTouchTargetTest` re-run; `u3-cell-chrome` re-driven on the tablet and the screenshots **looked at**. |
| **P2m-2** | The editor box: 1px always, brand blue plus elevation in edit mode, one shared modifier across the three fields rather than three copies. | `EditorPaletteTest` extended with the contrast assertion it already pins for every other pair; an instrumented test that the border follows the mode. |
| **P2n** | The cell gutter becomes two fixed-width slots, so the execution label's x does not depend on which optional controls a cell carries. | Instrumented: the label's x is equal across a plain code cell, a heading cell and a cell in a marked run - the three that differ today. |
| **P2o-1** | The mediator: which panel is selected, and the open notebook's headings and jump lambda, published by the editor and read by the panel. **No duplicated derivation** - `EditorViewModel` stays the one place headings, folds and numbering are computed. | JVM, including the lifetime: the panel empties when the notebook closes, so a ToC cannot outlive its document. |
| **P2o-2** | The rail on medium and expanded: `NavigationRail` at the left edge of the list pane, panels Files and Table of contents in the reference's rank order. `tocPresentationFor` is superseded - the rule becomes "is there a side pane", not "is the editor 840dp". | Tablet at 1280dp: switch to ToC, jump to a heading, the panel stays and the notebook moves. |
| **P2o-3** | **The two-pane threshold, re-derived rather than kept.** An 80dp rail (`NavigationRailCollapsedTokens.NarrowContainerWidth`) changes the arithmetic 840dp came from, and 800dp portrait is a real device in a real orientation. Measure before changing the constant; if it says 840dp stays, that is the outcome and it is recorded. | A measurement at 320 / 800 / 1280dp into `evidence/`, whichever way it lands. |
| **P2o-4** | The bar on compact, the same two panels. The in-notebook bottom-sheet ToC stays: the reference has both a sidebar tab and `toc:show-panel` for one widget. | Emulator at **320dp**, which is the width that decides; both entry points in one flow. |
| **P2p** | The suite re-captured on the shipped build, both devices, one build. | `run-flows-sharded.sh`, evidence committed, **every screenshot looked at** - three defects in this project's history were green against every assertion and wrong in the picture. |

**Named risk, and it is why P2o-4 is last.** On compact the app's root is the explorer, so the bar
is only on screen when no notebook is open - the ToC entry is then reachable only after leaving the
document, which is worse than the bottom sheet beside it. If the 320dp flow shows that, the answer
is a modal navigation drawer over the document, the reference's own narrow-window shape, and it is
costed here rather than discovered late.

**Deliberately not in this phase:** `dirty` rendered as the reference's `•`
(`base.css:114-121`) stays a P6c question rather than being smuggled in with the presentation
work, and it is named here so nobody re-derives that the reference does draw it.

**P2l-1 has shipped, and what it repaired was invisible by construction.** `theme/Type.kt` was the
Android Studio template and had been since Step 1: one `bodyLarge` at 16sp/24sp/0.5sp, which are
the three numbers Material already supplies for that role. So it changed nothing it appeared to
change - and stripped `includeFontPadding = false` and `DefaultLineHeightStyle` from the only role
`MaterialTheme` also provides as the ambient `LocalTextStyle`. What it settled is
`docs/DECISIONS.md`; the evidence is `evidence/p2l-1-typography/`.

Two things it cost that this file did not name:

- **A metric needs a pair, not a screenshot.** The driver takes `before`/`after` and is run twice,
  once on a build carrying the template. Nothing about one frame could have shown the difference,
  and the difference is real: the explorer row's file name moved 2px and the gap to its subtitle
  went from 5px to 8px.
- **The ten-line code cell in the fixture proved a negative, which is why it is kept.** It was put
  there to catch an accumulating per-line drift and is **pixel-identical** across the pair, because
  the code field states its own line height. That is the measurement that says P2l-3 is a separate
  sub-step rather than something P2l-1 half-did.

**P2l-2 has shipped, and the arithmetic decided two heights the eye would have got wrong.** 63
literals became 20, every survivor carrying the citation that earns it, and
`scripts/check_ui_dimensions.py` keeps `theme/Spacing.kt` the only place a gap is spelled out.
What it settled is `docs/DECISIONS.md`; the evidence is `evidence/p2l-2-spacing/`.

Three costs the plan did not name:

- **An off-grid value is not automatically rounded down.** The explorer's `14.dp` rows became
  `Spacing.lg` and measure **72dp**, `ListItemDefaults`' own two-line height. The breadcrumb
  carried the same 14dp and was therefore **exactly 48dp** - Material's floor - so rounding it to
  `Spacing.md` would have put a control *under* the minimum at 44dp. Measured on device from
  `uiautomator dump`, the emulator being at density 160 so 1px is 1dp.
- **The gate could not fail, and only its own test caught that.** `rglob` over a directory that is
  not there yields nothing, so run from `scripts/` it reported a clean tree while 53 offences sat
  in it. It raises now, and one of its tests runs it over this repository.
- **It found a defect it did not fix, which is P2l-4 above.** `CollapsedCell` is a bare `clickable`
  measuring 288x32dp, and this step moved it 28dp to 32dp without closing the 16dp gap. Filed
  rather than folded in: it needs its own failing test and its own evidence.

**P2l-4 has shipped, and the gate this file gave it could not have failed.** `CollapsedCell`
measured **288x32dp** and `HiddenCellsRow` **288x40dp**; both reserve 48dp now, via
`minimumInteractiveComponentSize()` - Material's own, the modifier `IconButton` applies to itself.
What it settled is `docs/DECISIONS.md`; the evidence is `evidence/p2l-4-touch-targets/`.

Three costs the plan did not name:

- **The assertion this row was written around cannot fail.** `assertIsAtLeastMinimumTouchTarget`
  reads `touchBoundsInRoot`, which Compose pads out to 48dp for *any* clickable node
  (`NodeCoordinator.calculateMinimumTouchTargetPadding`) - so it computes `max(measured, 48) >= 48`.
  `HiddenCellsRowTest` had shipped green against a 40dp row on the strength of it, and its
  docstring asserted the opposite. **P2l-3 is written against the same assumptions and should be
  re-read before it is picked up**: a size, like a gap, is only pinned by a test that measures the
  layout.
- **`dev36` is not a phone, and the width is the only part of that anyone chose.** 320dp is
  deliberate; density 160 is an AVD default, and at 1px per dp every screenshot in `evidence/` has
  the wrong proportions. P2l-4 captured a fourth frame at 411dp/420dpi beside the usual three.
  Whether the suite's standard emulator moves is not decided here - it would re-key 92 flows and
  the sharder's time balance.
- **One defect introduced and fixed inside the step, and again the measurement is what said so.**
  The driver read the density with `sed -n 1p`, which takes `Physical density` and ignores the
  `Override density` printed after it, so the phone run reported 126dp rows as 126dp - a number
  that clears 48 and would have been read as a pass.

**P2l-3a has shipped, and the reference turned out to have two rules where this file named one.**
`theme/CodeText.kt` is 14sp/18sp and `codeTextStyle()` is where the cell's source, its outputs, the
completion popup and the folded-cell placeholder take it from. What it settled is
`docs/DECISIONS.md`; the evidence is `evidence/p2l-3a-code-size/`.

Three things it cost that this file did not name:

- **The second rule, which is why the row became two.** `.jp-RenderedHTMLCommon pre, code` takes the
  code family and the code line height but **`font-size: inherit`** - the *prose* size
  (`rendermime/base.css:405-417`). Folding rendered markdown's code into the code size would have
  copied the reference wrong in the opposite direction, so it is P2l-3b with its own citation.
- **The source's size was never a choice, and that is why it is the one that did not move.** It
  resolved to Compose's `DefaultFontSize = 14.sp` (`ui-text`, `SpanStyle.kt:48`) because
  `LocalTextStyle`'s Material default is `TextStyle.Default`. Landing the scale on 14sp is
  therefore "everything else joins the source", not a size picked by eye.
- **`ktlintCheck` had never checked a single `.kt` file in this repository** - found while removing
  three imports this step made unused, which the gate did not notice. Fixed in the commit after
  this one; what it cost is `docs/DECISIONS.md`.

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
- **There is no command-mode way to move the head, and P2d-2 is what made it matter.** Focus is
  what selects a cell here (`EditorViewModel.cellFocusChanged`), so aiming a head-relative command —
  `run-all-above`, `run-all-below`, `restart-and-run-to-selected`, and every heading command P2f
  brings — means tapping into a cell, taking the keyboard, and dismissing it again. The reference
  has both: `_evtMouseDown` sets `activeCellIndex` on a press anywhere in a cell and enters edit
  mode only for a press *in the editor* (`widget.ts:3160-3167`). Ours has only the second half.
  Deliberately not fixed inside P2d-2: the obvious repair — a gesture on `CellHeader`'s row — is
  exactly what U5a removed, because a strip that claims every gesture leaves no room for the buttons
  on it (`CellHeader.kt:54-61`). Measured, not speculative: `evidence/p2d-2-ranged-runs/`, where the
  flow needs `hideKeyboard` + `back` between selecting a cell and reaching the bar.
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
