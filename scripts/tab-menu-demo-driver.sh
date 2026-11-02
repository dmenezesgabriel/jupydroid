#!/usr/bin/env bash
# The demo: a document is deleted or duplicated from its tab. `tab-delete-driver.sh` runs as it
# stands - a dirty file and a notebook beneath another tab deleted from their tabs - then
# `tab-duplicate-driver.sh` - other.py copied to other-Copy1.py and other-Copy2.py, a copy of a copy
# named other-Copy3.py, and a notebook's copy that runs. On the copies the duplicate left,
# other-Copy2.py is deleted from its tab, and after a kill and relaunch it stays gone while the
# other copies open with their content and the notebook's copy runs.
#
#   scripts/tab-menu-demo-driver.sh
#
# Exits non-zero if a flow fails, or the disk afterwards does not hold what the screens showed.
#
# TABLET ONLY (SM-X216B): the cells need a real Space.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/tab-menu-demo"

trap restore_rotation EXIT

record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }

workspace_bytes() { # workspace_bytes <file in the workspace>
  "${adb[@]}" exec-out run-as "$APP" cat "$NOTEBOOK_DIR/$1"
}

on_disk() { # on_disk <file in the workspace>
  "${adb[@]}" shell "run-as $APP sh -c 'test -e \"$NOTEBOOK_DIR/$1\"'"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"

EVIDENCE="$EVIDENCE/delete" "$ROOT/scripts/tab-delete-driver.sh"
EVIDENCE="$EVIDENCE/duplicate" "$ROOT/scripts/tab-duplicate-driver.sh"

# Part 3 acts on the copies the duplicate left, so it resets nothing.
rotate_to 1
run_flow "$ROOT/.maestro/tab-menu-demo-relaunch.yaml" "$EVIDENCE/part3-relaunch"
on_disk other-Copy2.py && fail "expected other-Copy2.py gone from the workspace"
record "after the kill and relaunch, other-Copy2.py is not on disk"
for copy in other-Copy1.py other-Copy3.py; do
  cmp -s <(workspace_bytes "$copy") <(workspace_bytes other.py) || fail "expected $copy to hold what other.py does"
  record "after the kill and relaunch, $copy holds what other.py does"
done
