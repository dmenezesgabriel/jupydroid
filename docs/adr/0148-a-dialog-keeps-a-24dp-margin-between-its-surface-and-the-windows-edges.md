# ADR-0148 — A dialog keeps a 24dp margin between its surface and the window's edges

Status: accepted
Date: 2026-11-14

## Context

On a 320dp phone a dialog whose text wraps was drawn the window's whole width: Rename, Rename refused, Space
settings and Save before close were 320.0dp wide with no margin to either edge. Material's own dialogs keep
one. The platform theme's `dialog_background_material` insets its background 16dp, and Material Components'
`MaterialAlertDialog` insets 24dp on a window 480dp tall or more (`mtrl_alert_dialog_background_inset_start`
and `_end`, 1.13.0). Compose M3 does not: `BasicAlertDialog` is `sizeIn(280dp, 560dp)` with nothing outside
it (material3 1.4.0, `AlertDialog.kt`), and Compose's `Dialog` replaces the platform's inset background with a
transparent one, so there is no window background to inset. VS Code and IntelliJ have no phone dialog to
compare.

## Decision

**`MarginedAlertDialog` in `:extension-api` wraps `AlertDialog` and pads its modifier by `Spacing.xl`, 24dp, on
each side.** The padding sits outside `sizeIn`, so it shrinks the constraints before the 280 to 560dp clamp,
and the window wraps surface plus margin.

- **A wrapper, not an override.** `LocalBasicAlertDialogOverride` is `internal` in material3 1.4.0, so an app
  cannot provide one; the alternative was the same padding at each of 22 call sites.
- **In `:extension-api`**, beside `FocusRingTextButton`, because `DeleteFileDialog` and `RenameFileDialog` are
  drawn there and an extension cannot import `:app`
  ([ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)).
- **Guarded.** `ArchitectureGuardTest.uiLayer_callsNoMaterialAlertDialog` and
  `FocusRingGuardTest.theContractNamesNoMaterialAlertDialog` refuse a raw `AlertDialog` import, so a new
  dialog cannot skip the margin unseen.
- **Not ported:** Material Components' 80dp top and bottom inset and its 10dp inset under 480dp tall. The
  outcome is a compact window's width; a height margin was not measured.
- **A tap in the margin does not dismiss.** The margin is transparent area of the dialog's own window, so
  Compose counts a touch there as inside the dialog. Settled by reading it: at 360dp with Space settings open
  (card 44.0 to 316.0dp), a tap at x=30dp, in the margin, left the dialog up, and a tap at x=8dp, outside the
  window, dismissed it. The fix, if one is wanted, is in `DialogProperties`, not a scrim drawn by the wrapper.
  Whether Material Components' inset behaves the same was not read.

## What was measured

`dev36` (x86_64, density 160, Light, font scale 1.0, no cell run) and the arm64 tablet `SM-X216B` (240dpi,
portrait, 800dp). The margin is read as the pixels from the frame's edge to the card on a row through the
middle of the dialog, in dp (`scripts/read_dialog_margin.py`, tested against a real frame by
`scripts/read_dialog_margin_test.py`
with the fixtures in `scripts/testdata/dialog-margin/`). The six dialogs are Delete,
Rename, Rename refused, Save before close and Space settings, at rest and scrolled
(`scripts/theme-six-combinations-driver.sh <dir> one-pane-dialogs phone|tablet`).

| Window | Left and right margin, all six dialogs | Card |
|---|---|---|
| 320dp | 24.0 and 24.0 | 272.0dp |
| 360dp | 44.0 and 44.0 | 272.0dp |
| 412dp | 70.0 and 70.0 | 272.0dp |
| 800dp, tablet | Rename 236.0, Delete 184.0, Save before close 224.0, Space settings 134.0 | 328.0, 431.3, 352.0, 532.0dp |

Before this change the dialogs were 320.0dp at 320dp (Save before close 319.0) and 328.0 to 560.0dp on the
tablet; the tablet's widths are unchanged, so the margin is not a new minimum. `DialogMarginTest` pins the
margin on both a `:app` dialog and `DeleteFileDialog`.

## Consequences

**The margin is a floor, not a fixed 24dp.** The padding leaves the card's own width alone, so 24.0dp is
what a wrapped card is left with only when the window is no wider than the card plus 48dp, as at 320dp. On a
360dp and a 412dp phone the card read 272.0dp and stood 44.0 and 70.0dp in. The change's first demo expected
24.0dp at all three widths and that was wrong as written: the item's earlier prediction that a wrapped dialog
"fills a 360dp or 412dp phone as it fills 320dp" came from reading the source, and no one had measured those
widths. **What a dialog was at 360 and 412dp before the margin was not measured**, so this does not say whether
the card at those widths is the margin's doing or the platform's. Why a card stays 272.0dp across the three
widths was not run down either.

**A person tapping the margin sees nothing happen.** It looks like scrim and is not. At 24dp each side on a
320dp phone that is a strip a thumb can land in.

**A new dialog owes the wrapper.** The two guards refuse the raw import in `ui/` and in `:extension-api`; an
extension module such as `:extensions:ai` has no `AlertDialog` today and no guard of its own.
