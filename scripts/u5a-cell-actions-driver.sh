#!/usr/bin/env bash
# Seeds two code cells and runs the U5a cell-actions flow.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime.
#
# The screenshots show what happened; the *saved file*, printed at the end, is what proves it.
# Change-type and merge both rewrite the notebook's structure, and a menu that appeared to work
# while writing the wrong thing to disk is exactly the failure Step 4b's cell menu had to be
# checked for by reading the file back.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# The handwriting IME steals focus from text fields; this flow taps into one.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/u5a-cell-actions.ipynb" "Cell Actions"
run_flow "$ROOT/.maestro/u5a-cell-actions.yaml" "$ROOT/evidence/u5a-cell-actions"

# The order and shape on disk is the real assertion: two code cells became one code cell holding
# both sources, by way of a markdown cell that the merge folded back into code.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Cell Actions.ipynb\""
