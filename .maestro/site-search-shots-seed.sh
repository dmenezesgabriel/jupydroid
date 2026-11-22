#!/usr/bin/env bash
# The workspace .maestro/site-search-shots.yaml shoots: files whose text the workspace search
# finds - plain files at two depths and a markdown one for the glob scope - and the notebooks
# the in-document find half opens: one whose matches span cell kinds, one whose printed output
# a filter must reach, one whose cells a replace rewrites. Run by
# scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_file "$ROOT/.maestro/fixtures/search.py" "notes.py"
seed_file "$ROOT/.maestro/fixtures/search-nested.py" "reports/analysis.py"
seed_file "$ROOT/.maestro/fixtures/search-notes.md" "readme.md"
seed_notebook "$ROOT/.maestro/fixtures/notebook-search.ipynb" "analysis"
seed_notebook "$ROOT/.maestro/fixtures/notebook-find.ipynb" "Find And Replace"
seed_notebook "$ROOT/.maestro/fixtures/search-filters.ipynb" "Printed Matches"
seed_notebook "$ROOT/.maestro/fixtures/notebook-replace.ipynb" "Replace Me"
