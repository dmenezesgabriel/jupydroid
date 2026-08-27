#!/usr/bin/env bash
# P2x-5i: one undo takes back the whole replace-all, across a notebook and a plain file.
#
# The flow's own header carries the citations. What this driver adds is the read-back that a flow
# cannot do: `run-as cat` of both files after the undo, held against the fixtures they were seeded
# from. "The editor shows cat again" is a redraw; "the file on disk is byte for byte the fixture" is
# an undo, and only the second is what one undo across every file promised.
#
# WIDE WINDOW: the panel is drawn only where there is room for two panes, which the tablet's 1280dp
# window has and the emulator's 320dp does not.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/p2x-5-undo-a-replace-all"

# The same two fixtures P2x-5h replaces across, so the counts the flow asserts are these files'.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2x-4-notebook-search.ipynb" "analysis"
seed_file "$ROOT/.maestro/fixtures/p2x-3-search.py" "notes.py"

run_flow "$ROOT/.maestro/p2x-5-undo-a-replace-all.yaml" "$OUT"

"$ADB" -s "$MAESTRO_DEVICE" shell "run-as $APP cat '$NOTEBOOK_DIR/notes.py'" > "$OUT/notes-after-the-undo.py"

# The `.py` is the half that can be compared **byte for byte**: it was seeded from this fixture and
# nothing but the replace and its undo has touched it. The notebook cannot be - it is re-serialised
# by `DiskNotebookRepository` on every save, so its keys are reordered and its whitespace is not the
# fixture's - so what is checked there is the source of the two cells that moved.
if diff -u "$ROOT/.maestro/fixtures/p2x-3-search.py" "$OUT/notes-after-the-undo.py"; then
  echo "notes.py is byte for byte the fixture again"
else
  echo "notes.py did NOT come back to the fixture"; exit 1
fi

"$ADB" -s "$MAESTRO_DEVICE" shell "run-as $APP cat '$NOTEBOOK_DIR/analysis.ipynb'" \
  > "$OUT/analysis-after-the-undo.ipynb"
grep -q 'the cat is in cell four' "$OUT/analysis-after-the-undo.ipynb" &&
  grep -q 'a raw cell, no cat here' "$OUT/analysis-after-the-undo.ipynb" &&
  ! grep -q 'dog' "$OUT/analysis-after-the-undo.ipynb" &&
  echo "both cells came back and no dog is left in the notebook"
