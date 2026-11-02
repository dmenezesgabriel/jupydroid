#!/usr/bin/env bash
# The four image mimes besides PNG draw: one cell displays a JPEG, a GIF, a WebP and a BMP strip,
# each sent alone under its own mime, and all four draw where each used to show its mime key.
# The hierarchy is kept for the bounds each image draws at.
#
#   scripts/image-formats-mimes-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/image-formats/t1-formats"
NOTEBOOK="Image Formats"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/image-formats.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/image-formats-mimes.yaml" "$EVIDENCE/run"

# `uiautomator dump` writes to the device, so it is read back rather than piped.
"${DEVICE[@]}" shell uiautomator dump /sdcard/image-formats-hierarchy.xml >/dev/null
"${DEVICE[@]}" shell cat /sdcard/image-formats-hierarchy.xml >"$EVIDENCE/run/hierarchy.xml"
"${DEVICE[@]}" shell rm -f /sdcard/image-formats-hierarchy.xml
grep -oE 'content-desc="Cell image output"[^>]*bounds="[^"]*"' "$EVIDENCE/run/hierarchy.xml" |
  grep -oE 'bounds="[^"]*"' | tee "$EVIDENCE/run/image-bounds.txt"
