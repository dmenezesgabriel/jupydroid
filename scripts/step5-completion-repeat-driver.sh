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
  seed_notebook "$ROOT/.maestro/fixtures/step5-completion.ipynb" step5-completion
  if maestro_test "$ROOT/.maestro/step5-completion.yaml" >"$ROOT/build/flow-logs/repeat-$run.log" 2>&1; then
    passed=$((passed + 1))
    printf '.'
  else
    failed=$((failed + 1))
    printf 'F'
    # The kernel's own account of the failure, which is the whole point of the logging.
    grep -oE '\{"stage":"kernel[^}]*\}' "$(newest_run_dir step5-completion)/logs/device-logcat.txt" \
      > "$ROOT/build/flow-logs/repeat-$run-kernel.jsonl" || true
  fi
done

echo
echo "passed $passed · failed $failed of $RUNS"
[ "$failed" -eq 0 ]
