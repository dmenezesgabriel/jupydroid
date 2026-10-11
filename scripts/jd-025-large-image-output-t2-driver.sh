#!/usr/bin/env bash
# An image shrunk while it decodes draws at its own width: a 300 x 60000 PNG, decoded 150 px wide,
# draws 300 dp wide as a markdown cell's attachment and as an image output the kernel builds
# (ADR-0087). The hierarchy is kept for the bounds each image draws at, which is what measures it.
#
#   scripts/jd-025-large-image-output-t2-driver.sh
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

EVIDENCE="$ROOT/evidence/jd-025-large-image-output/t2"
NOTEBOOK="Tall Image"
OWN_WIDTH_DP=300

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-025-tall-image.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"

run_flow "$ROOT/.maestro/jd-025-large-image-output-t2-markdown.yaml" "$EVIDENCE/markdown"
image_bounds "$EVIDENCE/markdown"
require_drawn "$EVIDENCE/markdown" "tall strip"
require_width_dp "$EVIDENCE/markdown" "tall strip" "$OWN_WIDTH_DP"

run_flow "$ROOT/.maestro/jd-025-large-image-output-t2.yaml" "$EVIDENCE/run"
image_bounds "$EVIDENCE/run"
require_drawn "$EVIDENCE/run" "Cell image output"
require_width_dp "$EVIDENCE/run" "Cell image output" "$OWN_WIDTH_DP"
