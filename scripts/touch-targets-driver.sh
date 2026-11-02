#!/usr/bin/env bash
# Seeds a notebook holding a folded cell and a folded heading, then measures what the two rows
# built from a bare `clickable` reserve.
#
# **The evidence for this step is a number, and a pair of them.** Compose expands the hit area of
# any clickable out to 48dp at the input layer, so nothing on screen and no assertion asking
# `touchBoundsInRoot` could distinguish a row that reserves 48dp from one that borrows it from its
# neighbours - and the borrowed half loses to a neighbour's direct hit
# (`NodeCoordinator.outOfBoundsHit`). What separates the two is the *layout* bound, which is what
# `uiautomator dump` reports and what this driver reads out. Run once on the build before the
# change and once on the build after it, into `before/` and `after/`.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime. The emulator is the
# better half of the pair anyway - the density is recorded and the conversion done here, but at
# density 160 a bound is already a dp.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/touch-targets.ipynb"
FLOW="$ROOT/.maestro/touch-targets.yaml"
# The half of the pair this run is capturing. `after` unless the caller says otherwise, because
# `before` can only be captured deliberately - from a checkout that predates the change.
EVIDENCE="$ROOT/evidence/touch-targets/${1:-after}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$FIXTURE" "Touch Targets"
run_flow "$FLOW" "$EVIDENCE"

# Taken after the flow rather than inside it: maestro leaves the app where the last step put it,
# and the last step is the frame holding both rows.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell uiautomator dump /sdcard/p2l-4.xml >/dev/null
"${adb[@]}" pull /sdcard/p2l-4.xml "$EVIDENCE/hierarchy.xml" >/dev/null
"${adb[@]}" shell rm -f /sdcard/p2l-4.xml

# Whole, not sed'd to one number: a device with an override reports two densities and the first
# one is the one not in use - `effective_density` is where that is decided, and tested.
"${adb[@]}" shell wm density | tr -d '\r' |
  python3 "$ROOT/scripts/read_touch_target_bounds.py" "$EVIDENCE/hierarchy.xml" |
  tee "$EVIDENCE/reserved.txt"

echo
echo "screenshots: $EVIDENCE/"
