#!/usr/bin/env bash
# Seeds a notebook whose results are already saved - one of them folded - and clears them all.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime, and it is run on both.
# The outputs are in the fixture rather than produced by a kernel, which is what makes that safe
# and also what lets the *saved file* be the last assertion: a clear that only ever lived in the
# ViewModel would look identical on screen until the app was killed. The same split
# `batch-delete-driver.sh` uses.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/clear-all-outputs.ipynb" "Clear Them All"

# Per-ABI, because this flow is run on **both** devices and a shared directory would have the
# second run silently overwrite the first.
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
run_flow "$ROOT/.maestro/clear-all-outputs.yaml" "$ROOT/evidence/clear-all-outputs/$ABI"

# Both outputs and both counts are back, and `"collapsed": true` is back on the second cell -
# the undo restored the fold as well as the results, which is the half a screenshot of the top of
# the notebook would not show.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Clear Them All.ipynb\""
