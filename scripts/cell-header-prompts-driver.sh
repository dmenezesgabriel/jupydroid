#!/usr/bin/env bash
# Seeds a notebook holding a prose cell, a heading cell and a code cell, then measures where each
# header's execution label starts.
#
# **The evidence for this step is an x, and a pair of them.** All three labels read the right words
# in the right cells whichever build is installed, so nothing on screen and no assertion in the
# suite could tell a straight gutter from a ragged one - the difference is a left layout bound,
# which is what `uiautomator dump` reports and what this driver reads out. Run once on the build
# before the change and once on the build after it, into `before/` and `after/`.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime. The emulator is the
# better half of the pair - the density is recorded and the conversion done here, but at density
# 160 a bound is already a dp, and 320dp is the width the gutter's cost is decided at.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/cell-header-prompts.ipynb"
FLOW="$ROOT/.maestro/cell-header-prompts.yaml"
# The half of the pair this run is capturing. `after` unless the caller says otherwise, because
# `before` can only be captured deliberately - from a checkout that predates the change.
EVIDENCE="$ROOT/evidence/cell-header-prompts/${1:-after}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$FIXTURE" "Gutter"
run_flow "$FLOW" "$EVIDENCE"

# Taken after the flow rather than inside it: maestro leaves the app where the last step put it,
# and the last step is the frame holding all three headers.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell uiautomator dump /sdcard/p2n.xml >/dev/null
"${adb[@]}" pull /sdcard/p2n.xml "$EVIDENCE/hierarchy.xml" >/dev/null
"${adb[@]}" shell rm -f /sdcard/p2n.xml

# Whole, not sed'd to one number: a device with an override reports two densities and the first
# one is the one not in use - `effective_density` is where that is decided, and tested.
"${adb[@]}" shell wm density | tr -d '\r' |
  python3 "$ROOT/scripts/read_gutter_x.py" "$EVIDENCE/hierarchy.xml" |
  tee "$EVIDENCE/gutter.txt"

echo
echo "screenshots: $EVIDENCE/"
