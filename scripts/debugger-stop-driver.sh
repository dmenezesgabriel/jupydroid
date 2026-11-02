#!/usr/bin/env bash
# The gate: a breakpoint armed in the gutter pauses a real kernel before the line it
# is on runs. Seeds a two-line cell (`x = 41` / `print(x + 1)`) so the value the second line would
# print - 42 - never appears anywhere in the source, and a real pause is what withholds it.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# whose attach/setBreakpoints/configurationDone are no-ops with nothing to pause a fake execute -
# the cell would run straight through to 42. scripts/run-all-flows.sh reads this marker and skips
# the flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/debugger-stop.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/debugger-stop"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"

# The handshake (attach/dumpCell/setBreakpoints/configurationDone) and the breakpoint hit happen
# off screen once Run is tapped, and nothing textual marks either finishing - the breakpoint dot
# turning verified and the stopped line's own wash are both canvas-drawn (screenshot evidence, not
# an assertion, per BreakpointGutterTapTest's own precedent). Waiting for them was tried first with
# `waitForAnimationToEnd` against the running cell's own spinner and measured unreliable: timed
# against a 1000ms vs a 14000ms timeout, wall clock differed by ~7.6s rather than the ~13s a
# faithful wait would cost, and two on-device runs at 20000ms and 60000ms both still caught the
# breakpoint grey and the line unhighlighted. So the wait is a real one, in bash, between two
# flows - the same shape P1c's own driver uses for its 45s idle-grace wait.
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

# Pulled whether or not the flow passed, for the same reason real-kernel-driver.sh pulls
# it: a stalled or crashed run is exactly when this file is the evidence, and it is redirected
# here rather than surfacing as cell output (JupyterServerLauncher).
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
