#!/usr/bin/env bash
# A tap on a traceback's path to a workspace file opens the file. A seeded `mod.py` divides by
# zero; a notebook prints a path to stdout, prints a caught traceback to stderr and lets IPython
# draw an uncaught one. The stderr and error paths into the workspace open `mod.py`; the stdout
# path, and the stderr frame in the kernel's own temp dir, stay text. A Console on the kernel
# opens `mod.py` from its traceback too, and once `mod.py` is gone a tap says so.
#
#   scripts/text-output-path-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/text-output-link/traceback-path"
FLOWS="$ROOT/.maestro/text-output-path"
NOTEBOOK="Traceback Path Link"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/traceback-path-link.ipynb" "$NOTEBOOK"
seed_file "$ROOT/.maestro/fixtures/mod.py" "mod.py"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$FLOWS-run.yaml" "$EVIDENCE/part1-run"
run_flow "$FLOWS-stdout.yaml" "$EVIDENCE/part2-stdout-path"
run_flow "$FLOWS-stderr.yaml" "$EVIDENCE/part3-stderr-paths"
run_flow "$FLOWS-error.yaml" "$EVIDENCE/part4-error-path"
run_flow "$FLOWS-console.yaml" "$EVIDENCE/part5-console-path"
"$ADB" shell "run-as $APP rm \"$NOTEBOOK_DIR/mod.py\""
run_flow "$FLOWS-missing.yaml" "$EVIDENCE/part6-missing"
