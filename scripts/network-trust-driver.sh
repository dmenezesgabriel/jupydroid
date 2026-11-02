#!/usr/bin/env bash
# TLS trust, DNS resolution, and a real %pip install, measured together because they are
# one flow's worth of cells - reusing P7e-0's own fixture and flow unmodified except for one new
# cell (PROBE-7) appended after PROBE-6, so evidence/runtime-probe/ (P7e-0's own, already
# committed evidence) is untouched.
#
# TABLET ONLY (SM-X216B). Every cell here runs against the kernel, and on x86_64 `MainActivity`
# falls back to `FakeKernelGateway` - a green run there would prove nothing about a real getaddrinfo
# shim talking to a real nameserver.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

# The primary evidence directory - the plan's own fixed gate name. P7e-3a (TLS trust) and P7e-3c
# (the getaddrinfo shim) are answered by the same run's earlier cells; their own directories below
# point here rather than duplicating screenshots and a multi-MB logcat three times over.
OUT="$ROOT/evidence/network-trust"
echo "=== measuring network trust and a real pip install on $MODEL ==="

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/runtime-probe.ipynb" "Probe"
NOTEBOOK_STATUS=0
run_flow "$ROOT/.maestro/runtime-probe.yaml" "$OUT" || NOTEBOOK_STATUS=$?

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} \
  shell "run-as $APP cat '$NOTEBOOK_DIR/Probe.ipynb'" > "$OUT/probe.ipynb"
python3 "$ROOT/scripts/read_notebook_outputs.py" "$OUT/probe.ipynb" > "$OUT/probe-output.txt"
echo "--- what the Space answered ---"
cat "$OUT/probe-output.txt"

exit "$NOTEBOOK_STATUS"
