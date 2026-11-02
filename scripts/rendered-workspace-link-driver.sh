#!/usr/bin/env bash
# A tap on a rendered workspace link opens what it names. A markdown cell holds a link to a seeded
# `notes.md`, one to a file nothing seeded, and one to a seeded `docs/` folder; a code cell displays
# a link to `notes.md` through IPython's Markdown and prints the kernel's 6 * 7.
#
#   scripts/rendered-workspace-link-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/rendered-link/workspace-link"
NOTEBOOK="Workspace Link"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/workspace-link.ipynb" "$NOTEBOOK"
seed_file "$ROOT/.maestro/fixtures/notes.md" "notes.md"
seed_file "$ROOT/.maestro/fixtures/docs-readme.txt" "docs/readme.txt"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/rendered-workspace-link-cell.yaml" "$EVIDENCE/cell-link"
run_flow "$ROOT/.maestro/rendered-workspace-link-output.yaml" "$EVIDENCE/output-link"
