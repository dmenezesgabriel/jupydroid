#!/usr/bin/env bash
# An output's metadata survives the wire and the file: a run displays an image with `width=100`,
# and the autosaved notebook carries that width - and a saved `execute_result`'s `isolated` that the
# run never touched - where a save used to write `"metadata": {}`.
#
#   scripts/jd-016-display-update-t1-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-016-display-update/t1-metadata"
NOTEBOOK="Display Metadata"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-016-display-update-metadata.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-016-display-update-t1-metadata.yaml" "$EVIDENCE/run"

# The width is written only by a save after the run; `isolated` was seeded, so it is read second,
# from that same rewritten file.
wait_for_workspace_text "$NOTEBOOK.ipynb" '"width"' 60 | tee "$EVIDENCE/saved.txt"
wait_for_workspace_text "$NOTEBOOK.ipynb" '"isolated"' 5 | tee -a "$EVIDENCE/saved.txt"
"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$NOTEBOOK.ipynb\"" >"$EVIDENCE/saved.ipynb"
