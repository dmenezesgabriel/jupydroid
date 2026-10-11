#!/usr/bin/env bash
# An HTML <img> draws: in a markdown cell at its width attribute, inside a line of prose, inside an HTML
# paragraph and in a code cell's text/html output, while one naming no file draws its alt
# text. The hierarchy is kept for the bounds each image draws at, and the sized one's width is
# measured against the 80 px its attribute asks for, in dp.
#
#   scripts/jd-024-markdown-images-t4-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/image-nodes.sh
. "$ROOT/scripts/image-nodes.sh"

EVIDENCE="$ROOT/evidence/jd-024-markdown-images/t4-html-images"
NOTEBOOK="HTML Images"
SIZED_WIDTH_DP=80

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-024-html-images.ipynb" "$NOTEBOOK"
seed_file "$ROOT/.maestro/fixtures/jd-024-plot.png" "img/plot.png"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"

run_flow "$ROOT/.maestro/jd-024-markdown-images-t4-part1-run.yaml" "$EVIDENCE/run"
image_bounds "$EVIDENCE/run"
require_drawn "$EVIDENCE/run" "sized plot" "inline plot" "output plot"
require_width_dp "$EVIDENCE/run" "sized plot" "$SIZED_WIDTH_DP"

run_flow "$ROOT/.maestro/jd-024-markdown-images-t4-part2-paragraph.yaml" "$EVIDENCE/paragraph"
image_bounds "$EVIDENCE/paragraph"
require_drawn "$EVIDENCE/paragraph" "paragraph plot"
require_not_drawn "$EVIDENCE/paragraph" "no such img"
