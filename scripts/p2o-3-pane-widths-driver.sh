#!/usr/bin/env bash
# P2o-3: what each column of the shell actually gets, at whatever width the device it is given has.
#
# The two-pane threshold is 840dp because of an arithmetic that predates the activity bar - 360dp of
# list pane plus a divider left the notebook more than the width the single-pane layout was designed
# for. A rail takes 80dp off the *window* before either pane sees it, so the arithmetic has to be
# redone against a measured rail rather than an assumed one, and this is that measurement.
#
# NOT A SUITE FLOW: it asserts nothing about the layout, on purpose - the layout is what is being
# measured, and a flow that asserted a width would have to be rewritten by whichever way the
# measurement lands. It is also run three times at three widths, which a sweep does not do.
#
# **A measurement, not an assertion**, and it is run at three widths:
#
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/p2o-3-pane-widths-driver.sh   # 1280dp landscape, two panes
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/p2o-3-pane-widths-driver.sh   # 800dp portrait,  one pane
#   ANDROID_SERIAL=emulator-5554 scripts/p2o-3-pane-widths-driver.sh   #  320dp,          one pane
#
# The evidence directory is named from the *measured* width, so a run cannot be mislabelled - the
# rule `adaptive-panes-driver.sh` established for the same reason.
#
# Runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook + window_width_dp

WIDTH_DP="$(window_width_dp)"
OUT="$ROOT/evidence/p2o-3-two-pane-threshold/${WIDTH_DP}dp"
mkdir -p "$OUT"
echo "=== ${WIDTH_DP}dp ==="

reset_notebooks
# p2f-2's fixture, so the document pane has a notebook in it rather than an empty area - the pane
# is measured from its own bounds either way, but an empty frame proves nothing to a reader.
seed_notebook "$ROOT/.maestro/fixtures/p2f-2-collapse-all-headings.ipynb" "Heading Sections"

run_flow "$ROOT/.maestro/p2o-3-pane-widths.yaml" "$OUT"

# The hierarchy is taken *after* the flow, with the notebook open, which is the state the split is
# about. `uiautomator dump` writes to the device, so it is read back rather than piped.
"$ADB" -s "$MAESTRO_DEVICE" shell uiautomator dump /sdcard/p2o3-hierarchy.xml > /dev/null
"$ADB" -s "$MAESTRO_DEVICE" shell cat /sdcard/p2o3-hierarchy.xml > "$OUT/hierarchy.xml"
"$ADB" -s "$MAESTRO_DEVICE" shell rm -f /sdcard/p2o3-hierarchy.xml

"$ADB" -s "$MAESTRO_DEVICE" shell wm density |
  python3 "$ROOT/scripts/read_pane_widths.py" "$OUT/hierarchy.xml" "$WIDTH_DP" | tee "$OUT/columns.txt"
