#!/usr/bin/env bash
# A tap on a traceback's path to a workspace file opens the file. A seeded `mod.py` divides by
# zero; a notebook prints a path to stdout, prints a caught traceback to stderr and lets IPython
# draw an uncaught one. The stderr and error paths into the workspace open `mod.py`; the stdout
# path, and the stderr frame in the kernel's own temp dir, stay text. A Console on the kernel
# opens `mod.py` from its traceback too, and once `mod.py` is gone a tap says so.
#
#   scripts/jd-022-text-output-link-t2-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-022-text-output-link/t2-traceback-path"
FLOWS="$ROOT/.maestro/jd-022-text-output-link-t2"
NOTEBOOK="Traceback Path Link"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-022-traceback-path-link.ipynb" "$NOTEBOOK"
seed_file "$ROOT/.maestro/fixtures/jd-022-mod.py" "mod.py"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$FLOWS-part1-run.yaml" "$EVIDENCE/part1-run"
run_flow "$FLOWS-part2-stdout-path.yaml" "$EVIDENCE/part2-stdout-path"
run_flow "$FLOWS-part3-stderr-paths.yaml" "$EVIDENCE/part3-stderr-paths"
run_flow "$FLOWS-part4-error-path.yaml" "$EVIDENCE/part4-error-path"
run_flow "$FLOWS-part5-console-path.yaml" "$EVIDENCE/part5-console-path"
"$ADB" shell "run-as $APP rm \"$NOTEBOOK_DIR/mod.py\""
run_flow "$FLOWS-part6-missing.yaml" "$EVIDENCE/part6-missing"
