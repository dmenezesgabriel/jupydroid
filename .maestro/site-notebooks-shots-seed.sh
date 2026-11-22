#!/usr/bin/env bash
# The workspace .maestro/site-notebooks-shots.yaml shoots: one notebook that mixes the three cell
# types, with code cells whose prints give the run something computed to wait on and a last cell
# that sleeps so a run can be caught mid-flight. Run by scripts/site-screenshots-driver.sh between
# the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_notebook "$ROOT/.maestro/fixtures/notebook-tour.ipynb" "Notebook Tour"
