#!/usr/bin/env bash
# The Console carries out clear_output by entry: entry 1 prints, clears and prints again and shows
# only its second print, and entry 2's clear_output(wait=True) keeps its first print until its
# second replaces it - where every clear used to be stored, invisible, beside what it should erase.
#
#   scripts/console-clear-entries-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/console-clear/console"
NOTEBOOK="Output Control"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/output-caps-control.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/console-clear-entries.yaml" "$EVIDENCE/run"
