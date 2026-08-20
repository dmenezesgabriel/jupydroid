#!/usr/bin/env bash
# Seeds a workspace with a folder and a notebook holding a folded cell, then captures the three
# surfaces whose padding P2l-2 moved onto `theme/Spacing`.
#
# **What the screenshots are for is a height, not a look.** The explorer's rows and its breadcrumb
# carried `vertical = 14.dp` - off Material's 4dp grid - and take `Spacing.lg` now. The before half
# of the pair is already committed: `evidence/p2l-1-typography/after/01-file-rows.png` is the same
# emulator listing a seeded notebook through the same steps, one commit earlier.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p2l-2-spacing.ipynb"
DATA="$ROOT/.maestro/fixtures/explorer-data.csv"
FLOW="$ROOT/.maestro/p2l-2-spacing.yaml"
EVIDENCE="$ROOT/evidence/p2l-2-spacing"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$FIXTURE" "Spacing"
# A folder with something in it, so the breadcrumb has a second crumb to draw and a separator
# between them - the control with the tightest height budget on that screen.
seed_file "$DATA" "reports/data.csv"
run_flow "$FLOW" "$EVIDENCE"

echo
echo "screenshots: $EVIDENCE/"
