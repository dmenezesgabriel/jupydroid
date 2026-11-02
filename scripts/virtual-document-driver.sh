#!/usr/bin/env bash
# The notebook is ONE virtual document under the language server, proven by a
# completion only a shared document can answer.
#
# TABLET ONLY (SM-X216B), for the reason `code-size-driver.sh` states: the emulator's
# `FakeKernelGateway` makes both halves of this comparison meaningless - no Space, no real
# `complete_request`, no `pylsp`.
#
# Three pieces of evidence, and the third is the discriminator:
#
#   1. `Completion join` over `os.path.jo` with `import os` in an earlier cell - attribute
#      completion across cell boundaries. (The kernel could answer this too, so it does not
#      discriminate - it proves the popup works end-to-end.)
#   2. logcat's `lsp.start` stage *during the flow*: the answer came down the port.
#   3. `Completion cross_cell_name_for_lsp`: the name lives in a cell that is NEVER RUN, so
#      the kernel's namespace cannot offer it, and it is not in the asked cell's own text, so
#      P5c's per-cell documents could not offer it either. It completes only if the language
#      server holds every code cell as one document - the virtual-document claim itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/virtual-document.ipynb"
FLOW="$ROOT/.maestro/virtual-document.yaml"
OUT="$ROOT/evidence/virtual-document"

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
echo "=== one virtual document under the language server, on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Virtual Document"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
if grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: pylsp spawned during the flow - the asks went through the port"
else
  echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1;
fi
if grep -q 'Completion join' "$OUT/maestro.log"; then
  echo "PASS: attribute completion across cells"
else
  echo "FAIL: 'Completion join' never appeared"; fail=1;
fi
if grep -q 'Completion cross_cell_name_for_lsp' "$OUT/maestro.log"; then
  echo "PASS: a never-run cell's name completed - the document is shared"
else
  echo "FAIL: 'Completion cross_cell_name_for_lsp' never appeared - cells are not one document"; fail=1;
fi
exit "$fail"
