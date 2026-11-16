# JD-089 — A flow focuses a cell by its field, not by a pixel

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-205`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Twenty tablet flows reach their first cell's field by the name the field carries, so a layout change above a cell
cannot send a tap to nothing. Today each taps `point: "1300,354"`, a pixel that lands 18px below the field on the
build of `a2d37992`, and the failure reads "Run cell not found", which looks like an app regression and is not one.

## Demo

Every flow here runs a cell, so it goes on the arm64 tablet (`SM-X216B`), never `dev36`.

1. Run one flow unchanged, `console-clear-demo-run.yaml` by `scripts/console-clear-demo-driver.sh`. It is red:
   the tap misses the field, nothing is typed and the flow fails at `Run cell`. Take a screenshot and a UI dump
   at the step that fails and say what the frame shows.
2. Read a dump of a freshly created notebook and name the node that takes the text: its bounds, its class, and
   whether a node named `Code cell` is in it (`ADR-0143` says the name rests on a JVM test and was never dumped
   on a device).
3. Run the same flow after the change: the cell takes `print(6 * 7)`, runs on the kernel and answers `42` in
   `Cell output`. Look at the frame, not only the log.
4. Repeat 3 for each of the other flows that carried the tap, a few at a time.

Not `shipped` until step 3 has run for every converted flow on the tablet and the frames were looked at.

## Who else implements this

The question is how a UI test addresses the first editable region of a notebook cell.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | FULL | `galata/src/helpers/notebook.ts:620` (`getCellLocator`), `:704` (`getCellInputLocator`) | a locator by structure (`.jp-Cell`, `.jp-InputArea-editor`) and a cell index, never a coordinate |
| `vscode@3f3380c668d0` | FULL | `test/automation/src/notebook.ts:38` (`focusFirstCell`) | runs the `notebook.focusTop` command, so no element or position is addressed |
| `vscode-jupyter@f01d80ca0aa0` | PARTIAL | `src/test/utils/notebook.ts:31` | drives cells through `commands.executeCommand('notebook.cell.execute', …)`, not through the UI |
| `termux-app@3df69d1da197` | ABSENT | `app/src/test/java` | unit tests only; no UI harness reaches a view |
| `intellij-community`, `winlator`, the shipped rootfs | ABSENT | none | no notebook cell UI test (`grep -rli jupyter` over IntelliJ's `*Test*.kt` and `*Test*.java` lists `KeymapsTestCase.java`, `FileEditorManagerTest.kt` and `PluginUtilsTest.kt`, none of which drives a cell; Winlator has no test source, only a `SoundFontTestDialog`; the rootfs is an image) |

**Count:** 3 of 7 address a cell's editor by something other than a position, and none uses a coordinate. The
Android analogue of a locator is Maestro's selector over the accessibility tree, and the field already has a name
there ([ADR-0143](../adr/0143-a-control-carries-its-name-and-a-text-field-carries-its-own.md)).

## Implementation inventory

### Ours

| Concern | Files |
|---|---|
| The field's name | `app/src/main/java/com/jupydroid/app/ui/editor/EditorCell.kt:239` passes `fieldName = CODE_CELL_NAME`, `:309` defines `"Code cell"` |
| Its test | `CodeCellFieldNameTest`, which ADR-0143 says is all the name rests on |
| The shared sub-flow idiom | `.maestro/open-base-space.yaml`: no driver runs it, every flow calls it with `runFlow` |
| The taps | `grep -rn '1300,354' .maestro`: 23 lines in 20 flows, three of them in `delete-stops-session-demo-overwrite`, `delete-stops-session-demo-delete` and `tab-reload-demo` |
| The flow that already uses the name | `.maestro/diagnostic-detail.yaml` (a comment only) |

Maestro text selectors are `Regex.matches`, full-string (`docs/architecture/cross-cutting.md` §8.6), so `Code cell`
cannot match a longer name, and a notebook with several cells holds several nodes of that name, so the first is
taken with `index: 0`.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What the tap addresses | a fixed point · the field's name · a test tag | a point breaks on any layout change (the comments already disagree: `[805,334]`, `[805,319]`, now `[800,284]`); the name is already spoken by TalkBack and already tested; a tag is a new app change | **The name `Code cell`**, if the dump shows the node; a tag only if it does not |
| Where the selector lives | 23 copies · one sub-flow | copies are the 23 places a rename would have to find | **One `focus-first-cell.yaml`**, so the name and its wait are one decision in one place |

## Gap analysis

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| A flow reaches a cell's field | by pixel, in 23 places | `galata/src/helpers/notebook.ts:704` | present and broken: nothing addresses the field |
| The field's name on a device | tested by JVM, never dumped | — | unmeasured |

What is not known: which commit moved the field. It does not decide the repair, since a selector survives the next
move too, and the three comments above show it moved before.

## Deviations

A flow cannot call a command as VS Code's automation does (`notebook.focusTop`); Maestro only sees the accessibility
tree. The tap by name is the nearest equivalent.

## Open decisions

- Whether `maestro hierarchy` shows a node named `Code cell` that a tap focuses. T1 settles it from a dump; if it
  does not, the repair adds a test tag with `testTagsAsResourceId`, as `SpaceVariablesEditor.kt:95` does, written
  test first.
- Whether each of the 23 taps targets the first cell. They all follow a fresh notebook, and each is read before it
  is converted.

## Surface

A test-harness item with no domain, port or adapter.

| Layer | Files |
|---|---|
| `.maestro/` | `focus-first-cell.yaml` (new); the 20 flows that carry the tap |
| `app/src/main` | none, unless T1 finds the name is not in the dump |
| ADR | one: a flow addresses a field by its accessibility name, never a coordinate |

## Tasks

- [x] T1 — run `console-clear-demo-run.yaml` unchanged on the tablet and read the dump: the failing frame, the
  field's bounds, and whether `Code cell` is in the tree. Read on `SM-X216B` at 1920x1200 with two panes, on the
  debug APK installed there (the commit it was built from was not checked):
  - Red: the flow passes `Tap on point (1300,354)`, `Input text` and `Hide Keyboard`, then fails at `Tap on "Run
    cell"` with `Element not found`. The failing frame is the app's own Launcher tab with the notebook's tab gone,
    which is `GAP-205`'s account: nothing was typed, and `hideKeyboard`'s BACK closed the tab.
  - A freshly created notebook's tree holds an unnamed, clickable `android.widget.EditText` at
    `[883,274][1884,346]`, with a child `android.view.View` of accessibility text `Code cell` at `[883,294][1884,325]`
    that is not clickable. So the name exists on a device, as a child of the node that takes the text, and a tap
    by that name lands at its centre `(1383,309)`, inside the `EditText`. The drawn box in the frame is about
    `[798,283][1895,336]`.
  - `(1300,354)` is 8px below the `EditText` and 18px below the drawn box, which is why it hits nothing.
  - Not yet shown: that a tap by the name focuses the field and takes text. T2 proves it on the same flow.
- [x] T2 — `focus-first-cell.yaml`, and `console-clear-demo-run.yaml` converted; green on the tablet, frames looked at.
  Read on `SM-X216B` with `scripts/console-clear-demo-driver.sh`, exit 0: `Tap on "Code cell", Index: 0` took
  `Input text print(6 * 7)`, `Tap on "Run cell"` found its button, and `42` appeared in `Cell output`. The frame shows
  the field focused with the text and a cursor, `[1]:` and "ran in 0.3 s" above `42`, so the kernel ran it and
  `FakeKernelGateway` did not echo it. 22 taps at `1300,354` remain in 19 flows. No UI dump was taken at the focused
  step (the driver takes screenshots only), so the tap's landing point rests on T1's bounds and the frame.
- [ ] T3 — the other 19 flows converted, each run on the tablet. Too many for one commit, so four, each ending
  in its own tablet run:
  - [x] T3a — `release-install-demo` (green end to end), and the first tap of `tab-reload-demo`,
    `session-follows-rename-demo-tab`, `recreated-path-demo-notebook` and `delete-stops-session-demo-delete`.
    Read on `SM-X216B`: `tab-reload-demo` run unchanged failed at `Tap on "Run cell"` as the console-clear flow
    did, with `a.ipynb`'s tab closed; converted, each of the first three typed into the cell and (all but
    `recreated-path-demo-notebook`, which only types) ran it, then stopped at a later step that is `GAP-208`'s, not
    the tap's. `delete-stops-session-demo-delete`'s second tap and the three in `delete-stops-session-demo-overwrite`
    were not reached, so they stay as they were and wait for `JD-093`. 17 taps remain in 15 flows. The release flow
    ran directly, not through its driver, which uninstalls the app and wipes its data.
  - [x] T3b — the three `python-environments-in-a-space-*` flows that type into a new notebook, plus
    `…-base-fails-tabulate`, `text-output-link-demo-stream-link`, `input-prompt-demo-answers`.
    Read on `SM-X216B` (same debug APK; no `app/src` commit since). Red: `input-prompt-demo-driver.sh` unchanged
    failed at `Tap on "Run cell"` with the Launcher on screen and the notebook's tab gone. Converted, each flow's
    cell took its text and ran on the kernel: `input-prompt-demo-answers` (`Name? Ada` / `hi Ada`, "Python 3"),
    `text-output-link-demo-stream-link` (the link in `Cell output`, resumed activity this app), `…-runs-in-stats`
    (`'/home/jupydroid/envs/stats'`, "Python (stats)", saved `kernelspec: stats`), `…-runs-python3` (`'/usr'`, saved
    `python3`), and `…-new-notebook-in-stats` and `…-base-fails-tabulate` through the whole demo driver, exit 0
    (`5` under "Python (stats)"; `ModuleNotFoundError: No module named 'tabulate'` under "Python 3"). Two drivers
    stop later than the tap, queued as `GAP-209`: `input-prompt-demo-driver.sh` at a second pixel tap
    (`1300,620`), and `text-output-link-demo-driver.sh` in part 2 at `back_to_app`. The deleted-environment driver
    was cut by the host's background limit in its last part, after part 7's assertions. No UI dump was taken at the
    focused step, which the drivers do not take. 11 taps remain in 9 flows.
  - [x] T3c — `progress-bar-demo-runs`, `display-update-demo-run`, `output-caps-demo-run`,
    `markdown-output-demo-notebook`, `ansi-colour-demo-dark`. The first cell's tap only; the three multi-cell flows
    carry later-cell pixel taps too, which are `GAP-209`'s. Read on `SM-X216B` (same debug APK; no `app/src` commit
    since). Red: `progress-bar-demo-driver.sh` unchanged typed nothing and did not close the tab this time: the
    frame shows the cell empty (`1` in the gutter), `[1]: ran in 0.3 s`, and the assertion on `100% done` failed.
    Converted: `progress-bar-demo-driver.sh` and `markdown-output-demo-driver.sh` exit 0 (the saved notebook holds one
    `stream` `100% done\na\nb\n`; frames show the heading, bold, code, bullet and `42`). `display-update`,
    `output-caps` and `ansi-colour` took and ran the first cell (the last in Dark: red, bold green, orange) and then
    stopped at their second cell's pixel tap, 6 taps in 4 flows left. No UI dump was taken at the focused step.
  - [ ] T3d — `chat-markdown-insert` and `chat-markdown-copy`; the copy flow long-presses, which the sub-flow
    cannot express, so it takes `longPressOn` by the same name inline.
- [ ] T4 — the ADR, `GAP-205` and the backlog row closed, this file deleted.

## Out of scope

- A gate against fixed-point taps: 117 other `point:` uses in `.maestro/` are legitimate targets on a canvas, and a
  gate keyed to one number would guard one number.
- The other failures queued in the backlog: `GAP-206`'s rootfs advisories and the instrumented suite's failures.
