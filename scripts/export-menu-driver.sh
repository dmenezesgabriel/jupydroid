#!/usr/bin/env bash
# The layout half of `export-lands-in-workspace`: the export row and its picker, at the width that decides whether they fit.
#
# NARROW WINDOW (<840dp): the 320dp emulator and the tablet in portrait at 800dp, whichever this is
# - the width is measured and named in the evidence path, so the two cannot overwrite each other.
# *"Save and Export Notebook As…"* is the longest row this menu has and *"Reveal.js Slides"* the
# longest the picker has, and neither is in any danger on a 1280dp tablet - so a green run there
# would be a green run against the wrong question. The mirror of `WIDE WINDOW`, and the rule
# `architecture/cross-cutting.md` §8.6 records.
#
# It deliberately makes no claim about the conversion: on x86_64 the exporter is the fake. That
# claim is `export-lands-in-workspace`'s, on the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/export-lands-in-workspace.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

width="$(window_width_dp)"
if [ "${width:-0}" -ge 840 ]; then
  echo "SKIP: this asserts the picker at a narrow width; this window is ${width}dp"
  exit 0
fi

reset_notebooks
seed_notebook "$FIXTURE" "export-lands-in-workspace"

run_flow "$ROOT/.maestro/export-menu.yaml" \
  "$ROOT/evidence/export-menu/${width}dp" || exit 1

echo "export-menu gate passed at ${width}dp: the export row fits, and so does its picker."
