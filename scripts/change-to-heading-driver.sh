#!/usr/bin/env bash
# `notebook:change-cell-to-heading-1..6`, behind one menu row and a picker.
#
# No cell is run - the fixture ships a saved output instead, so the "outputs go with the type"
# half is observable without a kernel. The flow therefore says the same thing on either device
# and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/change-to-heading.ipynb" "Change To Heading"

run_flow "$ROOT/.maestro/change-to-heading.yaml" "$ROOT/evidence/change-to-heading"

# What reached disk: `cell_type` is markdown, the two keys nbformat forbids a markdown cell are
# gone with it, and the cell's `tags` - which this app does not model - are still there, because a
# change of type goes through the same preserved-JSON overlay every other edit does.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Change To Heading.ipynb\""
