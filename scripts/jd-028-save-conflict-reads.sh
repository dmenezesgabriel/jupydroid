#!/usr/bin/env bash
# Sourced by the jd-028-save-conflict drivers. Not executable on its own.
#
# What each driver reads back after an answer to File Changed, since no screen shows what the file
# holds: the notebook's cells, and the text file's lines. Each appends one line to
# "$EVIDENCE/disk.txt", and reads the file the driver named in "$NOTEBOOK" or "$NOTES".
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/jd-028-save-conflict-reads.sh"
#   record_notebook_disk "after Revert"
#   record_notes_disk "after the shell wrote"

# Appends what the notebook file holds now: its markdown cells' text, and the first cell's output.
record_notebook_disk() {
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

# Appends what the text file holds now, on one line.
record_notes_disk() {
  local held
  held="$("$ADB" exec-out run-as "$APP" cat "$NOTEBOOK_DIR/$NOTES" | tr '\n' '|')"
  echo "$1: $held" | tee -a "$EVIDENCE/disk.txt"
}
