#!/usr/bin/env bash
# The workspace .maestro/site-interface-shots.yaml shoots: a notebook whose headings fill the
# outline panel, and two files whose text the workspace search finds. Run by
# scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself - a surface
# that needs content no tap makes reliably gets it seeded here, not invented in the flow.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_notebook "$ROOT/.maestro/fixtures/collapse-all-headings.ipynb" "Heading Sections"
seed_file "$ROOT/.maestro/fixtures/search-notes.md" "field-notes.md"
seed_file "$ROOT/.maestro/fixtures/search.py" "notes.py"
