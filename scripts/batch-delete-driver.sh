#!/usr/bin/env bash
# Seeds four code cells - one of them with a saved output and an execution count - and runs the
# The batch-delete flow.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime, and it is run on both.
#
# The screenshots show three cells going in one tap and coming back in one; the *saved file*,
# printed at the end, is what proves both reached disk. The same split `undo-cell-operation-driver.sh` uses,
# and for the same reason - a batch that only ever lived in the ViewModel would look identical on
# screen until the app was killed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/batch-delete.ipynb" "Take Three Back"

# Per-ABI, because this flow is run on **both** devices and a shared directory would have the
# second run silently overwrite the first.
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
run_flow "$ROOT/.maestro/batch-delete.yaml" "$ROOT/evidence/multi-select/$ABI-batch"

# Four cells again, with `batch output` and `execution_count: 7` back on the second: the undo of a
# batch restored the results, not only the sources.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Take Three Back.ipynb\""
