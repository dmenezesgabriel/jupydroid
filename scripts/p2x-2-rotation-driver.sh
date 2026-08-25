#!/usr/bin/env bash
# P2x-2b: a window that narrows while a panel is up still has to leave the file browser reachable.
#
# `sidePanel` is `rememberSaveable`, so it survives the activity relaunch a rotation causes; the
# rail is drawn only where there are two panes. Between them, a tablet rotated to portrait - 1280dp
# landscape is two panes, 800dp portrait is one - used to draw the panel it had with no rail to
# switch away from it, and the browser could not be reached again without killing the app. P2x-2
# widened that from two panels to three, which is why it is fixed rather than recorded.
#
# TABLET ONLY: the emulator's window is one pane in both orientations, so there is no narrowing to
# perform there. And the rotation is a real configuration change rather than a resize - which is
# the half `SidePaneNavigationTest`'s own two cases cannot drive.
#
# NOT A SUITE FLOW: it rotates the device, which every flow running after it on the same device
# would inherit.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# Its own subdirectory, the way `p2o-4` separates its two widths: `run_flow` writes `maestro.log`,
# `logcat.txt` and `device.txt` per call, so sharing a directory with the panel flow meant whichever
# driver ran second silently overwrote the other one's log.
EVIDENCE="$ROOT/evidence/p2x-2-search-panel/rotation"
mkdir -p "$EVIDENCE"

# The same rotation `p1g-one-container-driver.sh` established: the accelerometer off first, or the
# device turns itself back the moment it is set down.
rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

reset_notebooks
rotate_to 1 # landscape: 1280dp, two panes, the rail is drawn

run_flow "$ROOT/.maestro/p2x-2-rotation-part1-a-panel-is-up.yaml" "$EVIDENCE" || exit 1

rotate_to 0 # portrait: 800dp, one pane, no side area at all
run_flow "$ROOT/.maestro/p2x-2-rotation-part2-the-browser-is-back.yaml" "$EVIDENCE" || exit 1

echo "a narrowed window drew the file browser, not the panel it was left on"
