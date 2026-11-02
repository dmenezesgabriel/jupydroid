#!/usr/bin/env bash
# A display updated by id redraws in place: cell 1 shows a handle, cell 2 updates it three times, and
# cell 1 ends on the last frame alone while cell 2 shows nothing - where every update used to be
# appended under cell 2. The autosaved file then holds that one frame.
#
#   scripts/display-update-by-id-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/display-update/by-id"
NOTEBOOK="Display Update"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/display-update-by-id.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/display-update-by-id.yaml" "$EVIDENCE/run"

# The last frame is written only by a save after the run; no earlier frame may be left beside it.
wait_for_workspace_text "$NOTEBOOK.ipynb" 'frame 3' 60 | tee "$EVIDENCE/saved.txt"
"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$NOTEBOOK.ipynb\"" >"$EVIDENCE/saved.ipynb"
if grep -q "'frame [012]'" "$EVIDENCE/saved.ipynb"; then
  echo "an earlier frame was saved beside 'frame 3'" >&2
  exit 1
fi
