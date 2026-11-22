#!/usr/bin/env bash
# The workspace .maestro/site-interface-one-pane-shots.yaml shoots: the same three files the
# landscape interface flow seeds, so the Files panel at one pane lists what a workspace looks
# like. Run by scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_notebook "$ROOT/.maestro/fixtures/collapse-all-headings.ipynb" "Heading Sections"
seed_file "$ROOT/.maestro/fixtures/search-notes.md" "field-notes.md"
seed_file "$ROOT/.maestro/fixtures/search.py" "notes.py"
