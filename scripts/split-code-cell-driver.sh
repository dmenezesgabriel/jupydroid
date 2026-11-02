#!/usr/bin/env bash
# Seeds one two-line code cell carrying a saved output and an execution count, then runs the
# split flow.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime, and it is run on both.
#
# The screenshots show which fragment kept the output; the *saved file*, printed at the end, is
# what proves the split and the redo reached disk. Both matter and for different reasons - a split
# that only ever lived in the ViewModel would look identical on screen until the app was killed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# The handwriting IME steals focus from text fields and swallows inputText; this flow puts a caret
# in one and drives it with the symbol row.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/split-code-cell.ipynb" "Cut It In Two"

# Per-ABI, because this flow is run on **both** devices and a shared directory would have the
# second run silently overwrite the first - the trap `record_device` was written for.
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
run_flow "$ROOT/.maestro/split-code-cell.yaml" "$ROOT/evidence/split-code-cell/$ABI"

# Two cells, one line each, and the output and `execution_count: 7` on the *second* of them - the
# reference's rule, on disk rather than on screen.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Cut It In Two.ipynb\""
