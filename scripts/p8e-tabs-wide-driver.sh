#!/usr/bin/env bash
# P8e's wide half: the tab strip over the detail pane beside the browser - the launcher fill is
# a tab of its own, three documents join it, a tap reveals, a close leaves the rest, and a dirty
# close goes through Save/Discard/Cancel.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# Runs no cell, so an emulator at this width is evidence rather than a stand-in.
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
echo "=== the tab strip at ${WIDTH_DP}dp ==="

reset_notebooks
# `autosaveEnabled: false` - see p8e-tabs-narrow-driver.sh's note.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell "run-as $APP mkdir -p files"
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
  { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
seed_notebook "$ROOT/.maestro/fixtures/p8e-first.ipynb" "p8e-first"
seed_notebook "$ROOT/.maestro/fixtures/p8e-second.ipynb" "p8e-second"
seed_file "$ROOT/.maestro/fixtures/p8e-notes.txt" "p8e-notes.txt"
run_flow "$ROOT/.maestro/p8e-tabs-wide.yaml" \
  "$ROOT/evidence/p8e-tabs/${WIDTH_DP}dp"
echo "three documents were three tabs; reveal, close and the dirty gate all held at ${WIDTH_DP}dp"
