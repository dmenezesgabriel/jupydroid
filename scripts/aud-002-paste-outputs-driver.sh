#!/usr/bin/env bash
# AUD-002's paste defect, end to end: a cell copied and pasted below itself must show the outputs
# and execution count the original was saved with - the cell clipboard is the cell's JSON, so the
# copy arrives carrying them. Runs no cell on purpose: the results are seeded into the fixture,
# which makes the paste the only thing under test and needs no kernel.
#
# run_flow rather than maestro_test, so the frames land in evidence/ and get committed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/aud-002-paste-outputs.ipynb" "Paste Outputs"

run_flow "$ROOT/.maestro/aud-002-paste-outputs.yaml" "$ROOT/evidence/aud-002-paste-outputs"

# The debounce's write rather than the screen's copy: the pasted cell's results reaching the file
# is half of what the fix is for, and the flow itself cannot see it.
sleep 2
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Paste Outputs.ipynb\""
