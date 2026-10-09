#!/usr/bin/env bash
# A cell's `input()` and `getpass()` prompt under the cell and carry on with the answer, Stop closes
# an unanswered prompt, and a Console entry prompts the same way.
#
#   scripts/jd-012-input-prompt-t3-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-012-input-prompt}/t3-answers"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-012-input-prompt.ipynb" "Input Prompt"

run_flow "$ROOT/.maestro/jd-012-input-prompt-t3-answers.yaml" "$OUT"
