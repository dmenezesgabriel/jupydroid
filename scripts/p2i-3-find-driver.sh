#!/usr/bin/env bash
# P2i-3: the find bar replaces the notebook's bar, counts every match in the document across all
# three cell kinds, ignores case by default, and hands the bar back when it is closed.
#
# No cell is run, so nothing here needs the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# Its own fixture rather than a reused one: the count is the assertion here, so what is in the
# notebook has to be exactly what the flow claims - two matches in a code cell, one in markdown
# source, one in a raw cell, and one differing only in case.
seed_notebook "$ROOT/.maestro/fixtures/p2i-3-find.ipynb" "Find And Replace"

run_flow "$ROOT/.maestro/p2i-3-find.yaml" "$ROOT/evidence/p2i-3-find"
