#!/usr/bin/env bash
# P2x-4c: a workspace search finds a match **inside a notebook's cells**, and the row says which.
#
# The skip P2x-1d put in is gone (P2x-4b), so `analysis.ipynb` is walked as the cells it is rather
# than as the JSON it is stored in. Two things this proves that no JVM test can: that a notebook
# reaches the panel at all, and that two rows a tap apart answer to two different strings - which
# is the whole reason the cell is in the label (`search.showLineNumbers` is false, so every row in
# a notebook would otherwise be "line 1").
#
# A `.py` is seeded beside it so the tree holds both kinds and the notebook is not the only answer.
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

# A clean workspace, so the counts the flow asserts are the counts these two files hold - a search
# reads *everything*, which makes this flow the one most exposed to a tree it did not seed.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2x-4-notebook-search.ipynb" "analysis"
seed_file "$ROOT/.maestro/fixtures/p2x-3-search.py" "notes.py"

run_flow "$ROOT/.maestro/p2x-4-notebook-search.yaml" "$ROOT/evidence/p2x-4-notebook-search"
