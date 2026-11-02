#!/usr/bin/env bash
# The table of contents overlay opens from the View menu, lists every heading in document
# order, jumps the notebook to whichever one is tapped, and shows a placeholder with none.
#
# NARROW WINDOW: above 840dp of editor the same command draws a pane that stays open across a
# pick, so the dismissal this flow asserts is the wrong question there rather than a failure -
# `toc-side-panel` makes the neighbouring claims against the shell's own sidebar panel.
#
# No cell is run, so nothing here needs the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook + window_width_dp

# NARROW WINDOW is two real widths here, so the evidence says which one it holds - the shape
# `one-pane-side-panels-driver.sh` established, now gated by `check_driver_isolation.py`.
WIDTH_DP="$(window_width_dp)"
echo "=== the table of contents at ${WIDTH_DP}dp ==="

reset_notebooks
# **A fixture of its own, and the tablet in portrait is why.** This reused `collapse-all-headings`'s, on
# the grounds that "the notebook is already known not to fit one screen" - and that was measured on
# the 320dp emulator, 640dp tall. NARROW WINDOW also admits the tablet in portrait, 800x1280, where
# all seven of `collapse-all-headings`'s cells fit with 350px to spare: `scrollUntilVisible` found "Chapter two"
# without scrolling at all, so there was no movement for the jump to undo and the flow's whole claim
# was unassertable - the portrait run recorded no movement to assert on.
#
# Same three headings at the same depths and the same texts the flow addresses; eight `filler_N`
# code cells between them, which is `open-at-the-cell.ipynb`'s own idiom for a document that
# has to overflow. Fifteen cells is about 1.9x the taller window's viewport, so the first cell is
# off screen long before the last heading is reached - at *both* widths, which is the property the
# old fixture had at only one.
seed_notebook "$ROOT/.maestro/fixtures/table-of-contents-overlay.ipynb" "Heading Sections"
# The empty-notebook fixture: no `cells` key at all, so it opens on the one default cell `toEditorUiState`
# gives an empty file - a code cell, and so a notebook with no headings at all.
seed_notebook "$ROOT/.maestro/fixtures/empty-notebook.ipynb" "Empty Notebook"

run_flow "$ROOT/.maestro/table-of-contents-overlay.yaml" \
  "$ROOT/evidence/table-of-contents-overlay/${WIDTH_DP}dp"
