#!/usr/bin/env bash
# P5e-6: "Format cell" asks `textDocument/rangeFormatting` for the cell's whole virtual block
# and lands the formatter's answer on that cell alone - on the wired build, against the
# rootfs's real pylsp + autopep8.
#
# TABLET ONLY (SM-X216B), for the reason `p5e5-definition-driver.sh` states: the emulator's
# `FakeKernelGateway` never opens a Space, so no `pylsp` runs there to answer a range ask.
#
# Four pieces of evidence, because each proves a different half of the claim:
#
#   1. `x = 1` visible after the row is tapped - autopep8's whole-document answer was sliced
#      back to the asked cell and the field adopted the rewrite.
#   2. `untouched=1` still shows its own missing-whitespace - the suffix guard kept the
#      answer from reaching the cell after the asked range.
#   3. The raw cell's menu has no row - the virtual document maps code cells only, so a
#      non-code cell's ask could never resolve.
#   4. logcat's `lsp.start` and `rangeFormatting` during the flow - the answer came down the
#      P5b adapter. The buffer is cleared first, so an older `lsp.start` cannot satisfy it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p5e6-format.ipynb"
FLOW="$ROOT/.maestro/p5e6-format.yaml"
OUT="$ROOT/evidence/p5e6-format"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P5e-6 must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

mkdir -p "$OUT"
echo "=== format cell: autopep8's answer lands on the asked cell only - on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Format Cell"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
if grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: pylsp spawned during the flow - the format went through the port"
else
  echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1;
fi
if grep -q '"method":"rangeFormatting"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: the range ask went down the wire - the row reached the server"
else
  echo "FAIL: no rangeFormatting ask in the lifecycle - the port never asked"; fail=1;
fi
if grep -Fq 'Cell menu Format cell' "$OUT/maestro.log"; then
  echo "PASS: the flow tapped the format row"
else
  echo "FAIL: the row was never tapped"; fail=1;
fi
if grep -Fq 'x = 1' "$OUT/maestro.log"; then
  echo "PASS: the formatted source was asserted on screen - the rewrite landed"
else
  echo "FAIL: 'x = 1' was never asserted"; fail=1;
fi
if grep -Fq 'untouched=1' "$OUT/maestro.log"; then
  echo "PASS: the next cell was asserted unchanged - the answer stayed in range"
else
  echo "FAIL: 'untouched=1' was never asserted"; fail=1;
fi
exit "$fail"
