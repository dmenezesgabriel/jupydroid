#!/usr/bin/env bash
# P5e-1: a tap on a diagnosed line's gutter mark shows its findings in a floating card - The P5e-1
# gate: VS Code's `markerHoverParticipant` content performed as a tap.
#
# TABLET ONLY (SM-X216B), for the reason `p5d2-diagnostics-driver.sh` states: the emulator's
# `FakeKernelGateway` means no kernel session ever opens - the sync gate stays shut and no
# real `pylsp` runs to push a diagnostic.
#
# Three pieces of evidence:
#
#   1. `Diagnostic detail` + the severity line + `pyflakes` in view after the mark's own pixel
#      is tapped - the hover's content, performed with a finger.
#   2. The same text gone after an outside tap on empty notebook space - dismissal.
#   3. `02-marks-up.png` against `03-mark-tap-shows-the-findings.png`: the square is what the
#      finger aimed at, and the card is what answered.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p5d2-diagnostics.ipynb"
FLOW="$ROOT/.maestro/p5e1-diagnostic-detail.yaml"
OUT="$ROOT/evidence/p5e1-diagnostic-detail"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P5e-1 must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

mkdir -p "$OUT"
echo "=== diagnostic mark tap -> findings card, on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Diagnostics"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log" &&
  echo "PASS: pylsp spawned during the flow - the findings came down the real port" ||
  { echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1; }
grep -q "Diagnostic detail" "$OUT/maestro.log" &&
  echo "PASS: the mark's tap raised the findings card" ||
  { echo "FAIL: the mark's tap never raised the card"; fail=1; }
exit "$fail"
