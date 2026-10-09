#!/usr/bin/env bash
# A `\r` progress loop in a Console entry draws one line, and the prints after it join the same
# output.
#
#   scripts/jd-013-progress-bar-t2-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-013-progress-bar}/t2-console"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-013-progress-bar-console.ipynb" "Progress Console"

run_flow "$ROOT/.maestro/jd-013-progress-bar-t2-console.yaml" "$OUT"
