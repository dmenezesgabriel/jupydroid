#!/usr/bin/env bash
# The README's screenshots: the app on the tablet with a notebook open and run, captured raw into the
# local evidence directory and then cropped of the status and navigation bars into docs/images/.
#
#   scripts/readme-screenshots-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
#
# A frame committed to docs/images/ is published, and check_device_secrets.py judges a binary by its
# path alone, so the crop is what keeps the clock and the notifications out. The bars' heights are
# read from the device at capture time (crop_system_bars.py). Every frame is still looked at.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/readme-screenshots}"
IMAGES="$ROOT/docs/images"
mkdir -p "$IMAGES"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/readme-tour.ipynb" "Jupydroid tour"

# Light, so the README reads the same on a light page; put back however this run ends.
NIGHT_BEFORE="$("$ADB" shell cmd uimode night | awk '{print $3}')"
trap '"$ADB" shell cmd uimode night "$NIGHT_BEFORE" >/dev/null' EXIT
"$ADB" shell cmd uimode night no

run_flow "$ROOT/.maestro/readme-screenshots.yaml" "$OUT" || exit 1

# Read while the app is still in the orientation the frames were taken in.
"$ADB" shell dumpsys window >"$OUT/window.txt"
for frame in notebook-with-outputs:01-notebook-with-outputs html-table-output:02-html-table-output; do
  python3 "$ROOT/scripts/crop_system_bars.py" "$OUT/window.txt" "$OUT/${frame#*:}.png" "$IMAGES/${frame%%:*}.png"
done
