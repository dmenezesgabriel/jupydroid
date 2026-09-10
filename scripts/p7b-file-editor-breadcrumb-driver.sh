#!/usr/bin/env bash
# P7b: the plain file editor's breadcrumb - where a file lives, and the way back to its folder.
# NARROW WINDOW (<840dp): the 320dp emulator, and the tablet in portrait at 800dp.
#
# One window, deliberately, and the same reason `p2w-1-breadcrumb-driver.sh` gives: in one pane the
# file's trail is the only trail on screen, where two panes put the browser's beside it and every
# selector would match twice. The two-pane half is `p7b-file-editor-breadcrumb-wide`.
#
# Runs no cell, so `FakeKernelGateway` is never asked anything and the emulator is enough.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_file + window_width_dp

# The measured width names the evidence directory, so an 800dp run cannot land in a 320dp one -
# the gate `scripts/check_ui_dimensions.py`'s sibling P2z-d built for exactly this.
WIDTH_DP="$(window_width_dp)"
echo "=== the file editor's breadcrumb at ${WIDTH_DP}dp ==="

reset_notebooks
# P2w-1's own workspace, one file heavier. A **file** at the root with no ancestry to draw - the
# notebook fixture cannot play that part, since it opens the other editor - one inside a folder
# that has some, and a notebook beside it so the folder is not a one-file special case.
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
run_flow "$ROOT/.maestro/p7b-file-editor-breadcrumb.yaml" \
  "$ROOT/evidence/p7b-file-editor-breadcrumb/${WIDTH_DP}dp"
