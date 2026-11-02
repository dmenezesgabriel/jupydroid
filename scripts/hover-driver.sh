#!/usr/bin/env bash
# The "Symbol info" row asks `textDocument/hover` at the caret and the popup draws the
# answered markdown - on the wired build, against the rootfs's real pylsp.
#
# TABLET ONLY (SM-X216B), for the reason `language-completion-driver.sh` states: the
# emulator's `FakeKernelGateway` never opens a Space, so no `pylsp` runs there to answer
# `compute_total` with jedi's fenced signature.
#
# Three pieces of evidence, because each proves a different half of the claim:
#
#   1. `compute_total(values)` appears after the row is tapped - the markdown rendered, the
#      signature's `(values` being what the bare cell text cannot already show.
#   2. An outside tap makes the popup leave - dismissal through `onDismissRequest`, the same
#      focusable-Popup path the diagnostic card proved.
#   3. logcat's `lsp.start` during the flow - the answer came down the P5b adapter.
#      The buffer is cleared first, so an `lsp.start` older than this run cannot satisfy it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/hover.ipynb"
FLOW="$ROOT/.maestro/hover.yaml"
OUT="$ROOT/evidence/hover"

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
echo "=== symbol info: hover asked at the caret, markdown drawn - on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Symbol Info"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
if grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: pylsp spawned during the flow - the popup went through the port"
else
  echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1;
fi
# The log records the asserted pattern escaped (`compute_total\(values`), so match literally.
if grep -Fq 'compute_total\(values' "$OUT/maestro.log"; then
  echo "PASS: the popup showed the markdown's fenced signature"
else
  echo "FAIL: 'compute_total(values)' never appeared"; fail=1;
fi
if grep -Fq 'Symbol info" is not visible' "$OUT/maestro.log"; then
  echo "PASS: the outside tap's dismissal was asserted in the flow"
else
  echo "FAIL: the flow never checked the popup dismissed"; fail=1;
fi
# The same `notVisible` assert fires twice: the outside-tap dismissal, then the off-symbol
# ask (`# nothing` - a comment has no symbol) which must draw no card at all.
if [ "$(grep -Fc 'Symbol info" is not visible' "$OUT/maestro.log")" -ge 2 ]; then
  echo "PASS: the off-symbol ask drew no card"
else
  echo "FAIL: the off-symbol ask was never asserted"; fail=1;
fi
exit "$fail"
