#!/usr/bin/env bash
# P2h-6: `toc:display-outputs-numbering` - the headings a code cell printed, listed beside the ones
# its neighbours wrote, and the command that leaves them out.
#
# WIDE WINDOW rather than TABLET ONLY: nbformat stores a cell's outputs, so the fixture's HTML was
# saved into the file rather than produced by a kernel. No cell is run here.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2h-6-output-headings.ipynb" "Printed Headings"

run_flow "$ROOT/.maestro/p2h-6-output-headings.yaml" "$ROOT/evidence/p2h-6-output-headings"
