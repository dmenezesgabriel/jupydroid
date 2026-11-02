#!/usr/bin/env bash
# Gate: a drag over the terminal scrolls it back into real output a real shell
# produced, shows it, and a further output snaps it back to the live tail.
#
# `terminal_top_strip_differs` (`terminal_content_extent.py`) is the oracle, not
# `visible:`/`extendedWaitUntil` on the terminal's own text - `architecture/cross-cutting.md` §8.6
# found that unreliable on this device independent of anything drawn, and every terminal-content
# gate since has followed the pixel-based pattern instead. It compares a fixed strip just below the
# app bar between two captures - content-agnostic, and it never crosses into `TerminalExtraKeysRow`
# or a soft keyboard regardless of scroll position, unlike two earlier versions of this driver's own
# oracle that tried reading ink at a distant column and read each of those in turn instead.
#
# **Portrait, not whatever the device is already at** - `terminal-font-size-driver.sh`'s
# own `rotate_to`, reusing its already-tuned single-pane geometry rather than teaching the oracle
# a second pane layout.
#
# TABLET ONLY (SM-X216B). NOT A SUITE FLOW: it rotates the device, which every flow running after
# it on the same device would inherit, and leaves a shell session with 60+ lines of scrollback.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/terminal-scrollback"
mkdir -p "$EVIDENCE"

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}
restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

# `top_strip_differs` itself is the shared helper in `maestro-run.sh`, defaulting to the
# single-pane x_range this driver's own portrait rotation already provides.

reset_notebooks
rotate_to 0 # portrait

run_flow "$ROOT/.maestro/terminal-scrollback.yaml" "$EVIDENCE" || exit 1

SCROLLED="$(top_strip_differs "$EVIDENCE/01-live-tail.png" "$EVIDENCE/02-scrolled-back.png")"
SNAPPED_BACK="$(top_strip_differs "$EVIDENCE/02-scrolled-back.png" "$EVIDENCE/03-new-output-snaps-back-to-live.png")"
echo "top strip changed by the drag (live tail -> scrolled back):        ${SCROLLED}"
echo "top strip changed again by new output (scrolled back -> live):     ${SNAPPED_BACK}"

[ "$SCROLLED" = "True" ] || {
  echo "the terminal's own top strip did not change after five drags - the gesture did not scroll the grid back into its own history" >&2
  exit 1
}
[ "$SNAPPED_BACK" = "True" ] || {
  echo "the terminal's own top strip did not change back after new output arrived - TerminalPaneViewModel.attach's reset-on-output did not snap back to the live screen" >&2
  exit 1
}
echo "terminal-scrollback gate passed: a drag scrolled real scrollback into view, and new output snapped it back to live"
