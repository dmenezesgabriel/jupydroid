# ADR-0159 — The instrumented suite is closed by shards on one build

Status: accepted
Date: 2026-11-18

## Context

The first whole-suite run on `dev36` (2026-11-17) took 3,470 s and printed 55 failures, and its stream
ended inside its own end-of-run summary, without the `Tests run` line. Re-run in groups of classes, 13
of the 17 failures in the first group passed: the same tests, the same window, the same build. What a
55-minute process turns red was never found, and the verdict of one process that long could not be
attributed to a test, a window or a dialog.

Three causes sat under the 55 and each was a different kind of failure:

- **The window.** A test that names a 600dp column, laid out in `dev36`'s physical 320×640 window,
  reads 320 (`RichTextImageTest`). Repaired with `requiredWidth`, the way `CellOutputImageTest` was.
- **A defect.** The Theme row broke `Dark` inside a word at 320dp and 2.0 font scale
  (`SettingsScreenTest`), and a tab wider than the row it scrolls in left the active tab's close
  under Reload (`TabCloseTouchAreaTest`). Both repaired in `app/src/main` with the test as the red.
- **A test that read the wrong thing.** `ListGlyphFontScaleTest.theSearchChevron…` read
  `boundsInRoot`, which the list clips to its viewport, and the soft keyboard the search box raises
  moves that viewport between two readings. It failed in two shards at the same numbers and passed
  once alone, which looked like a flake until a probe made it fail four runs of four: "passes alone"
  had been a timing accident. It now reads each node's laid-out `size`
  (`theSearchChevronKeepsItsRatioWhenTheListIsCutThroughItsHeader` is its red).

A SystemUI "isn't responding" dialog also held window focus for a whole run, and every
`pressBack`/`closeSoftKeyboard` failed with `has-window-focus=false` while Compose-only assertions
passed.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One whole-suite process | One verdict | 3,470 s; a death or a dialog costs the whole run, and a failure cannot be attributed |
| `connectedDebugAndroidTest` | Gradle owns the run | Uninstalls the app from every attached device, the tablet's Space data included |
| **Shards: one cold boot and one driver run per class list** | A death costs minutes; a failure is one class | The close is a sum of runs, so every run must be on one build |

## Decision

The suite is run in shards, never as one process. A shard is one cold `dev36` boot
(`-no-window -no-snapshot -no-audio -gpu swiftshader_indirect`), 180 s idle, then
`ANDROID_SERIAL=emulator-5554 scripts/instrumented-suite-driver.sh <classes>`, and teardown with
`font_scale` 1.0 and `wm size` 320x640 read back and no emulator left.

- A class counts only when it ran on the build HEAD's sources produce. A change to `app/src/main`
  re-runs the classes that could see it; a change to a test re-builds the test APK and re-runs that class.
- The verdict is the instrument log's `OK (n tests)`, and `n` is checked against the
  `TestRunner: started:` lines in the kept `logcat.txt`, so a run whose stream was cut cannot read as a pass.
- A failure is re-run alone, in a second cold boot, before it is named. Passing alone proves the
  run, not the test: a repeating failure gets its cause read (a probe, a bisect of the predecessors)
  before anything is changed.
- The driver clears SystemUI's dialog before it instruments, samples `mCurrentFocus` and the dialog every
  2 s (`scripts/focus_samples.py`) and keeps `logcat -d` on every exit, so a lost focus is placed on the
  class that ran at the time.
- The suite is closed when every `@Test` has run `OK` on one build. A test that measures an on-screen node
  reads its laid-out `size`, not `boundsInRoot` (the six other `boundsInRoot.height` reads under
  `app/src/androidTest` stay until one fails).

## Consequences

- The last shard, the 41 classes S10 ran plus the repaired search-chevron test, read `OK (350 tests)` in
  497 s on a cold `dev36` at 320×640, with 350 `TestRunner: started:` lines, 239 focus samples, no
  dialog, and no `RootViewWithoutFocusException`, "isn't responding", `Process crashed` or
  `FATAL EXCEPTION` in logcat.
- A shard needs a host without another repository's job on a `devjob` slot for its whole run; the boot
  itself raises the load, and `devjob` holds the job until the host has room.
- The arm64 Space probes `assumeTrue`-skip on the emulator, by design: a named skip is part of a clean suite.
