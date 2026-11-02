#!/usr/bin/env bash
# A bare URL draws as a link. A markdown cell holds a scheme URL and a `www.` host;
# a code cell displays a bare URL through IPython's Markdown and prints the kernel's 6 * 7.
#
#   scripts/gfm-autolink-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/gfm-table/t3-autolink"
NOTEBOOK="GFM Autolink"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/gfm-autolink.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/gfm-autolink.yaml" "$EVIDENCE/run"
