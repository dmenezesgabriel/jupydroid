#!/usr/bin/env bash
# The four image mimes besides PNG draw: one cell displays a JPEG, a GIF, a WebP and a BMP strip,
# each sent alone under its own mime, and all four draw where each used to show its mime key.
# The hierarchy is kept for the bounds each image draws at.
#
#   scripts/jd-018-image-formats-t1-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-018-image-formats/t1-formats"
NOTEBOOK="Image Formats"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-018-image-formats.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-018-image-formats-t1-formats.yaml" "$EVIDENCE/run"

# `uiautomator dump` writes to the device, so it is read back rather than piped.
"${DEVICE[@]}" shell uiautomator dump /sdcard/jd-018-hierarchy.xml >/dev/null
"${DEVICE[@]}" shell cat /sdcard/jd-018-hierarchy.xml >"$EVIDENCE/run/hierarchy.xml"
"${DEVICE[@]}" shell rm -f /sdcard/jd-018-hierarchy.xml
grep -oE 'content-desc="Cell image output"[^>]*bounds="[^"]*"' "$EVIDENCE/run/hierarchy.xml" |
  grep -oE 'bounds="[^"]*"' | tee "$EVIDENCE/run/image-bounds.txt"
