#!/usr/bin/env bash
# P7d's other half: below two panes the arrow stays, because there the document is the window.
# ONE PANE (<840dp): the 320dp emulator, or the tablet in portrait.
#
# Runs no cell.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -ge 840 ]; then
  echo "skipped: this flow is the one-pane half and the window is ${WIDTH_DP}dp"
  exit 0
fi
echo "=== going back at ${WIDTH_DP}dp ==="

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "Root"
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
run_flow "$ROOT/.maestro/p7d-back-at-one-pane.yaml" \
  "$ROOT/evidence/p7d-close-at-two-panes/${WIDTH_DP}dp"
