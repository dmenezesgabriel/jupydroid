#!/usr/bin/env bash
# The gate: the Recents section's collapse survives the process that set it.
# ONE PANE (<840dp): the 320dp emulator, or the tablet in portrait.
#
# The tablet is not required and this says so: two plain files are opened and no cell is run, so
# `FakeKernelGateway` is never asked anything.
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
echo "=== the collapse survives a relaunch at ${WIDTH_DP}dp ==="

# `reset_notebooks` clears `explorer-settings.json` as well, so this run starts from the
# default (shut, `ExplorerSettings.recentsCollapsed`) rather than from whatever the last flow left.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/recents-persists.yaml" \
  "$ROOT/evidence/recents-persists/${WIDTH_DP}dp"
