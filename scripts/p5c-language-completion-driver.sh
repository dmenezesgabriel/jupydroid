#!/usr/bin/env bash
# P5c: the cell completion popup answered by `pylsp`, on the wired build.
#
# TABLET ONLY (SM-X216B), for the reason `p2l-3a-code-size-driver.sh` states: the emulator's
# `FakeKernelGateway` makes both halves of this comparison meaningless - no Space, no real
# `complete_request`, no `pylsp`.
#
# Two pieces of evidence, because each proves a different half of the claim:
#
#   1. The popup itself: `Completion join` appears over `import os` + `os.path.jo` - the
#      answer a static parser gives, typed into a real cell on a real device.
#   2. logcat's `lsp.start` stage *during the flow*: the port spawned `pylsp` on the first
#      keystroke's ask, so the popup's answer came down the P5b adapter - not the kernel.
#      The buffer is cleared first, so an `lsp.start` older than this run cannot satisfy it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p5c-language-completion.ipynb"
FLOW="$ROOT/.maestro/p5c-language-completion.yaml"
OUT="$ROOT/evidence/p5c-language-completion"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P5c must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

mkdir -p "$OUT"
echo "=== completion popup answered by pylsp, on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Language Completion"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log" &&
  echo "PASS: pylsp spawned during the flow - the popup went through the port" ||
  { echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1; }
grep -q 'Completion join' "$OUT/maestro.log" &&
  echo "PASS: the popup carried pylsp's answer" ||
  { echo "FAIL: 'Completion join' never appeared"; fail=1; }
exit "$fail"
