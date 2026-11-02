#!/usr/bin/env bash
# The wide half: the "Sessions and Tabs" side panel beside the document - three sections, a
# kernel session row that opens its notebook and ends it (the open document lands on the
# reference's own No Kernel), a terminal row that outlives and reattaches its tab, and the
# section toolbars' confirmed bulk closes, the dirty one through the shared gate.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# Runs a real cell, so the arm64 tablet is where this belongs - the emulator's fake kernel would
# still draw the row (the fakes share the registry) but "the kernel died and the notebook saw it"
# is only real here.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -lt 840 ]; then
  echo "skipped: this flow needs two panes and the window is ${WIDTH_DP}dp"
  exit 0
fi
echo "=== the Sessions and Tabs panel at ${WIDTH_DP}dp ==="

reset_notebooks
# `autosaveEnabled: false` - the tabs drivers' own seeding: the run's outputs mark the notebook dirty, and
# with the debounce live the close gate would find it clean 500 ms after the run.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell "run-as $APP mkdir -p files"
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
  { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
seed_notebook "$ROOT/.maestro/fixtures/running-panel-kernel.ipynb" "running-panel-kernel"
run_flow "$ROOT/.maestro/running-panel-wide.yaml" \
  "$ROOT/evidence/running-panel-wide/${WIDTH_DP}dp"
echo "the panel listed and ended a real session, reattached a terminal, and closed all tabs at ${WIDTH_DP}dp"
