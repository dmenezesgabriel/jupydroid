#!/usr/bin/env bash
# P2o-2: the table of contents as a **sidebar panel of the shell**, reached from the activity bar
# beside the file browser - the rail swaps the side area, a pick jumps and leaves the panel up, and
# picking the showing panel again collapses the area to the bar alone.
#
# WIDE WINDOW: the rail is drawn only where there is room for two panes, which the tablet's 1280dp
# window has and the emulator's 320dp does not. `p2h-2-table-of-contents` makes the neighbouring
# claims against the overlay the same menu row opens where there is no side area to activate.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# p2f-2's fixture: three headings at three depths, in a notebook already known not to fit one
# screen - which is what makes the jump below a movement worth asserting.
seed_notebook "$ROOT/.maestro/fixtures/p2f-2-collapse-all-headings.ipynb" "Heading Sections"

run_flow "$ROOT/.maestro/p2o-2-side-panel.yaml" "$ROOT/evidence/p2o-2-side-panel"
