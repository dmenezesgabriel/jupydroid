#!/usr/bin/env bash
# P2h-2: the table of contents overlay opens from the View menu, lists every heading in document
# order, jumps the notebook to whichever one is tapped, and shows a placeholder with none.
#
# NARROW WINDOW: above 840dp of editor the same command draws a pane that stays open across a
# pick, so the dismissal this flow asserts is the wrong question there rather than a failure -
# `p2h-3-table-of-contents-pane` makes the same claims against that adaptation.
#
# No cell is run, so nothing here needs the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# Reused rather than a new fixture: three headings at three different depths already exist here,
# and the notebook is already known not to fit one screen (p2f-2's own flow), which is what makes
# a jump-back a real movement to assert on.
seed_notebook "$ROOT/.maestro/fixtures/p2f-2-collapse-all-headings.ipynb" "Heading Sections"
# A2's own fixture: no `cells` key at all, so it opens on the one default cell `toEditorUiState`
# gives an empty file - a code cell, and so a notebook with no headings at all.
seed_notebook "$ROOT/.maestro/fixtures/a2-empty-notebook.ipynb" "Empty Notebook"

run_flow "$ROOT/.maestro/p2h-2-table-of-contents.yaml" "$ROOT/evidence/p2h-2-table-of-contents"
