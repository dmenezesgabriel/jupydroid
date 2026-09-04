#!/usr/bin/env bash
# P4i-3a's own gate: a real size change (rotation) reaches the wire, not only the app's own redraw.
#
# `vi`'s own full-screen redraw is `p4i-terminal-vi`'s own proof reused: its `~` fill lines run to
# the bottom of whatever the pty told it its screen was, so a changed fill-line count is proof
# `TerminalGateway.resize` actually wrote `set_size` to the real shell - `p1g-one-container-driver.sh`'s
# own split applies here too: the flows drive the shell and capture screenshots, this driver reads
# the ground truth (the accessibility tree's own text, P4i-2d's transparent-`Text`-per-row) and does
# the real comparison. `stty size` was tried first and rejected: this busybox/toybox build answers
# it from a fixed default rather than a live `ioctl`, confirmed by comparing it against `vi`'s own
# reflow, which did change - see `docs/DECISIONS.md`'s P4i-3a entry.
#
# TABLET ONLY (SM-X216B). NOT A SUITE FLOW: it rotates the device, which every flow running after it
# on the same device would inherit.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p4i-terminal-resize"
mkdir -p "$EVIDENCE"

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

# How many `~` fill lines vi is currently drawing, read off the live accessibility tree -
# `TerminalCanvas`'s own transparent `Text` per row (P4i-2d) is exactly what `uiautomator dump`
# reports.
count_vi_fill_lines() {
  "$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null | grep -oE 'text="~"' | wc -l
}

reset_notebooks
rotate_to 1 # landscape first, so the rotation below is a real configuration change

run_flow "$ROOT/.maestro/p4i-terminal-resize-part1.yaml" "$EVIDENCE" || exit 1
BEFORE="$(count_vi_fill_lines)"
[ "$BEFORE" -gt 0 ] || { echo "no vi fill lines found in the accessibility tree" >&2; exit 1; }
echo "fill lines before rotation: $BEFORE"

rotate_to 0 # portrait
run_flow "$ROOT/.maestro/p4i-terminal-resize-part2.yaml" "$EVIDENCE" || exit 1
AFTER="$(count_vi_fill_lines)"
[ "$AFTER" -gt 0 ] || { echo "no vi fill lines found in the accessibility tree after rotation" >&2; exit 1; }
echo "fill lines after rotation: $AFTER"

[ "$BEFORE" != "$AFTER" ] || {
  echo "vi's own fill-line count did not change across the rotation - 'set_size' did not reach it" >&2
  exit 1
}
echo "P4i-3a gate passed: the shell reflowed with the rotation ($BEFORE -> $AFTER fill lines)"
