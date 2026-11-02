#!/usr/bin/env bash
# The Recents section collapses from its heading, and Clear sits behind its overflow.
# ONE PANE (<840dp): the 320dp emulator, or the tablet in portrait.
#
# The tablet is not required and this says so: the flow opens two plain files and runs no cell, so
# `FakeKernelGateway` is never asked anything and an emulator proves the same thing a device would.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_file + window_width_dp

# The measured width names the evidence directory, so a two-pane run cannot land in a one-pane one.
WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -ge 840 ]; then
  echo "skipped: this flow is the one-pane half and the window is ${WIDTH_DP}dp"
  exit 0
fi
echo "=== recents collapses at ${WIDTH_DP}dp ==="

reset_notebooks
# Two documents in two places: a section with one row could be hidden by anything, and a row inside
# a folder is what makes the path a recent carries visible in the frame.
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/recents-collapse-narrow.yaml" \
  "$ROOT/evidence/recents-collapse-narrow/${WIDTH_DP}dp"
