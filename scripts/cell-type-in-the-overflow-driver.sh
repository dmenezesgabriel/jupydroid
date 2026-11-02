#!/usr/bin/env bash
# The cell-type control in the notebook's overflow, at the width whose bar cannot carry it.
#
# COMPACT WINDOW: measured, 72dp free in the action row at 320dp against a 73.3dp label, so
# `cellTypeOnBar` is false below the medium boundary and a `ReactiveToolbar` moves the item into
# its popup opener. The wide half is `cell-type-on-the-bar`.
#
# **Not NARROW WINDOW**, which means <840dp and so includes the tablet in portrait at 800dp - a
# window where the bar *does* carry the control and this flow's first assertion would be false.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# The same fixture the wide half uses, so the two capture one notebook from two widths.
seed_notebook "$ROOT/.maestro/fixtures/cell-types.ipynb" "Cell Types"

run_flow "$ROOT/.maestro/cell-type-in-the-overflow.yaml" \
  "$ROOT/evidence/cell-type-in-the-overflow"
