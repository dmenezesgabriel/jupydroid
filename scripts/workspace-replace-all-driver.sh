#!/usr/bin/env bash
# Replace All across the workspace, over a notebook and a plain file in one action.
#
# The flow's own header carries the citations. What this driver adds is the **read-back**: the two
# fixtures below are the ones the flow asserts over, so the counts are the counts these two files
# hold, and a `run-as cat` of each after the run - because "the editor shows dog" is a redraw and
# "the file on disk says dog" is a write, and only the second is what a replace promised.
#
# `analysis.ipynb` is the half that cannot be taken on trust: it goes through `NotebookRepository`
# cell by cell, and a replace written into a `.ipynb` as text would put `dog` into the JSON keys as
# happily as into the source. The dump below is what says which happened.
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

OUT="$ROOT/evidence/workspace-replace-all"

# A clean workspace, so the counts the flow asserts are the counts these two files hold - a search
# reads *everything*, which makes this flow the one most exposed to a tree it did not seed.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/notebook-search.ipynb" "analysis"
seed_file "$ROOT/.maestro/fixtures/search.py" "notes.py"

run_flow "$ROOT/.maestro/workspace-replace-all.yaml" "$OUT"

# What is actually on disk afterwards. `run-as` because the workspace is app-private, and the whole
# file rather than a grep, so the notebook's untouched keys are visible beside its rewritten source.
{
  echo "=== notes.py ==="
  "$ADB" -s "$MAESTRO_DEVICE" shell "run-as $APP cat '$NOTEBOOK_DIR/notes.py'"
  echo
  echo "=== analysis.ipynb ==="
  "$ADB" -s "$MAESTRO_DEVICE" shell "run-as $APP cat '$NOTEBOOK_DIR/analysis.ipynb'"
} > "$OUT/what-was-written.txt"

echo "--- what the replace wrote ---"
cat "$OUT/what-was-written.txt"
