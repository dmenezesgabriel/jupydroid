#!/usr/bin/env bash
# What a runaway output costs before any cap: a trivial print as the control, 200k printed lines
# folded into one stream, and 300 display() calls as 300 outputs - each timed from Run to the
# execution count, with the frames drawn and the memory held across that run.
#
#   scripts/jd-015-output-caps-baseline-driver.sh [control|print|display ...]
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-015-output-caps}/baseline"
mapfile -t DEVICE < <(device_adb)

# One measurement of the notebook [title], already seeded: open it untimed, reset the frame
# statistics, then time the run alone.
measure() {
  local title="$1" out="$EVIDENCE/$2"
  export MAESTRO_NOTEBOOK="$title"
  run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$out/open"
  "${DEVICE[@]}" shell dumpsys gfxinfo "$APP" reset >/dev/null
  local started ended status=0
  started="$(date +%s.%N)"
  run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-run.yaml" "$out/run" || status=$?
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
      seed_notebook "$ROOT/.maestro/fixtures/jd-015-output-caps-control.ipynb" "Output Control"
      measure "Output Control" control
      ;;
    print)
      reset_notebooks
      seed_notebook "$ROOT/.maestro/fixtures/jd-015-output-caps-print.ipynb" "Runaway Print"
      measure "Runaway Print" print
      ;;
    display)
      reset_notebooks
      seed_notebook "$ROOT/.maestro/fixtures/jd-015-output-caps-display.ipynb" "Runaway Display"
      measure "Runaway Display" display
      ;;
    *) echo "unknown case: $case (expected control, print or display)" >&2; exit 1 ;;
  esac
done
