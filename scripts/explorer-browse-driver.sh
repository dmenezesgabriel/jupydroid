#!/usr/bin/env bash
# Seeds a workspace with a folder in it and runs the explorer-browse flow.
#
# The emulator is enough: nothing here runs a cell, so `FakeKernelGateway` never gets asked
# anything. It is also the better device for this one - at 320dp it is the narrowest window the
# app supports, and the breadcrumb and the rows have the least room there.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# A notebook at the root, one inside a folder, and a file that is neither: the three things the
# explorer has to tell apart and Recents cannot show.
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "At Root"
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/explorer-browse.yaml" "$ROOT/evidence/explorer-browse"
