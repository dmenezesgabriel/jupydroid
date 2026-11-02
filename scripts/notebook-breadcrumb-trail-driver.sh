#!/usr/bin/env bash
# The open document's breadcrumb - where a notebook lives, and the way back to its folder.
# NARROW WINDOW (<840dp): the 320dp emulator, and the tablet in portrait at 800dp.
#
# One window, deliberately. The trail's whole claim is that a *third* row is affordable, so the
# device that has to show it is the narrowest one the app supports; and in one pane the notebook's
# trail is the only trail on screen, where two panes put the browser's beside it and every
# selector would match twice. The two-pane half is `notebook-breadcrumb-two-pane`.
#
# Runs no cell, so `FakeKernelGateway` is never asked anything.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook + window_width_dp

# The measured width names the evidence directory, so an 800dp run cannot land in a 320dp one.
WIDTH_DP="$(window_width_dp)"
echo "=== the breadcrumb at ${WIDTH_DP}dp ==="

reset_notebooks
# The browse fixture's own workspace: a notebook at the root with no ancestry to draw, one inside
# a folder that has some, and a file beside it so the folder is not a notebook-shaped special case.
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "At Root"
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/notebook-breadcrumb-trail.yaml" \
  "$ROOT/evidence/notebook-breadcrumb-trail/${WIDTH_DP}dp"
