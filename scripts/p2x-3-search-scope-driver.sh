#!/usr/bin/env bash
# P2x-3e: the "files to include" and "files to exclude" boxes behind the search panel's `…`.
#
# Three files are seeded rather than the two the other search flows use, because an include of
# `*.py` over two `.py` files removes nothing and would prove nothing. The flow's own header carries
# the citations and the arithmetic.
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

# A search reads *everything*, so a workspace this flow did not lay down is a count it cannot
# predict - the reason every flow in this family resets first.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/p2x-3-search.py" "notes.py"
seed_file "$ROOT/.maestro/fixtures/p2x-3-search-nested.py" "reports/analysis.py"
# The one file an include of `*.py` has to remove, and an exclude of `reports` has to bring back.
seed_file "$ROOT/.maestro/fixtures/p2x-3-search-notes.md" "readme.md"

run_flow "$ROOT/.maestro/p2x-3-search-scope.yaml" "$ROOT/evidence/p2x-3-search-scope"
