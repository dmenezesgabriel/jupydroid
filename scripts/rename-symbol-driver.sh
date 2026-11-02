#!/usr/bin/env bash
# "Rename symbol…" asks `textDocument/rename` at the caret and lands jedi's
# whole-document answer on every cell the name touches - on the wired build, against the
# rootfs's real pylsp.
#
# TABLET ONLY (SM-X216B), for the reason `definition-driver.sh` states: the emulator's
# `FakeKernelGateway` never opens a Space, so no `pylsp` runs there to answer a rename ask.
#
# Four pieces of evidence, because each proves a different half of the claim:
#
#   1. `def summed` and `total = summed` visible together after the dialog confirms - the
#      whole-document edit was resliced back to every cell the name touched.
#   2. One undo restores both - `recording` made the rename a single undoable transaction.
#   3. `x = 42` + the row answers "No result." - the caret's literal is the reference's
#      `resolveRenameLocation` miss, and the surface says so instead of asking anyway.
#   4. logcat's `lsp.start` and `rename` during the flow - the answer came down the P5b
#      adapter. The buffer is cleared first, so an older `lsp.start` cannot satisfy it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/rename-symbol.ipynb"
FLOW="$ROOT/.maestro/rename-symbol.yaml"
OUT="$ROOT/evidence/rename-symbol"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

mkdir -p "$OUT"
echo "=== rename symbol: jedi's whole-document edit lands on every cell - on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Rename Symbol"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
if grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: pylsp spawned during the flow - the rename went through the port"
else
  echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1;
fi
if grep -q '"method":"rename"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: the rename ask went down the wire - the dialog reached the server"
else
  echo "FAIL: no rename ask in the lifecycle - the port never asked"; fail=1;
fi
if grep -Fq 'Rename symbol' "$OUT/maestro.log"; then
  echo "PASS: the flow tapped the rename row"
else
  echo "FAIL: the row was never tapped"; fail=1;
fi
if grep -Fq 'def summed' "$OUT/maestro.log"; then
  echo "PASS: the renamed def was asserted on screen - the whole-document edit landed"
else
  echo "FAIL: 'def summed' was never asserted"; fail=1;
fi
if grep -Fq 'No result.' "$OUT/maestro.log"; then
  echo "PASS: the miss reported No result. - the surface refused rather than asked"
else
  echo "FAIL: 'No result.' was never asserted"; fail=1;
fi
exit "$fail"
