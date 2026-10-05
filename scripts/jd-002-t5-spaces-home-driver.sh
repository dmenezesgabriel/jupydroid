#!/usr/bin/env bash
# JD-002 T5: the Spaces home is the start destination, at two panes and at one.
#
# Two flows because the width is the variable: the tablet is two panes in landscape and one in
# portrait, and Back leaves the workspace from a different resting state in each - the lone
# launcher at two panes, the browser at one. The rotation is done here rather than in a flow, the
# way `p2x-2-rotation-driver.sh` does it, so each flow starts at the width it asserts.
#
# TABLET ONLY: the emulator's window is one pane in both orientations.
#
# NOT A SUITE FLOW: it rotates the device, which every flow running after it would inherit.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-002-manage-spaces-t5"
mkdir -p "$EVIDENCE/two-panes" "$EVIDENCE/one-pane"

# The accelerometer off first, or the device turns itself back the moment it is set down.
rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

list_spaces() { "$ADB" shell run-as "$APP" ls -la files/spaces; }

require_installed_app
{ echo "# before"; list_spaces; } >"$EVIDENCE/spaces-dir.txt"

rotate_to 1 # landscape: two panes
run_flow "$ROOT/.maestro/jd-002-t5-part1-two-panes.yaml" "$EVIDENCE/two-panes" || exit 1

rotate_to 0 # portrait: one pane
run_flow "$ROOT/.maestro/jd-002-t5-part2-one-pane.yaml" "$EVIDENCE/one-pane" || exit 1

{ echo "# after"; list_spaces; } >>"$EVIDENCE/spaces-dir.txt"
rotate_to 1
echo "the app opened on the Spaces home and Back returned to it at both widths"
