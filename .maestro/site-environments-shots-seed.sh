#!/usr/bin/env bash
# The workspace .maestro/site-environments-shots.yaml shoots: proj-a holding deps.ipynb - the
# folder the panel's "Create environment" acts on and the project whose `.venv` the picker
# offers. Run by scripts/site-screenshots-driver.sh between the per-flow reset and the flow.
#
# reset_notebooks wipes the workspace but not base's kernelspecs, which live in
# usr/local/share/jupyter/kernels beside it - so a spec a previous pass's create registered is
# removed here, or the picker's first frame would offer a kernel whose env is already gone.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_file "$ROOT/.maestro/fixtures/project-environments-proj-a.ipynb" "proj-a/deps.ipynb"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/base/usr/local/share/jupyter/kernels' || true"
