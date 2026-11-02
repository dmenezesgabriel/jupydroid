#!/usr/bin/env bash
# A `\r` progress loop in a notebook cell draws one line, and the prints after it join the same
# output.
#
#   scripts/progress-bar-notebook-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/progress-bar}/notebook"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/progress-bar.ipynb" "Progress Bar"

run_flow "$ROOT/.maestro/progress-bar-notebook.yaml" "$OUT"
