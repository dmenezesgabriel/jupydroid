#!/usr/bin/env bash
# The gate: a condition attached to a breakpoint actually gates whether the kernel
# stops - a plain breakpoint on this fixture's own loop would stop at i == 0.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# which has no condition evaluator of its own to prove anything against.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/debugger-conditional-loop.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/conditional-breakpoint"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Conditional Loop"

flow_status=0
run_flow "$ROOT/.maestro/conditional-breakpoint.yaml" "$EVIDENCE" || flow_status=$?

# Pulled whether or not the flow passed, for the same reason every other debugger driver pulls
# it: a stalled or crashed run is exactly when this file is the evidence. Also the one place that
# proves the condition reached the kernel over the wire, not just this app's own state.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
