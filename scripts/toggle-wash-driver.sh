#!/usr/bin/env bash
# Turns one search toggle on, once per system theme, and then **reads the wash's own pixels**
# rather than leaving the judgement to an eye.
#
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/toggle-wash-driver.sh two-pane
#   ANDROID_SERIAL=emulator-5554 scripts/toggle-wash-driver.sh one-pane
#
# P7a-1b's second half. `SearchOptionToggles` has always cited `inputOption.activeBackground` and
# has always been handed `secondaryContainer`, which was `list.inactiveSelectionBackground` - two
# reference ids folded onto one role because they measured 4/255 apart. P7a-1b unfolds them, and
# this is where the toggle's half is held to its own id.
#
# **Two themes rather than one, for the reason U1 records**: a single-theme capture cannot tell a
# working palette from one that has collapsed onto a value that happens to read in that theme.
#
# **The measurement is the gate, not the screenshot.** A Maestro assertion cannot read a colour,
# which is exactly how P7a-1's defect survived a green suite. The hierarchy is dumped after the
# flow - maestro leaves the app on the last frame - and `check_node_fill.py` holds the *on* toggle's
# own fill to the value `resolve_vscode_theme.py` resolves out of the reference.
#
# No cell is run, so this needs no Space runtime, and the search panel is reachable at both widths -
# no window rule is declared because both are the point. **Each width keeps its own directory**, the
# shape `chrome-parts-driver.sh` settled: one run overwriting the other's frames is how a
# two-device claim quietly becomes a one-device one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLOW="$ROOT/.maestro/toggle-wash.yaml"
LAYOUT="${1:-two-pane}"
# Overridable, so a later step can re-run this oracle without overwriting P7a-1b's own
# committed frames. P7a-3 needed exactly that: it moves this toggle's radius onto a named
# scale and changes no pixel, so the proof it owes is *this* check still green, captured
# under its own name (`EVIDENCE_ROOT=… evidence/p7a-3-corner-radius`).
EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/toggle-wash}/$LAYOUT"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)

# The resolved reference values these frames are held to. Stated here rather than read at run time
# so the check still means something on a machine with no clone - and cross-checked against
# `theme/ColorRamp.kt`, which is pinned to the same two values by `ColorSchemeTest`.
#
#   python3 scripts/resolve_vscode_theme.py 2026-dark.json --color inputOption.activeBackground
declare -A WASH=([dark]="#313233" [light]="#D6D6D6")
#: The *on* toggle, named for what a tap would do - see the flow's own note on why not "Match case".
ON_TOGGLE="Ignore case"

run_in_theme() {
  local theme="$1" night="$2"
  local out="$EVIDENCE/$theme"
  echo "=== $theme (uimode night $night) ==="
  # Set before the launch, not after: a mid-flow change recreates the activity and loses the panel.
  "${adb[@]}" shell cmd uimode night "$night"
  # Nothing is seeded - a toggle's own fill is not a function of what the workspace holds - but the
  # workspace is cleared anyway so the frame is the same on the tenth run as on the first.
  reset_notebooks
  run_flow "$FLOW" "$out"

  # Taken after the flow rather than inside it: maestro leaves the app where the last step put it,
  # and the last step is the frame holding the toggle on.
  "${adb[@]}" shell uiautomator dump /sdcard/toggle-wash.xml >/dev/null
  "${adb[@]}" pull /sdcard/toggle-wash.xml "$out/hierarchy.xml" >/dev/null
  "${adb[@]}" shell rm -f /sdcard/toggle-wash.xml
}

read_the_wash() {
  local theme="$1"
  local out="$EVIDENCE/$theme"
  echo
  echo "--- $theme: what the *on* toggle is actually filled with ---"
  python3 "$ROOT/scripts/read_node_fill.py" \
    "$out/hierarchy.xml" "$out/02-the-toggle-on.png" content-desc "$ON_TOGGLE"
  python3 "$ROOT/scripts/check_node_fill.py" \
    "$out/hierarchy.xml" "$out/02-the-toggle-on.png" content-desc "$ON_TOGGLE" "${WASH[$theme]}"
}

for theme in dark light; do
  case "$theme" in dark) night=yes ;; light) night=no ;; esac
  run_in_theme "$theme" "$night"
done

# Left on auto rather than forced back to a value this script invented.
"${adb[@]}" shell cmd uimode night auto

for theme in dark light; do read_the_wash "$theme"; done

echo
echo "screenshots: $EVIDENCE/{dark,light}/"
