#!/usr/bin/env bash
# The half of `notebook-breadcrumb-trail` only two panes can reach: a crumb has to bring the browser back before it can
# move it, because the side area may be drawing the table of contents instead.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# Runs no cell - a breadcrumb is a layout and navigation question.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -lt 840 ]; then
  echo "skipped: this flow needs two panes and the window is ${WIDTH_DP}dp"
  exit 0
fi

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "At Root"
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/notebook-breadcrumb-two-pane.yaml" "$ROOT/evidence/notebook-breadcrumb-two-pane"
