#!/usr/bin/env bash
# The `output` and `selection` filters, both off by default as the reference's are.
#
# **No kernel**, and that is a reading rather than a shortcut: nbformat stores a cell's outputs, so
# the fixture carries the printed line the filter is asserted against - the same thing that made
# P2h-6 device-free. Nothing here runs a cell.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/search-filters.ipynb" "Printed Matches"

run_flow "$ROOT/.maestro/search-filters.yaml" "$ROOT/evidence/search-filters"
