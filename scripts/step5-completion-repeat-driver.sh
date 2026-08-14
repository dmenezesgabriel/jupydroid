#!/usr/bin/env bash
# Runs the completion flow many times over, because the defect it guards is intermittent.
#
# TABLET ONLY (SM-X216B). It runs a cell, and on x86_64 MainActivity falls back to
# FakeKernelGateway, which answers from the source rather than from a kernel.
#
# **Why a repeat driver exists at all.** The execution stall appeared roughly once in twenty runs
# over seven sightings, so a single green flow was never evidence either way - that is exactly how
# it survived three investigations. This is what makes "it stopped happening" a measurement.
#
# It reports three outcomes, not two. Since P0b an unanswered greeting restarts the kernel and
# greets the new one, so a stall now ends in a *passing* run - and a driver that only counted
# pass/fail could not tell "no stall occurred" from "a stall occurred and was recovered from",
# which are the two readings this instrument exists to separate. `R` is the second.
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
recovered=0

# Everything the kernel and the server said during one run, kept beside each other.
#
# Pulled for a *pass* as well as a failure, which a driver written before P0b did not need to do.
# The recovery restarts the kernel on an unanswered greeting, so a stall that used to be a failed
# run is now a passed one - and capturing only failures would throw away the very run that shows
# the fix working. "Passed" and "passed after recovering" are different results.
capture_run() {
  local run="$1" why="$2"
  grep -oE '\{"stage":"kernel[^}]*\}' "$(newest_run_dir step5-completion)/logs/device-logcat.txt" \
    > "$ROOT/build/flow-logs/repeat-$run-$why-kernel.jsonl" || true
  # The server's half, which is the one no sighting before the seventh ever had, and which since
  # P0a carries the kernel's own `*** MESSAGE TYPE:...***` per shell dispatch - so a stall says
  # whether the request reached the kernel at all.
  "$ADB" shell run-as "$APP" cat files/spaces/base/run/jupyter_server.log \
    > "$ROOT/build/flow-logs/repeat-$run-$why-server.log" 2>/dev/null || true
}

for run in $(seq 1 "$RUNS"); do
  reset_notebooks
  seed_notebook "$ROOT/.maestro/fixtures/step5-completion.ipynb" "Completion"
  if maestro_test "$ROOT/.maestro/step5-completion.yaml" >"$ROOT/build/flow-logs/repeat-$run.log" 2>&1; then
    # `R` rather than `.`: the run passed *because* the kernel was replaced, which is the stall
    # happening and being recovered from. Counting it as a plain pass would hide the rate.
    if grep -q 'kernel.greet.restarting' "$(newest_run_dir step5-completion)/logs/device-logcat.txt"; then
      recovered=$((recovered + 1))
      printf 'R'
      capture_run "$run" recovered
    else
      printf '.'
    fi
    passed=$((passed + 1))
  else
    failed=$((failed + 1))
    printf 'F'
    capture_run "$run" failed
  fi
done

echo
echo "passed $passed (of which $recovered recovered from a stall) · failed $failed of $RUNS"
[ "$failed" -eq 0 ]
