#!/usr/bin/env bash
# P4j-3d-4's own gate: while the alternate screen is live (vi), a drag moves the cursor by a real
# arrow keypress rather than scrolling the screen.
#
# Two pixel measurements, not one - `terminal_content_bottom_y` (tuned for vi's own `~` glyph,
# `architecture/cross-cutting.md` §8.6 on what a pixel oracle is and is not tuned for) must stay
# unchanged across the drag - the screen itself never scrolled, since `TerminalPaneViewModel.
# scrollBy`'s alt-screen branch never touches `topRow` - while `terminal_top_strip_differs`, over a
# strip tall enough to hold the file's first several lines, must change - the cursor's own cell
# moved from one of those lines to another.
#
# **Portrait, not whatever the device is already at** - `p4j-3c-terminal-font-size-driver.sh`'s
# own `rotate_to`, reusing its already-tuned single-pane geometry.
#
# TABLET ONLY (SM-X216B). NOT A SUITE FLOW: it rotates the device and leaves a `vi` session
# running.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p4j-3d-4-terminal-alt-screen-arrows"
mkdir -p "$EVIDENCE"

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}
restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

content_bottom_y() {
  python3 -c "
import sys
sys.path.insert(0, '$ROOT/scripts')
from terminal_content_extent import terminal_content_bottom_y
print(terminal_content_bottom_y(sys.argv[1]))
" "$1"
}

cursor_area_differs() {
  python3 -c "
import sys
sys.path.insert(0, '$ROOT/scripts')
from terminal_content_extent import terminal_top_strip_differs
print(terminal_top_strip_differs(sys.argv[1], sys.argv[2], strip_height=200))
" "$1" "$2"
}

reset_notebooks
seed_file "$ROOT/.maestro/fixtures/p4j-3d-4-marker.txt" "p4j-3d-4-marker.txt"
rotate_to 0 # portrait

run_flow "$ROOT/.maestro/p4j-3d-4-terminal-alt-screen-arrows.yaml" "$EVIDENCE" || exit 1

BOTTOM_BEFORE="$(content_bottom_y "$EVIDENCE/01-vi-cursor-on-line-one.png")"
BOTTOM_AFTER="$(content_bottom_y "$EVIDENCE/02-vi-cursor-moved-down.png")"
CURSOR_MOVED="$(cursor_area_differs "$EVIDENCE/01-vi-cursor-on-line-one.png" "$EVIDENCE/02-vi-cursor-moved-down.png")"
echo "vi's own ~ fill bottom before the drag: ${BOTTOM_BEFORE}px"
echo "vi's own ~ fill bottom after the drag:  ${BOTTOM_AFTER}px"
echo "the cursor's own area changed:          ${CURSOR_MOVED}"

[ "$BOTTOM_BEFORE" = "$BOTTOM_AFTER" ] || {
  echo "vi's own ~ fill moved (${BOTTOM_BEFORE}px -> ${BOTTOM_AFTER}px) - the drag scrolled the screen instead of moving the cursor" >&2
  exit 1
}
[ "$CURSOR_MOVED" = "True" ] || {
  echo "the cursor's own area did not change - the drag never reached vi as an arrow keypress" >&2
  exit 1
}
echo "P4j-3d-4 gate passed: the alternate screen redirected a drag to the cursor, not a scroll"
