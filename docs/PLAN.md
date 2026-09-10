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
| Runtime | the shell has the Space's **own `python3`** since P7e-1 - `python3`, `python` and `python3.12` all reach the one interpreter Android permits running, so `python3 -m pip` answers in a terminal (`pip` itself does not yet: P7e-2); the notebook **says which kernel it is on and what that kernel is doing** - no kernel, starting, busy, idle, restarting; rootfs installs, server starts, kernels start and **reattach**; numpy/scipy import; the server stops when it is idle or the app is left, and comes back on the next run; a kernel can be ended on purpose from the kernel menu |
| Notebook | **kernel history** walked into the cell being edited; **find and replace** across the document, with JupyterLab's own filters; all three `cell_type`s - code, markdown and **raw**; cells editable, syntax-highlighted, executed; outputs (stream, error+ANSI, PNG, `text/html`, tables) render natively; collapse - of a cell's source, of its outputs, of the **section under a heading**, and outputs can be **bounded to a scrollable strip**; a contiguous run of cells can be marked and every cell **and run** command acts on it — ranged runs, the compound restarts and clear-all included; nbformat round-trip preserves unknown fields |
| Editor | `BasicTextField` + generated ANTLR lexer, auto-indent, bracket match (**toggleable, persisted, universal** across a cell and the plain file editor), line numbers (**toggleable**), kernel-backed completion, symbol row |
| Files | explorer with create / rename / delete / browse, Recents as a section, **workspace search** from the shell's own panel - the query, its three options, include / exclude globs, results grouped by file, a `.ipynb` searched as the **cells** it is rather than the JSON it is stored in, and a tap that opens the file **at** the match - a notebook **on the cell**, revealed out of whatever was hiding it; **replace across every file at once**, counted before it is agreed to, reaching a document that is *open* rather than the file under it, and taken back by one undo - **export** beside the document and out through a share sheet, reachable at **every** width since P2y, through a bottom activity bar wherever the shell draws one pane - polling on JupyterLab's own cadence (`packages/filebrowser/src/model.ts:26`, `DEFAULT_REFRESH_INTERVAL = 10000`, `standby: 'when-hidden'` at `:106`) |
| Terminal | a **real busybox `ash`** on the tablet, spawned by terminado off the Space's own pinned `bin/sh` and rooted in the same workspace the server is; drawn as a `Canvas` grid of batched glyph runs and a block cursor on VS Code's resolved 16-colour ramp, at a cell measured on the device rather than assumed. Underneath it is `TerminalEmulator.java` transcribed and then **run against its own original** on random escape streams - the state machine, CSI/OSC/DCS/APC, the margins, the protected attribute, the rectangular operations, the alternate screen, scrollback and reflow on resize (`evidence/p4-terminal/emulator-differential.md`, which is also where the four reference defects it found are written up). It is sized by its own layout, so a rotation or a keyboard opening writes `set_size` to the shell; a hardware arrow or Ctrl+letter reaches it as real bytes, and a touchscreen with neither gets them from an extra-keys row latching Ctrl/Alt on Termux's own `{isActive, isLocked}` rule; the font size is a persisted setting stepped from the terminal's own overflow; a drag scrolls back through real history, and while the alternate screen is live (`vi`, `less`, `htop`) that drag becomes Up/Down instead, because that buffer has no scrollback to show. A live terminal holds the server up against the idle stopper - the defect the phase introduced and closed inside itself. **P4m closed the last open one**: the hidden input field now asks the IME not to rewrite the line (`KeyboardType.Password`, the reference's own inputType for a terminal), after a tablet measurement showed the corruption was Gboard's quote-completion rather than the diff the row had blamed; the edit-to-bytes mapping is a general common-prefix diff behind that |
| Presentation | three scales and one gate each: Material's type scale stated rather than hand-built, `theme/Spacing.kt` on the 4dp grid, and `theme/CodeText.kt` at 14sp/18sp with `FontFamily.Monospace` confined to `EditorTheme.kt`; every tap target reserves Material's 48dp. **Everything below this point is JupyterLab-derived and named as such, because since P2t this file has two presentation references and its own rule is that a claim must say which.** Cell chrome is JupyterLab's: the collapser transparent at rest and brand blue on the active cell, the editor boxed by a 1dp border that turns brand blue for the cell being typed into. A cell's prompt is JupyterLab's - `[7]:` on code and **nothing at all** on markdown or raw, so every header's leading mark sits at one x. The document's name has a row of its own above its actions, where JupyterLab's single-document mode puts it; the heading caret sits beside the heading it folds, where JupyterLab draws it - but **only while that heading is rendered** (P2v-2), where the reference keeps it in both states, because beside an open source it cost 48dp of the editor's width for a fold nobody can reach mid-typing. The table of contents is a **sidebar panel of the shell** reached from an activity bar beside the file browser, where it used to be a column on the right of the notebook; the open document publishes its headings across to it, since the panel cannot reach the notebook's ViewModel. Picking the showing panel collapses the side area and the notebook takes the width. The active cell's type is named on the bar where there is room for it and in the notebook's overflow where there is not, which is what JupyterLab's `ReactiveToolbar` does with an item that does not fit. A nested document's **breadcrumb** is where `BreadcrumbsControl` sits relative to a tab title - *ancestors only*, since the name is on the row above it, and absent at the root (P2w-1). **Both documents draw one since P7b**, and "where" resolves per surface rather than by copying a position: between the notebook's title strip and its action row, and *under* the plain file editor's single row, which is its name row and its action row at once. A cell's Run control draws an **outlined** play, not a filled one (P2v-1): filled at rest reads as "playing" - and the kernel indicator on the action row follows the same rule for the same reason (P2w-2), where the reference's own filled bolt was the loudest mark in a row of outlines. That indicator is JupyterLab's `kernelName` and `executionProgress` as one control, right of Find and **left of** the overflow, which is where a `ReactiveToolbar` actually ends; below 600dp it keeps the status and drops the name. Every cell type's editor is **one font** - a markdown cell's source is drawn in the code font a code and a raw cell's is (P2s), which both references agree on and which is **not** rendered markdown's code inheriting the prose size (P2l-3b). **P2v declined the cell-layout re-derivation and P2w re-derives the notebook's top from VS Code**, so the four sentences above them stand: what P2v changes is the play's glyph and whether a heading's chevron is drawn while its source is open (`docs/DECISIONS.md`, "The cell keeps its header strip"). |
| Shell | two panes ≥ 840dp on `ListDetailSceneStrategy`, the activity bar as a **rail beside two panes and a bottom bar below one** - VS Code's own `ActivityBarPosition`, its items spread on a small screen and grouped on a medium one, which is `ShortNavigationBar`'s own rule - **VS Code's chrome palette** - every Material role on the resolved 2026 ramp, each pair held to a contrast floor (P2u-1) - Material's own type scale, per-notebook ViewModel scoping. Since P7a-2 the workbench is drawn as **parts with edges**: each part paints its own border on the side facing the next, `activityBar.border` between the rail and the panel and `sideBar.border` between the panel and the document, inside its own width the way both references budget it - and the document pane *states* `editor.background` rather than inheriting it from a Material role that could be re-mapped |
| Tests | **454 instrumented**, **2141 unit**, **253 script**, 154 Maestro drivers over 173 flows - `154 - 9` of them in the sweep, the other nine being tools rather than checks (`NOT A SUITE FLOW`) - `run-flows-sharded.sh` **balanced by measured time** across both devices, gradle + script gates. Counted, not carried, and re-counted off the tree at every phase: this row read 256/1261/100/114 until P2s, which was 35 instrumented tests behind what the tree held; 1330 unit until P2x-1; 347/1459 until P2x-5, which was ten instrumented tests behind before that phase added a single one; **366/1558 until P2y**; **153 script until P2z**, which added six for the width rule (P2z-d) and six for the stale-frame manifest (P2z-h); **378/1564/165** until P4f-2 re-counted it, P4a-P4f-1 having added 21 instrumented and 189 unit without the row moving; **399/1790/170 over 126 drivers and 141 flows until P4l**, which is P4b-P4k's worth of drift - the phase that transcribed a 2,617-line terminal emulator was never going to leave these alone. **Each figure now says how it is counted, because two of them are counted differently.** The unit figure is **executions** - `:app:testDebugUnitTest` reports 2116 over 210 classes where a `@Test` grep reads 2074, because the port contracts are written once and run per implementation, and the difference is the point of writing them that way. The instrumented and script figures are `@Test`/`def test` **sites**; the script one is confirmed by `python3 -m unittest discover`, which also runs 248. **And the sweep's share is counted with `driver_declares`, never with a bare `grep`** - a driver may name a rule in prose and only a `^#`-anchored declaration selects one, so `grep -l 'NOT A SUITE FLOW'` reads 14 where the sharder reads 9 - still 9 after P7a-1b, whose two drivers are checks rather than tools, so the sweep's share is `144 - 9`. **That subtraction is stated because the previous figure did not survive it**: this row read *142 drivers … 132 of them in the sweep*, and `144 - 9` is 135 where the old pair implies 133. The method is the one written here; the arithmetic behind the retired number is not recoverable, so it is re-counted rather than carried. That is the exact defect `scripts/driver-rules.sh` exists to close, and this row is not allowed to re-open it. Every command is in `evidence/p4-terminal/README.md`, so the next re-count is a re-run. **P7a-2 re-counted it again**: 424/2109/217 until then, against 430/2113/235 at that point - six instrumented (`PartBorderTest`, `ShellSidePaneTest`), four unit (two on the border ids, two on the cancelled listing) and eighteen script (the seam oracle, and the gate that had been reading comments). **P7a-3 re-counts it to 430/2116/248**: no instrumented test, three unit (`CornerRadiusTest`) and thirteen script - nine for `kotlin_source.py`, the scanner both comment-blind gates now share, and four for `check_ui_dimensions.py`'s own half of that defect. Drivers, flows and the sweep's share are unmoved at 144 / 162 / `144 - 9` across both steps: P7a-2 re-used `p7a-chrome-parts.yaml` and P7a-3 re-used `p7a-1b-toggle-wash.yaml`, since a step that changes no pixel owes a re-run rather than a new flow. **P7b then moves all three**: five instrumented (`FileEditorBreadcrumbTest`), no unit and no script, and **two** drivers and flows - the narrow and the wide half of one width rule, neither of them a tool, so the sweep's share is `146 - 9`. **P7e-1 re-counts every figure off the tree, and four of the five had gone stale**: this row read 435/2116/248 over 146 drivers and 164 flows, and P7c, P7d and P7e-0 shipped tests, drivers and flows without moving it - which is the drift the method exists to catch and the row is not allowed to re-open. Re-counted the documented way: **454 instrumented** and **253 script** `@Test`/`def test` sites (`unittest discover` runs 253 too), **2138 unit executions** over 214 classes where a `@Test` grep reads 2093, the gap still being the port contracts written once and run per implementation; **153 drivers over 172 flows**, `driver_declares` finding **9** tools, so the sweep's share is `153 - 9`. P7e-1's own share of that is small and stated separately so the drift is not laundered through it: five instrumented (`PythonInterpreterDeviceTest`), four unit (`BaseSpaceInstallerTest`), no script, and one driver and one flow. **P4m adds three unit sites** (`TerminalInputFieldTest` - a mid-line insert, a tail rewrite, a word corrected in place, each a silent drop under the old mapping) so the `@Test` grep reads **2096** and executions **2141** over the same 214 classes; **no instrumented, no script**; **one driver and one flow** (`p4m-terminal-input`, a suite member, not a tool), so **154 drivers over 173 flows** and the sweep's share is `154 - 9` |

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

**P4 is closed, and P7 comes before P5.** P7 is five gaps P2-P4 let through - the chrome that never got VS Code's *parts*, a breadcrumb only the notebook has, a Recents section that can only be emptied, a back arrow where two panes want a close, and a Space that cannot install a Python package - each measured before it was written down. **P7a-0, P7a-1, P7a-1b and P7a-2 are shipped**: the reference clone is re-based and re-verified, the rail and the side panels are drawn as *parts* rather than on the document's own colour, with a pixel oracle that fails on the frame the defect was found in - and **P7a-1b then found that the palette's next value was serving two reference ids at once**. `secondaryContainer` was `list.inactiveSelectionBackground` and had four consumers, only one of them a list; 2026 Light states that id at 60%, so honouring the alpha the plan set out to honour would have made the *search toggles'* wash translucent where the reference draws it opaque. The two ids were parted rather than folded harder - the explorer's open row is a **part** now and paints the `#E7E7E8` the reference does, the toggles and the two contextual bars took `inputOption.activeBackground`, which they had always cited - and a second pixel oracle was built for it, one that reads the colour inside a *named* node's own bounds and was shown red on both devices before it was shown green. **P7a-2 then gave each part the border id the reference states for it** - and found that the app drew one of the two, by accident: the rail's seam was a bare `VerticalDivider` whose `outlineVariant` happens to hold `panel.border`'s hex, and the side panel abutted the document with no `sideBar.border` at all. The oracle could not have said so, because `read_chrome_parts.py` discards a 1-2px band *on purpose* - a border is not a part - so the step began by teaching it to read the seams it throws away. **And reading the frames it captured is what found a defect no assertion in the repository could fail on**: the explorer drawing its own cancelled listing at the user as an error, two weeks old and fixed in its own commit. P7's own header says where it is and what is next. P4a-P4h are shipped - the parser (P4f), the ANSI ramp (P4g) and the
buffer's own resize, fast path through the lazy catch-up a buffer switch can owe (P4h-1 through
P4h-4) - and **P4i-1 through P4i-3 are shipped too**: the screen is reachable, a real shell on the
tablet answers what is typed into it, it is drawn faithfully - a real device cell, the resolved
VS Code palette, batched-run glyphs and a block cursor on a `Canvas`, with `vi`'s own full-screen
redraw as the closing proof (`evidence/p4i-terminal-tablet/`, `evidence/p4i-terminal-vi/`) - and
**P4i-3's own two commits** made the resize layout-driven (a real measured grid, the wire `set_size`
that had never once been sent, `vi`'s own reflow across a rotation as the proof -
`evidence/p4i-terminal-resize/`) and scoped `attach()` to visibility (`STARTED`, cancelled on
backgrounding, a re-attach that clears the screen and scrollback first - a device run's own finding
that terminado's `read_buffer` replays the whole session rather than only what was missed,
`evidence/p4i-terminal-background/`, `docs/DECISIONS.md`). **P4j-1 and P4j-2 are shipped too**: the
input field that used to sit under the terminal output as a visibly separate box now shares the
terminal's own `Box` and paints nothing of its own - the block cursor is the only input point on
screen, the way Termux's single-surface `TerminalView` is (`evidence/p4j-terminal-input-tablet/`,
`evidence/p4j-terminal-input-phone/`) - and a hardware arrow key or Ctrl+letter now reaches the
shell as a real escape sequence or C0 control byte rather than moving `BasicTextField`'s own caret,
proven on a real shell by a driver-issued `adb shell input keycombination` interrupting `sleep 30`
mid-run (`evidence/p4j-terminal-ctrl-c/`). **P4j-3a is shipped too**: `docs/PLAN.md`'s own P4j-3
split into four sub-steps - too big for one commit - and the first is the extra-keys row a
touchscreen with no hardware Ctrl needs, `TerminalExtraKeysRow` generalising `SymbolRow`'s shape
rather than building a second one, with Termux's own `SpecialButtonState{isActive,isLocked}`
transcribed as `TerminalModifierLatch`: a tap arms Ctrl/Alt for exactly the next character typed
on the soft keyboard, a long-press locks it caps-lock style, proven on a real shell by a tapped
Ctrl converting a typed letter into the byte that interrupts a real `sleep 30`
(`evidence/p4j-3a-terminal-extra-keys/`) - the same proof P4j-2 gave a hardware Ctrl+C, now given
a touchscreen's own. **P4j-3b is shipped too**: `TerminalCanvas` no longer cancels the IME's own
inset back out before computing its grid - it carries `imePadding()` itself, so a keyboard opening
is just another size change through the resize path P4i-3a already built, proven on a real shell by
`vi`'s own content reaching further down once the keyboard closes than while it was open, read off
the screenshot's own pixels rather than accessibility text (`evidence/p4j-3b-terminal-ime-resize/`,
`docs/DECISIONS.md` - Maestro's own text-reading of this terminal proved unreliable independent of
the fix, and a `key()` removal reasoned as a pure improvement broke real keyboard input instead,
both found and reverted/worked around before this shipped). **P4j-3c is shipped too**: the
terminal's own font-size setting - `TerminalSettingsRepository` (port/`Fake`/`Disk`, mirroring
`EditorSettingsRepository`'s own shape rather than folded into it), bounds and step measured
against Termux's `TermuxAppSharedPreferences` (4 min, 256 max, ±2 per step) as the plan row
already committed to, the control itself modelled on JupyterLab's own `terminal:increase-font`/
`terminal:decrease-font` (a discrete stepper in the terminal's own overflow, each row disabled at
its own bound) rather than Termux's pinch gesture - no `pointerInput` gesture exists anywhere in
this codebase yet, and P4j-3d is the step that establishes one, not this. `codeTextStyle` gained
an optional `fontSize` parameter, its line height computed through a newly extracted
`CodeText.lineHeightFor` rather than a second copy of the reference's `1.3077` ratio. Proven on a
real shell by two taps on Increase then one on Decrease against a real `vi`, read as the pixel
gap between consecutive `~` rows (`terminal_row_spacing_px`, `terminal_content_extent.py`) -
`terminal_content_bottom_y` would not have discriminated this, since a bigger font still fills the
same screen with fewer, taller rows and only the row-to-row spacing moves
(`evidence/p4j-3c-terminal-font-size/`). **A device-measured false start, not a defect in the
setting**: the first run of the gate found no reflow at all, because the tablet's own landscape
default put the terminal in `ListDetailSceneStrategy`'s two-pane layout, and the oracle's own
pixel geometry was tuned against a single-pane capture - fixed by rotating to portrait first
(`p4i-terminal-resize-driver.sh`'s own `rotate_to`), reusing that already-tuned geometry rather
than teaching the oracle a second pane layout. **P4j-3d-1 is shipped too**: the sub-step split
four ways once scoped, as this row already expected, after two Explore passes over the actual
source answered the row's own open question - `TerminalGrid`/`RowRing` already expose scrollback
by offset (`rowAt(externalRow)` already accepts any `externalRow` down to `-activeTranscriptRows`),
so this is a rendering-and-gesture change, not a new storage-read path. `TerminalGrid` gains one
extension function, `clampedTopRow(current, deltaRows)` - Termux's own `TerminalView.doScroll`
clamp, transcribed as pure row arithmetic reusing `activeTranscriptRows` the grid already exposes,
and taken as an extension rather than a member since `TerminalGrid` was already at detekt's
`TooManyFunctions` cap (P4h-1's own finding). Still no `pointerInput`, no UI change, no Maestro -
pure engine, same precedent as P2x-1a/P2y-a. **P4j-3d-2 is shipped too**: `TerminalCanvas` gained
a `topRow: Int = 0` parameter (every existing call site's default kept it drawing exactly what it
always drew) - both the `Canvas` draw loop and the parallel transparent-`Text` accessibility loop
became `topRow until topRow + screen.rows`, and `drawCursor` fires only `if (topRow == 0 &&
emulator.isCursorEnabled)`, since the cursor's own row is always among the *live* screen's
external rows and a scrolled window can now exclude it - Termux's own `TerminalRenderer.render`
row-window side effect, made an explicit condition here. **One line inside `drawRun`'s own pixel
math needed correcting that the parameter alone did not fix**: `drawRow` was still using the
*external* row index as the pixel row multiplier, which put a scrolled-back row's glyphs at a
negative (off-screen) y rather than at the window's own top - fixed by threading `topRow` through
so the pixel row is `rowIndex - topRow`, always `0`-based regardless of how far into scrollback
`rowIndex` reaches; the Column`-based accessibility layer needed no equivalent fix, since it just
stacks `Text`s in loop order rather than positioning by row number. New `TerminalCanvasTest`
cases (instrumented, tablet): a non-zero `topRow` reveals a scrolled-back row's own content
without anything else about the grid changing (`onNodeWithText`); the cursor is absent once
`topRow != 0`. Still no gesture, no Maestro - `topRow` is a hardcoded test parameter, exactly how
P4i-3a proved `resize` before anything called it from touch. **P4j-3d-3 is shipped too**: the
first in-app gesture, and it did not ship the way the row committed to. `Modifier.scrollable` -
Compose's own idiomatic drag-with-fling primitive, chosen over hand-porting Termux's
`GestureAndScaleRecognizer`+`Scroller` - produced no scroll at all on the real device, because
`TerminalInputField`'s full-bleed `BasicTextField` consumes touch on `PointerEventPass.Main`
before a sibling's or an ancestor's own Main-pass handler ever runs. The fix is a small custom
`pointerInput` on `PointerEventPass.Initial` instead (`detectTerminalScrollDrag`), which runs
ancestor-to-descendant - ahead of the field - tracking touch slop itself so a plain tap still
reaches the field beneath. Narrower than first planned: drag only, no fling, since
`PointerEventPass.Initial` gets none of `scrollable`'s own deceleration for free and this gate
names a drag revealing history, not momentum - a follow-up rather than a silent gap, the same
shape P4j-3c's own pinch-to-zoom deferral took. The Maestro oracle itself needed two corrections
before it proved anything: a marker-line-at-a-distant-column search read `TerminalExtraKeysRow`'s
own button labels, then a soft keyboard's own key-label glyphs, as if either were scrollback -
replaced with `terminal_top_strip_differs`, comparing a fixed strip just below the app bar
between two captures, a position neither piece of chrome ever reaches (`docs/DECISIONS.md`, both
findings). **P4j-3d-4 is shipped too, and it closes P4j-3d and P4j-3 and P4j itself**: the
alternate screen (`vi`, `less`, `htop`) redirects a drag to Up/Down arrow keystrokes instead of
scrolling, Termux's own `doScroll` special case for a buffer with no scrollback at all - reusing
`TerminalKeyEncoding.kt`'s own arrow-key bytes (P4j-2) rather than a second encoding. The row's
own plan named a new `TerminalScreen.isAlternateLive` delegate; checked against the actual class,
`ScreenBuffers.isAlternateLive` was already public and already reachable through `TerminalScreen`'s
own `buffers` property, so nothing was added there at all. Proven on the tablet by a five-line
file opened in `vi`: a drag moves the cursor from line 1 to line 5 while the `~` fill lines never
move, two oracle calls apart - one proving the screen did not scroll, one proving the cursor's own
cell did. **P4j is now shipped in full**: the parser, the palette, the resize path, the screen
reachable and drawn faithfully, input reaching a real shell as real bytes, the extra-keys row, the
IME's own inset, a persisted font size, and now a first-class scrollback gesture with its own
alternate-screen fidelity. The
order changed too - phases now run **P4 → P5 → P6 → P3**: the terminal, then
LSP, then the debugger/console/settings/launcher group, and **Spaces last**. Nothing ahead of P3
depends on it - `BaseSpaceServer`, `SpaceRuntime` and `JupyterServerLauncher` already give all three
the one base Space they need, and `BaseSpaceServer`'s own KDoc has named *"an export, a terminal, a
language server"* as clients of that single server since P1. **The labels did not move with the
order**: P3 is still "P3 - Spaces" wherever it is cited, and its section sits after P6 rather than
being renumbered, so no commit message or `docs/DECISIONS.md` entry became wrong.

**P4k is shipped too, in two commits, and it closes the defect P4j itself made reachable.**
`IdleServerStopper` used to wait on `cellsInFlight` alone, so backgrounding the app with a
terminal open and no cell running stopped the server under it after the 30s grace - reachable
only once P4i gave the app a real attached shell to lose. `ActiveTerminals` (`domain/model/`)
mirrors `ActiveCellRuns`'s shape - a standalone counter, not state kept inside
`RealTerminalGateway`/`BaseSpaceTerminalGateway`, because the latter rebuilds its underlying
gateway whenever the Space's server restarts (`PerRunningServer`) and a counter kept there would
lose exactly the count this counter has to survive across. `ActivityTrackedTerminalGateway`
(`data/space/`) wraps whichever `TerminalGateway` `AppContainer` holds, so the port itself and its
three implementations are untouched. `IdleServerStopper` now combines `cellsInFlight` with a
second `StateFlow<Int>`, reusing its existing wait-for-zero/wait-for-positive loop unchanged.
**The device gate needed its own fix before it proved anything**: `visible:`/`extendedWaitUntil`
against this terminal's own text failed the same way P4j-3b's own finding already named, on the
very first run - so the Maestro flows write a marker to disk instead of echoing one to the screen,
read back over `adb` the way `p1c-app-death-backstop-driver.sh` already reads a notebook's answer,
and "scrollback intact" is `terminal_top_strip_differs` reporting a full-width crop pixel-identical
across the background/foreground cycle, confirmed non-vacuous against a negative control before
being trusted (`docs/DECISIONS.md`). `evidence/p4k-idle-stopper-terminal/`.

**P4l closes the phase, and it is a re-count rather than a claim.** `evidence/p4-terminal/` had held
the phase's *measurements* - the pty probe, the busybox findings, the three differential harnesses,
the four reference defects and the three of our own - with **no index**, where 80
other evidence directories have one; `evidence/p4-terminal/README.md` is now that index, and it
carries the counting commands themselves so the next re-count is a re-run rather than an invention.
**The `docs/DECISIONS.md` half of this row was already done and the audit is the deliverable**: each
of the four topics it names has a heading written by the sub-step that earned it - *"A shell is
reached through a path named `sh`, and all 304 applets with it"*, *"The Space's shell is named once,
in `SHELL`, and three things read it"*, *"A terminal is ready when `setup` arrives, not when the
socket opens"* with *"terminado's `read_buffer` replays the whole session"* beside it, and *"A
pseudoterminal works on the device, and the terminal phase keeps its shape"* for P4a - so nothing
was added and nothing was rewritten for tidiness.
**The re-count found three claims P4 had made false, which is what a re-count is for.** The Tests
row had drifted on every figure at once (399/1790/170 over 126 drivers and 141 flows, against
424/2104/188 over 141 and 159), *"Not built at all: … a terminal"* had outlived the terminal, and
the table had no Terminal row at all. **And one of the figures was being counted the wrong way**: a
bare `grep -l 'NOT A SUITE FLOW'` reads 14 drivers where `driver_declares` - the `^#`-anchored
reader the sharder itself uses - reads 9, because a driver may name a rule in prose and only a
declaration selects one. That is the defect `scripts/driver-rules.sh` was written to close, so the
row now states which reader it counted with. **No production line changed**, so no flow is due and
none was run - the standing P2z-f…P2z-j already established.

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

**Not built at all:** the Space system, LSP, the debugger, the console, and a
**settings screen** - the five persisted editor settings live in the notebook's View menu until P6c
gives them one. Three entries this line used to carry are gone rather than outstanding: **export**
shipped in P2j, **a terminal** shipped in P4 and has a row in the table above, and **trust** is a
decision rather than a gap (`docs/DECISIONS.md` names the trigger that would make it real).

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

Read out of the local clone at `3f3380c668d`, and every line number below verified against it:

| Fact | Citation |
|---|---|
| The run button is in a gutter **outside** the cell: `width: 35px; left: -35px` | `media/notebook.css:222-228` |
| The gutter is a layout constant: `cellRunGutter: 32` / `codeCellLeftMargin: 28`, and `36` / `8` compact | `notebookOptions.ts:118-136` |
| The gutter node is built per **code** cell | `view/renderers/cellRenderer.ts:273` |
| The execution count is a **separate** node, on the left focus indicator | `cellRenderer.ts:276-277` |
| It is drawn at the editor's **foot**: `top = editorHeight - 22 + statusBarHeight` | `view/cellParts/cellExecution.ts:139,200` |
| Its text is `[N]`, and **`[ ]` when the cell has never run** | `cellExecution.ts:94,133,181,194` |
| Its style: `font-size: 10px`, `--monaco-monospace-font`, `opacity: .7`, `width: 35px`, centred | `media/notebook.css:250-260` |
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
does **not** shut itself down. **This app's own stopper is the one that would**; that was a defect
this phase introduced rather than inherited, and **P4k** closes it.

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
| **P4f-6b-2** | **SHIPPED.** **The colours.** `OSC 4` (set only - **the reference implements no query there**, where xterm does; the `?` falls into the spec and fails to parse, and still reports), `10`/`11`/`12` with their `?` reply, `104`, `110`/`111`/`112` and `119`'s no-op, over `TerminalColors`' 259-entry table and an XParseColor parser. **The defaults are a constructor parameter, not a global**: upstream's `COLOR_SCHEME` is a process-wide mutable static, VS Code's ANSI ramp is **theme-dependent**, and `terminal/` may not reach into `theme/` - so this owns the mechanism, **P4g stays where it is** and owns which colours, and the differential can still hold Termux's own scheme to compare against. Two arms disagree and both are transcribed: `OSC 4` guards its index to `0..255` and `OSC 104` guards nothing, so `104;258` resets the cursor colour and `4;258` is refused. | `TerminalColorsTest` (9) from `TerminalTest.testParseColor` plus the table and the generated cube; `OperatingSystemColorsTest` (13), the reference's five colour tests plus eight measured off `RefScreen`. **A fourth reference crash, and the first an ordinary prompt reaches**: `OSC 104` walks off the end of its own string whenever a token fails `Integer.parseInt` - a **trailing semicolon** does it - and writes an unchecked index into its array. **Fixed rather than shared**, eight forms excluded from the alphabet by name, two tests stating an expectation that is ours (`evidence/p4-terminal/the-colour-reset-that-walks-off-its-own-string.md`). Differential widened with **34 colour sequences**, each carrying its own terminator; the dump gained the table, `painted=` and **`palette=`** - a hash of all 259 defaults, because each side printing deltas against *its own* defaults made a wrong palette cancel out. **169 mutations run, 151 detected, 18 survived, 0 skipped**; ten of this step's twenty-five survived the first sweep and every one was answered by measuring more - five alphabet entries, the palette hash, and **two mutations admitted to be equivalent by construction and replaced**. 17 of the 18 survivors are killed by a named test. |
| **P4f-6b-3** | **SHIPPED.** **The clipboard.** `OSC 52`, with `java.util.Base64` where the reference has `android.util.Base64` - the second and last of the two departures the reference forces, both now taken. The selection parameter is skipped up to the first `;`, or the whole parameter if there is none; a payload that fails to decode is swallowed. **A wrapped payload decodes for a reason that is not the decoder**: a `\n` inside the string is a C0 control the collector never sees - `TerminalEmulator.handleControlCharacter` dispatches it to a screen linefeed first, the same guard P4f-6b-1 wrote for a title's carriage return - so the text that reaches the decoder is already newline-free. `java.util.Base64.getDecoder()`, the strict decoder, is the faithful match; `getMimeDecoder()` was tried first and rejected, because it would also have let genuinely invalid input through (`docs/DECISIONS.md`). | `OperatingSystemClipboardTest` (9): the reference's own `disabledTestSetClipboard` - never run upstream, and run here, its own comment saying why - plus eight measured off `RefScreen`: any selection parameter, none at all, both terminators, an invalid payload, an empty one, a wrapped one and a Unicode one, the last two pinning the C0 finding above. Differential widened with **nine `OSC 52` sequences**; `clip=` follows `said=`'s own per-step-clear shape. **One entry added because a mutation survived without it**: an all-ASCII alphabet cannot tell UTF-8 from Latin-1 decoding apart, since the two agree below `0x80` - closed with a non-ASCII payload rather than left to the unit test alone. **175 mutations run, 158 detected, 17 survived, 0 skipped**; the one that remains is P4f-5d's proven equivalent mutant. **P4f-6b is done**: four commits, one differential that grew from 29 alphabet kinds to 35 and from 114 mutations to 175 without a `SKIPPED` at the end. |
| **P4f-6c** | **SHIPPED.** **DCS, one state rather than two.** `ESC P` and `doDeviceControl`: DECRQSS, which answers only DECSCL by echoing the sequence that would restore it, and the termcap/terminfo query, which answers in hex. **The reference's own DCS guard is looser than OSC's** - an `ESC` mid-string is dropped outright (`case 27: if (mEscapeState == ESC_P) return;`) rather than looked ahead, so the terminator is a **bare** `\` with no `ESC` of its own and `EscapeState.DeviceControlString` needed no `…Escape` counterpart the way `OperatingSystemCommand` did. **The termcap table stops at what the reference's own test exercises** - `Co`/`colors`/`TN`/`name` and `kB` - because every other capability depends on an Android key identity and the cursor/keypad application modes `terminal/` has no caller for until P4j (`docs/DECISIONS.md`). **A malformed hex pair is skipped rather than thrown**, transcribing the reference's own `catch (NumberFormatException e) { continue; }` - found by reading its control flow before shipping, not by a crash after. | `DeviceControlStringTest` (12): the reference's three, plus nine measured off `RefScreen` - a bare backslash with no `ESC`, an unknown-but-valid-hex name, a malformed hex pair, an odd-length name, DECRQSS recognised and not, an unrecognised DCS prefix, and the overflow boundary at 8192/8193 bytes, which the differential's own short alphabet cannot reach. Differential widened with **thirteen `DCS` sequences**, including a `;`-separated multi-name query; **identical on three seeds first run**. **183 mutations run, 165 detected, 18 survived, 0 skipped**; the new survivor is the overflow boundary above, pinned by the two tests that bracket it, and one pre-existing anchor moved when `handleControlCharacter`'s `ESC` branch was split into `onEscape()` to stay under detekt's complexity cap - re-pointed at the same behaviour rather than left `SKIPPED` (`docs/DECISIONS.md`). |
| **P4f-6d** | **SHIPPED.** **APC, and the two surfaces earlier steps parked.** `ESC _`: every byte discarded - the reference's own comment is "Eat APC sequences silently for now" - until a bare `\` follows a lookahead `ESC`, checked **before** the C0 switch at all rather than widening the `BEL`/`ESC` guards `OSC` and `DCS` use, per `docs/DECISIONS.md`'s own note that P4f-6c and P4f-6d each need a predicate of their own. `EscapeParser.accept` sat at detekt's twenty-line cap already, so the two new states join `OSC`/`OSC`-escape/`DCS` under one shared arm rather than adding one - the same move `actOnIntermediate` made for its five. Then `TerminalEmulator.isCursorEnabled` (DECSET 25) and `paste()` (DECSET 2004, `ESC[200~...ESC[201~`, `ESC` and the C1 range stripped, `\r?\n` folded to `\r`) - both delegating to the `ScreenModes` bits P4f-4a already had no caller for. | `ApcTest` (2): the reference's `testApcConsumed`, plus one read off `RefScreen` - a control character (`CR`) is discarded along with the rest of an APC body rather than acting on the screen, closing a mutation the differential's first alphabet could not reach. `ScreenModesTest` gains `testEnableDisableCursor` and `testBracketedPasteMode`, transcribed now that both have a surface. Differential widened with six `APC` sequences (`random.nextInt` 37 to 39); **identical on three seeds, before and after the carriage-return entry**. **186 mutations run, 164 detected, 22 survived, 0 skipped**; all three new mutations detected, the third only after the carriage-return entry closed a real gap in the script rather than dead code. The wider modulus is itself a different random stream - P4f-5c's own finding - so four pre-existing survivors moved off the differential's catch list without anything regressing: measured the way that step measured it, all twenty-two current survivors run against the whole unit suite, twenty-one killed by a named test and the last being P4f-5d's already-proven equivalent mutant. Nothing left covered by neither. **P4f-6 and P4f are closed.** |
| **P4g** | **SHIPPED.** **The colour ramp.** The sixteen ANSI colours resolved from VS Code's `terminalColorRegistry.ts` **registered defaults** (`ansiColorMap`, lines 102-247) — the default themes override none of them, verified by grepping every default theme JSON rather than assumed, so this is the same resolve-through-the-registered-default shape P2u-3 used for the surfaces. `TerminalPalette.VS_CODE_DARK`/`VS_CODE_LIGHT` join `REFERENCE` as two more companion values, built by a generator factored out rather than duplicated. **"SGR to attributes" is no longer here**: this row used to claim it, and P4f-2 has it, because SGR is a CSI sequence and the machine that parses one is P4f's. What was left was a palette question with no parser in it — what index 1 *is*, where P4f-2 settled only that it is 1. **The gate column was wrong about where that lands**: `theme/EditorPalette` would have put sixteen fields nothing in production reads under `check_palette_consumers.py`'s own definition of dead — exactly what P2u-1 exists to catch — so the ramp stays in `terminal/`, outside that gate's domain entirely, the way `REFERENCE` already sits (`docs/DECISIONS.md`). Indices 256-258 (foreground/background/cursor) are out of scope — VS Code registers no default for two of the three — and stay `REFERENCE`'s placeholders for P4i. | JVM equality against the resolved ramp (`TerminalColorsTest`); `check_palette_consumers.py` and its own test suite still green, unchanged — evidence the ramp sits outside the gate rather than depending on it. No Maestro: `terminal/` still has no caller, same exception P4e/P4f-1 onward already state. |
| **P4h** | **Scrollback, and reflow on resize. Four sub-steps, cut before any code was written.** `TerminalBuffer.resize` (`:203-354`) is two structurally separate paths - a fast index-shift for rows-only, and a full rebuild-and-rewrap when columns change - and `TerminalGrid`/`TerminalScreen` were each already at ten of detekt's eleven functions, so each gets exactly one new function in this phase: the fast path, the reflow and the emulator-level wiring cannot share a class. Two corrections the reference reading found: the alternate buffer's `mTotalRows == mScreenRows` invariant means *growing* it also takes the reflow path even though its columns never change, and the lazy resize-on-buffer-switch branch `ModeSwitching.kt` has named unreachable since P4f-4b is exercised by exactly one upstream test, `testShrinkingInAltBuffer`. | Per sub-step below. |
| **P4h-1** | **SHIPPED.** **The ring's fast path.** `TerminalBuffer.resize`'s guard (`:204`) and its `if` body (`:205-232`): pure circular-buffer index arithmetic - shift `screenFirstRow`, adjust `activeTranscriptRows` (skip blank rows below the cursor on a shrink; backfill newly-revealed rows in the *current* style on a grow), shift the cursor row by the same amount. `RowRing.totalRows`/`screenRows` become `var`; `TerminalGrid.screenRows` becomes a computed property reading `ring.screenRows` rather than a second stored field. `TerminalGrid` spends its one remaining function slot (10/11 → 11/11) on `resize`, which takes no `newColumns` parameter at all - a column-changing call is P4h-2's, and nothing can call this one wrong since the parameter to do so does not exist. **The reference's `altScreen` flag - which forces the transcript to zero for the alternate buffer - is not transcribed**: `RowRing.resize` instead clamps `activeTranscriptRows` to `newTotalRows - newScreenRows`, which is `0` exactly when a ring is alternate-shaped and is proven a no-op for a ring shaped like the main screen's, where the invariant `activeTranscriptRows <= totalRows - screenRows` already holds before every call (`docs/DECISIONS.md`). | No upstream JUnit reaches `TerminalBuffer.resize` directly - every reference test goes through `TerminalEmulator.resize`, so `HistoryTest`/`ResizeTest` do not gate this sub-step. Five new (not transcribed) `TerminalGridTest` cases: a shrink absorbing blank rows below the cursor, a shrink the cursor blocks from absorbing anything, a grow inside existing transcript capacity, a grow past it backfilled in the *current* style, and the alt-shaped ring's zero-scrollback invariant surviving a shrink. Widens the **grid-level** differential (`evidence/p4-terminal/grid-differential/harness/`, P4e-4's own harness) with a `resize` op bounded to the fast path - not `RefScreen`/`EmulatorDump`, which are emulator-level and unreachable before P4h-3. **Found a defect of its own, not read off the source**: the reference's two resize paths share one common tail (`:352-353`) that resets a cursor to `0` if the shift carried it negative, and the first draft dropped it - 105 of 9,000 scripted steps disagreed before the fix (`evidence/p4-terminal/a-resize-cursor-left-unclamped.md`). **11 mutations run, 10 detected, 1 proven equivalent for this harness's ring shape and confirmed to fail the alt-shaped unit test instead** (`evidence/p4-terminal/grid-differential.md`). |
| **P4h-2** | **SHIPPED.** **Reflow.** `:233-350`, the whole `else` branch: the blank-line lookahead, the whole-line-vs-trim-trailing-blanks choice, the character-by-character replay respecting wide/combining columns with mid-row `scrollDownOneLine`, the hard-newline row break. Also what actually fires for "the alt buffer grows," per P4h-1's correction above - the dispatch guard now lives in `TerminalGrid.resize` itself (`newColumns == columns && newScreenRows <= ring.totalRows`), so an alt-shaped grow reflows even with its columns unchanged, without `RowRing` ever being told which grid shape it is. A new class, `GridReflow` - matching the ring/selection split `RowRing`/`GridSelection` already set - builds a fresh `RowRing` at the new size and replays through `TerminalGrid`'s existing public write surface (`setChar`, `scrollDownOneLine`, `rowAt(...).lineWrap`) rather than poking ring internals, mirroring how the reference's own slow path calls its own `setChar`/`scrollDownOneLine`/`setLineWrap`. `TerminalGrid.ring` and `columns` are now reassignable so the swap lands atomically. **A second cursor defect, this time the column**: `resize` now returns a `GridCursor(row, column)` rather than a bare row, and the fast path's first draft passed the caller's column through unchanged whenever `RowRing.resize`'s own (already row-clamped, P4h-1) result came back - the reference resets *both* coordinates together when either goes negative, not the row alone. Moved to a shared `GridCursor.clamped` factory both the fast path and `GridReflow.replay` now go through; `RowRing.resize` itself stopped clamping, since it has no column to compare against and cannot apply the joint rule alone (`evidence/p4-terminal/a-resize-cursors-column-left-unclamped-too.md`). | Extends `TerminalGridTest` with five cases: column growth over plain text with no spurious wrap, a column shrink wrapping one row across two, a wrapped row rejoining its continuation before re-wrapping together, trailing blanks trimmed on an unwrapped row, and a grow of an alt-shaped ring (`totalRows == screenRows`) taking the reflow path though its columns never move. Widens the grid differential with column-changing resizes and a second **"alt"** run mode - the ring built alt-shaped from the start, `totalRows == screenRows` on every resize - specifically to hit the guard's second clause: 3000 steps × 3 seeds × 2 modes = 18,000 operations, identical on all six after the fix. **8 of the 18,000 disagreed before it**, all `resize` lines, all the column riding through unclamped. **17 mutations run, 17 detected, 0 survived, 0 skipped** - including P4h-1's own one proven-equivalent mutant, which the new "alt" mode turns into a genuine detector (`ArrayIndexOutOfBoundsException`) rather than leaving it argued away (`evidence/p4-terminal/grid-differential.md`). |
| **P4h-3** | **SHIPPED in two commits, and it found the next unshipped step's own gap by name rather than by chance.** The public entry point. `TerminalEmulator.resize` (`:386-413`) fused with its private `resizeScreen` (`:416-422`) into `TerminalScreen.resize`: the no-op guard, the `< 2` `IllegalArgumentException`, the unconditional DECSTBM margin reset on a row change, the tab-stop rebuild-and-copy-prefix plus unconditional DECSLRM reset on a column change (`TerminalCursor.resize`, `TabStops.resize`), `resizeScreen`'s `newTotalRows` choice. **Row/column stay entirely `TerminalGrid.resize`'s to clamp** - the reference does not clamp them in `resize`/`resizeScreen` either, passing them by reference into `mScreen.resize` and reading back whatever came out; a second clamp in `TerminalCursor` would have repeated the exact mistake P4h-2's `GridCursor.clamped` already centralised away from, so this row's own gate text (which said `TerminalCursor` would clamp them) is corrected by the code rather than followed. `TerminalScreen.resize` assigns the grid's returned cursor back with bare field writes, not `moveTo`, since `moveTo` would clear `aboutToAutoWrap` where the reference's resize never does. `DEFAULT_TRANSCRIPT_ROWS` becomes **1000** - JupyterLab's `packages/terminal/src/tokens.ts:107,160`, since JupyterLab is authoritative for *behaviour* and scrollback depth is behaviour, not presentation. No cell-pixel parameters: `DEFAULT_CELL_WIDTH_PIXELS`/`HEIGHT_PIXELS`'s own KDoc already commits that to P4i. **P4h-3b's differential ran into the P4h-4 gap twice** while widening `EmulatorDump`/`EmulatorDifferentialTest` with a resize step - a resize followed by a buffer switch reaches the lazy resize-on-buffer-switch branch nothing has ported yet, crashing the differential's own state printer once and production once (`evidence/p4-terminal/a-resize-then-a-buffer-switch-reaches-p4h-4s-gap.md`, `docs/DECISIONS.md`). Excluded from the alphabet **by name** rather than reached by chance: both harnesses track one `everResized` boolean and stop drawing the ten buffer-switching `MODES` entries once it is true. `RefScreen.java` also gained an opt-in `SCRIPT` stdin protocol so one process can run a whole `ResizeTest`-shaped sequence, additive over its unchanged one-shot form. | New `HistoryTest.kt` (both upstream methods); new `ResizeTest.kt`, every method **except `testShrinkingInAltBuffer`** (P4h-4's). `EmulatorFixture` gains `resize`, `getStyleAt`, `assertLineWraps`, `assertForegroundIndices`; `assertInvariants`/`assertLinesAre` switch from the fixture's own fixed fields to the screen's live ones - required once a resize can make the fixed ones stale, not merely tidy. `TerminalReportingTest`'s two deferred loops close, `theWindowAndCellAreReportedInPixels`'s third leg staying P4i's. **192 mutations run, 163 detected, 29 survived, 0 skipped** - twenty-six killed by a named test (`ResizeTest` among them, closing this step's own `newTotalRows` miss), one already P4f-5d's proven equivalent mutant, one a new equivalent proved by reading (`TerminalGrid.resize`'s fast path and `TerminalCursor.resize`'s own conditionals are already idempotent on an unchanged size, so `TerminalScreen`'s no-op guard is provably unobservable), and one - `ModeSwitching`'s alternate screen not blanked on a re-entry that already carries content - confirmed to predate P4h-3 entirely (checked against P4h-2, `52ef8da`, in a disposable worktree) and closed the same day, not by changing behaviour: the shipped code already blanked correctly, so `AlternateScreenTest` gained the missing test rather than production gaining a fix, confirmed by re-running `mutations.py`'s own label against the whole suite (`SURVIVED` → `KILLED`). No Maestro - `terminal/` still has no app caller, same exception every P4e/P4f/P4g/P4h row states. |
| **P4h-4** | **SHIPPED.** **Lazy resize on buffer switch.** `TerminalEmulator.java:1249-1275`: the `resized` comparison, save-before-swap/restore-after-swap-back, and the raw unclamped cursor override that survives `restoreCursor`'s own clamp specifically so the resize that follows computes the right remapped position. `ModeSwitching.useAlternateScreen` compares the incoming buffer's size against `screen.columns`/`rows` and on disagreement calls `ScreenBuffers.resizeLiveGrid`, the same resize path P4h-3 wired - moved there from `TerminalScreen` itself, whose sole remaining function slot the public `resize` was already spending, so a second caller needed a home with room. Closes the branch `ModeSwitching.kt:64-73` had named unreachable since P4f-4b. **This row's own gate text undersold what re-enabling the differential's alphabet would cost**: "re-runs the differential with more steps" is not what closed it - `everResized`/`MODES_SAFE`'s hard exclusion had to come off both harnesses outright, since no number of extra steps draws a switch the alphabet itself refuses for the rest of a seed. | `ResizeTest.kt` gains `testShrinkingInAltBuffer`, green first run. `EmulatorDump`/`EmulatorDifferentialTest` drop the `everResized` gate entirely: byte-identical on all three seeds, 4,000 steps, 12,000 operations, both crashes `evidence/p4-terminal/a-resize-then-a-buffer-switch-reaches-p4h-4s-gap.md` recorded now exercised rather than excluded. **196 mutations run, 167 detected, 29 survived, 0 skipped** - four new, targeting this step's own lines, all detected; three pre-existing ones needed re-anchoring rather than re-deriving, since the fix moved the lines they targeted, and all three still detected once re-pointed. **One of those three is now caught by the differential itself for the first time**: P4h-3b's own "entering the alternate screen does not blank it" survivor was closed only by the whole unit suite at P4h-3b; unchanged, it is a differential-level detection now, because lifting the exclusion means the stream draws far more buffer switches per seed than it was ever allowed to before - measured in `evidence/p4-terminal/emulator-differential.md`'s own P4h-4 section, not argued. No Maestro - same exception every P4e/P4f/P4g/P4h row states. |
| **P4i-1** | **SHIPPED.** **Reach it, and read/write it plainly.** `Terminal(name)` joins `Editor`/`FileEditor` as a document `NavKey` (`entry<Terminal>` on `ListDetailSceneStrategy.detailPane()`), and `NotebookBackStack.openTerminal` replaces whatever detail is open exactly as the other two kinds do - a fresh session name never equals the one on top, so a second "New terminal" tap always replaces rather than reveals. `ui/terminal/TerminalPaneViewModel` is the binding P4a-P4h's own KDoc named P4i for: `TerminalReplies` → `TerminalGateway.send` (the emulator's own DA1/DCS answers go back to the shell exactly as a keystroke does), `TerminalNotices` → a `TerminalPaneUiState`, and `attach()`'s events → `emulator.append`. `ui/terminal/TerminalPaneScreen` draws the grid as one `Text` **per row** rather than one blob - not a Maestro convenience but a correctness fix a real device run found: a giant multi-line node fails a `visible:` assertion outright (Maestro full-matches a node's text), and it would have read TalkBack a whole screenful as one utterance regardless. Input is a `BasicTextField`, diffed against what it last held (append → typed text, shrink → that many DEL bytes) rather than reinventing key encoding, which stays P4j's. **Reach point is a departure, written down**: a second icon beside `ExplorerScreen`'s New menu, not a fourth item inside it - that menu's own KDoc already states parity with the reference's New widget (exactly three, no terminal), and the reference itself reaches `terminal:create-new` from a Launcher this app does not have until P6d. **Two real defects only a device run found**: `codeTextStyle()` alone draws black on a `BasicTextField` regardless of theme (`EditorTheme.kt`'s own documented pitfall - the fix is `codeSourceStyle()`, not a new one), and `imePadding` belongs on the input field itself, not a shared ancestor Column - `SymbolRow`/`NotebookBar`'s own precedent, and putting it on the whole screen pulled the output column's height budget negative under a real keyboard. | `TerminalPaneViewModelTest` (readiness, typed text round-tripping through a fake echo, the emulator's own replies reaching `send`, `Ended` surfacing its reason); `TerminalNavigationTest` (tap through, type, existence in the tree - not `assertIsDisplayed`, which reports every node "off-screen" once a real IME resizes this test host's own window, checked against the app-bar title carrying the identical offset). Maestro on both devices: `p4i-terminal-screen` (either device - `FakeTerminalGateway`'s echo proves the reach/render/input wiring) and **`p4i-terminal-tablet`** (TABLET ONLY - a real shell answers `echo $((6*7))` → `42` and `ls` lists a file the driver seeded, per `docs/DECISIONS.md`'s "a pty echoes, so `type X, read X back` proves nothing"). The tablet driver found its own defect: a flow's own `launchApp: clearState: true` wipes what the driver just seeded **and** the already-installed Space, forcing a full rootfs reinstall every run - fixed to a bare `launchApp` after `reset_notebooks`/`seed_file`, `a2-empty-notebook`'s own precedent, with a 120 s first wait for the cold-install case, matching `notebook-session-reattaches`/`p1d-part1-run-a-cell`. `evidence/p4i-terminal-screen/`, `evidence/p4i-terminal-tablet/`. |
| **P4i-2** | **SHIPPED (4 commits, P4i-2a-2d).** **Faithful rendering.** `TerminalPalette.VS_CODE_DARK`/`VS_CODE_LIGHT` resolved to their own foreground/background/cursor (P4i-2a) - `TerminalPalette.kt`'s own KDoc had left them `REFERENCE`'s placeholders - by walking VS Code's runtime fallback (`terminalColorRegistry.ts`, `xtermTerminal.ts`) rather than one theme file, and `TerminalPaneScreen` picks between them from `isSystemInDarkTheme()`. `TerminalEmulator.resize` gains an optional cell-pixel pair defaulting to "unchanged" (P4i-2b), closing `TerminalReportingTest.theWindowAndCellAreReportedInPixels`'s documented-pending third leg. `terminalCellMetrics()` measures a real "X" through a `TextMeasurer` at `codeTextStyle()` - Compose's own equivalent of `TerminalRenderer`'s `Paint.measureText`/`getFontSpacing` (P4i-2c). `TerminalCanvas` replaces the `Text`-per-row stand-in with batched-run glyphs (`GlyphStyle.kt`'s own resolved colours and `TextStyle`, mirroring `TerminalRenderer.drawTextRun`'s run-batching, bold-bright, inverse and dim rules) and a block cursor drawn only while `isCursorEnabled` (P4i-2d). **A defect only the device found**: a `Canvas` carries no semantics of its own, so the swap broke every existing `onNodeWithText`/Maestro `visible:` assertion against terminal output, including P4i-1's own two shipped flows - fixed with one transparent `Text` per row stacked over the `Canvas`, `docs/DECISIONS.md`'s own entry for the finding and the fix. | JVM: `TerminalPaletteTest` (the three resolved dynamic slots, dark and light), `ResizeTest`/`TerminalReportingTest` (the closed pending leg, a plain resize leaves the cell size unchanged). Instrumented (both devices): `TerminalCellMetricsTest` (a real, stable measurement, not the placeholder 13x15), `TerminalCanvasTest` (a coloured background resolves to the palette's own value, the cursor pixel appears only while DECTCEM is set), `TerminalNavigationTest` re-passing after the accessibility fix. Maestro: `p4i-terminal-screen`/`p4i-terminal-tablet` re-run green (the accessibility regression's own proof), and the closing gate, tablet only - `p4i-terminal-vi`: `vi` full-screen, its own alternate-screen redraw and `~` fill lines visible, proof execution-only content reached the `Canvas` rather than an echoed keystroke. `evidence/p4i-terminal-vi/`. |
| **P4i-3** | **SHIPPED (2 commits, P4i-3a-3b).** **Layout-driven resize and attach lifecycle.** `TerminalCanvas` became a `BoxWithConstraints` rather than a self-sized `Box` (P4i-3a): the grid it draws now comes from the container's own measured area, `WindowInsets.ime`'s current bottom inset added back before computing it so opening the keyboard - `CellCompletions.kt`'s own established technique - never masquerades as a real resize, which stays P4j's. A pure `terminalGridSize` turns that measurement into columns/rows; `TerminalPaneViewModel.resize` is the new entry point - a no-op once the grid repeats - that resizes the local emulator, bumps redraw so the next `key(state.redraw)` rebuild reads it, and calls `TerminalGateway.resize`, the wire `set_size` this app had never once sent. **A device run found the proof needed correcting before the code did**: `stty size` answers a fixed 25x80 default in this rootfs's busybox/toybox build rather than a live `ioctl`, unrelated to whether the resize works - confirmed by `vi`, whose own full-screen redraw (its `~` fill lines) changed across the same rotation the wire measurement did (`docs/DECISIONS.md`). `attach()` is a plain suspend function now (P4i-3b), no longer launched in `init` - `TerminalPaneScreen`'s own `repeatOnLifecycle(Lifecycle.State.STARTED)` launches it, a deliberate departure from `ExplorerScreen.KeepTheListingFresh`'s `RESUMED`: a live socket should stay live while merely visible, not tear down on every transient partial-obscure. **A second device-found defect**: terminado's own `read_buffer` (`management.py`: `deque(maxlen=1000)`, "historical messages for reconnection") replays the *whole* session on every attach, not only what was missed while backgrounded - appending that onto an emulator that already drew it once duplicated the screen (`evidence/p4i-terminal-background/`). Fixed by clearing the screen and scrollback (`ESC[H`/`ESC[2J`/`ESC[3J`, all three already `terminal/`'s own) before a re-attach's replay, so the replay becomes the sole source of truth. | JVM: `TerminalGridSizeTest`; `TerminalPaneViewModelTest` gains `resize`'s wire-call/dedup/redraw-bump cases, `attach`'s cancellation and re-attach cases, and the re-attach clear-before-replay case. Instrumented (both devices): `TerminalCanvasTest` gains a real-measured-grid case; its two pre-existing cases updated for the new sizing contract (a bounded `Modifier.fillMaxSize()` rather than the canvas self-sizing). Maestro, tablet: `p4i-terminal-resize` (rotate mid-session, `vi`'s own reflow proves it - `evidence/p4i-terminal-resize/`); `p4i-terminal-background` (background, foreground, a *second* command still gets an answer - proof of a live re-attach, not just old pixels - and the first is still visible too, not duplicated - `evidence/p4i-terminal-background/`); the three P4i-1/P4i-2 flows re-run green on both devices. |
| **P4j-1** | **SHIPPED.** **The UX gap P4i-1 already named but did not close, confirmed on the device during P4i-3a**: `TerminalInputField` used to be a visibly separate `BasicTextField` sitting under the terminal output, not attached to the shell's own prompt the way Termux's `TerminalView` is - Termux has no second input surface at all, the terminal view itself is the `View` that receives key events and the block cursor *is* the only input point on screen (confirmed reading `TerminalView.onCreateInputConnection` - it becomes the IME target directly via a `BaseInputConnection`, and never touches `CursorAnchorInfo`: the block cursor it draws is a rendering concern with no tie back to the IME's own caret). Compose's own equivalent, confirmed against this project's pinned `androidx.compose.foundation:foundation-android:1.10.6` (`gradle/libs.versions.toml`'s `androidxComposeBom = "2026.03.01"`) by reading `BasicTextField.kt`/`TextFieldDecorator.kt` directly rather than assuming: the `TextFieldState`-based `BasicTextField` overload's `decorator: TextFieldDecorator?` parameter is public and stable, and a decorator that never calls `innerTextField()` still leaves the field genuinely focusable and IME-connected, because `TextFieldDecoratorModifier` - focus, touch-to-focus, the IME session - sits on the field's outer `Box`, outside a decorator's reach. `TerminalInputField` migrated off its legacy `value`/`onValueChange` overload (which carries no `decorator`) onto the `TextFieldState` one, sized to fill the same `Box` as `TerminalCanvas` rather than docked below it in a `Column`, with a no-op decorator and a transparent `cursorBrush` so nothing it owns paints - `TerminalCanvas` still draws every glyph and the block cursor from the emulator's own grid. `sendTerminalDiff`'s diff-to-keystrokes logic is unchanged; only its caller moved, from `onValueChange`'s parameter to a `reportTerminalKeystroke` function driven by `snapshotFlow { state.text }`, extracted so a plain `TextFieldState` proves it without a Compose test harness - `PythonTypingRulesTest`'s own reason. | New `TerminalInputFieldTest` (JVM): the diff-and-clear logic against a real `TextFieldState` - an appended suffix, a shortened prefix, a trailing newline clearing the field, no change sending nothing. `TerminalPaneViewModelTest` unchanged and re-passing (this sub-step touches no ViewModel logic). Instrumented (both devices): `TerminalNavigationTest`/`TerminalCanvasTest` re-passing unmodified. Maestro, both devices - `p4j-terminal-input`: type `ls`, and the terminal grid itself shows it after the prompt with no separate box beneath - `evidence/p4j-terminal-input-tablet/` (SM-X216B, a real shell), `evidence/p4j-terminal-input-phone/` (`dev36`, `w320dp` - `FakeTerminalGateway`'s echo proves the same wiring at phone width). |
| **P4j-2** | **SHIPPED.** **A real key encoder.** `terminalKeystroke(key, ctrlPressed, keyCode, applicationCursorKeys)` in `ui/terminal/TerminalKeyEncoding.kt`: an arrow key becomes CSI (`ESC[A/B/C/D`) or, once the shell sets DECCKM (`DecMode.APPLICATION_CURSOR_KEYS`, exposed as `TerminalEmulator.isApplicationCursorKeys` beside `isCursorEnabled`), SS3 (`ESCOA` etc.) instead; Ctrl+letter becomes the matching C0 control byte (`KEYCODE_A`..`KEYCODE_Z`'s own contiguous range is the offset, so Ctrl-C is `0x03`). Takes plain values rather than a whole platform `KeyEvent`, so a JVM test drives every branch without `android.view.KeyEvent`'s own getters (stubbed to throw outside an instrumented test) - `PythonTypingRulesTest`'s own reason. Wired into `TerminalInputField` via `Modifier.onPreviewKeyEvent`, ahead of `BasicTextField`'s own default handling (which would otherwise move its invisible caret rather than the shell's), reading `applicationCursorKeys` fresh on every key since a shell can flip DECCKM at any time. **Maestro cannot drive this claim itself**: reading `maestro.KeyCode`'s own enum (decompiled from `maestro-client.jar`) turned up no arrow keys and no modifier-combination primitive at all - only `ENTER`/`BACKSPACE`/`TAB`/`ESCAPE`/`HOME`/etc. Device proof instead comes from Compose's own `performKeyInput` (a real hardware-shaped `KeyEvent`, dispatched exactly as a bluetooth keyboard's would be) for the wiring, and from a driver-issued `adb shell input keycombination 113 31` for the byte actually reaching a real shell - `input`'s own arguments are two key codes to press together, not a meta flag plus one, confirmed by the device rejecting `4096 31` outright ("Unknown keycode: 4096") before `113 31` (`KEYCODE_CTRL_LEFT`, `KEYCODE_C`) worked. **A `yes`-based first attempt at the device proof was rejected by its own evidence**: two screenshots of a screen saturated with identical `y` glyphs are pixel-identical whether or not the process was actually killed, so the flow instead runs `sleep 30`, which prints nothing on its own - `^C` (the line discipline's own echo) and a fresh prompt appearing within seconds is proof only a real interrupt byte can produce, `docs/DECISIONS.md`'s own rule that an assertion must name something that cannot appear in the prompt or the command line's own echo. | New `TerminalKeyEncodingTest` (JVM, 7 cases): each arrow in both cursor-key modes, Ctrl-A/Ctrl-C, a letter without Ctrl, an unrelated key. New `TerminalHardwareKeyTest` (instrumented, both devices): a real `performKeyInput` Ctrl+C and an Up arrow each reach a recording fake gateway as the exact bytes, composing `TerminalPaneScreen` directly rather than through `MainNavigation`. `TerminalNavigationTest`/`TerminalCanvasTest`/`TerminalPaneViewModelTest` unchanged and re-passing. Maestro, tablet only (a real shell to interrupt) - `p4j-terminal-ctrl-c-part1`/`-part2`, split around the driver's own `adb shell input keycombination` call the same way `p4i-terminal-resize`'s driver splits around a rotation: `evidence/p4j-terminal-ctrl-c/`. |
| **P4j-3a** | **SHIPPED.** **The extra-keys row, latching Ctrl/Alt.** `docs/PLAN.md`'s own P4j-3 split into four sub-steps here, too big for one commit. `TerminalExtraKeysRow` generalises `SymbolRow`'s shape (`Surface`/scrolling `Row`/`imePadding`/`navigationBarsPadding`) rather than a second row built from scratch, talking to `TerminalPaneViewModel` the way `SymbolRow` talks to `FocusedCellInput`. Termux's own `SpecialButtonState{isActive,isLocked}` (`extrakeys/SpecialButtonState.java`) is `TerminalModifierLatch`: a tap toggles `isActive` (off also clears `isLocked`), a long-press locks both together from the state *before* the change - caps-lock style - and a consuming read (`readSpecialButton(button, autoSetInActive=true)`'s own shape) clears `isActive` afterward unless locked. That read happens once per **character typed on the soft keyboard**, not once per string - Termux's own `readControlKey()` is consulted per `inputCodePoint` - so an unlocked latch correctly modifies only the first character of a multi-character diff and a locked one converts every character until tapped off. `terminalKeystroke` (P4j-2) is what every other key - Esc, Tab, arrows, Home/End, PgUp/PgDn - goes through, grown to cover them: Home/End take the same CSI-letter/SS3-letter pattern as arrows, PgUp/PgDn are the VT220 tilde sequences with no application-mode variant, Tab and Escape are bare bytes - read against Termux's own `KeyHandler.java` rather than invented. So the row sends exactly what a hardware key of the same name already sends, and - a side effect of not duplicating the encoding, not new scope - a hardware Tab/Esc/Home/End/PgUp/PgDn now reaches the shell too, where before only arrows and Ctrl+letter did. **The tablet proof is the latch pipeline plus one plain tap, not eight keys**: `TerminalExtraKeysRowTest` already proves every key's exact bytes reach a real device's own semantics tree, on both devices, through a recording fake - what only a real shell adds is confirmation a real program interprets the bytes, and that needs proving once per *mechanism* (the latch pipeline, via a tapped Ctrl converting a typed letter into the byte that interrupts a real `sleep 30`; a plain pre-encoded tap, via Esc leaving a real `vi`'s insert mode before `:wq` can write anything) rather than eight times over keys sharing the same two mechanisms. **A device run found a test-writing trap, not an app defect**: typing a real dictionary word (`hello`) immediately before tapping a row key left its last letter unwritten and, once, `vi` never quit at all - Gboard's own autocomplete/predictive-composing machinery for a recognised word, confirmed rather than assumed by the same steps against a nonsense string (`zqxvk`) landing correctly, byte for byte, in the file `vi` wrote. | JVM: `TerminalModifierLatchTest` (the tap/long-press/consume transitions), `TerminalKeyEncodingTest` gains Home/End/PgUp/PgDn/Tab/Escape and `applyModifierLatches`'s own cases (a consumed latch converts one character, a locked one converts every character, a non-letter under Ctrl stays literal but still consumes the latch). `TerminalPaneViewModelTest` gains `typeCharacters`/`tapModifier`/`longPressModifier`. Instrumented, both devices: `TerminalExtraKeysRowTest` - every key reaches a recording gateway as the right bytes, Ctrl visibly latches (its content description names `active`/`locked`), a long-press locks it across multiple letters, every key clears Material's 48dp. Maestro: tablet (`p4j-3a-terminal-extra-keys`) - a tapped Ctrl plus a typed letter interrupts a real `sleep 30`, and a tapped Esc leaves a real `vi`'s insert mode, provable only by what `:wq` then writes (`evidence/p4j-3a-terminal-extra-keys/`); phone-width emulator (`p4j-3a-terminal-extra-keys-phone`) - the row renders and every key is reachable at 320dp and a tap visibly latches, `FakeTerminalGateway`'s echo having no glyph for a control byte to prove wire content with instead (`evidence/p4j-3a-terminal-extra-keys-phone/`). |
| **P4j-3b** | **SHIPPED.** **`set_size` actually resizes on IME open.** `TerminalCanvas` no longer adds the IME's own inset back before computing `terminalGridSize` - it carries its own `imePadding()` on its `BoxWithConstraints`, the same rule `TerminalExtraKeysRow`/`SymbolRow`/`NotebookBar` already follow, so a keyboard opening is just another size change through the existing `LaunchedEffect(gridSize, cell)`, the way Termux's `TerminalView.onSizeChanged` -> `updateSize()` treats every size change alike. P4i-3a's own compensation answered a `Column` layout P4j-1 already replaced with a `Box`; left in, it was measured actually inflating the grid past what the pty had ever been told, not merely declining to shrink for one. **Two findings cost more than the fix itself, both in `docs/DECISIONS.md`.** Removing `TerminalPaneScreen`'s `key(state.redraw) { ... }` wrapper (reasoned as unnecessary accessibility-node churn, a plain `redraw: Int` parameter doing the same "don't skip recomposition" job without a full teardown) looked like a strict improvement and was a real regression instead - a real device stopped landing any typed character in the terminal at all with it removed, restored working with `key()` put back and every other line of the fix unchanged. And Maestro's own `visible:`/`extendedWaitUntil` proved unable to reliably read this app's terminal text at all, reproduced against the untouched pre-P4j-3b build too - not this phase's defect, but blocking its own gate, so the gate reads the screenshot's own pixels (`terminal_content_extent.py`) instead of accessibility text. | JVM/instrumented unaffected (unchanged files). Instrumented, tablet and emulator: `TerminalCanvasTest.theGridShrinksWhenTheImeInsetOpens` - a synthetic `WindowInsetsCompat` dispatched onto the activity's `decorView` stands in for a real keyboard, proving the grid genuinely shrinks; red against the old compensation (measured inflating instead of shrinking), green against the fix. `terminal_content_extent_test.py` (8): the pixel-based oracle's own thin-stroke-vs-thicker-chrome logic, in both light and dark theme polarities. Maestro, tablet: `p4j-3b-terminal-ime-resize` - a real `vi`'s own content extends further down once the keyboard closes than while it was open, measured off the screenshot's own pixels rather than accessibility text (`evidence/p4j-3b-terminal-ime-resize/`). |
| **P4j-3c** | **SHIPPED, in two commits.** **Terminal font size.** `TerminalSettingsRepository` (`domain/port/`, `data/fake/`, `data/disk/`) mirrors `EditorSettingsRepository`'s own port/`Fake`/`Disk` shape rather than reusing it - a terminal's glyph size is not an editor concern (`CLAUDE.md`'s "one home per kind of logic"). `TerminalSettings.fontSizeSp`, bounds/step measured against Termux's own `TermuxAppSharedPreferences` (4 min, 256 max, ±2 per step) exactly as this row already committed to, default kept at 14 - `CodeText.size`'s own value - so shipping the setting changed no pixel until a user acted. `codeTextStyle(fontSize: TextUnit = CodeText.size)` gained the parameter every other of its 19 call sites still defaults past, its line height computed through a newly extracted `CodeText.lineHeightFor(size)` rather than a second, undertested copy of the reference's `1.3077` ratio; `TerminalCanvas` gained a `textStyle` parameter feeding both `terminalCellMetrics` and its own glyphs from the one style `TerminalPaneScreen` resolves. **The control is JupyterLab's own shape, not Termux's**: `terminal:increase-font`/`terminal:decrease-font` (`terminal-extension/src/index.ts:64-66`) are a discrete stepper in the terminal's own overflow (`TerminalActionsMenu`, mirroring `NotebookActionsMenu`'s `Box`+`IconButton`+`DropdownMenu` shape and its own precedent for "no Settings screen until P6c, so it sits here"), each row disabled at its own bound - the reference's own `isEnabled` - rather than Termux's pinch-to-zoom: no `pointerInput` gesture exists anywhere in this codebase yet, and P4j-3d is the step that establishes one, not this. `TerminalPaneViewModel.changeFontSize(deltaSp)` is the one function both rows call (signed, rather than two near-identical functions - `detekt`'s own `TooManyFunctions` found the class at its cap), clamping and persisting through the repository. **A device-measured false start, not a defect in the setting**: the gate's first real run measured no reflow across three font-size changes at all - not a bug in `TerminalPaneViewModel`, but the tablet's own landscape default putting the terminal into `ListDetailSceneStrategy`'s two-pane layout, where the terminal's own content starts around x=716 rather than the screen's left edge every existing pixel-oracle constant (`_BACKGROUND_SAMPLE_X`, every default `x_range`) is tuned against a single-pane capture for. Fixed by rotating to portrait first (`p4i-terminal-resize-driver.sh`'s own `rotate_to`), reusing that already-proven geometry rather than teaching the oracle a second pane layout. | New `TerminalSettingsRepositoryContract`/`DiskTerminalSettingsRepositoryTest` (JVM): default, round-trip, survives relaunch, an unreadable store reads as the default. `CodeTextTest` gains `lineHeightForAnotherSizeUsesTheSameRatio`. `EditorThemeTest` gains `codeTextStyleOverridesTheSizeAndItsLineHeightTogether`. `TerminalCanvasTest` gains `aLargerTextStyleMeasuresAWiderCell` (instrumented, both devices - two `TerminalCanvas`es at 10sp/30sp in one composition, the reported cell width compared). New `TerminalActionsMenuTest` (instrumented): the row is reachable through `TerminalBar`, both commands fire, each disables at its own bound. `TerminalPaneViewModelTest` gains `changeFontSize`'s clamped stepping and persistence. New `terminal_row_spacing_px` in `terminal_content_extent.py` (6 new cases in `terminal_content_extent_test.py`): the pixel gap between consecutive rows' own ink, reusing `_ink_bands`'s thin-glyph filter - `terminal_content_bottom_y` would not have discriminated a font-size change, since a bigger font still fills the same screen with fewer, taller rows and only the *bottom* of the content barely moves; the *spacing between rows* is what actually tracks the setting. Maestro, tablet (portrait, rotated by the driver): `p4j-3c-terminal-font-size` - two taps on Increase then one on Decrease against a real `vi`, row spacing 35px -> 42px -> 39px (`evidence/p4j-3c-terminal-font-size/`). |
| **P4j-3d-1** | **SHIPPED.** **The viewport's own arithmetic.** Two Explore passes over `RowRing.kt`/`TerminalGrid.kt` answered this row's own open question first: `rowAt(externalRow)` already accepts any `externalRow` down to `-activeTranscriptRows` (P4f), so gesture scrollback is a rendering-and-gesture change, not a new storage-read path - nothing in `terminal/` needs a new way to *read* history. `TerminalGrid.clampedTopRow(current, deltaRows)` is Termux's own `TerminalView.doScroll` clamp (`Math.min(0, Math.max(-activeTranscriptRows, mTopRow + delta))`) as one line of pure row arithmetic, taken as an **extension function** rather than a member - `TerminalGrid` was already at detekt's `TooManyFunctions` cap (P4h-1's own "spends its one remaining function slot" finding), and this needs nothing but the one property the class already exposes. | New `TerminalGridTest` cases: clamps at `0` (can't scroll past live), clamps at `-activeTranscriptRows` (can't scroll past the oldest row history holds), a mid-range delta lands exactly, and the regression Termux's own `onScreenUpdated` defends against - a `current` already out of range (e.g. after a resize shrinks `activeTranscriptRows`) re-clamps into range in one call rather than compounding. No UI change, no Maestro - pure engine, same precedent as P2x-1a/P2y-a. |
| **P4j-3d-2** | **SHIPPED.** **Draw from an offset.** `TerminalCanvas` gains `topRow: Int = 0` (every existing call site and test keeps compiling and passing unchanged); the `Canvas` draw loop and the parallel transparent-`Text` accessibility loop both become `topRow until topRow + screen.rows`; `drawCursor` is called only `if (topRow == 0 && emulator.isCursorEnabled)` - Termux's own row-window side effect (the cursor's fixed screen row simply falls outside `[topRow, topRow+rows)` once scrolled) made an explicit condition here, since this file's loop bounds are now a parameter rather than always `0`. **A finding the parameter alone didn't fix**: `drawRun`'s pixel math used the *external* row index as its own `y` multiplier, so a scrolled-back row drew at a negative (off-screen) `y` instead of at the window's own top - fixed by threading `topRow` through `drawRow` so the pixel row is `rowIndex - topRow`, always `0`-based; the `Column`-based accessibility layer needed no equivalent fix, since it stacks `Text`s in loop order rather than positioning by row number. | New `TerminalCanvasTest` cases (instrumented, tablet): a non-zero `topRow` renders the scrolled-back row's own content instead of the live top row (via the existing transparent `Text` nodes, `onNodeWithText`); the cursor is absent once `topRow != 0`. Still no gesture, no Maestro - `topRow` is a hardcoded test parameter, exactly how P4i-3a proved `resize` before anything called it from touch. |
| **P4j-3d-3** | **SHIPPED.** **The gesture, the ViewModel wiring, and the Maestro proof.** `TerminalPaneUiState` gains `topRow: Int = 0`; `TerminalPaneViewModel.scrollBy(pixelDeltaY, cellHeightPx): Float` accumulates a pixel remainder across calls (Termux's own `mScrollRemainder`), converts whole cell-heights to row steps, clamps through `clampedTopRow`, and returns the pixel amount actually consumed - not the raw input, so a future fling's own deceleration could stop cleanly at a clamped boundary rather than believing more was consumed than the grid actually moved. `attach()`'s `Output` branch resets `topRow` to `0` on every event, Termux's own default path with no carve-out ported for text selection or an auto-scroll-lock toggle, since neither exists in this app - a named simplification, not a silent omission. **The plan's own committed approach - Compose's idiomatic `Modifier.scrollable`/`rememberScrollableState` over a hand-ported `GestureAndScaleRecognizer`+`Scroller` - failed outright on the real device**: a swipe produced no scroll at all, because `TerminalInputField`'s full-bleed `BasicTextField` consumes touch movement on `PointerEventPass.Main`, the default pass every gesture detector in this codebase (`scrollable` included) already uses, and Compose dispatches that pass descendant-to-ancestor - the field, lower in the tree, always sees it first, sibling or ancestor placement makes no difference. The fix (`detectTerminalScrollDrag`, `TerminalPaneScreen.kt`) is a small custom `pointerInput` on `PointerEventPass.Initial` instead - dispatched ancestor-to-descendant, ahead of the field's own pass - tracking `viewConfiguration.touchSlop` itself before consuming anything, so a plain tap still reaches the field beneath. **Narrower than the row first committed to, closed rather than carried silently**: drag only, no fling - `Modifier.scrollable`'s own value was its free fling deceleration, which a from-scratch `PointerEventPass.Initial` detector does not get merely by existing; this gate names a drag revealing history, not momentum, so fling is a follow-up the same shape as P4j-3c's own pinch-to-zoom deferral (`docs/DECISIONS.md`). | `TerminalPaneViewModelTest` gains `scrollBy`'s clamped stepping/remainder-carrying cases and the reset-to-bottom-on-output case. **The Maestro oracle itself needed two corrections, both device-measured**: a first version searched a screenshot for ink at a column no `seq` row's own digits could reach, and read `TerminalExtraKeysRow`'s own button-label ink there, then - once excluded - a soft keyboard's own key-label ink there too; replaced with `terminal_top_strip_differs`, comparing a fixed strip just below the app bar between two captures pixel-for-pixel, a position neither piece of chrome ever reaches regardless of scroll state (`docs/DECISIONS.md`). Maestro, tablet: `seq 1 60`, five drags, the terminal's own top strip changes (`evidence/p4j-3d-3-terminal-scrollback/02-scrolled-back.png` shows the flow's own command line and rows 1-38, not the live tail's 23-60); `echo done` changes it again, back toward the live tail - this row's own gate: "scroll back into real output a real shell produced, and show it, not the live screen." |
| **P4j-3d-4** | **SHIPPED.** **The alternate screen redirects a drag to arrow keys.** `TerminalPaneViewModel.scrollBy` checks `emulator.screen.buffers.isAlternateLive` first: while the alternate screen is live (`vi`, `less`, `htop` - which carry no scrollback at all, `totalRows == screenRows`), a drag sends Up/Down arrow keystrokes instead of moving `topRow`, reusing the arrow-key bytes `TerminalKeyEncoding.kt`'s `terminalKeystroke` already encodes for a hardware key (P4j-2) - Termux's own `doScroll` special case for `isAlternateBufferActive()`. **The row's own plan was wrong about needing a new `TerminalScreen.isAlternateLive` delegate** - `ScreenBuffers.isAlternateLive` was already public and already reachable through `TerminalScreen`'s own public `buffers` property, so nothing was added to `terminal/` at all; caught by checking the actual visibility before writing code that would have duplicated it. Both branches live as local functions inside `scrollBy` rather than new sibling members - `TerminalPaneViewModel` was already at detekt's `TooManyFunctions` cap, the same reason P4j-3d-1's `clampedTopRow` is an extension rather than a member - and the whole function collapses to one `return when {}` rather than the five early returns detekt's `ReturnCount` capped at two. No mouse-tracking branch is ported - this is a touchscreen app with no mouse events to redirect there, an adaptation rather than a gap. | `TerminalPaneViewModelTest` gains the alt-buffer-redirect cases (Up, Down, one arrow press per whole row of drag). Maestro, tablet: a five-line fixture opened in `vi`, a drag moves the cursor from line 1 to line 5 while `vi`'s own `~` fill stays at the same pixel row throughout - two oracle calls, not one: `terminal_content_bottom_y` (tuned for `~`, unchanged) proves the screen never scrolled, `terminal_top_strip_differs` (over a taller strip covering the file's first several lines) proves the cursor's own cell moved. **Two device-measured false starts before this passed**: `extendedWaitUntil: visible: "~"` timed out - the same P4j-3b finding this phase already named, re-applied rather than re-discovered, fixed by a fixed wait instead; and the flow's own swipe started *inside* the soft keyboard's own bounds (a starting touch there never reaches this app's gesture handling at all, a different window), fixed by keeping both ends of the swipe above 65% of the screen height. |
| **P4k** | **SHIPPED, in two commits.** **The idle stopper stops killing a live terminal** — the defect this phase introduces, closed inside it. `ActiveTerminals` (`domain/model/`) mirrors `ActiveCellRuns`'s standalone-counter shape rather than state kept inside `RealTerminalGateway`/`BaseSpaceTerminalGateway`, because the latter rebuilds its underlying gateway whenever the Space's server restarts (`PerRunningServer`) and a counter there would lose exactly the count this one has to survive across restarts. `ActivityTrackedTerminalGateway` (`data/space/`) wraps whichever `TerminalGateway` `AppContainer` holds — the port itself and its three implementations (`Real`/`BaseSpace`/`Fake`) are unchanged. `IdleServerStopper` combines `cellsInFlight` with a second `StateFlow<Int>` (`combine(...) { cells, terminals -> cells + terminals }`), reusing its existing wait-for-zero/wait-for-positive loop unchanged. | JVM: `ActiveTerminalsTest`, `ActivityTrackedTerminalGatewayTest` (wraps `FakeTerminalGateway`, proves the count moves and every other call still delegates), `IdleServerStopperTest` gains the live-terminal-blocks-stop and terminal-closes-then-stop-fires cases. Maestro, tablet (portrait, rotated by the driver): `scripts/p4k-idle-stopper-terminal-driver.sh` opens a terminal, writes a marker to disk, backgrounds the app 60 s, and reads `ps` for `jupyter_server` directly — the same evidence shape `p1c-app-death-backstop-driver.sh` established, inverted (there the server had to die once idle; here it has to survive). Not `visible:`/`extendedWaitUntil` on the terminal's own text anywhere in this gate — the same P4j-3b finding forbids it. The shell answering after resuming is proved by a second marker written to disk; scrollback intact is proved by `terminal_top_strip_differs` reporting the pre-background and resumed screenshots pixel-identical over a full-width crop, confirmed non-vacuous against a negative control (the same crop over the post-typing screenshot correctly reports `changed`). `evidence/p4k-idle-stopper-terminal/`. |
| **P4l** | **SHIPPED.** **The phase's own re-count, and it is where three false claims surfaced.** `evidence/p4-terminal/README.md` indexes what that directory actually is - the phase's *measurements* rather than a flow's screenshots - one line per artifact naming the sub-step and the question it closed, a pointer table out to the sixteen flow directories - fifteen siblings plus the one nested here - with the device each ran on, and the counting commands themselves, so the next re-count is a re-run. **The `docs/DECISIONS.md` half was already done, and the audit is the deliverable**: all four topics this row names already have headings written by the sub-steps that earned them (`:1614` the `bin/sh` link, `:92` the pinned `shell_command`, `:339` + `:5230` the `setup` frame and the replay, `:1664` what P4a measured), so nothing was added and nothing rewritten for tidiness. **The "Where we are" table gained a Terminal row and lost *"a terminal"* from "Not built at all"** - two claims P4 had made false and only a re-read catches. No production line changed, so no flow is due and none was run. | The counts match `git ls-files`-derived greps rather than the last row's arithmetic: **424** instrumented `@Test` sites, **2062** unit sites answering to **2104** executions over 208 classes (`:app:testDebugUnitTest`, 0 failures), **188** script tests by grep *and* by `python3 -m unittest discover`, **141** drivers over **159** flows, **9** of them `NOT A SUITE FLOW` **as `driver_declares` reads it** - a bare `grep -l` reads 14, which is the very defect `scripts/driver-rules.sh` exists to close. |
| **P4m** | **SHIPPED, and the lead was wrong.** The row read the defect as `sendTerminalDiff` dropping "the third case" - an IME committing a composing word that is neither an append nor a shortened prefix. **Measured on the tablet, it is not the diff.** A `Log.i` on every `(previous, current)` pair the mapping sees, driven by `adb shell input text` with no Maestro in the path, showed every edit arriving whole and the diff forwarding it faithfully - what corrupts the line is **Gboard's automatic quote-completion**: an earlier `'` plus a later `,` and the IME closes the quote in between, so `python3 -c 'import json, sys'` reaches the shell as `'import json', sys'` and busybox `ash` refuses to tokenise it (`evidence/p4m-terminal-input/edit-pairs.md`). `KeyboardOptions(autoCorrectEnabled = false)` clears only `TYPE_TEXT_FLAG_AUTO_CORRECT`; the reference neuters the IME outright - `TerminalView.onCreateInputConnection` sets `TYPE_NULL`, and for Samsung keyboards that ignore it `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS` (`reference/research_repos/termux-app/.../TerminalView.java:307-334`, their #686). `KeyboardType.Password` is the Compose lever for that inputType; the field is invisible, so masking shows nothing. **`sendTerminalDiff` was still made general** - delete back to the longest common prefix, then type the divergent tail - so the two old branches fall out of one rule and a mid-line rewrite is a delete-and-retype rather than a silent drift, as the backstop for whatever still gets through. **`docs/PLAN.md:40`'s own "drops … arrives twice" was the symptom read off a frame; the cause is the IME, and the duplication did not reproduce in the measurement** - it was almost certainly the same quote-completion editing a buffer that had already been cleared. | Three failing unit cases first in `TerminalInputFieldTest` (a mid-line insert, a tail rewrite, a word corrected in place - each a silent drop under the old mapping), then green. The device gate is `scripts/p4m-terminal-input-driver.sh`: a flow types the quoted python line into a real shell on the tablet, redirects it, and the driver reads the file and the exit status back with `run-as`. **Non-vacuous**: with `KeyboardType.Password` removed and nothing else changed the tablet answers `rc=2` and writes no file (`evidence/p4m-terminal-input/gate-without-the-fix.log` vs `gate-with-the-fix.log`). **Not in P4m's scope, recorded so it is not lost**: the rendered command echo appears to wrap ~one column early against the shell's own `COLUMNS=80` - a grid-geometry question. |

**This supersedes Step 11's Console REPL for shell work but does not replace the Console** (P6b),
which shares the notebook's kernel namespace — something a shell cannot do.

---

# P7 — the gaps P2–P4 left

**The number is a label, not an order**, exactly as `P3 — Spaces` below is. This phase sits here
because it runs here: five things earlier phases let through, closed one at a time before P5
starts. Four are presentation and navigation; the fifth is a runtime gap that makes the app
unable to install a Python package at all.

**Where P7 is: P7a-0 through P7a-3, P7b, P7c, P7d, P7e-0 and P7e-1 are shipped; P7e-2 is next.** The
order is the one the phase was approved with - measure the runtime gap first, then implement in risk
order. **P7e-1 changed two rows below it by measuring them**: its link went somewhere other than
where its own row said, and its device test settled which of the two exec refusals binds, which is
what P7e-2's design turns on. The remaining order is **P7e-2 -> P7e-3... -> P7f**. **P4m shipped
first** (the terminal input defect P7e-1's frames found, outside this phase): P7e-2's own gate
types into that terminal, so it had to receive what is typed before the gate could mean anything -
and the tablet measurement reframed it from a diff bug to Gboard's quote-completion.

Two devices are what this phase needs and neither is optional: the tablet `SM-X216B`
(`TABLET_SERIAL`) for anything that runs a cell and for every two-pane frame, and the `dev36`
emulator for the one-pane half. A width rule proved on one device is proved by half.

**P7c corrected the second half of that sentence, and it is worth keeping.** A 1920x1200 @240dpi
emulator *is* a 1280dp x 800dp window, so the two-pane frames a step owes can be captured there
whenever the step runs no cell - which is what P7c did for both its widths. The tablet stays
compulsory for anything that runs one, because `MainActivity` falls back to `FakeKernelGateway` on
x86_64 and a green flow there proves nothing about a kernel.

## What was measured before this phase was written

Read out of the tree, the committed evidence and the clone at `3f3380c668d`. Nothing below is
inferred from behaviour that was not looked at.

| Fact | Citation |
|---|---|
| The rail, the side panel and the document are **one flat field** | `evidence/p2u-1-chrome/after-tablet/dark/01-the-explorer.png`, scanline y=900: x 0-119 (rail) and x 122-699 (explorer) are both `#121314`; only x=120 differs, at `#2A2B2C`. Light reads `#FFFFFF` throughout, and the one-pane phone frame reads `#121314` across the whole explorer |
| VS Code 2026 draws them on **two** surfaces and a line, not one | `resolve_vscode_theme.py`: `activityBar.background` = `sideBar.background` = `panel.background` = `statusBar.background` = `editorGroupHeader.tabsBackground` = `tab.inactiveBackground` = **`#191A1B`** / **`#FAFAFD`**; `editor.background` = `tab.activeBackground` = **`#121314`** / **`#FFFFFF`**; and each of those parts' own border id = **`#2A2B2C`** / **`#F0F1F2`** |
| Why the app cannot say that today | `theme/Theme.kt:96,98` maps **both** `background` and `surface` onto `ColorRamp.DarkGray1`, which *is* `editor.background`. Material has no activity-bar or side-bar role, so `NavigationRail` and all four `Scaffold`s (`ExplorerScreen.kt:158`, `EditorScreen.kt:267`, `FileEditorScreen.kt:95`, `TerminalPaneScreen.kt:110`) fall through to a default that is the document's colour. **VS Code's part distinction has nowhere to live** — this is a missing token, not a wrong value |
| VS Code's parts have **no border-radius**, and this contradicts the ask | `grep -rn border-radius` over `parts/sidebar/`, `parts/activitybar/`, `parts/editor/media/`, `browser/media/`: every hit is a badge (`activityaction.css:168,179,191,203`), a floating panel, a drop target or a modal. There **is** a registered scale — `platform/theme/common/sizes/baseSizes.ts:91-113`, xSmall 2 / small 4 / medium 6 / large 8 / xLarge 12 px, and `strokeThickness` 1px |
| `ColorRamp.LightGray4` drops an alpha the reference states | `list.inactiveSelectionBackground` resolves **`#DADADA99`** in 2026 Light and the ramp holds `Color(0xFFDADADA)` (`ColorRamp.kt:96`). Pre-existing — it resolves the same at the sha P2u-1 read, so the pull did not cause it — and unrecorded: no `docs/DECISIONS.md` entry flattens it, while `:1313` asserts an alpha elsewhere for the opposite reason. Composited it is ≈`#E7E7E8`, so the app draws an opaque row where the reference draws a 60% wash |
| **…and the role that held it had four consumers, only one of them a list.** Read while sizing P7a-1b, and it is what the sub-step turned out to be about | `secondaryContainer` is drawn by `ExplorerScreen.kt:394` (the open row — genuinely this id), `SearchOptionToggles.kt:96` (which cites `inputOption.activeBackground`, **`#D6D6D6`/`#313233`, opaque**), and `FindTopBar.kt:107,187` + `SelectionTopBar.kt:74` (Material's contextual bar, no VS Code id at all). `docs/DECISIONS.md`'s P2x-3d folded the toggle onto the role on the strength of a **4/255** delta; honouring the alpha would have made that delta **17/255** and translucent, so the fold had to be undone rather than restated |
| Breadcrumbs are already shared; only one surface is missing one | `ui/Breadcrumb.kt` + `ui/PathCrumbs.kt` (`crumbsFor`, `ancestorCrumbsFor`, `ROOT_LABEL`) serve the explorer and the notebook since P2w-1. `FileEditorScreen.kt:95-106` draws a one-row `TopAppBar` and already holds `path` |
| Recents has no collapse, only a destructive Clear | `ui/explorer/RecentsSection.kt:74-93` — the heading's one control is `Icons.Filled.Clear` → `clearRecents`. VS Code collapses instead: `parts/views/viewPane.ts:437-458` (`setExpanded`, the twistie container, `Codicon.chevronRight` at `:86`), persisted through `setCollapsed(id, collapsed)` (`common/views.ts:380`) |
| Three screens carry a back arrow and none a close | `EditorTopBar.kt:217-224`, `FileEditorScreen.kt:101-102`, `TerminalPaneScreen.kt:203-204`. The app has exactly **one** width rule, `isWindowWideEnoughForTwoPanes()` (`PaneDirective.kt:29`); P2y-c already records what inventing a second one costs |
| `pip` **is** in the rootfs, and so are `python`, CA certs and a `resolv.conf` | `tar -tzf`: `usr/lib/python3.12/site-packages/pip/`, `usr/bin/python3.12`, `usr/bin/pip`, `etc/resolv.conf`, `etc/ssl/certs/ca-certificates.crt`. `INTERNET` is granted (`AndroidManifest.xml:11`). **Nothing here is missing** |
| `%pip` shells out to `sys.executable -m pip` | `IPython/core/magics/packaging.py:99,105`, read out of the tarball. `sys.executable` is the `nativeLibraryDir` interpreter, which *is* exec-able, and `shell.system` already works (`evidence/p4-terminal/shell-escapes.md`) — so the **invocation** is not the blocker |
| Why the **terminal** has no `python`/`pip`, and it is two causes | (1) `usr/bin/python3.12` is an ELF under `filesDir`, which `execve` refuses at targetSdk 36 — `docs/DECISIONS.md` "Exec model", the reason `BusyBoxApplets` exists. (2) `usr/bin/pip`'s shebang is `#!/usr/bin/python3`, an **absolute** path that does not exist, since the rootfs is not mounted at `/`. `SpaceRuntime.environment():42` does put all three bin dirs on `PATH`, so both names resolve and *then* fail to run |
| Nothing has ever configured DNS or TLS trust | `grep -rn "SSL_CERT\|resolv\|REQUESTS_CA\|nameserver"` over `app/src/main/java/`, `tools/rootfs/`, `docs/DECISIONS.md`, `docs/environment-fidelity.md`: **zero hits**. musl reads `/etc/resolv.conf` and OpenSSL reads `/etc/ssl/…` at **absolute** paths; the Space's copies are under `<baseDir>/`. **This is a hypothesis, not a measurement** — P7e-0 closes it, and no line is written against it before then |

## What this phase declines, so nobody re-derives it

- **No radius on the parts.** The ask was for "discrete borders with border-radius"; the reference
  has the borders and not the radius, and fidelity wins — the same rule P2u-2a applied when parity
  outranked the contrast floor. The scale is still transcribed and used where VS Code uses it.
- **No breadcrumb on the terminal.** A terminal is not a file, and breadcrumbs are an editor
  feature there (`parts/editor/breadcrumbsControl.ts`). "Every file" is the notebook, which has
  had one since P2w-1, and the plain file editor, which P7b gives one.
- **`EditorPalette` and the syntax colours are not reopened.** P2u-2/P2u-3 closed those. This
  phase is the *chrome* the re-base never reached.

## The sub-steps

| # | Sub-step | What settles it |
|---|---|---|
| **P7a-0** | **SHIPPED.** The clone pulled 322 commits to `3f3380c668d`; every citation naming the old sha re-pointed **after** being verified at the new one. No transcribed value moved — all 29 changed theme-JSON lines are `modernActivityBar*` renames — and two `media/notebook.css` line numbers moved by six. | The re-resolve of every `ColorRamp` id in both themes, and a `git diff` over the theme JSONs that is empty once `modernActivityBar` is excluded. |
| **P7a-1** | **SHIPPED.** `theme/ChromePalette.kt` - a palette keyed by VS Code **part id**, the shape `EditorPalette` has for the document and `TerminalPalette` for the ANSI ramp. **Two fields, not six**: `activityBarBackground` and `sideBarBackground` are what this step has consumers for, and the border ids the plan first listed here moved to P7a-2 with the seams that draw them, because `check_palette_consumers.py` fails a field nothing draws and that is the gate working rather than an obstacle. Consumers in the same commit: the rail, the bottom activity bar, and all three side panels' `Scaffold`s **and** their `TopAppBar`s - a panel that coloured only its scaffold still drew a title strip of the document's colour across its own top. `sideBarTopAppBarColors()` lives in `SidePanelPresentation.kt`, which already declares itself the one home for what a side panel is drawn as. The gate is now palette-generic and holds both palettes, with a test that fails if a third is added unregistered. | `ChromePaletteTest` pins both instances by equality and asserts neither part *is* the page. **The device half is a measurement, because a Maestro assertion cannot read a colour** - which is exactly how this survived P2u-1 green: `read_chrome_parts.py` reports a frame's vertical colour bands (the **mode** down each column, so a row label cannot split one) and `check_chrome_parts.py` holds them to the resolved values, checking **position not presence** - `cellBackground` is `#191A1B` too, so "the colour is somewhere in this frame" would pass over a broken rail. Non-vacuous both ways: green on `evidence/p7a-chrome-parts/`, red on the committed pre-fix frame. Tablet reads `[#191A1B, #191A1B, #121314]`, emulator one field where the panel is the window; both themes on each. |
| **P7a-1b** | **SHIPPED, and not as written — the alpha went where the *id* is.** The row above says the plan: drop `#DADADA99` onto `LightGray4`. Reading the consumers first is what stopped it: that position is `secondaryContainer`, and **three of its four consumers are not lists**, one of them citing an id the reference draws *opaque*. Putting the alpha on the position would have made the toggle's wash translucent and 17/255 from `inputOption.activeBackground`, and made P2x-3d's own table false. So the two ids were parted instead. **Two commits, each functional on its own, in this order because the reverse would briefly draw the row in the toggle's colour**: (1) `ChromePalette` gains `listInactiveSelectionBackground` — `0x99DADADA` light, `0xFF2C2D2E` dark — and the explorer's open row draws it, a side-bar list row being a *part*, which is what P7a-1 built that palette for; (2) `ColorRamp.LightGray4`/`DarkGray6` re-source to `inputOption.activeBackground` (`#D6D6D6`/`#313233`), which the toggles and the two contextual bars always cited. **No renumbering** — both values sit in the slot they replaced. `tertiaryContainer` moved with `DarkGray6` and has no consumer outside `Theme.kt`. P2x-3d is **rewritten rather than amended**: the fold is retired, with the delta that retired it. | `ChromePaletteTest.theLightRowIsAWashAndNotAFill` measures the **composited** value (`#E7E7E8` over the side bar), the way P2u-3c had to for dark's 70%-opaque focus border, and `ColorSchemeTest.theSecondaryContainerIsTheReferencesOwnToggleWash` pins the role by equality — membership on the ramp could never say *which* position, which is how this survived. Contrast re-measured, not assumed: `#BBBEBF` on `#313233` is **6.87:1**, `#202020` on `#D6D6D6` **11.21:1**. **The device half is a measurement, because a Maestro assertion cannot read a colour** — `read_node_fill.py` reports the mode colour inside a *named* node's own bounds (`check_node_fill.py` gates it), which is new: `read_chrome_parts.py` reads columns and nothing read a colour at a located region. **The row is selected by `checked`, not `selected`, and that was probed rather than assumed** — Compose maps a `selected` node carrying no `Role` onto `isCheckable`/`isChecked`. Red then green on real devices: the row `#DADADA → #E7E7E8` on the tablet (`evidence/p7a-1b-list-selection/`, two panes only — `Navigation.kt:152` ends `openDocumentPath` with `.takeIf { twoPane }`), the toggle `#DADADA → #D6D6D6` and `#2C2D2E → #313233` on **both** devices (`evidence/p7a-1b-toggle-wash/{one-pane,two-pane}/`). Both pre-fix frames are committed under `before-the-fix/`, because an oracle that cannot see the bug cannot be trusted to see the fix. `p2i-3-find` and `p2c-selection` re-run green for the two bars whose tint moved. |
| **P7a-2** | **SHIPPED, in four commits, and `panelBorder` is not what it was called.** The row said one field; the reference states **two** - `activityBar.border` *"separating to the side bar"* and `sideBar.border` *"on the side separating to the editor"* (`workbench/common/theme.ts:417,623`), both `#2A2B2C` / `#F0F1F2` in 2026 and both `dark: null` in the registry, so only the theme file makes them real. Folding them would repeat exactly the fold P7a-1b had just undone, so `ChromePalette` gained both. **The rail's seam was already the right hex for the wrong reason**: `Navigation.kt:222`'s bare `VerticalDivider()` took `DividerDefaults.color` -> `outlineVariant` -> `panel.border`. **The panel↔document seam was drawn by nothing at all** - measured at x661/x662, adjacent bands, both themes. Drawn by the *part* now, not between parts: `Modifier.trailingBorder` in `ui/shell/PartBorder.kt`, the shape `Modifier.cellEditorBox` already has, `drawWithContent` because every part paints its own background, and direction-resolved as `sidebarPart.ts:176` resolves it. `strokeThickness` (1dp, `baseSizes.ts:115-118`) is a documented private val there rather than the file P7a-3 names - **one consumer, so one home**, which is P7a-1's own "two fields, not six" rule. `ShellSidePane` took `twoPane` and dropped `barSelection`: the width fact arrived twice and this step needed it a third time. The three document `Scaffold`s state `editorBackground` instead of inheriting `colorScheme.background`, which retired the palette gate's last exemption - and **the gate had to be fixed first**, because it searched raw file text and a KDoc in `terminal/TerminalPalette.kt` already "consumed" the field. Comments are stripped by a scanner now (`strip_jsonc`'s reason: `"https://…"` is a string), measured to change exactly one field's verdict. | `read_chrome_parts.part_seams` + `check_chrome_parts.py`'s seam check - **the oracle threw the border away on purpose**, so nothing here could report one that was never drawn. Non-vacuous both ways in one suite: green on the re-captured frames, red on `evidence/p7a-2-part-borders/before-the-fix/`. `PartBorderTest` reads the edge's own pixels including right-to-left; `ShellSidePaneTest` holds the half that could be right in the modifier and wrong in the pane, `twoPane` driven as a parameter. Both widths, both themes: two seams at two panes, none at one. **And the frames were looked at, which is the only reason a defect in them was found** - see the `fix(explorer)` row below. |
| **P7a-2, the defect the frames showed** | **`ExplorerViewModel.goTo` drew its own cancellation at the user.** *"StandaloneCoroutine was cancelled"* in `colorScheme.error` across the file list, in a frame whose flow reported every step COMPLETED. `runCatching` catches `CancellationException` like anything else, so the `listing?.cancel()` that makes the newer navigation win became a failure to read; `DiskWorkspaceRepository.list:39` is a `withContext(ioDispatcher)`, which raises it when the block *finishes*, so `copy(error = …)` lands **on top of** the clean state the newer listing already wrote. Predates P7 by two weeks (`6aa52ba1`). Fixed in its own commit, not folded into P7a-2. | `aCancelledListingIsNotReportedAsAFailure` through `CancellingWorkspaceRepository`, a named fake - **the race itself cannot be tested**: `runTest` resumes a cancelled `delay` at once, so the error write lands *before* the newer listing's and is overwritten, green with the bug in place. `aListingThatReallyFailedIsStillReported` stops the guard eating a real failure. `evidence/p7a-2-part-borders/the-banner-a-flow-passed-over/` keeps the frame. |
| **P7a-3** | **SHIPPED, and the gate had to be fixed before the file could exist.** `theme/CornerRadius.kt` transcribes `baseSizes.ts:90-108` - xSmall 2 / small 4 / medium 6 / large 8 / xLarge 12 - and `SearchOptionToggles`' `TOGGLE_CORNER = 8.dp`, the only `RoundedCornerShape` in `app/src/main/java`, is retired onto `CornerRadius.large`. **A second scale rather than more steps on `Spacing`**: two of these are *off* Material's 4dp grid, and folding them in would make `SpacingTest`'s own `% 4` assertion meaningless. **`cornerRadius.circle` (9999px, `:110-112`) is declined** - CSS's way of spelling a shape Compose names outright, so transcribing it would add a val nothing could legitimately draw, which is `check_palette_consumers.py`'s own rule and P7a-1's "two fields, not six". **`strokeThickness` did not move**, as this row said it would not: one consumer, one home, still `ui/shell/PartBorder.kt`. **And `check_ui_dimensions.py` refused the new file over its own KDoc** - the sentence naming the literal it retired contains `8.dp`, and the gate matched raw file text. Exactly `check_palette_consumers.py`'s P7a-2 defect from the other side, so the scanner has one home now (`scripts/kotlin_source.py`, imported by both) and it blanks comments **to spaces** rather than deleting them, because this gate reports line numbers and reads the line above a declaration. | `CornerRadiusTest` pins the scale **by equality**, not by a property - a transcription has none to check, which is how `ColorRamp.LightGray4` held the wrong value for months (P7a-1b) - and `theCircleTokenIsNotOnTheScale` asserts the departure rather than leaving it to a comment. Shown red before green: `large = 9.dp` fails `theScaleIsTheReferencesOwnValues`. The gate's fix is measured, not asserted: **29 literals reading raw text, 26 reading code**, the three that vanish being this step's own prose, so **no pre-existing offence was pardoned**. **The device half is a re-run, and says so** - the step changes no pixel, so what it owes is P7a-1b's own colour oracle still green on the build that carries it: `#313233`/`#D6D6D6` at the *on* toggle's own bounds, both themes, at 320dp and 1280dp (`evidence/p7a-3-corner-radius/`). `p7a-1b-toggle-wash-driver.sh` gained an `EVIDENCE_ROOT` override for it, because re-running a shipped step's driver must not overwrite that step's committed frames. The tablet is not required: no cell is run. |
| **P7b** | **SHIPPED.** `ancestorCrumbsFor(path, ROOT_LABEL)` into `FileEditorScreen`'s bar, drawn by the existing `Breadcrumb` - reuse, and P2w-1's three departures inherited rather than re-decided. **Under the bar rather than between two**, because this screen's single row *is* its name row where the notebook has a strip above its actions; `EditorTopBar`'s collapsing arithmetic is not copied, as this row said. The inset defect that entry records cannot arise here - its `TopAppBar` was the *second* row and padded itself down by the status bar, and this one is still the first - and that is read off a device frame, since no assertion in this suite sees a system inset. **And `Navigation.kt` now holds one `goToFolder` for both documents**: the branch it carries (the side area may be showing the outline; in one window the browser is not composed until the open entry goes) was about to exist twice, which is the defect `ShellSidePane` stopped being handed the width fact twice over in P7a-2. | `FileEditorBreadcrumbTest` - five instrumented, red before green (`No parameter with name 'onGoToFolder'`): absent at the root, both ancestors on screen, the row *under* the name and the back arrow by measured bounds, and a tap reporting the **path** rather than the label, at the root and two levels down. Device: `evidence/p7b-file-editor-breadcrumb/` at **320dp**, where the file's trail is the only one on screen so a root file's row can be asserted absent, and `evidence/p7b-file-editor-breadcrumb-wide/` at **1280dp**, where the crumb has to bring the browser back before it can move it - the half `p2w-1-breadcrumb-wide` exists for, whose browser-only marker here is `Nested.ipynb`, since `data.csv` is also the open document's name. Frames looked at. **Both halves of P2w-1 re-run green on the refactor** - the notebook's flow did not change but its path through that lambda did - with their logs under `the-notebooks-own-trail-still-works/` and their own tablet-captured frames left as committed. |
| **P7c** | **SHIPPED, and the second thing it shipped was a state holder.** The row as planned: a twistie on the heading (`Codicon.chevronDown`/`chevronRight`, `viewPane.ts:85-86`), the whole row its target - the reference binds `setExpanded(!isExpanded())` to a **click *or a tap*** anywhere in the header (`paneview.ts:285-292`), which is the half that matters here - `role = Button` plus a `stateDescription` where it states `role="button"` and aria-expanded (`:261-263`), and Clear moved into a `…` under its own label, *Clear Recent Documents* (`docmanager-extension/src/recents.ts:73`). **The `…` is gone while the section is shut**, which was read off the CSS rather than chosen: every rule that reveals a pane header's actions requires `.expanded` (`paneview.css:79-82`, `paneviewlet.css:26-27`) - and on a touch screen it matters more than under a mouse, since a shut heading is then one target and not two. `itemsAboveTheListing` grew the `collapsed` parameter the row asked for **and its first test ever**. `ui/Twistie.kt` is one home for the chevron and its right-to-left rule, with `SearchResults`' own fold header as its second caller. **What was not planned: `ExplorerViewModel` hit detekt's eleven-function limit, and the honest fix was not a bigger limit.** `RecentsViewModel` + `RecentsUiState` are the split - which is what `RecentsSection.kt`'s own KDoc had said since X2h (recents belong to `docmanager` and are surfaced from `mainmenu-extension`, **never** from `filebrowser`) and what the browser's state holder had quietly merged. `shownWhileBrowsing` now applies at the screen, the one place that knows both the history and the directory. **The persistence** is the port the row named - `ExplorerSettings` + `ExplorerSettingsRepository` + `Fake` + `Disk` + `explorer-settings.json` in `filesDir` - and `reset_notebooks` clears it, because a collapsed section left behind by one flow is **invisible** to the next one and would just look like a browser with no recents. `docs/architecture.md`'s port table gained this port **and `TerminalSettingsRepository`, which P4j-3c never added** - found while reading, fixed rather than left, which is P7f's own rule applied early. | Four layers, each red before green. `RecentsSectionTest` pins the count in all four cases. `RecentsViewModelTest` holds the collapse, the write-through and the reference's own default, and **both** stores run the port's contract - the fake included, because `AppContainer` defaults to it and every instrumented case reads the collapse through it rather than through disk. `ExplorerScreenTest` gains six instrumented, one of them asserting that the heading offers the *action* matching its state and not only the label - TalkBack's expand/collapse, which is what aria-expanded amounts to for a screen reader. They were **shown red by breaking the app rather than by memory**: with `collapsed` forced false, four of the five collapse cases fail and the fifth (Clear behind the `…`) legitimately does not, since it does not depend on the collapse. Device: `evidence/p7c-recents-collapse/320dp/` and `evidence/p7c-recents-collapse-wide/` (1280dp, where the section shuts beside an open document and leaves it alone), plus **`evidence/p7c-recents-persists/320dp/`**, the row's own gate: collapse, `stopApp`, relaunch, still shut - **and non-vacuous, because it was run first against the in-memory fake and failed at exactly that assertion**. Frames looked at; the overflow menu sits flush to the right edge at 320dp, which was checked against `p2r-cell-type-in-the-overflow`'s committed frame and is Material's own clamping, app-wide, not something this step introduced. `explorer-rename-open-document` and `explorer-delete-open-document` tap Clear and were updated to the two-tap route; both re-run green, under an `EVIDENCE_ROOT` override so that **X2f and X2g's tablet-captured frames were not overwritten by an emulator's** - the trap `record_device` exists for, hit once here and undone. |
| **P7d** | **SHIPPED, and this row's own claim was one of the two things the frames corrected.** As planned: one `DocumentDismissAction` in `ui/` beside `Breadcrumb`, consumed by all three bars - the notebook's, the plain editor's and the terminal's - with the arrow below two panes and `Icons.Filled.Close` at and above, and the same `onBack` behind both. The label is `application:close`'s own verb with its noun dropped (*Close Tab*, `application-extension/src/index.tsx:309-310`): this app has no tab strip, so *Tab* would name something the user cannot see, the same trade `RecentsSection` makes against *Recently Closed*. `twoPane` is a parameter and never a measurement, which is what lets both arms be driven at all - and in `EditorScreen` it is a **second** reading of the width, since `onShowTableOfContents` is null for the same underlying reason. That is named in the KDoc rather than left to be found: the two answer different questions of different widgets, where P7a-2's rule is about two parameters describing one widget's state. **The correction: this row said closing pops the detail entry *"so the explorer takes the width"*. It does not** - the browser keeps its column and the document area goes empty, which is the reference's structure rather than a shortfall (VS Code leaves an empty editor group where its watermark lives; JupyterLab an empty dock), and what fills it is **P6d's Launcher**, already planned. The flow asserted the true things and *narrated* the false one, which is the shape a green log hides. | Nine instrumented, red before green: three on the component and two on each bar, because a component that is right and wired to nothing passes its own tests. `evidence/p7d-close-at-two-panes/1280dp/` closes a notebook, a plain file **and** a terminal; `320dp/` shows both arrows staying, because a width rule proved on one width is proved by half. Both on emulators - no cell is run. One flake fixed rather than re-run: `notVisible: "Terminal"` gave way on a frame whose hierarchy held no such string, so the oracle for an empty document area is now the document bar's own Close going, which only a document draws. |
| **P7d, the defect the frames showed** | **One glyph meant two things, and the reference draws neither of them as a cross.** `Icons.Filled.Clear` and `Icons.Filled.Close` are the same ×, so the notebook at two panes carried one on its bar meaning *close this document* and another on its bottom bar meaning *interrupt the kernel*. The parity gap is older than P7d - `createInterruptButton` takes `stopIcon` (`apputils/src/toolbar/widget.tsx:50`) and that icon is `M6 6h12v12H6z` (`ui-components/style/icons/toolbar/stop.svg`), a filled **square** - but P7d is what made it ambiguous, so P7d is what fixes it. Interrupt is `Icons.Filled.Stop` now, and the bottom bar finally reads as the reference's toolbar does: run, stop, restart, restart-and-run-all. | The glyph is **evidence, not an assertion** - no instrumented test can read a vector, which is what `ui/Twistie.kt` says for the chevron. `NotebookBarTest` pins the description and the enablement and never saw this; `evidence/p7d-close-at-two-panes/` at both widths carries the frames. Older steps' committed frames still show the cross, deliberately: evidence records what the app looked like when that step shipped, and P7a-3 set the rule that a later step does not overwrite it. |
| **P7e-0** | **SHIPPED, and every row below it is now written from an answer rather than a reading of the tarball.** Nine questions on the tablet, six to the kernel and three to the shell; `evidence/p7e-0-runtime-probe/README.md` carries them with what each forecloses, `probe-output.txt` the raw strings, `probe.ipynb` the notebook the kernel itself wrote. **The exec failure is EACCES, not ENOEXEC** - `python3 --version` and `pip --version` both give *Permission denied* with **rc=126**, which is the W^X refusal on an ELF under `filesDir` and confirms P7e-1's design unchanged. All three names *resolve* (`command -v`), so `SpaceRuntime.environment():42`'s `PATH` was never the problem. **`%pip --version` works in a cell** (`pip 24.3.1`), so the invocation never was the blocker either. **DNS is dead and TLS is untested**: `getaddrinfo` raises `gaierror [Errno -3] Try again`, the one real HTTPS GET fails at *DNS* rather than at trust, and `cert_store_stats()` is `{'x509': 0}`. | Two things the probe settles that change what P7e-3 can be. **TLS trust is an environment fix**: the image ships a real 217 KB `ca-certificates.crt`, `usr/lib/libcrypto.so.3` contains `SSL_CERT_FILE`/`SSL_CERT_DIR`, and cell 4 independently reports `openssl_cafile_env='SSL_CERT_FILE'` - one entry in `SpaceRuntime.environment()`. **DNS is not**: `ld-musl-aarch64.so.1` holds exactly `/etc/resolv.conf` and `/etc/hosts` as absolute paths with **no env override**, nothing without root can write there, and the shipped `etc/resolv.conf` is **0 bytes** - so `[Errno -3]` is musl falling back to `127.0.0.1` and finding nothing. **P7e-3 is a design step, not a one-line env step.** |
| **P7e-1** | **SHIPPED, and the row said the wrong directory.** The plan: a `bin/python3` link into `nativeLibraryDir`, *"in `bin/` so the existing `PATH` finds it"*. It would not have. `SpaceRuntime:42` searches `usr/local/bin:usr/bin:bin`, so `usr/bin/python3` - which the image already ships - wins, and P7e-0 had **already measured** `command -v python3` returning exactly that. The link went where `bin/busybox` goes instead: onto the one real ELF, `usr/bin/python3.12`, leaving the two *relative* links the image ships (`python3 -> python3.12`, `python -> python3`) to reach it unchanged. All three names work and only one link can go stale, which is `BusyBoxApplets`' own "one maintained link rather than 305". **No new class**: `linkInterpreterToNativeLibrary` sits beside `linkLoaderToNativeLibrary` in the installer, and the third occurrence of `mkdirs`/`delete`/`createSymbolicLink` is what earned the private `linkToNativeLibrary` both now call. **And rc=126 was not only the W^X refusal**, which the row and P7e-0's README both said it was: `TarGzExtractor` writes **no executable bit at all**, so mode 0600 explains EACCES on its own. A symlink settles both at once, so nothing in the design moved - but the two had to be separated for P7e-2, and they now are. | Four unit cases red before green, one of them - `install_makesEveryShippedPythonNameResolveThroughTheLink` - being the test the `bin/python3` design fails. `PythonInterpreterDeviceTest`: five instrumented, **shown red on the tablet by removing the installer's one new line** (three cases at exit 126, `Permission denied`), then green. `runInSpace` is P4b-2's rule with one home now, so the new class could not grow a copy that forgot why an applet must never be run through `run-as`. `evidence/p7e-1-space-python/` on the tablet: `Python 3.12.13`, `sys.prefix` on the rootfs, `import json` at rc=0, **`python3 -m pip --version` answering `pip 24.3.1` without P7e-2**, and `the-link.txt` read off the device, which is the claim no screenshot of a terminal can show. Frames looked at - which is the only reason the next two rows exist. |
| **P7e-2** | **NEXT, and P7e-1 measured away the design this row carried.** The row said: rewrite `usr/bin/pip`'s `#!/usr/bin/python3` at install time and be done. That fixes the *second* failure, not the first. `evidence/p7e-1-space-python/04-what-the-console-script-still-does.png` is `pip --version` **after** P7e-1: still *Permission denied*, **rc=126** - the exec of the script file, not its interpreter line. And the two causes of that refusal are now separated: `aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet` writes a script into this app's own `filesDir`, `chmod 0755`s it, and gets **126 anyway**. **So SELinux binds and a permission fix reaches nothing** - and unlike the interpreter there is no ELF to point a symlink at, because a console script *is* the file being exec'd. This step therefore owes a design before it owes a line of code, and the shebang rewrite is at best half of it. What is already true and needs no design: `python3 -m pip` works from the shell today, which is exactly what `%pip` does in a cell (`IPython/core/magics/packaging.py:99,105`). | `evidence/p7e-1-space-python/` is the measurement it starts from. Its own gate stays a terminal answering `pip --version`. |
| **P7e-3…** | **Network and trust.** Written after P7e-0. The candidates are named in the table above; whether the answer is env (`SSL_CERT_FILE`, `REQUESTS_CA_BUNDLE`) or a written `resolv.conf` is a measurement's to give, not this row's. | `evidence/p7e-pip-install/`: `%pip install` a real package and import it, **on the tablet** — the emulator falls back to `FakeKernelGateway`, so a green flow there would prove nothing. |
| **P7f** | **Three stale claims in `docs/architecture.md`**, found while reading and fixed rather than left: §4 says *"Three destinations"* where `Navigation.kt:323` has a fourth (`Terminal`); the `ui/{editor,explorer,fileeditor}` row omits `ui/terminal`; and the `theme` row still reads *"the IntelliJ-derived palette"*, which P2u-1 retired. | A re-read, which is the only thing that catches a claim going stale. |

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
- **`TerminalPaneScreenTest.theTerminalShrinksByRoughlyOneKeyboardsWorthOfRowsNotTwo` is red on
  the emulator and green on the tablet.** Found by P7a-1's own gate run and **checked rather than
  blamed on it**: it fails identically on the commit before, so it predates P7 and belongs to
  P4j-3b's territory. The assertion wants the terminal to shrink by roughly one keyboard's worth of
  rows for a 600 px IME inset and measures **13 rows at 18 px per row against an expected ~33** on
  `dev36`. The device disagreement is the interesting half - the same code satisfies it at the
  tablet's density and window - so the suspect is the test's own px-per-row arithmetic on a
  320dp/160dpi emulator rather than the inset handling P4j-3b shipped. Not scheduled inside P7,
  which is not a terminal phase; recorded so the next full `connectedDebugAndroidTest` does not
  re-derive it. **`TerminalExtraKeysRowTest` is load-flaky under a full suite and is not this** -
  its 5 s `waitUntil` is what gives way, not anything the app does. **Named as the class rather than
  as one test, because the row as first written named one method and a different one went red next
  time**: P7a-1's run lost `arrowAndNavigationKeysReachTheShellAsTheirOwnSequences`, and P7b's lost
  `theCtrlKeyIsDescribedAsActiveWhileArmedAndPlainOnceConsumed` - each passing on a targeted re-run
  immediately after (P7b's: 8 tests, 1 failure, that one being `TerminalPaneScreenTest` above).
  A note that names a method cannot report the *next* method, which is how a recorded risk quietly
  reads as an unrecorded one.
- **`runCatching` inside a `launch` reports a cancellation as a failure, in eleven more places.**
  P7a-2 found and fixed the one that was *visible* - `ExplorerViewModel.goTo`, the only listing with
  a `cancel()` aimed at it, which drew *"StandaloneCoroutine was cancelled"* at the user
  (`evidence/p7a-2-part-borders/the-banner-a-flow-passed-over/`). The shape is everywhere: **twelve
  `runCatching`s inside a `viewModelScope.launch` across six `ui/` files** - `EditorViewModel`,
  `FileEditorViewModel`, `NotebookExport`, `RenderedOutput`, `WorkspaceReplace` and the other two in
  `ExplorerViewModel` - each of which would write scope death into a UI state as an error. Bounded
  rather than urgent: only `goTo` cancels a job on purpose, so the rest can only fire as a screen is
  going away, where nothing is left to read the state. **Not scheduled, and deliberately not swept**
  - a blanket rewrite would touch six ViewModels for a defect that has been observed in one, and the
  honest fix is one home for "run this and report why it failed" rather than twelve guards.
- **A character went missing between Maestro and the shell** - P7e-0 saw it once, suspected the
  harness and could not reproduce it. **P7e-1 reproduced it, and the suspicion was wrong: it is the
  app.** `adb shell input text`, which never touches Maestro, drops the same characters, and the
  same screen shows a line arriving *twice*. It is no longer an open risk but a planned step,
  **P4m**, with its measurements in
  `evidence/p7e-1-space-python/the-defect-the-frames-showed/`. The markers stay in the flows: they
  are what turned one unreproducible sighting into a reproduction.
- **Android Lint crashes on a stale analysis cache, and it is not a finding.** P7d's gate run died
  with *"Unexpected failure during lint analysis of EditorTopBar.kt (this is a bug in lint or one of
  the libraries it depends on)"* - `@NotNull method AsyncExecutionService.getService must not return
  null`, thrown from `KotlinUImportStatement` while reading that file's import list, which the step
  had just changed. **`lintDebug` alone passes on the very next run with no edit in between**, so it
  is AGP's incremental lint and not the code. Recorded so the next gate failure of this shape is
  re-run rather than debugged, and so nobody "fixes" a source file over it.
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
