# JD-093 — The flows and drivers that stop after their cell run green on the tablet

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-205`, `GAP-208`, `GAP-209`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Every `.maestro/` flow and `scripts/*-driver.sh` that runs a cell on the tablet reaches its last assertion.
A step that fails is read from its frame and attributed to the app or to the flow, and the one that moved is
corrected. No flow focuses a cell by a pixel that a layout change above it can move.

## Demo

Everything here runs a cell, so the device is the arm64 tablet `SM-X216B` (AGENTS.md rule 2); on `dev36`
`FakeKernelGateway` echoes the source back and a green flow proves nothing about the kernel.

1. For each driver a task names, `nice -n 10 timeout 3600 scripts/<driver>.sh` on the tablet, from a cold
   start of the app.
2. A screenshot and a UI dump at every step that changes the screen; the frames are looked at, not only the
   dumps (`[1]:` and "ran in N s" above the answer say the kernel ran the cell).
3. The driver exits 0 and its last assertion is on the output the item expects.

Not `shipped` until every task's driver has done that on the tablet, `chat-markdown-insert` and
`chat-markdown-copy` included, which need opencode signed in on it.

## Who else implements this

Not a capability a reference ships: the item repairs this repository's own UI-test flows. `jupyterlab`'s
galata and `vscode`'s `test/automation` address a control by its role or name, never a coordinate, which is
what [ADR-0155](../adr/0155-a-flow-addresses-a-field-by-its-accessibility-name-never-a-coordinate.md) already
adopted; no reference is opened again here.

## Implementation inventory

| Concern | Files |
|---|---|
| the first-cell sub-flow | `.maestro/focus-first-cell.yaml` (`index: 0`, the node named `Code cell`, `EditorCell.kt` `CODE_CELL_NAME`) |
| three first-cell taps left | `.maestro/chat-markdown-insert.yaml:37`, `.maestro/chat-markdown-copy.yaml:42` (a `longPressOn`), `.maestro/delete-stops-session-demo-delete.yaml:93` |
| `Recent actions` step | `.maestro/delete-stops-session-demo-delete.yaml`, `.maestro/delete-stops-session-demo-overwrite.yaml`, `scripts/delete-stops-session-demo-driver.sh` |
| tab reload expects stale text | `.maestro/tab-reload-demo.yaml`, `scripts/tab-reload-demo-driver.sh`, `app/src/main/java/com/jupydroid/app/ui/shell/ActiveTabReload.kt` |
| long press on a tab now opens a menu | `.maestro/session-follows-rename-demo-tab.yaml`, `.maestro/session-follows-rename-demo-folder.yaml`, `.maestro/recreated-path-demo-notebook.yaml` and their drivers |
| later-cell pixel taps | `input-prompt-demo-answers` (67, 101), `display-update-demo-run` (41, 51), `output-caps-demo-run` (43), `ansi-colour-demo-dark` (55, 81), `image-formats-demo-cells` (43, 49, 57), `gfm-markdown-demo-notebook` (75), `session-follows-rename-demo-tab` (77), `-folder` (67), `delete-stops-session-demo-overwrite` (173), `rendered-link-demo-output-link` (15), `text-output-link-demo-error-url` (19), `-error-path` (18), all `.maestro/*.yaml` |
| Chrome stays resumed after BACK | `scripts/text-output-link-demo-driver.sh` (`back_to_app`) |

## Trade-offs and what we adopt

| Axis | Options | Trade-off | Choice |
|---|---|---|---|
| a later cell's field | a second sub-flow per cell · one sub-flow that takes the cell's index | a copy per case drifts as the pixels did; one sub-flow holds the name and the wait once | one sub-flow taking `index`, replacing `focus-first-cell.yaml` rather than sitting beside it, decided in the task that reads the later cells' bounds |
| a flow whose step the app changed | revert the app · correct the flow | a changed tab menu is a decision already shipped; the flow is the stale half | read each frame, then decide per step; the app is repaired only for a `defect`, red test first |

## Gap analysis

Present and partial. `JD-089` converted 20 of the 23 `1300,354` taps; four flows fail after the cell they
type into (`GAP-208`); 18 later-cell taps in 13 flows, four drivers and one Chrome step stop after the first
cell (`GAP-209`). Not known: which commits changed each step, and whether the tap by name reaches a later
cell. Each task reads before it changes.

## Deviations

None. `longPressOn` stays inline by the field's name, as ADR-0155 says the sub-flow only taps.

## Open decisions

- Whether `tab-reload-demo`'s `before` is the app's or the flow's. Read from a frame on `SM-X216B`, the
  tab selected with no reload tap already reading `after` and `Save tab` disabled: **the flow was stale, and T1 corrected it.** [ADR-0097](../adr/0097-an-open-document-with-no-unsaved-work-follows-its-file.md)
  (2026-10-14, after the flow was written under ADR-0096) makes a clean open document re-read its file when
  inotify reports it written (`FileSaveConflict.followDisk`, `NotebookSaveConflict.followDisk`, wired in
  `FileEditorViewModel`'s `init`), and measured on `SM-X216B` that a cell's rewrite shows in both open tabs with
  no tap and no dot. `ActiveTabReload.kt`'s remark that nothing re-reads a clean document predates it. So T1
  edits the flow only: `notes.txt` and `b.ipynb` read the cell's text on selecting their tab with no reload tap
  and `Save tab` disabled; the dirty section (asked first, Cancel keeps, Reload drops) stays, since the reload
  icon is how a dirty document takes the disk's version; the later-cell tap becomes `Code cell` with
  `index: 1` inline, the shared sub-flow staying T4's. To confirm on the tablet: run the driver unchanged for
  the red frame, then green after the edit.
- Whether the two chat flows can run: they need opencode signed in on the tablet, by the device code over
  `adb` with Chrome in a pop-up window.

## Surface

| Layer | Files |
|---|---|
| `.maestro/` flow + `scripts/` driver | the flows and drivers in the inventory |
| app | `ActiveTabReload.kt` only if T1 finds a defect |
| unit tests | `scripts/check_maestro_output_assertions.py` (+ `_test.py`) stays green; a regression test with any app repair |

## Tasks

One commit each, each closed by the driver green on the tablet with its frames looked at.

- [x] T1 — `tab-reload-demo`: read the frame at `Assert that "before\s*"`, decide app or flow from
  `ActiveTabReload.kt`, repair the one that moved, then its later-cell tap (163)
- [x] T2 — `session-follows-rename-demo-tab`, `-folder` and `recreated-path-demo-notebook`: the long press
  opens a menu where they expect the rename dialog; read it and tap `Rename…` in the menu. Both drivers
  (`session-follows-rename-demo-driver.sh`, `recreated-path-demo-driver.sh`) needed more than the menu step:
  `recreated-path-demo-file` has the same long press, `-folder`'s pixel tap `(1300,427)` landed below the
  field (it now calls `focus-first-cell.yaml`), `-folder` and `recreated-path-demo-reveal` cleared Recents
  though the section arrives shut ([ADR-0121](../adr/0121-the-recents-section-arrives-shut.md)), and the
  rename driver's `tree` skipped only top-level dotfiles, so a moved `g/.ipynb_checkpoints` failed its disk
  check. T3's `Recent actions` step has the same cause as the first two.
- [x] T8 — the flows that long-press a tab and wait for `Rename file` with no menu step, found by T2:
  `renamed-tab`, `renamed-tab-demo-file`, `recreated-path-rename`, `rotation-tab-strip-before`,
  `session-follows-rename`, `tab-rename-refusal`, and the four a wider grep found, `tab-rename-refusal-demo`
  (two presses), `rotation-demo-rename`, `tabs-wide` and `tabs-narrow` (run on the tablet in portrait, 800dp).
  Each driver is green on the tablet. Two flows had a second stale step, the shut Recents section
  ([ADR-0121](../adr/0121-the-recents-section-arrives-shut.md)): `session-follows-rename` and
  `renamed-tab-demo-notebook`, whose `Recent actions` step T3 shares the cause of.
- [ ] T3 — `delete-stops-session-demo-delete` and `-overwrite`: read why `Recent actions` is not found after
  `Files`, then convert the first-cell tap (93) and the later one (173)
- [ ] T4 — one cell-focus sub-flow that takes an index, from the later cells' bounds read off a dump;
  convert `input-prompt-demo-answers`, `image-formats-demo-cells`, `gfm-markdown-demo-notebook`,
  `rendered-link-demo-output-link` and `text-output-link-demo-error-url` / `-error-path`
- [ ] T5 — `display-update-demo-run`, `output-caps-demo-run`, `ansi-colour-demo-dark` and their drivers
- [ ] T6 — `text-output-link-demo-driver.sh` part 2: read Chrome's back stack after BACK, then fix
  `back_to_app`
- [ ] T7 — `chat-markdown-insert` and `chat-markdown-copy`: a `longPressOn` by the field's name; needs opencode
  signed in on the tablet

## Out of scope

- A gate against fixed-point taps: the other `point:` uses are targets on a canvas (ADR-0155).
- `JD-094` (a tab's close target) and `JD-095` (`dev36`'s SystemUI dialog).
