#!/usr/bin/env bash
# Seeds three code cells that each print a computed value, then runs the P2d ranged-run flow.
#
# TABLET ONLY (SM-X216B). Every assertion here is a number the kernel had to work out. On the
# x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's own source
# back as stdout - so `42` would never appear and the flow would fail for the wrong reason.
# scripts/run-all-flows.sh reads this marker and skips the flow there rather than failing it.
#
# The three values are `6 * 7`, `11 * 11` and `13 * 13`, and none of the products appears in the
# source that produces it. That is deliberate: Maestro matches text anywhere on screen, so a cell
# printing its own literal would let a source field satisfy an assertion about an output
# (scripts/check_maestro_output_assertions.py, and the three flows that learned it the hard way).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2d-ranged-runs.ipynb" "Ranged Runs"

run_flow "$ROOT/.maestro/p2d-ranged-runs.yaml" "$ROOT/evidence/p2d-2-ranged-runs"
