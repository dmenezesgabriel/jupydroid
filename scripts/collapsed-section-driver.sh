#!/usr/bin/env bash
# A collapsed heading behaves as its whole section - deleting one takes the cells it hides.
#
# No cell is run, so this flow says the same thing on either device and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/collapsed-section.ipynb" "Collapsed Section"

run_flow "$ROOT/.maestro/collapsed-section.yaml" "$ROOT/evidence/collapsed-section"

# What reached disk after the undo: all six cells, in their original order, with Chapter one still
# carrying the collapse it was deleted under.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Collapsed Section.ipynb\""
