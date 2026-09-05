#!/usr/bin/env bash
# P4j-3b's own gate: a real size change (the keyboard opening) reaches the wire, not only a
# rotation (P4i-3a's own gate, `p4i-terminal-resize-driver.sh`).
#
# `vi`'s own full-screen redraw is the same *idea* reused - its fill lines run to the bottom of
# whatever the pty was told its screen was - but not the same *reading* of it. P4i-3a's own
# oracle (`grep -c 'text="~"'` over a `uiautomator dump`) proved unreliable on this device,
# independent of anything `TerminalCanvas` draws: a real run found it reporting zero fill lines
# against a screenshot showing dozens, reproduced with and without this phase's own code
# (`docs/DECISIONS.md`'s own P4j-3b entry). `terminal_content_extent.py` reads the screenshot's
# own pixels instead - the terminal's thin monospace strokes against every UI chrome element's
# thicker glyphs - which is what proves the resize here.
#
# `stty size` is still rejected for the reason `docs/DECISIONS.md` already gives - this rootfs's
# busybox/toybox answers it from a fixed default, not a live `ioctl`.
#
# Measured with the keyboard *open* first, `p4i-terminal-vi.yaml`'s own proven shape (autofocused,
# no `hideKeyboard` before typing) - a resize racing `vi`'s own startup redraw is a real, measured
# hazard (`docs/DECISIONS.md`'s P4j-3b entry), so the keyboard is only ever dismissed once `vi` is
# already idle, in part2.
#
# TABLET ONLY (SM-X216B). NOT A SUITE FLOW: part1 leaves a `vi` session running, which any flow
# run after it on the same device would inherit.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p4j-3b-terminal-ime-resize"
mkdir -p "$EVIDENCE"

content_bottom_y() {
  python3 -c "
import sys
sys.path.insert(0, '$ROOT/scripts')
from terminal_content_extent import terminal_content_bottom_y
print(terminal_content_bottom_y(sys.argv[1]))
" "$1"
}

reset_notebooks

run_flow "$ROOT/.maestro/p4j-3b-terminal-ime-resize-part1.yaml" "$EVIDENCE" || exit 1
OPEN_Y="$(content_bottom_y "$EVIDENCE/01-vi-keyboard-open.png")"
echo "content bottom with the keyboard open: y=$OPEN_Y"

run_flow "$ROOT/.maestro/p4j-3b-terminal-ime-resize-part2.yaml" "$EVIDENCE" || exit 1
CLOSED_Y="$(content_bottom_y "$EVIDENCE/02-vi-keyboard-closed.png")"
echo "content bottom with the keyboard closed: y=$CLOSED_Y"

[ "$CLOSED_Y" -gt "$OPEN_Y" ] || {
  echo "the shell's own content did not extend further once the keyboard closed (y=$OPEN_Y -> y=$CLOSED_Y) - 'set_size' did not reach it" >&2
  exit 1
}
echo "P4j-3b gate passed: the shell reflowed with the keyboard (content bottom y=$OPEN_Y -> y=$CLOSED_Y)"
