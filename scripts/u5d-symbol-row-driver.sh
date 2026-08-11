#!/usr/bin/env bash
# Seeds a one-cell notebook and runs the U5d symbol-row flow.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime.
#
# The saved file is the assertion. A text field shows whatever it was given, so a screenshot
# cannot tell a key that typed from one that only looked pressed - `def area():` on disk can.
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
seed_notebook "$ROOT/.maestro/fixtures/u5d-symbol-row.ipynb" u5d-symbol-row
run_flow "$ROOT/.maestro/u5d-symbol-row.yaml" "$ROOT/evidence/u5d-symbol-row"

# The order and shape on disk is the real assertion: two code cells became one code cell holding
# both sources, by way of a markdown cell that the merge folded back into code.
echo "--- notebook as saved after the flow ---"
"$ADB" shell run-as com.jupydroid.app cat files/notebooks/u5d-symbol-row.ipynb
