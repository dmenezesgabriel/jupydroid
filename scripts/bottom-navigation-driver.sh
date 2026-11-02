#!/usr/bin/env bash
# The activity bar where the shell draws one pane - the bar under the panel, never over the
# document, and the workspace search that had no route at this width before it.
#
# NARROW WINDOW, which the sharder reads as < 840dp - exactly the windows this app draws as one
# pane. P2o-3 measured that this is two real widths and not one: the 320dp emulator *and* the tablet
# in portrait at 800dp, which the plan's own row had left with neither rail nor bar.
#
#   ANDROID_SERIAL=emulator-5554 scripts/bottom-navigation-driver.sh   # 320dp
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/bottom-navigation-driver.sh   # 800dp, rotated
#
# The evidence directory is named from the *measured* width, so a run cannot be mislabelled - the
# rule `adaptive-panes-driver.sh` established and `one-pane-side-panels-driver.sh` follows.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook + seed_file + window_width_dp

WIDTH_DP="$(window_width_dp)"
echo "=== one pane at ${WIDTH_DP}dp ==="

# A clean workspace, because a search reads *everything* and the count this flow asserts is the
# count these fixtures hold - the exposure `workspace-search-results-driver.sh` records. The filler
# below is written to match nothing the flow searches for, which is what keeps that count at 3.
reset_notebooks
# The notebook the bar must **not** be drawn over. `collapse-all-headings`'s fixture, as
# `one-pane-side-panels` uses it: known not to fit one screen at this width, so a bar under it would
# be over content rather than over blank.
seed_notebook "$ROOT/.maestro/fixtures/collapse-all-headings.ipynb" "Heading Sections"
# The file the search finds. Three matches for "cat", none of which is in the notebook above.
seed_file "$ROOT/.maestro/fixtures/search.py" "notes.py"
# **Twenty more, so the list overflows the pane.** The last claim is about the panel's foot -
# that its content is clipped BY the bar rather than stopping short of it - and blank space under
# the last row proves nothing either way. Twenty is what P2y-d measured the defect with; zero-
# padded so the order is the same at both widths.
for n in $(seq -w 0 19); do
  seed_file "$ROOT/.maestro/fixtures/bottom-navigation-filler.py" "file$n.py"
done

run_flow "$ROOT/.maestro/bottom-navigation.yaml" \
  "$ROOT/evidence/bottom-navigation/${WIDTH_DP}dp"
