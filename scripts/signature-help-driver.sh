#!/usr/bin/env bash
# Typing `(` inside a call asks `textDocument/signatureHelp` at the caret and the
# card draws jedi's signature - on the wired build, against the rootfs's real pylsp.
#
# TABLET ONLY (SM-X216B), for the reason `language-completion-driver.sh` states: the
# emulator's `FakeKernelGateway` never opens a Space, so no `pylsp` runs there to answer
# `os.path.join(` with jedi's signature.
#
# Three pieces of evidence, because each proves a different half of the claim:
#
#   1. `Signature help` + `join(path` appear after the `(` is typed - the card drew, and it
#      drew jedi's signature rather than anything the cell already says.
#   2. `)` makes the card leave - the caret stepping outside the call is `cancel()`'s
#      closing-the-parens half.
#   3. logcat's `lsp.start` during the flow - the answer came down the P5b adapter.
#      The buffer is cleared first, so an `lsp.start` older than this run cannot satisfy it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/signature-help.ipynb"
FLOW="$ROOT/.maestro/signature-help.yaml"
OUT="$ROOT/evidence/signature-help"

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
echo "=== signature help: a typed ( asks, jedi's signature drawn - on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Signature Help"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
if grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: pylsp spawned during the flow - the card went through the port"
else
  echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1;
fi
# The log echoes the flow's own regex, so the needle keeps its escaped paren.
if grep -Fq 'join\(path' "$OUT/maestro.log"; then
  echo "PASS: the card showed jedi's signature for os.path.join"
else
  echo "FAIL: 'join(path' never appeared"; fail=1;
fi
if grep -Fq 'Signature help" is not visible' "$OUT/maestro.log"; then
  echo "PASS: closing the call was asserted to clear the card"
else
  echo "FAIL: the flow never checked the card cleared"; fail=1;
fi
exit "$fail"
