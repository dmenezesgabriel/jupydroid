#!/usr/bin/env bash
# A tap on a rendered `#heading` link scrolls to the heading. A markdown cell holds links to a
# heading inside a collapsed section, the same heading by its slug, a heading nothing has, and a
# heading a code cell draws through IPython's Markdown before printing the kernel's 6 * 7.
#
#   scripts/jd-021-rendered-link-t3-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-021-rendered-link/t3-heading-link"
NOTEBOOK="Heading Link"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-021-heading-link.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-021-rendered-link-t3-part1-cell-link.yaml" "$EVIDENCE/cell-link"
run_flow "$ROOT/.maestro/jd-021-rendered-link-t3-part2-output-heading.yaml" "$EVIDENCE/output-heading"
