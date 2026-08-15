#!/usr/bin/env bash
# Seeds four code cells and runs the P2c selection flow.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime, and it is run on both.
#
# Nothing is printed off the device afterwards, and that is deliberate rather than an omission: a
# selection is not the document. `NotebookRevision` does not carry one and `NotebookCellSelection`
# schedules no save, so there is nothing on disk that marking cells should have changed - the
# screenshots and the bar's own count are the whole of the evidence here.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2c-multi-select.ipynb" "Pick A Few"

# Per-ABI, because this flow is run on **both** devices and a shared directory would have the
# second run silently overwrite the first - the trap `record_device` was written for, closed here
# the way `p2a-undo-driver.sh` closes it.
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
run_flow "$ROOT/.maestro/p2c-selection.yaml" "$ROOT/evidence/p2c-multi-select/$ABI"
