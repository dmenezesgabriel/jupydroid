#!/usr/bin/env bash
# P8e's narrow half: the tab strip over the one pane the detail gets below two panes - three
# documents become three tabs, a tap reveals, a close leaves the rest, and a dirty close goes
# through Save/Discard/Cancel.
# NARROW WINDOW (<840dp): the 320dp emulator, or the tablet in portrait.
#
# Runs no cell, so the emulator is evidence rather than a stand-in.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -ge 840 ]; then
  echo "skipped: this flow is the one-pane half and the window is ${WIDTH_DP}dp"
  exit 0
fi
echo "=== the tab strip at ${WIDTH_DP}dp ==="

reset_notebooks
# `autosaveEnabled: false`, seeded the way p6c-9-dirty-marker-driver.sh seeds it - the dirty arm
# tracks `dirty`, and the debounce would clear it 500 ms after the last keystroke otherwise.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell "run-as $APP mkdir -p files"
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
  { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
# `reports/`, not the root: a root document draws no crumbs, and at one pane the crumb is the way
# back to the browser that keeps the tab open (the bar's Back pops the document).
seed_file "$ROOT/.maestro/fixtures/p8e-first.ipynb" "reports/p8e-first.ipynb"
seed_file "$ROOT/.maestro/fixtures/p8e-second.ipynb" "reports/p8e-second.ipynb"
seed_file "$ROOT/.maestro/fixtures/p8e-notes.txt" "reports/p8e-notes.txt"
run_flow "$ROOT/.maestro/p8e-tabs-narrow.yaml" \
  "$ROOT/evidence/p8e-tabs/${WIDTH_DP}dp"
echo "three documents were three tabs; reveal, close and the dirty gate all held at ${WIDTH_DP}dp"
