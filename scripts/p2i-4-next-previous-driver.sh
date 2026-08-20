#!/usr/bin/env bash
# P2i-4: the find bar's arrows step through the matches, wrap at both ends, and expand the
# collapsed section hiding the match they land on - which the reference does too, through
# `expandParent` on `activeCellChanged`.
#
# No cell is run, so nothing here needs the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# Its own fixture: two matches with a collapsed heading between them is exactly the shape the
# reveal needs, and P2i-3's has no folded section at all.
seed_notebook "$ROOT/.maestro/fixtures/p2i-4-next-previous.ipynb" "Folded Matches"

run_flow "$ROOT/.maestro/p2i-4-next-previous.yaml" "$ROOT/evidence/p2i-4-next-previous"
