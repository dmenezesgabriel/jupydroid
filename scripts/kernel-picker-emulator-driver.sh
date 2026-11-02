#!/usr/bin/env bash
# The picker's shape on the emulator: the dialog opens from the indicator and lists the
# catalogue's own spec. No cell is run - see the flow's header for why.
#
# NARROW WINDOW (<840dp): the emulator at 320dp. The driver itself does not gate on width - the
# flow asserts nothing that would change at another size; the declaration is for the sharder's
# placement, the same reading `run-flows-sharded.sh` makes.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/kernel-picker-emulator.ipynb" "P8g Emulator"
run_flow "$ROOT/.maestro/kernel-picker-emulator.yaml" \
  "$ROOT/evidence/kernel-picker-tablet/emulator/$(window_width_dp)dp"
