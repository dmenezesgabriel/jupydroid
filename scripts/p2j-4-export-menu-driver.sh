#!/usr/bin/env bash
# P2j-4's layout half: the export row and its picker, at the width that decides whether they fit.
#
# NARROW WINDOW (<840dp): the emulator at 320dp. *"Save and Export Notebook As…"* is the longest
# row this menu has and *"Reveal.js Slides"* the longest the picker has, and neither is in any
# danger on a 1280dp tablet - so a green run there would be a green run against the wrong question.
# The mirror of `WIDE WINDOW`, and the rule `docs/DECISIONS.md` records.
#
# It deliberately makes no claim about the conversion: on x86_64 the exporter is the fake. That
# claim is `p2j-4-export`'s, on the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p2j-4-export.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p2j-4-export-menu"
mkdir -p "$EVIDENCE"

width="$(window_width_dp)"
if [ "${width:-0}" -ge 840 ]; then
  echo "SKIP: this asserts the picker at a narrow width; this window is ${width}dp"
  exit 0
fi

reset_notebooks
seed_notebook "$FIXTURE" "p2j-4-export"

run_flow "$ROOT/.maestro/p2j-4-export-menu.yaml" "$EVIDENCE" || exit 1

echo "P2j-4 menu gate passed at ${width}dp: the export row fits, and so does its picker."
