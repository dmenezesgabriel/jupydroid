#!/usr/bin/env bash
# The gate: a cell whose breakpoint was armed, hit, continued past and then disarmed
# must still run cleanly a second and third time on the same kernel - the exact regression a real
# tablet run found (debugpy rejects a repeated `attach`/`initialize`, and a disarmed breakpoint
# this app never told debugpy to clear stayed armed forever, hanging every later run of that cell
# with nothing in the app ever noticing).
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# whose attach/setBreakpoints are no-ops - there is no real debugpy session to re-attach to.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/debugger-step.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/debugger-reattach"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Step"

flow_status=0
run_flow "$ROOT/.maestro/debugger-reattach-after-clear.yaml" "$EVIDENCE" || flow_status=$?

# Pulled whether or not the flow passed, for the same reason every other debugger driver pulls
# it: a stalled or crashed run is exactly when this file is the evidence.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
