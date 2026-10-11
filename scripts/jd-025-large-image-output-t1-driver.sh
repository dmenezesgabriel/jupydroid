#!/usr/bin/env bash
# An image output too large for a canvas draws shrunk to the column: a 12800 x 3200 PNG the kernel
# builds draws as wide as its output column and a quarter as tall (ADR-0087, ADR-0086).
# The hierarchy is kept for the bounds the image draws at, which is what measures it.
#
#   scripts/jd-025-large-image-output-t1-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/image-nodes.sh
. "$ROOT/scripts/image-nodes.sh"

EVIDENCE="$ROOT/evidence/jd-025-large-image-output/t1"
NOTEBOOK="Large Image Output"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-025-large-image-output.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-025-large-image-output-t1.yaml" "$EVIDENCE/run"

image_bounds "$EVIDENCE/run"
require_drawn "$EVIDENCE/run" "Cell image output"

# The left, top, right and bottom of the node described [name].
edges_of() {
  grep "content-desc=\"$1\"" "$EVIDENCE/run/image-bounds.txt" |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/'
}
read -r left top right bottom < <(edges_of "Cell image output")
read -r column_left _ column_right _ < <(edges_of "Cell output")
width=$((right - left))
height=$((bottom - top))
echo "image $width x $height px, column $((column_right - column_left)) px wide" |
  tee "$EVIDENCE/run/image-measure.txt"
[ "$left" -eq "$column_left" ] && [ "$right" -eq "$column_right" ] || { echo "the image is not the column's width"; exit 1; }
# A layout rounds to whole pixels.
((height * 4 >= width - 4 && height * 4 <= width + 4)) || { echo "the image is not 4:1"; exit 1; }
