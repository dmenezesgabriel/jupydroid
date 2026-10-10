#!/usr/bin/env bash
# A display updated by id redraws in place in the Console: entry 1 shows a handle, entry 2 updates
# it three times, and entry 1 ends on the last frame alone while entry 2 shows nothing - where every
# update used to be stored, invisible, under entry 2.
#
#   scripts/jd-016-display-update-t3-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-016-display-update/t3-console"
NOTEBOOK="Output Control"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-015-output-caps-control.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-016-display-update-t3-console.yaml" "$EVIDENCE/run"
