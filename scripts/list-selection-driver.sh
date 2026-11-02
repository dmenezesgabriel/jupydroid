#!/usr/bin/env bash
# Opens a document beside the explorer, once per system theme, and then **reads the marked row's
# own pixels** rather than leaving the judgement to an eye.
#
#   ANDROID_SERIAL=TABLET_SERIAL scripts/list-selection-driver.sh
#
# P7a-1b's first half. `list.inactiveSelectionBackground` is **translucent in 2026 Light**
# (`#DADADA99`) and the ramp held it opaque, because the row was drawn from `secondaryContainer` -
# a role that also served the search toggles' on-wash and two contextual top bars, none of them a
# list. The row now takes the side bar's own field and paints the wash the reference draws.
#
# **WIDE WINDOW (>= 840dp)**, and that is a fact rather than a preference: `Navigation.kt:152` ends
# `openDocumentPath` with `.takeIf { twoPane }`, so below two panes no row is ever marked and there
# is nothing here to measure. One device can only prove the half it can draw.
#
# **Two themes rather than one, for the reason U1 records**: a single-theme capture cannot tell a
# working palette from one that has collapsed onto a value that happens to read in that theme. It
# is also the only theme in which the alpha is visible at all - dark's value is opaque.
#
# **The measurement is the gate, not the screenshot.** A Maestro assertion cannot read a colour,
# which is exactly how P7a-1's defect survived a green suite.
#
# No cell is run, so this needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLOW="$ROOT/.maestro/list-selection.yaml"
FIXTURE="$ROOT/.maestro/fixtures/app-chrome.ipynb"
EVIDENCE="$ROOT/evidence/list-selection"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)

# What the row paints, and light's is a **composite** rather than the id's own hex: `#DADADA99` over
# `sideBar.background` `#FAFAFD` is `#E7E7E8`, which is the only value a pixel ever takes. Pinned to
# the same number by `ChromePaletteTest.theLightRowIsAWashAndNotAFill`, so the constant and the
# pixel cannot drift apart. Dark's id is opaque in the reference, so there is no composite.
declare -A ROW=([dark]="#2C2D2E" [light]="#E7E7E8")

# **`checked`, not `selected`, and this was measured rather than assumed.** `ExplorerScreen` sets
# `selected = isOpen` in semantics; Compose maps a `selected` node that carries no `Role` onto
# `isCheckable`/`isChecked`, so `uiautomator dump` writes it as `checked="true"` and leaves
# `selected="false"`. A probe on a two-pane window found exactly one `checked="true"` node in the
# whole frame - the marked row - which is what makes this a selector and not a guess.
#
# Asking the app which row it thinks is open, rather than naming the file, is also what keeps this
# honest: a fixture renamed tomorrow does not quietly move the measurement onto the wrong row.
SELECTOR=(checked true)

run_in_theme() {
  local theme="$1" night="$2"
  local out="$EVIDENCE/$theme"
  echo "=== $theme (uimode night $night) ==="
  # Set before the launch, not after: a mid-flow change recreates the activity and loses the
  # opened notebook - and the opened notebook is the whole point of this frame.
  "${adb[@]}" shell cmd uimode night "$night"
  reset_notebooks
  seed_notebook "$FIXTURE" "Chrome Check"
  run_flow "$FLOW" "$out"

  # Taken after the flow rather than inside it: maestro leaves the app where the last step put it,
  # and the last step is the frame with the row marked.
  "${adb[@]}" shell uiautomator dump /sdcard/list-selection.xml >/dev/null
  "${adb[@]}" pull /sdcard/list-selection.xml "$out/hierarchy.xml" >/dev/null
  "${adb[@]}" shell rm -f /sdcard/list-selection.xml
}

read_the_row() {
  local theme="$1"
  local out="$EVIDENCE/$theme"
  echo
  echo "--- $theme: what the marked row is actually filled with ---"
  python3 "$ROOT/scripts/read_node_fill.py" \
    "$out/hierarchy.xml" "$out/02-the-open-row-is-washed.png" "${SELECTOR[@]}"
  python3 "$ROOT/scripts/check_node_fill.py" \
    "$out/hierarchy.xml" "$out/02-the-open-row-is-washed.png" "${SELECTOR[@]}" "${ROW[$theme]}"
}

for theme in dark light; do
  case "$theme" in dark) night=yes ;; light) night=no ;; esac
  run_in_theme "$theme" "$night"
done

# Left on auto rather than forced back to a value this script invented.
"${adb[@]}" shell cmd uimode night auto

for theme in dark light; do read_the_row "$theme"; done

echo
echo "screenshots: $EVIDENCE/{dark,light}/"
