#!/usr/bin/env bash
# A notebook's kernel session follows its rename: does the Running panel's row name the notebook as
# it is now, and open its live tab? `f/a.ipynb` is run, renamed to `b.ipynb` from its tab, then its
# folder renamed to `g` from the explorer. The strip's titles are read off the window afterwards:
# one `b.ipynb` tab, because a row that opened a dead path would have pushed a second.
#
#   scripts/session-follows-rename-running-row-driver.sh
#
# Runs a cell, so it belongs on the arm64 tablet: on x86_64 the fake gateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/session-follows-rename-running-row"

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/counter.ipynb" "f/a.ipynb"
rotate_to 1

run_flow "$ROOT/.maestro/session-follows-rename.yaml" "$EVIDENCE/rename"
tabs="$(tab_order)"
disk="$("${adb[@]}" shell "run-as $APP ls -R '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' ')"
{
  echo "tabs after the row was tapped, left to right: $tabs"
  echo "workspace on disk: $disk"
} | tee "$EVIDENCE/strip.txt"
[[ "$disk" == *"g:"*"b.ipynb"* ]] || { echo "g/b.ipynb is not on disk; nothing was measured" >&2; exit 1; }
[[ "$(grep -o 'b\.ipynb' <<<"$tabs" | wc -l)" -eq 1 ]] || { echo "the row opened a second b.ipynb tab: $tabs" >&2; exit 1; }
