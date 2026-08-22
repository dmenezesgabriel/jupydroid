#!/usr/bin/env bash
# Seeds a markdown cell and a raw cell holding the same leading text, opens the markdown cell's
# source, and captures the frame where the two editors meet.
#
# **The evidence is a pair, because the change is a font.** A markdown cell's source was drawn in
# `bodyMedium` - prose - where every other cell's is drawn in the editor font. Run once on the
# build before the change and once after, into `before/` and `after/`.
#
# **The fixture is what makes the frame readable.** Both cells open with `iiii mmmm WWWW`: in a
# monospace font those three groups are the same width and the two lines align down the frame; in
# a proportional font they are not and do not. So the difference is visible rather than only
# measurable - which matters here, because three defects in this repo's history were green against
# every assertion and wrong in the picture.
#
# **Captured at phone geometry rather than the AVD's own**, for the reason
# `p2l-3b-rendered-code-driver.sh` records: `dev36` is 320x640px at density 160, so it draws 1px
# per dp and a type judgement made there is made on proportions no reader will see
# (`docs/DECISIONS.md`). The driver overrides the window to 1080x2400 at 420dpi - Pixel-class,
# 411dp - and resets it afterwards.
#
# No cell is run - a markdown cell renders from the file - so this needs no Space runtime and
# either device will do.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p2s-markdown-source-font.ipynb"
FLOW="$ROOT/.maestro/p2s-markdown-source-font.yaml"
# The half of the pair this run is capturing. `after` unless the caller says otherwise, because
# `before` can only be captured deliberately - from a checkout that predates the change.
EVIDENCE="$ROOT/evidence/p2s-markdown-source-font/${1:-after}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)
reset_window() {
  "${adb[@]}" shell wm size reset >/dev/null 2>&1 || true
  "${adb[@]}" shell wm density reset >/dev/null 2>&1 || true
  "${adb[@]}" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
}
trap reset_window EXIT

# **Portrait, locked, and the tablet is why.** `wm size` sets the *natural* orientation's
# resolution, so on a device auto-rotated to landscape the override came out 2400x1080 - a
# two-pane shell with the notebook in a narrow column, which is not the layout this frame is
# about and not the one the emulator drew. p1g's own rotation lock, for the same reason.
"${adb[@]}" shell settings put system accelerometer_rotation 0 >/dev/null
"${adb[@]}" shell settings put system user_rotation 0 >/dev/null
"${adb[@]}" shell wm size 1080x2400 >/dev/null
"${adb[@]}" shell wm density 420 >/dev/null

reset_notebooks
seed_notebook "$FIXTURE" "Markdown Source Font"
run_flow "$FLOW" "$EVIDENCE"

echo
echo "screenshots: $EVIDENCE/"
