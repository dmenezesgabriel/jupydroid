#!/usr/bin/env bash
# A notebook's save is refused when its file changed since it was read, and the editor asks. The
# notebook's first cell calls a seeded module that appends a markdown cell to the notebook's own
# file, in place, as any cell may: the run's save then finds a file it did not write. Revert must
# show the added cell and leave it on disk; a second run and Overwrite must leave the editor's
# cells there instead; and an edit with nobody else writing must be saved without a question.
# What is on disk is read back after each answer, since no screen shows it.
#
#   scripts/jd-028-save-conflict-t1-driver.sh
#
# TABLET ONLY (SM-X216B): the file is rewritten by a real kernel. FakeKernelGateway writes nothing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-028-save-conflict/t1"
NOTEBOOK="jd-028-save-conflict"
export MAESTRO_NOTEBOOK="$NOTEBOOK"

# Appends what the notebook file holds now: its markdown cells' text, and the first cell's output.
record_disk() {
  local left="$ROOT/build/jd-028/left.ipynb"
  mkdir -p "$(dirname "$left")"
  "$ADB" exec-out run-as "$APP" cat "$NOTEBOOK_DIR/$NOTEBOOK.ipynb" >"$left"
  python3 - "$left" "$1" <<'PY' | tee -a "$EVIDENCE/disk.txt"
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
added = ["".join(c["source"]) for c in cells if c["cell_type"] == "markdown"]
printed = ["".join(o.get("text", "")).strip() for o in cells[0].get("outputs", [])]
edit = "".join(cells[1]["source"])
print(f"{sys.argv[2]}: {len(cells)} cells; added on disk {added}; first cell printed {printed}; second cell {edit!r}")
PY
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-028-save-conflict.ipynb" "$NOTEBOOK"
seed_file "$ROOT/.maestro/fixtures/jd-028-save-conflict-diskwriter.py" diskwriter.py
record_disk seeded
run_flow "$ROOT/.maestro/jd-028-save-conflict-t1-revert.yaml" "$EVIDENCE/revert"
record_disk "after Revert"
run_flow "$ROOT/.maestro/jd-028-save-conflict-t1-overwrite.yaml" "$EVIDENCE/overwrite"
wait_for_workspace_text "$NOTEBOOK.ipynb" "cells on disk: 4"
record_disk "after Overwrite"
run_flow "$ROOT/.maestro/jd-028-save-conflict-t1-plain-edit.yaml" "$EVIDENCE/plain-edit"
wait_for_workspace_text "$NOTEBOOK.ipynb" "value = 1 + 1"
record_disk "after a plain edit"
grep -q "after Revert: 3 cells; added on disk \['ADDED ON DISK 3'\]" "$EVIDENCE/disk.txt"
grep -q "after Overwrite: 3 cells; added on disk \['ADDED ON DISK 3'\]; first cell printed \['cells on disk: 4'\]" "$EVIDENCE/disk.txt"
grep -q "after a plain edit: 3 cells; .*second cell 'value = 1 + 1'" "$EVIDENCE/disk.txt"
