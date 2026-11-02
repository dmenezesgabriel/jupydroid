#!/usr/bin/env bash
# `publishDiagnostics` drawn as severity marks in the cell gutter - the editor half of the
# diagnostics gate (the port stream itself shipped before it).
#
# TABLET ONLY (SM-X216B), for the reason `code-size-driver.sh` states: the emulator's
# `FakeKernelGateway` means no kernel session ever opens - the sync gate stays shut and no
# real `pylsp` runs to push anything.
#
# Four pieces of evidence:
#
#   1. logcat's `lsp.start` stage during the flow: the publications came down the real port.
#   2. `diagnostic on line 1: error, undefined name 'never_defined_lsp_name'` - pyflakes' own
#      words on a cell that is NEVER RUN. The kernel's namespace cannot have produced it;
#      only the shared virtual document the server linted can - the virtual-document claim.
#   3. `diagnostic on line 1: warning, E225 missing whitespace around operator` - a second severity
#      level rendering, so the screenshot's two mark colours are the two severities.
#   4. `undefined name 'second_also_undefined'` AFTER the cell is retyped - a mark naming the
#      new text appears only if the edit resynced the document and the server republished:
#      the live-as-you-type half.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/diagnostics.ipynb"
FLOW="$ROOT/.maestro/diagnostics.yaml"
OUT="$ROOT/evidence/diagnostics"

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
echo "=== publishDiagnostics as cell gutter marks, on $MODEL ==="

# Cleared so the check below reads only what this flow did.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -c

reset_notebooks
seed_notebook "$FIXTURE" "Diagnostics"
run_flow "$FLOW" "$OUT"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle during the flow ---"; cat "$OUT/lsp-lifecycle.log"

fail=0
if grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: pylsp spawned during the flow - the publications came down the port"
else
  echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1;
fi
if grep -q "diagnostic on line 1: error, undefined name 'never_defined_lsp_name'" "$OUT/maestro.log"; then
  echo "PASS: error mark on a never-run cell - the shared document was linted"
else
  echo "FAIL: the never-run cell's error mark never appeared"; fail=1;
fi
if grep -q "diagnostic on line 1: warning, E225 missing whitespace around operator" "$OUT/maestro.log"; then
  echo "PASS: warning mark alongside the error - severities render distinctly"
else
  echo "FAIL: the warning mark never appeared"; fail=1;
fi
if grep -q "diagnostic on line 1: error, undefined name 'second_also_undefined'" "$OUT/maestro.log"; then
  echo "PASS: retyping republished - diagnostics live as you type"
else
  echo "FAIL: the retyped cell's mark never appeared - edits do not resync"; fail=1;
fi
exit "$fail"
