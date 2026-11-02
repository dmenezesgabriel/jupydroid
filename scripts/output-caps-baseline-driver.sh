#!/usr/bin/env bash
# What a runaway output costs before any cap: a trivial print as the control, 200k printed lines
# folded into one stream, and 300 display() calls as 300 outputs - each timed from Run to the
# execution count, with the frames drawn and the memory held across that run.
#
#   scripts/output-caps-baseline-driver.sh [control|print|scrolled|display ...]
#   PROFILE_AFTER=90 scripts/output-caps-baseline-driver.sh print   # plus a CPU profile
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/output-caps}/baseline"
# The flow that runs the cell and reads the result; a task's driver names its own.
RUN_FLOW="${RUN_FLOW:-$ROOT/.maestro/output-caps-baseline-run.yaml}"
mapfile -t DEVICE < <(device_adb)

# Where the app's CPU goes, [PROFILE_AFTER] seconds into the run: an 8 s simpleperf sample, which a
# debuggable build allows without root. Flat by symbol, then the app's own frames by inclusive cost -
# the second is what says whether our code or the platform's is the one burning the time.
profile_app() {
  local out="$1"
  sleep "$PROFILE_AFTER"
  "${DEVICE[@]}" shell "simpleperf record --app $APP --duration 8 -f 1000 -g -o /data/local/tmp/jd015.data" >/dev/null 2>&1
  {
    echo "== by symbol, self"
    "${DEVICE[@]}" shell "simpleperf report -i /data/local/tmp/jd015.data --sort symbol -n --percent-limit 1"
    echo "== app frames, inclusive"
    "${DEVICE[@]}" shell "simpleperf report -i /data/local/tmp/jd015.data --children --sort symbol --percent-limit 0.5" |
      grep -E "com\.jupydroid|Children" || echo "(no app frame at or above 0.5%)"
  } >"$out/profile.txt" 2>&1
}

# One measurement of the notebook [title], already seeded: open it untimed, reset the frame
# statistics, then time the run alone.
measure() {
  local title="$1" out="$EVIDENCE/$2"
  export MAESTRO_NOTEBOOK="$title"
  run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$out/open"
  "${DEVICE[@]}" shell dumpsys gfxinfo "$APP" reset >/dev/null
  local started ended status=0
  started="$(date +%s.%N)"
  if [ -n "${PROFILE_AFTER:-}" ]; then
    run_flow "$RUN_FLOW" "$out/run" &
    local flow_pid=$!
    profile_app "$out"
    wait "$flow_pid" || status=$?
  else
    run_flow "$RUN_FLOW" "$out/run" || status=$?
  fi
  ended="$(date +%s.%N)"
  "${DEVICE[@]}" exec-out screencap -p >"$out/run/04-when-measured.png"
  {
    echo "flow status   $status"
    echo "run seconds   $(echo "$ended - $started" | bc)"
    # The app answers gfxinfo from its own process, so a UI thread that is still folding output
    # answers nothing - which is a measurement too, not a reason to lose the rest of this file.
    timeout 30 "${DEVICE[@]}" shell dumpsys gfxinfo "$APP" |
      grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile" ||
      echo "gfxinfo       no answer from the app within 30 s"
    timeout 30 "${DEVICE[@]}" shell dumpsys meminfo "$APP" | grep -E "TOTAL PSS" || echo "meminfo       no answer"
    echo "app cpu       $("${DEVICE[@]}" shell top -b -n1 | grep " $APP\$" | awk '{print $9 "% now, " $11 " cumulative"}')"
    echo "ANR lines     $(grep -c "ANR in $APP" "$out/run/logcat.txt" || true)"
  } | tee "$out/measured.txt"
}

# Named cases on the command line run alone - `... print` re-measures one after a fix.
CASES=("$@")
[ "${#CASES[@]}" -gt 0 ] || CASES=(control print display)
for case in "${CASES[@]}"; do
  case "$case" in
    control)
      reset_notebooks
      seed_notebook "$ROOT/.maestro/fixtures/output-caps-control.ipynb" "Output Control"
      measure "Output Control" control
      ;;
    print)
      reset_notebooks
      seed_notebook "$ROOT/.maestro/fixtures/output-caps-print.ipynb" "Runaway Print"
      measure "Runaway Print" print
      ;;
    scrolled)
      reset_notebooks
      seed_notebook "$ROOT/.maestro/fixtures/output-caps-print-scrolled.ipynb" "Runaway Scrolled"
      measure "Runaway Scrolled" scrolled
      ;;
    display)
      reset_notebooks
      seed_notebook "$ROOT/.maestro/fixtures/output-caps-display.ipynb" "Runaway Display"
      measure "Runaway Display" display
      ;;
    *) echo "unknown case: $case (expected control, print, scrolled or display)" >&2; exit 1 ;;
  esac
done
