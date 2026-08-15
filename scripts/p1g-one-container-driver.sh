#!/usr/bin/env bash
# P1g gate: one composition root per process, whatever the platform does to the activity.
#
# A rotation relaunches `MainActivity` - the window manager says so in as many words,
# `not-handles={CONFIG_ORIENTATION}` - and while the container hung off the activity, each
# relaunch built another one with another `ServerLauncher`. Reopening a notebook then started a
# **second `jupyter_server`** beside the first, both children of the app
# (`evidence/p1g-one-container-per-process/`).
#
# So the assertion is about processes, not pixels: **the same server pid, before and after**, and
# exactly one of them. A flow cannot see that, which is why the driver reads `ps` and the flows
# only prove a kernel really answered.
#
# The last check is the one that says the fix is not merely cosmetic: P1c's backstop must still
# stop the server after a rotation. It used to - by accident, because the leaked per-activity
# observer happened to hold the launcher that owned the running process.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flows assert `54` and `56`, which only a real kernel produces.
# NOT A SUITE FLOW: it rotates the device and waits out the idle grace, ~2 minutes of wall time.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p1g-rotation.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p1g-one-container-per-process"
mkdir -p "$EVIDENCE"
IDLE_WAIT=45

server_pids() {
  "$ADB" shell "ps -A -o PID,CMDLINE | grep -- '-m jupyter_server' | grep -v grep" 2>/dev/null |
    awk '{print $1}' || true
}

app_pid() { "$ADB" shell pidof "$APP" 2>/dev/null | tr -d '\r'; }

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

reset_notebooks
seed_notebook "$FIXTURE" "Rotation"
rotate_to 1 # landscape, so the rotation below is a real configuration change

run_flow "$ROOT/.maestro/p1g-part1-run-before-rotation.yaml" "$EVIDENCE" || exit 1
BEFORE="$(server_pids)"
APP_PID="$(app_pid)"
[ "$(echo "$BEFORE" | wc -w)" -eq 1 ] || { echo "expected exactly one server, got: $BEFORE" >&2; exit 1; }
echo "one server ($BEFORE) under app $APP_PID"

rotate_to 0
run_flow "$ROOT/.maestro/p1g-part2-reopen-after-rotation.yaml" "$EVIDENCE" || exit 1

AFTER="$(server_pids)"
"$ADB" shell ps -A -o PID,PPID,RSS,CMDLINE >"$EVIDENCE/ps-after-rotation.txt" 2>/dev/null || true
[ "$(app_pid)" = "$APP_PID" ] || { echo "the app was restarted; the gate measured nothing" >&2; exit 1; }
[ "$AFTER" = "$BEFORE" ] || {
  echo "the rotation changed the server: '$BEFORE' -> '$AFTER'" >&2
  echo "two pids means a second container built a second server; see ps-after-rotation.txt" >&2
  exit 1
}
echo "same single server after the rotation and a reopened notebook"

# P1c still holds on the other side of a relaunch.
"$ADB" shell input keyevent KEYCODE_HOME
echo "waiting ${IDLE_WAIT}s for the backstop, now that the observer is the process's own..."
sleep "$IDLE_WAIT"
"$ADB" shell ps -A -o PID,PPID,RSS,CMDLINE >"$EVIDENCE/ps-after-idle.txt" 2>/dev/null || true
[ -z "$(server_pids)" ] || { echo "the backstop did not fire after a rotation: $(server_pids)" >&2; exit 1; }
[ "$(app_pid)" = "$APP_PID" ] || { echo "the app died rather than being backgrounded" >&2; exit 1; }

echo "P1g gate passed: one container per process - one server across a rotation, and the backstop still stops it."
