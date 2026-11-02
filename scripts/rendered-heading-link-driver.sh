#!/usr/bin/env bash
# A tap on a rendered `#heading` link scrolls to the heading. A markdown cell holds links to a
# heading inside a collapsed section, the same heading by its slug, a heading nothing has, and a
# heading a code cell draws through IPython's Markdown before printing the kernel's 6 * 7.
#
#   scripts/rendered-heading-link-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/rendered-link/t3-heading-link"
NOTEBOOK="Heading Link"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/heading-link.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/rendered-heading-link-cell.yaml" "$EVIDENCE/cell-link"
run_flow "$ROOT/.maestro/rendered-heading-link-output.yaml" "$EVIDENCE/output-heading"
