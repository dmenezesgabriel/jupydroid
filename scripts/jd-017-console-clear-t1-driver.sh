#!/usr/bin/env bash
# The Console carries out clear_output by entry: entry 1 prints, clears and prints again and shows
# only its second print, and entry 2's clear_output(wait=True) keeps its first print until its
# second replaces it - where every clear used to be stored, invisible, beside what it should erase.
#
#   scripts/jd-017-console-clear-t1-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-017-console-clear/t1-console"
NOTEBOOK="Output Control"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-015-output-caps-control.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-017-console-clear-t1-console.yaml" "$EVIDENCE/run"
