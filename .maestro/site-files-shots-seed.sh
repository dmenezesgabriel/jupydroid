#!/usr/bin/env bash
# The workspace .maestro/site-files-shots.yaml shoots: a reports/ folder holding a notebook and
# a data file, plus a notebook and a script at the root - enough tree for the browser, the
# breadcrumb, the file editor and the Recents section to all have something true to show. Run
# by scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "At Root"
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "script.py"
