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
- [ ] T5 — `TerminalPaneScreenTest`, `ConsoleInputRowTest`, `CellOutputImageTest`: each read and
  repaired for the window it needs.
- [ ] T6 — the whole suite on `dev36`, `OK` in the instrument log; stragglers beyond `GAP-182`'s
  list become tasks or their own JD; delete this PRD and mark `GAP-182` closed.

## Out of scope

- The arm64 Space probes — they `assumeTrue`-skip on the emulator, as designed.
- CI for the instrumented suite — none exists; `per-space-runtime-driver.sh` is the precedent.
- A windowed emulator — `-no-window` is the environment every driver assumes; T4 may revisit it.
