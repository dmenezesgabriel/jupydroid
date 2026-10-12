#!/usr/bin/env bash
# An unsaved document beneath the top tab is still known for unsaved after the device turns.
# `second.py` is typed into with autosave off and left beneath `first.py`; the device is turned to
# portrait and back, which relaunches the activity twice in the same process; then the close on
# `second.py`'s tab must ask before it closes. Cancel keeps it, and nothing was written.
#
#   scripts/jd-030-rotation-t1-driver.sh
#
# No cell runs, so any device will do; the tablet's turn is the one that crosses two panes to one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-030-rotation/t1"
FLOWS="$ROOT/.maestro/jd-030-rotation-t1"
FIRST="first.py"
SECOND="second.py"
export MAESTRO_FIRST="$FIRST" MAESTRO_SECOND="$SECOND"
mapfile -t adb < <(device_adb)

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "${adb[@]}" shell settings put system accelerometer_rotation 0
  "${adb[@]}" shell settings put system user_rotation "$1"
  sleep 5
}
restore_rotation() { "${adb[@]}" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT
app_pid() { "${adb[@]}" shell pidof "$APP" | tr -d '\r'; }

mkdir -p "$EVIDENCE"
reset_notebooks
# Autosave off, base64 for `p6c-8-close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-030-first.py" "$FIRST"
seed_file "$ROOT/.maestro/fixtures/jd-030-second.py" "$SECOND"
rotate_to 1

run_flow "$FLOWS-part1-before.yaml" "$EVIDENCE/before"
before="$(app_pid)"
rotate_to 0
run_flow "$FLOWS-portrait.yaml" "$EVIDENCE/portrait"
rotate_to 1
run_flow "$FLOWS-part2-after.yaml" "$EVIDENCE/after"

# A relaunch of the activity, not of the process: a new process would rebuild every document.
after="$(app_pid)"
echo "app pid before the turn: $before, after: $after" | tee "$EVIDENCE/disk.txt"
[ "$before" = "$after" ] || { echo "the process was restarted; the turn measured nothing" >&2; exit 1; }
disk="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$SECOND'" | tr -d '\r')"
echo "$SECOND on disk after Cancel: $disk" | tee -a "$EVIDENCE/disk.txt"
[ "$disk" = 'second = "typed into"' ] || { echo "Cancel wrote the owed line" >&2; exit 1; }
