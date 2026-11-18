# JD-092 — The instrumented suite runs clean on the emulator

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-182`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

`am instrument` over `com.jupydroid.app.test` on `dev36` reports `OK` with zero failures. Each failure
`GAP-182` lists becomes one of four things: a repaired defect, a test given the window it needs
through `DeviceConfigurationOverride.ForcedSize`, a test marked for the window it needs with
`Assume`, or a focus flake whose cause is named and answered. The arm64 Space probes stay
`assumeTrue` skips — a named skip is part of a clean suite; a silent failure is not.

## Demo

Nothing here runs a cell, so the device rule 2 names for it is the emulator (`dev36`, x86_64,
320×640dp at 160dpi): `FakeKernelGateway` is what these tests already drive.

1. Boot `dev36` headless with `-gpu swiftshader_indirect` — a plain start aborted inside
   `EditorTopBarTest` on 2026-10-27 (`lvp_GetMemoryFdKHR: Assertion '*pFD >= 0' failed`), and 264
   `com.jupydroid.app.ui.shell` tests ran to the end with the flag.
2. `scripts/instrumented-suite-driver.sh` builds both APKs, installs them with `adb -s`, and runs
   `am instrument -w` — never `connectedDebugAndroidTest`, which uninstalls the app it just proved.
3. The verdict is read from the instrument log: `OK` with zero failures, or the run is not done.

Not `shipped` until step 3 reports OK on `dev36` for the whole suite.

## Who else implements this

The question is two: how a UI test gets the window it needs on a small device, and how a suite
survives a window without focus.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | FULL | `galata/src/playwright-config.ts:20` | the whole suite pins `viewport: { width: 1024, height: 768 }` — the window a test needs is part of its environment |
| `vscode@3f3380c668d0` | FULL | `test/automation/src/playwrightBrowser.ts:138` | `page.setViewportSize(1440×900)` before driving — again the environment states the window |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | — | drives cells through `commands.executeCommand`; no window assertion |
| `intellij-community@406a27a8da07` | ABSENT | `platform/remote-driver/test-sdk/.../UiRobot.kt` | UI tests run the IDE at its own window; no small-window forcing found |
| `termux-app@3df69d1da197` | ABSENT | `termux-shared/src/androidTest/.../ExampleInstrumentedTest.java` | a single generated instrumented test; no suite to size |
| `winlator@fb66541b93a4` | ABSENT | — | no instrumented suite |
| the shipped rootfs | ABSENT | — | an image, no UI tests |

**Count:** 2 of 7 state the window their suite wants. For an Android instrumented suite the
mechanism is ours already — `DeviceConfigurationOverride.ForcedSize`, and the adaptive library still
serves it: `currentWindowAdaptiveInfo` reads `containerSize` through `LocalDensity.current` so a
forced size reaches the app's own window-class reads (b/358626778). For the focus flake none of them
answers: the failure is Android's (`RootViewWithoutFocusException` on `has-window-focus=false`), and
ours to cause.

## Implementation inventory

`GAP-182` names fourteen classes on `dev36`; the tab-strip repair's whole-suite run (parity ledger)
still failed ~21 tests, none new, so stragglers beyond the list are expected. `am instrument` runs
every `androidTest` class, including the arm64 Space probes that `assumeTrue`-skip on the emulator.

| Concern | Files |
|---|---|
| two-pane tests with no forced size (8 classes) | `DeletedTabTest`, `LauncherAutoCreateTest`, `MultiDocumentNavigationTest`, `OpenDocumentMarkingTest`, `RecreatedPathTabTest`, `RenamedTabBeneathTest`, `SpacesHomeNavigationTest`, `TabStripRecreationTest` |
| menu nodes absent at HEAD | `app/src/androidTest/java/com/jupydroid/app/ui/shell/TabCloseSiblingsTest.kt` |
| window-focus flakes | `ChatSidePanelTest.kt:90` (`closeSoftKeyboard`), `SidePanelBarTest.kt:181` (`Espresso.pressBack`) |
| per-failure singles | `TerminalPaneScreenTest`, `ConsoleInputRowTest`, `CellOutputImageTest` |
| the precedent to apply | `TwoPaneLayoutTest.kt:196-218,253` and `SidePaneNavigationTest.kt:382,420` — `ForcedSize(windowFor(twoPane))` around `MainNavigation(twoPane = …)` |
| the two-pane decision | `Navigation.kt:198` — `measured` unless `twoPane` and a panel show, else `forcedToOnePane()`; `measured` reads the activity's real window |
| the skip precedent | `ProotRuntimeProbeDeviceTest.kt` — `assumeTrue` in `onlyWhereProotShips` names the device property the test needs |
| the menu helper | `ui/shell/TabInReach.kt` — scrolls an off-screen tab into view before it is pressed |
| the suite invocation | `scripts/per-space-runtime-driver.sh:52` — `am instrument -w -e class <cls> com.jupydroid.app.test/...` via `device_adb` |

## Trade-offs

| Axis | Options | Trade-off | Choice |
|---|---|---|---|
| a test gets the window it needs | `ForcedSize` (ours, precedent) · `assumeTrue(width)` | `ForcedSize` runs anywhere — including `dev36` — but relies on the b/358626778 workaround reaching the app's window reads; `assumeTrue` skips on the emulator and only the tablet ever proves it | `ForcedSize` for the two-pane group — the pattern exists and dev36 executes it; `assumeTrue` only where a real device property (not size) is the requirement |
| the focus flake | a retry naming `has-window-focus` · a mechanism needing no focus (`UiDevice`) · a windowed emulator | a retry masks a race with no cause read; `UiDevice.pressBack` bypasses root focus but changes what the test exercises; windowed may not reproduce | T4 reads the cause first; if `-no-window` grants focus unreliably, a retry that names it is the honest answer |
| `CellOutputImageTest`'s 600dp column | `ForcedSize(600+)` · `requiredWidth` on the one Box | `ForcedSize` scales the whole composition; `requiredWidth` lets the one measurement box outgrow the parent | T5 reads the failure before choosing |

## Gap analysis

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| a two-pane test on a 320dp window | passes `twoPane`, never forces the size — the scene declines | `TwoPaneLayoutTest.kt` (ours) | the pattern exists and is not applied |
| a rerunnable suite verdict | per-class `am instrument`, ad hoc | `per-space-runtime-driver.sh` (ours) | no single command builds, installs and reads the verdict |
| the focus flake | fails with `RootViewWithoutFocusException`; re-run alone passes | — | the cause is unnamed |

## Deviations

- The suite is invoked with `am instrument`, not `connectedDebugAndroidTest`, because the Gradle task
  uninstalls the app it just proved — `per-space-runtime-driver.sh` already works this way.
- No script boots the emulator: every driver assumes `ANDROID_SERIAL` points at a live device, and
  the GPU flag is a launch decision, not a test property — `GAP-182` records which flag works.

## Open decisions

- ~~Whether the repo comments claiming `currentWindowAdaptiveInfo()` ignores
  `DeviceConfigurationOverride` are wrong for adaptive 1.3.0~~ **answered by T2: they were.**
  `ForcedSize(900dp)` at dev36's 320dp made `paneScaffoldDirective()` report two partitions — 33
  tests green across the touched classes — and the eleven sites carrying the claim were corrected.
- Whether more than `GAP-182`'s list fails at HEAD — the later whole-suite run says ~21. T6 owns the
  whole-suite sweep; a straggler becomes its own task here or its own JD.

## Surface

| Layer | Files |
|---|---|
| instrumented tests | the fourteen classes above, plus a retry rule if T4 lands one |
| scripts | `scripts/instrumented-suite-driver.sh` |
| docs | this PRD (until ship), `docs/backlog.md` row, `GAP-182` resolved |
| domain / ports / adapters | none expected — a real defect found by T1 becomes its own task or JD |

## Classification at HEAD (T1, `dev36` at its physical 320×640)

`scripts/instrumented-suite-driver.sh` over the fourteen classes: **95 tests, 22 failures.** The
AVD carried a stale `wm size 360x640` override that masked three of them and invented one; the
driver now resets it before instrumenting, and this table reads the 320×640 run.

| Verdict | Tests |
|---|---|
| **window assumption — the explorer's pane is not drawn** at `twoPane = true` on a 320dp window (`measured` = 1 partition, `ListDetailSceneStrategy` draws one entry, the file row never enters the tree) | `DeletedTabTest` ×2 (`waitForRow` 20 s), `RecreatedPathTabTest`, `RenamedTabBeneathTest`, `TabStripRecreationTest` ×3 (`waitForRow`), `MultiDocumentNavigationTest` ×2 and `OpenDocumentMarkingTest` ×2 (node missing on inject), `LauncherAutoCreateTest.closingTheLastDocumentRefillsTheMainAreaAtTwoPanes` (`waitUntil` 5 s), `SpacesHomeNavigationTest.anImportOpensInsideTheWorkspace` (`waitForText` 5 s) |
| **window assumption — a width the device does not have** | `CellOutputImageTest` (expects a 600dp column, the window is 320), `ConsoleInputRowTest` (the input field is 160dp at 320; green at 360), `TabCloseSiblingsTest` ×2 (`GAP-182`'s item 2: the menu never opened, because the press at a minimum-width tab's centre is the close button's — T3, `GAP-210`) |
| **window focus** — `RootViewWithoutFocusException` at `pressBack`/`closeSoftKeyboard`, `has-window-focus=false` | `ChatSidePanelTest.aQuestionSent…` and `SidePanelBarTest.backClosesTheRailWithoutActing` (GAP-182's two), **plus two new**: `LauncherAutoCreateTest.aLoneLauncherCannotBeClosed…` and `SpacesHomeNavigationTest.backFromTheBrowserReturnsToTheHomeAtOnePane` |
| **measurement** — not obviously window-shaped | `TerminalPaneScreenTest`: the terminal shrank 24 rows for a 600px keyboard where ~33 was claimed |
| green at HEAD | `ConsoleInputRowTest`'s 14 others, `ChatSidePanelTest.aReaderScrolledUpIsLeftWhereTheyAre` (red only under the stale 360px override), `SpacesHomeNavigationTest`'s 5 others |

## The focus failures (T4, `dev36`, `-no-window -gpu swiftshader_indirect`)

Cause: `mCurrentFocus` was `Application Not Responding: com.android.systemui` in **789 of 789** samples,
one every 2 s from before the first test to the last. Espresso's `pressBack` and `closeSoftKeyboard` need
the root to have window focus and failed with `has-window-focus=false`; Compose-only assertions do not,
which is why only those calls failed. Each of the four failed alone too, so the PRD's "re-run alone
passes" was a run on which the dialog was not up. The same run's two "Process crashed" class runs
(`LauncherAutoCreateTest`, `SpacesHomeNavigationTest`) and `ChatSidePanelTest.aDroppedChat…` (a
`waitUntil` timeout) passed once the dialog was gone.

What removes it: a force-stop of SystemUI and of `settings.intelligence` removed it in none of three
runs, and the run with two dialogs stacked (SystemUI's and the launcher's) lost the package service after
one; one press on "Wait" removed it. `adb shell uiautomator dump /dev/tty` returns only a one-line notice,
`adb exec-out` returns the XML. After the repair: the four classes `OK (56 tests)` on three consecutive
driver runs, the first starting with the dialog up.

## The three singles (T5, `dev36` at 320×640)

`ForcedSize` was the plan and was tried first on two of them; both got worse (five failures, not three),
because each test's number was tied to something other than the window.

- `CellOutputImageTest`: the column is a `Box` the test names (600dp, 300dp). `width(600.dp)` is clamped
  to the window's 320; `requiredWidth` is not. `ForcedSize(900dp)` fixed that one but moved `96.0` to
  `97.14` (a different density) and the 300dp column to 900, so the window was not the property.
- `ConsoleInputRowTest`: the field is 160dp of a 320dp row and 216dp of 360dp, since three 48dp buttons
  take 144dp. The test now asserts the field's share of the row (0.4; 0.5 at 320, 0.6 at 360) against a
  field that lost its weight at a few dp, instead of an absolute 200dp.
- `TerminalPaneScreenTest`: a 600px keyboard exceeds the 528px a 640px window gives the terminal, so the
  grid hit its two-row floor. Resizing the keyboard was not enough: the focused field raises the
  emulator's real keyboard (259px) about half a second after the first layout and re-dispatches its
  insets over any sent to the window, so the baseline read 13 rows on one run and 26 on the next. The
  insets are now the composition's own, through `DeviceConfigurationOverride.WindowInsets`: none, then 40%
  of the window (256px, 14 rows expected, 9 to 17 accepted).

## The first whole-suite run (T6, `dev36` at 320×640, 2026-11-17)

`scripts/instrumented-suite-driver.sh` with no arguments, after a 180 s idle: the runner printed
`Time: 3,470.9` and `There were 55 failures:`. **The suite finished.** The driver's `head -30` showed 30 and
the stream ends inside the end-of-run summary at failure 53 (`com.jupy`), without the `Tests run` line; failures
53 to 55 are all in the inline records above it (`SpaceSettingsDialogVariablesTest` ×3). Why the stream stops
there is not known: the emulator's own log says nothing about adb or the instrument process, and no logcat was
taken. Read next to it: 0 `RootViewWithoutFocusException`, 0 `Process crashed`, 0 "isn't responding" text.

| Cause the message names | Tests (55) |
|---|---|
| **a number the window decides** — the assertion carries it (600 vs 320, 136 vs 204, 12 vs 18, a 640 that is the window's height) | 9: `RichTextImageTest` ×2, `MarkdownCellFieldTest`, `MarkdownCellTest`, `ChatSidePanelTest.sendIsPadded…`, `CodeCellFieldFontSizeTest`, `ListGlyphFontScaleTest`, `SettingsScreenTest.everyThemeChoice…`, `CellOutputsViewTest.aScrolledCell…` |
| **a node "is not displayed"** — the sheet, dialog or row the test opened or scrolled to | 13: `CellOutputsViewTest` ×3, `DiagnosticMarkTapTest` ×3 (it forces 320, 360 and 412dp and fails at all three), `NotebookBarShutdownTest` ×3, `DebuggerVariablesSheetTest`, `KernelPickerDialogTest`, `RunningSidePanelTest`, `SearchSidePanelTest.theGlobBoxes…` |
| **input could not be injected or typed** | 8: `NotebookBarShutdownTest` ×2, `EditorCloseGateTest`, `SpaceSettingsDialogDefaultKernelTest`, `…ThreadCapTest`, `…VariablesTest` ×2, `SearchReplaceAllTest` |
| **a `waitUntil` of 5000 ms ran out** | 10: `SearchSidePanelTest` ×7, `SettingsScreenTest` ×3 |
| **a second window stood beside the sheet** (`PopupLayout 0,0-153,40`, "expected exactly the sheet") | 4: `SheetStatusBarAppearanceTest` |
| **a result is wrong and the message names no window** | 11: `TerminalExtraKeysRowTest`, `BundledLicenceAssetsTest`, `ConsoleTranscriptViewTest`, `CellOutputsViewTest` ×2, `DiagnosticSquiggleDrawTest` ×2, `FileEditorBreadcrumbTest`, `NotebookBarShutdownTest.shuttingTheKernelDown…`, `TabCloseTouchAreaTest` (`GAP-210`'s press), `SpaceSettingsDialogVariablesTest.aStoredVariable…` |

This reads each failure's message; none has been re-run alone, so no row says *window*, *focus* or *defect* yet.
What the run does and does not support:

- **Not one dead run.** Failures sit beside passes inside a class, by each class's `@Test` count
  (`ChatSidePanelTest` 1 of 26, `SettingsScreenTest` 4 of 36, `SearchSidePanelTest` 8 of 27; the runner's dot
  lines do not count tests, so they are not the source): a dialog holding the root's focus for the whole run
  failed all four `pressBack` tests in T4's run, not a share of them. `NotebookBarShutdownTest` (6 of 7) and
  `SheetStatusBarAppearanceTest` (4 of 4) fail nearly or wholly, which a dead stretch would also do.
- **A dialog or a dead process is not ruled out.** Nothing sampled `mCurrentFocus` during the run (the one read,
  at the end, is the launcher, which is where a finished run leaves it). The driver pressed `Wait` on SystemUI's
  dialog before it started, so a cold boot after a 180 s idle raised it once more: a data point for `GAP-211` and
  `JD-095`. A mid-run return would show only in samples that were not taken.
- **The earlier clean whole-suite runs predate the `wm size reset`.** `ff309bf6e` (2026-11-17) is the driver's
  first reset; T1 found the AVD carrying a 360×640 override that hid failures. A run at 360 passing and one at
  320 failing is what the window rows predict, and is a lead, not a finding.
- **Defects are not named.** `DiagnosticMarkTapTest` failing at 412dp as well as 320dp, and
  `CellOutputsViewTest.tappingBesideAnAddress…` following a link, are the two the window does not obviously
  explain; each earns its own row only once it fails alone at a window that fits it.
- The emulator logged 14,632 `Failed to allocate ColorBuffer with Vulkan backing` from line 100 on, with no
  timestamps to set beside a failure: noted, not attributed.

### Next shards (one `devjob` each, one cold boot each, `devjob --status` and load under `nproc` between them)

The suite is not run as one process again. Each shard is a class list through the driver, so a death costs
minutes and a failure stays attributable; a class that fails the same way alone is the window or a defect, one
that passes alone was the run.

| Shard | Classes |
|---|---|
| S1 — the numbers | `RichTextImageTest`, `MarkdownCellFieldTest`, `MarkdownCellTest`, `ChatSidePanelTest`, `CodeCellFieldFontSizeTest`, `ListGlyphFontScaleTest`, `SettingsScreenTest`, `CellOutputsViewTest` |
| S2 — sheets and dialogs | `NotebookBarShutdownTest`, `EditorCloseGateTest`, `KernelPickerDialogTest`, `DebuggerVariablesSheetTest`, `RunningSidePanelTest`, `DiagnosticMarkTapTest`, `DiagnosticSquiggleDrawTest`, `SheetStatusBarAppearanceTest` |
| S3 — panels and settings | `SearchSidePanelTest`, `SearchReplaceAllTest`, `SpaceSettingsDialogDefaultKernelTest`, `…ThreadCapTest`, `…VariablesTest`, `FileEditorBreadcrumbTest`, `TabCloseTouchAreaTest` |
| S4 — the rest | `TerminalExtraKeysRowTest`, `BundledLicenceAssetsTest`, `ConsoleTranscriptViewTest` |

Before S1 the driver is made to say when focus was lost: a `mCurrentFocus` and "isn't responding" sample taken
every two seconds (T6a: a single `am instrument` has no between-classes), and `logcat -d` kept with the log.
That is its own tested change, not part of a shard. After
S4 the green classes run in shards by package, and the item ships only when a whole-suite run, or every shard
together, reports `OK`.

## Shard S1 (T6b, `dev36` at 320×640, 2026-11-17)

Cold boot, 180 s idle (load 6.2 at its end, one neighbour's job on a `devjob` slot), then the driver over S1's
eight classes: the runner printed `Tests run: 121,  Failures: 4` in 2 min 41 s. The 121 is every test: the
`TestRunner: started:` lines in the kept logcat count 12, 5, 19, 26, 1, 4, 36 and 18 per class. The focus sampler
took 75 samples with no ANR dialog; its 6 samples without a focused window are single samples at a
launcher-to-test-activity handoff, never two in a row, and each failure is one assertion, not a
`RootViewWithoutFocusException`. A second cold boot (same idle) then re-ran each failure alone through the driver:

| Test | Alone | Verdict |
|---|---|---|
| `RichTextImageTest.anImgWiderThanTheColumnShrinksToIt` | fails, `expected:<600.0> but was:<320.0>` | **window** — the test's column is `Modifier.width(600.dp)`, which the 320dp window clamps; `CellOutputImageTest` was the same box and `requiredWidth` fixed it |
| `RichTextImageTest.anImageTooLargeToDrawWholeDrawsShrunkToTheColumn` | fails, same message | **window**, same box |
| `SettingsScreenTest.everyThemeChoiceKeepsItsWholeWordOnAPhoneAtTwiceTheFontScale` | fails, `Dark breaks inside a word` | **defect**, not window and not drift: the test forces `requiredWidth(PHONE_WIDTH)` itself. At 320dp and 2.0 the row read `Dark` 50 x 76dp on two lines (natural 56 x 38), the check 33dp, the row full to 304dp. `0de2361c9` budgeted an 18dp check with 8dp spare; `c9061e8e0` sized the check from its label (33dp at 2.0), which spent the 8dp and ran 6dp over (labels 90 + 61 + 56, check and gap 41, six paddings of 8, minus 2dp of border overlap = 294 of 288). Repaired by `SEGMENT_PADDING` 8dp to 6dp: 282 of 288, `Dark` 56 x 38 on one line, the class `OK`. Capping the check instead would save under 1dp (its ratio floor is 0.85 of the label line, it reads 0.868) |
| `ListGlyphFontScaleTest.theSearchChevronKeepsItsRatioToItsFileNameAtEveryLargerFontScale` | **passes** (`OK (1 test)`) | **run** — in the shard it read glyph 43dp / label line 38dp / container 51dp at 2.0 against 1.2 at 1.0; alone the ratio held. The cause of the difference is not read: a leak from an earlier test in the process is a lead, not a finding |

Counts: 2 window, 1 defect, 1 run. **What S1 says about the 55-failure run:** its S1 classes
failed 17 tests there (9 "a number the window decides", `CellOutputsViewTest` 3 + 2, `SettingsScreenTest` 3
`waitUntil`) and 4 here, so 13 of them passed once S1 ran by itself: 5 the earlier table read as a number the
window decides, 3 as a node not displayed, 3 as a `waitUntil` timeout and 2 as a wrong result. Those 13 are
*run*, not window: the same tests, the same window, the same build. What in a 55-minute process turns them red is not known; the answer the table can give is only that a
smaller process does not.

## Shard S2 (T6c, `dev36` at 320×640, 2026-11-17)

Cold boot, 180 s idle (load 8.9 at boot, 6.7 after the idle, two other repositories' jobs on `devjob` slots), then
the driver over S2's eight classes: the runner printed `Tests run: 61,  Failures: 1`. The 61 is every test: the
`TestRunner: started:` lines in the kept logcat count 7, 6, 8, 5, 17, 11, 3 and 4 for `NotebookBarShutdownTest`,
`EditorCloseGateTest`, `KernelPickerDialogTest`, `DebuggerVariablesSheetTest`, `RunningSidePanelTest`,
`DiagnosticMarkTapTest`, `DiagnosticSquiggleDrawTest` and `SheetStatusBarAppearanceTest`. The focus sampler took 96
samples with no ANR dialog; its 13 samples without a focused window were never two in a row. The one failure was
re-run alone on a second cold boot (same idle, load 16 at boot):

| Test | Alone | Verdict |
|---|---|---|
| `DiagnosticSquiggleDrawTest.aWarningMarkIsATriangle_anErrorMarkACircle` | fails 3 of 3 (twice as one test, once inside its class: `Tests run: 3,  Failures: 1`), `a triangle is wider at its base` | **stale test**, not window, focus or run: it scans the gutter's first 45px for the severity mark's ink, and the gutter has drawn no diagnostic mark since the squiggle took over its tap (`DiagnosticMarkTapTest`; `drawDiagnosticMark` is gone from `app/src/`). With no ink `markThird` has no extent, both counts read 0 and `0 > 0` fails |

Counts: 0 window, 0 defect, 1 stale test, and 18 *run*. **What S2 says about the 55-failure run:** its S2 classes
failed 19 tests there (`NotebookBarShutdownTest` 6, `SheetStatusBarAppearanceTest` 4, `DiagnosticMarkTapTest` 3,
`DiagnosticSquiggleDrawTest` 2, and one each in `EditorCloseGateTest`, `KernelPickerDialogTest`,
`DebuggerVariablesSheetTest`, `RunningSidePanelTest`) and 1 here, so 18 of them passed once S2 ran by itself, the
same tests, the same window, the same build. This includes the two the table named as not obviously window,
`DiagnosticMarkTapTest` at 320, 360 and 412dp: it was 11 of 11 here. Why a 55-minute process turned them red is
still not known.

## Shard S3 (T6d, `dev36` at 320×640, 2026-11-18)

Cold boot, 180 s idle (load 7.9 at boot, 6.8 after the idle, two other repositories' jobs on `devjob` slots), then
the driver over S3's seven classes: the runner printed `Tests run: 58,  Failures: 1` in 2 min 44 s. The 58 is every
test: the `TestRunner: started:` lines in the kept logcat count 27, 9, 3, 4, 3, 7 and 5 for `SearchSidePanelTest`,
`SearchReplaceAllTest`, `SpaceSettingsDialogDefaultKernelTest`, `…ThreadCapTest`, `…VariablesTest`,
`FileEditorBreadcrumbTest` and `TabCloseTouchAreaTest`. The focus sampler took 76 samples with no ANR dialog; its 9
samples without a focused window include two pairs of two in a row (00:41:47 and 00:41:50, 00:41:56 and 00:41:58), at
the launcher-to-test-activity handoffs between classes, and no failure is a `RootViewWithoutFocusException`. The one
failure was re-run alone on a second cold boot (same idle, load 12 at boot, 1.3 after the idle):

| Test | Alone | Verdict |
|---|---|---|
| `TabCloseTouchAreaTest.aPressBelowTheOnePaneRowStillClosesTheTab` | fails 3 of 3 (once in its class, twice alone), `document presses, tabs closed expected:<(0, 1)> but was:<(0, 0)>` | **defect**, not window, focus or run: `MainAreaTabStrip` sized a tab wider than the row it scrolls in (repaired in T6d2) |

Counts: 0 window, 1 defect, 0 stale test, and 15 *run*.

**What the failing frame shows.** The test's own claim is that the active tab has been scrolled whole into view, and
at one pane on a 320dp window it has not. Read from the semantics tree printed at the settled state (px at 160dpi,
so 1px is 1dp; a temporary `printToLog` in the test, reverted): the strip's row holds the Up arrow (4 to 44), the
tab scroller (48 to 192, 144 wide), `Reload` (192 to 232), `Save` (232 to 272) and `Spaces` (276 to 316). Two tabs
at 160dp are 320dp of content, so showing the second whole needs a scroll of 176, and the scroller reads
`value=160, maxValue=176`. The active tab `a.ipynb` therefore runs 48 to 208 with the last 16dp beyond the scroller's
edge, its close at 180 to 204. The press the test aims at the close's top-left plus 12dp lands on x=192, the
scroller's edge, which `Reload` takes (disabled, so it does nothing): no close and no document press, `(0, 0)`.
The class's doc comment and ADR-0142's one-pane sweep both assume the whole tab is in view; the `Spaces` button at
one pane (added after both) and `Reload` and `Save` leave the scroller 144dp where two 80dp tabs need 160.

**The cause (T6d2).** The reveal was not late or short. A frame-by-frame log of the scroller (a temporary probe,
deleted) read `scroll=0/176` and a 144dp viewport from the first frame, and the scroll animated to 160 and stopped:
the tab is 160dp, wider than the 144dp row, so no scroll can show it whole and Compose aligns its leading edge. The
width is the fault: `MainAreaTabStrip` took each tab's fair share of the *strip's* 320dp, and at one pane the Up arrow,
`Reload`, `Save` and `Spaces` leave the scroller 144. Two tabs got 160 each; one tab got 216. Sizing the share from the
scroller instead was tried first and is wrong: it shrinks three tabs from 107dp to the 80dp floor, where a press on the
centre closes the tab (`GAP-210`), and `MainAreaTabStripTest.clickingATabActivatesItWithoutMovingIt` went red on it
(88 tests, 1 failure). The repair keeps the strip's share and caps the tab at the scroller's width, so nothing
changes where a tab already fit. `MainAreaTabStripRevealTest` held the strip to 320dp at two panes only, which is why
nothing covered it.

**What S3 says about the 55-failure run.** Its S3 classes failed 16 tests there (`SearchSidePanelTest` 7 `waitUntil`
timeouts and 1 "not displayed", `SearchReplaceAllTest` 1, `SpaceSettingsDialog*` 5, `FileEditorBreadcrumbTest` 1,
`TabCloseTouchAreaTest` 1) and 1 here, so 15 of them passed once S3 ran by itself, the same tests, the same window,
the same build. Across S1 to S3 that is 13, 18 and 15 tests red in the 55-minute process and green in a shard; what
in the long process turns them red is still not known.

## Shard S4 (T6e, `dev36` at 320×640, 2026-11-18)

Cold boot, 180 s idle (load 12.6 at boot, 9.9 after the idle, three other repositories' jobs on `devjob` slots), then
the driver over S4's three classes: the runner printed `Tests run: 18,  Failures: 1` in 57 s. The 18 is every test:
the `TestRunner: started:` lines in the kept logcat count 10, 7 and 1 for `TerminalExtraKeysRowTest`,
`ConsoleTranscriptViewTest` and `BundledLicenceAssetsTest`. The focus sampler took 27 samples with no ANR dialog; its 3
samples without a focused window (05:14:29, :35 and :38) are single samples between classes, never two in a row, and
the failure is one assertion, not a `RootViewWithoutFocusException`. The APKs were the ones the HEAD sources build
(`assembleDebug` and `assembleDebugAndroidTest` up to date at the start of the job).

| Test | Result | Verdict |
|---|---|---|
| `BundledLicenceAssetsTest.everyDocumentIsTheRepositoryFileItsTitleNames` | fails at `BundledLicenceAssetsTest.kt:41`, `Source offer` does not contain `written offer` | **stale test**, not window and not run: the assertion reads the bundled `SOURCE_OFFER.md`, which `e79bdaa31` (2026-11-03, "point the source offer at the public source and promise no copy") rewrote to say `This project keeps no copy of that source and sends none`; the test's last change (`b066874c4`) predates it. It reads no window and no focus, so no alone-run could change the verdict; the repaired class is the alone-run |

Counts: 0 window, 0 defect, 1 stale test, and 2 *run*.

**The repair.** The test's claim is that each bundled document is the repository file its title names, so it asserts
the file's own heading, `Source for the GPL and LGPL parts of the APK`, instead of wording the file dropped. After it
the class read `OK (1 test)` on a second cold `dev36` (same 180 s idle, load 11.2 after it, no ANR dialog in its 2
focus samples), the build having executed 4 tasks, among them the test APK's compile.

**What S4 says about the 55-failure run.** Its S4 classes failed 3 tests there (`TerminalExtraKeysRowTest`,
`ConsoleTranscriptViewTest` and `BundledLicenceAssetsTest`, one each, all under "a result is wrong and the message
names no window") and 1 here, so the other 2 passed once S4 ran by itself, the same tests, the same window. Across S1
to S4 that is 13, 18, 15 and 2 tests red in the 55-minute process and green in a shard; what in the long
process turns them red is still not known.

## Shard S5 (T6f1, `dev36` at 320×640, 2026-11-18)

Cold boot, 180 s idle (load 16.1 at boot, 12.4 after the idle, two other repositories' jobs on `devjob` slots, the job
itself queued 55 minutes behind them), then the driver over S5's 25 classes: the runner printed `OK (143 tests)` in
332.7 s. The 143 is every test: the `TestRunner: started:` lines in the kept logcat count 143, class by class the
`@Test`s each file holds (the five `theme` classes hold 14, not the 16 this task first wrote). The APKs were the ones the
HEAD sources build (`assembleDebug` and `assembleDebugAndroidTest` 125 of 125 tasks up to date at the start of the job:
`MainAreaTabStrip.kt` last changed 02:52:47 and the app APK built 02:53:38; the licence test last changed 05:15:54 and
the test APK built 05:24:59). The focus sampler took 155 samples with no ANR dialog; its 22 samples without a focused
window never run past two in a row, so no stretch longer than about 4 s, and logcat holds no `RootViewWithoutFocusException`,
"isn't responding" or `Process crashed`.

Counts: 0 window, 0 defect, 0 stale, 0 failures, no repair, no code changed.

**What S5 says about the 55-failure run.** None of S5's classes was in that run's 55 failures, so what it adds is
that a package of 25 classes and 143 tests, among them the 28 of `ExplorerScreenTest` and the 26 of `SpacesHomeScreenTest`,
runs clean in one 5.5-minute process on the same window. The remaining classes are S6 to S8.

## Shard S6 (T6f2, `dev36` at 320×640, 2026-11-18)

Cold boot, 180 s idle (load 19.1 at boot, 22.9 after the idle: the emulator's own four cores plus two other
repositories' jobs on `devjob` slots, the job itself queued behind them), then the driver over the 22 classes of
`data/space` that hold a `@Test`: the runner printed `OK (78 tests)` in 2.568 s. The 78 is every test: the
`TestRunner: started:` lines in the kept logcat count 78, class by class the `@Test`s each file holds. The PRD's "24
classes" counted `BaseSpaceUnderTest` and `PrivateRootUnderTest`, two helpers with no `@Test`, so the shard is 22. The APKs
were the ones the HEAD sources build (no file under `app/src`, `extension-api` or `extensions` is newer than the test
APK, built 05:24:59). The focus sampler took 4 samples with no ANR dialog; logcat holds no
`RootViewWithoutFocusException`, "isn't responding", `Process crashed` or `FATAL EXCEPTION`. `font_scale` read 1.0 before
the emulator was killed.

**What this run does not show.** 2.568 s for 78 tests says most of them returned at an `assumeTrue` that the x86_64
emulator fails by design (19 of the package's files hold one; the arm64 Space probes are out of scope below), and the
log does not count them, so how many ran their body is not measured here. What `OK` states is that the package loads,
starts and does not crash or lose focus on `dev36`, not that the Space runtime works: that is the tablet's question.

Counts: 0 window, 0 defect, 0 stale, 0 failures, no repair, no code changed.

**What S6 says about the 55-failure run.** None of S6's classes was in that run's 55 failures, so it adds no
attribution either way. The remaining classes are S7 and S8.

## Tasks

- [x] T1 — `scripts/instrumented-suite-driver.sh` (build + install both APKs, `am instrument -w`
  over the suite or a `-e class` list, verdict read off the log), then `GAP-182`'s classes re-run at
  HEAD on `dev36` with `-gpu swiftshader_indirect` and each failure classified
  defect / window / flake, recorded in this PRD.
- [x] T2 — the window group: `ForcedSize` wrap per file (DeletedTab, LauncherAutoCreate,
  MultiDocumentNavigation, OpenDocumentMarking, RecreatedPathTab, RenamedTabBeneath,
  SpacesHomeNavigation, TabStripRecreation), each red→green on the driver; the `measured` comments
  corrected where T1's read proved them wrong.
- [x] T3 — `TabCloseSiblingsTest`: the missing menu nodes diagnosed at HEAD and repaired. T1 read
  them as width-dependent — red at the physical 320px, green under a 360px override — so the read is
  whether the menu's items genuinely overflow on a phone or the test reaches for what the device
  never drew. Neither: the long press at the centre of an 80dp tab landed on the close button's 48dp
  target and closed the tab, so the test now presses a quarter of the way in and the product's
  defect is `GAP-210`.
- [x] T4 — the focus flakes, now four: cause named (`-no-window`'s focus or an app bug), then the
  smallest honest answer — a retry that names the cause, or a mechanism that needs none. Neither:
  SystemUI's "isn't responding" dialog held window focus for the whole run (see "The focus failures"),
  so the driver clears it before instrumenting and the four tests are unchanged.
- [x] T5 — `TerminalPaneScreenTest`, `ConsoleInputRowTest`, `CellOutputImageTest`: each read and
  repaired for the window it needs. None needed `ForcedSize` (see "The three singles"): the three
  classes `OK (22 tests)` on three consecutive driver runs.
- [x] T6a — the driver says when focus was lost: `scripts/focus_samples.py` stamps `mCurrentFocus` and the
  "isn't responding" packages every 2 s into `focus-samples.jsonl`, the driver keeps `logcat -d` beside it
  and prints one summary line on every exit. Time-based, not "between classes": one `am instrument` process
  has no seam between classes, and logcat's `TestRunner: started:` lines place each class on the same clock.
- [x] T6b — shard S1 (the numbers), one cold boot; each failure re-run alone and classified: 121 tests, 4
  failures, 2 window, 1 not-window and unsplit, 1 run (see "Shard S1").
- [x] T6b2 — `RichTextImageTest`'s two window failures: the column the test names outgrows the 320dp window,
  repaired the way `CellOutputImageTest` was (`requiredWidth`), red first. The class re-run at HEAD on a cold
  `dev36` read `Tests run: 12,  Failures: 2`, both `expected:<600.0> but was:<320.0>`; with the one `Box`
  changed from `width` to `requiredWidth` it read `OK (12 tests)` on three consecutive driver runs, no ANR dialog
  in any run's focus samples. `show()`'s `width(column)` has the same shape and passes (its images fit 320dp).
- [x] T6b3 — `SettingsScreenTest.everyThemeChoice…AtTwiceTheFontScale`: a defect from `c9061e8e0`, not drift. Read at
  320dp and 2.0 on a cold `dev36`: `Dark` 50 x 76dp on two lines, the check 33dp, the row full to 304dp (see the S1
  table). Repaired by `SEGMENT_PADDING` 8dp to 6dp, this test as its red: `Dark` 56 x 38 on one line, the check 33dp,
  6dp to spare; `SettingsScreenTest`, `ButtonGlyphFontScaleTest`, `LauncherScreenTest` and `TerminalExtraKeysRowTest`
  read `OK (62 tests)` on two consecutive driver runs in one boot, no ANR dialog in either run's focus samples.
- [x] T6c — shard S2 (sheets and dialogs), one cold boot, the one failure re-run alone and classified: 61 tests, 1
  failure, a stale test (see "Shard S2"). `aWarningMarkIsATriangle_anErrorMarkACircle` is deleted with the helpers
  only it used, since the mark it measured no longer exists; the class reads `OK (2 tests)` on two consecutive
  driver runs in one cold boot, no ANR dialog in either run's focus samples.
- [x] T6d — shard S3 (panels and settings), one cold boot, the one failure re-run alone and classified: 58 tests, 1
  failure, a defect whose cause is not yet read (see "Shard S3"). No code changed.
- [x] T6d2 — `TabCloseTouchAreaTest.aPressBelowTheOnePaneRowStillClosesTheTab`: the cause read (a tab wider than the
  row it scrolls in, see "Shard S3"), then repaired with `aTabFitsTheRowTheOnePaneStripLeavesIt` as its red
  (`Tests run: 9,  Failures: 2` before, both tests the failing ones). The 15 classes that drive the tab strip read
  `OK (88 tests)` on a cold `dev36` after it. Not run twice in one boot, unlike T6b2: one 88-test run.
- [x] T6e — shard S4 (the rest), one cold boot: 18 tests, 1 failure, a stale test (see "Shard S4"). The assertion
  on `SOURCE_OFFER.md`'s dropped wording now names the file's heading; the class reads `OK (1 test)` on a second cold
  `dev36`.
- [ ] T6f — the classes no shard has run, by package, then the item's close. One run is the first whole-suite
  run's 3,470 s and its stream ended inside its own summary, so each shard is one cold boot and one driver run
  over the classes of its packages that S1 to S4, T5 and the tab-strip run have not already run alone, and
  each is its own task. Stragglers beyond `GAP-182`'s list become tasks or their own JD.
  - [x] T6f1 — shard S5: `theme`, `domain/model`, `ui/explorer`, `ui/fileeditor`, `ui/terminal`, `ui/console`,
    `ui/spaces`, `ui/settings`, `data/intent` and `data/asset` under `com.jupydroid.app` (25 classes, 143
    `@Test`s; the classes S1 to S4 ran stay out): `OK (143 tests)` on a cold `dev36`, no failure (see "Shard S5").
  - [x] T6f2 — shard S6: `data/space` (22 classes, 78 `@Test`s; the other two files are helpers): `OK (78 tests)`
    on a cold `dev36`, no failure (see "Shard S6").
  - [ ] T6f3 — shard S7: `ui/shell` (45 classes, less the 15 tab-strip classes already `OK`).
  - [ ] T6f4 — shard S8: `ui/editor` (61 classes, less those S1 to S4 ran).
  - [ ] T6f5 — the close: every shard `OK` together (this item's own ship rule), delete this PRD, mark `GAP-182`
    closed, edit the backlog row. **Not done:** the first whole-suite run finished with 55 failures (see "The
    first whole-suite run").

## Out of scope

- The arm64 Space probes — they `assumeTrue`-skip on the emulator, as designed.
- CI for the instrumented suite — none exists; `per-space-runtime-driver.sh` is the precedent.
- A windowed emulator — `-no-window` is the environment every driver assumes; T4 may revisit it.
