#!/usr/bin/env bash
# A text/markdown output draws as markdown. One cell displays IPython's Markdown (a heading, a
# paragraph with bold and code, a list), then prints the text/plain IPython sends beside it and
# the kernel's 6 * 7. The hierarchy is kept for the text nodes the markdown drew as.
#
#   scripts/markdown-output-cell-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/markdown-output/t1-route"
NOTEBOOK="Markdown Output"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/markdown-output.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/markdown-output-cell.yaml" "$EVIDENCE/run"

# `uiautomator dump` writes to the device, so it is read back rather than piped.
"${DEVICE[@]}" shell uiautomator dump /sdcard/markdown-output-hierarchy.xml >/dev/null
"${DEVICE[@]}" shell cat /sdcard/markdown-output-hierarchy.xml >"$EVIDENCE/run/hierarchy.xml"
"${DEVICE[@]}" shell rm -f /sdcard/markdown-output-hierarchy.xml
grep -oE 'text="[^"]+"' "$EVIDENCE/run/hierarchy.xml" | tee "$EVIDENCE/run/text-nodes.txt"
