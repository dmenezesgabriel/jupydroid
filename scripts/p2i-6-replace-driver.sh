#!/usr/bin/env bash
# P2i-6: Replace, Replace All and Preserve Case - and one undo for the whole of a Replace All.
#
# No cell is run, so nothing here needs the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# Single-line cells on purpose: Maestro matches whole nodes, and a cell's field is one node holding
# all of its source - so a one-line cell is the only kind whose text a flow can name.
seed_notebook "$ROOT/.maestro/fixtures/p2i-6-replace.ipynb" "Replace Me"

run_flow "$ROOT/.maestro/p2i-6-replace.yaml" "$ROOT/evidence/p2i-6-replace"
