#!/usr/bin/env bash
# Sourced by the jd-026-atomic-save drivers. Not executable on its own.
#
# The documents every kill-mid-save driver puts in the workspace, and how it reads back what a save
# left: built on the host, not committed, and too large for seed_file's base64 argument, so pushed
# and copied in by the app's own uid. Sizes are what make a save take long enough - over 100 ms on
# the tablet - for a stop issued from the device to land inside it.
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/jd-026-atomic-save-fixtures.sh"
#   build_notes "$ROOT/build/jd-026/notes.txt" 4000
#   reset_notebooks
#   push_into_workspace "$ROOT/build/jd-026/notes.txt" notes.txt 755

# A ~31 MiB notebook: a markdown cell, a code cell `value = 1`, then 60 cells of 5000-line stream
# outputs.
build_large_notebook() {
  mkdir -p "$(dirname "$1")"
  python3 - "$1" <<'PY'
import json, sys
line = "x" * 99 + "\n"
cells = [{"cell_type": "markdown", "id": "notes", "metadata": {}, "source": ["Notes before the edit"]},
         {"cell_type": "code", "execution_count": None, "id": "edit", "metadata": {}, "outputs": [],
          "source": ["value = 1"]}]
for i in range(60):
    cells.append({"cell_type": "code", "execution_count": i + 1, "id": f"c{i}", "metadata": {},
                  "source": [f"print('x' * 99)  # {i}"],
                  "outputs": [{"name": "stdout", "output_type": "stream", "text": [line] * 5000}]})
kernelspec = {"name": "python3", "display_name": "Python 3", "language": "python"}
notebook = {"cells": cells, "metadata": {"kernelspec": kernelspec}, "nbformat": 4, "nbformat_minor": 5}
with open(sys.argv[1], "w") as out:
    json.dump(notebook, out, indent=1)
PY
}

# A text file of <lines> numbered lines between a first and a last that no truncation can leave
# both of.
build_notes() {
  mkdir -p "$(dirname "$1")"
  python3 - "$1" "$2" <<'PY'
import sys
lines = ["notes for the save test"] + [f"{i:08d} " + "x" * 90 for i in range(int(sys.argv[2]))]
open(sys.argv[1], "w").write("\n".join(lines + ["the last line of the notes"]) + "\n")
PY
}

# Copies <fixture> into the workspace as <name> with <mode>, leaving whatever else is there: the
# caller resets first (`reset_notebooks`), so two documents can sit side by side.
push_into_workspace() {
  local fixture="$1" name="$2" mode="$3"
  "$ADB" push "$fixture" /data/local/tmp/jd-026.seed >/dev/null
  "$ADB" shell "chmod 644 /data/local/tmp/jd-026.seed && run-as $APP sh -c 'cp /data/local/tmp/jd-026.seed \"$NOTEBOOK_DIR/$name\" && chmod $mode \"$NOTEBOOK_DIR/$name\"'"
  "$ADB" shell rm /data/local/tmp/jd-026.seed
}

# The autosave interval at its minimum, so a save follows an edit within a flow's reach.
set_minimum_autosave() {
  "$ADB" shell "run-as $APP sh -c 'echo {\\\"autosaveIntervalSeconds\\\":5} > $EDITOR_SETTINGS_STORE'"
}

# Reads the notebook at <remote> back as JSON on the host. Prints its size and cell count, or why
# it is not a notebook.
read_left_notebook() {
  local left="$ROOT/build/jd-026/left.ipynb"
  "$ADB" exec-out run-as "$APP" cat "$1" >"$left"
  python3 - "$left" <<'PY'
import json, sys
raw = open(sys.argv[1], "rb").read()
try:
    print(f"whole: {len(raw)} bytes, {len(json.loads(raw)['cells'])} cells")
except ValueError as failure:
    print(f"HALF: {len(raw)} bytes, not JSON ({failure})")
PY
}

# Whole is the seeded <fixture>, or it with one write in it: its first and last lines both there,
# and no more or less than that write between. Prints the size of the file at <remote>, or why it
# is half.
read_left_file() {
  local remote="$1" fixture="$2" left="$ROOT/build/jd-026/left.txt"
  "$ADB" exec-out run-as "$APP" cat "$remote" >"$left"
  python3 - "$left" "$fixture" <<'PY'
import sys
left, seeded = open(sys.argv[1]).read(), open(sys.argv[2]).read()
grown = len(left) - len(seeded)
# Replace All grows the file by "saved"'s one letter, the editor by its " edited".
whole = left.startswith("notes for the save") and left.endswith("the last line of the notes\n") and 0 <= grown <= 7
print(f"{'whole' if whole else 'HALF'}: {len(left)} bytes of {len(seeded)} seeded, {grown:+d}")
PY
}
