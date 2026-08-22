#!/usr/bin/env bash
# P2o-4: both of the shell's side panels are reachable where there is no side area to put them in.
#
# NARROW WINDOW, which the sharder reads as < 840dp - exactly the windows this app draws as one
# pane. P2o-3 measured that this is two real widths and not one: the 320dp emulator *and* the
# tablet in portrait at 800dp, which has no activity bar either.
#
#   ANDROID_SERIAL=emulator-5554 scripts/p2o-4-one-pane-side-panels-driver.sh   # 320dp
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/p2o-4-one-pane-side-panels-driver.sh   # 800dp, rotated
#
# The evidence directory is named from the *measured* width, so a run cannot be mislabelled - the
# rule `u7-adaptive-driver.sh` established for the same reason.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook + window_width_dp

WIDTH_DP="$(window_width_dp)"
echo "=== one pane at ${WIDTH_DP}dp ==="

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2f-2-collapse-all-headings.ipynb" "Heading Sections"

run_flow "$ROOT/.maestro/p2o-4-one-pane-side-panels.yaml" \
  "$ROOT/evidence/p2o-4-one-pane-side-panels/${WIDTH_DP}dp"
