#!/usr/bin/env bash
# The error log .maestro/site-troubleshooting-shots.yaml shoots: emptied first so the count is
# this run's alone - the log lives in the app's external files dir, which reset_notebooks never
# reaches - then one real crash, so Diagnostics counts entries the app's own handlers wrote
# (diagnostics-clear-driver.sh's `am crash`, the mechanism it proves). The flow's own launch is
# what records the exit half of the pair.
# Run by scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

"$ADB" shell "rm -f /sdcard/Android/data/$APP/files/errors-*.jsonl"
"$ADB" shell am start -n "$APP/.BlueOrbitIcon" > /dev/null
# The crash handler installs in Application.onCreate, so the wait is for the process, not a screen.
sleep 8
"$ADB" shell am crash "$APP"
sleep 3
