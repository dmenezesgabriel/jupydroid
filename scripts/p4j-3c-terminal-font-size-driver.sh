#!/usr/bin/env bash
# P4j-3c's own gate: the terminal's own font-size setting actually reflows the grid and redraws
# the glyphs at the new scale against a real shell - not only the unit/instrumented proof that
# the setting persists and the stepper clamps (`TerminalSettingsRepositoryContract`,
# `TerminalPaneViewModelTest`, `TerminalActionsMenuTest`).
#
# `vi`'s own full-screen `~` fill is the same oracle idea P4i-3a/P4j-3b already use for a resize,
# read differently again: `terminal_row_spacing_px` measures the pixel gap between consecutive
# `~` rows in the screenshot - `codeTextStyle`'s cell height rendered - rather than
# `terminal_content_bottom_y`, which would not discriminate here: a bigger font still fills the
# same screen with fewer, taller `~` rows, so the *bottom* of the content stays near the bottom
# either way and only the *spacing between rows* moves.
#
# **Portrait, not whatever the device is already at** - `p4i-terminal-resize-driver.sh`'s own
# `rotate_to`. A real run of this flow at this device's own landscape default came up in the
# two-pane list-detail layout (`ListDetailSceneStrategy`), which puts the terminal's own content
# to the right of the Files pane rather than at the screen's left edge - `_BACKGROUND_SAMPLE_X`
# and every oracle's own `x_range` default are tuned against a single-pane capture
# (`evidence/p4j-3b-terminal-ime-resize/`'s own `w800dp-h1280dp`), and sampled the *Files* pane's
# background instead, which measured no reflow at all across three real font-size changes -
# a device-measured false negative, not a defect in the setting itself (confirmed by inspecting
# the raw pixels: the terminal's own cursor and glyphs sat at x~716-730, not x=0-60). Portrait
# reuses that same already-tuned geometry rather than teaching the oracle a second pane layout.
#
# TABLET ONLY (SM-X216B). NOT A SUITE FLOW: it rotates the device, which every flow running after
# it on the same device would inherit, and leaves a `vi` session running.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p4j-3c-terminal-font-size"
mkdir -p "$EVIDENCE"

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}
restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

row_spacing_px() {
  python3 -c "
import sys
sys.path.insert(0, '$ROOT/scripts')
from terminal_content_extent import terminal_row_spacing_px
print(terminal_row_spacing_px(sys.argv[1]))
" "$1"
}

reset_notebooks
rotate_to 0 # portrait

run_flow "$ROOT/.maestro/p4j-3c-terminal-font-size.yaml" "$EVIDENCE" || exit 1

DEFAULT_SPACING="$(row_spacing_px "$EVIDENCE/01-vi-default-size.png")"
INCREASED_SPACING="$(row_spacing_px "$EVIDENCE/02-vi-increased-size.png")"
DECREASED_SPACING="$(row_spacing_px "$EVIDENCE/03-vi-decreased-size.png")"
echo "row spacing at default size:            ${DEFAULT_SPACING}px"
echo "row spacing after two increases:         ${INCREASED_SPACING}px"
echo "row spacing after increasing then one decrease: ${DECREASED_SPACING}px"

[ "$INCREASED_SPACING" -gt "$DEFAULT_SPACING" ] || {
  echo "two taps on Increase Terminal Font Size did not widen the row spacing (${DEFAULT_SPACING}px -> ${INCREASED_SPACING}px) - the setting did not reach the grid" >&2
  exit 1
}
[ "$DECREASED_SPACING" -lt "$INCREASED_SPACING" ] || {
  echo "Decrease Terminal Font Size did not narrow the row spacing back (${INCREASED_SPACING}px -> ${DECREASED_SPACING}px)" >&2
  exit 1
}
echo "P4j-3c gate passed: the grid reflowed and the glyphs redrew at each size (${DEFAULT_SPACING}px -> ${INCREASED_SPACING}px -> ${DECREASED_SPACING}px)"
