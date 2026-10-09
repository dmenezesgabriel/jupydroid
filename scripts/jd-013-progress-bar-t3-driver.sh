#!/usr/bin/env bash
# A notebook saved with one `stream` output per flush opens as one output, the `\r` frames
# interpreted and the prints after them joined.
#
#   scripts/jd-013-progress-bar-t3-driver.sh
#
# Runs no cell - the outputs are stored in the fixture - so any device will do.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-013-progress-bar}/t3-reopen"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-013-progress-bar-split.ipynb" "Progress Bar Split"

run_flow "$ROOT/.maestro/jd-013-progress-bar-t3-reopen.yaml" "$OUT"
