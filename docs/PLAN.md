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
| Runtime | the notebook **says which kernel it is on and what that kernel is doing** - no kernel, starting, busy, idle, restarting; rootfs installs, server starts, kernels start and **reattach**; numpy/scipy import; the server stops when it is idle or the app is left, and comes back on the next run; a kernel can be ended on purpose from the kernel menu |
| Notebook | **kernel history** walked into the cell being edited; **find and replace** across the document, with JupyterLab's own filters; all three `cell_type`s - code, markdown and **raw**; cells editable, syntax-highlighted, executed; outputs (stream, error+ANSI, PNG, `text/html`, tables) render natively; collapse - of a cell's source, of its outputs, of the **section under a heading**, and outputs can be **bounded to a scrollable strip**; a contiguous run of cells can be marked and every cell **and run** command acts on it — ranged runs, the compound restarts and clear-all included; nbformat round-trip preserves unknown fields |
| Editor | `BasicTextField` + generated ANTLR lexer, auto-indent, bracket match (**toggleable, persisted, universal** across a cell and the plain file editor), line numbers (**toggleable**), kernel-backed completion, symbol row |
| Files | explorer with create / rename / delete / browse, Recents as a section, **workspace search** from the shell's own panel - the query, its three options, include / exclude globs, results grouped by file, a `.ipynb` searched as the **cells** it is rather than the JSON it is stored in, and a tap that opens the file **at** the match - a notebook **on the cell**, revealed out of whatever was hiding it; **replace across every file at once**, counted before it is agreed to, reaching a document that is *open* rather than the file under it, and taken back by one undo - **export** beside the document and out through a share sheet, reachable at **every** width since P2y, through a bottom activity bar wherever the shell draws one pane - polling on JupyterLab's own cadence (`packages/filebrowser/src/model.ts:26`, `DEFAULT_REFRESH_INTERVAL = 10000`, `standby: 'when-hidden'` at `:106`) |
| Presentation | three scales and one gate each: Material's type scale stated rather than hand-built, `theme/Spacing.kt` on the 4dp grid, and `theme/CodeText.kt` at 14sp/18sp with `FontFamily.Monospace` confined to `EditorTheme.kt`; every tap target reserves Material's 48dp. **Everything below this point is JupyterLab-derived and named as such, because since P2t this file has two presentation references and its own rule is that a claim must say which.** Cell chrome is JupyterLab's: the collapser transparent at rest and brand blue on the active cell, the editor boxed by a 1dp border that turns brand blue for the cell being typed into. A cell's prompt is JupyterLab's - `[7]:` on code and **nothing at all** on markdown or raw, so every header's leading mark sits at one x. The document's name has a row of its own above its actions, where JupyterLab's single-document mode puts it; the heading caret sits beside the heading it folds, where JupyterLab draws it - but **only while that heading is rendered** (P2v-2), where the reference keeps it in both states, because beside an open source it cost 48dp of the editor's width for a fold nobody can reach mid-typing. The table of contents is a **sidebar panel of the shell** reached from an activity bar beside the file browser, where it used to be a column on the right of the notebook; the open document publishes its headings across to it, since the panel cannot reach the notebook's ViewModel. Picking the showing panel collapses the side area and the notebook takes the width. The active cell's type is named on the bar where there is room for it and in the notebook's overflow where there is not, which is what JupyterLab's `ReactiveToolbar` does with an item that does not fit. A nested document's **breadcrumb** is between its name and its actions, where `BreadcrumbsControl` sits relative to a tab title - *ancestors only*, since the name is on the row above, and absent at the root (P2w-1). A cell's Run control draws an **outlined** play, not a filled one (P2v-1): filled at rest reads as "playing" - and the kernel indicator on the action row follows the same rule for the same reason (P2w-2), where the reference's own filled bolt was the loudest mark in a row of outlines. That indicator is JupyterLab's `kernelName` and `executionProgress` as one control, right of Find and **left of** the overflow, which is where a `ReactiveToolbar` actually ends; below 600dp it keeps the status and drops the name. Every cell type's editor is **one font** - a markdown cell's source is drawn in the code font a code and a raw cell's is (P2s), which both references agree on and which is **not** rendered markdown's code inheriting the prose size (P2l-3b). **P2v declined the cell-layout re-derivation and P2w re-derives the notebook's top from VS Code**, so the four sentences above them stand: what P2v changes is the play's glyph and whether a heading's chevron is drawn while its source is open (`docs/DECISIONS.md`, "The cell keeps its header strip"). |
| Shell | two panes ≥ 840dp on `ListDetailSceneStrategy`, the activity bar as a **rail beside two panes and a bottom bar below one** - VS Code's own `ActivityBarPosition`, its items spread on a small screen and grouped on a medium one, which is `ShortNavigationBar`'s own rule - **VS Code's chrome palette** - every Material role on the resolved 2026 ramp, each pair held to a contrast floor (P2u-1) - Material's own type scale, per-notebook ViewModel scoping |
| Tests | **399 instrumented**, **1790 unit**, **170 script**, 126 Maestro drivers over 141 flows - 119 of them in the sweep, the other seven being tools rather than checks (`NOT A SUITE FLOW`) - `run-flows-sharded.sh` **balanced by measured time** across both devices, gradle + script gates. Counted, not carried, and re-counted off the tree at every phase: this row read 256/1261/100/114 until P2s, which was 35 instrumented tests behind what the tree held; 1330 unit until P2x-1; 347/1459 until P2x-5, which was ten instrumented tests behind before that phase added a single one; **366/1558 until P2y**; **153 script until P2z**, which added six for the width rule (P2z-d) and six for the stale-frame manifest (P2z-h); and **378/1564/165** until P4f-2 re-counted it, P4a-P4f-1 having added 21 instrumented and 189 unit without the row moving. **The unit figure is executions rather than `@Test` sites** - a `grep` reads 1748, because the port contracts are written once and run per implementation, and the difference is the point of writing them that way |

**Where the plan is:** P2l-P2p is closed, its re-capture included - P2z absorbed it and shipped.
**P2t-P2z is closed too**: the VS Code presentation re-base - since P2t, VS Code answers for how the notebook
looks and JupyterLab still answers for what it does (`docs/DECISIONS.md` names exactly what that
does and does not reopen). **Shipped in it: P2t, P2s, P2u-1, P2u-2a/b/c, P2u-3a-f, P2v-1/2, P2w-1, P2w-2, P2x-1a-e, P2x-2a/b, P2x-3a-f, P2x-4a-d, P2x-5a-k, P2y-a-e and P2z-a-j plus P2z-w/x/z.** P2u-1 retired the
IntelliJ-derived chrome palette the Shell row above used to report and corrected two of its own
colour ids by measuring them; **P2u-2a** did the same for the eight syntax colours - and found that
this file's own contrast table had been measured against the page rather than the cell, which cost
the floor its place as the gate: light's `number` is **4.42:1** on the cell, and **parity won**, so
the palette is now pinned by equality to the resolved themes. **P2u-2b** retired the markdown fold
the re-base had left without a citation, and **P2u-2c** fixed the defect that exposed - a quote was
green on its chevron and plain on its body. **P2u-3a-f then finished the palette**: two fields
nothing drew are gone and a gate stops the next one, the surfaces and lines are VS Code's ids
resolved through their registered defaults, a search match is a translucent wash where it used to
be an opaque fill with the text restated over it, and rendered markdown takes the webview base
stylesheet that actually themes it. **`EditorPalette` is now entirely VS Code's** - the split
`theme/EditorPalettes.kt` used to state in its header is closed.

**P2z is shipped**, and with it P2p: the whole suite re-captured on one build (sha `68b795af`),
sharded across both devices, **118 green**, and then every re-captured frame looked at -
440 of them across 107 directories, one line per flow in `evidence/p2z-suite-sweep/frames.md`.

The reading is what the phase was for. It found **nine defects no green log could have shown**,
each with its own commit: two `NARROW WINDOW` flows that had only ever run at 320dp and were
wrong at 800dp (P2z-b, P2z-c), a flow whose evidence path could not tell its two legal widths
apart - now a gate (P2z-d), a claim in a screenshot's name that was not a step (P2z-e), two more
assertions that could not fail (P2z-f, P2z-i), two captions that had stopped being true of their
own pictures (P2z-g, P2z-j), and **the harness keeping every frame it had ever written** so that
eleven committed screenshots showed UI the app had retired (P2z-h).

No production line changed in P2z-f…P2z-j - the defects were in flows, fixtures, captions and
the harness - so the sweep's own result stands for the app, and every flow that did change was
re-run green on the device its rule names, in the commit that changed it.

**In P4 now**, at **P4f-2**: P4a-P4e are shipped and the parser has its ground state (P4f-1) and
its escape state machine (P4f-2). The order changed too - phases now run **P4 → P5 → P6 → P3**: the terminal, then
LSP, then the debugger/console/settings/launcher group, and **Spaces last**. Nothing ahead of P3
depends on it - `BaseSpaceServer`, `SpaceRuntime` and `JupyterServerLauncher` already give all three
the one base Space they need, and `BaseSpaceServer`'s own KDoc has named *"an export, a terminal, a
language server"* as clients of that single server since P1. **The labels did not move with the
order**: P3 is still "P3 - Spaces" wherever it is cited, and its section sits after P6 rather than
being renumbered, so no commit message or `docs/DECISIONS.md` entry became wrong.

**P2y shipped in five commits, corrected its own gate, and found a defect only the tablet could
have.** The gate said the bar is *"present below 600dp and absent at and above it"* - Material's own
breakpoint, and not this app's: the rail is drawn at 840dp, so that would have left **600-840dp with
neither control**, which is this project's tablet in portrait and the one window where workspace
search had no route and no overlay to fall back to. P2o-3 had already written the sharper rule down
- *"every window the shell draws as one pane"* - and this row had not caught up with it.
**Three decisions, all recorded in `docs/DECISIONS.md`:** the bar is `!twoPane`; it is drawn **over
the panel and never over a document**, which costs the notebook 0dp where P2o-4 costed an always-on
bar at 20% of the cell list *and* which a screenshot showed would have stacked two bottom bars,
since the notebook already has one; and its items are **icon-only**, because `ExplorerScreen` titles
itself "Files" and `SearchSidePanel` titles itself "Search", so a labelled item would put each string
on screen twice and `onNodeWithText` throws on two matches.
**The engine came first and nothing drew it** - the destinations and the narrowing rule (**a**), the
bar itself (**b**) - then the shell drew it (**c**), which is also where `ShellSidePane` was split
out because detekt read `MainNavigation` at complexity 17.
**P2y-d is the finding.** One arrangement at both widths was wrong at 800dp: two items **396dp** wide
with their centres **404dp** apart, a 24dp icon in a tap target half the window across. The fix is
`ShortNavigationBar`'s own KDoc - `EqualWeight` on a small screen, `Centered` on a medium one - so
the width branch is the reference's rather than an adaptation, and the bar reads its own constraints
instead of the window, which is the seam a test can actually drive. It also came in 16dp shorter than
`NavigationBar`.
**And P2x-5k's rule was applied rather than re-learned**: `p2o-4-one-pane-side-panels` changed and
was re-run in the same commit, on both devices.

**P2x-5 shipped in eleven commits and closed the phase's one named prerequisite.** The engine came
first and nothing drew it: the fold that rewrites a document (**5a**), the `OpenDocuments` registry
(**5b**), the engine itself with its three routes and its all-or-none refusal (**5c**), and the one
undo a replace-all is (**5d**). Then the controls: the replace row (**5e**), the sixteen counted
strings by equality (**5f**), the ViewModel that asks before it writes (**5g**), the button and the
dialog and the report (**5h**), and the undo's own surface (**5i**). **5j** is a gate rather than a
change - two panes, a document open in one and the panel in the other, the editor never closed
between the replace and the assertion. **5k** is this file and the re-runs.
**Three findings, and two of them cost a picture to see.** *(i)* Material's `find_replace` at 24dp
reads as **refresh**, beside a query box whose leading icon is the magnifier it is drawn from - so
Replace All is a labelled button on a row of its own, which is P2x-3d's answer to a missing codicon
applied to an action. *(ii)* `p2x-3-search-scope` **had been red since P2x-5e** and nothing knew,
because that sub-step changed chrome two flows draw and re-ran neither; its committed frame was
already showing a half-clipped field. *(iii)* detekt and the file-length gate found the same seam a
commit apart - *the workspace searched* and *the workspace rewritten* - and both halves are split.
**Two departures, both named in `docs/DECISIONS.md`**: an undo brings the **results** back with the
files, because the panel is what performed it and nothing else on screen would have shown that
anything happened; and the undo is offered beside the report rather than in a snackbar, decided on
lifetime - a snackbar carries the only route to the undo away after seconds, where the reference's
stays available until the file is edited.
**P2x-4 shipped in four commits and corrected its own row twice.** The row claimed notebook-aware
matching *"**is** VS Code parity rather than a departure from it"*; checked in the tree, that holds
only for an **open** notebook - a closed one reaches cell-aware search solely under
`search.experimental.closedNotebookRichContentResults`, whose registered default is **false**, so
out of the box VS Code hands a closed `.ipynb` to ripgrep and does report `"cell_type"`. What
shipped is that experimental branch, which is also what JupyterLab's own document search does
unconditionally. And the row's third gate - *"round-trip that replace preserves unknown fields"* -
is **P2x-5's**, not this step's: P2x-1e took `replaceAll` off the port, so there is no replace here
to round-trip.
What did ship: the model, where a match names its cell **by index** because a cell's nbformat id is
optional and `CellFile.toDomain` mints a `UUID` for one without (**4a**); the walk parsing a `.ipynb`
instead of skipping it, with a notebook it *cannot* read becoming the fourth kind of file it
declines (**4b**); the cell named in the row's label rather than as a third level of the tree, which
is the reference's own presentation (**4c**); and a tap landing the notebook **on the cell**,
revealed out of a collapsed section (**4d**).
**Cell outputs are declined, with a prerequisite rather than a shrug** - `SearchHit.outputAt`
indexes the *rendered* outputs and the rule for which outputs have text at all lives in `ui/editor/`,
which `data/` may not import. See the open risks.
**4d introduced a defect and only a screenshot caught it.** Its `LaunchedEffect` was keyed on the
cells the landing itself changes, so Compose cancelled `animateScrollToItem` mid-flight; the flow's
`assertVisible` on the match was green against a notebook sitting on cell one, because a
composed-but-clipped `LazyColumn` item is still in the semantics tree. `docs/DECISIONS.md` carries
both halves, and two things now fail on it where nothing did.
**P2x-3 shipped in six commits and declined one thing its row asked for**: the *replace toggle*,
which goes with P2x-5 where the behaviour behind it lives (P2x-1e took `replaceAll` off the port
because the reference replaces through the editor-model stack over the matches on screen). A Replace
All that cannot replace is the control `docs/DECISIONS.md` already refuses under the export formats.
What did ship: the walk saying when it is over and whether it left anything out (**3a**), the box
read on `search.searchOnTypeDebouncePeriod` (**3b**), the results grouped by file with the cap drawn
rather than silent (**3c**), the three option toggles (**3d**), the two glob boxes behind the `…`
(**3e**), and a tap that lands the editor *on* the match (**3f**).
**Three of the six found a defect of their own, and two of the three were only visible in a
picture.** 3c's panel drew its lower results under the keyboard. 3e's results were composed, correct
and **zero pixels tall** once the glob boxes were open - the panel is one scrolling surface now -
and its two glob headings answered to the same string as their fields, so a flow tapped a label and
typed a glob into the wrong box. 3f's flow went green with the editor **on line 1**, the match
forty-six lines below the fold, because the editor is one node carrying the whole file: what proves
a landing is the screenshot and a scroll offset, and the flow's header now says so. `docs/DECISIONS.md`
carries all of it, along with the two harness readings 3e paid for - a two-pane test's pane lays out
at zero height under `ForcedSize`, and a scroll action inside a wait hangs the instrumentation.
**P2x-2 shipped in two commits and corrected its own row**, which said the rail's order was VS
Code's *"which is also JupyterLab's rank order"*. Checked in both trees, the second half is false:
JupyterLab registers no search panel at all. It takes **both** references to state the order and
neither could alone - the browser first in both, search second on VS Code's `order: 1`, the table of
contents last on JupyterLab's rank 400, VS Code having no such container and JupyterLab no such
search. `docs/DECISIONS.md` carries it, with the panel's place measured off a hierarchy dump because
an empty panel has no edge to see. **P2x-2b is a defect the sub-step widened rather than
introduced**: a window that narrowed while a panel was up kept drawing the panel with no rail to
leave it by, stranding the file browser - pre-existing since P2o-2, measured red on both cases
before it was fixed.
**P2x-1 shipped in five commits** and is the first port since P2t: `WorkspaceSearcher`, its fake,
`DiskWorkspaceSearcher`, and a glob language underneath them all. Three things it decided are in
`docs/DECISIONS.md` - `SearchQuery`'s home, why the globs are transcribed rather than handed to
`java.nio`, and the three kinds of file a walk declines. **P2x-1e is a correction to P2x-1c**, and
the kind this file exists to record: `replaceAll` came off the port, because the reference replaces
through the editor-model stack and takes the matches on screen, where ours took a query and re-ran
the search. What that leaves is a *prerequisite* rather than a defect - P2x-5 needs a registry of
open documents keyed by path, and the open risks say why this app has none.
**P2w-2 shipped in three commits** - the state, the menu into a home of its own for its second
anchor, and the control - and it corrected its own row's gate on a measurement: *"the rightmost
item"* is not where either reference puts it, because a `ReactiveToolbar`'s popup opener is always
the last widget, so the indicator sits right of Find and left of the `⋮`. Below 600dp the kernel's
**name** goes and its status stays, which reverses the reference's own removal order; that, the
glyph set - the reference's two components disagree about which of busy and idle is the filled one,
and the retired `KernelStatus` item is the one followed - and the stale session P1c can leave
behind are the three entries in `docs/DECISIONS.md`. **P2w-1 shipped in three commits** - the shared segmenting,
the race it exposed, and the row itself - and the two departures it decided are in
`docs/DECISIONS.md`: the trail is *ancestors only*, because the title row above it already carries
the name, and it is *absent* on a notebook at the workspace root, because most notebooks here are
at the root and a row that says nothing is the 48dp P2v declined the run gutter over. The bar is
still 112dp for a root notebook and **160dp** for a nested one, measured. **P2v shipped in two
commits, and its layout half is declined** -
the run gutter, the count at its foot, the heading cell's chevron/play slot and `CellHeader`'s
retirement - declined on a measurement rather than on taste: built, a 48dp column beside every editor took 15%
of a 320dp window out of the one thing a cell exists for, and left `[ ]:` and the editor at two
x's. `docs/DECISIONS.md`, "The cell keeps its header strip", carries the frames and the arithmetic.
What shipped in its place is two adaptations that keep the header strip where it is: **P2v-1**,
the cells' play outlined rather than filled, and **P2v-2**, a heading's fold chevron drawn rendered
only - which gave the editor back the 48dp the caret was taking, measured at 80dp against prose's
32dp. **Colour is untouched** - P2u is VS Code's and stays VS Code's - and so is P2w onward, none
of which is cell chrome.

Status lives here rather than in the table, because that table says what the app **is** and this
says where the work **is up to**; the two were one cell until P2s and the Presentation row was
carrying "still open in this phase" as though it were a fact about presentation.

**Not built at all:** the Space system, a terminal, LSP, the debugger, the console, and a
**settings screen** - the five persisted editor settings live in the notebook's View menu until P6c
gives them one. Two entries this line used to carry are gone rather than outstanding: **export**
shipped in P2j, and **trust** is a decision rather than a gap (`docs/DECISIONS.md` names the
trigger that would make it real).

---

## How to read this

Phases run in the order **the sections appear in**, which since P2z is no longer the order their
labels sort in: P4 → P5 → P6 → **P3 last**. Each sub-step is **one commit** and closes on one gate. `CLAUDE.md` owns the
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
| **VS Code** `reference/research_repos/vscode` | **presentation, since P2t**: notebook layout and cell chrome (`src/vs/workbench/contrib/notebook/browser/`), the palette (`extensions/theme-defaults/themes/`), the search viewlet (`src/vs/workbench/contrib/search/browser/`) | the kernel wire protocol — it reaches a Jupyter server through an extension, and speaks no ZMQ in this tree |
| **vscode-jupyter** `reference/research_repos/vscode-jupyter` | kernel selection and status **as an extension contributes them** (`src/kernels`, `src/notebooks/controllers`) | **notebook chrome** — `src/` is `kernels`, `notebooks`, `standalone`, `webviews`, and `grep -rl 'run-button-container\|execution-count-label\|cellRunGutter' src/` returns **nothing**. Every cell part is core VS Code |
| **Compose docs** developer.android.com | which component exists and how it behaves on a phone or tablet | — |
| **Termux** `reference/research_repos/termux-app` | the **terminal**: a VT emulator (`terminal-emulator/`), how a buffer is drawn with a monospace font (`terminal-view/TerminalRenderer.java`), and the extra-keys row with its latching modifiers (`termux-shared/…/extrakeys/`). `LICENSE.md` carves both terminal modules out of the repo's GPLv3 as **Apache 2.0**, inherited from Jack Palevich's Android Terminal Emulator | its exec model — `gradle.properties:19` is `targetSdkVersion=28`, the same boundary Winlator sits on |

A statement that "the reference does X" must name **which** reference. **Three more trees are
checked out and none is authoritative for anything scheduled**, so they are named here rather than
re-discovered: `box64` and `box86` are x86 userspace emulators, relevant only if this app ever has
to run a non-native binary, which the exec model forbids; `reference/xeus-android` is two prose
documents (`FEASIBILITY.md`, `STUDY_CASES.md`), not source.

**Two references now answer for presentation, and the split is deliberate.** Since P2t, VS Code
decides how the notebook *looks* and JupyterLab decides how it *behaves* — `docs/DECISIONS.md`,
"The presentation reference is VS Code; the behaviour reference stays JupyterLab", names exactly
what that does and does not reopen. IntelliJ stays the editor reference for everything below the
palette.

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
- **The cell gutter is ragged, and committed evidence shows it.** `CellHeader` adds the heading
  collapser as an ordinary `Row` child before the execution label, so two markdown headers start at
  different x in `git show b4f157b:evidence/p2h-3-table-of-contents-pane/02-the-pane.png` - P2o-2
  renamed that flow to `p2o-2-side-panel`, so the frame is in git history rather than in the tree.
  **P2n found the cause was not the gutter.** The reference gives a markdown or raw cell no prompt
  at all (`widget.ts:1693-1700`); the words this app drew there were its own, and they were what the
  collapser had to push. `flex: 0 0` at `inputarea.css:29-31` - the citation this row was written
  from - is the *desktop* rule, overridden at `width <= 760px` (`:76-88`) for exactly this layout.
  Settled in `docs/DECISIONS.md`.
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
| **P2l-3b** | *(shipped)* **Rendered markdown's code is a different rule, and that is why this is two commits.** `.jp-RenderedHTMLCommon pre, code` takes the code family and the code line height but **`font-size: inherit`** (`rendermime/base.css:405-417`) - the prose size, not the code size. Ours draws both at `bodySmall`. Plus the gate that keeps `CodeText` the only place a code size is spelled out. | Instrumented: a fenced block in a markdown cell measures the prose size, not the code size. The gate red before, clean after. |
| **P2m-1** | *(shipped)* The collapser: transparent at rest, brand blue when active. `stripeResting` left `EditorPalette`, and the position it reversed is argued in `docs/DECISIONS.md` rather than deleted. | — |
| **P2m-2** | *(shipped)* The editor box: 1dp always, brand blue in edit mode, one shared modifier across the three fields rather than three copies. No elevation - the reference's shadow is *inset*, and why that matters is `docs/DECISIONS.md`. | — |
| **P2n** | *(shipped, and not as written)* **The prompt is the reference's, and a non-code cell has none.** The row asked for two fixed-width gutter slots on the strength of `flex: 0 0` at `inputarea.css:29-31`; that is the desktop rule, overridden at `width <= 760px` by `flex: 0 0 auto; text-align: left` (`:76-88`), which is this app's layout at every width. What was actually ragged was this app's invented `markdown`/`raw` label being pushed by the heading collapser. Removed - `_setPrompt`'s one caller is `CodeCell._updatePrompt` (`widget.ts:1693-1700`) - plus the reference's trailing colon (`inputarea.ts:283-290`). | Instrumented at 320dp: every code prompt at one x, the collapser leading its own header, and the `⋮` still on screen. Measured on both devices: `RAGGED by 48dp` → `aligned`. |
| **P2o-1** | *(shipped)* The mediator: which panel is selected, and the open notebook's headings and jump lambda, published by the editor and read by the panel. **No duplicated derivation** - `EditorViewModel` stays the one place headings, folds and numbering are computed. **The reference is a settable, nullable model** - `TableOfContentsPanel.model` (`packages/toc/src/panel.ts:41-60`), assigned from `labShell.currentChanged` and disposed with the widget (`toc-extension/src/index.ts:316,341-343,353`) - so `OpenDocumentOutline` is that property rather than an invention. | JVM over both types; instrumented that a real editor entry publishes and that **disposing it empties the holder** - checked against a build with the teardown removed before it was believed. |
| **P2o-2** | *(shipped, and the rail is not where this row put it)* The rail on medium and expanded, panels Files and Table of contents in the reference's rank order. **Beside the whole shell, not inside the list pane**: `hboxPanel` holds `leftHandler.sideBar` next to the split that contains both the panel and the document area (`packages/application/src/shell.ts:468-477`), which is what lets the area collapse to the bar alone. `TableOfContentsPane`, `TocPresentation` and `tocPresentationFor` are deleted - the rule is now "does the shell have a side area". | Instrumented over the rail, the panel and the wiring across the two `NavEntry`s; tablet at 1280dp - switch to ToC, jump to a heading, the panel stays and the notebook moves, and picking the showing panel again gives the notebook the width. |
| **P2o-3** | *(shipped, and the number did not move)* **The two-pane threshold, re-derived rather than kept.** An 80dp rail (`NavigationRailCollapsedTokens.NarrowContainerWidth`) changes the arithmetic 840dp came from, and 800dp portrait is a real device in a real orientation. Measure before changing the constant; It says **840dp stays**: measured, two panes in an 800dp window leave the document **334.7dp**, narrower than the 360dp the single-pane layout was designed for. The bar and the pane spacer cost 104dp, which is almost exactly the headroom that would have allowed lowering it. | `scripts/read_pane_widths.py` + its tests, and the measurement at 320 / 800 / 1280dp in `evidence/p2o-3-two-pane-threshold/`. |
| **P2o-4** | *(shipped as a decline, **reversed by P2y**, and the measurement is what survives)* **No bar.** Android's own guidance for `NavigationBar` is "three to five destinations of equal importance"; this app has two panels, and at one pane only Files is a destination at all. Measured, an 80dp bar is **20% of the 400dp notebook** at 320dp. Both panels are already reachable there - back for Files, `toc:show-panel` for the overlay - and P6d's Launcher is what would make three. | `p2o-4-one-pane-side-panels` at **320dp** and **800dp**, both entry points in one flow, both green; the cost measured from P2o-3's own hierarchies. |
| **P2n-3** | *(shipped)* **The heading caret leaves the header strip for the heading it folds.** The reference appends it to the *prompt node* at `position: absolute; right: 0` (`widget.ts:2386-2408`, `widget.css:154-168`) - a disclosure triangle beside the heading, in both the rendered and the source state. Its vertical placement is the reference's six per-level rules, re-derived: ours was 10dp low, measured. With it gone a heading cell's strip is a markdown cell's strip. | Instrumented: the caret is left of the heading and shares its line, at all six levels. Both devices. |
| **P2q** | *(shipped)* **The notebook's title has a row of its own.** It had 64dp of the 175dp `Heading Sections.ipynb` needs at 320dp, and truncated. The reference separates them: in single-document mode `#jp-title-panel` holds the name and nothing else (`application/style/titlepanel.css:10-37`), with the document's toolbar below it (`application/src/shell.ts:690-705`). 48dp + 64dp = `MediumTopAppBar`'s own 112dp. | Instrumented at 320dp: the title draws the width it wants, and its bounds sit above the actions'. Screenshots on both devices - which is what caught the doubled window inset. |
| **P2q-2** | *(shipped)* **The title's row collapses as the notebook is read.** Measured, the row cost 48dp of document at 320dp and put a fourth cell's source under the bottom bar - four flows caught it. `titleCollapseFraction` gives it back from the first scroll, driven off the cell list's own `LazyListState`. | JVM over the rule; instrumented that the action row rises when it collapses; on device the action row moves y=92 → y=64. |
| **P2r-1** | *(shipped)* **The bar names the active cell's type**, where the reference puts it: `cellType` in the notebook toolbar at rank 40 (`notebook-extension/schema/panel.json`), an `HTMLSelect` over the active cell's `model.type` (`default-toolbar.tsx:332-397`). In `TopAppBar`'s title slot, which is the free space *and* rank order. **The reference's `-` is declined**: `SelectionTopBar` replaces this bar the moment a run is marked, so the selection here is always one cell and a `-` branch is unreachable. | JVM over the labels and the placement rule; instrumented that the label fits at the threshold and sits clear of the actions, and that the bar leaves it alone at 320dp; `p2r-cell-type-on-the-bar` on the tablet at 1280dp. |
| **P2r-2** | *(shipped)* The overflow, where the bar has no room - measured, 72dp free at 320dp against a 73.3dp label. A `ReactiveToolbar` *moves* an item that does not fit into its popup opener (`ui-components/src/components/toolbar.tsx:570-700`), and this app's popup opener is `NotebookActionsMenu`. | Instrumented that the row is there where the bar has no room and **absent where it has**; `p2r-cell-type-in-the-overflow` on the emulator at 320dp. |
| **P2p** | The suite re-captured on the shipped build, both devices, one build. | `run-flows-sharded.sh`, evidence committed, **every screenshot looked at** - three defects in this project's history were green against every assertion and wrong in the picture. |

**P2r-1 has shipped, and it found a defect it did not fix.** What it settled is
`docs/DECISIONS.md`; the evidence is `evidence/p2r-cell-type-on-the-bar/`.

Three costs this file did not name:

- **The screenshot decided two things no assertion would have.** A `TextButton` draws its label in
  the brand colour, which made the control the only coloured thing in the row - and this app spends
  brand blue on the active cell's collapser (P2m-1) and the editor being typed into (P2m-2). And a
  `TextButton`'s own minimum is 40dp, so Material's 48dp is asked for rather than inherited.
- **A Maestro selector is a regex matched against the whole node**, not a substring. `E = mc^2`
  found nothing twice: once because `^` anchors, and once because the raw cell's field holds three
  lines. Both are now in the flow's own header, since three other fixtures have multi-line fields.
- **A markdown cell's source is drawn in the prose font and the reference draws it in the code
  font.** Frames 03 and 04 of this step's evidence are the same three lines before and after a
  change of type, and the family changes: `markdownSourceStyle` is `bodyMedium` where
  `codeSourceStyle` is the code family. `.cm-editor` takes `--jp-code-font-family`
  (`packages/codemirror/style/base.css:5-9`) with **no** per-cell-type override, and
  `packages/cells/style/` sets a font family in exactly two places, neither of them an editor. Filed
  as **P2s** below rather than folded in, which is how P2l-2 handed `CollapsedCell` to P2l-4.

**P2r-2 has shipped, and with it P2r is closed.** What it settled is `docs/DECISIONS.md`; the
evidence is `evidence/p2r-cell-type-in-the-overflow/`. One cost this file did not name: **the
harness had no rule for this width.** `WIDE WINDOW` is `>= 600dp` and `NARROW WINDOW` is `< 840dp`,
and declaring the latter would have placed this flow on the tablet in portrait at 800dp - a window
where the bar *does* carry the control and the flow's first assertion is false. `COMPACT WINDOW`
(`< 600dp`) is the exact complement of `WIDE WINDOW`, added to both the runner and the sharder in
one commit because `driver-rules.sh` exists so the two cannot disagree.

| # | Sub-step | Gate |
|---|---|---|
| **P2s** | *(shipped)* **A markdown cell's source takes the code font, and one function draws all three.** `markdownSourceStyle` was `bodyMedium` - prose - and is **deleted** rather than redefined: one editor font means one function. Both references agree and both were read - VS Code builds a markup cell's editor over `getBaseCellEditorOptions(language)` with no font override (`markupCell.ts:66,331`), its one markdown font setting being a `computeWebviewOptions()` value for the **rendered** preview (`notebookOptions.ts:748-772`); JupyterLab's `.cm-editor` takes `--jp-code-font-family` with no per-cell-type override. **Not P2l-3b**, which settled the opposite direction about the other surface, and `EditorThemeTest` now pins both in one test so the pair cannot drift. | Measured: three lines of markdown source at **60.0dp** against a raw cell's **54.0dp** - `bodyMedium`'s 20sp against `CodeText`'s 18sp - and matching after. Both devices at a matched w411dp, before and after, every frame looked at. |

**P2s has shipped, and the emulator hid a defect in its own flow.** What it settled is
`docs/DECISIONS.md`; the evidence is `evidence/p2s-markdown-source-font/`.

Three costs this file did not name:

- **A markdown cell's source is `codeSourceStyle` now, not a second style that matches it.**
  `markdownSourceStyle` is deleted rather than redefined: one editor font means one function, and a
  second name that happened to return the same thing is what let the two drift in the first place.
  P2t's new reference agrees with the old one here - VS Code builds a markup cell's editor over
  `getBaseCellEditorOptions(language)` with no font override (`markupCell.ts:66,331`), and its one
  markdown font setting is a `computeWebviewOptions()` value for the **rendered** preview
  (`notebookOptions.ts:748-772`).
- **The flow was green on the emulator and wrong on the tablet, for a reason no assertion carried.**
  Opening the editor takes the caret, so the soft keyboard covered the cell being compared against;
  `dev36` has a hardware keyboard and never showed one. Fixed with `hideKeyboard`, which is what
  `a5-markdown-editor.yaml` already did for the same reason. And `wm size 1080x2400` on an
  auto-rotated tablet is `2400x1080` - a two-pane landscape shell - so the driver locks portrait the
  way `p1g-one-container-driver.sh` does.
- **A one-second `assembleDebug` looks exactly like a stale APK.** The `before` build returned from
  the build cache, so it was checked for the deleted symbol before either half was captured:
  `classes4.dex` carries `markdownSourceStyle` before and no dex carries it after. A `before` frame
  taken from an `after` APK would have been green, identical and worthless.

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

**P2l-3b has shipped, and with it P2l is closed.** `richTextCodeStyle()` is *derived* from
`richTextProseStyle()`, so "inherit" is structural rather than a number that happens to match:
prose is `bodyMedium` at 14sp and `CodeText.size` is 14sp, and only the derivation says which rule
is meant. `FontFamily.Monospace` now has one home, `ui/editor/EditorTheme.kt`, kept there by
`scripts/check_monospace_home.py`. What it settled is `docs/DECISIONS.md`; the evidence is
`evidence/p2l-3b-rendered-code/`.

Two costs the plan did not name:

- **The gate is about where the font may be named, not about the size.** A size is easy to grep for
  and keep in step; a *new surface* reaching for `FontFamily.Monospace` directly is how four sizes
  happened, one reasonable commit at a time. The symbol row's exception therefore lives beside the
  rule as `symbolKeyStyle()` rather than as an exemption the gate has to know about.
- **The pair is captured at 411dp/420dpi, not on `dev36`.** A type-size judgement made at 1px per
  dp is made on proportions no reader sees, which `docs/DECISIONS.md` has said since P2l-4 and this
  is the first flow to act on.

**P2m-1 has shipped, and it is the first row in this phase that deletes a decision rather than a
number.** `.jp-Collapser` is transparent until `.jp-mod-active`, so a cell at rest carries no mark
at all; `stripeResting` left `EditorPalette`, `CellFrame` guards the draw the way it already guarded
`tint`, and the widths stay IntelliJ's - a hybrid, named as one. What it settled is
`docs/DECISIONS.md`; the evidence is `evidence/u3-cell-chrome/`.

Three costs the plan did not name:

- **The stale caption was in the fixture too, and it is rendered in all four frames.** The plan
  named the flow's `01-resting-stripes` comment. The markdown cell *inside*
  `.maestro/fixtures/u3-cell-chrome.ipynb` also asserted the old behaviour in prose - so the
  screenshot that proves the step would have carried a sentence contradicting it, in the largest
  type on screen. No assertion in the flow reads its own fixture's prose; only opening the picture
  does.
- **The colour this row deleted was the one palette value with no floor on it.** `EditorPaletteTest`
  pins a contrast ratio for every pair that carries information and asserted nothing about
  `stripeResting` - the suite had already implied it was decoration before the reference said so.
  That is the check to run on the next colour this app adds by eye: if no test can say what it must
  clear, ask what it is for.
- **The device rule can make a step un-closable, and it did.** The tablet left the USB bus
  mid-step. The emulator could confirm the resting frames, but not `04`: the last screenshot needs a
  real traceback, and `FakeKernelGateway` echoes the source back as stdout - the cell would have
  "succeeded" and the error stripe would never have been drawn. The plan's verification section said
  both devices were attached; attached at planning time is not attached at gate time.

**P2m-2 has shipped, and with it P2m is closed.** `Modifier.cellEditorBox(editing)` draws a 1dp
border on all three fields - brand blue on the one being typed into - and `EditorPalette` gained
`cellEditorBorder`. With the collapser transparent since P2m-1, this is now the only thing that
says where a cell's editor ends, which is the reference's own arrangement and why the two rows are
one phase. What it settled is `docs/DECISIONS.md`; the evidence is `evidence/u3-cell-chrome/`
(tablet, dark, 1280dp) and `evidence/u2-cell-mode/` (emulator, light, 320dp).

Three costs the plan did not name:

- **The floor this row was planned around does not survive measurement.** The plan asked for 3:1
  against the cell, decided by whichever IntelliJ ramp step reached it. No step that reads as a
  border does - Gray5 is 1.74:1 dark, Gray9 1.51:1 light - and **neither reference clears it
  either**, JupyterLab's own light border measuring 1.21:1 on its own surface. A value that passed
  would be a hard grey rule around every cell in the document. 3:1 now sits where 1.4.11 actually
  points, on the *state*: the brand border measures 3.23:1 dark and 4.03:1 light and is held to it.
  The full table is in `docs/DECISIONS.md`.
- **"Elevation in edit mode" was the wrong direction, and reading the rule is what said so.** The
  reference's `--jp-cell-editor-box-shadow` is `inset 0 0 2px` - an inner glow. An elevation casts
  outward and would lift the field off the cell, which the reference never does; it is declined
  rather than approximated.
- **The tablet found a defect this phase had already shipped, and it was not this row's.**
  `CellOutputsViewTest` asserted `CodeText.lineHeight.toDp()`, which under Android 14's non-linear
  font scaling is **19.2dp** at the tablet's `fontScale = 1.1` while a line of that style lays out
  to **20.67dp**. Green on `dev36` at `fontScale = 1.0`, red on the device the evidence rule names.
  Fixed in its own commit before this one - it measures a line of code now - and the lesson is
  P2l-4's again in a new form: **`dev36`'s defaults are not a device**, and a suite only ever run
  there is a suite run at one font scale.

**P2o-1 has shipped, and the mediator it asked for turned out to be a property the reference
already has.** `TableOfContentsPanel.model` is settable and nullable, assigned from outside by an
extension listening to `labShell.currentChanged` (`packages/toc/src/panel.ts:41-60`,
`packages/toc-extension/src/index.ts:316,353`). `OpenDocumentOutline` is that property and
`DocumentOutline` is the model reduced to what a panel draws with; `EditorScreen` publishes the
heading list it already computes for its own cells, so nothing is derived twice. What it settled is
`docs/DECISIONS.md`; the evidence is `evidence/p2o-1-side-panel-mediator/`.

Three things it cost that this file did not name:

- **Half of the row is not a mediator at all.** "Which panel is selected" has exactly one rule that
  is not Compose's own - picking the panel already showing *collapses* the area
  (`packages/application/src/shell.ts:2383-2394`) - and `SidePanel?` round-trips through
  `rememberSaveable` unaided. So it is a pure function and an enum, not a holder, and the holder
  this row named covers only the outline.
- **The holder is remembered by the composition, not owned by `AppContainer`,** which departs from
  where every other piece of app-level state here lives. The lifetime is the argument: the
  container outlives the activity by design (P1g), and a process-scoped holder would survive a
  rotation still carrying a jump closed over a disposed composition's `LazyListState`.
- **The lifetime assertion was checked against a build with the teardown removed.** It failed there
  - `expected null, but was: <DocumentOutline@...>` - which is the only reason it is believed.
  Three assertions in this project's history could not fail, and a test whose subject is a teardown
  is exactly that shape.

**P2o-2 has shipped, and the row it was written from named the wrong edge.** The rail is beside the
whole shell rather than inside the list pane, because that is where the reference's activity bar is
(`shell.ts:468-477`) - and the correction is load-bearing, not cosmetic: it is what lets a collapsed
side area leave the bar on screen while the notebook takes the width. `TableOfContentsPane`,
`TocPresentation` and `tocPresentationFor` are gone with it. What it settled is `docs/DECISIONS.md`;
the evidence is `evidence/p2o-2-side-panel/` (tablet, 1280dp) and `evidence/p2h-4-table-of-contents-folds/`.

Three costs the plan did not name:

- **The first run was green on every assertion and wrong in three places in the picture.** The
  panel's header drew on top of the status bar, the rail's "Table of contents" label spilled out of
  the rail and across the panel, and nothing separated the bar from what it was showing. The fixes
  are a `Scaffold` (which is also what pads a panel out of the status bar), an **icon-only** rail -
  the reference's own 32px strip - and a divider. Fourth time in this project's history.
- **It answered a question P2q left open rather than opening a new one.** "Whether the explorer's
  'Files' header and a notebook's title should agree" was filed as belonging to this rail; the
  answer is that both panels wear the same `TopAppBar`, and the ToC's is named for the document,
  which is `panel.ts:77-80`.
- **Renaming the flow broke four historical citations, and they had to be re-pointed at a commit
  rather than a path.** `p2h-3-table-of-contents-pane` is now `p2o-2-side-panel`, and four places
  cited a frame in its evidence directory as the record of a defect P2n fixed. They name
  `git show b4f157b:…` now. A capture that is *evidence of history* needs a revision, not a path -
  re-running the flow would have replaced the frame even without the rename.

**P2o-3 has shipped and the number did not move, which is the outcome the row asked for in
advance.** Measured at three widths, two panes in an 800dp window leave the document **334.7dp** -
narrower than the 360dp floor - so the tablet in portrait keeps one pane and 840dp stays. What it
settled is `docs/DECISIONS.md`; the evidence is `evidence/p2o-3-two-pane-threshold/`.

Two things it cost that this file did not name:

- **The rail is what keeps the threshold, not what lowers it.** An 840dp window used to leave the
  document 478.7dp and now leaves 374.7dp: the 80dp bar plus the 24dp pane spacer cost 104dp,
  almost exactly the headroom a lower threshold would have needed. The number is now 14.7dp above
  the floor rather than 118dp above it.
- **It found that this app gates the rail on the wrong thing**, and handed the finding to P2o-4
  rather than fixing it here. The rail is drawn when there are two panes; Material draws one from
  the *medium* class (600dp) and the reference's activity bar is not a function of pane count at
  all. So P2o-4's problem is not "the compact window" but **every window the shell draws as one
  pane** - which includes a real orientation of the real tablet.

**P2o-4 has shipped as a decline, and with it P2o is closed.** The named risk this row was written
around does not bite - the table of contents was never "reachable only after leaving the document",
because `toc:show-panel` opens the overlay from the notebook's own menu, and the flow now says so at
**both** one-pane widths rather than at one. What it settled is `docs/DECISIONS.md`; the evidence is
`evidence/p2o-4-one-pane-side-panels/`.

Three things it cost that this file did not name:

- **The decision was made by Android's guidance, not by taste.** `NavigationBar` is for "three to
  five destinations of equal importance". Two is below the floor, and at one pane only one of the
  two is a destination at all - Files is a screen, the table of contents is a surface over the
  document you are already in.
- **The cost was measured rather than asserted.** The notebook's cell list is 400.0dp at 320×640dp,
  so an 80dp bar is 20% of it and about three-quarters of a cell. P2q-2 fought for 48dp at that
  width; this would spend 80 to add a second path to two things that already have one.
- **It names what reopens it.** P6d's Launcher is the third destination, which this file already
  called "the second top-level destination a navigation rail has been waiting for since U7".
  Re-cost then, against these numbers rather than from scratch.

---

# P2t–P2z — the VS Code presentation re-base

P2l–P2p closed the presentation **against JupyterLab**. This phase re-bases it on **VS Code**,
which is a change of reference and not a change of taste - `docs/DECISIONS.md`, "The presentation
reference is VS Code; the behaviour reference stays JupyterLab", is the entry that permits it and
names its limits. Behaviour, the command surface, `nbformat` and the kernel protocol are untouched.

This phase was written as six changes. **Four of them were the cell's layout, and all four are
declined** - the run control into a left gutter, the execution count at that gutter's foot, the
markdown heading's chevron/play slot, and `CellHeader` retired with the overflow moving top-right.
The reason is a measurement taken with the gutter built, and it is in `docs/DECISIONS.md` under
"The cell keeps its header strip". The table below carries what replaced them.

What still changes:

1. **A cell's play is outlined, not filled**, where the header row already holds it (P2v-1).
2. **A heading's fold chevron shows rendered only**, so an open source runs full width (P2v-2).
3. The notebook's top grows a **breadcrumb** and a **kernel indicator at its right**.
4. **Workspace search and replace** - a panel on the rail, and a bottom navigation on phones.

## What was measured before this phase was written

Read out of the local clone at `f3fa55c3`, and every line number below verified against it:

| Fact | Citation |
|---|---|
| The run button is in a gutter **outside** the cell: `width: 35px; left: -35px` | `media/notebook.css:228-234` |
| The gutter is a layout constant: `cellRunGutter: 32` / `codeCellLeftMargin: 28`, and `36` / `8` compact | `notebookOptions.ts:118-136` |
| The gutter node is built per **code** cell | `view/renderers/cellRenderer.ts:273` |
| The execution count is a **separate** node, on the left focus indicator | `cellRenderer.ts:276-277` |
| It is drawn at the editor's **foot**: `top = editorHeight - 22 + statusBarHeight` | `view/cellParts/cellExecution.ts:139,200` |
| Its text is `[N]`, and **`[ ]` when the cell has never run** | `cellExecution.ts:94,133,181,194` |
| Its style: `font-size: 10px`, `--monaco-monospace-font`, `opacity: .7`, `width: 35px`, centred | `media/notebook.css:256-266` |
| A **markdown** cell gets a fold chevron on that same indicator, three states | `cellRenderer.ts:148`, `view/cellParts/markupCell.ts:467-477` |
| A markdown cell has **no run button** - `grep -c RunToolbar markupCell.ts` is **0** | `view/cellParts/markupCell.ts` |
| The cell's own toolbar defaults to the cell's **top-right** | `notebookOptions.ts:162` |
| The kernel control is `NotebookToolbar` / group `'status'` / order `-10`, which is the right-hand toolbar | `viewParts/notebookKernelView.ts:73-76`, `notebookEditorToolbar.ts:233,377` |
| Search is **notebook-aware**: a `.ipynb` hit is a *cell* hit, input held apart from rendered output | `contrib/search/browser/notebookSearch/searchNotebookHelpers.ts:39,46` |
| The search widget: query, replace-toggle, case / word / regex, include / exclude, preserve-case | `contrib/search/browser/searchWidget.ts:55-133`, `patternInputWidget.ts` |
| A cell's **duration** lives in the cell's status bar at its foot, not beside the run button | `contrib/cellStatusBar/executionStatusBarItemController.ts` |

Two measurements decided things this file would otherwise have guessed, and both are
`docs/DECISIONS.md`:

- **The default theme is `2026 Dark` / `2026 Light`, not Dark Modern**, and it is the outermost of
  a four-file `include` chain that overrides most of what it inherits. `editor.background` is
  `#121314`, not `#1F1F1F`. Resolve the chain before transcribing any value.
- **The 4.5:1 floor survives**, measured against the resolved themes: every syntax role clears it,
  with 2026 Light's `comment` at **4.55:1** the tightest. `EditorPaletteTest` is a live gate in
  P2u, not a formality.

## What this phase declines to decide by taste

- **The run gutter's 48dp is what declined it**, and the decision was taken with the gutter built
  rather than before it. P2l-4 settled that a touch target is *reserved space, not expanded
  bounds*, so a 35dp gutter cannot hold Material's 48dp minimum - and 48dp is 15% of a 320dp
  window against the reference's single-digit fraction of a desktop one. Both numbers, and the
  frame that settled it, are in `docs/DECISIONS.md`.
- **P2p is folded into P2z** rather than run first. It is the whole suite on two devices, and every
  frame it would capture is re-drawn by this phase. Each sub-step still gates on its own evidence,
  which is what `CLAUDE.md` actually requires.
- **P2s ships first.** It is already filed, it is one commit, and P2v rewrites the same files.

| # | Sub-step | Gate |
|---|---|---|
| **P2t** | *(shipped)* The clones land and the doctrine is written down: two rows in the reference table above, and three entries in `docs/DECISIONS.md` - the split of references, the theme-chain trap, and the contrast measurement. `/reference/` is gitignored, so the clone commits nothing. | Docs committed with both `HEAD` shas; the "empty for" claim about vscode-jupyter checked with `grep`, not assumed. |
| **P2u-1** | *(shipped)* `ColorRamp` re-sourced from the **resolved** `2026-dark.json` / `2026-light.json`, named by ramp position with its VS Code colour id in the doc comment. `surface` ← `editor.background`, `surfaceContainer` ← `sideBar.background`, `onSurface` ← `editor.foreground` as planned. **Two ids in this row were wrong, and measurement is what said so**: `primary` is `textLink.foreground` and not `button.background` - the app draws `primary` as text in all nine of its sites and as a filled button in none, where `button.background` measures 3.88:1 - and `outline` is `checkbox.border` and not `panel.border` (1.31:1, below what 1.4.11 asks of a component's bounds), with `panel.border` moving to `outlineVariant` where Material keeps its decorative divider. `docs/DECISIONS.md` carries both. | `ColorSchemeTest` re-pinned **and given a contrast gate** - the retiring IntelliJ ramp fails it on thirteen pairs. Explorer and notebook, both themes, both devices: `evidence/p2u-1-chrome/`. |
| **P2u-2a** | *(shipped, and the gate it was given is not the one this row asked for)* The eight `SyntaxPalette` values re-sourced from the resolved themes. **The floor could not be the gate**: this row planned for "`EditorPaletteTest`'s floor green per value", and measuring said light's `number` `#098658` is **4.42:1** on the cell - because `docs/DECISIONS.md`'s own table had measured the swap against `editor.background` rather than against the surface `notebook.cellEditorBackground` resolves to. **Parity won over the floor**, by decision, and the gate became *equality* to the resolved themes instead - the stronger assertion everywhere else. Two drivers were silently discarding evidence and both are fixed. | `EditorPaletteTest` pins all eight by equality; `resolve_vscode_theme_test.py` bounds the shortfall to one role in one theme. `u1-palette` and `step5-theme` on **both devices**, every frame sampled off the PNG - all five Python kinds at their VS Code values, zero stale IntelliJ values in any frame. |
| **P2u-2b** | *(shipped)* The markdown fold, which the re-base left without a citation: `styles()` folded `CODE` and `QUOTE` onto `string` and `MARKER` onto `keyword` on the strength of IntelliJ's `MarkdownHighlighterColors.java:24-41`, where VS Code has a rule for each - `markup.inline.raw`, `markup.quote`, `punctuation.definition.list.begin.markdown`. `STRONG` and `EMPHASIS` stay colourless, now because VS Code's own `markup.bold` / `markup.italic` foreground measures **1.21:1** and **1.03:1** against `plain`. **It found the quote's span extent is wrong, which is P2u-2c.** | JVM that the three no longer fall back and are mutually distinct; the parity pin extended to eleven scopes. `u1-palette` on both devices, all three sampled off the tablet PNG. |
| **P2u-2c** | *(shipped)* **A quote is green for its whole length, not just its `>`.** `MarkdownSyntax.markerOf` marked only the leading run of markup for both `ListItem` and `BlockQuote` - right for a list, since VS Code's `punctuation.definition.list.begin.markdown` is the bullet alone, and wrong for a quote: `markup.quote.markdown` is a **begin/end rule over the whole blockquote** with `>` a separate capture (`extensions/markdown-basics/syntaxes/markdown.tmLanguage.json`, `/repository/blockquote`). `BlockQuote` moves to `Node.kind()`, where every other block already gets its full range. Invisible until P2u-2b gave the quote a colour that is not the string colour. | JVM over the extent, including a two-line quote, plus the assertion that a list item's text stays **outside** its marker so the halves cannot be conflated again. Measured on the PNG: the quote's colour goes from **6 px to 733 px** on the tablet and **0 to 127** on the emulator. |

| **P2u-3a** | *(shipped)* **Two fields nothing drew.** `caretRow` and `outputForeground` were declared, valued in both palettes and cited to a `JUPYTER.*` key, and read by no composable at all - so P2u-3 would have looked up, measured and transcribed a VS Code colour for two surfaces that do not exist. Deleted, and `scripts/check_palette_consumers.py` is what stops the next one: `ColorRamp` has carried *"a position with no consumer is not here"* in prose since P2u-1 and `EditorPalette` had no gate for it. | The gate red on exactly those two before, green after; its own tests including a run over this repository. No frame changes, so no re-capture - nothing drew these. |
| **P2u-3b** | *(shipped)* The surfaces: `editorBackground` ← `notebook.editorBackground` (two hops to `editor.background`), `cellBackground` ← `notebook.cellEditorBackground` → `sideBar.background`, `cellMultiSelected` ← `notebook.selectedCellBackground`. **`res/values-night/colors.xml` moved with it** - the window is painted before there is a composition. Light's mark is **translucent** (`#C8DDF150`), which is where VS Code's `#RRGGBBAA` meets Compose's `0xAARRGGBB`. | The surface parity pin, red before and green after. `AppWindowTest` in **both themes on both devices** - four runs, which is what catches a window/palette drift. `u3-cell-chrome` re-captured on the tablet, every new surface sampled off the PNG and no stale value in any frame. |
| **P2u-3c** | *(shipped)* The lines and the secondary text: `cellEditorBorder` ← `notebook.cellBorderColor`, `stripeSelected` ← `focusBorder` (both `notebook.focusedCellBorder` and `notebook.focusedEditorBorder` resolve to it, which is why one field still serves both), `stripeError` ← `notebookStatusErrorIcon.foreground` → `errorForeground`, `lineNumber` ← `editorLineNumber.foreground`, `executionTime` ← `descriptionForeground` - the cell status bar states no colour of its own in `media/notebook.css`, so it inherits. **Dark's focus border is 70% opaque**, so the contrast test now composites before measuring rather than reading a hex no pixel ever is. | The chrome pin red before, green after. Re-measured: the resting border still beats the fill step (1.26:1 against 1.07:1 dark), and the active border paints as `#2F708C` at **3.17:1**, not the 5.09:1 its raw hex reads. `u3-cell-chrome` and `u4-execution-status` on the tablet, `#2F708C` sampled off the PNG at 4752 px. |
| **P2u-3d** | *(shipped)* **Rendered markdown's inline marks, which are not the source's.** `cellSelectionBackground` was documented as "the selected cell's surface" and its one reader was `richInlineStyles().code.background` - nothing had drawn a selected cell with it. Renamed `inlineCodeBackground`, given the `inlineCodeForeground` it needed, and both sourced from `textPreformat.*`. **The link moved too**, to `textLink.foreground`: a notebook's markdown preview is a webview and `webviewThemeMapping.ts:8-27` is the reference's own table for what themes it - `theme-code-*` onto the `textPreformat` pair, `theme-link` onto `textLink.foreground`. Rendered prose and its source now disagree **on purpose**: a link is a string in the source and the accent in the render. | The chrome pin extended; `a1c-markdown-render` on both devices, all three values sampled off the PNG. |
| **P2u-3e** | *(shipped)* **The highlights, and the two foregrounds VS Code does not state.** `matchedBracket` ← `editorBracketMatch.background`, `searchMatch` ← `editor.findMatchHighlightBackground`, `currentSearchMatch` ← `editor.findMatchBackground` - all three **translucent**, because `editor.findMatchForeground` and `editor.findMatchHighlightForeground` are registered `null` and the code has to show through. `searchMatchText` and `currentSearchMatchText` are **deleted**. **Measured cost**: the reference separates current from found by alpha alone, `#214555` against `#20414F` on the dark cell, **1.06:1**, with no border id set - so the app's pre-existing **bold** on the current match now carries the signal, recorded in `docs/DECISIONS.md` as a departure. | The wash assertion replaced two readability ones; `p2i-3-find` on both devices, both composites sampled off the tablet PNG (`#214555` and `#20414E`) with `#BBBEBF` code visible through each. |
| **P2u-3f** | *(shipped, and it corrected P2u-3d's citation)* Rendered markdown's **block** colours: the fenced block onto the `textPreformat` pair it shares with an inline span (`pre code` resets only padding), the block quote onto `textBlockQuote.background` **and** `.border` - a wash it did not have before, which is why the rule can be as faint as the reference draws it - the thematic break and the list marker onto the inherited body colour, which is what the notebook's renderer leaves them to. **The real stylesheet is the webview's base**, `contrib/webview/browser/pre/index.html:125-160`; `webviewThemeMapping.ts` exports the same ids under `theme-*` names but **nothing in the tree consumes them** - they are a compatibility surface for third-party renderers, and P2u-3d cited them by mistake. The values were right, the citation was not. | The chrome pin extended to fifteen ids; `a1c-markdown-render` on both devices, the quote wash, its rule and the preformat chip all sampled off the tablet PNG. |
| **P2v-1** | *(shipped)* **A cell's play is outlined, not filled** - `Icons.Outlined.PlayArrow`. A filled triangle at rest reads as "playing"; outlined reads as available, which is what an idle control means, and a notebook draws one per cell. Scope is the three run controls in `CellHeader`; `NotebookBar`'s *Run all* and `SelectionTopBar`'s play are toolbar commands and keep their filled glyphs. | JVM that `cellRunIcon` is the outlined glyph and is not the filled one - a value rather than an inline argument so the pin has something to hold. `u4-execution-status` on the **tablet** (its three frames are the play/stop states) and `a1-markdown` on the emulator. |
| **P2v-2** | *(shipped)* **A heading's fold chevron shows rendered only.** While the source is open the chevron is gone, the editor runs full width, and the header's play - the control that renders the cell - is the only thing on it. Retires P2n-3's both-states assertion. **Its cost is named in `docs/DECISIONS.md`**: the caret is `ToggleHeadingCollapse`'s only caller, so a heading is rendered before its section folds; `HiddenCellsRow` keeps expanding reachable either way. | Instrumented that the caret is absent unrendered and present rendered, and that an editing heading's field starts at the same x as a non-heading markdown cell's. `a5-markdown-editor`, `a4-markdown-contrast`, `a1c-markdown-render` re-anchored off the caret and re-captured; `p2f-1-heading-collapse` re-run. |
| **P2w-1** | *(shipped, in three commits, and it found a defect older than itself)* **The breadcrumb**, between the title row and the action row. **Ancestors only** - VS Code ends its trail with the file (`breadcrumbsModel.ts:142`) because its breadcrumb sits under a *tab bar*, and ours sits under a title row that already carries the name - and **absent at the workspace root**, where the reference renders `"no elements"`. A tap navigates the browser, which is JupyterLab's `cd` rather than VS Code's picker: the re-base moved the presentation reference, never the behaviour one. `crumbsFor` **moved** to `ui/PathCrumbs.kt` and the explorer's `Breadcrumb` composable to `ui/Breadcrumb.kt`, so one of each serves both surfaces. `docs/DECISIONS.md` carries all of it. | `PathCrumbsTest` over the segmenting; seven instrumented, including the measurement - **as a difference, because a total includes a status bar that differs by device**: each collapsing row reserves Material's minimum, so two rows are P2q-2's 112dp and three are **160dp**. `p2w-1-breadcrumb` on the emulator at 320dp and `p2w-1-breadcrumb-wide` on the tablet, whose two-pane branch is the one that **exposed a pre-existing race** in `ExplorerViewModel` - a poll re-listing the directory a navigation had just left. Fixed, pinned, and every explorer flow re-run. |
| **P2w-2** | *(shipped, in three commits, and this row's own gate was wrong)* **The kernel indicator at the action row's right**, which is JupyterLab's `kernelName` (rank 1000) and `executionProgress` (1002) as one control - a phone's row has no width for two 48dp targets answering one question, and VS Code already draws them as one. **Not a picker**, for the reason this row gave. **"Rightmost" was corrected by reading the reference's own layout**: a `ReactiveToolbar`'s popup opener is inserted first and never collected (`toolbar.tsx:391`, `:470-480`, `:661-700`), so it is always the last widget - ours is `NotebookActionsMenu`, and the indicator goes right of Find and *left* of the `⋮`, which is Android's convention too. Below 600dp the **name** goes and the status glyph stays, which reverses the reference's own removal order and is the one departure of the three in `docs/DECISIONS.md`; the others are the glyph set (the reference's two components disagree, and the retired one is followed) and P1c's stale session, which can read *Idle* for a kernel already stopped. | Split in three, each a commit. **P2w-2a** the state: `KernelStatus`, five values off JupyterLab's twelve, derived rather than stored; 12 JVM, nothing drawn so nothing re-captured (P2u-3a's precedent). **P2w-2b** the menu into a home of its own for its second anchor - and it found a defect, a `DropdownMenu` left expanded behind its own dialog, which `NotebookBarShutdownTest` now pins. **P2w-2c** the control: 7 instrumented over its placement and both widths, 5 JVM pinning the glyph per state. `p2w-2-kernel-indicator` on the **tablet** - No Kernel → Starting → Busy → Idle across a real run, then the menu, then back to No Kernel - and `p2w-2-kernel-indicator-narrow` on the emulator at 320dp. Every frame looked at, and **looking is what caught the resting glyph shouting**: the reference's filled bolt was the loudest mark in the row, and outlined is what shipped. |
| **P2x-1** | *(shipped, in four commits, and it needed a language this app did not have)* **The port.** `domain/port/WorkspaceSearcher` - a streamed flow of per-file matches. **Search only**: replace shipped on it in P2x-1c and came off in P2x-1e, on the reference rather than on taste. **`SearchQuery`'s shared home is `domain/model`, decided by measurement**: this row's own argument pointed at CLAUDE.md's document-logic rule, and a port takes a `SearchQuery`, so a package beside `syntax/` would have been the first project-local import into `domain` from outside it - one package across the whole layer today, and `docs/architecture.md` §1 says so. `replacementFor` **moved with it and went public**, because the disk adapter is what substitutes and `data ↛ ui`. **The globs needed transcribing, not delegating**: `java.nio`'s leading globstar requires a separator to be present, so `**/node_modules` matches `a/node_modules` and **not** `node_modules` - all eight registered defaults are written that way, so the platform matcher would have kept a top-level `node_modules` in every search silently. `docs/DECISIONS.md` carries that, the three files a walk declines, and the consequence P2x-5 inherits. | Split in four, each a commit. **P2x-1a** the move: nothing drawn, so its gate is the suite plus the five find-bar flows re-run on **both devices** (`evidence/p2x-1-search-port/`), because a move that changes behaviour is what a green unit suite misses. **P2x-1b** `com.jupydroid.app.glob` - and **the gate for a transcription is equality, not behaviour**: `PathGlobParityTest` holds the regex source for 25 patterns against `parseRegExp`'s own output, extracted from `glob.ts` and run under node, mutation-checked on a one-character change. **P2x-1c** the port, `FakeWorkspaceSearcher` and a 14-test contract. **P2x-1e** took `replaceAll` back **off** the port - uncalled, and mis-shaped: the reference's `IReplaceService` goes through the editor-model stack and takes the matches on screen, where ours took a query and re-ran the search, so a file changed in between would have been rewritten against matches nobody saw. **P2x-1d** `DiskWorkspaceSearcher`, 20 JVM: binary by ripgrep's NUL rule (VS Code states none and delegates the whole question), `.ipynb` skipped until P2x-4 can name a cell rather than reporting `"cell_type"`, and a directory symlink **not followed** where `search.followSymlinks` is true, because a cell can `os.symlink` inside a rootfs. |
| **P2x-2** | *(shipped, in two commits, and the row was wrong about its own reason)* **`SidePanel.SEARCH` on the rail**, panel wired and empty. This row said the order was VS Code's activity-bar order *"which is also JupyterLab's rank order"* - **and the second half is false, checked in both trees**: JupyterLab's five `shell.add(…, 'left', …)` call sites hold no search at all, its `documentsearch` being scoped to `currentWidget` (`documentsearch-extension/src/index.ts:227`), which is P2i's find bar. So it takes **both** references to state the order, and neither could state it alone: the browser is first in both (rank 100; `order: 0`, `explorerViewlet.ts:265`), **search is second and only VS Code says so** (`order: 1`, `search.contribution.ts:59`), **the table of contents is last and only JupyterLab says so** (rank 400) - VS Code's Outline being a view *inside* the explorer's container (`outline.contribution.ts:26-38`), not a container of its own. They answer different halves and agree where they overlap, which is the same split P2x-1e already recorded. **No placeholder in the panel, and that is the reference rather than an omission**: VS Code's search view with an empty query is its input box and nothing else, so an invented string would be one P2x-3 deletes. | **P2x-2a** the rail and the panel: JVM over the three-panel order with both citations, instrumented that the rail carries three and that a second tap collapses, and `p2x-2-search-panel` on the tablet (WIDE WINDOW). **The panel's place was measured rather than seen** - an empty panel has no visible right edge, so a hierarchy dump says it: rail 80dp, panel `[122,0][662,1200]` = the 360dp list pane, document area 815dp (`evidence/p2x-2-search-panel/`). **P2x-2b** is a defect this sub-step widened rather than introduced - see the row below. |
| **P2x-2b** | *(shipped)* **A window that narrows while a panel is up still draws the file browser.** `sidePanel` is `rememberSaveable` and the rail is drawn only where there are two panes, with no guard between them - so a tablet rotated to portrait (1280dp landscape is two panes, 800dp portrait is one) kept drawing the panel it had with **no rail to leave it by**, and the browser could not be reached again without killing the app; collapsed, the pane drew nothing at all. Pre-existing since **P2o-2**, and P2x-2 took it from two panels to three. `p2o-4`'s own header states the rule that was being broken: *"where the shell draws one pane there is no side area"*. Fixed by deriving the showing panel once, beside the `directive` that already spells the other half of the same rule - an **override rather than a reset**, so rotating back restores the panel the user had chosen. | Red first and measured: 9 instrumented / 2 failed before, green after. The two cases drive the width as **state**, which is the half of a rotation composition sees; the device half is `p2x-2-rotation` (TABLET ONLY, NOT A SUITE FLOW), because a real configuration change is not something a Compose test drives. `p2o-4-one-pane-side-panels` re-run on the emulator at 320dp as the regression that the narrow shell is untouched. |
| **P2x-3** | *(shipped, in six commits, and this row asked for one thing it does not get)* **The query UI** - query box, three option toggles, include / exclude behind the `…`, results grouped by file, the cap drawn rather than silent, and a tap that opens **at** the match. **The replace toggle is declined and ships in P2x-5**, where the behaviour behind it lives: P2x-1e took `replaceAll` off the port because the reference replaces through the editor-model stack over the matches on screen, and a Replace All that cannot replace is the control `docs/DECISIONS.md` refuses under the export formats. **Three departures, each measured**: the option glyphs are drawn as type (`Aa`, `ab`, `.*`) because Material ships none of the three and the reference's own icons are typographic; the panel is **one scrolling surface**, box included, because with the glob boxes open its fixed chrome is taller than an IME leaves it; and a revealed match goes to the **top** rather than the reference's centre. **Out of scope, and named rather than forgotten**: the *"Use Exclude Settings and Ignore Files"* toggle (`userDisabledExcludesAndIgnoreFiles`) needs a settings screen to disable, so it waits for P6c. | Split in six, each a commit. **3a** the flow's `Complete(limitHit)`, nothing drawn. **3b** the ViewModel, 13 tests, three rewritten because a mutation walked through them. **3c** the panel; found its own `imePadding` defect. **3d** the toggles: `p2x-3-search-options`, each proved by a query whose *answer* it changes, and `scripts/read_search_box_width.py` measuring what the box has left (336dp, three 48dp targets, **192dp** for the query). **3e** the globs: `p2x-3-search-scope` over three files, because `*.py` over two `.py` files removes nothing; two defects and two harness readings. **3f** the landing: `p2x-3-open-at-the-match`, one match on **line 47 of 60**, and the flow states in its own header that it cannot assert where the editor scrolled to - the screenshot and a scroll offset do. 341/341 instrumented on both devices. |
| **P2x-4** | *(shipped, in four commits, and it corrected its own row twice)* **Notebook-aware matching** - a `.ipynb` hit is a *cell* hit, and the skip P2x-1d put in is gone. **The parity claim this row used to make is false as written**: cell-aware search of a *closed* notebook sits behind `search.experimental.closedNotebookRichContentResults`, default **false** (`search.contribution.ts:287-291`), so out of the box VS Code hands one to ripgrep and reports `"cell_type"`. What shipped is that experimental branch - which JupyterLab's own document search does unconditionally, so it takes both references again. **The cell is named by index**, `INotebookCellMatchNoModel.index` and the reference's own `rawCell#${index}` fallback, and here the id would be worse than absent: `CellFile.toDomain` mints a `UUID` for a cell nbformat left without one. **Outputs are declined and named rather than forgotten** - `SearchHit.outputAt` indexes the *rendered* outputs and "what text an output shows" lives in `ui/editor/`, which `data/` may not import; the open risks carry the prerequisite. **The replace half of this row is P2x-5's**, since P2x-1e left no replace here to round-trip. | Split in four, each a commit. **4a** the model, 6 JVM. **4b** the walk, 6 JVM with **two mutation-checked** - reading a notebook as raw text turns all six red, including the two that were green before it, and removing the decline around `readNotebook` turns the unreadable-notebook one red; five P2x-3 flows re-run on the tablet, whose README states in as many words that no notebook is on screen in any of them. **4c** the label: `p2x-4-notebook-search`, two matches on their own line 1 in two different cells - the case that makes the cell load-bearing - and `cell_type` finding nothing. **4d** the landing: `p2x-4-open-at-the-cell`, the marker in the fifteenth cell **and** under a collapsed heading, so neither an unscrolled notebook nor a scrolled one could show it. It found its own defect, green, and the screenshot is what said so. 347/347 instrumented on both devices. |
| **P2x-5** | *(shipped, in eleven commits, and it closed this file's one named prerequisite)* **Replace across files, through the editor's model rather than the filesystem** - which is what the reference does, and not what P2x-1c built. `ReplaceService.replace` hands `ResourceTextEdit`s to `IBulkEditService` (`replaceService.ts:115-118`); `BulkTextEdits` resolves a text model for **every** resource, open or not (`bulkTextEdits.ts:227`), edits the models, and only then saves each (`replaceService.ts:136`). Three things follow: replace-all is **one undo** across every file (`MultiModelEditStackElement`, `:319-330`); a conflict **throws** `"has changed in the meantime"` on a `versionId` mismatch rather than prompting (`:213-216`); and the confirmation is **counted** - *"Replace {0} occurrences across {1} files?"* with the same counts reported after (`searchView.ts:831-837`, `:860-925`). It takes the **matches already on screen**, never a query to re-run. **The prerequisite is built and the open risk it came from is closed**: `OpenDocuments` bridges two `NavEntry`-scoped ViewModels, and it holds **one** document rather than a map because `chooseDetailThatFitsTheSurface` cannot have two open at once. **Three departures, each measured and each in `docs/DECISIONS.md`**: Replace All is a labelled button on a row of its own, because Material's `find_replace` at 24dp reads as *refresh* beside this panel's own magnifier; an undo brings the **results** back with the files, because the panel is what performed it and nothing else would have shown that it happened; and that undo is offered beside the report rather than in a snackbar, which would carry the only route to it away in seconds. `truncateValue`'s ten-line clamp is **declined** - a `singleLine` box cannot hold a newline, so transcribing it would be unreachable code. | Split in eleven, each a commit. **5a-5d** the model, the registry, the engine and the undo - nothing drawn, 18 JVM over the engine alone. **5f** the sixteen counted strings **by equality**, mutation-checked. **5g** the ViewModel, 13 JVM, mutation-checked three ways; split twice, by detekt at 13 of 11 and then by the file-length gate at 609 lines, both pointing at one seam. **5h** the button, the dialog and the report: 9 instrumented - three of which went red on a real finding, that a two-file tree is never composed below this window's fold - and `p2x-5-replace-all` on the tablet, a notebook and a `.py` rewritten in **one** action with `run-as cat` of both committed beside the frames. **5i** the undo: `p2x-5-undo-a-replace-all`, whose driver **diffs `notes.py` against its fixture** and gets it back byte for byte. **5j** `p2x-5-replace-under-an-open-document` - two panes, the editor **never closed** between the replace and the assertion, which is the one gate no JVM test can stand in for. **5k** the docs, the re-count, and all eight panel flows re-run on the tablet - which is what found `p2x-3-search-scope` red since 5e. |
| **P2y** | *(shipped, in five commits, and this row's own gate was wrong)* **The one-pane activity bar**, which **reverses P2o-4**. Two destinations, Files and Search; the table of contents stays the overlay, because where the bar is drawn a panel *is* the window and there is no document for an outline to belong to. Two is **off Material's own 3-5**, recorded in `docs/DECISIONS.md` rather than padded. **"Below 600dp" would have shipped a hole**: the rail is drawn at `maxHorizontalPartitions > 1` = 840dp, so 600-840dp - this project's own tablet in portrait - would have had neither control and no route to search at all. The rule is `!twoPane`, which is what P2o-3 had already recorded and this row had not caught up with. It is VS Code's activity bar **relocated**, `ActivityBarPosition.BOTTOM` (`layoutService.ts:75-80`), not an Android control invented beside one. | **P2y-a** the rule, drawn by nothing: 6 JVM. **P2y-b** `SidePanelBar`, drawn by nothing: 5 instrumented, written to be read beside `SidePanelRailTest`. **P2y-c** the shell draws it: 5 instrumented and P2x-2b's narrowing case split in two, `p2y-bottom-navigation` at 320dp, `p2o-4` re-run **in the same commit that changed it**. **P2y-d** is a defect the tablet found. **P2y-e** this file. Cost re-measured rather than quoted, both devices: `evidence/p2y-bottom-navigation/measurement.txt`. |
| **P2z** ✅ | The suite re-captured on the shipped build, both devices, one build - absorbing **P2p**. **Shipped**: 118 green from sha `68b795af`, then 440 frames read across 107 directories (`evidence/p2z-suite-sweep/frames.md`) and **nine defects fixed**, P2z-b…P2z-j. | `run-flows-sharded.sh`, evidence committed, **every screenshot looked at**. |

## What P2u-3 is transcribing, measured before it starts

Every id below is resolved: the theme's own value where it sets one, otherwise the chain of
`registerColor` defaults, which is where most of the notebook ids land - **2026 Dark sets no
`notebook.*` colour at all** and 2026 Light sets only two.

| Field | VS Code id | Resolution | Dark | Light |
|---|---|---|---|---|
| `editorBackground` | `notebook.editorBackground` | → `editorPane.background` → `editor.background` | `#121314` | `#FFFFFF` |
| `cellBackground` | `notebook.cellEditorBackground` | → `sideBar.background` | `#191A1B` | `#FAFAFD` |
| `cellMultiSelected` | `notebook.selectedCellBackground` | dark → `list.inactiveSelectionBackground`; light set | `#2C2D2E` | `#C8DDF150` |
| `cellEditorBorder` | `notebook.cellBorderColor` | dark → `transparent(listInactiveSelectionBackground, 1)`; light set | `#2C2D2E` | `#E5E5E5` |
| `stripeSelected` | `notebook.focusedCellBorder`, `notebook.focusedEditorBorder` | both → `focusBorder` | `#3994BCB3` | `#0069CCFF` |
| `stripeError` | `notebookStatusErrorIcon.foreground` | → `errorForeground` | `#F48771` | `#AD0707` |
| `lineNumber` | `editorLineNumber.foreground` | set | `#858889` | `#606060` |
| `executionTime` | `descriptionForeground` | set - the cell status bar states no colour of its own and inherits | `#8C8C8C` | `#606060` |
| inline code bg / fg | `textPreformat.background` / `.foreground` | set | `#262626` / `#8C8C8C` | `#ECECEC` / `#606060` |
| `matchedBracket` | `editorBracketMatch.background` | set | `#3994BC55` | `#0069CC40` |
| `searchMatch` | `editor.findMatchHighlightBackground` | set | `#27678280` | `#0069CC1A` |
| `currentSearchMatch` | `editor.findMatchBackground` | set | `#27678290` | `#0069CC40` |
| `searchMatchText`, `currentSearchMatchText` | `editor.findMatchForeground`, `editor.findMatchHighlightForeground` | **both registered `null`** | — | — |

**Three things in that table are findings rather than transcription**, and each is why its
sub-step exists: the two fields nothing draws (P2u-3a), the field whose name says "selected cell"
and whose one reader is rendered markdown's inline code (P2u-3d), and the two foregrounds the
reference declines to state at all - VS Code keeps a match readable by making the wash
**translucent** over the code, where this app makes it opaque and restates the text on top
(P2u-3e). **Alpha is load-bearing** in six of these values and this app's palette has been opaque
throughout, so P2u-3e is the one that changes how a colour is applied rather than only which.

**Two script gates will need extending, and that is part of the work rather than a surprise:**
`check_ui_dimensions.py` sees the new gutter constant, and `check_monospace_home.py` sees the
execution-count label. Extending a gate is allowed; routing around one is not.

---

# P4 — Terminal

Everything the server side needs is **already shipped**: `jupyter_server_terminals==0.5.4`,
`terminado==0.18.1`, `ptyprocess==0.7.0` (`tools/rootfs/requirements.lock`), and
`ServerApp.terminals_enabled` defaults to **True** — not by its `Bool()` declaration, which alone
would be False, but by the `@default` under it (`serverapp.py:1997-1999`). The extension enables
itself through `usr/etc/jupyter/jupyter_server_config.d/jupyter_server_terminals.json`, which is
in the tarball.

## Two blockers, verified in the tarball rather than assumed

**One: every shell in the rootfs is a dangling link.** Alpine's `bin/sh`, `bin/ash` and every other
applet is an **absolute** symlink `-> /bin/busybox` (`tar -tvzf $T | grep '^l'`). On device that
resolves against Android's root, not the rootfs. `bin/busybox` itself is real — a 919 KB aarch64
PIE — but its `PT_INTERP` is `/lib/ld-musl-aarch64.so.1`, equally absolute and equally dangling,
and a rootfs file cannot be `execve`'d at targetSdk 36 in any case.

**Two: `argv[0]` cannot carry the applet name, and this corrects the fix this section used to
state.** It read *"applet dispatch is by `argv[0]`, which the caller sets independently of the
path"*. That is true of `execve` and false of the caller we actually have.
`ptyprocess.PtyProcess.spawn` does `command = which(argv[0])` and then **`argv[0] = command`**
(`ptyprocess/ptyprocess.py`), overwriting whatever name was asked for with the resolved path. And
busybox falls back to reading the applet out of `argv[1]` **only** when its own basename is prefixed
`busybox`, which `libbusybox.so` is not - so it dies *"applet not found"*, exit 127, and passing
`sh` as `argv[1]` does not rescue it. Run against the shipped busybox rather than argued from
source, with a control showing the multiplexer is present and working:
`evidence/p4-terminal/busybox-dispatch.md`. And `lib*.so` is not negotiable - it is what AGP packages
into `lib/<abi>/` and what Android extracts to `nativeLibraryDir`.

**So the shell is reached through a path whose *basename* is `sh`.** Stage busybox as
`jniLibs/<abi>/libbusybox.so`, patchelf'd `--set-interpreter` / `--set-rpath` exactly as
`libpython3.so` is in `tools/rootfs/stage-app-artifacts.sh`, and have `BaseSpaceInstaller` point the
rootfs at it, the way it already points the loader. Dispatch then comes from the basename of the
path ptyprocess resolves, and the SELinux exec check lands on the symlink **target** in
`nativeLibraryDir` — the mechanism `evidence/step8.3/musl-runs.txt` already measured for the
interpreter.

**And `bin/sh` alone is not enough, which is worth saying because the obvious gate hides it.** The
rootfs holds **304** applet links and every one is the same absolute `-> /bin/busybox`, so a shell
with only `bin/sh` repaired is a shell in which `ls`, `cat`, `grep`, `vi` and `top` are all *not
found* - while `sh -c 'echo ok'` still passes, because `echo` is an ash builtin. The rewrite is
therefore all 304, and the cheapest correct shape is **one** maintained link: point `bin/busybox` at
the native library on every launch, and make the applets *relative* to it once at extract time.
`execve` resolves the chain to find the inode, and busybox reads its applet from the caller's
`argv[0]` - which for a shell forking `ls` is `"ls"`, not the path - so one indirection costs
nothing. Three other absolute links exist and are harmless: `etc/mtab -> /proc/mounts`, which is a
real Android path, and two `etc/ssl1.1/` entries nothing reads.

## The protocol

Read out of the **shipped** `terminado` and `jupyter_server_terminals` rather than JupyterLab's
TypeScript alone, because two of the claims this section used to make were incomplete.

- REST `api/terminals` (`restapi.ts`, `TERMINAL_SERVICE_URL`; handlers in
  `jupyter_server_terminals/api_handlers.py`) — `POST {name, cwd}` returns **200 with the model**,
  not a redirect (`restapi.ts` errors on `status !== 200`), and a relative `cwd` is resolved against
  `server_root_dir` by the handler itself; `GET` lists; `DELETE` sets 204.
- WebSocket `/terminals/websocket/<name>`, registered in `jupyter_server_terminals/app.py` as
  `r"/terminals/websocket/(\w+)"`. Messages are JSON arrays `[type, ...content]` (`default.ts`:
  `const msg = [message.type, ...message.content]`).
- **Five message types, not four.** The socket's *first* frame is `["setup", {}]`, sent from
  `TermSocket.open` (`terminado/websocket.py`), and the reference client refuses to call itself
  connected until it arrives (`default.ts:300-304`). The rest are `["stdout", text]` and
  `["disconnect", 1]` from the server, `["stdin", text]` and `["set_size", rows, cols]` — read as
  `command[1:3]` — to it.
- **A reconnect replays.** `TermSocket.open` drains `terminal.read_buffer` into the new socket
  before anything else. That is what makes backgrounding the app survivable, and it is the
  server's behaviour rather than something this app has to build.
- `ServerApp.terminado_settings` (`serverapp.py:1492`) carries `shell_command`, which `app.py`
  prefers over `which("sh")`. Pinning it buys a second thing measured in the same file: the `-l`
  login-shell append is guarded on `shell_override is None`, so a pinned shell never gets it.

## What the server already does, so this phase does not

`shutdown_no_activity` early-returns on `extension_manager.any_activity()`
(`serverapp.py:2602-2618`, `extension/manager.py:463-468`), and the terminals extension's
`current_activity()` reports its live terminals — so a server with a terminal open and no kernel
does **not** shut itself down. **This app's own stopper is the one that would**; that is a defect
this phase introduces rather than inherits, it is in the open risks, and **P4k** closes it.

## The emulator has a reference after all, and this file said it did not

P4d used to read *"Nothing existing is reusable"* and *"IntelliJ does not answer this either"*, and
concluded that the buffer *"is written against the VT spec, and that gap is stated here so nobody
looks for a reference that is not there."* **Half of that is right and the conclusion is wrong.**

Right: `RenderedOutput.withoutAnsiColours` (`RenderedOutput.kt:96-102`) is a regex *stripper* for
SGR escapes in tracebacks and cannot become a grid; VS Code's emulator is xterm.js, an npm
dependency (`package.json:143-144`); IntelliJ's is JediTerm, an external library
(`plugins/terminal/BUILD.bazel:55-56`). Neither of those trees carries a buffer to read.

Wrong: **`reference/research_repos/termux-app` does**, and it was checked out all along - the
reference table above simply never listed it, so P4d only ever cited its extra-keys row.
`terminal-emulator/` is a complete VT emulator in Android-flavoured Java: `TerminalEmulator.java`
(2617 lines, an explicit `ESC_*` state machine covering CSI with every intermediate, OSC, DCS and
APC), `TerminalBuffer.java`, `TerminalRow.java`, `TextStyle.java`, `TerminalColors.java` and
`WcWidth.java`. It ships **19 JUnit classes** that are a conformance suite in their own right -
`CursorAndScreenTest`, `ScrollRegionTest`, `RectangularAreasTest`, `DecSetTest`, `ResizeTest`,
`HistoryTest`, `UnicodeInputTest` and the rest. `terminal-view/TerminalRenderer.java` (249 lines)
answers the half after that: drawing a buffer with a monospace font.

**And the licence permits it.** `termux-app/LICENSE.md` is GPLv3 *with an explicit exception* for
`terminal-view` and `terminal-emulator`, which are **Apache 2.0**, inherited from Jack Palevich's
Android Terminal Emulator. So these are read - and where transcribed, attributed - like every other
reference in this repo, rather than looked at and reimplemented at arm's length.

What this does **not** change: P4e-P4h are still ours to write, in Kotlin, in `terminal/`, free of
`android.*`, under TDD. What it changes is that "correct" now has an oracle, and the tests come from
one instead of from a reading of the spec.

## The sub-steps

**Re-lettered, and P4a/P4b are swapped on purpose.** Proving the PTY needs no build change at all —
`pty.fork()`'s child can write and `_exit` without any shell — and its answer reshapes the phase, so
staging busybox behind it would be work that a refusal throws away. The old P4d ("a terminal
emulator in Compose") is now **P4e–P4h**, and `docs/DECISIONS.md` was updated where it cited it.

| # | Sub-step | Gate |
|---|---|---|
| **P4a** | **SHIPPED.** **Prove the PTY before anything is built.** terminado reaches a shell through `ptyprocess`, i.e. `/dev/ptmx` and `pty.fork()`. Whether that is permitted under Android's app seccomp filter is **unverified**, and if it fails the whole phase changes shape. Termux raises the prior without closing it: `terminal-emulator/src/main/jni/termux.c:36-51` opens `/dev/ptmx` and calls `grantpt`/`unlockpt`/`ptsname_r` from an ordinary app process, so the device node is reachable - but that process is bionic at `targetSdkVersion=28`, and ours is musl under a preloaded SIGSYS handler at 36. Driven from an instrumented test through the existing `PythonRunner`, `Assume`-skipped where `SpaceAbi.of` has no rootfs so it is green-by-skip on the emulator. Three probes: fork a pty and read the child's bytes; exec `sys.executable` inside it; `TIOCSWINSZ` and read the size back. | A master fd carrying the child's bytes, **or** the failure and its errno — either outcome closes the sub-step. `tmp/sigsys.log` pulled after the run names any refused syscall. Both into `evidence/p4-terminal/`. |
| **P4b** | **SHIPPED, then corrected by P4b-2.** Stage busybox into `jniLibs`, and give the rootfs **one** link that has to be maintained. `bin/busybox` -> `<nativeLibraryDir>/libbusybox.so`, relinked on every launch the way the loader already is; the **304** applet links are rewritten *relative* at extract time (`bin/ls` -> `busybox`, `usr/bin/awk` -> `../../bin/busybox`) so they never go stale when `nativeLibraryDir` moves. Bump `BUNDLED_ROOTFS_VERSION`. | JVM: the applet links resolve, `bin/busybox` points into `nativeLibraryDir`, and a second `installIfNeeded()` repairs it after it is left pointing at a stale path. Device: `run-as … bin/sh -c 'ls /'` - **not `echo`**, which is an ash builtin and would pass with every applet still broken - plus `ls -l` of `bin/busybox` and a sample applet, into `evidence/p4-terminal/`. |
| **P4b-2** | **SHIPPED.** *Not planned - P4c's gate found it.* Alpine's busybox is built `CONFIG_FEATURE_SUID=y`, so **every applet dispatch calls `setgid`/`setuid`** (`libbb/appletlib.c:965`), both refused to app processes by bionic's `SECCOMP_BLOCKLIST_APP.TXT` - and busybox carries no SIGSYS handler, so `sh`, `ls` and `echo` alike died ~7 ms after exec having printed nothing. **P4b's gate could not see it**: it ran under `adb shell run-as`, which is not zygote-spawned and so carries no app filter. busybox is now built from Alpine's own config with that one line flipped - Termux's setting, for Termux's reason. | `scripts/check_busybox_seccomp.py` (config + the `check_suid` fingerprint in the staged ELF); `BusyBoxAppletsDeviceTest` forks applets **from inside the app**, never through `run-as`; `p4b2-shell-escapes` runs `!head`, `files = !ls` and `%pip` in a real notebook on the tablet. `evidence/p4-terminal/busybox-under-app-seccomp.md`. |
| **P4c** | **SHIPPED.** Pin the shell with `--ServerApp.terminado_settings`, rather than trusting `which` - which also drops the `-l` login-shell append, guarded on `shell_override is None`. `SHELL` is set alongside it, and that is load-bearing rather than tidy: it is what `Popen(executable=…)` uses for a cell's **capture** escape, `files = !ls`, which returned `''` without it. | JVM over `jupyterServerArgs`; then on the tablet `TerminalServiceTest` - `GET` → `200 []`, `POST` → 200, the pinned argv read back out of `/proc` with no `-l`, still listed 2 s later, `DELETE` → 204. `evidence/p4-terminal/terminal-service.md`, `shell-escapes.md`. |
| **P4d-0** | **SHIPPED.** *Not planned - reading the references for P4d found it.* `jupyter_server` declares no default for `root_dir`; the `@default` under it returns `os.getcwd()` (`serverapp.py:1839-1848`), so upstream the server's cwd **is** its root_dir. X2h set `--ServerApp.root_dir` and left `ProcessBuilder` alone, splitting them - and a terminal *inherits* the process cwd whenever the `POST` carries none (`terminado/management.py:225-232`), where a kernel's comes from `root_dir` via `cwd_for_path`. So every shell P4d could open would have started in Android's read-only `/`. `JupyterServerLauncher` now calls `.directory(rootDir)`. | JVM: `JupyterServerWorkingDirTest` - the directory **and** its equality with the `--ServerApp.root_dir` argument, since checking only the first would pass for a launcher pointed anywhere. Tablet: `TerminalServiceTest.theServerProcessRunsInTheWorkspaceItIsRootedAt` reads `/proc/<pid>/cwd` off the running server - measured `/` with the line backed out. `evidence/p4-terminal/server-working-dir.md`. |
| **P4d** | **SHIPPED in five commits**, and it corrected its own gate. `TerminalGateway` + `RealTerminalGateway` over a socket seam and a REST lifecycle, `FakeTerminalGateway` in `data/fake/`, built through `PerRunningServer` off the one `BaseSpaceServer`. **P4d-2a is a refactor the phase found rather than planned**: `KernelSocket`/`KernelSocketEvent` were never the kernel's - `send(String)`, `close()` and three events are the *transport* - so they are `JupyterSocket`/`JupyterSocketEvent` and the OkHttp half is written once, leaving each opener its URL. `BaseSpaceTerminalGateway` needs **no** stale-name registry, unlike the kernel's: the wire already reports it. | JVM: both implementations on one contract, the codec, the two adapters over MockWebServer. Tablet: `TerminalGatewayDeviceTest` 4/4 - `setup` arrives, a typed command **runs**, the shell is in the workspace, a closed terminal leaves the listing - and 10/10 beside `TerminalServiceTest`; green-by-skip on x86_64. `evidence/p4-terminal/terminal-gateway.md`. |
| **P4d-e** | *The gate this row used to state was hollow, and the bytes are what showed it.* `send("echo ok\n")` → `ok` passes on the pty's **echo** - canonical mode echoes the line before any program sees it - and the `pwd` check passed on the **prompt**, since busybox ash's `PS1` is `\w \$` and `read_buffer` replays it the instant an attach connects. Both now assert something only execution can produce. | `echo $((6*7))` → `42`; `echo cwd=$(pwd)` → `cwd=<workspace>`. `docs/DECISIONS.md`, "A pty echoes". |
| **P4e** | **SHIPPED in four commits. The screen buffer, re-scoped to the storage layer.** The row's cells, the grid that holds rows, the style packed into each cell and the East Asian width table that decides how far a glyph advances the cursor. Lives in `terminal/`, a document-and-language home like `syntax/` and `markdown/`, free of `android.*` — which is one real departure from the reference, whose `TerminalEmulator` imports `android.util.Base64` on its OSC path where `java.util.Base64` will do. **The row this used to be was gated on five test classes that drive the parser rather than the buffer** — counted, 84/51/45/40/31 `enterString` call sites and no direct buffer construction — so cursor addressing, the scroll region and ED/EL erase moved to P4f, where `TerminalEmulator.java` actually implements them (`docs/DECISIONS.md`, "The emulator's storage and its parser are two steps"). | JVM only; nothing here has a caller in the app until P4i, so the phase's device evidence stays at P4i/P4j. |
| **P4e-1** | **SHIPPED.** `CharacterWidth` + `CharacterWidthRanges`, transcribed from `WcWidth.java` (jquast/wcwidth `1b9b6585`, Unicode 15). The two interval tables are packed as flat `(low, high)` pairs — 566 upstream lines into 146 — because the file-length gate is 500. | `CharacterWidthTest`, 15 green: the 9 from `WcWidthTest`, three for code paths upstream only reaches through `TerminalRow` (`at`, `zeroWidthCountIn`, and the C0/C1 departure), and two on the packing itself — pairing, ordering, disjointness, and the 343/121 range counts. Four mutations run rather than trusted. |
| **P4e-2** | **SHIPPED.** `CellStyle`, from `TextStyle.java`: the effects, the indexed and 24-bit colours packed into one `Long` behind a value class. **Named `CellStyle` because `TextStyle` already resolves to Compose's in 18 files here.** | `CellStyleTest`, 9 green: the 5 from `TextStyleTest`, the mask asymmetry - **measured at 512 before it was fixed** - the `NORMAL` and zero-long contracts, and that the attribute bits are distinct and below the markers. Four mutations run; the two that survived are equivalent and `docs/DECISIONS.md` says why. |
| **P4e-3** | **SHIPPED.** `TerminalRow` + `RowText` + `PackedText`, from `TerminalRow.java`: a row's cells, its combining characters and its wide-character halves. `setChar` is 117 lines and complexity well over detekt's 15, so it is **decomposed along its own comment seams** — fast path, wide-neighbour repair, the combining cap, the array splice, the width transitions. | `TerminalRowTest` (31) + `RowTextTest` (12); the randomised test is **seeded**, since an unseeded failure is not reproducible. And 26 assertions being a thin oracle for a splice rewrite, **both implementations were run against the same scripted 12,000 operations and compared byte for byte after every one** - identical on three seeds, `copyInterval` included (`evidence/p4-terminal/row-differential.md`). |
| **P4e-4** | **SHIPPED.** `TerminalGrid` + `RowRing` + `GridSelection`, from `TerminalBuffer.java`: the circular buffer of rows and the transcript. **Named `TerminalGrid` because `read_buffer` is already terminado's in this codebase.** Three members are left to the steps that own their callers — `resize` to P4h, `setOrClearEffect` to P4f's rectangular/DECSCA path, `getWordAtLocation` to selection, which nothing schedules. | `TerminalGridTest` (20): `ScreenBufferTest`'s two buffer-level tests plus eighteen for the ring and the selection, which upstream only ever covers through the emulator. And the same differential P4e-3 used - **9,000 operations over six kinds of write, identical on three seeds**, seven of seven mutations detected (`evidence/p4-terminal/grid-differential.md`). `docs/architecture.md`'s document-and-language row gains `terminal`, counts re-measured off the tree. |
| **P4f** | **The parser.** The escape state machine feeding P4e: ground / ESC / CSI (with its `?`, `>`, `$`, `!`, `"`, `'`, space and asterisk intermediates) / OSC / DCS / APC — and with it everything that *addresses* the grid: the cursor, the scroll region, ED/EL erase, wrap, LF/CR and the rectangular operations. **Six sub-steps, cut along the reference's own seams and its test classes** — P4f-1 shipped before this table existed, and the table is here so the next sub-step starts from evidence rather than re-derivation. | JVM tests transcribed from Termux's own: `ControlSequenceIntroducerTest`, `OperatingSystemControlTest`, `DeviceControlStringTest`, `ApcTest`, `DecSetTest`, **and the five P4e's row used to claim** — `CursorAndScreenTest`, `ScrollRegionTest`, `RectangularAreasTest`, `UnicodeInputTest`, plus `ScreenBufferTest`'s other three. `ResizeTest` goes with P4h, which owns `resize`. Plus the differential P4e-3 set as the standard, widened and re-run at every sub-step. |
| **P4f-1** | **SHIPPED.** The ground state: `Utf8Decoder` push-driven where the reference's is neither, `LineDrawingCharset`, `TerminalScreen`'s operations, `TerminalCursor` and the C0 controls. **The split is JediTerm's, not the reference's** — `TerminalEmulator` decodes bytes into operations and never touches `TerminalGrid`, because fused, `processCodePoint` is 348 lines. `ArchitectureGuardTest` became the gate for `terminal/`'s freedom from `android.*` here, before the step that would have broken it. | `PlainTextInputTest` (15), `Utf8DecoderTest` (9), `LineDrawingCharsetTest` (7); 12,000 operations byte-identical over three seeds, six of seven mutations detected and the seventh proved dead. `docs/DECISIONS.md`, "DEC Special Graphics `d` and `h`". |
| **P4f-2** | **SHIPPED.** The escape state machine — `ESC` entry, the CSI state, `EscapeParameters`, the abort and the termination — with **exactly one final byte acted on**: `m`, Select Graphic Rendition. Everything else the machine reaches is *swallowed*, which is the frame the later sub-steps fill in rather than a stub. **This is where "SGR to attributes" landed**, which the P4g row used to claim; SGR is a CSI sequence and belongs with the machine that parses one. `58`/`59` are parsed and **discarded** — see `docs/DECISIONS.md`. | `EscapeParametersTest` (11), `GraphicRenditionTest` (12, the whole of `TerminalTest.testSelectGraphics` for both separators plus `testUnderlineVariants` and `testManyParameters`), `EscapeSequenceTest` (13, including `testCsiSGRParameters` and `testTab`'s second line). Differential widened with SGR sequences, a bare `ESC` and **every column's style**: identical on three seeds, 13 of 14 mutations detected. |
| **P4f-3** | **Three sub-steps**, cut before any code was written: one dispatch function cannot hold ~24 CSI final bytes under `LongMethod`'s 20 lines, and the reference's own seams are `doEsc` (94 lines) and `doCsi` (327). **Scope is the whole seam** - every byte of each that needs nothing from P4f-4/5/6, including the twelve this row never named. | Per sub-step below. |
| **P4f-3a** | **SHIPPED.** The `ESC` dispatch: charset designation (`ESC ( )`), DECSC/DECRC, IND/NEL/RI, DECALN, RIS, DECBI/DECFI, lower-left, HTS, the single shifts. **`TerminalScreen` split three ways on detekt's `TooManyFunctions` at 20 of 11** - `CharacterPlacement` takes the write path, `EscapeDispatch` takes the `ESC` operations, which is where the reference puts them too. **Two divergences, both measured against the shipped reference rather than read off its source**: `ESC F` transposes its own arguments, and RIS never clears `mEffect`. | `EscapeDispatchTest` (21), whose expectations come from `harness/RefScreen.java` driving the reference itself. Differential widened with eighteen `ESC` sequences and re-run: identical on three seeds, **22 of 25 mutations detected** and the three survivors proved unreachable by a `throw`. `ESC F`, `ESC c` and the `d`/`h` line-drawing divergence are excluded **by name** in the evidence rather than silently absent. |
| **P4f-3b** | **SHIPPED.** CSI cursor addressing and tabs: `A B C D E F G H f I Z d e \` a g s u`, in `CursorAddressing`. Two behaviours are the reference's **minus a mode that cannot be set yet**: `CSI s` saves unconditionally where upstream DECLRMM makes it DECSLRM, and the six sequences that go through `setCursorPosition` are written as its origin-mode-off case rather than read off the margins. P4f-4 and P4f-5 add the modes and the guards. | `CursorAddressingTest` (21) plus `ScreenBufferTest`'s two selection cases driven end to end. Differential widened with **36 CSI cursor sequences**: identical on three seeds first run, **33 of 36 mutations detected** - P4f-3a's RI survivor among them, exactly as predicted, since this is the first alphabet that can move the cursor *up*. The three that survive are unreachable, and P4f-5 owns two of them. | `CursorAndScreenTest`'s `testCursorUp/Down/Forward/Back` and `testCursorHorizontalPositionAbsolute` (the halves that need no margin), the ANSI.SYS halves of the two save/restore tests, and `UnicodeInputTest`'s `testWideCharacterDeletion`/`testWideCharOverwriting`, which `PlainTextInputTest` defers. |
| **P4f-3c** | **SHIPPED.** CSI erases and edits: `J` ED (`3J` included), `K` EL, `X` ECH, `@` ICH, `P` DCH, `L` IL, `M` DL, `S` SU, `T` SD, `b` REP, in `ScreenEditing`. **What an erase blanks a cell *to* is the current style**, so a shell that sets a background and clears paints the screen that colour. Six of the ten clear the pending wrap and two do not - transcribed rather than regularised, since ED and EL clear it only for a parameter they understood. | `ScreenEditingTest` (15). Differential widened with **31 erase and edit sequences**, `9J`/`9K` among them so that the unknown-parameter asymmetry is visible: identical on three seeds first run. **48 mutations run, 42 detected, 6 survived** - four are the earlier steps' unreachable branches, and the **two new ones are not unreachable**: IL's pending wrap and REP's re-substitution each need a conjunction the random alphabet did not draw, so each is pinned by a test read off `RefScreen` and **run against its own mutation to watch it fail**. That distinction is recorded rather than filed under "unreachable". |
| **P4f-4** | **Two sub-steps**, cut before any code was written: the second screen buffer is a structural change to `TerminalScreen` - `grid` is a `val` today and the saved cursor is one field - and the modes are not. **Three things this row used to imply are not P4f-4's**, and the sub-steps say where they went: DECSED/DECSEL (`CSI ? J`/`K`) need the protected attribute DECSCA sets, so they are **P4f-5**'s; `CSI ? n` (DECXCPR), `CSI ? $ p` (DECRQM) and `CSI > c` (DA2) write back to the shell, so they are **P4f-6**'s. | Per sub-step below. `DecSetTest` has three tests and only `testWrapAroundMode` is reachable here - `testEnableDisableCursor` needs a cursor-visibility surface and `testBracketedPasteMode` needs `paste()` and the reply path, and each goes with the step that gives it one rather than being stubbed early. |
| **P4f-4a** | **SHIPPED.** The mode store and the modes with **no second buffer in them**: `CSI ? Pm h`/`l` (DECSET/DECRST) and the external-to-internal bit map, `CSI ? Pm s`/`r` which save and recall those bits, **origin mode's flag** and its home-on-set side effect - but **not the clamp it feeds**: without a scrolling region the effective margins are the whole screen whichever way DECOM is set, so the clamp is unobservable until DECSTBM, and it lands in **P4f-5** with the margin that makes it testable rather than being written blind here. That is the same call P4f-3b made when it wrote the six `setCursorPosition` sequences as their origin-mode-off case - DECAWM onto the existing `autoWrap`, DECCOLM's four side effects, the ANSI `h`/`l` modes (IRM onto the existing `insertMode`), and `ESC =`/`ESC >`. **`TabStops` splits out of `TerminalCursor`** on detekt's `TooManyFunctions`: the cursor sat at eleven of eleven, and the margins P4f-5 adds would have tripped it regardless - where the cursor is and where a tab would take it are two questions, and only the second needs an array. | `DecSetTest.testWrapAroundMode`, `PlainTextInputTest.testWideCharacterWithoutWrapping`, and the origin-mode halves of `CursorAndScreenTest` that P4f-3b deferred. Differential widened with the mode sequences and re-run; the table re-pointed if a refactor moves an anchor. |
| **P4f-4b** | **SHIPPED.** The **alternate screen buffer**: `CSI ? 47`/`1047`/`1049` and `1048`. `ScreenBuffers` holds both grids and **one saved cursor each**, picked by which is live - which is what makes entering and leaving carry the cursor, since the save runs *before* the swap and the restore *after* it. Three things the reference does that a specification-reader would not: the three switch codes reach **one arm** (xterm distinguishes them, Termux does not); the **transcript is the main screen's whichever buffer is live**, so `CSI 3 J` and RIS clear it from inside the alternate screen; and **RIS does not switch back** - it clears both saved cursors and stays put. The alternate grid is built with exactly `rows` total rows, so `RowRing` can never file history into it. **The resize branch is not here**: both buffers are the same size and nothing resizes until P4h, so it is unreachable and left to the step that makes it so - the call P4f-4a made for origin mode's clamp. `TerminalScreen` sat at ten functions of detekt's eleven, which is why this is a class and not four fields. | `AlternateScreenTest` (11): the four transcribed cases below, plus seven read off `RefScreen` for what upstream covers only incidentally. Differential widened with **twelve** sequences - the exclusion P4f-4a named is lifted - and **identical on three seeds first run**. **70 mutations run, 63 detected, 7 survived, 0 skipped**; the two new survivors are **not** an undrawn conjunction but a stated property of the harness - `ESC c` is excluded from its alphabet - and each, with the third RIS survivor that had gone unexplained since P4f-3a, is now pinned by a test run against its own mutation. The stream's shape changed and is measured rather than assumed: half of it now runs in a buffer with no transcript. |
| **P4f-5** | **Three sub-steps**, cut before any code was written and along the reference's own dispatch states: `doCsi`'s `r`/`s` with `ESC_CSI_SINGLE_QUOTE`, then `ESC_CSI_DOUBLE_QUOTE` with `doCsiQuestionMark`'s `J`/`K`, then `ESC_CSI_DOLLAR` with `ESC_CSI_ARGS_ASTERIX`. The `$` arm alone is ~120 lines upstream with a margin preamble of its own, so it cannot share a commit with the margins that feed it. **Two things the reference reading corrected in this row**: CUU and CUD clamp to the *screen*, not the margins (`:1556-1560`), so `CursorAddressing` is already right and only the six `setCursorPosition` sequences and `CSI s` move; and DECIC/DECDC are margin arithmetic that the intermediate-byte state swallows today, so they go with the margins rather than being left behind. | Per sub-step below. `ScrollRegionTest` (17) and `RectangularAreasTest` (8) split across them; `?69h` re-enters the differential alphabet at P4f-5a, which is the exclusion P4f-4a named. |
| **P4f-5a** | **SHIPPED.** **The margins.** DECSTBM (`CSI Pt;Pb r`), DECSLRM (`CSI Pl;Pr s`) and with it DECLRMM's condition on `CSI s` — which P4f-3b wrote unconditionally and named as owing a correction here — **origin mode's clamp**, which P4f-4a declined to write blind because without a region the effective margins are the whole screen either way, and DECIC/DECDC (`CSI ' }`/`' ~`). The margins already exist on `TerminalCursor` and eight call sites already read them; what is missing is any way to *set* one. | **All 17 of `ScrollRegionTest`** - counted, and none needs a rectangle, which this row claimed before the file was read - plus six more read off `RefScreen`: DECIC and DECDC, which upstream never tests, and the four that close survivors. **P4f-1's linefeed mutation is now detected**, which is the re-run this row owed; CBT's two still survive, for the new reason below, and are pinned. **The differential drove the reference into an `IllegalArgumentException`** - `CSI ? 69 h`, `CSI 2 s`, backspace, and its cursor column is **-1** - and this aborted with it, the transcription being faithful. `backspace` now tests `>` where the reference tests `==`, which differs only in that state; three DECSLRM forms are excluded from the alphabet by name as a result (`evidence/p4-terminal/backspace-past-the-left-margin.md`). **81 mutations run, 72 detected, 9 survived, 0 skipped**, and every survivor is pinned by a test run against its own mutation. |
| **P4f-5b** | **SHIPPED.** **The protected attribute.** DECSCA (`CSI " q`), which is a `CellStyle` effect bit rather than a screen mode — arg `0`/`2` clear it, `1` sets it, anything else is an unknown sequence — and the two erases that read it, DECSED and DECSEL (`CSI ? J`/`K`), which P4f-4's row assigned here by name. | `SelectiveErasingTest` (4): the reference's two, plus two read off `RefScreen` for what its own tests never reach - DECSCA's `2` and its unknown parameter, and the pending wrap a selective erase clears **whatever** its parameter, where ED and EL clear it only for one they understood (the reference assigns the flag *before* its switch here and *after* it there). Differential widened with 16 sequences, **identical on three seeds first run**. **89 mutations run, 79 detected, 10 survived, 0 skipped** - all eight new ones detected. The survivor set grew by the `9J`/`9K` asymmetry that keeps moving between tables; its P4f-3c test was re-run against it and still kills it. |
| **P4f-5c** | **SHIPPED.** **Three sub-steps**, cut after reading the arm rather than before: `ESC_CSI_DOLLAR` is ~120 lines over three groups that share only an effective-margin preamble, and each group has its own reference test. **The fill and erase group**: DECFRA (`$ x`), DECERA (`$ z`) and DECSERA (`$ {`), which reads P4f-5b's protected bit. DECFRA takes its fill character as the **first** parameter where the other two start at the top row, and a character outside `32..126` or `160..255` makes the reference ignore the whole command. | `RectangularAreasTest` (6): the reference's three, plus three off `RefScreen`. Differential widened with 16 sequences, **identical on three seeds first run**. **97 mutations run, 85 detected, 12 survived, 0 skipped** - all eight new ones detected. **The survivor set moved by three that earlier tables had detected**, so this step stopped trusting that and measured it: all twelve applied against the *whole* suite, and **two were covered by nothing** - the linefeed and tab pending-wrap pair - each now pinned. The first linefeed test written did not fail against its mutation (`>=` and `>` agree on the last row); the working case needs `bottomMargin == rows - 1`. |
| **P4f-5d** | **SHIPPED.** **DECCRA** (`$ v`), the rectangular copy: eight parameters, two of them pages it ignores, and a destination clipped to the screen rather than to the source's size. | `RectangularAreasTest` gains 3: the reference's seven cases, its origin-mode-with-margins case as a test of its own (which is why DECCRA follows P4f-5a), and the inverted rectangle. Differential widened with 7 sequences, **identical on three seeds first run**. **103 mutations run, 90 detected, 13 survived, 0 skipped**; five of six new ones detected and **the sixth is an equivalent mutant** - the clamps it drops can only make an extent negative, and both consumers of one (`blockCopy`'s `0 until h`, `copyInterval`'s `while (charIndex < end)`) refuse to iterate. Proved by reading, confirmed by the sweep: it is the only one of thirteen nothing kills. |
| **P4f-5e** | **SHIPPED.** **DECCARA** (`$ r`), **DECRARA** (`$ t`) and **DECSACE** (`CSI * x`), which is what `TerminalGrid.setOrClearEffect` was left out of P4e-4 for. DECSACE sets a flag with **no DECSET number** — `mapDecSetBitToInternalBit` never returns it — so it needs a member no shell can name or a field beside `ScreenModes`. The attribute walk is ~50 lines upstream and takes only the bits `0`, `1`, `4`, `5`, `7`, `22`, `24`, `25` and `27`; a reverse ignores the clearing ones. | `RectangularAreasTest` gains 2, both the reference's, asserting per-cell effects. **The differential drove the reference into an NPE again**: DECCARA's bottom is not clamped to the screen and `setOrClearEffect` indexes `mLines` directly where its six siblings allocate, so origin mode plus a top margin walks off the last row (`evidence/p4-terminal/deccara-past-the-last-row.md`). One clamped loop bound fixes it; the alphabet excludes every bottom but 1, and **the first attempt at that exclusion allowed 0 and crashed anyway** - `getArg` treats a zero as absent, so 0 is the *largest* rectangle. **114 mutations run, 103 detected, 11 survived, 0 skipped**, all eleven new ones detected. `RowStyles` splits out of `TerminalRow` on detekt's function cap - the counterpart `RowText` has had since P4e-3, earned now because DECCARA is the first thing to write a style without a character. |
| **P4f-6** | **Four sub-steps**, cut after measuring the reference's own functions: `doOscSetTextParameters` is **143 lines** and `doDeviceControl` **121**, so neither can share a commit with the other or with the seam they write through. The reference writes back in **23 places**; P4f-1 split `TerminalNotices` from its one `TerminalOutput` precisely so this direction could arrive with its own interface. | Per sub-step below. `OperatingSystemControlTest` (7), `DeviceControlStringTest` (3), `ApcTest` (1) and `TerminalTest`'s three reply tests, split across them. |
| **P4f-6a** | **SHIPPED.** **The reply seam, and the reports that need no string parsing.** The write-back interface itself, then DA1 (`CSI c`), DA2 (`CSI > c`), DSR (`CSI n`), DECXCPR (`CSI ? n`), DECRQM (`CSI ? $ p`) — whose `47`/`1047`/`1049` arm reads the buffer pointer **P4f-4b** introduced, so it needed that step first — and the window operations (`CSI t`), which report sizes in cells and pixels. | `TerminalReportingTest` (7): `testDeviceStatusReport` whole, `testReportTerminalSize` and `testReportPixelSize` as far as their first resize (P4h's), and four off `RefScreen`. **The dump now carries the replies** - 229 of 4,000 steps on seed 1 - and ten of this step's eleven mutations are detectable only because of that. **A third reference crash**: a wide character right of a narrow right margin throws, and **this threw with it**, the first time the differential caught both failing rather than diverging (`evidence/p4-terminal/wide-character-outside-the-right-margin.md`). DECSLRM leaves the alphabet as the cost. **125 mutations run, 112 detected, 13 survived, 0 skipped.** |
| **P4f-6b** | **Three sub-steps, cut after measuring the reference's own arm.** `doOscSetTextParameters` is **143 lines** over three groups that share only a numeric-prefix preamble - the title, the colour family and the clipboard - and each group has a reference test of its own. **A fourth commit came first**: `EscapeParser.inModeQuery` was dead from the commit that wrote it, and `EscapeParser` sat at detekt's eleven-function cap, so removing it is what made room for the OSC arm (`P4f-6b-0`). | Per sub-step below. `OperatingSystemControlTest` (7) and `TerminalTest.testParseColor`, split across them. |
| **P4f-6b-1** | **SHIPPED.** **The string state, and the window title.** `ESC ]`, the buffer emptied on the way in, both terminators - `BEL` and `ESC \` - the 8192-character cap, and `OSC 0`/`1`/`2`, which reach **one arm**: the reference draws no distinction between an icon name and a window title. With it the four window operations `TerminalReporting` deferred here **by name** - `CSI 20 t`/`21 t`, answered with the constant upstream calls "disabled due to security concerns", and `CSI 22 t`/`23 t`, the title stack, which holds twenty and **drops the oldest**. **Two C0 bytes now ask the escape state first, and only two**: a `BEL` ends the string where it would otherwise ring and an `ESC` opens `ESC \` where it would otherwise start a sequence - measured, not read, because both guards are `== ESC_OSC` and so **a `BEL` one byte later, in the escape state, rings after all** (`docs/DECISIONS.md`). | `OperatingSystemCommandTest` (15): the reference's `testSetTitle` and `testTitleStack`, plus eleven read off `RefScreen` for ground upstream never covers - the stack's cap and its `null`, the non-terminating `ESC`, the bell inside the escape state, a control character that keeps acting mid-string, the empty, non-numeric and separator-less bodies, and the cap at 8189/8190/8191. **`RefScreen` gained the title, the change pairs, the bell count, the clipboard and the colour count**, the blind spot `replied =` closed at P4f-6a; the dump gained `title=` **and `titled=`**, the second because a title set to what it already was is invisible in the first. Differential widened with **16 OSC sequences** and six window operations - **four of them added because a mutation survived without them** - and **identical on three seeds first run**, before and after the widening. **143 mutations run, 125 detected, 18 survived, 0 skipped**; one anchor had moved and was re-pointed rather than left `SKIPPED`, and 17 of the 18 survivors are killed by a named test, the eighteenth being P4f-5d's proven equivalent mutant. |
| **P4f-6b-2** | **The colours.** `OSC 4` (set, no query - the reference implements one only for `10`/`11`/`12`), `10`/`11`/`12` with their `?` reply, `104`, `110`/`111`/`112` and `119`'s no-op, over a 259-entry table and an XParseColor parser. **The defaults are a constructor parameter, not a global** - upstream's `TerminalColors.COLOR_SCHEME` is a process-wide mutable static, VS Code's ANSI ramp is **theme-dependent**, and `terminal/` may not reach into `theme/`; so this owns the mechanism and **P4g stays where it is** and owns which colours. It is also what makes the differential possible: the Kotlin side has to be able to take Termux's own scheme to compare against `RefScreen`. | `TerminalColorsTest` from `TerminalTest.testParseColor`, and `OperatingSystemControlTest`'s `testSetColor`, `testResetColor`, `testResettingTerminalResetsColor`, `testSettingDynamicColors` and `testReportSpecialColors`. |
| **P4f-6b-3** | **The clipboard.** `OSC 52`, with `java.util.Base64` where the reference has `android.util.Base64` - the departure P4e's row named. | **The reference's own test is named `disabledTestSetClipboard`** and says why: *"Cannot run this as a unit test since Base64 is a android.util class."* `terminal/` is `android.*`-free by `ArchitectureGuardTest`, so it runs here, plus an invalid-Base64 case and a missing-`;` case off `RefScreen`. |
| **P4f-6c** | **DCS.** `ESC P` and `doDeviceControl`: DECRQSS, which echoes back the sequence that would restore a setting, and the terminfo query that answers in hex. | `DeviceControlStringTest` (3). |
| **P4f-6d** | **APC, and the two surfaces earlier steps parked.** `ESC _` and its terminator, plus `paste()` with bracketed-paste framing and the cursor-visibility query — the two `DecSetTest` cases P4f-4a deferred for want of anything to ask. | `ApcTest` (1), `DecSetTest.testEnableDisableCursor` and `testBracketedPasteMode`. |
| **P4g** | **The colour ramp.** The sixteen ANSI colours resolved from VS Code's `terminalColorRegistry.ts` **registered defaults** — the default themes override none of them, so this is the same resolve-through-the-registered-default shape P2u-3 used for the surfaces. **"SGR to attributes" is no longer here**: this row used to claim it, and P4f-2 has it, because SGR is a CSI sequence and the machine that parses one is P4f's. What is left is a palette question with no parser in it — what index 1 *is*, where P4f-2 settled only that it is 1. | JVM equality against the resolved ramp, under the existing palette-consumers gate. |
| **P4h** | Scrollback, and reflow on resize. | JVM tests from `HistoryTest` and `ResizeTest`: a resize narrower and back preserves what was written, and lines pushed off the top are reachable. |
| **P4i** | **The screen.** `ui/terminal/`, a navigation destination beside the editors — JupyterLab adds a terminal to `'main'` as a `MainAreaWidget` (`packages/terminal-extension/src/index.ts:523-524`), a document-like surface rather than a panel, which is also the shape that gives a shell the whole window on a phone. `set_size` from measured monospace cell metrics - `terminal-view/TerminalRenderer.java` is the reference for how a buffer becomes glyphs at a fixed cell size; the font stays behind `theme/CodeText.kt` under the monospace-home gate. | Instrumented tests; Maestro on the tablet: open a terminal, type `ls`, read the workspace back, and a screenshot of `vi`. |
| **P4j** | **Mobile input. Two things already exist and neither should be reinvented.** The app has `SymbolRow` (U5d) — a scrolling key row with `imePadding` + `navigationBarsPadding`, written for exactly this problem in the cell editor — so this generalises it rather than adding a second one. And Termux answers what a *terminal* row needs, which the editor's does not: `ExtraKeysView` / `ExtraKeysInfo` / `ExtraKeysConstants` (`reference/research_repos/termux-app/termux-shared/src/main/java/com/termux/shared/termux/extrakeys/`), including modifier keys that **latch** (Ctrl/Alt held for the next keystroke) rather than emitting a character. Read that before choosing the key set. Plus gesture scrollback, font size, and `set_size` on every resize including when the IME opens. | Maestro on tablet and at phone width: type a command, read its output, send Ctrl-C to a running one, resize with the keyboard and show the shell reflowed. |
| **P4k** | **The idle stopper stops killing a live terminal** — the defect this phase introduces, closed inside it. `IdleServerStopper`'s idleness takes attached terminals as well as in-flight cells. | JVM regression test first; then the tablet: open a terminal, background 60 s, foreground, and the shell is still live with its scrollback intact (the `read_buffer` replay is what makes that true). |
| **P4l** | `evidence/p4-terminal/README.md`, the "Where we are" counts re-counted off the tree, and `docs/DECISIONS.md` for the `bin/sh` link, the pinned `shell_command`, the `setup` frame and the replay, and whatever P4a measured. | The counts match `git ls-files`-derived greps rather than the last row's arithmetic. |

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

# P3 — Spaces

**Last, not third.** The label is kept because it is cited elsewhere; the section sits here because
P4-P6 need only the one base Space that already exists, and this is the largest unbuilt thing in the
file. Nothing above it waits on it.

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

## Open risks

Measured, not speculative. Anything closed moves to `docs/DECISIONS.md`.

- **`IdleServerStopper` will kill a live terminal, and P4 is what makes that reachable.** It waits
  on `cellsInFlight` alone (`IdleServerStopper.kt`), so backgrounding the app with a shell open and
  no cell running stops the server under it after the 30 s grace. **The server side is already
  safe** - `shutdown_no_activity` early-returns on `extension_manager.any_activity()`
  (`serverapp.py:2602-2618`), and `jupyter_server_terminals`' `current_activity()` reports its live
  terminals - so this is ours alone. Read in the source rather than observed, because nothing can
  observe it until P4d exists. **Scheduled: P4k.**
- **A workspace search does not reach a notebook's outputs, and closing that is a refactor rather
  than a switch.** `webviewResults` is the third field of `INotebookCellMatchNoModel` and P2x-4
  builds the other two. Three things hold it shut, and only the first is a preference: JupyterLab's
  own `output` filter is off by default while VS Code's is on but hidden unless a notebook is open;
  `SearchHit.outputAt` indexes the **rendered** outputs (`renderedOutputsOf`), which drop
  `ClearOutput`, so a disk-side raw index would name a different output from the one drawn; and the
  rule for *which* outputs have searchable text - "only what `RenderedOutput.Text` draws" - lives in
  `ui/editor/`, which `data/` may not import. The prerequisite is one home for that rule, and it
  carries a real design question with it: the mime ranking that makes `text/plain` lose to
  `text/html`. `docs/DECISIONS.md` carries the measurement. Not scheduled.
- **Workspace search matches line by line, so a pattern spanning lines finds nothing.** The
  reference decides multiline from the pattern itself (`isMultilineRegexSource` onto
  `IPatternInfo.isMultiline`) and searches accordingly. Ours is bounded on purpose: a result row
  *is* a line, so matching over the whole file would count hits that no row could draw, and
  `matchedLinesIn` says so in as many words. The cost is a real parity gap and not a rounding
  error — `def foo\n    return` is a search a user can reasonably type. Not scheduled: closing it
  means a result row that spans lines, which is a presentation problem before it is a matching one.
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
