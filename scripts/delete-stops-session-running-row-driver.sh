#!/usr/bin/env bash
# Deleting a notebook stops the kernel session it was running: does the Running panel's row leave
# with the file? `a.ipynb` is deleted with its tab open, `b.ipynb` with its tab closed, and
# `c.ipynb` is replaced by `d.ipynb` renamed over it; `keep.ipynb` holds a kernel throughout and
# is the row every absence is read beside. The workspace is read off the disk afterwards, because
# a row that left while its file stayed would be a stop that nothing had asked for.
#
#   scripts/delete-stops-session-running-row-driver.sh
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

EVIDENCE="$ROOT/evidence/delete-stops-session-running-row"

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
for name in a b c d keep; do
  seed_file "$ROOT/.maestro/fixtures/counter.ipynb" "$name.ipynb"
done
rotate_to 1

run_flow "$ROOT/.maestro/delete-stops-session.yaml" "$EVIDENCE/delete"
disk="$("${adb[@]}" shell "run-as $APP ls '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' ')"
echo "workspace on disk: $disk" | tee "$EVIDENCE/disk.txt"
[[ "$disk" == "c.ipynb keep.ipynb " ]] || { echo "expected c.ipynb and keep.ipynb alone on disk, found: $disk" >&2; exit 1; }
