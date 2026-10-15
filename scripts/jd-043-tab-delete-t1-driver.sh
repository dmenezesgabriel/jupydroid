#!/usr/bin/env bash
# A document is deleted from its own tab: with autosave off, notes.py is edited and deleted from its
# tab's long-press menu - Cancel keeps it, Delete closes the tab unasked about the edit - and the
# disk no longer holds it. Then kernel.ipynb runs its cell, other.py opens on top, and kernel.ipynb
# is deleted from its tab beneath: the tab closes, other.py stays, and its kernel row goes.
#
#   scripts/jd-043-tab-delete-t1-driver.sh
#
# Runs a cell, so it is driven on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="${EVIDENCE:-$ROOT/evidence/jd-043-tab-delete/t1}"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

on_disk() { # on_disk <file in the workspace>
  "${adb[@]}" shell "run-as $APP sh -c 'test -e \"$NOTEBOOK_DIR/$1\"'"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `p6c-8-close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" notes.py
seed_notebook "$ROOT/.maestro/fixtures/jd-043-kernel.ipynb" kernel
seed_file "$ROOT/.maestro/fixtures/jd-043-other.py" other.py
rotate_to 1

run_flow "$ROOT/.maestro/jd-043-tab-delete-t1-part1-file.yaml" "$EVIDENCE/part1-file"
on_disk notes.py && fail "expected notes.py gone from the workspace"
echo "after part 1, notes.py is not on disk" | tee "$EVIDENCE/disk.txt"

run_flow "$ROOT/.maestro/jd-043-tab-delete-t1-part2-notebook.yaml" "$EVIDENCE/part2-notebook"
on_disk kernel.ipynb && fail "expected kernel.ipynb gone from the workspace"
on_disk other.py || fail "expected other.py still on disk"
echo "after part 2, kernel.ipynb is not on disk and other.py is" | tee -a "$EVIDENCE/disk.txt"
