#!/usr/bin/env bash
# The gate: an ad hoc expression typed while stopped runs against the real kernel's own
# top frame, not this app's own state echoing something already on screen.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# which has no real interpreter behind it to evaluate anything against.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/debugger-step.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/debugger-evaluate"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Step"

flow_status=0
run_flow "$ROOT/.maestro/debugger-evaluate.yaml" "$EVIDENCE" || flow_status=$?

# Pulled whether or not the flow passed - the one place that proves the expression reached the
# kernel over the wire, not just this app's own state.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
