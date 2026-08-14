#!/usr/bin/env bash
# The gate for "tap Run on a code cell and the kernel answers", in both directions: the value
# on screen came from the kernel, and the kernel's log of dispatching the request is in the
# server's log file and not in the notebook.
#
# Seeds a notebook that actually has cells. A notebook created from the FAB has none
# (DiskNotebookRepository.createNotebook), so it would only ever prove the "No cells yet"
# branch. The seeded file lands in the app's own storage via run-as (the debug build is
# debuggable; no root needed), and DiskNotebookRepository scans rootDir in its init block, so a
# fresh launch picks it up.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flow asserts `10.000000000000002` - a float only CPython produces. On
# the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source
# back as stdout, so the run proves nothing. scripts/run-all-flows.sh reads this marker and skips
# the flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/step10-real-kernel.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # $ADB, $APP, reset_notebooks, seed_notebook, run_flow

EVIDENCE="$ROOT/evidence/kernel-observability/shell-dispatch-logged"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"
# ipykernel's dispatch_shell, at DEBUG (kernelbase.py:455), matched with grep -F: the line is
# full of `*`, and as a regex it would mean something else entirely.
DISPATCHED='*** MESSAGE TYPE:execute_request***'

reset_notebooks
seed_notebook "$FIXTURE" "Real Kernel"

flow_status=0
run_flow "$ROOT/.maestro/step10-run-cell-real-kernel.yaml" "$EVIDENCE" || flow_status=$?

# Pulled whether or not the flow passed, because a stalled run is exactly when this file is the
# evidence: the kernel inherits the server's stderr (jupyter_client/launcher.py:68-76 leaves it
# None off Windows) and the server's output is redirected here (JupyterServerLauncher), so this
# is where the kernel's own account of the shell channel lands.
"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

# The one line that separates the two causes of the execution stall (docs/PLAN.md P0): present,
# the kernel heard the request and did not reply; absent, the request never reached it. The
# server cannot answer that - its nudge is satisfied by a *control* reply alone
# (jupyter_server/services/kernels/connection/channels.py:158-164), so a dead shell channel looks
# healthy to it. Asserted on a passing run too, so the diagnostic is known to work before the
# next sighting rather than during it.
if ! grep -qF "$DISPATCHED" "$EVIDENCE/jupyter_server.log"; then
  echo "the kernel logged no shell dispatch for execute_request in $SERVER_LOG."
  echo "is --log-level=DEBUG still in the kernelspec argv (SpaceKernelSpec)?"
  exit 1
fi

exit "$flow_status"
