#!/usr/bin/env bash
# Seeds four cells in two dependent pairs, then drives both compound restarts.
#
# TABLET ONLY (SM-X216B). A restart is only observable through a kernel that had state to lose: on
# the x86_64 emulator MainActivity falls back to FakeKernelGateway, which has no state, no restart
# and no NameError to give. scripts/run-all-flows.sh reads this marker and skips the flow there
# rather than failing it.
#
# The pairing is the point. Cell 2 needs cell 1's `alpha` and cell 4 needs cell 3's `zeta`, so with
# the head on cell 2 a correct `restart-and-run-to-selected` leaves the notebook in a state no
# other outcome produces: 121 recomputed, and `zeta` gone.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/compound-restarts.ipynb" "Compound Restarts"

run_flow "$ROOT/.maestro/compound-restarts.yaml" "$ROOT/evidence/compound-restarts"
