#!/usr/bin/env bash
# P5e-5: "Go to definition" asks `textDocument/definition` at the caret and lands the jump -
# the target cell scrolled to, its field focused, the caret on the name - on the wired build,
# against the rootfs's real pylsp.
#
# TABLET ONLY (SM-X216B), for the reason `p5c-language-completion-driver.sh` states: the
# emulator's `FakeKernelGateway` never opens a Space, so no `pylsp` runs there to answer
# where `compute_total` is defined.
#
# Three pieces of evidence, because each proves a different half of the claim:
#
#   1. `def compute_total` visible after the row is tapped - the Location[] mapped back to a
#      cell that was off-screen and scrolled to it, the cross-cell half of the claim.
#   2. `No definition found` on a comment cell - the miss says so once, the way VS Code's
#      `MessageController.showMessage` does, and no jump happens.
#   3. logcat's `lsp.start` during the flow - the answers came down the P5b adapter.
#      The buffer is cleared first, so an `lsp.start` older than this run cannot satisfy it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p5e5-definition.ipynb"
FLOW="$ROOT/.maestro/p5e5-definition.yaml"
OUT="$ROOT/evidence/p5e5-definition"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P5e-5 must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

mkdir -p "$OUT"
echo "=== go to definition: the ask jumps to the def cell, a miss says so - on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Go to Definition"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log" &&
  echo "PASS: pylsp spawned during the flow - the jump went through the port" ||
  { echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1; }
grep -Fq 'Cell menu Go to definition' "$OUT/maestro.log" &&
  echo "PASS: the flow tapped the inspect row" ||
  { echo "FAIL: the row was never tapped"; fail=1; }
grep -Fq 'def compute_total' "$OUT/maestro.log" &&
  echo "PASS: the def cell was asserted on screen - the jump landed" ||
  { echo "FAIL: 'def compute_total' was never asserted"; fail=1; }
grep -Fq 'No definition found' "$OUT/maestro.log" &&
  echo "PASS: the miss was asserted to say so" ||
  { echo "FAIL: 'No definition found' was never asserted"; fail=1; }
exit "$fail"
