#!/usr/bin/env bash
# Three PNG strips draw at JupyterLab's widths: 96 px with no width at 96 dp, one asking for width=100
# at 100 dp, and a 2000 px one shrunk to the column, each at the column's start (ADR-0086).
# The hierarchy is kept for the bounds each image draws at, which is what measures the widths.
#
#   scripts/jd-018-image-formats-t2-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-018-image-formats/t2-widths"
NOTEBOOK="Image Widths"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-018-image-widths.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-018-image-formats-t2-widths.yaml" "$EVIDENCE/run"

# `uiautomator dump` writes to the device, so it is read back rather than piped.
"${DEVICE[@]}" shell uiautomator dump /sdcard/jd-018-hierarchy.xml >/dev/null
"${DEVICE[@]}" shell cat /sdcard/jd-018-hierarchy.xml >"$EVIDENCE/run/hierarchy.xml"
"${DEVICE[@]}" shell rm -f /sdcard/jd-018-hierarchy.xml
grep -oE 'content-desc="Cell image output"[^>]*bounds="[^"]*"' "$EVIDENCE/run/hierarchy.xml" |
  grep -oE 'bounds="[^"]*"' | tee "$EVIDENCE/run/image-bounds.txt"
