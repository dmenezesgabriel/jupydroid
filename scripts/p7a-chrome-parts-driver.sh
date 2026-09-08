#!/usr/bin/env bash
# Seeds a workspace and a document, runs the chrome-parts flow once per system theme, and then
# **reads each frame's own pixels** rather than leaving the judgement to an eye.
#
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/p7a-chrome-parts-driver.sh            # two panes: rail too
#   ANDROID_SERIAL=emulator-5554 scripts/p7a-chrome-parts-driver.sh one-pane   # no rail at 320dp
#
# **Two themes rather than one, for the reason U1 records**: a single-theme capture cannot tell a
# working palette from one that has collapsed onto a value that happens to read in that theme.
#
# **The measurement is the gate, not the screenshot.** P7a's claim is that the activity bar and the
# side panel are drawn a step off the page, and a Maestro assertion cannot read a colour - which is
# exactly how the defect survived P2u-1 with every flow green. `read_chrome_parts.py` reports the
# frame's own vertical colour bands and this script checks them against the values
# `resolve_vscode_theme.py` resolves out of the reference, so a regression fails here.
#
# No cell is run, so this needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLOW="$ROOT/.maestro/p7a-chrome-parts.yaml"
LAYOUT="${1:-two-pane}"
EVIDENCE="$ROOT/evidence/p7a-chrome-parts/$LAYOUT"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)

# The resolved reference values these frames are held to. Stated here rather than read at run time
# so the check still means something on a machine with no clone - and cross-checked against
# `theme/ChromePalettes.kt`, which is pinned to the same values by `ChromePaletteTest`.
declare -A PART_COLOUR=([dark]="#191A1B" [light]="#FAFAFD")
declare -A PAGE_COLOUR=([dark]="#121314" [light]="#FFFFFF")
# `activityBar.border` and `sideBar.border`, which the 2026 themes state at the same hex - two ids
# for two seams, one value. P7a-2 is what draws the second of them.
declare -A BORDER_COLOUR=([dark]="#2A2B2C" [light]="#F0F1F2")

run_in_theme() {
  local theme="$1" night="$2"
  echo "=== $theme (uimode night $night) ==="
  # Set before the launch, not after: a mid-flow change recreates the activity and loses the
  # opened notebook.
  "${adb[@]}" shell cmd uimode night "$night"
  reset_notebooks
  seed_notebook "$ROOT/.maestro/fixtures/p2u-1-chrome.ipynb" "Chrome Check"
  run_flow "$FLOW" "$EVIDENCE/$theme"
}

# Reports every frame's bands, then holds them to the shape the reference draws.
#
# **Position, not presence, and that distinction is load-bearing.** `EditorPalette.cellBackground`
# is `#191A1B` too - a cell sits a step off the page exactly as the side bar does - so "the part
# colour appears somewhere in this frame" is satisfied by an open notebook's own cells whether or
# not the rail was ever fixed. An assertion that can pass for the wrong reason is not an assertion
# (`docs/DECISIONS.md`). What is checked instead is that the **leftmost** band is the part - the
# rail beside two panes, the side panel where there is one - and never the document.
#
# **And the seams between them, since P7a-2.** `read_chrome_parts.py` drops a 1-2px band on purpose,
# so until this step nothing here could report a border that was never drawn - which is how the side
# panel came to abut the document with no `sideBar.border` between them, through P7a-1 green.
read_the_frames() {
  local theme="$1" frames=("$EVIDENCE/$theme"/*.png)
  echo
  echo "--- $theme: what each frame is actually drawn in ---"
  python3 "$ROOT/scripts/read_chrome_parts.py" "${frames[@]}"
  python3 "$ROOT/scripts/check_chrome_parts.py" \
    "${PART_COLOUR[$theme]}" "${PAGE_COLOUR[$theme]}" "${BORDER_COLOUR[$theme]}" "$LAYOUT" "${frames[@]}"
}

for theme in dark light; do
  case "$theme" in dark) night=yes ;; light) night=no ;; esac
  run_in_theme "$theme" "$night"
done

# Left on auto rather than forced back to a value this script invented.
"${adb[@]}" shell cmd uimode night auto

for theme in dark light; do read_the_frames "$theme"; done

echo
echo "screenshots: $EVIDENCE/{dark,light}/"
