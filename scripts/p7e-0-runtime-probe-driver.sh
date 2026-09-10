#!/usr/bin/env bash
# P7e-0: measure the Space's runtime before a line of P7e is written.
#
# TABLET ONLY (SM-X216B). Every cell here runs against the kernel, and on x86_64 `MainActivity`
# falls back to `FakeKernelGateway`, which echoes a cell's source back as its output - so on an
# emulator this probe would "answer" all six questions with the questions themselves. The driver
# refuses to run anywhere else rather than producing evidence that looks fine and says nothing.
#
# **The flow is not the oracle here; the saved notebook is.** Outputs are pulled off the device
# after the run and committed verbatim, because a screenshot of a traceback is a picture of the
# answer and P7e-3 is written from the answer itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P7e-0 must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

OUT="$ROOT/evidence/p7e-0-runtime-probe"
echo "=== probing the Space's runtime on $MODEL ==="

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p7e-0-runtime-probe.ipynb" "Probe"
# **The status is kept and the pull happens either way**, which is this driver's own first defect:
# `run_flow` propagates a failed flow, `set -e` killed the driver at that line, and the run's
# answers - already written to the notebook on the device - were never fetched. A probe whose
# evidence survives only a perfect flow is not a probe.
NOTEBOOK_STATUS=0
run_flow "$ROOT/.maestro/p7e-0-runtime-probe.yaml" "$OUT" || NOTEBOOK_STATUS=$?

# The answers themselves. `run-as` reads the app's own data dir on a debuggable build, which is
# where the notebook the kernel just wrote lives.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} \
  shell "run-as $APP cat '$NOTEBOOK_DIR/Probe.ipynb'" > "$OUT/probe.ipynb"
python3 "$ROOT/scripts/read_notebook_outputs.py" "$OUT/probe.ipynb" > "$OUT/probe-output.txt"
echo "--- what the Space answered ---"
cat "$OUT/probe-output.txt"

# The shell's half, into the same directory: two surfaces, one measurement. It runs second because
# it opens a terminal, and a terminal keeps the server alive (P4k) - which would change what the
# notebook half is measuring if it ran first.
TERMINAL_STATUS=0
run_flow "$ROOT/.maestro/p7e-0-terminal-probe.yaml" "$OUT" || TERMINAL_STATUS=$?

# Reported at the end rather than swallowed: the answers are captured whatever the flows did, and
# a flow that failed is still a failure.
exit $(( NOTEBOOK_STATUS > TERMINAL_STATUS ? NOTEBOOK_STATUS : TERMINAL_STATUS ))
