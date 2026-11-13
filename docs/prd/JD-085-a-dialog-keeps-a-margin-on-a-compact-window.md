# JD-085 — A dialog keeps a margin on a compact window

Milestone: `M11` · State: open · Depends on: — · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A person on a phone sees a dialog whose text wraps stand 24dp in from the window's left and right edges, as
Material's own dialogs do; today it fills the window's whole width.

## Demo

Nothing here runs a cell, so `dev36` serves for the phone and the tablet (`SM-X216B`, portrait) gives the
wide window.

1. Cold start on `dev36` at 320dp, Light, font scale 1.0. Open Rename on a file and Save before close on a
   dirty tab. Read each dialog's surface from `uiautomator dump`: its left and right edges against the window's.
   Today both are 0.0dp inside (`AUD-014`: 320.0dp wide).
2. Repeat at 360dp and 412dp (`wm size`, with rotation locked and restored). Each side is 24.0dp inside.
3. A short dialog (Delete's one-line body) is still its content's width, never under 280dp, and centred.
4. On the tablet in portrait (800dp) the dialogs are the width they were (328.0 to 560.0dp): the margin is
   not a new minimum.
5. Tap in the 24dp margin: settle whether the dialog dismisses, as a tap on the scrim beside it does.
6. Restore `wm size`, rotation and font scale.

The item is not `shipped` until this has run on both devices and the frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| Material Components (`MaterialAlertDialog`, 1.13.0) | FULL | `mtrl_alert_dialog_background_inset_*` in the library's `values/dimens.xml` and `values-h480dp/dimens.xml`, read from the 1.13.0 resources | an `InsetDrawable` around the dialog's background: 24dp left and right on a window 480dp tall or more, 10dp on every side under it; 80dp top and bottom on the tall window |
| Android platform theme | FULL | `dialog_background_material` | a 16dp inset round the dialog's background |
| Compose M3 `BasicAlertDialog` (material3 1.4.0) | ABSENT | `AlertDialog.kt:162-176` | `sizeIn(280dp, 560dp)` and nothing outside it; the window wraps the content |
| `jupyterlab@7f0e6906` | not read | — | a browser; no Android window to compare |
| `vscode@3f3380c6` | ABSENT | — | `AUD-014` found no phone dialog |
| `vscode-jupyter@f01d80ca` | ABSENT | — | no dialog of its own |
| `intellij-community@406a27a8` | ABSENT | — | `AUD-014` found no phone dialog |
| `termux-app@3df69d1d`, `winlator@fb66541b` | not read | — | not Compose references; not opened for this question |
| the shipped rootfs | ABSENT | — | no UI |

**Count:** 2 Android sources keep a margin (the platform's and Material Components'), and the library
this app is built on, Compose M3, does not. The desktop references have no compact window to compare. Per
`cross-cutting.md` §8.3, the Compose and Android documentation answers how a component behaves on a phone;
the margin is therefore Material Components' 24dp, not parity with VS Code or IntelliJ.

## Implementation inventory

### Material Components 1.13.0

| Concern | Files |
|---|---|
| The inset values | `dimens.xml` (10dp every side), `dimens-h480.xml` (24dp start and end, 80dp top and bottom) |

### Compose, material3 1.4.0 and ui 1.10.6

| Concern | Files |
|---|---|
| Width of a dialog | `AlertDialog.kt` (`DialogMinWidth` 280dp, `DialogMaxWidth` 560dp, `DefaultBasicAlertDialogOverride`) |
| Hook | `LocalBasicAlertDialogOverride` and `BasicAlertDialogOverride`, **internal** in 1.4.0: an app cannot provide one |
| The window | `AndroidDialog.android.kt` (`WRAP_CONTENT` when `usePlatformDefaultWidth`, line 289; transparent window background, line 498) |

Disagreement: the platform and Material Components inset the window's background, and Compose draws no
background in the window, so there is nothing to inset. The decision is where our own margin goes.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the margin lives | an override of `BasicAlertDialog` · a wrapper over `AlertDialog` · `Modifier.padding` at each of 20 sites | the override is internal, so it cannot be provided; 20 sites repeat one fact | a wrapper in `theme/`, as `FocusRingTextButton` is for `TextButton`; the call sites pass nothing new |
| How | `padding` on the dialog's modifier · `DialogProperties(usePlatformDefaultWidth = false)` and a width | padding outside `sizeIn` shrinks the constraints before the 280 to 560dp clamp, and the window wraps content plus margin, so a wide window is unchanged; the other needs a width computed from the window | the padding, `Spacing.xl` (24dp) a side |
| Guarding it | a review note · an `ArchitectureGuardTest` rule | a new `AlertDialog(` would pass unseen | the rule, beside the one for `TextButton` |
| Under 480dp tall | MDC's 10dp · one 24dp | MDC changes it for a short landscape window, which this item does not measure | 24dp until a measurement says otherwise |

Anything here that outlives the item becomes an ADR before the PRD is deleted.

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| A dialog's side margin | none: 20 `AlertDialog(` calls in 18 files under `ui/` | Material Components, 24dp | absent |
| A guard against a raw dialog | `ArchitectureGuardTest` bans `TextButton`, `IconButton`, `DropdownMenuItem` | — | absent for `AlertDialog` |
| Measuring tools | `theme-six-combinations-driver.sh` (`one-pane-dialogs`), `read_layout_spacing.py` | — | complete: this item adds none |

`:extensions:ai` has no `AlertDialog`. Ours is absent, not present-but-unreachable.

## Deviations

- Not ported: Material Components' 80dp top and bottom inset and its 10dp under 480dp tall. The outcome is a
  compact window's width; a height margin is a different capability and is not measured here.
- Not ported: the `InsetDrawable` itself. Compose has no window background to inset.

## Open decisions

- Whether a tap in the 24dp margin dismisses. The margin is transparent window area, and a touch inside the
  window is not "outside" to the platform. Settled by the demo's step 5; if it does not, the answer is in
  `DialogProperties`, not in a scrim drawn here.
- Whether the wrapper needs an `icon`, `shape` or colour parameter: no site passes one today, so none is added.

## Surface

A theme and UI item with no domain, port or adapter.

| Layer | Files |
|---|---|
| `theme/` | `MarginedAlertDialog.kt`, using `Spacing.xl` |
| `ui/` | the 18 files that call `AlertDialog(`; `SaveBeforeCloseDialog.kt` first |
| unit tests | `ArchitectureGuardTest` |
| instrumented tests | `MarginedAlertDialogTest` on `dev36` |
| `.maestro/` flow + `scripts/` driver | the existing `theme-six-combinations-driver.sh` `one-pane-dialogs phone`, at three widths |

## Tasks

- [ ] T1 — `MarginedAlertDialog` and its first caller, `SaveBeforeCloseDialog`: a wrapped-text dialog sits
  at least 24dp inside the window on both sides (red at 0 and 0 on `dev36`).
- [ ] T2 — the 17 other files, and `ArchitectureGuardTest` bans material3's `AlertDialog` in `ui/`.
- [ ] T3 — read the six dialogs at 320, 360 and 412dp and on the tablet, settle the margin tap, write the ADR,
  edit the ledger row in `parity-ledger.md`.

## Out of scope

A vertical margin, the 10dp short-window rule, and any dialog that is not an `AlertDialog` (a bottom sheet, a
popup menu).
