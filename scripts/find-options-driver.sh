#!/usr/bin/env bash
# Match case, Match whole word and Use regular expression, each changing the match count by
# exactly one against a fixture built to make that the assertion.
#
# No cell is run, so nothing here needs the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/find-options.ipynb" "Search Options"

run_flow "$ROOT/.maestro/find-options.yaml" "$ROOT/evidence/find-options"
