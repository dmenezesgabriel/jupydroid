#!/usr/bin/env bash
# Seeds a notebook of prose over ten short code lines, then captures the three surfaces
# `bodyLarge` reaches.
#
# **The evidence is a pair, not a screenshot.** The change moves no size and no colour;
# it restores the vertical metrics Material builds every other role with - `includeFontPadding =
# false` and `DefaultLineHeightStyle` - to the one role that was hand-built without them. Nothing
# about a single frame can show that, so the driver is run once on the build before the change and
# once on the build after it, into `before/` and `after/`, and the two are compared.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/text-metrics.ipynb"
FLOW="$ROOT/.maestro/text-metrics.yaml"
# The half of the pair this run is capturing. `after` unless the caller says otherwise, because
# `before` can only be captured deliberately - from a checkout that predates the change.
EVIDENCE="$ROOT/evidence/text-metrics/${1:-after}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$FIXTURE" "Text Metrics"
run_flow "$FLOW" "$EVIDENCE"

echo
echo "screenshots: $EVIDENCE/"
