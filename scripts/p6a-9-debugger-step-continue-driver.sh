#!/usr/bin/env bash
# The gate for P6a-9: a breakpoint pauses a real kernel (p6a-9-debugger-arms-and-runs.yaml +
# p6a-9-debugger-stopped-settled.yaml, the same two-part shape p6a-7b-debugger-stop-driver.sh
# already established), then Step Over moves it to the next line and Continue resumes it to
# completion (p6a-9-debugger-step-continue.yaml) - the reverse of p6a-7b's own assertion that
# nothing sends `continue` yet.
#
# Its own fixture (.maestro/fixtures/p6a-9-step.ipynb) rather than p6a-7b's shared one: a third
# line (`print(y)`) gives Step Over somewhere to land before Continue finishes the run, and
# touching the shared fixture would risk p6a-7b/p6a-8's own already-green line/value assertions.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# whose attach/setBreakpoints/configurationDone are no-ops with nothing to pause a fake execute -
# the cell would run straight through to 42 immediately.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p6a-9-step.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/p6a-9"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"

# Measured necessary by p6a-7b-debugger-stop-driver.sh: `waitForAnimationToEnd` against the
# canvas-drawn breakpoint dot/line wash is unreliable, so the settle between arming/running and
# reading that state is a real shell sleep, not a Maestro wait. The step-continue flow itself
# needs none of this - see that file's own header for why.
SETTLE_WAIT=20

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Step"

flow_status=0
run_flow "$ROOT/.maestro/p6a-9-debugger-arms-and-runs.yaml" "$EVIDENCE" || flow_status=$?

if [ "$flow_status" -eq 0 ]; then
  echo "waiting ${SETTLE_WAIT}s for the debug handshake and the breakpoint hit..."
  sleep "$SETTLE_WAIT"
  run_flow "$ROOT/.maestro/p6a-9-debugger-stopped-settled.yaml" "$EVIDENCE" || flow_status=$?
fi

if [ "$flow_status" -eq 0 ]; then
  run_flow "$ROOT/.maestro/p6a-9-debugger-step-continue.yaml" "$EVIDENCE" || flow_status=$?
fi

# Pulled whether or not the flow passed, for p6a-7b-debugger-stop-driver.sh's own reason: a
# stalled or crashed run is exactly when this file is the evidence.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
