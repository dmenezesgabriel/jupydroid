#!/usr/bin/env bash
# A document is duplicated from its own tab: other.py is copied twice from its tab - other-Copy1.py,
# other-Copy2.py - and other-Copy1.py once from its own, which the server's rule names
# other-Copy3.py; each copy holds what other.py does. Then kernel.ipynb is copied to
# kernel-Copy1.ipynb, which opens and runs its cell.
#
#   scripts/tab-duplicate-driver.sh
#
# Runs a cell, so it is driven on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="${EVIDENCE:-$ROOT/evidence/tab-duplicate}"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

workspace_bytes() { # workspace_bytes <file in the workspace>
  "${adb[@]}" exec-out run-as "$APP" cat "$NOTEBOOK_DIR/$1"
}

same_as() { # same_as <copy> <source>
  cmp -s <(workspace_bytes "$1") <(workspace_bytes "$2") || fail "expected $1 to hold what $2 does"
  echo "$1 holds what $2 does" | tee -a "$EVIDENCE/disk.txt"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/tab-copy-other.py" other.py
seed_notebook "$ROOT/.maestro/fixtures/kernel.ipynb" kernel
rotate_to 1

run_flow "$ROOT/.maestro/tab-duplicate-file.yaml" "$EVIDENCE/part1-file"
for copy in other-Copy1.py other-Copy2.py other-Copy3.py; do same_as "$copy" other.py; done

run_flow "$ROOT/.maestro/tab-duplicate-notebook.yaml" "$EVIDENCE/part2-notebook"
"${adb[@]}" shell "run-as $APP test -s \"$NOTEBOOK_DIR/kernel-Copy1.ipynb\"" || fail "expected kernel-Copy1.ipynb on disk"
echo "kernel-Copy1.ipynb is on disk" | tee -a "$EVIDENCE/disk.txt"
