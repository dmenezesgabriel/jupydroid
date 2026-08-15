#!/usr/bin/env bash
# Seeds two code cells - one of them with a saved output and an execution count - and runs the
# P2a undo/redo flow.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime, and it is run on both.
#
# The screenshots show that the cell came back; the *saved file*, printed at the end, is what
# proves the undo and the redo reached disk. Both matter here for different reasons - an undo that
# only ever lived in the ViewModel would look identical on screen until the app was killed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# The handwriting IME steals focus from text fields and swallows inputText; this flow types.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2a-undo-cell-operation.ipynb" "Take It Back"

# Evidence goes under the ABI of the device that produced it, because this flow is run on **both**
# and a shared directory would have the second run silently overwrite the first. That is the trap
# `record_device` was written for - it stamps the device *inside* a directory that a later sweep on
# another device can still replace - and a per-device directory closes it for this flow.
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
run_flow "$ROOT/.maestro/p2a-undo-cell-operation.yaml" "$ROOT/evidence/p2a-undo-cell-operation/$ABI"

# One cell, holding the typed characters: the redo took the second cell away for good, and the
# typing that happened between the delete and the undo was never rolled back with it.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Take It Back.ipynb\""
