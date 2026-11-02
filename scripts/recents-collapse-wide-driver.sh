#!/usr/bin/env bash
# At two panes: the Recents section collapses while a document is open beside it.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# Runs no cell - a collapse is a presentation question and the document is a plain file - so an
# emulator is evidence rather than a stand-in for the tablet.
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
echo "=== recents collapses beside a document at ${WIDTH_DP}dp ==="

reset_notebooks
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/recents-collapse-wide.yaml" \
  "$ROOT/evidence/recents-collapse-wide"
