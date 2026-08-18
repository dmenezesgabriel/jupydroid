#!/usr/bin/env bash
# P2h-3: the table of contents as a persistent pane beside the notebook, where the editor has room
# for one - it opens from the View menu, survives a jump, and closes from its own button.
#
# WIDE WINDOW: `tocPresentationFor` gives a pane only above 840dp of *editor* width, which the
# tablet has (1280dp window less the 360dp explorer) and the emulator does not.
# `p2h-2-table-of-contents` makes the same claims against the overlay a narrow one gets.
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

run_flow "$ROOT/.maestro/p2h-3-table-of-contents-pane.yaml" "$ROOT/evidence/p2h-3-table-of-contents-pane"
