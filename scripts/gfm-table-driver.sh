#!/usr/bin/env bash
# A GitHub-flavoured pipe table draws as a grid. A markdown cell holds a table with a left, a centred
# and a right column; a code cell displays a markdown table through IPython's Markdown and prints the
# kernel's 6 * 7. The hierarchy is kept so each cell's text node, and where it sits, can be read.
#
#   scripts/gfm-table-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/gfm-table/t1-table"
NOTEBOOK="GFM Markdown"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/gfm-table.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/gfm-table.yaml" "$EVIDENCE/run"

# `uiautomator dump` writes to the device, so it is read back rather than piped.
"${DEVICE[@]}" shell uiautomator dump /sdcard/gfm-table-hierarchy.xml >/dev/null
"${DEVICE[@]}" shell cat /sdcard/gfm-table-hierarchy.xml >"$EVIDENCE/run/hierarchy.xml"
"${DEVICE[@]}" shell rm -f /sdcard/gfm-table-hierarchy.xml
grep -oE 'text="[^"]+"[^>]*bounds="[^"]+"' "$EVIDENCE/run/hierarchy.xml" \
  | sed -E 's/ (resource-id|class|package|content-desc|checkable|checked|clickable|enabled|focusable|focused|scrollable|long-clickable|password|selected)="[^"]*"//g' \
  | tee "$EVIDENCE/run/text-nodes.txt"
