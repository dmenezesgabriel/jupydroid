#!/usr/bin/env bash
# Seeds one two-line markdown cell and runs the P2b markdown split flow.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime, and it is run on both.
#
# The screenshots show the two cells; the *saved file*, printed at the end, is what says both
# fragments are still `markdown` and that the original kept its id - `md-1` on the second of them,
# exactly as `code-1` is in the code-cell flow.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# The handwriting IME steals focus from text fields; this flow puts a caret in one and drives it.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2b-split-markdown.ipynb" "Cut The Prose"

# Per-ABI, because this flow is run on **both** devices and a shared directory would have the
# second run silently overwrite the first.
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
run_flow "$ROOT/.maestro/p2b-split-markdown.yaml" "$ROOT/evidence/p2b-split-cell/markdown-$ABI"

echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Cut The Prose.ipynb\""
