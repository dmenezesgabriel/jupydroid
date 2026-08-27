#!/usr/bin/env bash
# P2x-3f: a tap on a search result opens the file **at the match**.
#
# One file, one match, on line 47 of 60 - because a match on line 1 is on screen whether anything
# revealed it or not, and would pass against the editor this step exists to change. The flow's own
# header carries the citations.
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

# One file and one match, so "1 result in 1 file" is the whole answer and the tap is unambiguous.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/p2x-3-search-deep.py" "deep.py"

run_flow "$ROOT/.maestro/p2x-3-open-at-the-match.yaml" "$ROOT/evidence/p2x-3-open-at-the-match"
