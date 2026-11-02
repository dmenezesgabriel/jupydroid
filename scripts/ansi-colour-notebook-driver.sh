#!/usr/bin/env bash
# A notebook cell's ANSI-coloured print and repr, and a traceback, draw in their colours with no
# escape left in the text.
#
#   scripts/ansi-colour-notebook-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/ansi-colour}/notebook"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/ansi-colour.ipynb" "Ansi Colour"

run_flow "$ROOT/.maestro/ansi-colour-notebook.yaml" "$OUT"
