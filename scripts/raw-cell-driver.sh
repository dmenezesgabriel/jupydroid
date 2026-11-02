#!/usr/bin/env bash
# Opens a notebook holding a raw cell, and runs it.
#
# The fixture is an ordinary nbformat 4 notebook with all three cell types in it. On the build
# before this step it could not be opened at all - `CellFile.toDomain` threw on `cell_type: "raw"`
# and the editor showed the exception instead of the notebook.
#
# **No device rule.** Nothing here needs a real kernel: the claims are about this app's own
# filtering, and the code cell's `[1]` arrives on the emulator's `FakeKernelGateway` too. That is
# deliberate - the run has to reach the code cell wherever it is run, and still not reach the raw
# one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/raw-cell.ipynb" "Raw Cells"

run_flow "$ROOT/.maestro/raw-cell.yaml" "$ROOT/evidence/raw-cell"

# The raw cell survived the save the run triggered, and did not acquire the two keys nbformat
# forbids it: `raw_cell` is `additionalProperties: false` over id/cell_type/metadata/source/
# attachments (shipped rootfs, `nbformat/v4/nbformat.v4.schema.json`).
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Raw Cells.ipynb\""
