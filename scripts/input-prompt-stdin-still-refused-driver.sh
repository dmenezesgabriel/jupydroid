#!/usr/bin/env bash
# The stdin wire is in, and `allow_stdin` is still false: `input()` raises StdinNotImplementedError
# instead of hanging its cell, and the kernel runs the next cell after it.
#
#   scripts/input-prompt-stdin-still-refused-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/input-prompt}/t2-stdin-still-refused"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/stdin-refused.ipynb" "Stdin Refused"

run_flow "$ROOT/.maestro/input-prompt-stdin-still-refused.yaml" "$OUT"
