#!/usr/bin/env bash
# P8f's narrow half: the same panel where the window is the pane - reached from the bottom bar,
# its session rows opening documents over it, the Workspace crumb returning beneath them (the
# strip's Up arrow closes, the crumb does not). The terminal half proves the reference's own
# twist: a closed tab leaves the shell running, and the row reattaches to it.
# NARROW WINDOW (<840dp): the 320dp emulator, or the tablet in portrait.
#
# The emulator is evidence here even though a cell runs: FakeKernelGateway echoes, but the fake
# gateway and the fake session list share the app's one KernelSessionRegistry, so the panel's
# whole loop - run -> row -> shut down -> the open document lands on No Kernel - is real.
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
echo "=== the Sessions and Tabs panel at ${WIDTH_DP}dp ==="

reset_notebooks
# `autosaveEnabled: false`, for the same reason as the wide driver: two dirty closes are what the
# gate's queue is being proved on.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell "run-as $APP mkdir -p files"
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
  { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
# `reports/`, not the root: a root document draws no crumbs, and at one pane the crumb is the way
# back to the bar that keeps the tab open (the strip's Up closes it).
seed_file "$ROOT/.maestro/fixtures/p8f-kernel.ipynb" "reports/p8f-kernel.ipynb"
seed_file "$ROOT/.maestro/fixtures/p8f-kernel.ipynb" "reports/p8f-second.ipynb"
run_flow "$ROOT/.maestro/p8f-running-panel-narrow.yaml" \
  "$ROOT/evidence/p8f-running-panel/${WIDTH_DP}dp"
echo "the bar reached the panel, two sessions shut down by count, and a terminal outlived its tab at ${WIDTH_DP}dp"
