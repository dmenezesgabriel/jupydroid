#!/usr/bin/env bash
# An open Console draws what it drew before the activity was relaunched, and its kernel still
# answers. A Console is opened from the Launcher and answers an entry; the device is turned to
# portrait and back, which relaunches the activity twice, and the Console answers again; then the
# device is switched to dark mode, which relaunches it once more, and the Console answers a third
# time. The app's pid must be the same throughout: a new process would start with no Console.
#
#   scripts/rotation-console-driver.sh
#
# The Console runs the Space's real kernel, so this goes on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/rotation-console"
FLOWS="$ROOT/.maestro/rotation-console"

# `auto` afterwards, as `palette-driver.sh` restores it: a theme left forced would decide the
# look of every flow that runs after this one.
restore_device() {
  restore_rotation
  "${adb[@]}" shell cmd uimode night auto >/dev/null
}
trap restore_device EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
"${adb[@]}" shell cmd uimode night no >/dev/null
rotate_to 1

run_flow "$FLOWS-before.yaml" "$EVIDENCE/before"
before_pid="$(app_pid)"
rotate_to 0
# The middle of the turn is the same one-pane frame T1 took; its flow names nothing of T1's.
run_flow "$ROOT/.maestro/rotation-dirty-tab-portrait.yaml" "$EVIDENCE/portrait"
rotate_to 1
run_flow "$FLOWS-after-the-turn.yaml" "$EVIDENCE/after-the-turn"
turned_pid="$(app_pid)"
"${adb[@]}" shell cmd uimode night yes >/dev/null
sleep 5
run_flow "$FLOWS-after-dark-mode.yaml" "$EVIDENCE/after-dark-mode"
dark_pid="$(app_pid)"

# A relaunch of the activity, not of the process: a new process would start with no Console.
echo "app pid before the turn: $before_pid, after it: $turned_pid, after dark mode: $dark_pid" |
  tee "$EVIDENCE/pids.txt"
[ "$before_pid" = "$turned_pid" ] && [ "$turned_pid" = "$dark_pid" ] ||
  { echo "the process was restarted; the relaunch measured nothing" >&2; exit 1; }
