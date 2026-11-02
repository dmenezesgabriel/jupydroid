#!/usr/bin/env bash
# Gate: a real size change (rotation) reaches the wire, not only the app's own redraw.
#
# The ground truth is the pty's own `TIOCGWINSZ` answer, written to the workspace by the flows
# themselves: `os.get_terminal_size()` is CPython's raw ioctl on fd 1 - the live report `stty size`
# could not give (this busybox build answers a fixed `25 80`, `research/closed-questions.md`
# CLOSED-006) and the quantity `vi`'s fill-line count was only ever a proxy for. A changed `columns
# lines` pair across the rotation is `set_size` observed at the pty, not a redraw inferred. `vi`
# stays open across the rotation so the screenshots still carry the same full-screen-program reflow
# as visual evidence - read by eye, where the fill-line count's own accessibility-tree oracle went
# away: P4j-3b's own entry records this device's per-row `Text` nodes reporting `text=""`
# run-to-run, and by P6d-6 they report nothing at all on this build - which is why the count is gone
# rather than wrapped in yet another wait.
#
# TABLET ONLY (SM-X216B). NOT A SUITE FLOW: it rotates the device, which every flow running after it
# on the same device would inherit.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/terminal-resize"
mkdir -p "$EVIDENCE"

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

# The pty's own reported size, off the workspace file the flow's `tee` wrote - `p4k`'s own
# `marker_text` shape. Polled rather than read once: the flow's own settle covers the echo, and
# the write itself is the thing a slow shell could still be mid-way through.
size_report() { # size_report <file> -> "cols lines" once non-empty, or exit 1 after ~30s
  local out="" tries=0
  while [ "$tries" -lt 30 ]; do
    out="$("$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/$1\" 2>/dev/null" | tr -d '\r\n')"
    [ -n "$out" ] && { echo "$out"; return 0; }
    sleep 1; tries=$((tries + 1))
  done
  echo "timed out: $NOTEBOOK_DIR/$1 never had a size report" >&2
  return 1
}

reset_notebooks
rotate_to 1 # landscape first, so the rotation below is a real configuration change

run_flow "$ROOT/.maestro/terminal-resize-before-rotation.yaml" "$EVIDENCE" || exit 1
BEFORE="$(size_report size-1.txt)" || exit 1
echo "pty size before rotation (cols lines): $BEFORE"

rotate_to 0 # portrait
run_flow "$ROOT/.maestro/terminal-resize-after-rotation.yaml" "$EVIDENCE" || exit 1
AFTER="$(size_report size-2.txt)" || exit 1
echo "pty size after rotation (cols lines):  $AFTER"

[ "$BEFORE" != "$AFTER" ] || {
  echo "the pty's own reported size did not change across the rotation - 'set_size' never reached it" >&2
  exit 1
}
echo "terminal-resize gate passed: the pty itself reports the new size after rotation ($BEFORE -> $AFTER)"
