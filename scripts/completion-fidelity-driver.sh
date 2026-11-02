#!/usr/bin/env bash
# The completion row reads the signature-resolved *label* and accepting splices the
# *insert* - on the wired build, against the rootfs's real pylsp.
#
# TABLET ONLY (SM-X216B), for the reason `language-completion-driver.sh` states: the
# emulator's `FakeKernelGateway` never opens a Space, so no `pylsp` runs there to answer
# `os.path.jo` with `join(path, paths)`.
#
# Three pieces of evidence, because each proves a different half of the claim:
#
#   1. `Completion join(path, paths)` appears in the popup - the label rendered, not the
#      insert text (the signature is what VS Code's suggest widget shows too).
#   2. The cell reads `os.path.join` after a tap and `join(path, paths)` is nowhere - the
#      splice took `insertText`, which is what keeps `${1:}` payload text out while
#      `snippetSupport` stays undeclared.
#   3. logcat's `lsp.start` during the flow - the answer came down the P5b adapter.
#      The buffer is cleared first, so an `lsp.start` older than this run cannot satisfy it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/completion-fidelity.ipynb"
FLOW="$ROOT/.maestro/completion-fidelity.yaml"
OUT="$ROOT/evidence/completion-fidelity"

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
echo "=== completion row: label shown, insert spliced - on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Completion Fidelity"
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
# The log records the asserted patterns escaped (`join\(path`), so these match literally.
if grep -Fq 'Completion join\(path, paths\)' "$OUT/maestro.log"; then
  echo "PASS: the row showed the signature-resolved label"
else
  echo "FAIL: 'Completion join(path, paths)' never appeared"; fail=1;
fi
if grep -Fq 'os\.path\.join' "$OUT/maestro.log"; then
  echo "PASS: accepting wrote the insert text"
else
  echo "FAIL: the accepted text was not os.path.join"; fail=1;
fi
exit "$fail"
