#!/usr/bin/env bash
# The gate for P6a-19: at one-pane width, DebuggerControlBar's own toggle - not the "Notebook
# actions" overflow - reaches the call stack and variables, drawn as a non-modal sheet docked
# above the control bar rather than a ModalBottomSheet blocking it
# (p6a-19-debugger-variables-toggle-one-pane.yaml + p6a-19-debugger-variables-toggle-stopped.yaml,
# the same two-part shape p6a-9-debugger-step-continue-driver.sh already established).
#
# Reuses p6a-9's own fixture (.maestro/fixtures/p6a-9-step.ipynb) - the same three-line
# `x = 41` / `y = x + 1` / `print(y)` cell, so this proves the fix against exactly the stop
# p6a-18's own tablet-portrait reproduction already measured, not a fresh fixture that could
# disagree with it by accident.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# whose attach/setBreakpoints/configurationDone are no-ops with nothing to pause a fake execute.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p6a-9-step.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p6a-19"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"
SETTLE_WAIT=20

# The same rotation one-container-driver.sh established: the accelerometer off first, or the
# device turns itself back the moment it is set down.
rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 3
}

# shellcheck disable=SC2329 # invoked by the EXIT trap below
restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Step"
rotate_to 0 # portrait: 800dp, one pane - see evidence/p6a-18/portrait/window-size.txt

flow_status=0
run_flow "$ROOT/.maestro/p6a-19-debugger-variables-toggle-one-pane.yaml" "$EVIDENCE" || flow_status=$?

if [ "$flow_status" -eq 0 ]; then
  echo "waiting ${SETTLE_WAIT}s for the debug handshake and the breakpoint hit..."
  sleep "$SETTLE_WAIT"
  run_flow "$ROOT/.maestro/p6a-19-debugger-variables-toggle-stopped.yaml" "$EVIDENCE" || flow_status=$?
fi

# Pulled whether or not the flow passed - a stalled or crashed run is exactly when this matters.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
