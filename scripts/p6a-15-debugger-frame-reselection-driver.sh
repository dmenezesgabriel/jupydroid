#!/usr/bin/env bash
# The gate for P6a-15: a real, two-frame call stack (not FakeKernelDebugger's single hardcoded
# frame, and not p6a-9-step.ipynb's own flat cell, which never calls anything) reselects onto its
# caller frame on tap, replacing the callee's own scopes/variables with the caller's.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# which never genuinely pauses anything for a frame tap to reselect.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p6a-15-nested-call.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/p6a-15"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Nested Call"

flow_status=0
run_flow "$ROOT/.maestro/p6a-15-debugger-frame-reselection.yaml" "$EVIDENCE" || flow_status=$?

# Pulled whether or not the flow passed, for the same reason every other debugger driver pulls
# it: a stalled or crashed run is exactly when this file is the evidence.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
