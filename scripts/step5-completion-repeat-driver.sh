#!/usr/bin/env bash
# Runs the completion flow many times over, because the defect it guards is intermittent.
#
# TABLET ONLY (SM-X216B). It runs a cell, and on x86_64 MainActivity falls back to
# FakeKernelGateway, which answers from the source rather than from a kernel.
#
# **Why a repeat driver exists at all.** The execution stall appeared roughly once in twenty runs
# over four sightings, so a single green flow was never evidence either way - that is exactly how
# it survived three investigations. Holding `execute_request` until the socket reports `Opened`
# (RealKernelGateway) is the fix; this is what makes "it stopped happening" a measurement.
#
#   ANDROID_SERIAL=TABLET_SERIAL scripts/step5-completion-repeat-driver.sh 30
#
# Every run is a fresh launch with a fresh kernel, which is the state all four sightings were in.
# NOT A SUITE FLOW. `scripts/run-all-flows.sh` skips this: it is a measuring instrument, not a
# check, and a sweep that ran it would quietly grow by twenty launches of the same flow.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNS="${1:-20}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

passed=0
failed=0
for run in $(seq 1 "$RUNS"); do
  reset_notebooks
  seed_notebook "$ROOT/.maestro/fixtures/step5-completion.ipynb" "Completion"
  if maestro_test "$ROOT/.maestro/step5-completion.yaml" >"$ROOT/build/flow-logs/repeat-$run.log" 2>&1; then
    passed=$((passed + 1))
    printf '.'
  else
    failed=$((failed + 1))
    printf 'F'
    # The kernel's own account of the failure, which is the whole point of the logging.
    grep -oE '\{"stage":"kernel[^}]*\}' "$(newest_run_dir step5-completion)/logs/device-logcat.txt" \
      > "$ROOT/build/flow-logs/repeat-$run-kernel.jsonl" || true
    # And the server's, which is the half no sighting has ever had. Ours can say "nothing came
    # back" and never which of `Nudge: attempt N`, `No such channel` or `Received message on
    # closed websocket` the server was reporting - three causes with three different fixes, and
    # all three invisible until --ServerApp.log_level=DEBUG (JupyterServerLauncher).
    "$ADB" shell run-as "$APP" cat files/spaces/base/run/jupyter_server.log \
      > "$ROOT/build/flow-logs/repeat-$run-server.log" 2>/dev/null || true
  fi
done

echo
echo "passed $passed · failed $failed of $RUNS"
[ "$failed" -eq 0 ]
