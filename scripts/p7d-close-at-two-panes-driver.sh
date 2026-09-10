#!/usr/bin/env bash
# P7d: beside a browser a document is closed, not backed out of - all three bars.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# Runs no cell, so an emulator is evidence rather than a stand-in for the tablet.
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
echo "=== closing a document at ${WIDTH_DP}dp ==="

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "Root"
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
run_flow "$ROOT/.maestro/p7d-close-at-two-panes.yaml" \
  "$ROOT/evidence/p7d-close-at-two-panes/${WIDTH_DP}dp"
