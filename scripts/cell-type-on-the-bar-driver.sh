#!/usr/bin/env bash
# The notebook's bar names the active cell's type and changes it in one gesture - the
# reference's `cellType` toolbar item (`packages/notebook-extension/schema/panel.json`, rank 40).
#
# WIDE WINDOW: measured, the action row has 72dp free at 320dp and the widest label this control
# draws wants 73.3dp before its caret, so the bar carries it only from the medium width class up
# (`cellTypeOnBar`). The narrow half is `cell-type-in-the-overflow`, which is where a
# `ReactiveToolbar` puts an item that does not fit.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# One cell of each of the three types, because the claim is that the control names *which* - and a
# raw cell is the one this app has no other way to tell from an unrendered markdown cell.
seed_notebook "$ROOT/.maestro/fixtures/cell-types.ipynb" "Cell Types"

run_flow "$ROOT/.maestro/cell-type-on-the-bar.yaml" "$ROOT/evidence/cell-type-on-the-bar"
