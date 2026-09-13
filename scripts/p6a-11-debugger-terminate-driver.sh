#!/usr/bin/env bash
# The gate for P6a-11: Terminate is the escape hatch Interrupt cannot be for a kernel paused under
# the debugger - P6a-10's own investigation found eleven repeated interrupts over two minutes
# never freed a genuinely stopped session on this same tablet.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator MainActivity falls back to FakeKernelDebugger,
# which never genuinely pauses anything for Terminate to free.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p6a-9-step.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/p6a-11"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"

reset_notebooks
seed_notebook "$FIXTURE" "Debugger Step"

flow_status=0
run_flow "$ROOT/.maestro/p6a-11-debugger-terminate.yaml" "$EVIDENCE" || flow_status=$?

# Pulled whether or not the flow passed, for the same reason every other debugger driver pulls
# it: a stalled or crashed run is exactly when this file is the evidence.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

exit "$flow_status"
