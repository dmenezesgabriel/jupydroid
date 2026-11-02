#!/usr/bin/env bash
# In the app's Dark theme, a notebook's and a Console's ANSI-coloured print, repr and traceback
# draw in their colours with no escape left in the text.
#
#   scripts/ansi-colour-dark-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/ansi-colour}/console-dark"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/ansi-colour-console.ipynb" "Ansi Console"

run_flow "$ROOT/.maestro/ansi-colour-console-dark.yaml" "$OUT"
