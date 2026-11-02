#!/usr/bin/env bash
# The gate: once a breakpoint has genuinely paused a real kernel, the debugger's side
# panel shows the real call stack and the real top-frame variables a live `debug_reply` answered.
#
# Reuses `debugger-stop-driver.sh`'s own two flows to reach that paused state - the breakpoint
# arm/run and the real `sleep` settle it measured necessary - rather than duplicating either,
# then runs this row's own flow against the same fixture and evidence directory.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# whose attach/setBreakpoints/configurationDone are no-ops with nothing to pause a fake execute -
# the cell would run straight through and there would be no real stop for this flow to open a
# panel on. scripts/run-all-flows.sh reads this marker and skips the flow rather than failing it.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/debugger-stop.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/debugger-variables-panel"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"
SETTLE_WAIT=20

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Stop"

flow_status=0
run_flow "$ROOT/.maestro/debugger-stops-at-breakpoint.yaml" "$EVIDENCE" || flow_status=$?

if [ "$flow_status" -eq 0 ]; then
  echo "waiting ${SETTLE_WAIT}s for the debug handshake and the breakpoint hit..."
  sleep "$SETTLE_WAIT"
  run_flow "$ROOT/.maestro/debugger-stopped-at-breakpoint-settled.yaml" "$EVIDENCE" || flow_status=$?
fi

if [ "$flow_status" -eq 0 ]; then
  run_flow "$ROOT/.maestro/debugger-variables.yaml" "$EVIDENCE" || flow_status=$?
fi

# Pulled whether or not the flow passed, for debugger-stop-driver.sh's own reason: a
# stalled or crashed run is exactly when this file is the evidence.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
